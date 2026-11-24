package dev.spectra.wallets

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import dev.spectra.components.SpectraDetailCard
import dev.spectra.components.SpectraErrorText
import dev.spectra.components.SpectraHintText
import dev.spectra.models.AppLocalization

/**
 * Where a restored or watched wallet's scan starts: a Monero wallet's, or a
 * Zcash wallet's for its shielded funds. Blank reads a Polyseed's birthday,
 * or scans from the first block the wallet could have received at.
 */
@Composable
internal fun RestoreHeightCard(draft: WalletImportDraft) {
    SpectraDetailCard {
        Text(
            AppLocalization.string("Restore Height (Optional)"),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        OutlinedTextField(
            value = draft.restoreHeightInput,
            onValueChange = { draft.restoreHeightInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(AppLocalization.string("Block height")) },
            singleLine = true,
            isError = !draft.isRestoreHeightValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (draft.isRestoreHeightValid) {
            SpectraHintText(
                AppLocalization.string(
                    "The scan starts here and cannot find funds received earlier. Leave it blank to use the phrase's creation date when it records one (a Polyseed), or to scan from the first block the wallet could have received at, which takes longest.",
                ),
            )
        } else {
            SpectraErrorText(AppLocalization.string("A restore height is a whole block number."))
        }
    }
}
