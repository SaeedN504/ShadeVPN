package com.shadevpn.android.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.shadevpn.android.model.ProfileSecrets
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ProfileSecretStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val keyAlias = "shadevpn.profile.secrets.v1"

    fun save(profileId: String, secrets: ProfileSecrets) {
        val payload = listOf(secrets.uuid, secrets.password, secrets.publicKey, secrets.shortId)
            .joinToString(DELIMITER) { it.orEmpty().replace(DELIMITER, "") }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        preferences.edit().putString(profileId, encoded).apply()
    }

    fun load(profileId: String): ProfileSecrets? = preferences.getString(profileId, null)?.let { encoded ->
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > IV_BYTES) { "Corrupt encrypted profile secret" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, packed.copyOfRange(0, IV_BYTES)))
        }
        val values = cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size))
            .toString(StandardCharsets.UTF_8)
            .split(DELIMITER)
        ProfileSecrets(
            uuid = values.getOrNull(0)?.ifBlank { null },
            password = values.getOrNull(1)?.ifBlank { null },
            publicKey = values.getOrNull(2)?.ifBlank { null },
            shortId = values.getOrNull(3)?.ifBlank { null },
        )
    }

    fun delete(profileId: String) {
        preferences.edit().remove(profileId).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build())
        }.generateKey()
    }

    private companion object {
        const val PREFERENCES = "shadevpn.profile.secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val DELIMITER = "\u001f"
    }
}
