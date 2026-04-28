package com.scloud.httpdns.sdk

fun interface HttpDnsCallback {
    fun onHttpDnsCompleted(result: HTTPDNSResult)
}
