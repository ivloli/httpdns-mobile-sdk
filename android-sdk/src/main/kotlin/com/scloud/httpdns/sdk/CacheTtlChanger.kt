package com.scloud.httpdns.sdk

fun interface CacheTtlChanger {
    fun changeCacheTtl(host: String, type: RequestIpType, ttl: Int): Int
}
