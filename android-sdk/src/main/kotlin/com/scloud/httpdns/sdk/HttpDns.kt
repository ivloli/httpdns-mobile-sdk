package com.scloud.httpdns.sdk

import android.content.Context
import com.scloud.httpdns.sdk.internal.DefaultHttpDnsService
import java.util.concurrent.ConcurrentHashMap

object HttpDns {
    private val services = ConcurrentHashMap<String, DefaultHttpDnsService>()

    @Synchronized
    fun init(accountId: String, config: InitConfig) {
        val existing = services[accountId]
        if (existing == null) {
            services[accountId] = DefaultHttpDnsService(accountId, config)
        } else {
            existing.updateConfig(config)
        }
    }

    fun getService(accountId: String): HttpDnsService {
        return requireNotNull(services[accountId]) {
            "HttpDns for accountId=$accountId is not initialized. Call HttpDns.init(...) first."
        }
    }

    @Synchronized
    fun getService(accountId: String, config: InitConfig): HttpDnsService {
        init(accountId, config)
        return requireNotNull(services[accountId])
    }

    @Deprecated("Use HttpDns.init(accountId, config) + HttpDns.getService(accountId)")
    @Synchronized
    fun getService(context: Context, accountId: String): HttpDnsService {
        val existing = services[accountId]
        requireNotNull(existing) {
            "HttpDns for accountId=$accountId is not initialized. Please call HttpDns.init(accountId, config) first."
        }
        return existing
    }
}
