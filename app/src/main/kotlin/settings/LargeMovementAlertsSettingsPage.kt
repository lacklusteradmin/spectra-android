package dev.spectra.settings

import android.icu.text.NumberFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraStepper
import dev.spectra.components.SpectraSwitchRow
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.CoreReferenceTables
import dev.spectra.models.formatPercent
import uniffi.spectra_core.AppSettingUpdate
import java.text.ParsePosition

/** Alerts for a large swing in the portfolio's value: whether, and how large. */
@Composable
internal fun LargeMovementAlertsSettingsPage() {
    val app = LocalAppState.current
    val settings = app.appSettings
    val bounds = CoreReferenceTables.bounds
    val isOn = settings.useLargeMovementNotifications
    SpectraPage(title = AppLocalization.string("Large Movement Alerts")) {
        FormSection(title = AppLocalization.string("Notifications")) {
            row {
                SpectraSwitchRow(
                    title = AppLocalization.string("Large Portfolio Movement Alerts"),
                    checked = isOn,
                    onCheckedChange = { app.updateSetting(AppSettingUpdate.UseLargeMovementNotifications(it)) },
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                )
            }
        }
        FormSection(title = AppLocalization.string("Alert Controls")) {
            row {
                SpectraStepper(
                    text = AppLocalization.format(
                        "Large movement threshold: %@",
                        formatPercent(settings.largeMovementAlertPercentThreshold / 100),
                    ),
                    value = settings.largeMovementAlertPercentThreshold,
                    range = bounds.largeMovementPercentMin..bounds.largeMovementPercentMax,
                    onValueChange = { app.updateSetting(AppSettingUpdate.LargeMovementAlertPercentThreshold(it)) },
                    modifier = Modifier.formStepperPadding(),
                    enabled = isOn,
                )
            }
            row { MinimumMovementRow(settings.largeMovementAlertUsdThreshold, enabled = isOn) }
        }
    }
}

/**
 * The smallest movement, in US dollars, worth an alert. Committed as a
 * number field commits: on Done or when the field loses focus; text that is
 * not a number goes back to the stored figure.
 */
@Composable
private fun MinimumMovementRow(stored: Double, enabled: Boolean) {
    val app = LocalAppState.current
    val focus = LocalFocusManager.current
    val label = AppLocalization.string("Minimum movement (USD)")
    var text by remember { mutableStateOf(formatUngrouped(stored)) }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(stored) { if (!isFocused) text = formatUngrouped(stored) }

    fun commit() {
        val value = parseNumber(text)
        if (value == null) {
            text = formatUngrouped(stored)
            return
        }
        app.updateSetting(AppSettingUpdate.LargeMovementAlertUsdThreshold(value))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .widthIn(max = 160.dp)
                .onFocusChanged { state ->
                    if (isFocused && !state.isFocused) commit()
                    isFocused = state.isFocused
                },
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle.Default.merge(MaterialTheme.typography.bodyLarge).copy(textAlign = TextAlign.End),
            placeholder = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
    }
}

/** A figure without grouping, in the display language, as the field shows it. */
private fun formatUngrouped(value: Double): String =
    NumberFormat.getNumberInstance(AppLocalization.locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = 6
    }.format(value)

/**
 * What was typed, read with the keyboard's separator or the display
 * language's; `null` when it is not a number.
 */
private fun parseNumber(text: String): Double? {
    AmountPresentation.canonicalDecimalInput(text).toDoubleOrNull()?.let { return it }
    val trimmed = text.trim()
    val position = ParsePosition(0)
    val parsed = NumberFormat.getNumberInstance(AppLocalization.locale).parse(trimmed, position) ?: return null
    return if (position.index == trimmed.length) parsed.toDouble() else null
}
