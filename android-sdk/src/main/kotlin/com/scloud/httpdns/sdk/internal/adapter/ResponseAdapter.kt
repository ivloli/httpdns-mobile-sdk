package com.scloud.httpdns.sdk.internal.adapter

import com.scloud.httpdns.sdk.RequestIpType
import com.scloud.httpdns.sdk.internal.Crypto
import com.scloud.httpdns.sdk.internal.DispatchResult
import com.scloud.httpdns.sdk.internal.ResolveItem
import org.json.JSONObject

internal object ResponseAdapter {
    fun decryptResolvePayload(aesSecretKeyBytes: ByteArray, rawResponseBody: String): String {
        val data = JSONObject(rawResponseBody).optString("data")
        return Crypto.decryptFromHex(aesSecretKeyBytes, data)
    }

    fun decryptDispatchPayload(aesSecretKeyBytes: ByteArray, rawResponseBody: String): String {
        val data = JSONObject(rawResponseBody).optString("data")
        return Crypto.decryptFromHex(aesSecretKeyBytes, data)
    }

    fun parseDispatchPayload(payload: String): DispatchResult {
        val root = JSONObject(payload)
        val list = root.optJSONArray("list")
        if (list == null || list.length() == 0) return DispatchResult(emptyList(), emptyList(), null)

        val first = list.optJSONObject(0) ?: return DispatchResult(emptyList(), emptyList(), null)
        val domains = jsonArrayToList(first.optJSONArray("domains"))
        val ips = jsonArrayToList(first.optJSONArray("ips"))
        val ttlSeconds = first.optLong("ttl", -1L).takeIf { it > 0 }?.toInt()
        return DispatchResult(domains = domains, ips = ips, ttlSeconds = ttlSeconds)
    }

    fun parseResolvePayload(
        requestHost: String,
        requestIpType: RequestIpType,
        payload: String,
        ttlMapper: ((String, RequestIpType, Int) -> Int)?,
    ): ResolveItem {
        val batch = parseResolvePayloadBatch(requestIpType, payload, ttlMapper)
        val matched = batch.firstOrNull { it.host.equals(requestHost, ignoreCase = true) }
        if (matched != null) return matched
        if (batch.isNotEmpty()) return batch.first()

        return ResolveItem(
            host = requestHost,
            ipsV4 = emptyList(),
            ipsV6 = emptyList(),
            ttl = ttlMapper?.invoke(requestHost, requestIpType, 60)?.coerceAtLeast(1) ?: 60,
            extras = emptyMap(),
        )
    }

    fun parseResolvePayloadBatch(
        requestIpType: RequestIpType,
        payload: String,
        ttlMapper: ((String, RequestIpType, Int) -> Int)?,
    ): List<ResolveItem> {
        val root = JSONObject(payload)
        val answers = root.optJSONArray("answers") ?: return emptyList()

        val out = ArrayList<ResolveItem>(answers.length())
        for (index in 0 until answers.length()) {
            val answer = answers.optJSONObject(index) ?: continue
            val resolvedHost = answer.optString("dn").takeIf { it.isNotBlank() } ?: continue

            val v4Obj = answer.optJSONObject("v4")
            val v6Obj = answer.optJSONObject("v6")
            val rawTtl = answer.optInt("ttl", 60)
            val ttl = ttlMapper?.invoke(resolvedHost, requestIpType, rawTtl)?.coerceAtLeast(1) ?: rawTtl

            val extras = linkedMapOf<String, String>()
            extras.putIfNotBlank("v4_extra", v4Obj?.optString("extra"))
            extras.putIfNotBlank("v4_no_ip_code", v4Obj?.optString("no_ip_code"))
            extras.putIfNotBlank("v6_extra", v6Obj?.optString("extra"))
            extras.putIfNotBlank("v6_no_ip_code", v6Obj?.optString("no_ip_code"))
            extras.putIfNotBlank("cip", root.optString("cip"))
            extras.putIfPositiveInt("latency", root.optInt("latency", -1))

            collectUnknownPrimitiveExtras(root, extras, setOf("answers", "cip", "latency"), "")
            collectUnknownPrimitiveExtras(answer, extras, setOf("dn", "v4", "v6", "ttl"), "answer_")
            collectUnknownPrimitiveExtras(v4Obj, extras, setOf("ips", "extra", "ttl", "no_ip_code"), "v4_")
            collectUnknownPrimitiveExtras(v6Obj, extras, setOf("ips", "extra", "ttl", "no_ip_code"), "v6_")

            out.add(
                ResolveItem(
                    host = resolvedHost,
                    ipsV4 = jsonArrayToList(v4Obj?.optJSONArray("ips")),
                    ipsV6 = jsonArrayToList(v6Obj?.optJSONArray("ips")),
                    ttl = ttl,
                    extras = extras,
                )
            )
        }

        return out
    }

    private fun jsonArrayToList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (index in 0 until arr.length()) {
            out.add(arr.optString(index))
        }
        return out.filter { it.isNotBlank() }
    }

    private fun collectUnknownPrimitiveExtras(
        obj: JSONObject?,
        extras: MutableMap<String, String>,
        excludeKeys: Set<String>,
        prefix: String,
    ) {
        if (obj == null) return
        obj.keys().forEach { key ->
            if (excludeKeys.contains(key)) return@forEach
            val value = obj.opt(key)
            val extraKey = "$prefix$key"
            when (value) {
                is String -> if (value.isNotBlank()) extras.putIfAbsent(extraKey, value)
                is Number, is Boolean -> extras.putIfAbsent(extraKey, value.toString())
            }
        }
    }

    private fun MutableMap<String, String>.putIfNotBlank(key: String, value: String?) {
        if (!value.isNullOrBlank()) {
            this[key] = value
        }
    }

    private fun MutableMap<String, String>.putIfPositiveInt(key: String, value: Int) {
        if (value >= 0) {
            this[key] = value.toString()
        }
    }
}
