package com.otokit.noharayh.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** This store is only used on the authorization worker. */
internal class EncryptedOAuthStore(context: Context) {
    private val preferences = context.getSharedPreferences("provider_oauth", Context.MODE_PRIVATE)
    private val key by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey) ?:
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(
                    KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build())
                generateKey()
            }
    }

    fun read(entry: String): JSONObject? {
        val encoded = preferences.getString(entry, null) ?: return null
        val bytes = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, IV_SIZE))
        cipher.updateAAD(entry.toByteArray())
        return JSONObject(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).toString(Charsets.UTF_8))
    }

    fun write(entry: String, value: JSONObject) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(entry.toByteArray())
        val ciphertext = cipher.iv + cipher.doFinal(value.toString().toByteArray())
        check(preferences.edit().putString(entry, Base64.getEncoder().encodeToString(ciphertext)).commit())
    }

    fun remove(entry: String) {
        check(preferences.edit().remove(entry).commit())
    }

    private companion object {
        const val KEY_ALIAS = "OTOKiT.ProviderOAuth"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
