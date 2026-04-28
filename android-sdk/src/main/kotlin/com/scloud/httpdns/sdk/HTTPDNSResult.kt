package com.scloud.httpdns.sdk

class HTTPDNSResult(
    private val host: String,
    private val ips: Array<String>,
    private val ipv6s: Array<String>,
    private val extras: Map<String, String>,
    private val ttl: Int,
    private val expired: Boolean,
) {
    fun getHost(): String = host
    fun getIps(): Array<String> = ips
    fun getIpv6s(): Array<String> = ipv6s
    fun getExtras(): Map<String, String> = extras
    fun getTtl(): Int = ttl
    fun isExpired(): Boolean = expired
}
