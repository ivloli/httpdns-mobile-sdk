package com.scloud.httpdns.sdk.internal.request

import com.scloud.httpdns.sdk.internal.HttpTransport

internal class HttpRequest(
    private val transport: HttpTransport,
    private val config: HttpRequestConfig,
) {
    fun execute(): String {
        return transport.get(
            host = config.host,
            pathWithQuery = config.pathWithQuery,
            connectIp = config.connectIp,
            portOverride = config.portOverride,
        )
    }
}
