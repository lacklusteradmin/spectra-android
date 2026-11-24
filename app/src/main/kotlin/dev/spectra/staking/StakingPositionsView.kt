package dev.spectra.staking

import android.icu.text.NumberFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraDivider
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraSearchField
import dev.spectra.components.SpectraValueRow
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.DateStyle
import dev.spectra.models.TimeStyle
import dev.spectra.models.formatUnixDate
import dev.spectra.models.gasTokenSymbol
import uniffi.spectra_core.StakingPosition
import uniffi.spectra_core.StakingValidator
import uniffi.spectra_core.formatNativeAmount

/** The wallet's positions, as core reads them, with what each lets the wallet do next. */
@Composable
internal fun StakingPositionsView(
    staking: StakingState,
    canSign: Boolean,
    requiresPassword: Boolean,
    /** Hide Balances is on: the positions' amounts are masked. */
    hidesBalances: Boolean,
) {
    val enabled = !staking.isBusy
    SpectraDetailCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AppLocalization.string("staking.positions"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (staking.isBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        OutlinedTextField(
            value = staking.extraTarget,
            onValueChange = { staking.extraTarget = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text(AppLocalization.string("staking.additional_pool")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        FilledTonalButton(
            onClick = { staking.begin(StakingState.Operation.Positions) },
            enabled = enabled && !(
                staking.rules.positionsRequireAuthorization &&
                    (!canSign || (requiresPassword && staking.password.text.isEmpty()))
                ),
        ) { Text(AppLocalization.string("staking.refresh_positions")) }
        staking.positionsError?.let { StakingErrorText(it) }
        if (staking.positions.isEmpty()) {
            Text(
                AppLocalization.string(if (staking.hasLoadedPositions) "staking.no_positions" else "staking.positions_not_loaded"),
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText,
            )
        } else {
            staking.positions.forEach { position ->
                SpectraDivider()
                PositionRow(staking, position, canSign = canSign && enabled, hidesBalances = hidesBalances)
            }
        }
    }
}

@Composable
private fun PositionRow(staking: StakingState, position: StakingPosition, canSign: Boolean, hidesBalances: Boolean) {
    fun amount(units: String): String =
        "${formatNativeAmount(staking.chain, units)?.let { AmountPresentation.localizedDecimal(it) } ?: "—"} ${staking.chain.gasTokenSymbol}"
    fun time(unix: ULong): String = formatUnixDate(unix.toDouble(), DateStyle.ABBREVIATED, TimeStyle.SHORTENED)

    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        Text(position.status.localizedTitle, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
        SelectionContainer {
            Text(
                position.id,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = secondaryText,
            )
        }
        SpectraValueRow(
            label = AppLocalization.string("staking.staked_balance"),
            value = amount(position.stakedAmountSmallestUnit),
            isHidden = hidesBalances,
        )
        SpectraValueRow(
            label = AppLocalization.string("staking.unbonding_balance"),
            value = amount(position.unbondingAmountSmallestUnit),
            isHidden = hidesBalances,
        )
        SpectraValueRow(
            label = AppLocalization.string("staking.withdrawable_balance"),
            value = amount(position.withdrawableAmountSmallestUnit),
            isHidden = hidesBalances,
        )
        position.claimableRewardsSmallestUnit?.let {
            SpectraValueRow(label = AppLocalization.string("staking.rewards"), value = amount(it), isHidden = hidesBalances)
        }
        position.pendingRewardsSmallestUnit?.let {
            SpectraValueRow(label = AppLocalization.string("staking.pending_rewards"), value = amount(it), isHidden = hidesBalances)
        }
        position.rewardsUnlockTimeUnix?.let {
            SpectraValueRow(label = AppLocalization.string("staking.rewards_unlock_time"), value = time(it))
        }
        position.unlockTimeUnix?.let {
            SpectraValueRow(label = AppLocalization.string("staking.unlock_time"), value = time(it))
        }
        position.unlockEpoch?.let {
            Text(
                AppLocalization.format("staking.unlock_epoch", it.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryText,
            )
        }
        if (position.availableActions.isNotEmpty()) {
            // Side by side while they fit, wrapping onto the next line otherwise.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
            ) {
                position.availableActions.forEach { action ->
                    FilledTonalButton(onClick = { staking.selectPosition(position, action) }, enabled = canSign) {
                        Text(action.localizedTitle)
                    }
                }
            }
        }
    }
}

/** A staking failure, where the step that raised it is. */
@Composable
internal fun StakingErrorText(message: String) {
    SpectraLabel(
        text = message,
        icon = Icons.Outlined.WarningAmber,
        color = SpectraColors.semantic.failure,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The network's validators to stake with, searchable by name or identifier,
 * the chosen one checked. A network can list thousands, so the list is lazy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StakingValidatorPicker(
    validators: List<StakingValidator>,
    isLoading: Boolean,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = if (search.isEmpty()) {
        validators
    } else {
        validators.filter { it.displayName.contains(search, ignoreCase = true) || it.identifier.contains(search, ignoreCase = true) }
    }
    // Opened full height: a list long enough to search has no half state.
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = SpectraLayout.screenHorizontal)
                .padding(bottom = SpectraLayout.screenBottom),
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AppLocalization.string("Validators"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) { Text(AppLocalization.string("Done")) }
            }
            SpectraSearchField(search, { search = it }, AppLocalization.string("Search"))
            when {
                // Loading is not empty: "No validators" waits for the answer.
                isLoading && validators.isEmpty() -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
                filtered.isEmpty() -> Text(
                    AppLocalization.string("staking.no_validators"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = secondaryText,
                )
                else -> SpectraCard {
                    LazyColumn {
                        itemsIndexed(filtered, key = { _, validator -> validator.identifier }) { index, validator ->
                            if (index > 0) SpectraDivider(startInset = SpectraLayout.rowHorizontal)
                            ValidatorRow(validator, isSelected = validator.identifier == selected) {
                                onSelect(validator.identifier)
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidatorRow(validator: StakingValidator, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs)) {
            Text(validator.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                validator.identifier,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            validator.commission?.let {
                Text(
                    AppLocalization.format("staking.commission_format", commissionPercent(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryText,
                )
            }
        }
        if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** A commission as a percent with up to two decimals, in the display locale. */
private fun commissionPercent(fraction: Double): String =
    NumberFormat.getPercentInstance(AppLocalization.locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(fraction)
