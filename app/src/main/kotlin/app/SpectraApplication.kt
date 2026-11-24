package dev.spectra.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.spectra.models.AppLocalization
import kotlinx.coroutines.launch

/**
 * The process, and the one [AppState] it holds for as long as it runs, as the
 * iOS app holds its store in the `App`. Activities come and go with
 * configuration changes; the state does not.
 */
class SpectraApplication : Application() {
    lateinit var appState: AppState
        private set

    override fun onCreate() {
        super.onCreate()
        AppLocalization.init(this)
        appState = AppState(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppActivityObserver(appState))
    }
}

/**
 * When the app is with its user and when it is away: started is in front of
 * them, stopped is gone to the background, where auto-lock locks. A
 * permission dialog or the biometric prompt pauses the activity without
 * stopping the process, so it is not leaving.
 */
private class AppActivityObserver(private val app: AppState) : DefaultLifecycleObserver {
    private var hasBeenActive = false

    override fun onStart(owner: LifecycleOwner) {
        app.setAppIsActive(true)
        // The first start is the launch, whose refresh is the engine's first tick.
        if (hasBeenActive) app.scope.launch { app.refreshForForegroundIfNeeded() }
        hasBeenActive = true
    }

    override fun onStop(owner: LifecycleOwner) {
        app.setAppIsActive(false)
    }
}
