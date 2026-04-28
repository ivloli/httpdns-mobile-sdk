package com.scloud.httpdns.sdk.internal.adapter

import com.scloud.httpdns.sdk.RequestIpType
import com.scloud.httpdns.sdk.internal.Crypto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class RequestAdapterClientIpTest {
    private val aesKey: ByteArray = hexToBytes("78eb9332f1fc18a45597dbf332858ff4")

    @Test
    fun `buildResolvePath keeps cidr client ip`() {
        val path = RequestAdapter.buildResolvePath(
            accountId = "430992419037876224",
            aesSecretKeyBytes = aesKey,
            host = "www.baidu.com",
            requestIpType = RequestIpType.both,
            expEpochSeconds = 1776768688L,
            clientIp = "1.2.3.0/24"
        )

        val plain = decryptResolvePayload(path)
        assertTrue(plain.contains("\"cip\":\"1.2.3.0/24\""))
        assertTrue(plain.contains("\"dn\":\"www.baidu.com\""))
        assertTrue(plain.contains("\"q\":\"4,6\""))
    }

    @Test
    fun `buildResolvePath omits empty client ip`() {
        val path = RequestAdapter.buildResolvePath(
            accountId = "430992419037876224",
            aesSecretKeyBytes = aesKey,
            host = "www.baidu.com",
            requestIpType = RequestIpType.both,
            expEpochSeconds = 1776768688L,
            clientIp = ""
        )

        val plain = decryptResolvePayload(path)
        assertFalse(plain.contains("\"cip\":"))
    }

    private fun decryptResolvePayload(path: String): String {
        val encParam = path.substringAfter("enc=")
        val enc = URLDecoder.decode(encParam, "UTF-8")
        return Crypto.decryptFromHex(aesKey, enc)
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
