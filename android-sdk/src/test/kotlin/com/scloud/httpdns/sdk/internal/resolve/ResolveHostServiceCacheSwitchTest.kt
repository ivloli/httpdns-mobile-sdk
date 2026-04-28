package com.scloud.httpdns.sdk.internal.resolve

import com.scloud.httpdns.sdk.HTTPDNSResult
import com.scloud.httpdns.sdk.InitConfig
import com.scloud.httpdns.sdk.Region
import com.scloud.httpdns.sdk.RequestIpType
import com.scloud.httpdns.sdk.internal.CacheStore
import com.scloud.httpdns.sdk.internal.ResolveItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ResolveHostServiceCacheSwitchTest {
    @Test
    fun `sync query bypasses cache when cache disabled`() {
        val config = createConfig(enableCacheIp = false)
        val configRef = AtomicReference(config)
        val cache = CacheStore()
        val resolveCount = AtomicInteger(0)

        val service = ResolveHostService(
            worker = Executors.newSingleThreadExecutor(),
            cache = cache,
            configRef = configRef,
            normalizeHost = { host -> host.trim().lowercase().takeIf { it.isNotBlank() } },
            shouldBypassHttpDns = { false },
            fallbackResult = { host -> HTTPDNSResult(host, emptyArray(), emptyArray(), emptyMap(), 0, true) },
            toPublicResult = { item, expired ->
                HTTPDNSResult(
                    host = item.host,
                    ips = item.ipsV4.toTypedArray(),
                    ipv6s = item.ipsV6.toTypedArray(),
                    extras = item.extras,
                    ttl = item.ttl,
                    expired = expired,
                )
            },
            requestResolveBatch = { _, _, _ -> },
            requestResolve = { host, _, _ ->
                val seq = resolveCount.incrementAndGet()
                HTTPDNSResult(host, arrayOf("1.1.1.$seq"), emptyArray(), emptyMap(), 60, false)
            },
            triggerResolveInBackground = { _, _, _ -> },
            log = { _ -> },
        )

        val first = service.getHttpDnsResultForHostSync("www.example.com", RequestIpType.v4)
        val second = service.getHttpDnsResultForHostSync("www.example.com", RequestIpType.v4)

        assertEquals(2, resolveCount.get())
        assertEquals("1.1.1.1", first.getIps().first())
        assertEquals("1.1.1.2", second.getIps().first())
        assertTrue(cache.get("www.example.com", RequestIpType.v4) == null)
    }

    private fun createConfig(enableCacheIp: Boolean): InitConfig {
        val context = android.test.mock.MockContext()
        return InitConfig.Builder()
            .setContext(context)
            .setAesSecretKey("1234567890abcdef")
            .setRegion(Region.DEFAULT)
            .setEnableCacheIp(enableCacheIp, TimeUnit.HOURS.toMillis(24))
            .setEnableExpiredIp(true)
            .setEnableHttps(true)
            .setUseCronet(false)
            .setTimeoutMillis(1000)
            .build()
    }
}
