package com.scloud.httpdns.sdk

import android.content.Context

class InitConfig private constructor(
    val context: Context,
    val aesSecretKey: String,
    internal val aesSecretKeyBytesForDispatch: ByteArray,
    internal val aesSecretKeyBytesForResolve: ByteArray,
    val region: Region,
    val enableCacheIp: Boolean,
    val cacheExpiredThresholdMillis: Long,
    val enableExpiredIp: Boolean,
    val timeoutMillis: Int,
    val enableHttps: Boolean,
    val useCronet: Boolean,
    val resolveHostOverride: String?,
    val resolveConnectIpOverride: String?,
    val resolvePortOverride: Int?,
    val clientIp: String?,
    val cacheTtlChanger: CacheTtlChanger?,
    val notUseHttpDnsFilter: NotUseHttpDnsFilter?,
    val logger: ILogger?,
) {
    class Builder {
        private var context: Context? = null
        private var aesSecretKey: String? = null
        private var region: Region = Region.DEFAULT
        private var enableCacheIp: Boolean = true
        private var cacheExpiredThresholdMillis: Long = 24 * 60 * 60 * 1000L
        private var enableExpiredIp: Boolean = true
        private var timeoutMillis: Int = 2000
        private var enableHttps: Boolean = true
        private var useCronet: Boolean = true
        private var resolveHostOverride: String? = null
        private var resolveConnectIpOverride: String? = null
        private var resolvePortOverride: Int? = null
        private var clientIp: String? = null
        private var cacheTtlChanger: CacheTtlChanger? = null
        private var notUseHttpDnsFilter: NotUseHttpDnsFilter? = null
        private var logger: ILogger? = null

        fun setContext(context: Context) = apply {
            this.context = context.applicationContext
        }

        fun setAesSecretKey(aesSecretKey: String) = apply {
            this.aesSecretKey = aesSecretKey
        }

        fun setRegion(region: Region) = apply {
            this.region = region
        }

        fun setEnableCacheIp(enableCacheIp: Boolean, expiredThresholdMillis: Long) = apply {
            this.enableCacheIp = enableCacheIp
            this.cacheExpiredThresholdMillis = expiredThresholdMillis
        }

        fun setEnableExpiredIp(enableExpiredIp: Boolean) = apply {
            this.enableExpiredIp = enableExpiredIp
        }

        fun setTimeoutMillis(timeoutMillis: Int) = apply {
            this.timeoutMillis = timeoutMillis.coerceIn(100, 5000)
        }

        fun setEnableHttps(enableHttps: Boolean) = apply {
            this.enableHttps = enableHttps
        }

        fun setUseCronet(useCronet: Boolean) = apply {
            this.useCronet = useCronet
        }

        fun setResolveHostOverride(resolveHostOverride: String?) = apply {
            this.resolveHostOverride = resolveHostOverride?.trim()?.takeIf { it.isNotBlank() }
        }

        fun setResolveEndpoint(resolveHost: String, connectIp: String? = null) = apply {
            this.resolveHostOverride = resolveHost.trim().takeIf { it.isNotBlank() }
            this.resolveConnectIpOverride = connectIp?.trim()?.takeIf { it.isNotBlank() }
        }

        fun setResolvePortOverride(resolvePort: Int?) = apply {
            this.resolvePortOverride = resolvePort
        }

        fun setClientIp(clientIp: String?) = apply {
            this.clientIp = clientIp?.trim()?.takeIf { it.isNotBlank() }
        }

        fun clearResolveEndpointOverride() = apply {
            this.resolveHostOverride = null
            this.resolveConnectIpOverride = null
            this.resolvePortOverride = null
        }

        fun setCacheTtlChanger(cacheTtlChanger: CacheTtlChanger?) = apply {
            this.cacheTtlChanger = cacheTtlChanger
        }

        fun setNotUseHttpDnsFilter(notUseHttpDnsFilter: NotUseHttpDnsFilter?) = apply {
            this.notUseHttpDnsFilter = notUseHttpDnsFilter
        }

        fun setLogger(logger: ILogger?) = apply {
            this.logger = logger
        }

        fun build(): InitConfig {
            val ctx = requireNotNull(context) { "context is required" }
            val aes = requireNotNull(aesSecretKey) { "aesSecretKey is required" }.trim()
            val aesBytesDispatch = parseAesKeyBytesForDispatch(aes)
            val aesBytesResolve = parseAesKeyBytesForResolve(aes)
            val hostOverride = resolveHostOverride?.trim()?.takeIf { it.isNotBlank() }
            val ipOverride = resolveConnectIpOverride?.trim()?.takeIf { it.isNotBlank() }
            val portOverride = resolvePortOverride
            val normalizedClientIp = clientIp?.trim()?.takeIf { it.isNotBlank() }
            require(ipOverride == null || hostOverride != null) {
                "resolveHostOverride is required when resolveConnectIpOverride is set"
            }
            require(portOverride == null || portOverride in 1..65535) {
                "resolvePortOverride must be between 1 and 65535"
            }
            return InitConfig(
                context = ctx,
                aesSecretKey = aes,
                aesSecretKeyBytesForDispatch = aesBytesDispatch,
                aesSecretKeyBytesForResolve = aesBytesResolve,
                region = region,
                enableCacheIp = enableCacheIp,
                cacheExpiredThresholdMillis = cacheExpiredThresholdMillis,
                enableExpiredIp = enableExpiredIp,
                timeoutMillis = timeoutMillis,
                enableHttps = enableHttps,
                useCronet = useCronet,
                resolveHostOverride = hostOverride,
                resolveConnectIpOverride = ipOverride,
                resolvePortOverride = portOverride,
                clientIp = normalizedClientIp,
                cacheTtlChanger = cacheTtlChanger,
                notUseHttpDnsFilter = notUseHttpDnsFilter,
                logger = logger,
            )
        }

        private fun parseAesKeyBytesForDispatch(raw: String): ByteArray {
            val plain = raw.toByteArray(Charsets.UTF_8)
            if (plain.size in setOf(16, 24, 32)) {
                return plain
            }

            if (raw.matches(Regex("^[0-9a-fA-F]{32}$|^[0-9a-fA-F]{48}$|^[0-9a-fA-F]{64}$"))) {
                return raw.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }

            throw IllegalArgumentException("aesSecretKey must be utf8 length in [16,24,32] or hex length in [32,48,64]")
        }

        private fun parseAesKeyBytesForResolve(raw: String): ByteArray {
            if (raw.matches(Regex("^[0-9a-fA-F]{32}$|^[0-9a-fA-F]{48}$|^[0-9a-fA-F]{64}$"))) {
                return raw.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }

            val plain = raw.toByteArray(Charsets.UTF_8)
            if (plain.size in setOf(16, 24, 32)) {
                return plain
            }

            throw IllegalArgumentException("aesSecretKey must be utf8 length in [16,24,32] or hex length in [32,48,64]")
        }
    }
}
