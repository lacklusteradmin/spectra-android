package dev.spectra.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import dev.spectra.components.SpectraHintText
import dev.spectra.components.SpectraLayout
import dev.spectra.components.secondaryText
import dev.spectra.models.AppLocalization
import uniffi.spectra_core.PaymentMemo
import uniffi.spectra_core.PaymentMemoKind

/**
 * The destination tag or memo a payment carries to a recipient that shares
 * its account, such as an exchange's deposit address. Offered where core's
 * `paymentMemoKinds(chain)` names any; core checks the value and refuses a
 * send without one to an account that asks for it.
 */
@Composable
internal fun SendPaymentMemoField(
    kinds: List<PaymentMemoKind>,
    kind: PaymentMemoKind?,
    onKindChange: (PaymentMemoKind) -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
) {
    val selected = kind?.takeIf { it in kinds } ?: kinds.first()
    // The kind core reads is always one this network takes.
    LaunchedEffect(kinds) { if (kind != selected) onKindChange(selected) }
    Column(verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
        SendComposerSectionLabel(
            AppLocalization.string(if (kinds == listOf(PaymentMemoKind.DESTINATION_TAG)) "Destination Tag" else "Memo"),
        )
        if (kinds.size > 1) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == selected,
                        onClick = { onKindChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                    ) { Text(option.localizedTitle) }
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(AppLocalization.string("Optional")) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (selected == PaymentMemoKind.MEMO_TEXT || selected == PaymentMemoKind.COMMENT) KeyboardType.Text else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
        )
        SpectraHintText(
            AppLocalization.string(
                "An exchange or other shared account gives one to tell whose deposit it is. Leave it empty unless the recipient gave you one.",
            ),
        )
    }
}

/** What the field is called on review and in the picker. */
internal val PaymentMemoKind.localizedTitle: String
    get() = AppLocalization.string(
        when (this) {
            PaymentMemoKind.DESTINATION_TAG -> "Destination Tag"
            PaymentMemoKind.MEMO_TEXT -> "Text Memo"
            PaymentMemoKind.MEMO_ID -> "ID Memo"
            PaymentMemoKind.COMMENT -> "Comment"
        },
    )

/** A reviewed transaction's destination tag or memo, exactly as core built it. */
@Composable
internal fun PaymentMemoRow(memo: PaymentMemo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Text(memo.kind.localizedTitle, style = MaterialTheme.typography.bodyMedium, color = secondaryText, modifier = Modifier.weight(1f))
        SelectionContainer {
            Text(memo.value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
        }
    }
}
