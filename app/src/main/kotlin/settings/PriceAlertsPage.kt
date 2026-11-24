package dev.spectra.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraEmptyStateContent
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraSwitchRow
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.chainName
import dev.spectra.models.code
import dev.spectra.models.displayName
import dev.spectra.models.priceAlertConditions
import dev.spectra.models.statusColor
import dev.spectra.models.statusText
import dev.spectra.models.titleText
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.spectra_core.PriceAlertCondition
import uniffi.spectra_core.PriceAlertRule
import uniffi.spectra_core.StateCommand

/** Price alerts: the switch, a new rule for an asset held, and the rules core watches. */
@Composable
internal fun PriceAlertsPage() {
    val app = LocalAppState.current
    val scope = rememberCoroutineScope()
    var selectedHoldingKey by rememberSaveable { mutableStateOf("") }
    var selectedCondition by rememberSaveable { mutableStateOf(PriceAlertCondition.ABOVE) }
    var targetPriceText by rememberSaveable { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var formMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var removingAlertId by rememberSaveable { mutableStateOf<String?>(null) }
    val portfolio = app.portfolio
    val alertableHoldingKeys = portfolio.map { it.id }.toSet()
    val selectedCoin = portfolio.firstOrNull { it.id == selectedHoldingKey }
    val canAddAlert = selectedCoin != null && targetPriceText.isNotEmpty() && !isSubmitting

    LaunchedEffect(alertableHoldingKeys) {
        if (selectedHoldingKey !in alertableHoldingKeys) selectedHoldingKey = portfolio.firstOrNull()?.id ?: ""
    }

    fun editAlert(command: StateCommand) {
        scope.launch {
            try {
                app.priceAlerts.edit(command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                formMessage = userErrorMessage(error)
            }
        }
    }

    fun addAlert() {
        val coin = selectedCoin ?: return
        val target = AmountPresentation.canonicalDecimalInput(targetPriceText)
        isSubmitting = true
        scope.launch {
            try {
                app.priceAlerts.edit(StateCommand.AddPriceAlert(coin.id, target, app.selectedFiatCurrency, selectedCondition))
                app.notifications.requestPermission()
                targetPriceText = ""
                selectedCondition = PriceAlertCondition.ABOVE
                formMessage = AppLocalization.string("Alert added. Spectra will notify you when this target is hit.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                formMessage = userErrorMessage(error)
            } finally {
                isSubmitting = false
            }
        }
    }

    SpectraPage(title = AppLocalization.string("Price Alerts")) {
        FormSection(title = AppLocalization.string("Notifications")) {
            row {
                SpectraSwitchRow(
                    title = AppLocalization.string("Enable Price Alerts"),
                    checked = app.appSettings.usePriceAlerts,
                    onCheckedChange = { app.updateSetting(uniffi.spectra_core.AppSettingUpdate.UsePriceAlerts(it)) },
                )
            }
        }
        FormSection(title = AppLocalization.string("New Alert")) {
            if (portfolio.isEmpty()) {
                row {
                    SpectraEmptyStateContent(
                        title = AppLocalization.string("No alertable assets"),
                        message = AppLocalization.string(
                            "Import a wallet with assets first. Alerts are created from assets currently in your portfolio.",
                        ),
                        icon = Icons.AutoMirrored.Outlined.ShowChart,
                        modifier = Modifier.spectraRowPadding(),
                    )
                }
            } else {
                row {
                    FormPickerRow(
                        label = AppLocalization.string("Asset"),
                        options = portfolio.map { it.id },
                        selected = selectedHoldingKey,
                        optionTitle = { key ->
                            portfolio.firstOrNull { it.id == key }?.let { AppLocalization.format("%@ on %@", it.symbol, it.chainName) } ?: ""
                        },
                        onSelect = { selectedHoldingKey = it },
                    )
                }
                row {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().spectraRowPadding()) {
                        priceAlertConditions.forEachIndexed { index, condition ->
                            SegmentedButton(
                                selected = selectedCondition == condition,
                                onClick = { selectedCondition = condition },
                                shape = SegmentedButtonDefaults.itemShape(index, priceAlertConditions.size),
                            ) { Text(condition.displayName) }
                        }
                    }
                }
                row {
                    FormTextFieldRow(
                        value = targetPriceText,
                        onValueChange = { targetPriceText = it },
                        label = AppLocalization.format("Target Price (%@)", app.selectedFiatCurrency.code),
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                selectedCoin?.let { coin ->
                    row {
                        SpectraHintText(
                            AppLocalization.format("Current price: %@", app.amounts.formattedFiat(app.amounts.price(coin))),
                            modifier = Modifier.spectraRowPadding(),
                        )
                    }
                }
                formMessage?.let { message -> row { FormTextRow(message) } }
                row {
                    FilledTonalButton(
                        onClick = ::addAlert,
                        enabled = canAddAlert,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.rowVertical),
                    ) { Text(AppLocalization.string("Add Alert")) }
                }
            }
        }
        FormSection(title = AppLocalization.string("Active Alerts")) {
            val rules = app.priceAlerts.rules
            if (rules.isEmpty()) {
                row {
                    SpectraEmptyStateContent(
                        title = AppLocalization.string("No alerts configured yet"),
                        message = AppLocalization.string("Add a price rule to watch one of your portfolio assets."),
                        icon = Icons.Outlined.NotificationsOff,
                        modifier = Modifier.spectraRowPadding(),
                    )
                }
            } else {
                rules.forEach { alert ->
                    row {
                        AlertRow(
                            alert = alert,
                            onToggle = { editAlert(StateCommand.TogglePriceAlert(alert.id)) },
                            onRemove = { removingAlertId = alert.id },
                        )
                    }
                }
            }
        }
    }
    removingAlertId?.let { id ->
        AlertDialog(
            onDismissRequest = { removingAlertId = null },
            title = { Text(AppLocalization.string("Remove Alert")) },
            text = { Text(AppLocalization.string("This alert rule will be permanently removed.")) },
            confirmButton = {
                TextButton(onClick = {
                    removingAlertId = null
                    editAlert(StateCommand.RemovePriceAlert(id))
                }) { Text(AppLocalization.string("Remove"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removingAlertId = null }) { Text(AppLocalization.string("Cancel")) } },
        )
    }
}

@Composable
private fun AlertRow(alert: PriceAlertRule, onToggle: () -> Unit, onRemove: () -> Unit) {
    val app = LocalAppState.current
    val statusColor = alert.statusColor
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                Text(alert.titleText, style = MaterialTheme.typography.titleMedium)
                Text(alertTargetText(app, alert), style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
            Text(
                alert.statusText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = statusColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 78.dp)
                    .background(statusColor.copy(alpha = 0.18f), CircleShape)
                    .padding(horizontal = SpectraLayout.Space.s, vertical = SpectraLayout.Space.xs),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onToggle) {
                Text(AppLocalization.string(if (alert.isEnabled) "Pause" else "Resume"))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRemove) {
                Text(AppLocalization.string("Remove"), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** "Above $3,000": one sentence per condition, since languages put the price on either side of the word. */
private fun alertTargetText(app: AppState, alert: PriceAlertRule): String {
    val price = app.amounts.formattedFiat(app.amounts.alertTarget(alert))
    return when (alert.condition) {
        PriceAlertCondition.ABOVE -> AppLocalization.format("priceAlert.above_format", price)
        PriceAlertCondition.BELOW -> AppLocalization.format("priceAlert.below_format", price)
    }
}
