package dev.spectra.wallets.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Route
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.entry
import dev.spectra.models.gasTokenSymbol
import dev.spectra.models.isWatchOnly
import dev.spectra.wallets.title
import uniffi.spectra_core.WalletAction
import uniffi.spectra_core.WalletOperation
import uniffi.spectra_core.WalletState
import uniffi.spectra_core.WalletTrustLine
import uniffi.spectra_core.WalletTrustLines

/** One trust line to open or remove. */
internal data class TrustLineChangeRoute(val walletId: String, val asset: String, val remove: Boolean) : Route

/**
 * An XRP Ledger or Stellar wallet's trust lines, as core reads them from a
 * node: the issued assets the wallet can hold. Trusting another asset and
 * removing an empty line are transactions core builds; their page signs and
 * broadcasts them through the same stages as a send.
 */
@Composable
internal fun WalletTrustLinesView(wallet: WalletState) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val lines = remember(wallet.id) { ToolRead<WalletTrustLines>() }
    // Kept while the change's page is open, so Back returns to what was typed.
    var assetInput by rememberSaveable { mutableStateOf("") }
    suspend fun load() = lines.load { app.bridge.ready().walletTrustLines(wallet.id) }
    LaunchedEffect(wallet.id) { load() }

    // What the network's issued-asset protocol identifies an asset by.
    val identifierPrompt = wallet.chainId.entry?.tokenStandards?.firstOrNull()?.identifierPrompt ?: "Token Identifier"

    SpectraPage(title = WalletAction.TRUST_LINES.title, onRefresh = { load() }) {
        ToolReadSections(lines) { read ->
            if (read.lines.isEmpty()) {
                WalletToolEmptySection(AppLocalization.string("No trust lines."))
            } else {
                ToolListSection(
                    items = read.lines,
                    footer = AppLocalization.format(
                        "Each trust line locks %@ %@ of reserve while it exists.",
                        read.reservePerLine,
                        wallet.chainId.gasTokenSymbol,
                    ),
                ) { line ->
                    TrustLineRow(wallet, line) { navigator.push(TrustLineChangeRoute(wallet.id, line.asset, remove = true)) }
                }
            }
            if (!wallet.signing.isWatchOnly) {
                ToolSection(
                    title = AppLocalization.string("Trust an Asset"),
                    footer = AppLocalization.string("A wallet can receive an issued asset only once it trusts the asset's issuer for it."),
                ) {
                    OutlinedTextField(
                        value = assetInput,
                        onValueChange = { assetInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(AppLocalization.string(identifierPrompt)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    )
                    FilledTonalButton(
                        onClick = { navigator.push(TrustLineChangeRoute(wallet.id, assetInput.trim(), remove = false)) },
                        enabled = assetInput.isNotBlank(),
                    ) { Text(AppLocalization.string("Trust Asset")) }
                }
            }
        }
    }
}

@Composable
private fun TrustLineRow(wallet: WalletState, line: WalletTrustLine, remove: () -> Unit) {
    Column(modifier = Modifier.spectraRowPadding(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                line.code,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                AmountPresentation.localizedDecimal(line.balance),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
        }
        IdentifierText(line.issuer, style = MaterialTheme.typography.labelSmall)
        if (!line.authorized) {
            SpectraLabel(
                text = AppLocalization.string("The issuer has not authorized this wallet to hold it."),
                icon = Icons.Outlined.Block,
                color = SpectraColors.semantic.warning,
            )
        }
        if (line.frozen) {
            SpectraLabel(
                text = AppLocalization.string("The issuer has frozen this line."),
                icon = Icons.Outlined.AcUnit,
                color = SpectraColors.semantic.warning,
            )
        }
        val blocked = line.removalBlocked
        if (blocked != null) {
            SpectraLabel(text = AppLocalization.string(blocked), icon = Icons.Outlined.Lock, color = secondaryText)
        } else if (!wallet.signing.isWatchOnly) {
            ToolRowButton(text = AppLocalization.string("Remove Trust Line"), onClick = remove, destructive = true)
        }
    }
}

/** Building, signing and broadcasting one trust line change. */
@Composable
internal fun TrustLineChangeView(wallet: WalletState, asset: String, remove: Boolean) {
    val app = LocalAppState.current
    WalletOperationPage(
        wallet = wallet,
        title = AppLocalization.string(if (remove) "Remove Trust Line" else "Trust Asset"),
        authenticationReason = AppLocalization.format("Authenticate to change trust lines of %@", wallet.name),
        submittedText = AppLocalization.string("Submitted. The trust line changes once it confirms."),
        build = {
            val service = app.bridge.ready()
            if (remove) service.buildRemoveTrustLine(wallet.id, asset) else service.buildTrustAsset(wallet.id, asset)
        },
    ) { artifact ->
        when (val operation = artifact?.operation) {
            is WalletOperation.TrustAsset -> TrustLineSummary(
                wallet = wallet,
                asset = operation.asset,
                reserveTitle = AppLocalization.string("Reserve Locked"),
                reserve = operation.reserve,
                note = AppLocalization.string(
                    "The line accepts any amount of this asset from now on; removing it later frees the reserve.",
                ),
            )
            is WalletOperation.RemoveTrustLine -> TrustLineSummary(
                wallet = wallet,
                asset = operation.asset,
                reserveTitle = AppLocalization.string("Reserve Freed"),
                reserve = operation.reserve,
                note = AppLocalization.string("The wallet stops accepting this asset; trust it again to receive it."),
            )
            else -> Unit
        }
    }
}

@Composable
private fun TrustLineSummary(wallet: WalletState, asset: String, reserveTitle: String, reserve: String, note: String) {
    val app = LocalAppState.current
    ToolSection(footer = note) {
        SelectionContainer {
            Text(asset, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        }
        SpectraValueRow(label = reserveTitle, value = app.amounts.formattedNativeAmount(reserve, wallet.chainId))
    }
}
