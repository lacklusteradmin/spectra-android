package dev.spectra.send

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.spectra.app.AppState
import dev.spectra.app.LocalAppState
import dev.spectra.components.BalanceText
import dev.spectra.components.CoinBadge
import dev.spectra.components.FlowPageHeading
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraLabel
import dev.spectra.components.SpectraLayout
import dev.spectra.components.secondaryText
import dev.spectra.models.AmountPresentation
import dev.spectra.models.AppLocalization
import dev.spectra.models.artworkName
import dev.spectra.models.color
import dev.spectra.models.formatPercent
import dev.spectra.models.hasBalance
import dev.spectra.models.isNativeCoin
import uniffi.spectra_core.sendAmountShortcutPercentages

/**
 * Core's shortcuts, in order. A quote prices each; until then they show
 * disabled rather than appearing once it arrives.
 */
private val shortcutPercentages: List<UInt> by lazy { sendAmountShortcutPercentages() }

/** The amount at its full size, before more digits shrink it. */
private const val AMOUNT_FONT_SIZE = 56f

/** The composer's amount: typed, or a share of what the quote says can be sent. */
@Composable
internal fun SendAmountPage() {
    val app = LocalAppState.current
    val flow = app.sendFlow
    val presentation = SendComposerPresentation(app)
    SendAssetContextView(presentation)
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        FlowPageHeading(title = AppLocalization.string("How much are you sending?"))
        SendRecipientIdentityView(chain = presentation.selectedCoin?.chainId, address = flow.address, compact = true)
    }
    SpectraCard(elevated = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SpectraLayout.Space.xl, horizontal = SpectraLayout.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.l),
        ) {
            val coin = presentation.selectedCoin
            if (coin != null) CoinBadge(coin.artworkName, coin.symbol, coin.color, size = 44.dp)
            AmountField(app, presentation)
            Column(
                modifier = Modifier.padding(top = SpectraLayout.Space.s),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xs),
            ) {
                Text(AppLocalization.string("Available to send"), style = MaterialTheme.typography.bodySmall, color = secondaryText)
                MaximumText(app, presentation)
                // Why the maximum is not the balance: a coin pays its own fee.
                // A token's maximum is its balance.
                val balance = presentation.selectedCoinAmountText
                if (coin != null && coin.isNativeCoin && balance != null) {
                    BalanceText(
                        text = balance,
                        isHidden = app.preferences.hideBalances,
                        style = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                        color = secondaryText,
                        sentence = { AppLocalization.format("Wallet balance %@, less the network fee", it) },
                    )
                }
            }
            if (coin?.hasBalance == true) ShortcutButtons(app)
            AmountProblem(app, Modifier.fillMaxWidth())
        }
    }
}

/**
 * The amount with its unit beside it, centred together. The field is as wide
 * as its digits and their size steps down as they grow, so a long amount
 * stays on one line beside its unit; at large text sizes it wraps instead,
 * with the unit under it, and never cuts a digit.
 */
@Composable
private fun AmountField(app: AppState, presentation: SendComposerPresentation) {
    val flow = app.sendFlow
    val focus = remember { FocusRequester() }
    val isAccessibilitySize = LocalDensity.current.fontScale >= ACCESSIBILITY_FONT_SCALE
    // Eight digits at the full size, then smaller as more are typed, down to a
    // size that still fits an asset's full precision on the line.
    val digits = maxOf(flow.amount.length, 1)
    val fontSize = if (isAccessibilitySize) AMOUNT_FONT_SIZE else AMOUNT_FONT_SIZE * minOf(1f, maxOf(0.35f, 8f / digits))
    val style = MaterialTheme.typography.displayLarge.copy(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.15f).sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        fontFeatureSettings = "tnum",
    )
    val amountLabel = AppLocalization.string("Amount")
    // The flow owns the amount: Max, a scanned code and a reset write it
    // there, and what is typed goes back to it. Only a change the field did
    // not write itself is copied into it, so fast typing is never overwritten
    // by an older value on its way round.
    val fieldState = remember { TextFieldState(flow.amount) }
    var lastWritten by remember { mutableStateOf(flow.amount) }
    LaunchedEffect(flow.amount) {
        if (flow.amount != lastWritten) {
            lastWritten = flow.amount
            fieldState.setTextAndPlaceCursorAtEnd(flow.amount)
        }
    }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { text ->
            lastWritten = text
            flow.amount = text
        }
    }
    val field: @Composable (Modifier) -> Unit = { modifier ->
        BasicTextField(
            state = fieldState,
            modifier = modifier.focusRequester(focus).semantics { contentDescription = amountLabel },
            textStyle = style,
            lineLimits = if (isAccessibilitySize) TextFieldLineLimits.MultiLine() else TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorator = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (fieldState.text.isEmpty()) {
                        Text("0", style = style.copy(color = secondaryText))
                    }
                    inner()
                }
            },
        )
    }
    val unit: @Composable () -> Unit = {
        presentation.selectedCoin?.let { coin ->
            Text(
                coin.symbol,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = secondaryText,
                maxLines = 1,
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
    ) {
        if (isAccessibilitySize) {
            field(Modifier.fillMaxWidth())
            unit()
        } else {
            // The field is as wide as its digits; the whole row takes the tap,
            // as a full-width field would.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        focus.requestFocus()
                    },
                horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                field(Modifier.width(IntrinsicSize.Min).weight(1f, fill = false))
                unit()
            }
        }
        ApproximateValue(app, presentation)
    }
}

/**
 * The amount in the display currency once core has priced it. An asset with
 * no price says so in words; a quote still on its way keeps the line's room
 * without a value, so nothing below it moves.
 */
@Composable
private fun ApproximateValue(app: AppState, presentation: SendComposerPresentation) {
    val flow = app.sendFlow
    if (flow.amount.isBlank()) return
    val quoted = flow.quoteIsCurrent && app.sendQuoteForEnteredAmount != null
    val fiat = presentation.selectedCoinApproximateFiatText
    Text(
        fiat?.let { "≈ $it" } ?: AppLocalization.string("No price available"),
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        color = secondaryText,
        modifier = Modifier.alpha(if (quoted || fiat != null) 1f else 0f),
    )
}

/**
 * The fee-adjusted maximum at the asset's display precision, a balance like
 * any other; the exact figure is what Max fills in.
 */
@Composable
private fun MaximumText(app: AppState, presentation: SendComposerPresentation) {
    val style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")
    val maximum = app.sendShortcutAmount(100u)
    val coin = presentation.selectedCoin
    when {
        app.sendFlow.isPreparingPreview -> Text(AppLocalization.string("Estimating…"), style = style)
        app.sendFlow.quoteIsCurrent && maximum != null && coin != null -> BalanceText(
            text = app.amounts.formattedAssetAmount(maximum, coin.symbol, coin.id),
            isHidden = app.preferences.hideBalances,
            style = style,
        )
        else -> Text("—", style = style)
    }
}

@Composable
private fun ShortcutButtons(app: AppState) {
    val isAccessibilitySize = LocalDensity.current.fontScale >= ACCESSIBILITY_FONT_SCALE
    val rows = if (isAccessibilitySize) shortcutPercentages.chunked(2) else listOf(shortcutPercentages)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
                row.forEach { percentage -> PercentButton(app, percentage, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PercentButton(app: AppState, percentage: UInt, modifier: Modifier) {
    val flow = app.sendFlow
    val amount = if (flow.quoteIsCurrent) app.sendShortcutAmount(percentage) else null
    val description = if (percentage == 100u) {
        AppLocalization.string("Maximum after estimated fees")
    } else {
        AppLocalization.format("%lld percent of estimated maximum", percentage.toLong())
    }
    FilledTonalButton(
        onClick = { amount?.let { flow.amount = AmountPresentation.decimalFieldText(it) } },
        enabled = amount != null,
        modifier = modifier.heightIn(min = 40.dp).semantics { contentDescription = description },
    ) {
        Text(
            if (percentage == 100u) AppLocalization.string("Max") else formatPercent(percentage.toDouble() / 100.0),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What stands between this amount and Review, under the field it is about: a
 * malformed amount, then core's refusal of it, then a quote that did not come
 * back.
 */
@Composable
private fun AmountProblem(app: AppState, modifier: Modifier) {
    val flow = app.sendFlow
    val error = MaterialTheme.colorScheme.error
    val refusal = app.sendAmountRefusal
    val previewError = flow.previewError
    when {
        flow.amount.isNotEmpty() && !app.sendAmountIsValid -> SpectraLabel(
            text = AppLocalization.string("Enter a positive decimal amount within this asset's precision."),
            icon = Icons.Outlined.WarningAmber,
            color = error,
            modifier = modifier,
        )
        refusal != null -> SpectraLabel(text = refusal, icon = Icons.Outlined.WarningAmber, color = error, modifier = modifier)
        previewError != null -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            SpectraLabel(text = previewError, icon = Icons.Outlined.WarningAmber, color = error, modifier = Modifier.weight(1f))
            TextButton(onClick = flow::schedulePreview) { Text(AppLocalization.string("Retry")) }
        }
    }
}
