package com.scloud.httpdns.sdk.internal

import com.scloud.httpdns.sdk.RequestIpType
import java.util.concurrent.ConcurrentHashMap

internal class CacheStore {
    private val map = ConcurrentHashMap<String, CachedResult>()

    fun get(host: String, type: RequestIpType): CachedResult? {
        return map[key(host, type)]
    }

    fun put(host: String, type: RequestIpType, item: ResolveItem, nowMillis: Long) {
        val ttlMillis = item.ttl.coerceAtLeast(1) * 1000L
        map[key(host, type)] = CachedResult(item, nowMillis + ttlMillis)
    }

    fun clearHosts(hosts: Collection<String>) {
        val hostSet = hosts.toSet()
        if (hostSet.isEmpty()) return
        for (cacheKey in map.keys.toList()) {
            if (hostSet.any { host -> cacheKey.startsWith("$host:") }) {
                map.remove(cacheKey)
            }
        }
    }

    fun clearAll() {
        map.clear()
    }

    private fun key(host: String, type: RequestIpType): String = "$host:${type.name}"
}
