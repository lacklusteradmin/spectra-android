package dev.spectra.dashboard

import androidx.navigation3.runtime.EntryProviderScope
import dev.spectra.app.Route
import dev.spectra.app.Routes

/** The dashboard's own pages, which no other feature opens. */
object DashboardRoutes {
    /** Every active notice, from the toolbar's notice button. */
    data object Notices : Route

    /** Which wallets count in the portfolio total. */
    data object PortfolioWallets : Route
}

/** The dashboard's pages: Home, an asset group, the pinned assets, the notices and the counted wallets. */
fun EntryProviderScope<Route>.dashboardEntries() {
    entry<Routes.Home> { DashboardScreen() }
    entry<Routes.AssetGroupDetail> { AssetGroupDetailScreen(it.groupId) }
    entry<Routes.PinnedAssets> { PinnedAssetsScreen() }
    entry<DashboardRoutes.Notices> { AppNoticesScreen() }
    entry<DashboardRoutes.PortfolioWallets> { PortfolioWalletSelectionScreen() }
}
