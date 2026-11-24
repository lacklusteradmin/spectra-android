package dev.spectra.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.app.StateCommandQueue
import dev.spectra.models.AppLocalization
import dev.spectra.models.DisplayedError
import uniffi.spectra_core.PriceAlertRejection
import uniffi.spectra_core.PriceAlertRule
import uniffi.spectra_core.StateCommand
import uniffi.spectra_core.StateEvent

/**
 * The price alerts, as core last committed them. Core owns the rules,
 * evaluates them on its refresh and says which fired.
 */
class PriceAlertsState(private val commands: StateCommandQueue) {
    var rules by mutableStateOf<List<PriceAlertRule>>(emptyList())
        private set

    /** The only writer of [rules], called with each committed core state. */
    fun adopt(rules: List<PriceAlertRule>) {
        if (this.rules != rules) this.rules = rules
    }

    /** Send an edit. Core's refusal arrives as an event, and is thrown here in words. */
    suspend fun edit(command: StateCommand) {
        val transition = commands.apply(command)
        transition.events.filterIsInstance<StateEvent.PriceAlertRejected>().firstOrNull()?.let {
            throw DisplayedError(priceAlertRejectionMessage(it.reason))
        }
    }
}

private fun priceAlertRejectionMessage(reason: PriceAlertRejection): String = AppLocalization.string(
    when (reason) {
        PriceAlertRejection.MISSING_CURRENCY_RATE -> "Exchange rates for this currency have not loaded yet. Try again shortly."
        PriceAlertRejection.INVALID_TARGET -> "Enter a target price above zero."
        PriceAlertRejection.UNKNOWN_ASSET -> "This asset is no longer in your wallets."
        PriceAlertRejection.DUPLICATE_ALERT -> "An identical alert already exists."
        PriceAlertRejection.ALERT_NOT_FOUND -> "This alert no longer exists."
    },
)
