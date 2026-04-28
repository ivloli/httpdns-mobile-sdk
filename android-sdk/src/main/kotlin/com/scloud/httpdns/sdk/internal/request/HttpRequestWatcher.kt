package com.scloud.httpdns.sdk.internal.request

internal interface HttpRequestWatcher<T> {
    fun onStart(config: HttpRequestConfig)
    fun onSuccess(config: HttpRequestConfig, responseBody: String): T?
    fun onFail(config: HttpRequestConfig, throwable: Throwable)
}
