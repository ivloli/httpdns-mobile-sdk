package com.scloud.httpdns.sdk.internal

data class DispatchResult(
    val domains: List<String>,
    val ips: List<String>,
    val ttlSeconds: Int?,
)

data class ResolveItem(
    val host: String,
    val ipsV4: List<String>,
    val ipsV6: List<String>,
    val ttl: Int,
    val extras: Map<String, String>,
)

data class CachedResult(
    val item: ResolveItem,
    val expiredAtMillis: Long,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiredAtMillis
}
