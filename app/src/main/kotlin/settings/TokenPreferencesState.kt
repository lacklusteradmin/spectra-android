package dev.spectra.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.app.StateCommandQueue
import dev.spectra.diagnostics.WalletDiagnosticsState
import dev.spectra.models.AppLocalization
import dev.spectra.models.userErrorMessage
import uniffi.spectra_core.Chain
import uniffi.spectra_core.StateCommand
import uniffi.spectra_core.StateEvent
import uniffi.spectra_core.StateTransition
import uniffi.spectra_core.TokenPreferenceEntry
import uniffi.spectra_core.TokenPreferenceRejection
import uniffi.spectra_core.TokenStandard

/**
 * The known-token projection — the catalog and the user's custom tokens — as
 * core last committed it. Every rule about a token is the reducer's; a
 * refusal comes back as an event carrying its reason, worded here.
 */
class TokenPreferencesState(private val commands: StateCommandQueue, private val diagnostics: WalletDiagnosticsState) {
    var entries by mutableStateOf<List<TokenPreferenceEntry>>(emptyList())
        private set

    /** Why core refused the last token-preference change, if it did. */
    var error by mutableStateOf<String?>(null)

    /** The only writer of [entries], called with each committed core state. */
    fun adopt(entries: List<TokenPreferenceEntry>) {
        if (this.entries != entries) this.entries = entries
    }

    fun removeCustom(entry: TokenPreferenceEntry) {
        commands.enqueue(StateCommand.RemoveCustomToken(entry.token.chainId, entry.token.contract)) { result ->
            error = errorMessage(result)
        }
    }

    /**
     * Add a token under [standard], one of [chain]'s protocols — the network's
     * only one when `null` — or edit [editing], which keeps its own. Returns
     * the refusal to show beside the form, or `null` once core accepted it.
     */
    suspend fun addCustom(
        chain: Chain,
        standard: TokenStandard? = null,
        symbol: String,
        name: String,
        contractAddress: String,
        coingeckoId: String = "",
        coinpaprikaId: String = "",
        decimals: UInt?,
        editing: TokenPreferenceEntry? = null,
    ): String? {
        val command = if (editing != null) {
            StateCommand.UpdateCustomToken(
                editing.token.chainId, editing.token.contract, symbol, name, coingeckoId, coinpaprikaId, decimals,
            )
        } else {
            StateCommand.AddCustomToken(standard, chain, symbol, name, contractAddress, coingeckoId, coinpaprikaId, decimals)
        }
        val result = runCatching { commands.apply(command) }
        error = errorMessage(result)
        return error
    }

    private fun errorMessage(result: Result<StateTransition>): String? = result.fold(
        onSuccess = { transition ->
            transition.events.filterIsInstance<StateEvent.TokenPreferenceRejected>().firstOrNull()
                ?.let { tokenPreferenceRejectionMessage(it.reason) }
        },
        onFailure = {
            diagnostics.appendOperationalLog(category = "Tokens", message = it.toString())
            userErrorMessage(it)
        },
    )
}

private fun tokenPreferenceRejectionMessage(reason: TokenPreferenceRejection): String = AppLocalization.string(
    when (reason) {
        TokenPreferenceRejection.UNKNOWN_CHAIN -> "That network cannot hold tokens."
        TokenPreferenceRejection.EMPTY_SYMBOL -> "Symbol is required."
        TokenPreferenceRejection.SYMBOL_TOO_LONG -> "Symbol is too long."
        TokenPreferenceRejection.INVALID_PRICE_ID -> "Enter a price provider ID, not a URL or name."
        TokenPreferenceRejection.EMPTY_NAME -> "Token name is required."
        TokenPreferenceRejection.EMPTY_CONTRACT -> "Token identifier is required."
        TokenPreferenceRejection.INVALID_CONTRACT -> "That token identifier is not valid for this network."
        TokenPreferenceRejection.DUPLICATE_TOKEN -> "This network already knows this token."
        TokenPreferenceRejection.TOO_MANY_DECIMALS -> "That is more decimal places than a token has."
        TokenPreferenceRejection.BUILT_IN_TOKEN -> "Built-in tokens cannot be edited or removed."
        TokenPreferenceRejection.UNKNOWN_TOKEN -> "That token is no longer in the list."
        TokenPreferenceRejection.STANDARD_REQUIRED -> "Choose the standard the token was issued under."
        TokenPreferenceRejection.UNSUPPORTED_STANDARD -> "That network does not support this token standard."
        TokenPreferenceRejection.DECIMALS_REQUIRED -> "Enter the token's decimal places."
    },
)
