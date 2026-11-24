package dev.spectra.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.spectra.models.AppLocalization
import dev.spectra.models.ChainCatalog
import dev.spectra.models.color
import dev.spectra.models.entry
import dev.spectra.models.reviewWarningMessages
import dev.spectra.models.title
import dev.spectra.models.userErrorMessage
import kotlinx.coroutines.delay
import uniffi.spectra_core.AddressBookEntry
import uniffi.spectra_core.Chain
import uniffi.spectra_core.ChainTag
import uniffi.spectra_core.SendArtifact
import uniffi.spectra_core.chainMatchesSearch
import uniffi.spectra_core.readScannedAddress

// ── Clipboard ────────────────────────────────────────────────────────────

/** Put [text] on the clipboard. */
fun copyToClipboard(context: Context, text: String, label: String = "Spectra") {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** How long a copied secret stays on the clipboard if nothing replaces it. */
const val SECRET_CLIPBOARD_LIFETIME_MILLIS = 60_000L

/**
 * Put a secret — a seed phrase — on the clipboard, marked sensitive so the
 * system's paste preview hides it, and clear it after a minute unless
 * something else was copied since.
 */
fun copySecretToClipboard(context: Context, secret: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("Spectra", secret).apply {
        description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
    val stamp = clip.description.timestamp
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        if (clipboard.primaryClipDescription?.timestamp == stamp) clipboard.clearPrimaryClip()
    }, SECRET_CLIPBOARD_LIFETIME_MILLIS)
}

/**
 * Copies [value] and says so for a moment: the icon turns to a check. With a
 * [title] it is a tonal button; without, an icon button for the end of a row.
 */
@Composable
fun CopyButton(value: String, modifier: Modifier = Modifier, title: String? = null, wide: Boolean = false) {
    val context = LocalContext.current
    var didCopy by remember { mutableStateOf(false) }
    LaunchedEffect(didCopy) {
        if (didCopy) {
            delay(1500)
            didCopy = false
        }
    }
    val copy = {
        copyToClipboard(context, value)
        didCopy = true
    }
    val icon = if (didCopy) Icons.Outlined.Check else Icons.Outlined.ContentCopy
    if (title != null) {
        FilledTonalButton(onClick = copy, modifier = if (wide) modifier.fillMaxWidth() else modifier) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            SpectraGap()
            Text(if (didCopy) AppLocalization.string("Copied") else title)
        }
    } else {
        IconButton(onClick = copy, modifier = modifier) {
            Icon(
                icon,
                contentDescription = AppLocalization.string(if (didCopy) "Copied" else "Copy"),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** A row that copies [value] wherever it is tapped and ends in the copy icon. */
@Composable
fun CopyRow(value: String, modifier: Modifier = Modifier, padded: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val context = LocalContext.current
    var didCopy by remember { mutableStateOf(false) }
    LaunchedEffect(didCopy) {
        if (didCopy) {
            delay(1500)
            didCopy = false
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = AppLocalization.string("Copy")) {
                copyToClipboard(context, value)
                didCopy = true
            }
            .then(if (padded) Modifier.spectraRowPadding() else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = content)
        Icon(
            if (didCopy) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = AppLocalization.string(if (didCopy) "Copied" else "Copy"),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Links and sharing ────────────────────────────────────────────────────

/** Open [url] in the browser, as a link does on iOS. `false` when nothing can open it. */
fun openExternalUrl(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Hand [text] to the system share sheet, as iOS's share sheet does. `null`
 * once the sheet is up; otherwise why it could not open.
 */
fun shareText(context: Context, text: String, subject: String? = null): String? = try {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    context.startActivity(
        Intent.createChooser(send, AppLocalization.string("Share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    null
} catch (error: RuntimeException) {
    // Nothing to share with, or a payload larger than the system hands on.
    error.localizedMessage ?: error.toString()
}

// ── Address entry ────────────────────────────────────────────────────────

/**
 * An address typed, pasted, scanned or picked from the address book: the
 * field every page that asks for a recipient uses. A scanned code is read by
 * core against [chain], so a payment URI yields its address and a code for
 * another network is refused rather than pasted in. [readScan] reads the
 * whole payment request instead, as the send composer does.
 */
@Composable
fun AddressEntryRow(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    chain: Chain,
    modifier: Modifier = Modifier,
    contacts: List<AddressBookEntry> = emptyList(),
    isError: Boolean = false,
    supportingText: String? = null,
    readScan: ((String) -> String)? = null,
) {
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var scanProblem by remember { mutableStateOf<String?>(null) }
    var showsContacts by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { onTextChange(it.filterNot { c -> c == '\n' || c == '\r' }) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(title) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            Row {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (pasted != null) onTextChange(pasted.trim())
                }) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = AppLocalization.string("Paste"))
                }
                IconButton(onClick = { isScanning = true }) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = AppLocalization.string("Scan QR Code"))
                }
                if (contacts.isNotEmpty()) {
                    Box {
                        IconButton(onClick = { showsContacts = true }) {
                            Icon(Icons.Outlined.Contacts, contentDescription = AppLocalization.string("Contacts"))
                        }
                        DropdownMenu(expanded = showsContacts, onDismissRequest = { showsContacts = false }) {
                            contacts.forEach { contact ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(contact.name)
                                            Text(
                                                shortAddress(contact.address),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = secondaryText,
                                            )
                                        }
                                    },
                                    onClick = {
                                        onTextChange(contact.address)
                                        showsContacts = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
    if (isScanning) {
        QRScannerDialog(
            onDismiss = { isScanning = false },
            onScan = { payload ->
                isScanning = false
                try {
                    onTextChange(readScan?.invoke(payload) ?: readScannedAddress(chain, payload))
                } catch (error: Exception) {
                    scanProblem = userErrorMessage(error)
                }
            },
            onUnavailable = {
                isScanning = false
                scanProblem = AppLocalization.string(
                    "QR scanning is unavailable right now. Check camera permission and try again.",
                )
            },
        )
    }
    scanProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = { scanProblem = null },
            confirmButton = { TextButton(onClick = { scanProblem = null }) { Text(AppLocalization.string("OK")) } },
            title = { Text(AppLocalization.string("Scan QR Code")) },
            text = { Text(problem) },
        )
    }
}

// ── Secrets ──────────────────────────────────────────────────────────────

/**
 * A seed word or secret typed in a field that turns off every keyboard aid
 * that reads or learns what is typed: no suggestions, no autocorrect, no
 * personalised learning. Keyboards treat a password field as incognito.
 */
@Composable
fun SecretWordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
    )
}

/**
 * A wallet's password, typed where a step needs it: masked, and with every
 * keyboard aid that learns what is typed turned off. [state] is the
 * caller's, made with `remember { TextFieldState() }` rather than
 * `rememberTextFieldState()`, so the password is never written to the saved
 * instance state; the caller clears it once used.
 */
@Composable
fun WalletPasswordField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    label: String? = AppLocalization.string("Wallet Password"),
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    OutlinedSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
    )
}

// ── Search ───────────────────────────────────────────────────────────────

/**
 * The search field at the top of a page's content, where iOS keeps its
 * `.searchable` bar on screen: typed without capitals or corrections,
 * cleared from its end.
 */
@Composable
fun SpectraSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = AppLocalization.string("Clear"))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    )
}

// ── Rows ─────────────────────────────────────────────────────────────────

/** A row that opens a page: an icon, a title, an optional value and a chevron. */
@Composable
fun SpectraNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    value: String? = null,
    tint: Color? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint ?: Color.Unspecified)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A row that ends in its switch; the whole row toggles. */
@Composable
fun SpectraSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondaryText)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A number stepped within [range], as iOS's `Stepper`: its label, then a
 * minus and a plus button. The caller pads it as its row or card does.
 */
@Composable
fun SpectraStepper(
    text: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    enabled: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val contentColor = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = style, color = contentColor, modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = enabled && value > range.start,
        ) { Icon(Icons.Outlined.Remove, contentDescription = AppLocalization.string("Decrease")) }
        IconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = enabled && value < range.endInclusive,
        ) { Icon(Icons.Outlined.Add, contentDescription = AppLocalization.string("Increase")) }
    }
}

/** A small rounded label inside a card: a symbol, a tag, a status. */
@Composable
fun SpectraPill(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(color?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = SpectraLayout.Space.s, vertical = SpectraLayout.Space.xs),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color ?: secondaryText,
        maxLines = 1,
    )
}

/** What core warns of in a reviewed transaction, one line each. */
@Composable
fun ReviewWarnings(artifact: SendArtifact) {
    artifact.reviewWarningMessages.forEach { reason ->
        Row(horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.s)) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = SpectraColors.semantic.warning, modifier = Modifier.size(20.dp))
            Text(reason, style = MaterialTheme.typography.bodyMedium, color = SpectraColors.semantic.warning)
        }
    }
}

// ── Chain pickers ────────────────────────────────────────────────────────

/** One chain as the chain pickers draw it, projected once from its catalog row. */
data class ChainSelectionDescriptor(
    val chain: Chain,
    val title: String,
    /** The chain's native symbol. */
    val symbol: String,
    val artworkName: String?,
    val color: Color,
    /** In `listChainTags()` order, from core. */
    val tags: List<ChainTag>,
    val popularRank: Int,
) {
    val isTestnet: Boolean get() = ChainTag.TESTNET in tags

    /** The tags as one line under the chain's name. */
    val tagLine: String get() = tags.joinToString(" · ") { it.title }

    /** Core's match — name, symbol, keywords, tags — or the reader's words for the chain and its tags. */
    fun matches(query: String): Boolean =
        chainMatchesSearch(chain, query) ||
            title.contains(query, ignoreCase = true) ||
            tags.any { it.title.contains(query, ignoreCase = true) }

    companion object {
        fun of(chain: Chain): ChainSelectionDescriptor? {
            val entry = chain.entry ?: return null
            return ChainSelectionDescriptor(
                chain = chain,
                title = AppLocalization.string(entry.name),
                symbol = entry.gasTokenSymbol,
                artworkName = entry.artworkName,
                color = entry.color.color,
                tags = entry.tags,
                popularRank = entry.popularRank.toInt(),
            )
        }

        /**
         * [chains] in the catalog's popular order. A testnet shares its
         * mainnet's rank, so catalog order breaks the tie and it follows.
         */
        fun popularOrder(chains: List<Chain> = ChainCatalog.all): List<ChainSelectionDescriptor> =
            chains.withIndex()
                .mapNotNull { (index, chain) -> of(chain)?.let { index to it } }
                .sortedWith(compareBy({ it.second.popularRank }, { it.first }))
                .map { it.second }
    }
}

/** How the chain picker orders its rows. */
enum class ChainPickerOrder { POPULAR, NAME }

/** Which rows the chain picker shows: all, or one tag's. */
data class ChainPickerFilter(val tag: ChainTag?) {
    val title: String get() = tag?.title ?: AppLocalization.string("All")

    companion object {
        val ALL = ChainPickerFilter(null)
    }
}

/**
 * The rows a picker shows. Test networks appear while the picker shows them,
 * each after its mainnet — or when a search finds nothing else: a test
 * network asked for by name is the answer, not "No Results".
 */
fun List<ChainSelectionDescriptor>.picked(
    filter: ChainPickerFilter,
    query: String,
    order: ChainPickerOrder,
    showsTestNetworks: Boolean,
): List<ChainSelectionDescriptor> {
    fun matching(showsTests: Boolean) = filter { row ->
        if (row.isTestnet && !showsTests) return@filter false
        if (query.isNotEmpty() && !row.matches(query)) return@filter false
        filter.tag == null || filter.tag in row.tags
    }
    val rows = matching(showsTestNetworks)
    val shown = if (rows.isEmpty() && !showsTestNetworks && query.isNotEmpty()) matching(true) else rows
    return when (order) {
        ChainPickerOrder.POPULAR -> shown
        ChainPickerOrder.NAME -> shown.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
}

/** One chain in a picker: badge, name, tags, gas token and a trailing mark. */
@Composable
fun ChainSelectionRow(
    descriptor: ChainSelectionDescriptor,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disclosure: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).spectraRowPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpectraLayout.Space.m),
    ) {
        CoinBadge(descriptor.artworkName, descriptor.symbol, descriptor.color, size = 36.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpectraLayout.Space.xxs)) {
            Text(
                descriptor.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(descriptor.tagLine, style = MaterialTheme.typography.bodySmall, color = secondaryText, maxLines = 1)
        }
        SpectraPill(descriptor.symbol)
        if (disclosure) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (isSelected) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        } else {
            Spacer(Modifier.size(24.dp))
        }
    }
}
