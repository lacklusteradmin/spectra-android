package dev.spectra.receive

import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes

/** A chosen wallet's address, pushed over the receive flow's wallet list. */
internal data object ReceiveAddressRoute : Route

/** The receive flow's pages: the wallet list, or the one wallet's address, and the address a choice opens. */
fun EntryProviderScope<Route>.receiveEntries() {
    entry<Routes.Receive> { ReceiveScreen() }
    entry<ReceiveAddressRoute> { ReceiveAddressPage() }
}
