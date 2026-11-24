package dev.spectra.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.spectra_core.Chain
import uniffi.spectra_core.listAllChains
import java.io.File
import java.util.UUID

/**
 * Core loads on the device through JNA, opens a store of its own, and answers
 * through the bridge: what every page of the app stands on.
 */
@RunWith(AndroidJUnit4::class)
class WalletServiceBridgeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun coreLoadsAndListsItsCatalog() {
        val chains = listAllChains()
        assertTrue(chains.isNotEmpty())
        assertEquals(Chain.entries.size, chains.size)
    }

    @Test
    fun aFreshStoreOpensEmpty() = runTest {
        val path = File(context.cacheDir, "bridge-test-${UUID.randomUUID()}.db")
        try {
            val service = WalletServiceBridge(context, databasePath = path.absolutePath).ready()
            assertTrue(service.portfolioSnapshot().wallets.isEmpty())
            assertTrue(service.transactionSnapshot().recentAndPending.isEmpty())
        } finally {
            path.delete()
        }
    }
}
