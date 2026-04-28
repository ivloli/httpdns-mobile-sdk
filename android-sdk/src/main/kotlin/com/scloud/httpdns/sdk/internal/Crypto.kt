package com.scloud.httpdns.sdk.internal

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private const val GCM_TAG_LENGTH = 128
    private const val IV_SIZE = 12

    fun encryptToHex(aesKey: ByteArray, plaintext: String): String {
        return encryptToHex(aesKey, plaintext.toByteArray(Charsets.UTF_8))
    }

    fun encryptToHex(aesKey: ByteArray, plaintext: ByteArray): String {
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        val keySpec = SecretKeySpec(aesKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(plaintext)

        return (iv + cipherText).toHex()
    }

    fun decryptFromHex(aesKey: ByteArray, hex: String): String {
        val bytes = hex.hexToBytes()
        require(bytes.size > IV_SIZE) { "invalid encrypted payload" }
        val iv = bytes.copyOfRange(0, IV_SIZE)
        val cipherText = bytes.copyOfRange(IV_SIZE, bytes.size)

        val keySpec = SecretKeySpec(aesKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val plain = cipher.doFinal(cipherText)
        return plain.toString(Charsets.UTF_8)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "hex length must be even" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
