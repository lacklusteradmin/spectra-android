package dev.spectra.settings

import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.resetSelectedData
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraSwitchRow
import dev.spectra.models.AppLocalization
import dev.spectra.models.detail
import dev.spectra.models.resetScopesInOrder
import dev.spectra.models.title
import kotlinx.coroutines.launch
import uniffi.spectra_core.ResetScope

/**
 * What to remove from this device. Nothing is chosen until the user chooses
 * it, and every reset is confirmed once more before anything is deleted.
 */
@Composable
internal fun ResetWalletWarningPage(route: SettingsRoute.ResetWallet) {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    var selectedScopes by remember { mutableStateOf(emptySet<ResetScope>()) }
    var isConfirming by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun reset() {
        isResetting = true
        // The app's scope, not the page's: a reset half done because the
        // page closed under it would leave data the user asked to delete.
        app.scope.launch {
            errorMessage = app.resetSelectedData(selectedScopes)
            isResetting = false
            if (errorMessage == null) navigator.dismiss(route)
        }
    }

    SpectraPage(title = AppLocalization.string("Reset Wallet")) {
        SpectraCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SpectraLayout.cardPadding),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
            ) {
                Text(
                    AppLocalization.string(
                        "Choose which categories to remove from this device. Selected items are deleted locally and some options also clear data in secure storage.",
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    Text(
                        AppLocalization.string(
                            "You must have your seed phrase backed up. Without it, you cannot recover your funds after reset.",
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        FormSection(
            title = AppLocalization.string("Choose What To Reset"),
            footer = if (selectedScopes.isEmpty()) AppLocalization.string("Select at least one category to enable reset.") else null,
        ) {
            resetScopesInOrder.forEach { scope ->
                row {
                    SpectraSwitchRow(
                        title = scope.title,
                        subtitle = scope.detail,
                        checked = scope in selectedScopes,
                        onCheckedChange = { isSelected ->
                            selectedScopes = if (isSelected) selectedScopes + scope else selectedScopes - scope
                        },
                    )
                }
            }
        }
        SpectraCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SpectraLayout.cardPadding),
                verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s),
            ) {
                Button(
                    onClick = { isConfirming = true },
                    enabled = selectedScopes.isNotEmpty() && !isResetting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(AppLocalization.string("Reset Selected Data")) }
                errorMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (isConfirming) {
        AlertDialog(
            onDismissRequest = { isConfirming = false },
            title = { Text(AppLocalization.string("Delete the selected data?")) },
            text = { Text(confirmationMessage(selectedScopes)) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirming = false
                    reset()
                }) { Text(AppLocalization.string("Reset Selected Data"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { isConfirming = false }) { Text(AppLocalization.string("Cancel")) } },
        )
    }
}

/** The chosen categories by name, in the page's order, and that this cannot be undone. */
private fun confirmationMessage(selected: Set<ResetScope>): String {
    val titles = resetScopesInOrder.filter { it in selected }.map { it.title }
    return AppLocalization.format("reset.confirm.message_format", ListFormatter.getInstance(AppLocalization.locale).format(titles))
}
