package com.scloud.httpdns.sdk.internal

import android.content.Context
import com.scloud.httpdns.sdk.Region
import org.json.JSONObject

object BootstrapConfigLoader {
    fun load(context: Context): BootstrapConfig {
        return runCatching {
            val text = context.assets.open("bootstrap_endpoints.json").bufferedReader().use { it.readText() }
            fromJson(text)
        }.getOrElse {
            defaultConfig()
        }
    }

    private fun fromJson(text: String): BootstrapConfig {
        val root = JSONObject(text)
        val domain = root.getString("domain")
        val ipsArray = root.getJSONArray("all_ips")
        val allIps = mutableListOf<String>()
        for (i in 0 until ipsArray.length()) {
            allIps.add(ipsArray.getString(i))
        }

        val regionIpsObj = root.optJSONObject("region_ips") ?: JSONObject()
        val regionIps = mutableMapOf<Region, List<String>>()
        regionIpsObj.keys().forEach { key ->
            val region = when (key.lowercase()) {
                "cn" -> Region.CN
                "os" -> Region.OS
                "global" -> Region.GLOBAL
                else -> Region.DEFAULT
            }
            val arr = regionIpsObj.getJSONArray(key)
            val ips = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                ips.add(arr.getString(i))
            }
            regionIps[region] = ips
        }

        return BootstrapConfig(domain = domain, allIps = allIps, regionIps = regionIps)
    }

    private fun defaultConfig(): BootstrapConfig {
        val ips = listOf("8.163.21.3", "8.156.93.88", "39.107.70.15", "47.103.212.241")
        return BootstrapConfig(
            domain = "r.pp.fgnlo.com",
            allIps = ips,
            regionIps = mapOf(
                Region.CN to ips,
                Region.OS to ips,
                Region.GLOBAL to ips,
                Region.DEFAULT to ips,
            ),
        )
    }
}
