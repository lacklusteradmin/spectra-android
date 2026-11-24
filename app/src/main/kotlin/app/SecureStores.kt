package dev.spectra.app

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.AtomicFile
import uniffi.spectra_core.SecretClass
import uniffi.spectra_core.SecretStore
import uniffi.spectra_core.SecretStoreException
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Why the Keystore or the files under it could not do what was asked. */
class KeystoreStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * An AES-256-GCM key in the Android Keystore, created on first use by [obtain]
 * and never by [existing]. The key cannot leave the device's secure hardware,
 * is used only while the device is unlocked, and asks for no user presence,
 * so background work that derives from a seed is unaffected.
 *
 * An emulator's Keystore is software: the logic is exercised, the hardware
 * protection is not.
 */
internal class KeystoreKey(private val alias: String, private val preferStrongBox: Boolean) {
    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** The key, or `null` when none was ever created. */
    fun existing(): SecretKey? = keyStore().getKey(alias, null) as SecretKey?

    /** The key, created now when there is none. */
    fun obtain(context: Context): SecretKey {
        existing()?.let { return it }
        val strongBox = preferStrongBox &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        return try {
            generate(strongBox)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUnlockedDeviceRequired(true)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** `iv || ciphertext+tag`. The IV is the Keystore's own, never chosen here. */
        fun seal(key: SecretKey, plaintext: ByteArray): ByteArray {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
            val sealed = cipher.doFinal(plaintext)
            return cipher.iv + sealed
        }

        fun open(key: SecretKey, sealed: ByteArray): ByteArray {
            if (sealed.size <= IV_BYTES) throw KeystoreStoreException("the sealed value is too short")
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            }
            return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        }

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/**
 * One class of core's secrets: a file per key under the app's no-backup
 * directory, each sealed with the storage key, so a copied file opens
 * nothing. The file is named by the SHA-256 of the key, so a key's
 * characters never reach the file system.
 */
private class SealedFileBucket(private val directory: File) {
    private fun file(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return File(directory, digest.joinToString("") { "%02x".format(it) })
    }

    fun save(storageKey: SecretKey, key: String, value: String) {
        directory.mkdirs()
        val atomic = AtomicFile(file(key))
        val stream = atomic.startWrite()
        try {
            stream.write(KeystoreKey.seal(storageKey, value.toByteArray(Charsets.UTF_8)))
            atomic.finishWrite(stream)
        } catch (error: Throwable) {
            atomic.failWrite(stream)
            throw error
        }
    }

    /** `null` only when nothing is stored under [key]; a failed read throws. */
    fun load(storageKey: SecretKey?, key: String): String? {
        val sealed = try {
            AtomicFile(file(key)).readFully()
        } catch (_: FileNotFoundException) {
            return null
        }
        storageKey ?: throw KeystoreStoreException("a secret is stored but its storage key is gone")
        return KeystoreKey.open(storageKey, sealed).toString(Charsets.UTF_8)
    }

    /** Deleting what is not there is not a failure. */
    fun delete(key: String) {
        AtomicFile(file(key)).delete()
    }
}

/**
 * Core's secrets on this device, one directory per class.
 *
 * Core seals seeds and private keys under its device key before they arrive,
 * and decides when that key is minted, reused or refused. What is left here
 * is what only this platform has: the Keystore, the key that seals every
 * stored value at rest, and the key that wraps the device key — in StrongBox
 * where the device has one.
 *
 * Secrets are stored only on a device with a secure lock screen, as iOS keeps
 * them only on a device with a passcode: without one, "unlocked" means
 * anyone holding the phone.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val context = context.applicationContext
    private val root = File(this.context.noBackupFilesDir, "secrets")
    private val storageKey = KeystoreKey("spectra.secrets.storage", preferStrongBox = false)
    private val wrappingKey = KeystoreKey("spectra.devicekey.wrapping", preferStrongBox = true)

    private fun bucket(kind: SecretClass) = SealedFileBucket(
        File(
            root,
            when (kind) {
                SecretClass.SEED -> "seed"
                SecretClass.PRIVATE_KEY -> "privatekey"
                SecretClass.GENERIC -> "wallet"
                SecretClass.DEVICE_KEY -> "devicekey"
            },
        ),
    )

    private fun requireSecureDevice() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) {
            throw KeystoreStoreException("this device has no screen lock; set a PIN, pattern or password to keep secrets on it")
        }
    }

    /**
     * [SecretStoreException.NotFound] only for a value that is not there.
     * Everything else — a locked device, a key that is gone — is the store
     * failing, and core refuses rather than reading it as absence.
     */
    override fun loadSecret(kind: SecretClass, key: String): String {
        val value = backend { bucket(kind).load(storageKey.existing(), key) }
        return value ?: throw SecretStoreException.NotFound()
    }

    override fun saveSecret(kind: SecretClass, key: String, value: String) {
        backend {
            requireSecureDevice()
            bucket(kind).save(storageKey.obtain(context), key, value)
        }
    }

    override fun deleteSecret(kind: SecretClass, key: String) {
        backend { bucket(kind).delete(key) }
    }

    /** Encrypt the device key to the wrapping key, creating that key on first use. */
    override fun wrapDeviceKey(key: ByteArray): ByteArray = backend {
        requireSecureDevice()
        KeystoreKey.seal(wrappingKey.obtain(context), key)
    }

    /**
     * Decrypt a stored device key. Never creates a wrapping key: a wrapped key
     * whose wrapping key is gone is unreadable, not absent.
     */
    override fun unwrapDeviceKey(wrapped: ByteArray): ByteArray = backend {
        val wrapping = wrappingKey.existing()
            ?: throw KeystoreStoreException("no wrapping key is stored for a wrapped device key")
        KeystoreKey.open(wrapping, wrapped)
    }

    private inline fun <T> backend(block: () -> T): T = try {
        block()
    } catch (error: SecretStoreException) {
        throw error
    } catch (error: Exception) {
        throw SecretStoreException.Backend(error.message ?: error.javaClass.simpleName)
    }
}
