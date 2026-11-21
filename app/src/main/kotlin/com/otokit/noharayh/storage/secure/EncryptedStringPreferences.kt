package com.otokit.noharayh.storage.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.KeyStore
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 独立于账号存储的加密字符串存取；内容结构和默认值由使用者解释。 */
internal class EncryptedStringPreferences(context: Context, fileName: String, private val alias: String) {
    private val preferences = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    @Synchronized fun read(entry: String): String? = secure {
        val encoded = preferences.getString(entry, null) ?: return@secure null
        val bytes = Base64.getDecoder().decode(encoded)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12))
        cipher.updateAAD(entry.toByteArray(Charsets.UTF_8))
        cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
    }

    @Synchronized fun write(entry: String, value: String?) = secure {
        val edit = preferences.edit()
        if (value == null) edit.remove(entry) else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            cipher.updateAAD(entry.toByteArray(Charsets.UTF_8))
            val encoded = Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
            edit.putString(entry, encoded)
        }
        if (!edit.commit()) throw IOException("Cannot save encrypted preferences")
    }

    private inline fun <T> secure(action: () -> T): T = try { action() }
    catch (failure: GeneralSecurityException) { throw IOException("Secure network preferences are unavailable", failure) }
    catch (failure: SecurityException) { throw IOException("Secure network preferences are unavailable", failure) }
    catch (failure: ProviderException) { throw IOException("Secure network preferences are unavailable", failure) }
    catch (failure: IllegalArgumentException) { throw IOException("Secure network preferences are invalid", failure) }
}
