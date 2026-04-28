package com.scloud.httpdns.sdk

enum class RequestIpType(val queryValue: String) {
    v4("4"),
    v6("6"),
    both("4,6"),
    auto("4,6");

    companion object {
        @JvmField val V4: RequestIpType = v4
        @JvmField val V6: RequestIpType = v6
        @JvmField val BOTH: RequestIpType = both
        @JvmField val AUTO: RequestIpType = auto
    }
}
