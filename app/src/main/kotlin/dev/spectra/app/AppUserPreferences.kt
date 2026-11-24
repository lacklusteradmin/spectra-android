package dev.spectra.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import dev.spectra.models.AppLocalization

enum class AppearanceMode(val key: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    ;

    val label: String get() = AppLocalization.string(key)

    /** `null` follows the system. */
    val isDark: Boolean? get() = when (this) {
        SYSTEM -> null
        LIGHT -> false
        DARK -> true
    }
}

/**
 * The six preferences this platform keeps for itself, in this device's
 * shared preferences, read before the first frame. Settings core owns are
 * `AppState.appSettings`.
 */
class AppUserPreferences(context: Context) {
    private val store = context.getSharedPreferences("settings.platform", Context.MODE_PRIVATE)

    private fun bool(key: String, default: Boolean) = store.getBoolean(key, default)
    private fun save(key: String, value: Boolean) = store.edit { putBoolean(key, value) }

    // Each is a snapshot state, so a screen that reads one redraws when it
    // changes, and writes itself back to the shared preferences as it does.

    private var hideBalancesState by mutableStateOf(bool(HIDE_BALANCES, false))
    private var hideSmallBalancesState by mutableStateOf(bool(HIDE_SMALL_BALANCES, true))
    private var appearanceModeState by mutableStateOf(
        store.getString(APPEARANCE_MODE, null)?.let { saved -> AppearanceMode.entries.firstOrNull { it.name == saved } }
            ?: AppearanceMode.SYSTEM,
    )
    private var useDeviceAuthenticationState by mutableStateOf(bool(USE_DEVICE_AUTHENTICATION, true))
    private var useAutoLockState by mutableStateOf(bool(USE_AUTO_LOCK, false))
    private var authenticateSendsState by mutableStateOf(bool(AUTHENTICATE_SENDS, true))

    // ── UI ──────────────────────────────────────────────────────────────

    var hideBalances: Boolean
        get() = hideBalancesState
        set(value) {
            hideBalancesState = value
            save(HIDE_BALANCES, value)
        }

    /**
     * Fold Home's rows worth less than core's small-balance threshold — the
     * dust and airdropped tokens anyone can send an address — behind one row.
     */
    var hideSmallBalances: Boolean
        get() = hideSmallBalancesState
        set(value) {
            hideSmallBalancesState = value
            save(HIDE_SMALL_BALANCES, value)
        }

    var appearanceMode: AppearanceMode
        get() = appearanceModeState
        set(value) {
            appearanceModeState = value
            store.edit { putString(APPEARANCE_MODE, value.name) }
        }

    // ── Security ────────────────────────────────────────────────────────

    var useDeviceAuthentication: Boolean
        get() = useDeviceAuthenticationState
        set(value) {
            if (value == useDeviceAuthenticationState) return
            useDeviceAuthenticationState = value
            save(USE_DEVICE_AUTHENTICATION, value)
            if (!value) deviceAuthenticationDisabledHandler?.invoke()
        }

    var useAutoLock: Boolean
        get() = useAutoLockState
        set(value) {
            useAutoLockState = value
            save(USE_AUTO_LOCK, value)
        }

    /** Ask the device owner — biometrics or the screen lock — before a send is signed. */
    var authenticateSends: Boolean
        get() = authenticateSendsState
        set(value) {
            authenticateSendsState = value
            save(AUTHENTICATE_SENDS, value)
        }

    /** Wired by [AppState] in its init. */
    var deviceAuthenticationDisabledHandler: (() -> Unit)? = null

    /** Reset to factory defaults. */
    fun resetToDefaults() {
        hideBalances = false
        hideSmallBalances = true
        appearanceMode = AppearanceMode.SYSTEM
        useDeviceAuthentication = true
        useAutoLock = false
        authenticateSends = true
    }

    private companion object {
        const val HIDE_BALANCES = "hideBalances"
        const val HIDE_SMALL_BALANCES = "hideSmallBalances"
        const val APPEARANCE_MODE = "appearanceMode"
        const val USE_DEVICE_AUTHENTICATION = "useDeviceAuthentication"
        const val USE_AUTO_LOCK = "useAutoLock"
        const val AUTHENTICATE_SENDS = "authenticateSends"
    }
}
