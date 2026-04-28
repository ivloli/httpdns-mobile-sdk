package com.scloud.httpdns.sdk

interface HttpDnsService {
    fun setPreResolveHosts(hostList: List<String>) {
        setPreResolveHosts(hostList, RequestIpType.v4)
    }

    fun getHttpDnsResultForHostSync(host: String, requestIpType: RequestIpType): HTTPDNSResult

    fun getHttpDnsResultForHostSync(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult> {
        return hostList.map { getHttpDnsResultForHostSync(it, requestIpType) }
    }

    fun getHttpDnsResultForHostSync(host: String): HTTPDNSResult {
        return getHttpDnsResultForHostSync(host, RequestIpType.v4)
    }

    fun getHttpDnsResultForHostSync(hostList: List<String>): List<HTTPDNSResult> {
        return getHttpDnsResultForHostSync(hostList, RequestIpType.v4)
    }

    fun getHttpDnsResultForHostAsync(
        host: String,
        requestIpType: RequestIpType,
        callback: HttpDnsCallback,
    )

    fun getHttpDnsResultForHostAsync(
        hostList: List<String>,
        requestIpType: RequestIpType,
        callback: HttpDnsBatchCallback,
    ) {
        callback.onHttpDnsBatchCompleted(hostList.map { getHttpDnsResultForHostSync(it, requestIpType) })
    }

    fun getHttpDnsResultForHostAsync(host: String, callback: HttpDnsCallback) {
        getHttpDnsResultForHostAsync(host, RequestIpType.v4, callback)
    }

    fun getHttpDnsResultForHostAsync(hostList: List<String>, callback: HttpDnsBatchCallback) {
        getHttpDnsResultForHostAsync(hostList, RequestIpType.v4, callback)
    }

    @Deprecated("Use getHttpDnsResultForHostAsync(host, type, callback) instead")
    fun getHttpDnsResultForHostAsync(host: String, requestIpType: RequestIpType): HTTPDNSResult {
        return getHttpDnsResultForHostSyncNonBlocking(host, requestIpType)
    }

    @Deprecated("Use getHttpDnsResultForHostAsync(hostList, type, callback) instead")
    fun getHttpDnsResultForHostAsync(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult> {
        return hostList.map { getHttpDnsResultForHostSyncNonBlocking(it, requestIpType) }
    }

    @Deprecated("Use getHttpDnsResultForHostAsync(host, callback) instead")
    fun getHttpDnsResultForHostAsync(host: String): HTTPDNSResult {
        return getHttpDnsResultForHostSyncNonBlocking(host, RequestIpType.v4)
    }

    @Deprecated("Use getHttpDnsResultForHostAsync(hostList, callback) instead")
    fun getHttpDnsResultForHostAsync(hostList: List<String>): List<HTTPDNSResult> {
        return hostList.map { getHttpDnsResultForHostSyncNonBlocking(it, RequestIpType.v4) }
    }

    fun getHttpDnsResultForHostSyncNonBlocking(
        host: String,
        requestIpType: RequestIpType,
    ): HTTPDNSResult

    fun getHttpDnsResultForHostSyncNonBlocking(
        hostList: List<String>,
        requestIpType: RequestIpType,
    ): List<HTTPDNSResult> {
        return hostList.map { getHttpDnsResultForHostSyncNonBlocking(it, requestIpType) }
    }

    fun getHttpDnsResultForHostSyncNonBlocking(host: String): HTTPDNSResult {
        return getHttpDnsResultForHostSyncNonBlocking(host, RequestIpType.v4)
    }

    fun getHttpDnsResultForHostSyncNonBlocking(hostList: List<String>): List<HTTPDNSResult> {
        return getHttpDnsResultForHostSyncNonBlocking(hostList, RequestIpType.v4)
    }

    fun setPreResolveHosts(hostList: List<String>, requestIpType: RequestIpType = RequestIpType.auto)

    fun cleanHostCache(hosts: List<String>?)
}
