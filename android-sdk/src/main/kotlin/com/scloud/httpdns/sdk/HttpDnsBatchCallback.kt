package com.scloud.httpdns.sdk

fun interface HttpDnsBatchCallback {
    fun onHttpDnsBatchCompleted(results: List<HTTPDNSResult>)
}
