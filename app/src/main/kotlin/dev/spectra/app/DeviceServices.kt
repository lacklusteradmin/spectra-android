package dev.spectra.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.spectra.models.AppLocalization
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/** The activity on screen, for the system prompts that need one. Set by [MainActivity]. */
object CurrentActivity {
    private var reference: WeakReference<FragmentActivity>? = null

    fun set(activity: FragmentActivity?) {
        reference = activity?.let { WeakReference(it) }
    }

    fun get(): FragmentActivity? = reference?.get()
}

enum class DeviceAuthenticationAction {
    UNLOCK,

    /**
     * Every use of a wallet's signing material that shows nothing: signing,
     * rebroadcasting and scanning a Monero wallet locally.
     */
    SEND,
    DELETE_WALLET,
    RESET_DATA,

    /** Every use of signing material that is not a signature: a phrase, a key export, a copy. */
    SECRET_MATERIAL,
    ;

    fun requiresAuthentication(useDeviceAuthentication: Boolean, authenticateSends: Boolean): Boolean = when (this) {
        SEND -> useDeviceAuthentication && authenticateSends
        UNLOCK, DELETE_WALLET, RESET_DATA -> useDeviceAuthentication
        // Signing material leaving its seal is never unprotected, whatever the preferences say.
        SECRET_MATERIAL -> true
    }
}

/**
 * What this device proves its owner with: biometrics, or the screen lock
 * the prompt falls back to and accepts too.
 */
enum class DeviceBiometry {
    BIOMETRICS,
    SCREEN_LOCK,
    ;

    val displayName: String
        get() = AppLocalization.string(if (this == BIOMETRICS) "Biometrics" else "Screen Lock")

    companion object {
        fun current(context: Context): DeviceBiometry =
            if (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS) {
                BIOMETRICS
            } else {
                SCREEN_LOCK
            }
    }
}

/**
 * Ask the device owner — strong biometrics or the screen lock — to confirm.
 * `null` when they did; otherwise why not, for the caller's own flow to show.
 */
suspend fun authenticateDeviceOwner(reason: String): String? {
    val activity = CurrentActivity.get()
        ?: return AppLocalization.format("Device authentication unavailable: %@", AppLocalization.string("unknown error"))
    val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    val availability = BiometricManager.from(activity).canAuthenticate(authenticators)
    if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
        return AppLocalization.format(
            "Device authentication unavailable: %@",
            AppLocalization.string("Set a screen lock in the system settings."),
        )
    }
    return suspendCancellableCoroutine { continuation ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(errString.toString())
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(AppLocalization.string("Spectra"))
                .setSubtitle(reason)
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}

/**
 * The device conditions core's refresh policy reads: the network, power
 * saving and the battery. Reports each network change through [onChange].
 */
class DeviceConditionsMonitor(context: Context, private val onChange: () -> Unit) {
    private val context = context.applicationContext
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    var isNetworkReachable = true
        private set
    var isConstrainedNetwork = false
        private set
    var isExpensiveNetwork = false
        private set

    val isLowPowerMode: Boolean get() = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    /** The battery's charge from 0 to 1, or 1 when it does not report one. */
    val batteryLevel: Float
        get() {
            val capacity = context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: return 1f
            return if (capacity in 0..100) capacity / 100f else 1f
        }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            update(capabilities)
        }

        override fun onLost(network: Network) {
            update(connectivity?.getNetworkCapabilities(connectivity.activeNetwork))
        }
    }

    private fun update(capabilities: NetworkCapabilities?) {
        val reachable = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        // Data Saver restricts the app's background data: the network is constrained.
        val constrained = connectivity?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        val expensive = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        ContextCompat.getMainExecutor(context).execute {
            isNetworkReachable = reachable
            isConstrainedNetwork = constrained
            isExpensiveNetwork = expensive
            onChange()
        }
    }

    fun start() {
        connectivity?.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }
}
