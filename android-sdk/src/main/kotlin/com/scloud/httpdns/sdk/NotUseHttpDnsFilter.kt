package com.scloud.httpdns.sdk

fun interface NotUseHttpDnsFilter {
    fun notUseHttpDns(host: String): Boolean
}
