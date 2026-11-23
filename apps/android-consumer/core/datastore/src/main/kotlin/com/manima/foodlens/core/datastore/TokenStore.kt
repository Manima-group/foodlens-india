package com.manima.foodlens.core.datastore

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.first

/** Encrypts small secrets. Production: Tink AEAD keyed by Android Keystore (SPEC §10.7). */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(cipher: ByteArray): ByteArray
}

class TinkKeystoreCipher(context: Context) : SecretCipher {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, "aahar_token_keyset", "aahar_token_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri("android-keystore://aahar_token_master_key")
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override fun encrypt(plain: ByteArray): ByteArray = aead.encrypt(plain, AD)

    override fun decrypt(cipher: ByteArray): ByteArray = aead.decrypt(cipher, AD)

    private companion object {
        val AD = "aahar.guest_token".toByteArray()
    }
}

internal val Context.tokenStore: DataStore<Preferences> by preferencesDataStore(name = "aahar_tokens")

/**
 * Guest/user token, encrypted at rest. Keeps a decrypted in-memory copy so the OkHttp interceptor
 * can read it synchronously. EncryptedSharedPreferences is deprecated and not used (SPEC §10.7).
 */
class TokenStore(private val store: DataStore<Preferences>, private val cipher: SecretCipher) {
    @Volatile
    private var cached: String? = null

    @Volatile
    private var loaded = false

    fun current(): String? = cached

    suspend fun load(): String? {
        if (!loaded) {
            cached = store.data.first()[TOKEN]?.let { decryptOrNull(it) }
            loaded = true
        }
        return cached
    }

    suspend fun save(token: String) {
        val encrypted = Base64.encodeToString(cipher.encrypt(token.toByteArray()), Base64.NO_WRAP)
        store.edit { it[TOKEN] = encrypted }
        cached = token
        loaded = true
    }

    suspend fun clear() {
        store.edit { it.remove(TOKEN) }
        cached = null
        loaded = true
    }

    private fun decryptOrNull(value: String): String? = try {
        String(cipher.decrypt(Base64.decode(value, Base64.NO_WRAP)))
    } catch (_: GeneralSecurityException) {
        null // key lost (e.g. restored to a new device): re-register as guest
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        val TOKEN = stringPreferencesKey("guest_token")
    }
}
