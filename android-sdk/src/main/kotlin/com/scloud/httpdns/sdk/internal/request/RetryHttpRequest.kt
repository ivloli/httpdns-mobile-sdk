package com.scloud.httpdns.sdk.internal.request

import com.scloud.httpdns.sdk.internal.HttpTransport

internal class RetryHttpRequest(
    private val transport: HttpTransport,
) {
    fun <T> execute(configs: List<HttpRequestConfig>, watcher: HttpRequestWatcher<T>): T? {
        configs.forEach { config ->
            watcher.onStart(config)
            val result = runCatching {
                HttpRequest(transport, config).execute()
            }
            if (result.isSuccess) {
                val mapped = watcher.onSuccess(config, result.getOrThrow())
                if (mapped != null) {
                    return mapped
                }
            } else {
                watcher.onFail(config, result.exceptionOrNull() ?: IllegalStateException("request failed"))
            }
        }
        return null
    }
}
