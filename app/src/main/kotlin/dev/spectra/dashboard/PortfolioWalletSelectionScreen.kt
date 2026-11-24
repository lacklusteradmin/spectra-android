package dev.spectra.dashboard

import androidx.compose.runtime.Composable
import dev.spectra.app.LocalAppState
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowSection
import dev.spectra.components.SpectraSwitchRow
import dev.spectra.models.AppLocalization
import dev.spectra.models.chainName

/** Which wallets count in Home's total and its asset list. */
@Composable
internal fun PortfolioWalletSelectionScreen() {
    val app = LocalAppState.current
    SpectraPage(title = AppLocalization.string("Portfolio Wallets")) {
        SpectraRowSection(
            title = AppLocalization.string("Included In Portfolio Total"),
            footer = AppLocalization.string(
                "Only selected wallets contribute to the portfolio total and the aggregated asset list on the home page.",
            ),
            dividerInset = SpectraLayout.rowHorizontal,
        ) {
            app.wallets.forEach { wallet ->
                row {
                    SpectraSwitchRow(
                        title = wallet.name,
                        subtitle = wallet.chainName,
                        // Core's answer, not this switch's: the row shows what
                        // core committed once the command lands.
                        checked = app.wallet(wallet.id)?.includeInPortfolioTotal ?: true,
                        onCheckedChange = { app.setPortfolioInclusion(it, wallet.id) },
                    )
                }
            }
        }
    }
}
