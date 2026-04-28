package com.scloud.httpdns.sdk.internal.request

internal data class HttpRequestConfig(
    val host: String,
    val pathWithQuery: String,
    val connectIp: String?,
    val portOverride: Int?,
)
