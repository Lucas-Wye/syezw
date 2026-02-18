package org.syezw.sync

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

private const val AES_MODE = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val IV_SIZE_BYTES = 12

fun deriveAesKeyFromPassphrase(passphrase: String): SecretKeySpec {
    val md5 = MessageDigest.getInstance("MD5")
    val keyBytes = md5.digest(passphrase.toByteArray(Charsets.UTF_8))
    return SecretKeySpec(keyBytes, "AES")
}

fun encryptToBlob(plainBytes: ByteArray, key: SecretKeySpec): EncryptedBlob {
    val iv = ByteArray(IV_SIZE_BYTES)
    SecureRandom().nextBytes(iv)
    val cipher = Cipher.getInstance(AES_MODE)
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    val encrypted = cipher.doFinal(plainBytes)
    return EncryptedBlob(
        iv = Base64.getEncoder().encodeToString(iv),
        data = Base64.getEncoder().encodeToString(encrypted)
    )
}

fun decryptFromBlob(blob: EncryptedBlob, key: SecretKeySpec): ByteArray {
    val iv = Base64.getDecoder().decode(blob.iv)
    val encrypted = Base64.getDecoder().decode(blob.data)
    val cipher = Cipher.getInstance(AES_MODE)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(encrypted)
}

fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(bytes)
    return hash.joinToString("") { "%02x".format(it) }
}
