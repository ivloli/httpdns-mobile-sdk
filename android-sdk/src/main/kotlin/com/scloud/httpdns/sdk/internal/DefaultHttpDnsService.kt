package com.scloud.httpdns.sdk.internal

import com.scloud.httpdns.sdk.HTTPDNSResult
import com.scloud.httpdns.sdk.HttpDnsBatchCallback
import com.scloud.httpdns.sdk.HttpDnsCallback
import com.scloud.httpdns.sdk.HttpDnsService
import com.scloud.httpdns.sdk.InitConfig
import com.scloud.httpdns.sdk.RequestIpType
import com.scloud.httpdns.sdk.internal.cache.CacheService
import com.scloud.httpdns.sdk.internal.dispatch.DispatchService
import com.scloud.httpdns.sdk.internal.resolve.ResolveRequestExecutor
import com.scloud.httpdns.sdk.internal.resolve.ResolveHostService
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference

internal class DefaultHttpDnsService(
    private val accountId: String,
    config: InitConfig,
) : HttpDnsService {
    private val configRef = AtomicReference(config)
    private val bootstrap = BootstrapConfigLoader.load(config.context)
    private val cache = CacheStore()
    private val worker = Executors.newCachedThreadPool()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val inFlightResolveKeys = ConcurrentHashMap.newKeySet<String>()

    private val dispatchService = DispatchService(
        accountId = accountId,
        configRef = configRef,
        bootstrap = bootstrap,
        scheduler = scheduler,
        log = ::log,
    )

    private val cacheService = CacheService(
        cache = cache,
        normalizeHost = ::normalizeHost,
    )

    private val resolveHostService = ResolveHostService(
        worker = worker,
        cache = cache,
        configRef = configRef,
        normalizeHost = ::normalizeHost,
        shouldBypassHttpDns = ::shouldBypassHttpDns,
        fallbackResult = ::fallbackResult,
        toPublicResult = ::toPublicResult,
        requestResolveBatch = ::requestResolveBatch,
        requestResolve = ::requestResolve,
        triggerResolveInBackground = ::triggerResolveInBackground,
        log = ::log,
    )

    private val resolveRequestExecutor = ResolveRequestExecutor(
        accountId = accountId,
        configRef = configRef,
        cache = cache,
        dispatchService = dispatchService,
        toPublicResult = ::toPublicResult,
        log = ::log,
    )

    init {
        dispatchService.initialize()
    }

    fun updateConfig(newConfig: InitConfig) {
        configRef.set(newConfig)
    }

    override fun getHttpDnsResultForHostSync(host: String, requestIpType: RequestIpType): HTTPDNSResult {
        return resolveHostService.getHttpDnsResultForHostSync(host, requestIpType)
    }

    override fun getHttpDnsResultForHostSync(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult> {
        return resolveHostService.getHttpDnsResultForHostSync(hostList, requestIpType)
    }

    override fun getHttpDnsResultForHostAsync(
        host: String,
        requestIpType: RequestIpType,
        callback: HttpDnsCallback,
    ) {
        resolveHostService.getHttpDnsResultForHostAsync(host, requestIpType, callback)
    }

    override fun getHttpDnsResultForHostAsync(
        hostList: List<String>,
        requestIpType: RequestIpType,
        callback: HttpDnsBatchCallback,
    ) {
        worker.submit {
            val results = resolveHostService.getHttpDnsResultForHostSync(hostList, requestIpType)
            runCatching { callback.onHttpDnsBatchCompleted(results) }
        }
    }

    override fun getHttpDnsResultForHostSyncNonBlocking(
        host: String,
        requestIpType: RequestIpType,
    ): HTTPDNSResult {
        return resolveHostService.getHttpDnsResultForHostSyncNonBlocking(host, requestIpType)
    }

    override fun getHttpDnsResultForHostSyncNonBlocking(
        hostList: List<String>,
        requestIpType: RequestIpType,
    ): List<HTTPDNSResult> {
        return resolveHostService.getHttpDnsResultForHostSyncNonBlocking(hostList, requestIpType)
    }

    override fun setPreResolveHosts(hostList: List<String>, requestIpType: RequestIpType) {
        resolveHostService.setPreResolveHosts(hostList, requestIpType)
    }

    override fun cleanHostCache(hosts: List<String>?) {
        cacheService.cleanHostCache(hosts)
    }

    private fun triggerResolveInBackground(host: String, requestIpType: RequestIpType, source: String) {
        val key = inFlightKey(host, requestIpType)
        if (!inFlightResolveKeys.add(key)) {
            return
        }

        worker.submit {
            try {
                runCatching { requestResolve(host, requestIpType, source) }
            } finally {
                inFlightResolveKeys.remove(key)
            }
        }
    }

    private fun inFlightKey(host: String, requestIpType: RequestIpType): String = "$host:${requestIpType.name}"

    private fun normalizeHost(host: String): String? {
        val normalized = host.trim().lowercase(Locale.ROOT)
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun fallbackResult(host: String): HTTPDNSResult {
        return HTTPDNSResult(host, emptyArray(), emptyArray(), emptyMap(), 0, true)
    }

    private fun requestResolve(host: String, requestIpType: RequestIpType, source: String): HTTPDNSResult {
        return resolveRequestExecutor.requestResolve(host, requestIpType, source)
    }

    private fun requestResolveBatch(hosts: List<String>, requestIpType: RequestIpType, source: String) {
        resolveRequestExecutor.requestResolveBatch(hosts, requestIpType, source)
    }

    private fun toPublicResult(item: ResolveItem, expired: Boolean): HTTPDNSResult {
        return HTTPDNSResult(
            host = item.host,
            ips = item.ipsV4.toTypedArray(),
            ipv6s = item.ipsV6.toTypedArray(),
            extras = item.extras,
            ttl = item.ttl,
            expired = expired,
        )
    }

    private fun shouldBypassHttpDns(host: String): Boolean {
        return configRef.get().notUseHttpDnsFilter?.notUseHttpDns(host) == true
    }

    private fun log(message: String) {
        configRef.get().logger?.log(message)
    }

}
