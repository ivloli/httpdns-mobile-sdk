package com.scloud.httpdns.sdk.internal.dispatch

import com.scloud.httpdns.sdk.InitConfig
import com.scloud.httpdns.sdk.internal.BootstrapConfig
import com.scloud.httpdns.sdk.internal.DispatchResult
import com.scloud.httpdns.sdk.internal.HttpTransport
import com.scloud.httpdns.sdk.internal.adapter.RequestAdapter
import com.scloud.httpdns.sdk.internal.adapter.ResponseAdapter
import com.scloud.httpdns.sdk.internal.request.HttpRequestConfig
import com.scloud.httpdns.sdk.internal.request.HttpRequestWatcher
import com.scloud.httpdns.sdk.internal.request.RetryHttpRequest
import java.net.InetAddress
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class DispatchService(
    private val accountId: String,
    private val configRef: AtomicReference<InitConfig>,
    private val bootstrap: BootstrapConfig,
    private val scheduler: ScheduledExecutorService,
    private val log: (String) -> Unit,
) {
    companion object {
        private const val DEFAULT_DISPATCH_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L
        private const val DISPATCH_REFRESH_AHEAD_MILLIS = 30 * 1000L
        private const val DISPATCH_REFRESH_CHECK_INTERVAL_SECONDS = 15L
        private const val MIN_DISPATCH_TIMEOUT_MILLIS = 10_000
    }

    @Volatile
    private var resolveHost: String = ""

    @Volatile
    private var resolveEndpoints: List<ResolveEndpoint> = emptyList()

    @Volatile
    private var resolveEndpointCursor: Int = 0

    @Volatile
    private var nextDispatchRefreshAtMillis: Long = 0L

    fun initialize() {
        val overrideHost = configRef.get().resolveHostOverride
        if (!overrideHost.isNullOrBlank()) {
            replaceResolveEndpoints(
                buildOverrideEndpoints(
                    host = overrideHost,
                    connectIp = configRef.get().resolveConnectIpOverride,
                    includeDnsFallback = false,
                ),
            )
            updateNextDispatchRefreshAt(null)
        } else {
            refresh(forceExpandOverride = false)
        }
        scheduleHealthRefresh()
    }

    fun refresh(forceExpandOverride: Boolean = false): Boolean {
        val overrideHost = configRef.get().resolveHostOverride
        if (!overrideHost.isNullOrBlank()) {
            replaceResolveEndpoints(
                buildOverrideEndpoints(
                    host = overrideHost,
                    connectIp = configRef.get().resolveConnectIpOverride,
                    includeDnsFallback = forceExpandOverride,
                ),
            )
            updateNextDispatchRefreshAt(null)
            return true
        }

        val config = configRef.get()
        val query = RequestAdapter.buildDispatchPath(
            accountId = accountId,
            aesSecretKeyBytes = config.aesSecretKeyBytesForDispatch,
            regionValue = config.region.serverValue,
            expEpochSeconds = System.currentTimeMillis() / 1000 + 600,
        )

        val dispatchTimeoutMillis = maxOf(config.timeoutMillis, MIN_DISPATCH_TIMEOUT_MILLIS)
        val transport = HttpTransport(config.context, dispatchTimeoutMillis, config.enableHttps, config.useCronet)
        val retryHttpRequest = RetryHttpRequest(transport)

        val candidates = listOf(bootstrap.domain to null)

        val dispatchResult = retryHttpRequest.execute(
            configs = candidates.map { (host, ip) ->
                HttpRequestConfig(
                    host = host,
                    pathWithQuery = query,
                    connectIp = ip,
                    portOverride = null,
                )
            },
            watcher = object : HttpRequestWatcher<DispatchResult> {
                override fun onStart(config: HttpRequestConfig) {
                    log("dispatch attempt host=${config.host} ip=${config.connectIp ?: "<dns>"} port=${if (configRef.get().enableHttps) 443 else 80}")
                }

                override fun onSuccess(config: HttpRequestConfig, responseBody: String): DispatchResult? {
                    return runCatching {
                        val decrypted = ResponseAdapter.decryptDispatchPayload(configRef.get().aesSecretKeyBytesForDispatch, responseBody)
                        val parsed = ResponseAdapter.parseDispatchPayload(decrypted)
                        val endpoints = buildResolveEndpoints(parsed)
                        if (endpoints.isNotEmpty()) {
                            log("dispatch success host=${config.host} ip=${config.connectIp ?: "<dns>"} endpoints=${endpoints.size} ttl=${parsed.ttlSeconds ?: -1}")
                            replaceResolveEndpoints(endpoints)
                            updateNextDispatchRefreshAt(parsed.ttlSeconds)
                            parsed
                        } else {
                            log("dispatch parsed empty host=${config.host} ip=${config.connectIp ?: "<dns>"}")
                            null
                        }
                    }.getOrElse {
                        onFail(config, it)
                        null
                    }
                }

                override fun onFail(config: HttpRequestConfig, throwable: Throwable) {
                    log("dispatch failed host=${config.host} ip=${config.connectIp ?: "<dns>"}: ${throwable.message}")
                }
            },
        )

        if (dispatchResult != null) {
            return true
        }

        log("dispatch failed for all candidates, keep current resolve endpoints")
        updateNextDispatchRefreshAt(null)
        return false
    }

    fun orderedResolveEndpointsSnapshot(): List<ResolveEndpoint> = synchronized(this) {
        val endpoints = resolveEndpoints
        val start = if (endpoints.isEmpty()) 0 else (resolveEndpointCursor % endpoints.size)
        (0 until endpoints.size).map { index ->
            endpoints[(start + index) % endpoints.size]
        }
    }

    fun markEndpointSucceeded(endpoint: ResolveEndpoint) = synchronized(this) {
        val index = resolveEndpoints.indexOf(endpoint)
        if (index >= 0) {
            resolveEndpointCursor = index
            resolveHost = endpoint.host
        }
    }

    private fun scheduleHealthRefresh() {
        scheduler.scheduleWithFixedDelay(
            {
                val now = System.currentTimeMillis()
                if (now >= nextDispatchRefreshAtMillis) {
                    runCatching { refresh(forceExpandOverride = false) }
                }
            },
            DISPATCH_REFRESH_CHECK_INTERVAL_SECONDS,
            DISPATCH_REFRESH_CHECK_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    private fun updateNextDispatchRefreshAt(ttlSeconds: Int?) {
        val now = System.currentTimeMillis()
        val next = if (ttlSeconds != null && ttlSeconds > 0) {
            val ttlMillis = ttlSeconds * 1000L
            val refreshMillis = (ttlMillis - DISPATCH_REFRESH_AHEAD_MILLIS).coerceAtLeast(5_000L)
            now + refreshMillis
        } else {
            now + DEFAULT_DISPATCH_REFRESH_INTERVAL_MILLIS
        }
        nextDispatchRefreshAtMillis = next
    }

    private fun buildResolveEndpoints(dispatchResult: DispatchResult): List<ResolveEndpoint> {
        val endpoints = LinkedHashSet<ResolveEndpoint>()
        val domains = dispatchResult.domains.filter { it.isNotBlank() }
        val ips = dispatchResult.ips.filter { it.isNotBlank() }

        domains.forEach { domain -> endpoints.add(ResolveEndpoint(domain, null)) }

        val hostForIp = domains.firstOrNull()
            ?: resolveHost.takeIf { it.isNotBlank() }
            ?: bootstrap.domain
        ips.forEach { ip -> endpoints.add(ResolveEndpoint(hostForIp, ip)) }

        return endpoints.toList()
    }

    private fun buildOverrideEndpoints(host: String, connectIp: String?, includeDnsFallback: Boolean): List<ResolveEndpoint> {
        if (!connectIp.isNullOrBlank()) {
            log("override endpoint configured host=$host ip=${connectIp.trim()}")
            return listOf(ResolveEndpoint(host, connectIp.trim()))
        }

        val endpoints = LinkedHashSet<ResolveEndpoint>()
        endpoints.add(ResolveEndpoint(host, null))

        if (includeDnsFallback) {
            runCatching {
                InetAddress.getAllByName(host)
                    .mapNotNull { it.hostAddress?.takeIf { ip -> ip.isNotBlank() } }
                    .forEach { ip -> endpoints.add(ResolveEndpoint(host, ip)) }
            }.onFailure {
                log("resolve override host dns lookup failed host=$host: ${it.message}")
            }
        }

        log("override endpoint built host=$host size=${endpoints.size}")
        return endpoints.toList()
    }

    private fun replaceResolveEndpoints(endpoints: List<ResolveEndpoint>) = synchronized(this) {
        resolveEndpoints = endpoints
        resolveEndpointCursor = 0
        resolveHost = endpoints.firstOrNull()?.host.orEmpty()
    }
}
