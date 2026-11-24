package dev.spectra.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.openExternalUrl
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.BuyProvider
import dev.spectra.models.BuyProviders

/** Where to buy crypto: on-ramps and exchanges, each opening its own site. */
@Composable
internal fun BuyCryptoHelpPage() {
    val context = LocalContext.current
    // Read once: the rows keep their identity while balances and prices
    // update, so a row is never replaced mid-tap.
    val directory = remember { BuyProviders.load(context) }
    SpectraPage(title = AppLocalization.string("Where can I buy crypto?")) {
        FormSection {
            row { FormTextRow(AppLocalization.string("settings.buyProvidersIntro")) }
        }
        ProviderSection(AppLocalization.string("On-ramps"), AppLocalization.string("settings.buyOnrampNote"), directory.onramps)
        ProviderSection(AppLocalization.string("Exchanges"), AppLocalization.string("settings.buyExchangeNote"), directory.exchanges)
        FormSection(title = AppLocalization.string("Reminder")) {
            row { FormTextRow(AppLocalization.string("settings.buyWarning")) }
            row { FormTextRow(AppLocalization.string("settings.buyListingNote")) }
        }
    }
}

@Composable
private fun ProviderSection(title: String, note: String, providers: List<BuyProvider>) {
    val context = LocalContext.current
    FormSection(title = title) {
        row { FormTextRow(note) }
        providers.forEach { provider ->
            row {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { openExternalUrl(context, provider.url) }
                        .spectraRowPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
                        Text(provider.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            provider.label,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = secondaryText,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = secondaryText, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
