package dev.spectra.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity

/**
 * The one activity. It is a [FragmentActivity] because the system's
 * biometric prompt is shown from one.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The recents screen shows the app's icon, never its balances or
        // addresses, as the iOS app switcher shows its logo cover.
        setRecentsScreenshotEnabled(false)
        val app = (application as SpectraApplication).appState
        setContent { SpectraRoot(app) }
    }

    override fun onResume() {
        super.onResume()
        CurrentActivity.set(this)
    }

    override fun onPause() {
        if (CurrentActivity.get() === this) CurrentActivity.set(null)
        super.onPause()
    }
}
