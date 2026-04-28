package com.scloud.httpdns.sdk.internal.resolve

import android.os.Looper
import com.scloud.httpdns.sdk.HTTPDNSResult
import com.scloud.httpdns.sdk.HttpDnsCallback
import com.scloud.httpdns.sdk.InitConfig
import com.scloud.httpdns.sdk.RequestIpType
import com.scloud.httpdns.sdk.internal.CacheStore
import com.scloud.httpdns.sdk.internal.ResolveItem
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference

internal class ResolveHostService(
    private val worker: ExecutorService,
    private val cache: CacheStore,
    private val configRef: AtomicReference<InitConfig>,
    private val normalizeHost: (String) -> String?,
    private val shouldBypassHttpDns: (String) -> Boolean,
    private val fallbackResult: (String) -> HTTPDNSResult,
    private val toPublicResult: (ResolveItem, Boolean) -> HTTPDNSResult,
    private val requestResolveBatch: (List<String>, RequestIpType, String) -> Unit,
    private val requestResolve: (String, RequestIpType, String) -> HTTPDNSResult,
    private val triggerResolveInBackground: (String, RequestIpType, String) -> Unit,
    private val log: (String) -> Unit,
) {
    private fun isCacheEnabled(): Boolean = configRef.get().enableCacheIp

    fun getHttpDnsResultForHostSync(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            log("getHttpDnsResultForHostSync(hostList) called on main thread, downgrade to non-blocking")
            return getHttpDnsResultForHostSyncNonBlocking(hostList, requestIpType)
        }

        val normalizedHosts = hostList.map { host -> requireNotNull(normalizeHost(host)) { "host is blank" } }
        if (normalizedHosts.isEmpty()) return emptyList()

        val bypassHosts = normalizedHosts.filter { shouldBypassHttpDns(it) }.toSet()
        bypassHosts.forEach { host ->
            log("host=$host is filtered by NotUseHttpDnsFilter")
        }

        val targets = normalizedHosts.filterNot { it in bypassHosts }.distinct()
        if (targets.isEmpty()) {
            return normalizedHosts.map { host -> fallbackResult(host) }
        }

        val now = System.currentTimeMillis()
        val cacheEnabled = isCacheEnabled()
        val cachedResults = LinkedHashMap<String, HTTPDNSResult>()
        val pendingHosts = ArrayList<String>()

        targets.forEach { host ->
            val cached = if (cacheEnabled) cache.get(host, requestIpType) else null
            if (cached != null && (!cached.isExpired(now) || configRef.get().enableExpiredIp)) {
                log(
                    if (cached.isExpired(now)) {
                        "source=sync cache hit expired host=$host type=${requestIpType.name} ttl=${cached.item.ttl}"
                    } else {
                        "source=sync cache hit host=$host type=${requestIpType.name} ttl=${cached.item.ttl}"
                    },
                )
                if (cached.isExpired(now)) {
                    log("source=sync cache refresh in background host=$host type=${requestIpType.name}")
                    triggerResolveInBackground(host, requestIpType, "sync-expired")
                }
                cachedResults[host] = toPublicResult(cached.item, cached.isExpired(now))
            } else {
                log("source=sync cache miss host=$host type=${requestIpType.name}")
                pendingHosts += host
            }
        }

        if (pendingHosts.isNotEmpty()) {
            runCatching {
                if (pendingHosts.size == 1 || !cacheEnabled) {
                    pendingHosts.forEach { pendingHost ->
                        cachedResults[pendingHost] = requestResolve(pendingHost, requestIpType, "sync")
                    }
                } else {
                    requestResolveBatch(pendingHosts, requestIpType, "sync-batch")
                    val updatedNow = System.currentTimeMillis()
                    pendingHosts.forEach { host ->
                        val updated = cache.get(host, requestIpType)
                        if (updated != null) {
                            cachedResults[host] = toPublicResult(updated.item, updated.isExpired(updatedNow))
                        }
                    }
                }
            }.onFailure { error ->
                log("sync batch resolve failed hosts=$pendingHosts type=${requestIpType.name}: ${error.message}")
            }
        }

        return normalizedHosts.map { host ->
            if (host in bypassHosts) {
                fallbackResult(host)
            } else {
                cachedResults[host] ?: fallbackResult(host)
            }
        }
    }

    fun getHttpDnsResultForHostSync(host: String, requestIpType: RequestIpType): HTTPDNSResult {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            log("getHttpDnsResultForHostSync called on main thread, downgrade to non-blocking")
            return getHttpDnsResultForHostSyncNonBlocking(host, requestIpType)
        }

        val normalizedHost = requireNotNull(normalizeHost(host)) { "host is blank" }
        if (shouldBypassHttpDns(normalizedHost)) {
            log("host=$normalizedHost is filtered by NotUseHttpDnsFilter")
            return fallbackResult(normalizedHost)
        }

        val now = System.currentTimeMillis()
        val cached = if (isCacheEnabled()) cache.get(normalizedHost, requestIpType) else null
        if (cached != null && (!cached.isExpired(now) || configRef.get().enableExpiredIp)) {
            log(
                if (cached.isExpired(now)) {
                    "source=sync cache hit expired host=$normalizedHost type=${requestIpType.name} ttl=${cached.item.ttl}"
                } else {
                    "source=sync cache hit host=$normalizedHost type=${requestIpType.name} ttl=${cached.item.ttl}"
                },
            )
            if (cached.isExpired(now)) {
                log("source=sync cache refresh in background host=$normalizedHost type=${requestIpType.name}")
                triggerResolveInBackground(normalizedHost, requestIpType, "sync-expired")
            }
            return toPublicResult(cached.item, cached.isExpired(now))
        }

        log("source=sync cache miss host=$normalizedHost type=${requestIpType.name}")

        return runCatching {
            requestResolve(normalizedHost, requestIpType, "sync")
        }.getOrElse { error ->
            log("sync resolve failed host=$normalizedHost type=${requestIpType.name}: ${error.message}")
            fallbackResult(normalizedHost)
        }
    }

    fun getHttpDnsResultForHostAsync(host: String, requestIpType: RequestIpType, callback: HttpDnsCallback) {
        val normalizedHost = normalizeHost(host)
        worker.submit {
            val result = runCatching {
                requireNotNull(normalizedHost) { "host is blank" }
                if (shouldBypassHttpDns(normalizedHost)) {
                    log("host=$normalizedHost is filtered by NotUseHttpDnsFilter")
                    return@runCatching fallbackResult(normalizedHost)
                }
                requestResolve(normalizedHost, requestIpType, "async")
            }.getOrElse {
                fallbackResult(normalizedHost ?: host)
            }
            runCatching { callback.onHttpDnsCompleted(result) }
        }
    }

    fun getHttpDnsResultForHostSyncNonBlocking(host: String, requestIpType: RequestIpType): HTTPDNSResult {
        val normalizedHost = requireNotNull(normalizeHost(host)) { "host is blank" }
        if (shouldBypassHttpDns(normalizedHost)) {
            log("host=$normalizedHost is filtered by NotUseHttpDnsFilter")
            return fallbackResult(normalizedHost)
        }

        val now = System.currentTimeMillis()
        val cached = if (isCacheEnabled()) cache.get(normalizedHost, requestIpType) else null
        if (cached != null && (!cached.isExpired(now) || configRef.get().enableExpiredIp)) {
            log(
                if (cached.isExpired(now)) {
                    "source=nonblocking cache hit expired host=$normalizedHost type=${requestIpType.name} ttl=${cached.item.ttl}"
                } else {
                    "source=nonblocking cache hit host=$normalizedHost type=${requestIpType.name} ttl=${cached.item.ttl}"
                },
            )
            if (cached.isExpired(now)) {
                log("source=nonblocking cache refresh in background host=$normalizedHost type=${requestIpType.name}")
                triggerResolveInBackground(normalizedHost, requestIpType, "nonblocking-expired")
            }
            return toPublicResult(cached.item, cached.isExpired(now))
        }

        log("source=nonblocking cache miss host=$normalizedHost type=${requestIpType.name}")
        triggerResolveInBackground(normalizedHost, requestIpType, "nonblocking")
        return fallbackResult(normalizedHost)
    }

    fun getHttpDnsResultForHostSyncNonBlocking(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult> {
        val normalizedHosts = hostList.map { host -> requireNotNull(normalizeHost(host)) { "host is blank" } }
        if (normalizedHosts.isEmpty()) return emptyList()

        val bypassHosts = normalizedHosts.filter { shouldBypassHttpDns(it) }.toSet()
        bypassHosts.forEach { host ->
            log("host=$host is filtered by NotUseHttpDnsFilter")
        }

        val targets = normalizedHosts.filterNot { it in bypassHosts }.distinct()
        if (targets.isEmpty()) {
            return normalizedHosts.map { host -> fallbackResult(host) }
        }

        val now = System.currentTimeMillis()
        val cacheEnabled = isCacheEnabled()
        val results = LinkedHashMap<String, HTTPDNSResult>()
        val refreshHosts = ArrayList<String>()

        targets.forEach { host ->
            val cached = if (cacheEnabled) cache.get(host, requestIpType) else null
            if (cached != null && (!cached.isExpired(now) || configRef.get().enableExpiredIp)) {
                log(
                    if (cached.isExpired(now)) {
                        "source=nonblocking cache hit expired host=$host type=${requestIpType.name} ttl=${cached.item.ttl}"
                    } else {
                        "source=nonblocking cache hit host=$host type=${requestIpType.name} ttl=${cached.item.ttl}"
                    },
                )
                if (cached.isExpired(now)) {
                    refreshHosts += host
                }
                results[host] = toPublicResult(cached.item, cached.isExpired(now))
            } else {
                log("source=nonblocking cache miss host=$host type=${requestIpType.name}")
                refreshHosts += host
                results[host] = fallbackResult(host)
            }
        }

        if (refreshHosts.isNotEmpty()) {
            if (refreshHosts.size == 1) {
                val host = refreshHosts.first()
                log("source=nonblocking cache refresh in background host=$host type=${requestIpType.name}")
                triggerResolveInBackground(host, requestIpType, "nonblocking")
            } else {
                log("source=nonblocking schedule batch hosts=$refreshHosts type=${requestIpType.name}")
                worker.submit {
                    runCatching {
                        requestResolveBatch(refreshHosts, requestIpType, "nonblocking")
                    }.onFailure {
                        log("source=nonblocking batch failed hosts=$refreshHosts type=${requestIpType.name}: ${it.message}")
                    }
                }
            }
        }

        return normalizedHosts.map { host ->
            if (host in bypassHosts) {
                fallbackResult(host)
            } else {
                results[host] ?: fallbackResult(host)
            }
        }
    }

    fun setPreResolveHosts(hostList: List<String>, requestIpType: RequestIpType) {
        if (!isCacheEnabled()) {
            return
        }

        val now = System.currentTimeMillis()
        val pendingHosts = hostList
            .asSequence()
            .mapNotNull { normalizeHost(it) }
            .filterNot { shouldBypassHttpDns(it) }
            .distinct()
            .filter { host ->
                val cached = cache.get(host, requestIpType)
                if (cached == null || cached.isExpired(now)) {
                    true
                } else {
                    log("source=preResolve skip cache-hit host=$host type=${requestIpType.name} ttl=${cached.item.ttl}")
                    false
                }
            }
            .toList()

        if (pendingHosts.isEmpty()) return

        if (pendingHosts.size == 1) {
            val host = pendingHosts.first()
            log("source=preResolve schedule host=$host type=${requestIpType.name}")
            triggerResolveInBackground(host, requestIpType, "preResolve")
            return
        }

        log("source=preResolve schedule batch hosts=$pendingHosts type=${requestIpType.name}")
        worker.submit {
            runCatching {
                requestResolveBatch(pendingHosts, requestIpType, "preResolve")
            }.onFailure {
                log("source=preResolve batch failed hosts=$pendingHosts type=${requestIpType.name}: ${it.message}")
            }
        }
    }
}
