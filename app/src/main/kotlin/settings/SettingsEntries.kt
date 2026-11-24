package dev.spectra.settings

import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes
import uniffi.spectra_core.AddressBookEntry
import uniffi.spectra_core.EndpointDirectoryEntry
import uniffi.spectra_core.TokenPreferenceEntry

/**
 * The pages under the settings tab that only settings opens. The ones other
 * features open — Tor, the endpoint catalog, a token's detail, adding an
 * endpoint — are [Routes]; diagnostics opens the endpoint catalog too.
 */
internal sealed interface SettingsRoute : Route {
    data object AddressBook : SettingsRoute
    data object NewContact : SettingsRoute

    /** A saved recipient, as it was when opened; the page follows the stored one. */
    data class Contact(val entry: AddressBookEntry) : SettingsRoute
    data object KnownTokens : SettingsRoute

    /** The token form: a new custom token, or [editing] one. */
    data class TokenForm(val editing: TokenPreferenceEntry? = null) : SettingsRoute
    data object Appearance : SettingsRoute
    data object PriceAlerts : SettingsRoute
    data object LargeMovementAlerts : SettingsRoute

    /** The endpoint form on a custom endpoint the user added; adding one is [Routes.AddCustomEndpoint]. */
    data class EditEndpoint(val editing: EndpointDirectoryEntry) : SettingsRoute
    data object Explorers : SettingsRoute
    data object BuyCryptoHelp : SettingsRoute
    data object About : SettingsRoute
    data object Donate : SettingsRoute
    data object ResetWallet : SettingsRoute
}

/** The settings tab and every page under it. */
fun EntryProviderScope<Route>.settingsEntries() {
    entry<Routes.Settings> { SettingsPage() }
    entry<SettingsRoute.AddressBook> { AddressBookPage() }
    entry<SettingsRoute.NewContact> { NewAddressBookContactPage(it) }
    entry<SettingsRoute.Contact> { AddressBookContactPage(it) }
    entry<SettingsRoute.KnownTokens> { TokenRegistrySettingsPage() }
    entry<Routes.TokenDetail> { TokenRegistryDetailPage(it) }
    entry<SettingsRoute.TokenForm> { AddCustomTokenPage(it) }
    entry<SettingsRoute.Appearance> { AppearanceSettingsPage() }
    entry<SettingsRoute.PriceAlerts> { PriceAlertsPage() }
    entry<SettingsRoute.LargeMovementAlerts> { LargeMovementAlertsSettingsPage() }
    entry<Routes.EndpointSettings> { EndpointCatalogSettingsPage() }
    entry<Routes.AddCustomEndpoint> { AddCustomEndpointPage(it, editing = null, initialChain = it.chain) }
    entry<SettingsRoute.EditEndpoint> { AddCustomEndpointPage(it, editing = it.editing, initialChain = null) }
    entry<SettingsRoute.Explorers> { ExplorerSettingsPage() }
    entry<SettingsRoute.BuyCryptoHelp> { BuyCryptoHelpPage() }
    entry<SettingsRoute.About> { AboutPage() }
    entry<SettingsRoute.Donate> { DonationsPage() }
    entry<Routes.TorSettings> { TorSettingsPage() }
    entry<SettingsRoute.ResetWallet> { ResetWalletWarningPage(it) }
}
