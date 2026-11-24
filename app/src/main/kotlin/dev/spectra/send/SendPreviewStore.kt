package dev.spectra.send

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uniffi.spectra_core.AssetHolding
import uniffi.spectra_core.OwnedSendPreview

// Preview types are generated from spectra's `core/src/send/`. What is left
// here is the composer's hold on the latest quote.

class SendPreviewStore {
    /** The latest quote core returned; it names the wallet, holding and network it was made for. */
    var quote by mutableStateOf<OwnedSendPreview?>(null)
        private set

    fun apply(quote: OwnedSendPreview?) {
        this.quote = quote
    }

    /**
     * The quote, if it was made for this wallet's holding on its network. A
     * quote for another holding, or a mainnet quote shown on a testnet, is
     * none.
     */
    fun quote(walletId: String, coin: AssetHolding): OwnedSendPreview? {
        val quote = quote ?: return null
        if (quote.walletId != walletId || quote.holdingKey != coin.id || quote.chainId != coin.chainId) return null
        return quote
    }

    fun reset() {
        quote = null
    }
}
