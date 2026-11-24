package dev.spectra.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.spectra.components.SpectraCard
import dev.spectra.components.SpectraColors
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraLogo
import dev.spectra.components.SpectraTheme
import dev.spectra.components.secondaryText
import dev.spectra.dashboard.dashboardEntries
import dev.spectra.diagnostics.diagnosticsEntries
import dev.spectra.history.historyEntries
import dev.spectra.models.AppLocalization
import dev.spectra.receive.receiveEntries
import dev.spectra.send.sendEntries
import dev.spectra.settings.settingsEntries
import dev.spectra.staking.stakingEntries
import dev.spectra.wallets.tools.walletToolsEntries
import dev.spectra.wallets.walletsEntries
import dev.spectra.wiki.wikiEntries
import kotlinx.coroutines.launch

/**
 * The whole app: the theme, the tabs once core has said what is stored, and
 * whatever covers them — the lock, or a store this build cannot read.
 */
@Composable
fun SpectraRoot(app: AppState) {
    val dark = app.preferences.appearanceMode.isDark ?: isSystemInDarkTheme()
    SpectraTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalAppState provides app, LocalNavigator provides app.navigator) {
            // The tabs are drawn once core has said what is stored: drawn
            // before, Home showed the welcome for a device with no wallets
            // and then replaced it.
            if (app.storeStatus == StoreStatus.OPENING) {
                SpectraLogoScreen()
            } else {
                MainTabs(app)
            }
            when {
                app.isAppLocked -> AppCover { LockCard(app) }
                app.storeStatus == StoreStatus.UNREADABLE -> AppCover { UnreadableStoreCard(app) }
            }
        }
    }
}

private data class TabItem(val tab: MainAppTab, val titleKey: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val TABS = listOf(
    TabItem(MainAppTab.HOME, "Home", Icons.Outlined.PieChart, Icons.Filled.PieChart),
    TabItem(MainAppTab.HISTORY, "History", Icons.Outlined.History, Icons.Filled.History),
    TabItem(MainAppTab.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

/**
 * The three tabs, each with its own back stack. The navigation bar shows at
 * a tab's root and steps aside for a detail or a flow, as the iOS tab bar
 * hides during a flow.
 */
@Composable
private fun MainTabs(app: AppState) {
    val navigator = app.navigator
    val holder = rememberSaveableStateHolder()
    Scaffold(
        bottomBar = {
            if (navigator.isAtRoot) {
                NavigationBar {
                    TABS.forEach { item ->
                        val selected = navigator.selectedTab == item.tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigator.selectTab(item.tab) },
                            icon = {
                                // A send still waiting on its network is worth a
                                // glance from any tab; zero shows no badge.
                                val pending = app.pendingTransactionCount
                                if (item.tab == MainAppTab.HISTORY && pending > 0uL) {
                                    BadgedBox(badge = { Badge { Text(pending.toString()) } }) {
                                        Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null)
                                }
                            },
                            label = { Text(AppLocalization.string(item.titleKey)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val tab = navigator.selectedTab
            holder.SaveableStateProvider(tab) {
                NavDisplay(
                    backStack = navigator.stack(tab),
                    onBack = { navigator.pop() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                    entryProvider = entryProvider {
                        dashboardEntries()
                        walletsEntries()
                        walletToolsEntries()
                        stakingEntries()
                        sendEntries()
                        receiveEntries()
                        historyEntries()
                        settingsEntries()
                        diagnosticsEntries()
                        wikiEntries()
                    },
                )
            }
        }
    }
}

/**
 * Whatever covers the app, in a window above everything it shows: a dialog
 * or sheet opened before the cover sits under it, not over it.
 */
@Composable
private fun AppCover(content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler {}
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(contentAlignment = Alignment.Center) { content() }
        }
    }
}

/** The logo and nothing of the wallet: what launch shows until core has read the store. */
@Composable
private fun SpectraLogoScreen() {
    val name = AppLocalization.string("Spectra")
    Surface(modifier = Modifier.fillMaxSize().semantics { contentDescription = name }, color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) { SpectraLogo() }
    }
}

@Composable
private fun LockCard(app: AppState) {
    val scope = rememberCoroutineScope()
    val biometry = DeviceBiometry.current(LocalContext.current)
    CoverCard(
        icon = Icons.Outlined.Lock,
        iconTint = secondaryText,
        title = AppLocalization.string("content.locked.title"),
        message = AppLocalization.string("content.locked.subtitle"),
        error = app.appLockError,
    ) {
        Button(onClick = { scope.launch { app.unlockApp() } }) {
            Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                AppLocalization.format("Unlock with %@", biometry.displayName),
                modifier = Modifier.padding(start = SpectraLayout.Space.s),
            )
        }
    }
}

/**
 * The store holds data this build cannot read. Nothing else can run, so this
 * is the whole app until the data is discarded.
 */
@Composable
private fun UnreadableStoreCard(app: AppState) {
    val scope = rememberCoroutineScope()
    var isConfirming by remember { mutableStateOf(false) }
    var isDiscarding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    CoverCard(
        icon = Icons.Outlined.WarningAmber,
        iconTint = SpectraColors.semantic.warning,
        title = AppLocalization.string("content.unreadable.title"),
        message = AppLocalization.string("content.unreadable.detail"),
        error = error,
    ) {
        Button(
            onClick = { isConfirming = true },
            enabled = !isDiscarding,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(AppLocalization.string("content.unreadable.reset"), modifier = Modifier.padding(start = SpectraLayout.Space.s))
        }
    }
    if (isConfirming) {
        AlertDialog(
            onDismissRequest = { isConfirming = false },
            title = { Text(AppLocalization.string("content.unreadable.confirm")) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirming = false
                    scope.launch {
                        isDiscarding = true
                        error = app.discardUnreadableStore()
                        isDiscarding = false
                    }
                }) { Text(AppLocalization.string("content.unreadable.reset"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { isConfirming = false }) { Text(AppLocalization.string("Cancel")) } },
        )
    }
}

@Composable
private fun CoverCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    message: String,
    error: String?,
    action: @Composable () -> Unit,
) {
    SpectraCard(elevated = true, modifier = Modifier.padding(SpectraLayout.Space.xl).widthIn(max = 420.dp)) {
        Column(
            modifier = Modifier.padding(SpectraLayout.Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = secondaryText, textAlign = TextAlign.Center)
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            action()
        }
    }
}
