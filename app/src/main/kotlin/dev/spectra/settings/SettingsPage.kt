package dev.spectra.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.spectra.app.DeviceBiometry
import dev.spectra.app.LocalAppState
import dev.spectra.app.LocalNavigator
import dev.spectra.app.Routes
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraPage
import dev.spectra.components.SpectraRowSection
import dev.spectra.components.SpectraRowSectionScope
import dev.spectra.components.SpectraSwitchRow
import dev.spectra.components.openExternalUrl
import dev.spectra.components.secondaryText
import dev.spectra.components.spectraRowPadding
import dev.spectra.models.AppLocalization
import dev.spectra.models.FiatCurrencies
import dev.spectra.models.code
import dev.spectra.models.displayName
import uniffi.spectra_core.AppSettingUpdate

/** Opens the support page itself; a page in between would hold only its link. */
private const val REPORT_PROBLEM_URL = "https://spectrawallet.org/bug/"

/** A divider starts under a row's title, past its icon. */
private val SettingsDividerInset = SpectraLayout.rowHorizontal + 24.dp + SpectraLayout.Space.m

/**
 * The settings tab: cards of rows grouped by what the user is looking after
 * rather than by implementation.
 */
@Composable
internal fun SettingsPage() {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val preferences = app.preferences
    val settings = app.appSettings
    SpectraPage(title = AppLocalization.string("Settings"), large = true) {
        SecuritySection()
        Section(AppLocalization.string("Display")) {
            row {
                SpectraSwitchRow(
                    title = AppLocalization.string("Hide Balances"),
                    checked = preferences.hideBalances,
                    onCheckedChange = { preferences.hideBalances = it },
                    icon = Icons.Outlined.VisibilityOff,
                )
            }
            row {
                SpectraSwitchRow(
                    title = AppLocalization.string("Hide Small Balances"),
                    checked = preferences.hideSmallBalances,
                    onCheckedChange = { preferences.hideSmallBalances = it },
                    icon = Icons.Outlined.FilterList,
                )
            }
            row {
                SettingsLinkRow(AppLocalization.string("Appearance"), Icons.Outlined.Contrast, { navigator.push(SettingsRoute.Appearance) }) {
                    TrailingValue(preferences.appearanceMode.label)
                }
            }
        }
        Section(AppLocalization.string("Notifications")) {
            row { SettingsLinkRow(AppLocalization.string("Price Alerts"), Icons.Outlined.NotificationsActive, { navigator.push(SettingsRoute.PriceAlerts) }) }
            row {
                SpectraSwitchRow(
                    title = AppLocalization.string("Transaction Status Updates"),
                    checked = settings.useTransactionStatusNotifications,
                    onCheckedChange = { app.updateSetting(AppSettingUpdate.UseTransactionStatusNotifications(it)) },
                    icon = Icons.Outlined.PendingActions,
                )
            }
            row {
                SettingsLinkRow(
                    AppLocalization.string("Large Movement Alerts"),
                    Icons.AutoMirrored.Outlined.ShowChart,
                    { navigator.push(SettingsRoute.LargeMovementAlerts) },
                )
            }
        }
        Section(AppLocalization.string("Wallets & Data")) {
            row { SettingsLinkRow(AppLocalization.string("Address Book"), Icons.AutoMirrored.Outlined.MenuBook, { navigator.push(SettingsRoute.AddressBook) }) }
            row { SettingsLinkRow(AppLocalization.string("Known Tokens"), Icons.Outlined.Token, { navigator.push(SettingsRoute.KnownTokens) }) }
            row { CurrencyRow() }
            row { SettingsLinkRow(AppLocalization.string("Endpoints"), Icons.Outlined.Hub, { navigator.push(Routes.EndpointSettings) }) }
            row { SettingsLinkRow(AppLocalization.string("Explorers"), Icons.Outlined.Explore, { navigator.push(SettingsRoute.Explorers) }) }
        }
        Section(AppLocalization.string("Help & About")) {
            row {
                SettingsLinkRow(AppLocalization.string("Where can I buy crypto?"), Icons.Outlined.CreditCard, { navigator.push(SettingsRoute.BuyCryptoHelp) })
            }
            row { SettingsLinkRow(AppLocalization.string("Crypto Wiki"), Icons.AutoMirrored.Outlined.LibraryBooks, { navigator.push(Routes.CryptoWiki) }) }
            row {
                // Leaves the app for the support page, so it ends in the
                // outward arrow rather than a chevron.
                SettingsRow(
                    title = AppLocalization.string("Report a Problem"),
                    icon = Icons.Outlined.Feedback,
                    onClick = { openExternalUrl(context, REPORT_PROBLEM_URL) },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = secondaryText, modifier = Modifier.size(18.dp))
                }
            }
            row { SettingsLinkRow(AppLocalization.string("About Spectra"), Icons.Outlined.Info, { navigator.push(SettingsRoute.About) }) }
            row { SettingsLinkRow(AppLocalization.string("Donate"), Icons.Outlined.FavoriteBorder, { navigator.push(SettingsRoute.Donate) }) }
        }
        Section(AppLocalization.string("Developer")) {
            row { SettingsLinkRow(AppLocalization.string("Diagnostics"), Icons.Outlined.MonitorHeart, { navigator.push(Routes.Diagnostics) }) }
            row {
                SettingsLinkRow(AppLocalization.string("Operational Logs"), Icons.AutoMirrored.Outlined.ManageSearch, { navigator.push(Routes.OperationalLogs) })
            }
        }
        SpectraRowSection(dividerInset = SettingsDividerInset) {
            row {
                SettingsLinkRow(
                    AppLocalization.string("Reset Wallet"),
                    Icons.Outlined.Delete,
                    { navigator.push(SettingsRoute.ResetWallet) },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Locking and confirming sends both ask the device owner, so neither does
 * anything while the device check is off. Android names what proves the
 * owner — biometrics or the screen lock — where iOS names Face ID.
 */
@Composable
private fun SecuritySection() {
    val app = LocalAppState.current
    val navigator = LocalNavigator.current
    val preferences = app.preferences
    val biometry = DeviceBiometry.current(LocalContext.current)
    val isProtected = preferences.useDeviceAuthentication
    SpectraRowSection(
        title = AppLocalization.string("Security & Privacy"),
        footer = if (isProtected) null else AppLocalization.format("settings.security.off_footer_format", biometry.displayName),
        dividerInset = SettingsDividerInset,
    ) {
        row {
            SpectraSwitchRow(
                title = AppLocalization.format("Use %@", biometry.displayName),
                checked = preferences.useDeviceAuthentication,
                onCheckedChange = { preferences.useDeviceAuthentication = it },
                icon = if (biometry == DeviceBiometry.BIOMETRICS) Icons.Outlined.Fingerprint else Icons.Outlined.Password,
            )
        }
        row {
            SpectraSwitchRow(
                title = AppLocalization.string("Auto Lock"),
                checked = preferences.useAutoLock,
                onCheckedChange = { preferences.useAutoLock = it },
                icon = Icons.Outlined.LockClock,
                enabled = isProtected,
            )
        }
        row {
            SpectraSwitchRow(
                title = AppLocalization.format("Confirm Sends with %@", biometry.displayName),
                checked = preferences.authenticateSends,
                onCheckedChange = { preferences.authenticateSends = it },
                icon = Icons.Outlined.VerifiedUser,
                enabled = isProtected,
            )
        }
        row {
            // Acts in place: no chevron, since it leads nowhere.
            SettingsRow(
                title = AppLocalization.string("Lock Now"),
                icon = Icons.Outlined.Lock,
                enabled = isProtected,
                onClick = { app.isAppLocked = true },
            )
        }
        row {
            SettingsLinkRow(AppLocalization.string("Tor Network"), Icons.Outlined.VpnLock, { navigator.push(Routes.TorSettings) }) {
                TorStatusBadge(app.tor.status)
            }
        }
    }
}

/**
 * The display currency, chosen in place: a page that held one picker was a
 * tap away from nothing else. A pricing read that fails is a notice on Home,
 * with its Retry.
 */
@Composable
private fun CurrencyRow() {
    val app = LocalAppState.current
    var expanded by remember { mutableStateOf(false) }
    val selected = app.selectedFiatCurrency
    Box {
        SettingsRow(
            title = AppLocalization.string("Display Currency"),
            icon = Icons.Outlined.Paid,
            onClick = { expanded = true },
            role = Role.DropdownList,
        ) {
            TrailingValue(selected.code)
            Icon(Icons.Outlined.UnfoldMore, contentDescription = null, tint = secondaryText, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FiatCurrencies.all.forEach { currency ->
                DropdownMenuItem(
                    text = { Text(currency.displayName) },
                    onClick = {
                        expanded = false
                        app.updateSetting(AppSettingUpdate.FiatCurrency(currency))
                    },
                    trailingIcon = if (currency == selected) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: SpectraRowSectionScope.() -> Unit) {
    SpectraRowSection(title = title, dividerInset = SettingsDividerInset, content = content)
}

/** A row that leads to a page, with what it is set to before the chevron. */
@Composable
private fun SettingsLinkRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color? = null,
    trailing: @Composable () -> Unit = {},
) {
    SettingsRow(title = title, icon = icon, onClick = onClick, tint = tint) {
        trailing()
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An icon, a title and whatever ends the row. */
@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color? = null,
    role: Role = Role.Button,
    trailing: @Composable () -> Unit = {},
) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) tint ?: MaterialTheme.colorScheme.primary else disabled,
            modifier = Modifier.size(24.dp),
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) tint ?: Color.Unspecified else disabled,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun TrailingValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = secondaryText, maxLines = 1)
}
