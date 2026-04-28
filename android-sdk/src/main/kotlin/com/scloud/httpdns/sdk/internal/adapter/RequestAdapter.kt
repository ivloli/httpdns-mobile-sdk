package com.scloud.httpdns.sdk.internal.adapter

import com.scloud.httpdns.sdk.RequestIpType
import com.scloud.httpdns.sdk.internal.Crypto
import org.json.JSONObject
import java.net.URLEncoder

internal object RequestAdapter {
    fun buildResolvePath(
        accountId: String,
        aesSecretKeyBytes: ByteArray,
        host: String,
        requestIpType: RequestIpType,
        expEpochSeconds: Long,
        clientIp: String?,
    ): String {
        val plainJson = JSONObject()
            .put("exp", expEpochSeconds)
            .put("dn", host)
            .put("q", requestIpType.queryValue)
        if (!clientIp.isNullOrBlank()) {
            plainJson.put("cip", clientIp)
        }
        val plain = plainJson.toString()
        val enc = Crypto.encryptToHex(aesSecretKeyBytes, plain)
        return "/v1/d?id=${url(accountId)}&enc=${url(enc)}"
    }

    fun buildDispatchPath(
        accountId: String,
        aesSecretKeyBytes: ByteArray,
        regionValue: String,
        expEpochSeconds: Long,
    ): String {
        val plain = buildDispatchProtoPlain(regionValue, expEpochSeconds)
        val enc = Crypto.encryptToHex(aesSecretKeyBytes, plain)
        return "/dnps-apis/v1/httpdns/endpoints?account_id=${url(accountId)}&enc=${url(enc)}"
    }

    private fun buildDispatchProtoPlain(regionValue: String, expEpochSeconds: Long): ByteArray {
        val region = regionValue.toByteArray(Charsets.UTF_8)
        return encodeLengthDelimited(1, region) + encodeVarintField(3, expEpochSeconds)
    }

    private fun encodeVarintField(fieldNumber: Int, value: Long): ByteArray {
        return byteArrayOf(((fieldNumber shl 3) or 0).toByte()) + encodeVarint(value)
    }

    private fun encodeLengthDelimited(fieldNumber: Int, raw: ByteArray): ByteArray {
        return byteArrayOf(((fieldNumber shl 3) or 2).toByte()) + encodeVarint(raw.size.toLong()) + raw
    }

    private fun encodeVarint(value: Long): ByteArray {
        var current = value
        val out = ArrayList<Byte>()
        while (true) {
            val toWrite = (current and 0x7F).toInt()
            current = current ushr 7
            if (current != 0L) {
                out.add((toWrite or 0x80).toByte())
            } else {
                out.add(toWrite.toByte())
                return out.toByteArray()
            }
        }
    }

    private fun url(value: String): String = URLEncoder.encode(value, "UTF-8")
}
