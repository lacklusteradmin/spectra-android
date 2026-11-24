package dev.spectra.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.spectra_core.SecretClass
import uniffi.spectra_core.SecretStoreException
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID

/**
 * These read and write the device's real Keystore and the app's no-backup
 * files, and share its wrapping key. The device needs a screen lock: the
 * store keeps secrets only on a device with one.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = KeystoreSecretStore(context)

    /** The file as stored, read beside the store rather than through it. */
    private fun storedBytes(directory: String, key: String): ByteArray? {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(File(File(context.noBackupFilesDir, "secrets"), directory), digest)
        return if (file.exists()) file.readBytes() else null
    }

    private fun ByteArray.contains(needle: ByteArray): Boolean =
        (0..size - needle.size).any { start -> needle.indices.all { this[start + it] == needle[it] } }

    /** A missing value reaches core as not found, in every class. */
    @Test
    fun missingValuesReportNotFound() {
        val key = "test.missing.${UUID.randomUUID()}"
        for (kind in SecretClass.entries) {
            assertThrows(SecretStoreException.NotFound::class.java) { store.loadSecret(kind, key) }
        }
    }

    /** Each class round-trips in its own directory, sealed: the file never holds the value. */
    @Test
    fun eachClassRoundTripsSealedInItsOwnDirectory() {
        val key = "test.roundtrip.${UUID.randomUUID()}"
        val directories = mapOf(
            SecretClass.SEED to "seed",
            SecretClass.PRIVATE_KEY to "privatekey",
            SecretClass.GENERIC to "wallet",
            SecretClass.DEVICE_KEY to "devicekey",
        )
        for ((kind, directory) in directories) {
            store.saveSecret(kind, key, "sealed by core")
            try {
                assertEquals("sealed by core", store.loadSecret(kind, key))
                val stored = storedBytes(directory, key)
                assertNotNull("the value is stored under its class", stored)
                assertFalse("the file holds the value in the clear", stored!!.contains("sealed by core".toByteArray()))
            } finally {
                store.deleteSecret(kind, key)
            }
        }
    }

    @Test
    fun deletedValueIsGoneAndReportsNotFound() {
        val key = "test.deleted.${UUID.randomUUID()}"
        store.saveSecret(SecretClass.SEED, key, "sealed by core")
        store.deleteSecret(SecretClass.SEED, key)
        assertEquals(null, storedBytes("seed", key))
        assertThrows(SecretStoreException.NotFound::class.java) { store.loadSecret(SecretClass.SEED, key) }
        // Deleting what is not there is not a failure.
        store.deleteSecret(SecretClass.SEED, key)
    }

    /** The device key is sealed to a Keystore key, never stored raw, and unwraps to itself. */
    @Test
    fun theDeviceKeyIsWrappedToAKeystoreKey() {
        val key = ByteArray(32) { it.toByte() }
        val wrapped = store.wrapDeviceKey(key)
        assertTrue("a wrapped key carries an IV and a tag", wrapped.size == 12 + key.size + 16)
        assertFalse(wrapped.contains(key))
        assertArrayEquals(key, store.unwrapDeviceKey(wrapped))
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertTrue("the wrapping key is a Keystore key, not data", keyStore.containsAlias("spectra.devicekey.wrapping"))
    }

    /** A blob that does not unwrap is an error, never an empty key. */
    @Test
    fun aCorruptWrappedKeyIsAnError() {
        store.wrapDeviceKey(ByteArray(32))
        assertThrows(SecretStoreException.Backend::class.java) { store.unwrapDeviceKey(byteArrayOf(4, 1, 2, 3)) }
        val tampered = store.wrapDeviceKey(ByteArray(32)).also { it[it.lastIndex] = (it.last() + 1).toByte() }
        assertThrows(SecretStoreException.Backend::class.java) { store.unwrapDeviceKey(tampered) }
    }
}
