package dev.spectra.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraSwitchRow
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.formatPercent
import uniffi.spectra_core.AppSettingUpdate
import uniffi.spectra_core.TorStatus

/** Compact pill for Tor's state: the settings row and the dashboard's toolbar. */
@Composable
fun TorStatusBadge(status: TorStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        TorStatus.Stopped -> secondaryText
        is TorStatus.Bootstrapping -> SpectraColors.semantic.warning
        TorStatus.Ready -> SpectraColors.semantic.success
        is TorStatus.Error -> SpectraColors.semantic.failure
    }
    val label = when (status) {
        TorStatus.Stopped -> AppLocalization.string("Off")
        is TorStatus.Bootstrapping ->
            if (status.percent > 0u) formatPercent(status.percent.toDouble() / 100.0) else AppLocalization.string("Starting")
        TorStatus.Ready -> AppLocalization.string("On")
        is TorStatus.Error -> AppLocalization.string("Error")
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = SpectraLayout.Space.s, vertical = SpectraLayout.Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
    ) {
        if (status is TorStatus.Bootstrapping) {
            CircularProgressIndicator(modifier = Modifier.size(10.dp), color = color, strokeWidth = 1.5.dp)
        } else {
            Box(Modifier.size(6.dp).background(color, CircleShape))
        }
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

/** Tor: whether it runs, how far it has got, and whether through the user's own proxy. */
@Composable
internal fun TorSettingsPage() {
    val app = LocalAppState.current
    val settings = app.appSettings
    SpectraPage(title = AppLocalization.string("Tor Network")) {
        TorMainSection()
        if (settings.torEnabled) ConnectionModeSection()
        // What the built-in client does; a proxy of the user's is theirs to
        // vouch for.
        if (!settings.torUseCustomProxy) {
            FormSection(title = AppLocalization.string("About")) {
                row { FormValueRow(AppLocalization.string("Tor client"), AppLocalization.string("tor.client.arti")) }
                row { FormValueRow(AppLocalization.string("Stream isolation"), AppLocalization.string("Per connection")) }
                row { FormValueRow(AppLocalization.string("Onion routing hops"), "3") }
            }
        }
    }
}

@Composable
private fun TorMainSection() {
    val app = LocalAppState.current
    val status = app.tor.status
    FormSection(title = AppLocalization.string("Tor Network"), footer = AppLocalization.string("tor.footer.android")) {
        row {
            SpectraSwitchRow(
                title = AppLocalization.string("Enable Tor"),
                checked = app.appSettings.torEnabled,
                onCheckedChange = { app.updateSetting(AppSettingUpdate.TorEnabled(it)) },
                icon = Icons.Outlined.VpnLock,
            )
        }
        row {
            Column(
                modifier = Modifier.spectraRowPadding(),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(AppLocalization.string("Status"), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TorStatusBadge(status)
                }
                if (status is TorStatus.Bootstrapping) {
                    LinearProgressIndicator(
                        progress = { status.percent.toFloat() / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (status is TorStatus.Error) {
                    Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3)
                }
            }
        }
        if (status is TorStatus.Error) {
            row { FormActionRow(AppLocalization.string("Reconnect"), onClick = { app.tor.reconnect() }, icon = Icons.Outlined.Sync) }
        }
    }
}

@Composable
private fun ConnectionModeSection() {
    val app = LocalAppState.current
    val settings = app.appSettings
    val focus = LocalFocusManager.current
    var editingProxyAddress by rememberSaveable { mutableStateOf(settings.torCustomProxyAddress) }

    // Core parses the address and refuses one that is not a SOCKS5 URL; a
    // stored change reconnects the proxy.
    fun applyProxyAddress() {
        focus.clearFocus()
        val trimmed = editingProxyAddress.trim()
        if (trimmed.isEmpty()) return
        app.updateSetting(AppSettingUpdate.TorCustomProxyAddress(trimmed))
    }

    FormSection(
        title = AppLocalization.string("Connection Mode"),
        footer = AppLocalization.string(
            if (settings.torUseCustomProxy) {
                "Points all traffic at your own SOCKS5 proxy (e.g. Orbot on port 9150). Arti is not started."
            } else {
                "Uses the built-in Arti Tor client. No external app required."
            },
        ),
    ) {
        row {
            SpectraSwitchRow(
                title = AppLocalization.string("Use Custom SOCKS5 Proxy"),
                checked = settings.torUseCustomProxy,
                onCheckedChange = { app.updateSetting(AppSettingUpdate.TorUseCustomProxy(it)) },
                icon = Icons.Outlined.Lan,
            )
        }
        if (settings.torUseCustomProxy) {
            row {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = SpectraLayout.rowHorizontal, vertical = SpectraLayout.rowVertical),
                    verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
                ) {
                    OutlinedTextField(
                        value = editingProxyAddress,
                        onValueChange = { editingProxyAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(AppLocalization.string("SOCKS5 Address")) },
                        placeholder = { Text("socks5://127.0.0.1:9150", color = secondaryText) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { applyProxyAddress() }),
                    )
                    if (editingProxyAddress != settings.torCustomProxyAddress) {
                        TextButton(onClick = { applyProxyAddress() }) {
                            Text(AppLocalization.string("Apply"), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
