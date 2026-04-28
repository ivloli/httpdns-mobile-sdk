package com.scloud.httpdns.sdk.internal.resolve

import com.scloud.httpdns.sdk.HTTPDNSResult
import com.scloud.httpdns.sdk.InitConfig
import com.scloud.httpdns.sdk.RequestIpType
import com.scloud.httpdns.sdk.internal.CacheStore
import com.scloud.httpdns.sdk.internal.HttpTransport
import com.scloud.httpdns.sdk.internal.ResolveItem
import com.scloud.httpdns.sdk.internal.adapter.RequestAdapter
import com.scloud.httpdns.sdk.internal.adapter.ResponseAdapter
import com.scloud.httpdns.sdk.internal.dispatch.DispatchService
import com.scloud.httpdns.sdk.internal.dispatch.ResolveEndpoint
import com.scloud.httpdns.sdk.internal.request.HttpRequestConfig
import com.scloud.httpdns.sdk.internal.request.HttpRequestWatcher
import com.scloud.httpdns.sdk.internal.request.RetryHttpRequest
import java.util.concurrent.atomic.AtomicReference

internal class ResolveRequestExecutor(
    private val accountId: String,
    private val configRef: AtomicReference<InitConfig>,
    private val cache: CacheStore,
    private val dispatchService: DispatchService,
    private val toPublicResult: (ResolveItem, Boolean) -> HTTPDNSResult,
    private val log: (String) -> Unit,
) {
    fun requestResolveBatch(hosts: List<String>, requestIpType: RequestIpType, source: String) {
        val targetHosts = hosts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (targetHosts.isEmpty()) return

        val config = configRef.get()
        val query = RequestAdapter.buildResolvePath(
            accountId = accountId,
            aesSecretKeyBytes = config.aesSecretKeyBytesForResolve,
            host = targetHosts.joinToString(","),
            requestIpType = requestIpType,
            expEpochSeconds = System.currentTimeMillis() / 1000 + 600,
            clientIp = config.clientIp,
        )
        val transport = HttpTransport(config.context, config.timeoutMillis, config.enableHttps, config.useCronet)
        val retryHttpRequest = RetryHttpRequest(transport)

        var lastError: Throwable? = null

        val firstRoundResult = retryHttpRequest.execute(
            configs = dispatchService.orderedResolveEndpointsSnapshot().map { endpoint ->
                HttpRequestConfig(
                    host = endpoint.host,
                    pathWithQuery = query,
                    connectIp = endpoint.connectIp,
                    portOverride = config.resolvePortOverride,
                )
            },
            watcher = object : HttpRequestWatcher<Boolean> {
                override fun onStart(config: HttpRequestConfig) {
                    log("resolve batch attempt endpoint host=${config.host} ip=${config.connectIp ?: "<dns>"} port=${config.portOverride ?: 443}")
                }

                override fun onSuccess(config: HttpRequestConfig, responseBody: String): Boolean? {
                    return runCatching {
                        val decrypted = ResponseAdapter.decryptResolvePayload(configRef.get().aesSecretKeyBytesForResolve, responseBody)
                        val resolvedItems = ResponseAdapter.parseResolvePayloadBatch(
                            requestIpType = requestIpType,
                            payload = decrypted,
                            ttlMapper = configRef.get().cacheTtlChanger?.let { changer ->
                                { targetHost, targetType, ttl -> changer.changeCacheTtl(targetHost, targetType, ttl) }
                            },
                        )
                        if (resolvedItems.isEmpty()) {
                            null
                        } else {
                            dispatchService.markEndpointSucceeded(ResolveEndpoint(config.host, config.connectIp))
                            val now = System.currentTimeMillis()
                            resolvedItems.forEach { resolved ->
                                if (configRef.get().enableCacheIp) {
                                    cache.put(resolved.host, requestIpType, resolved, now)
                                }
                                log(
                                    "source=$source cache write host=${resolved.host} type=${requestIpType.name} ttl=${resolved.ttl} " +
                                        "v4=${resolved.ipsV4.size} ipsV4=${resolved.ipsV4} " +
                                        "v6=${resolved.ipsV6.size} ipsV6=${resolved.ipsV6}"
                                )
                            }
                            log("resolve batch success endpoint host=${config.host} ip=${config.connectIp ?: "<dns>"} answers=${resolvedItems.size}")
                            true
                        }
                    }.getOrElse { throwable ->
                        onFail(config, throwable)
                        null
                    }
                }

                override fun onFail(config: HttpRequestConfig, throwable: Throwable) {
                    log("resolve batch failed endpoint host=${config.host} ip=${config.connectIp ?: "<dns>"}: ${throwable.message}")
                    lastError = throwable
                }
            },
        )
        if (firstRoundResult == true) return

        if (dispatchService.orderedResolveEndpointsSnapshot().isEmpty()) {
            log("no resolve endpoints available after initial dispatch, skip resolve batch")
            throw (lastError ?: IllegalStateException("no resolve endpoints available"))
        }

        log("all current endpoints failed, force refresh dispatch/override endpoints for resolve batch")
        dispatchService.refresh(forceExpandOverride = true)

        val secondRoundEndpoints = dispatchService.orderedResolveEndpointsSnapshot()
        if (secondRoundEndpoints.isEmpty()) {
            log("no resolve endpoints available after dispatch refresh, skip resolve batch")
            throw (lastError ?: IllegalStateException("no resolve endpoints available after refresh"))
        }

        val secondRoundResult = retryHttpRequest.execute(
            configs = secondRoundEndpoints.map { endpoint ->
                HttpRequestConfig(
                    host = endpoint.host,
                    pathWithQuery = query,
                    connectIp = endpoint.connectIp,
                    portOverride = config.resolvePortOverride,
                )
            },
            watcher = object : HttpRequestWatcher<Boolean> {
                override fun onStart(config: HttpRequestConfig) {
                    log("resolve batch retry endpoint host=${config.host} ip=${config.connectIp ?: "<dns>"} port=${config.portOverride ?: 443}")
                }

                override fun onSuccess(config: HttpRequestConfig, responseBody: String): Boolean? {
                    return runCatching {
                        val decrypted = ResponseAdapter.decryptResolvePayload(configRef.get().aesSecretKeyBytesForResolve, responseBody)
                        val resolvedItems = ResponseAdapter.parseResolvePayloadBatch(
                            requestIpType = requestIpType,
                            payload = decrypted,
                            ttlMapper = configRef.get().cacheTtlChanger?.let { changer ->
                                { targetHost, targetType, ttl -> changer.changeCacheTtl(targetHost, targetType, ttl) }
                            },
                        )
                        if (resolvedItems.isEmpty()) {
                            null
                        } else {
                            dispatchService.markEndpointSucceeded(ResolveEndpoint(config.host, config.connectIp))
                            val now = System.currentTimeMillis()
                            resolvedItems.forEach { resolved ->
                                if (configRef.get().enableCacheIp) {
                                    cache.put(resolved.host, requestIpType, resolved, now)
                                }
                                log(
                                    "source=$source cache write host=${resolved.host} type=${requestIpType.name} ttl=${resolved.ttl} " +
                                        "v4=${resolved.ipsV4.size} ipsV4=${resolved.ipsV4} " +
                                        "v6=${resolved.ipsV6.size} ipsV6=${resolved.ipsV6}"
                                )
                            }
                            log("resolve batch success after refresh host=${config.host} ip=${config.connectIp ?: "<dns>"} answers=${resolvedItems.size}")
                            true
                        }
                    }.getOrElse { throwable ->
                        onFail(config, throwable)
                        null
                    }
                }

                override fun onFail(config: HttpRequestConfig, throwable: Throwable) {
                    log("resolve batch retry failed host=${config.host} ip=${config.connectIp ?: "<dns>"}: ${throwable.message}")
                    lastError = throwable
                }
            },
        )
        if (secondRoundResult == true) return

        throw (lastError ?: IllegalStateException("resolve batch failed for all endpoints"))
    }

    fun requestResolve(host: String, requestIpType: RequestIpType, source: String): HTTPDNSResult {
        val config = configRef.get()
        val query = RequestAdapter.buildResolvePath(
            accountId = accountId,
            aesSecretKeyBytes = config.aesSecretKeyBytesForResolve,
            host = host,
            requestIpType = requestIpType,
            expEpochSeconds = System.currentTimeMillis() / 1000 + 600,
            clientIp = config.clientIp,
        )
        val transport = HttpTransport(config.context, config.timeoutMillis, config.enableHttps, config.useCronet)
        val retryHttpRequest = RetryHttpRequest(transport)

        var lastError: Throwable? = null

        val firstRoundResult = retryHttpRequest.execute(
            configs = dispatchService.orderedResolveEndpointsSnapshot().map { endpoint ->
                HttpRequestConfig(
                    host = endpoint.host,
                    pathWithQuery = query,
                    connectIp = endpoint.connectIp,
                    portOverride = config.resolvePortOverride,
                )
            },
            watcher = object : HttpRequestWatcher<HTTPDNSResult> {
                override fun onStart(config: HttpRequestConfig) {
                    log("resolve attempt endpoint host=${config.host} ip=${config.connectIp ?: "<dns>"} port=${config.portOverride ?: 443}")
                }

                override fun onSuccess(config: HttpRequestConfig, responseBody: String): HTTPDNSResult? {
                    return runCatching {
                        val decrypted = ResponseAdapter.decryptResolvePayload(configRef.get().aesSecretKeyBytesForResolve, responseBody)
                        val resolved = ResponseAdapter.parseResolvePayload(
                            requestHost = host,
                            requestIpType = requestIpType,
                            payload = decrypted,
                            ttlMapper = configRef.get().cacheTtlChanger?.let { changer ->
                                { targetHost, targetType, ttl -> changer.changeCacheTtl(targetHost, targetType, ttl) }
                            },
                        )

                        dispatchService.markEndpointSucceeded(ResolveEndpoint(config.host, config.connectIp))
                        if (configRef.get().enableCacheIp) {
                            cache.put(host, requestIpType, resolved, System.currentTimeMillis())
                        }
                        log(
                            "source=$source cache write host=$host type=${requestIpType.name} ttl=${resolved.ttl} " +
                                "v4=${resolved.ipsV4.size} ipsV4=${resolved.ipsV4} " +
                                "v6=${resolved.ipsV6.size} ipsV6=${resolved.ipsV6}"
                        )
                        log("resolve success endpoint host=${config.host} ip=${config.connectIp ?: "<dns>"}")
                        toPublicResult(resolved, false)
                    }.getOrElse { throwable ->
                        onFail(config, throwable)
                        null
                    }
                }

                override fun onFail(config: HttpRequestConfig, throwable: Throwable) {
                    log("resolve failed endpoint host=${config.host} ip=${config.connectIp ?: "<dns>"}: ${throwable.message}")
                    lastError = throwable
                }
            },
        )
        if (firstRoundResult != null) return firstRoundResult

        if (dispatchService.orderedResolveEndpointsSnapshot().isEmpty()) {
            log("no resolve endpoints available after initial dispatch, skip resolve")
            throw (lastError ?: IllegalStateException("no resolve endpoints available"))
        }

        log("all current endpoints failed, force refresh dispatch/override endpoints")
        dispatchService.refresh(forceExpandOverride = true)

        val secondRoundEndpoints = dispatchService.orderedResolveEndpointsSnapshot()
        if (secondRoundEndpoints.isEmpty()) {
            log("no resolve endpoints available after dispatch refresh, skip resolve")
            throw (lastError ?: IllegalStateException("no resolve endpoints available after refresh"))
        }

        val secondRoundResult = retryHttpRequest.execute(
            configs = secondRoundEndpoints.map { endpoint ->
                HttpRequestConfig(
                    host = endpoint.host,
                    pathWithQuery = query,
                    connectIp = endpoint.connectIp,
                    portOverride = config.resolvePortOverride,
                )
            },
            watcher = object : HttpRequestWatcher<HTTPDNSResult> {
                override fun onStart(config: HttpRequestConfig) {
                    log("resolve retry endpoint host=${config.host} ip=${config.connectIp ?: "<dns>"} port=${config.portOverride ?: 443}")
                }

                override fun onSuccess(config: HttpRequestConfig, responseBody: String): HTTPDNSResult? {
                    return runCatching {
                        val decrypted = ResponseAdapter.decryptResolvePayload(configRef.get().aesSecretKeyBytesForResolve, responseBody)
                        val resolved = ResponseAdapter.parseResolvePayload(
                            requestHost = host,
                            requestIpType = requestIpType,
                            payload = decrypted,
                            ttlMapper = configRef.get().cacheTtlChanger?.let { changer ->
                                { targetHost, targetType, ttl -> changer.changeCacheTtl(targetHost, targetType, ttl) }
                            },
                        )

                        dispatchService.markEndpointSucceeded(ResolveEndpoint(config.host, config.connectIp))
                        if (configRef.get().enableCacheIp) {
                            cache.put(host, requestIpType, resolved, System.currentTimeMillis())
                        }
                        log(
                            "source=$source cache write host=$host type=${requestIpType.name} ttl=${resolved.ttl} " +
                                "v4=${resolved.ipsV4.size} ipsV4=${resolved.ipsV4} " +
                                "v6=${resolved.ipsV6.size} ipsV6=${resolved.ipsV6}"
                        )
                        log("resolve success after refresh host=${config.host} ip=${config.connectIp ?: "<dns>"}")
                        toPublicResult(resolved, false)
                    }.getOrElse { throwable ->
                        onFail(config, throwable)
                        null
                    }
                }

                override fun onFail(config: HttpRequestConfig, throwable: Throwable) {
                    log("resolve retry failed host=${config.host} ip=${config.connectIp ?: "<dns>"}: ${throwable.message}")
                    lastError = throwable
                }
            },
        )
        if (secondRoundResult != null) return secondRoundResult

        throw (lastError ?: IllegalStateException("resolve failed for all endpoints"))
    }
}
