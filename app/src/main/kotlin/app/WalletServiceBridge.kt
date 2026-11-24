package dev.spectra.app

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.spectra_core.RefreshEngine
import uniffi.spectra_core.RefreshObserver
import uniffi.spectra_core.WalletService
import java.io.File

/**
 * The app's one `WalletService` and the refresh engine on it.
 *
 * Callers use core's API directly: `bridge.ready().portfolioSnapshot()`. This
 * type only owns what core cannot know — which database file and cache
 * directory this device uses, and the Keystore-backed secret store — and
 * makes every call wait for the same database binding.
 */
class WalletServiceBridge(
    context: Context,
    private val databasePath: String? = null,
    private val suppliedService: WalletService? = null,
) {
    private val context = context.applicationContext
    private val openLock = Mutex()
    @Volatile private var stateIsOpen = false
    @Volatile private var service: WalletService? = null
    private var engine: RefreshEngine? = null

    /**
     * The service, bound to its database. Core serializes concurrent opens and
     * retries failures without caching them; the lock keeps this side from
     * asking twice.
     */
    suspend fun ready(): WalletService {
        val svc = service()
        if (stateIsOpen) return svc
        openLock.withLock {
            if (!stateIsOpen) {
                svc.openState(sqliteDbPath())
                if (suppliedService == null) svc.configureNetworkRuntime(torCacheDirectory())
                stateIsOpen = true
            }
        }
        return svc
    }

    /**
     * Delete a store `openState` reported unreadable, with the secrets of the
     * wallets it names, and bind the empty one core opens in its place.
     */
    suspend fun discardUnreadableStore() {
        val svc = service()
        openLock.withLock {
            svc.discardState(sqliteDbPath())
            if (suppliedService == null) svc.configureNetworkRuntime(torCacheDirectory())
            stateIsOpen = true
        }
    }

    /**
     * The service without waiting for the database: for synchronous calls
     * that read the secret store (seed reveal, funds scan) or register the
     * store. Those calls block on the Keystore and on key derivation, so
     * callers run them off the main thread.
     */
    @Synchronized
    fun service(): WalletService {
        service?.let { return it }
        val svc = suppliedService ?: WalletService.newCatalog().also { it.setSecretStore(KeystoreSecretStore(context)) }
        service = svc
        return svc
    }

    /** Core runs the balance sweep and the maintenance tick on this engine. */
    suspend fun refreshEngine(): RefreshEngine {
        val svc = ready()
        synchronized(this) {
            engine?.let { return it }
            return RefreshEngine(svc).also { engine = it }
        }
    }

    suspend fun setRefreshObserver(observer: RefreshObserver) {
        refreshEngine().setObserver(observer)
    }

    private fun sqliteDbPath(): String =
        databasePath ?: File(context.noBackupFilesDir, "spectra_state.db").absolutePath

    private fun torCacheDirectory(): String = context.cacheDir.absolutePath
}
