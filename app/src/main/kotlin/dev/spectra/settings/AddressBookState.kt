package dev.spectra.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.spectra.app.StateCommandQueue
import dev.spectra.models.AppLocalization
import dev.spectra.models.addressPrefixHint
import dev.spectra.models.displayName
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.Deferred
import uniffi.spectra_core.AddressBookEntry
import uniffi.spectra_core.AddressBookRejection
import uniffi.spectra_core.Chain
import uniffi.spectra_core.StateCommand
import uniffi.spectra_core.StateEvent
import uniffi.spectra_core.StateTransition
import uniffi.spectra_core.isValidSendAddress

/**
 * Saved recipients, as core last committed them. Core owns the list, the
 * rules about what may be saved and the persistence; every change is a
 * command through the shared queue, and what core commits comes back
 * through [adopt].
 */
class AddressBookState(private val commands: StateCommandQueue) {
    var entries by mutableStateOf<List<AddressBookEntry>>(emptyList())
        private set

    /** Why core refused the last address-book change, if it did. */
    var error by mutableStateOf<String?>(null)

    /** The only writer of [entries], called with each committed core state. */
    fun adopt(entries: List<AddressBookEntry>) {
        if (this.entries != entries) this.entries = entries
    }

    /**
     * Save a recipient. Core trims, normalizes and validates the address,
     * rejects duplicates and assigns the id. `null` once stored, or why not.
     */
    suspend fun add(name: String, address: String, chain: Chain, note: String = ""): String? {
        runCatching { send(StateCommand.AddAddressBookEntry(name, chain, address, note)).await() }
        return error
    }

    fun rename(id: String, newName: String) {
        send(StateCommand.RenameAddressBookEntry(id, newName))
    }

    fun remove(id: String) {
        send(StateCommand.RemoveAddressBookEntry(id))
    }

    /** A refusal arrives as an `AddressBookRejected` event carrying core's reason. */
    private fun send(command: StateCommand): Deferred<StateTransition> = commands.enqueue(command) { result ->
        result.onSuccess { transition ->
            error = null
            transition.events.filterIsInstance<StateEvent.AddressBookRejected>().forEach {
                error = addressBookRejectionMessage(it.reason)
            }
        }.onFailure { error = userErrorMessage(it) }
    }
}

private fun addressBookRejectionMessage(reason: AddressBookRejection): String = AppLocalization.string(
    when (reason) {
        AddressBookRejection.EMPTY_NAME -> "Enter a name for this contact."
        AddressBookRejection.INVALID_ADDRESS -> "That address is not valid for this chain."
        AddressBookRejection.DUPLICATE_ADDRESS -> "That address is already saved."
    },
)

/** Enables the save button. Core still validates and refuses a duplicate. */
fun canSaveAddressBookEntry(name: String, address: String, chain: Chain): Boolean =
    name.isNotBlank() && isValidSendAddress(chain, address)

fun addressBookAddressValidationMessage(address: String, chain: Chain): String {
    val trimmed = address.trim()
    if (trimmed.isNotEmpty() && isValidSendAddress(chain, trimmed)) {
        return AppLocalization.format("Valid %@ address.", chain.displayName)
    }
    val hint = chain.addressPrefixHint
    if (hint.isEmpty()) {
        return if (trimmed.isEmpty()) {
            AppLocalization.string("Enter an address for the selected chain.")
        } else {
            AppLocalization.format("Enter a valid %@ address.", chain.displayName)
        }
    }
    return if (trimmed.isEmpty()) {
        AppLocalization.format("%@ addresses look like %@", chain.displayName, hint)
    } else {
        AppLocalization.format("Enter a valid %@ address — they look like %@", chain.displayName, hint)
    }
}
