package dev.spectra.components

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import dev.spectra.app.LocalNavigator
import dev.spectra.models.AppLocalization
import kotlinx.coroutines.launch
import androidx.activity.compose.LocalActivity

/**
 * Where a short-lived confirmation such as "Copied" shows: the snackbar of
 * the page on screen. [SpectraScaffold] provides it.
 */
val LocalSnackbarHost = compositionLocalOf { SnackbarHostState() }

/** Show [text] for a moment in the page's snackbar. */
@Composable
fun rememberNotice(): (String) -> Unit {
    val host = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    return remember(host) {
        { text: String ->
            scope.launch {
                host.currentSnackbarData?.dismiss()
                host.showSnackbar(text)
            }
        }
    }
}

/** The back action of the page on screen, or `null` at a tab's root. */
@Composable
fun rememberBackAction(): (() -> Unit)? {
    val navigator = LocalNavigator.current
    return if (navigator.isAtRoot) null else ({ navigator.pop() })
}

/**
 * A page: its top app bar, its content and an optional bottom action bar.
 *
 * A tab's root has a [large] title that collapses as the page scrolls, as
 * the iOS app's large titles do; a detail or a flow's step has a small bar
 * with Back. A flow's primary action goes in [bottomBar] as a
 * [SpectraBottomActionBar], so the content scrolls above it and it rises
 * with the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectraScaffold(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onBack: (() -> Unit)? = rememberBackAction(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = if (large) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    } else {
        TopAppBarDefaults.pinnedScrollBehavior()
    }
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = AppLocalization.string("Back"))
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSnackbarHost provides snackbar) {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                if (large) {
                    LargeTopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                    )
                } else {
                    TopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = { SnackbarHost(snackbar) },
            content = content,
        )
    }
}

/**
 * A scrolling page of cards: [SpectraScaffold] around a column with the
 * screen inset and the section spacing every page uses, no wider than
 * [SpectraLayout.pageMaxWidth]. [onRefresh] makes it pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectraPage(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    onBack: (() -> Unit)? = rememberBackAction(),
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    onRefresh: (suspend () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SpectraScaffold(
        title = title,
        modifier = modifier,
        large = large,
        onBack = onBack,
        actions = actions,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
    ) { padding ->
        val column: @Composable () -> Unit = {
            Box(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier.spectraPageWidth().padding(SpectraScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(SpectraLayout.sectionSpacing),
                    content = content,
                )
            }
        }
        if (onRefresh != null) {
            var isRefreshing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        try {
                            onRefresh()
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                // What the bars already cover — a bottom bar riding on the
                // keyboard included — is consumed, so the column's own
                // keyboard padding adds only what is left.
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            ) { column() }
        } else {
            Box(Modifier.padding(padding).consumeWindowInsets(padding)) { column() }
        }
    }
}

/**
 * The lazy form of [SpectraPage], for a list of hundreds of rows — the known
 * tokens, the log, every chain — which a scrolling column would compose all
 * at once. Each section is a [pageItem] or a [lazyRowGroup], which adds its
 * own gap after it.
 */
@Composable
fun SpectraLazyPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = rememberBackAction(),
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    SpectraScaffold(title = title, modifier = modifier, onBack = onBack, actions = actions) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = SpectraScreenPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** One section of a [SpectraLazyPage] — a card, a field, a notice — at the page's width, with the section gap after it. */
fun LazyListScope.pageItem(key: Any? = null, content: @Composable () -> Unit) {
    item(key = key) {
        Box(Modifier.spectraPageWidth().padding(bottom = SpectraLayout.sectionSpacing)) { content() }
    }
}

/**
 * Keeps the window out of screenshots, screen recordings and the recents
 * thumbnail while this is composed: around a recovery phrase or a private
 * key on screen.
 */
@Composable
fun SecureWindow() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** A section title between cards, for a page that groups its cards. */
@Composable
fun SpectraSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = SpectraLayout.Space.xs, vertical = SpectraLayout.Space.xs),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
