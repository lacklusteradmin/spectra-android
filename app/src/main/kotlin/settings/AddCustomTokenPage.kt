package dev.spectra.settings

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraStepper
import dev.spectra.components.breakableAnywhere
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainCatalog
import dev.spectra.models.CoreReferenceTables
import dev.spectra.models.displayName
import dev.spectra.models.entry
import kotlinx.coroutines.launch
import uniffi.spectra_core.Chain
import uniffi.spectra_core.TokenStandard

/**
 * Teaching the wallet a token the catalog does not ship, or editing one it
 * was taught. A token keeps the network and protocol it was added under.
 */
@Composable
internal fun AddCustomTokenPage(route: SettingsRoute.TokenForm) {
    val tokens = LocalAppState.current.tokenPreferences
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val editing = route.editing
    var selectedChain by rememberSaveable {
        mutableStateOf(if (editing != null) editing.hostingChain else ChainCatalog.tokenHostingChains.firstOrNull())
    }
    // The protocol the token is added under. Chosen, not guessed, where the
    // network has several: an ERC-20 and a BEP-20 contract look alike, and a
    // TRC-10 ID is not a TRC-20 address.
    var selectedStandard by rememberSaveable { mutableStateOf(editing?.token?.tokenStandard) }
    var symbolInput by rememberSaveable { mutableStateOf(editing?.token?.symbol ?: "") }
    var nameInput by rememberSaveable { mutableStateOf(editing?.token?.name ?: "") }
    var identifierInput by rememberSaveable { mutableStateOf(editing?.token?.contract ?: "") }
    var coingeckoIdInput by rememberSaveable { mutableStateOf(editing?.token?.coingeckoId ?: "") }
    var coinpaprikaIdInput by rememberSaveable { mutableStateOf(editing?.token?.coinpaprikaId ?: "") }
    var decimalsInput by rememberSaveable { mutableStateOf(editing?.token?.decimals?.toLong() ?: 6L) }
    var formMessage by rememberSaveable {
        mutableStateOf(
            if (editing != null && editing.hostingChain == null) AppLocalization.string("That network cannot hold tokens.") else null,
        )
    }
    var isSaving by remember { mutableStateOf(false) }

    // The protocols the selected network supports, in the catalog's order.
    val standards = selectedChain?.entry?.tokenStandards ?: emptyList()
    // The chosen protocol: the network's only one, or the one picked.
    val standard = if (standards.size == 1) standards.first() else standards.firstOrNull { it.standard == selectedStandard }
    // What the chosen protocol identifies a token by.
    val identifierPrompt = standard?.identifierPrompt ?: "Token Identifier"
    // The places the chosen protocol gives every token, if it fixes them.
    val fixedDecimals = standard?.fixedDecimals
    val maxDecimals = CoreReferenceTables.bounds.maxTokenDecimals.toLong()
    val canSave = !isSaving && selectedChain != null && (editing != null || standard != null)

    fun save() {
        val chain = selectedChain ?: return
        isSaving = true
        scope.launch {
            formMessage = tokens.addCustom(
                chain = chain,
                standard = standard?.standard,
                symbol = symbolInput,
                name = nameInput,
                contractAddress = identifierInput,
                coingeckoId = coingeckoIdInput,
                coinpaprikaId = coinpaprikaIdInput,
                decimals = if (fixedDecimals == null) decimalsInput.toUInt() else null,
                editing = editing,
            )
            isSaving = false
            if (formMessage == null) navigator.dismiss(route)
        }
    }

    SpectraPage(
        title = AppLocalization.string(if (editing == null) "New Token" else "Edit Token"),
        actions = {
            TextButton(onClick = ::save, enabled = canSave) { Text(AppLocalization.string("Save")) }
        },
    ) {
        FormSection(
            title = AppLocalization.string("Network"),
            footer = if (editing == null && standards.size > 1) {
                AppLocalization.string("This network has more than one token standard. Choose the one the token was issued under.")
            } else {
                null
            },
        ) {
            if (editing != null) {
                row { FormValueRow(AppLocalization.string("Network"), editing.token.chainId.displayName) }
                row { FormValueRow(AppLocalization.string("Standard"), editing.token.tokenStandard?.displayName ?: "") }
                row {
                    SelectionContainer(Modifier.spectraRowPadding()) {
                        Text(
                            breakableAnywhere(editing.token.contract),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = secondaryText,
                        )
                    }
                }
            } else {
                row {
                    FormPickerRow(
                        label = AppLocalization.string("Network"),
                        options = listOf<Chain?>(null) + ChainCatalog.tokenHostingChains,
                        selected = selectedChain,
                        optionTitle = { it?.displayName ?: AppLocalization.string("Select a chain") },
                        onSelect = { chain ->
                            // Another network's protocols are not this one's.
                            if (chain != selectedChain) selectedStandard = null
                            selectedChain = chain
                        },
                        modifier = Modifier.formStepperPadding(),
                        enabled = !isSaving,
                    )
                }
                if (standards.size > 1) {
                    row {
                        FormPickerRow(
                            label = AppLocalization.string("Standard"),
                            options = listOf<TokenStandard?>(null) + standards.map { it.standard },
                            selected = selectedStandard,
                            optionTitle = { it?.displayName ?: AppLocalization.string("Select a standard") },
                            onSelect = { selectedStandard = it },
                            enabled = !isSaving,
                        )
                    }
                } else {
                    standards.firstOrNull()?.let { only ->
                        row { FormValueRow(AppLocalization.string("Standard"), only.standard.displayName) }
                    }
                }
                row {
                    FormTextFieldRow(
                        value = identifierInput,
                        onValueChange = { identifierInput = it },
                        label = AppLocalization.string(identifierPrompt),
                        enabled = !isSaving,
                        imeAction = ImeAction.Next,
                    )
                }
            }
        }
        FormSection(title = AppLocalization.string("Token Details")) {
            row {
                FormTextFieldRow(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = AppLocalization.string("Name"),
                    enabled = !isSaving,
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrect = true,
                    imeAction = ImeAction.Next,
                )
            }
            row {
                FormTextFieldRow(
                    value = symbolInput,
                    onValueChange = { symbolInput = it },
                    label = AppLocalization.string("Symbol"),
                    enabled = !isSaving,
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next,
                )
            }
            if (fixedDecimals != null) {
                // The network's protocol fixes the places; there is nothing to choose.
                row {
                    FormTextRow(
                        AppLocalization.formatCount("Token Supports: %lld decimals", fixedDecimals.toLong(), fixedDecimals.toLong()),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                row {
                    SpectraStepper(
                        text = AppLocalization.formatCount("Token Supports: %lld decimals", decimalsInput, decimalsInput),
                        value = decimalsInput.toDouble(),
                        range = 0.0..maxDecimals.toDouble(),
                        onValueChange = { decimalsInput = it.toLong() },
                        enabled = !isSaving,
                    )
                }
            }
        }
        FormSection(
            title = AppLocalization.string("Price Sources"),
            footer = AppLocalization.string("Both price sources are optional. Use the provider's token ID, not its website URL."),
        ) {
            row {
                FormTextFieldRow(
                    value = coingeckoIdInput,
                    onValueChange = { coingeckoIdInput = it },
                    label = AppLocalization.string("CoinGecko ID (Optional)"),
                    enabled = !isSaving,
                    imeAction = ImeAction.Next,
                )
            }
            row {
                FormTextFieldRow(
                    value = coinpaprikaIdInput,
                    onValueChange = { coinpaprikaIdInput = it },
                    label = AppLocalization.string("CoinPaprika ID (Optional)"),
                    enabled = !isSaving,
                )
            }
        }
        formMessage?.let { message -> FormSection { row { FormErrorRow(message) } } }
    }
}
