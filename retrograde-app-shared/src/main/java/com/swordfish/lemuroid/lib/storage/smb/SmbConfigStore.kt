package com.swordfish.lemuroid.lib.storage.smb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the games library comes from (a local folder or a network share, only one at a time) and the network share
 * settings. The password is encrypted with a key of the Android keystore, which never leaves the device.
 */
class SmbConfigStore(context: Context) {
    enum class LibrarySource { LOCAL, SMB }

    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var librarySource: LibrarySource
        get() =
            runCatching { LibrarySource.valueOf(preferences.getString(KEY_SOURCE, null)!!) }
                .getOrDefault(LibrarySource.LOCAL)
        set(value) = preferences.edit { putString(KEY_SOURCE, value.name) }

    fun load(): SmbConfig? {
        val server = preferences.getString(KEY_SERVER, null) ?: return null
        return SmbConfig(
            displayName = preferences.getString(KEY_DISPLAY_NAME, null).orEmpty(),
            server = server,
            path = preferences.getString(KEY_PATH, null).orEmpty(),
            useAuthentication = preferences.getBoolean(KEY_USE_AUTH, false),
            user = preferences.getString(KEY_USER, null).orEmpty(),
            password = preferences.getString(KEY_PASSWORD, null)?.let { decrypt(it) }.orEmpty(),
        )
    }

    fun save(config: SmbConfig) {
        preferences.edit {
            putString(KEY_DISPLAY_NAME, config.displayName)
            putString(KEY_SERVER, config.server)
            putString(KEY_PATH, config.path)
            putBoolean(KEY_USE_AUTH, config.useAuthentication)
            putString(KEY_USER, config.user)
            putString(KEY_PASSWORD, encrypt(config.password))
        }
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? {
        return runCatching {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = bytes.copyOfRange(0, IV_SIZE)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
            String(cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size)), Charsets.UTF_8)
        }.onFailure { Timber.e(it, "Unable to read the network share password") }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFERENCES_NAME = "smb_config"
        private const val KEY_SOURCE = "library_source"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_SERVER = "server"
        private const val KEY_PATH = "path"
        private const val KEY_USE_AUTH = "use_authentication"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "playretro_smb_password"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE_BITS = 128
    }
}
