package dev.spectra.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.spectra.components.SpectraLayout
import dev.spectra.components.SpectraScreenPadding
import dev.spectra.models.AppLocalization
import androidx.lifecycle.compose.currentStateAsState

/**
 * Text that is not prose — an address, a key, a descriptor, a path — typed
 * with no keyboard aid that would reshape it: no capitals, no autocorrect.
 */
@Composable
internal fun PlainEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    monospaced: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = if (monospaced) {
            MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyLarge
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
    )
}

/**
 * A BIP-39 passphrase: masked, typed on a keyboard that learns nothing from
 * it. [revealable] adds the eye that shows it, as a passphrase's spaces count
 * and a masked field hides them. A wallet's password is a
 * [dev.spectra.components.WalletPasswordField] instead, which has no eye.
 */
@Composable
internal fun MaskedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    revealable: Boolean = false,
    monospaced: Boolean = false,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    var isRevealed by remember { mutableStateOf(false) }
    // Not `rememberTextFieldState`, which would write the secret into the
    // saved instance state; the caller's value seeds it again after a restore.
    val state = remember { TextFieldState(value) }
    val currentValue by rememberUpdatedState(value)
    val onChange by rememberUpdatedState(onValueChange)
    // The caller's value wins when it changes from outside: a field cleared after use.
    LaunchedEffect(value) {
        if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { typed -> if (typed != currentValue) onChange(typed) }
    }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = if (label != null) {
            { Text(label) }
        } else {
            null
        },
        placeholder = if (placeholder != null) {
            { Text(placeholder) }
        } else {
            null
        },
        isError = isError,
        textStyle = if (monospaced) {
            MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyLarge
        },
        textObfuscationMode = if (isRevealed) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = if (revealable) {
            {
                IconButton(onClick = { isRevealed = !isRevealed }) {
                    Icon(
                        if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = AppLocalization.string(if (isRevealed) "Hide" else "Show"),
                    )
                }
            }
        } else {
            null
        },
    )
}

/**
 * What the activity's lifecycle means for a page holding a secret: [onPause]
 * and [onResume] as the scene going inactive and active again, [onStop] as
 * the app leaving for the background.
 */
@Composable
internal fun SecretLifecycleEffect(
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {},
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pause by rememberUpdatedState(onPause)
    val resume by rememberUpdatedState(onResume)
    val stop by rememberUpdatedState(onStop)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** Whether the activity is in front and interactive now. */
@Composable
internal fun isLifecycleResumed(): Boolean =
    LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)

/**
 * A full-screen sheet over the page, in a window of its own kept out of
 * screenshots, recordings and the recents thumbnail: a recovery phrase or an
 * exported key. iOS shows these in a page sheet over a shielded screen; a
 * dialog's window needs its own secure flag, which the page's does not give it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SecretSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            securePolicy = SecureFlagPolicy.SecureOn,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        actions = { TextButton(onClick = onDismiss) { Text(AppLocalization.string("Done")) } },
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().widthIn(max = SpectraLayout.pageMaxWidth).padding(SpectraScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(SpectraLayout.sectionSpacing),
                        content = content,
                    )
                }
            }
        }
    }
}
