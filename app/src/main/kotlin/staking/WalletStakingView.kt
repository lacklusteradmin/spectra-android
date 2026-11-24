package dev.spectra.staking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.components.BalanceText
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDisclosure
import dev.spectra.components.SpectraGap
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowGroup
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.WalletPasswordField
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.CoreReferenceTables
import dev.spectra.models.coinSymbol
import dev.spectra.models.isNativeCoin
import dev.spectra.models.isWatchOnly
import dev.spectra.models.requiresPassword
import dev.spectra.send.sendSigningConfirmationMessage
import uniffi.spectra_core.SendStage
import uniffi.spectra_core.StakingAction
import uniffi.spectra_core.StakingChainEntry
import uniffi.spectra_core.WalletState

/**
 * One wallet's staking: its positions, and staking from it. A wallet's page
 * opens it where core offers staking for the wallet's network; there is no
 * other way in.
 */
@Composable
internal fun WalletStakingView(wallet: WalletState) {
    val app = LocalAppState.current
    val chain = wallet.chainId
    val staking = remember(wallet.id) { StakingState(chain, app.bridge).also { it.selectWallet(wallet.id) } }
    var showsValidators by remember { mutableStateOf(false) }
    var confirmsSigning by remember { mutableStateOf(false) }
    // Whether the signing confirmation asks for the password: decided when it
    // opens, so the field stays while it is typed in.
    var signingAsksPassword by remember { mutableStateOf(false) }
    var confirmsBroadcast by remember { mutableStateOf(false) }

    val current = app.wallet(staking.walletId)
    val requiresPassword = current?.signing?.requiresPassword ?: true
    val isWatchOnly = current?.signing?.isWatchOnly == true
    // The wallet's balance of the network's coin, as its rows show it.
    val availableToStake = app.availableSendCoins(staking.walletId).firstOrNull { it.isNativeCoin }
        ?.let { app.amounts.formattedAssetAmount(it.amount, it.symbol, it.id) }

    LaunchedEffect(staking) { staking.loadValidators() }
    LaunchedEffect(staking.walletId) { staking.loadWalletData() }
    LaunchedEffect(staking.session.id, staking.session.artifact?.revision) { staking.loadSavedArtifacts() }
    LaunchedEffect(staking.request?.id) { staking.request?.id?.let { staking.perform(it, app) } }
    LaunchedEffect(staking.session.artifact?.id, app.transactionRevision) { staking.session.refresh() }
    DisposableEffect(staking) { onDispose { staking.cancel() } }

    SpectraPage(title = AppLocalization.string("Staking")) {
        CoreReferenceTables.stakingEntry(chain)?.let { Mechanics(it) }
        if (isWatchOnly) {
            SpectraHintText(AppLocalization.string("staking.watch_only"))
        } else if (requiresPassword) {
            // The one place the password is typed; every step reads it.
            SpectraDetailCard {
                WalletPasswordField(staking.password)
                SpectraHintText(AppLocalization.string("staking.password_once"))
            }
        }
        if (staking.walletId.isNotEmpty()) {
            val progress = staking.session.progress
            if (progress != null) {
                StakingTransactionView(
                    staking = staking,
                    progress = progress,
                    onSign = {
                        signingAsksPassword = requiresPassword && staking.password.text.isEmpty()
                        confirmsSigning = true
                    },
                    onBroadcast = { confirmsBroadcast = true },
                )
            } else {
                StakingPositionsView(
                    staking = staking,
                    canSign = current?.signing?.isWatchOnly == false,
                    requiresPassword = requiresPassword,
                    hidesBalances = app.preferences.hideBalances,
                )
                Preparation(
                    staking = staking,
                    canBuild = current?.signing?.isWatchOnly == false && !(requiresPassword && staking.password.text.isEmpty()),
                    availableToStake = availableToStake,
                    hidesBalances = app.preferences.hideBalances,
                    chooseValidator = { showsValidators = true },
                )
                SavedTransactions(staking)
            }
        }
    }

    if (showsValidators) {
        StakingValidatorPicker(
            validators = staking.validators,
            isLoading = staking.isLoading,
            selected = staking.validatorId,
            onSelect = { staking.validatorId = it },
            onDismiss = { showsValidators = false },
        )
    }
    if (confirmsSigning) {
        val artifact = staking.session.artifact
        val message = artifact?.let { staged ->
            staged.staking?.let { intent ->
                val action = listOfNotNull(intent.action.localizedTitle, intent.validatorId?.let { staking.validatorName(it) })
                    .joinToString(" · ")
                sendSigningConfirmationMessage(staged, app.amounts, action)
            }
        }
        val passwordMissing = requiresPassword && staking.password.text.isEmpty()
        AlertDialog(
            onDismissRequest = { confirmsSigning = false },
            title = { Text(AppLocalization.string("Sign this transaction?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m)) {
                    if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
                    // Asked here only for a review resumed before the password
                    // was typed; otherwise the page's password signs.
                    if (signingAsksPassword) WalletPasswordField(staking.password)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmsSigning = false
                        staking.begin(StakingState.Operation.Sign)
                    },
                    enabled = !passwordMissing,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(AppLocalization.string("Sign Transaction")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmsSigning = false }) { Text(AppLocalization.string("Cancel")) }
            },
        )
    }
    if (confirmsBroadcast) {
        AlertDialog(
            onDismissRequest = { confirmsBroadcast = false },
            title = { Text(AppLocalization.string("Broadcast Transaction")) },
            text = { Text(AppLocalization.string("staking.broadcast_confirmation")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmsBroadcast = false
                        staking.begin(StakingState.Operation.Broadcast)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(AppLocalization.string("Broadcast Transaction")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmsBroadcast = false }) { Text(AppLocalization.string("Cancel")) }
            },
        )
    }
}

/** What staking means on this network: its mechanic, minimum, unbonding and how it works. */
@Composable
private fun Mechanics(entry: StakingChainEntry) {
    SpectraDetailCard(elevated = true) {
        Text(AppLocalization.string(entry.shortMechanic), style = MaterialTheme.typography.titleMedium)
        SpectraValueRow(label = AppLocalization.string("Minimum Stake"), value = AppLocalization.string(entry.minimumStake))
        SpectraValueRow(label = AppLocalization.string("Unbonding"), value = AppLocalization.string(entry.unbondingPeriod))
        SpectraDisclosure(title = AppLocalization.string("How it works")) {
            Text(AppLocalization.string(entry.explanation), style = MaterialTheme.typography.bodyMedium, color = secondaryText)
        }
    }
}

/** The action being prepared — a new stake, or one on a position — and building it. */
@Composable
private fun Preparation(
    staking: StakingState,
    canBuild: Boolean,
    availableToStake: String?,
    hidesBalances: Boolean,
    chooseValidator: () -> Unit,
) {
    val enabled = !staking.isBusy
    val rules = staking.rules
    SpectraDetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(staking.action.localizedTitle, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (staking.positionId != null) {
                FilledTonalButton(onClick = { staking.startStake() }, enabled = enabled) {
                    Text(AppLocalization.string("staking.new_stake"))
                }
            }
        }
        staking.positionId?.let { position ->
            SelectionContainer {
                Text(position, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = secondaryText)
            }
        }
        if (rules.validatorRequired) {
            OutlinedTextField(
                value = staking.validatorId,
                onValueChange = { staking.validatorId = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(AppLocalization.string("staking.validator_identifier")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
            staking.validatorName(staking.validatorId)?.let { name ->
                SpectraLabel(
                    text = name,
                    icon = Icons.Outlined.Verified,
                    color = MaterialTheme.colorScheme.onSurface,
                    iconColor = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            FilledTonalButton(onClick = chooseValidator, enabled = enabled) {
                Text(AppLocalization.string("staking.choose_validator"))
            }
        }
        if (rules.amountAllowed) {
            OutlinedTextField(
                value = staking.amount,
                onValueChange = { staking.amount = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(AppLocalization.string(if (rules.amountRequired) "Amount" else "staking.optional_amount")) },
                suffix = { Text(staking.chain.coinSymbol, color = secondaryText) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            // What there is to stake. No Max: what a stake must leave for its
            // fee and the network's reserve differs by network, and core does
            // not yet answer it, so the figure is the user's.
            if (staking.action == StakingAction.STAKE && availableToStake != null) {
                BalanceText(
                    text = availableToStake,
                    isHidden = hidesBalances,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                    sentence = { AppLocalization.format("Available: %@", it) },
                )
            }
            if (!rules.amountRequired) SpectraHintText(AppLocalization.string("staking.full_withdrawal"))
        }
        if (rules.lockupRequired) {
            OutlinedTextField(
                value = staking.lockupSeconds,
                onValueChange = { staking.lockupSeconds = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                label = { Text(AppLocalization.string("staking.dissolve_delay")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            SpectraHintText(AppLocalization.string("staking.dissolve_delay_help"))
        }
        SpectraHintText(AppLocalization.string("staking.build_explanation"))
        Button(
            onClick = { staking.begin(StakingState.Operation.Build) },
            enabled = enabled && canBuild,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Build, contentDescription = null, modifier = Modifier.size(18.dp))
            SpectraGap()
            Text(AppLocalization.string("Build Transaction"))
        }
        if (staking.isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.5.dp)
        }
        staking.stepError?.let { StakingErrorText(it) }
    }
}

/** Reviews built or signed earlier on this network, to resume. */
@Composable
private fun SavedTransactions(staking: StakingState) {
    if (staking.savedArtifacts.isEmpty()) return
    SpectraRowGroup(
        items = staking.savedArtifacts,
        title = AppLocalization.string("staking.saved_transactions"),
        dividerInset = SpectraLayout.rowHorizontal,
    ) { saved ->
        Column(
            modifier = Modifier
                .clickable(enabled = !staking.isBusy) { staking.begin(StakingState.Operation.Resume(saved.id)) }
                .spectraRowPadding(),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
        ) {
            Text(
                saved.staking?.action?.localizedTitle ?: AppLocalization.string("Staking"),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                AmountPresentation.exactAmount(saved.amount, saved.symbol),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
            )
            Text(
                AppLocalization.string(if (saved.stage == SendStage.PREPARED) "Awaiting signing" else "Signed"),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
            )
        }
    }
}
