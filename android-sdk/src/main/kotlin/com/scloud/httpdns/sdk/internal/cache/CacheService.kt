package com.scloud.httpdns.sdk.internal.cache

import com.scloud.httpdns.sdk.internal.CacheStore

internal class CacheService(
    private val cache: CacheStore,
    private val normalizeHost: (String) -> String?,
) {
    fun cleanHostCache(hosts: List<String>?) {
        if (hosts.isNullOrEmpty()) {
            cache.clearAll()
            return
        }

        val normalized = hosts.mapNotNull { normalizeHost(it) }
        if (normalized.isEmpty()) return
        cache.clearHosts(normalized)
    }
}
