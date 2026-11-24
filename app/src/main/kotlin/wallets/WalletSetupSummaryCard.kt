package dev.spectra.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.spectra.app.AppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.app.readOrNull
import dev.spectra.app.reportCommandError
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.spectra_core.AppSettingUpdate
import uniffi.spectra_core.CapabilityCoverage
import uniffi.spectra_core.Chain
import uniffi.spectra_core.StateCommand
import uniffi.spectra_core.WalletSetupLimit
import uniffi.spectra_core.WalletSetupSummary

/**
 * The setup's last page: what a wallet on the network will do once added,
 * its limits, and the endpoints its first refresh reads from — all core's —
 * with the way to change those endpoints before the address goes anywhere.
 */
@Composable
internal fun WalletSetupSummaryCard(app: AppState, chain: Chain) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<WalletSetupSummary?>(null) }
    suspend fun reload() {
        summary = readOrNull { app.bridge.ready().walletSetupSummary(chain) }
    }
    // Read again whenever settings change: an endpoint added, or the switch
    // below, changes whom the first refresh asks.
    LaunchedEffect(chain, app.appSettings) { reload() }
    val shown = summary ?: return
    SpectraDetailCard(title = AppLocalization.string("What This Wallet Does")) {
        CapabilityRow(AppLocalization.string("Balance"), shown.balance)
        CapabilityRow(AppLocalization.string("History"), shown.history)
        shown.tokenDiscovery?.let { CapabilityRow(AppLocalization.string("Token Discovery"), it) }
        Row {
            Text(AppLocalization.string("Staking"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                AppLocalization.string(if (shown.staking) "Available" else "Not available"),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText,
            )
        }
        shown.limits.forEach { limit ->
            SpectraLabel(text = limit.explanation, icon = Icons.Outlined.Info, color = secondaryText)
        }
        SpectraDivider()
        Text(
            AppLocalization.string("Its first refresh reads from"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        if (shown.endpoints.isEmpty()) {
            SpectraHintText(AppLocalization.string("No endpoint yet: nothing is read until you add one."))
        }
        shown.endpoints.forEach { endpoint ->
            Column {
                SelectionContainer {
                    Text(
                        endpoint.endpoint,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 2,
                    )
                }
                Text(
                    AppLocalization.string(if (endpoint.isBuiltIn) "Built-In" else "Custom"),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryText,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AppLocalization.string("Use only my endpoints"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = shown.customEndpointsOnly,
                onCheckedChange = { value ->
                    // Committed before the summary is read again, so the list
                    // shown is the one core now holds.
                    scope.launch {
                        try {
                            app.stateCommands.apply(StateCommand.SetAppSetting(AppSettingUpdate.CustomEndpointsOnly(chain, value)))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            app.reportCommandError(error)
                        }
                        reload()
                    }
                },
            )
        }
        TextButton(onClick = { navigator.push(Routes.AddCustomEndpoint(chain)) }) {
            Text(AppLocalization.string("Add Endpoint"), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun CapabilityRow(title: String, coverage: CapabilityCoverage) {
    Row {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            coverage.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (coverage == CapabilityCoverage.CONFIGURED) secondaryText else SpectraColors.semantic.warning,
        )
    }
}

val CapabilityCoverage.title: String
    get() = AppLocalization.string(
        when (this) {
            CapabilityCoverage.CONFIGURED -> "Available"
            CapabilityCoverage.NEEDS_CUSTOM_ENDPOINT -> "Needs your endpoint"
            CapabilityCoverage.UNAVAILABLE -> "Not available"
        },
    )

val WalletSetupLimit.explanation: String
    get() = AppLocalization.string(
        when (this) {
            WalletSetupLimit.ACCOUNT_RESERVE ->
                "The account exists only once it holds the network's reserve; a smaller first payment fails."
            WalletSetupLimit.TOKEN_BEARING_INPUTS_UNTOUCHED ->
                "ADA held beside native tokens cannot pay; only pure-ADA inputs are spent."
            WalletSetupLimit.SHIELDED_SCAN ->
                "Shielded funds are found by scanning blocks on this device from the restore height on; only a wallet restored from its seed phrase holds them."
            WalletSetupLimit.SCANS_ON_DEVICE ->
                "Balance and history come from scanning blocks on this device, from the restore height on."
        },
    )

/** A labelled value that opens a menu of the choices: iOS's menu picker. */
@Composable
private fun <T> ChoiceRow(
    title: String,
    value: String,
    choices: List<Pair<String, T>>,
    selected: T?,
    enabled: Boolean,
    onChoose: (T) -> Unit,
) {
    var isChoosing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { isChoosing = true }, enabled = enabled) {
                Text(value, color = secondaryText)
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = secondaryText)
            }
            DropdownMenu(expanded = isChoosing, onDismissRequest = { isChoosing = false }) {
                choices.forEach { (label, choice) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = if (choice == selected) {
                            { Icon(Icons.Outlined.CheckCircle, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            isChoosing = false
                            onChoose(choice)
                        },
                    )
                }
            }
        }
    }
}
