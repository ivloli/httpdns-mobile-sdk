package com.scloud.httpdns.sdk.internal

import com.scloud.httpdns.sdk.Region

data class BootstrapConfig(
    val domain: String,
    val allIps: List<String>,
    val regionIps: Map<Region, List<String>>,
)
