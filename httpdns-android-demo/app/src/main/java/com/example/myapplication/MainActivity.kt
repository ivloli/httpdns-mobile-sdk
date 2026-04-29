package com.example.myapplication

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.scloud.httpdns.sdk.HTTPDNSResult
import com.scloud.httpdns.sdk.HttpDnsBatchCallback
import com.scloud.httpdns.sdk.HttpDns
import com.scloud.httpdns.sdk.InitConfig
import com.scloud.httpdns.sdk.Region
import com.scloud.httpdns.sdk.RequestIpType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Collections
import java.util.concurrent.TimeUnit

private const val ACCOUNT_ID = "430992419037876224"
private const val AES_KEY = "78eb9332f1fc18a45597dbf332858ff4"

private val cacheTraceLines = Collections.synchronizedList(mutableListOf<String>())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ResolveDebugPage(
                        modifier = Modifier.padding(innerPadding),
                        onInit = { cip, updateResult, setLoading ->
                            initializeSdk(cip, updateResult, setLoading)
                        },
                        onPreResolve = { hosts, updateResult, setLoading ->
                            preResolveHosts(hosts, updateResult, setLoading)
                        },
                        onSync = { host, updateResult, setLoading ->
                            resolveSync(host, updateResult, setLoading)
                        },
                        onAsync = { host, updateResult, setLoading ->
                            resolveAsync(host, updateResult, setLoading)
                        },
                        onNonBlocking = { host, updateResult, setLoading ->
                            resolveNonBlocking(host, updateResult, setLoading)
                        },
                        onClearCache = { host, updateResult, setLoading ->
                            clearHostCache(host, updateResult, setLoading)
                        }
                    )
                }
            }
        }
    }

    private fun initializeSdk(
        cipInput: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) {
        lifecycleScope.launch {
            val cip = cipInput.trim()
            clearCacheTrace()
            setLoading(true)
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    val config = buildConfig(cip)
                    HttpDns.init(ACCOUNT_ID, config)
                    "初始化完成\n当前cip(输入): ${if (cip.isBlank()) "<none>" else cip}\n请再点预取/同步/异步/非阻塞按钮"
                }.getOrElse { error ->
                    "初始化失败: ${error.message}\n${error.stackTraceToString()}"
                }
            }
            updateResult(content)
            setLoading(false)
        }
    }

    private fun preResolveHosts(
        hostListInput: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) {
        lifecycleScope.launch {
            val hosts = parseHosts(hostListInput)
            if (hosts.isEmpty()) {
                updateResult("请先输入要预取的域名，支持逗号/空格/换行分隔")
                return@launch
            }

            clearCacheTrace()
            setLoading(true)
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    val service = requireInitializedService()
                    service.setPreResolveHosts(hosts, RequestIpType.both)
                    "已触发预取\nhosts: $hosts\n说明: 这是后台请求，结果会写入缓存，可看 Logcat 里的 preResolve 日志"
                }.getOrElse { error ->
                    "预取失败: ${error.message}\n${error.stackTraceToString()}"
                }
            }
            updateResult(content)
            setLoading(false)
        }
    }

    private fun resolveSync(
        hostInput: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) {
        lifecycleScope.launch {
            val hosts = parseHosts(hostInput)
            if (hosts.isEmpty()) {
                updateResult("请先输入要同步解析的域名")
                return@launch
            }

            clearCacheTrace()
            setLoading(true)
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    val service = requireInitializedService()
                    if (hosts.size == 1) {
                        val host = hosts.first()
                        val dnsResult = service.getHttpDnsResultForHostSync(host, RequestIpType.both)
                        buildReport("同步解析结果", host, dnsResult, "host")
                    } else {
                        val results = service.getHttpDnsResultForHostSync(hosts, RequestIpType.both)
                        buildBatchReport("同步解析结果", hosts, results, "hostList")
                    }
                }.getOrElse { error ->
                    "同步解析失败: ${error.message}\n${error.stackTraceToString()}"
                }
            }
            updateResult(content)
            setLoading(false)
        }
    }

    private fun resolveAsync(
        hostInput: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) {
        lifecycleScope.launch {
            val hosts = parseHosts(hostInput)
            if (hosts.isEmpty()) {
                updateResult("请先输入要异步解析的域名")
                return@launch
            }

            clearCacheTrace()
            setLoading(true)
            withContext(Dispatchers.IO) {
                runCatching {
                    val service = requireInitializedService()
                    if (hosts.size == 1) {
                        val host = hosts.first()
                        service.getHttpDnsResultForHostAsync(host, RequestIpType.both) { result ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val report = buildReport("异步解析结果", host, result, "host")
                                withContext(Dispatchers.Main) {
                                    updateResult(report)
                                    setLoading(false)
                                }
                            }
                        }
                    } else {
                        service.getHttpDnsResultForHostAsync(hosts, RequestIpType.both, object : HttpDnsBatchCallback {
                            override fun onHttpDnsBatchCompleted(results: List<HTTPDNSResult>) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val report = buildBatchReport("异步解析结果", hosts, results, "hostList")
                                    withContext(Dispatchers.Main) {
                                        updateResult(report)
                                        setLoading(false)
                                    }
                                }
                            }
                        })
                    }
                    val mode = if (hosts.size == 1) "host" else "hostList"
                    updateResult("已触发异步解析（调用方式: $mode），结果会在回调里返回。请看下方输出和 Logcat。")
                }.getOrElse { error ->
                    updateResult("异步解析失败: ${error.message}\n${error.stackTraceToString()}")
                    setLoading(false)
                }
            }
        }
    }

    private fun resolveNonBlocking(
        hostInput: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) {
        lifecycleScope.launch {
            val hosts = parseHosts(hostInput)
            if (hosts.isEmpty()) {
                updateResult("请先输入要非阻塞解析的域名")
                return@launch
            }

            clearCacheTrace()
            setLoading(true)
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    val service = requireInitializedService()
                    if (hosts.size == 1) {
                        val host = hosts.first()
                        val dnsResult = service.getHttpDnsResultForHostSyncNonBlocking(host, RequestIpType.both)
                        buildReport("非阻塞解析结果", host, dnsResult, "host")
                    } else {
                        val results = service.getHttpDnsResultForHostSyncNonBlocking(hosts, RequestIpType.both)
                        buildBatchReport("非阻塞解析结果", hosts, results, "hostList")
                    }
                }.getOrElse { error ->
                    "非阻塞解析失败: ${error.message}\n${error.stackTraceToString()}"
                }
            }
            updateResult(content)
            setLoading(false)
        }
    }

    private fun clearHostCache(
        hostInput: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) {
        lifecycleScope.launch {
            val host = hostInput.trim().lowercase()
            if (host.isBlank()) {
                updateResult("请先输入要清缓存的域名")
                return@launch
            }

            clearCacheTrace()
            setLoading(true)
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val service = requireInitializedService()
                    service.cleanHostCache(listOf(host))
                    "已清除域名缓存: $host"
                }.getOrElse { error ->
                    "清缓存失败: ${error.message}\n${error.stackTraceToString()}"
                }
            }
            updateResult(message)
            setLoading(false)
        }
    }

    private fun buildConfig(cip: String): InitConfig {
        val builder = InitConfig.Builder()
            .setContext(applicationContext)
            .setAesSecretKey(AES_KEY)
            .setRegion(Region.DEFAULT)
            .setLogger { msg ->
                Log.d("HTTPDNS", msg)
                recordCacheTrace(msg)
            }

        if (cip.isNotBlank()) {
            builder.setClientIp(cip)
        }
        return builder.build()
    }

    private fun requireInitializedService() = HttpDns.getService(ACCOUNT_ID)

    private fun parseHosts(raw: String): List<String> {
        return raw.replace("\n", ",")
            .replace("\t", ",")
            .replace(";", ",")
            .split(",", " ")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
    }

    private fun buildReport(sectionTitle: String, host: String, result: HTTPDNSResult, callMode: String): String {
        val v4Ips = result.getIps().toList()
        val v6Ips = result.getIpv6s().toList()
        val allIps = (v4Ips + v6Ips).distinct()
        val extras = result.getExtras()
        val returnedCip = extras["cip"] ?: "<none>"
        val browserLikeText = fetchByResolvedIp(host, allIps)

        return buildString {
            appendLine("=== $sectionTitle ===")
            appendLine("accountId: $ACCOUNT_ID")
            appendLine("调用方式: $callMode")
            appendLine("host: ${result.getHost()}")
            appendLine("v4: $v4Ips")
            appendLine("v6: $v6Ips")
            appendLine("ttl: ${result.getTtl()} | expired: ${result.isExpired()}")
            appendLine("extras: $extras")
            appendLine("cip(服务端回显): $returnedCip")
            appendLine()
            appendLine("=== 缓存状态 ===")
            appendLine(cacheSummary())
            appendLine("缓存日志:")
            cacheTraceSnapshot().forEach { appendLine(it) }
            appendLine()
            appendLine("=== 用解析 IP 访问域名（浏览器式 Host/SNI）===")
            appendLine(browserLikeText)
        }
    }

    private fun buildBatchReport(
        sectionTitle: String,
        inputHosts: List<String>,
        results: List<HTTPDNSResult>,
        callMode: String,
    ): String {
        val first = results.firstOrNull()
        val browserLikeText = if (first == null) {
            "无返回结果，跳过访问。"
        } else {
            val allIps = (first.getIps().toList() + first.getIpv6s().toList()).distinct()
            fetchByResolvedIp(first.getHost(), allIps)
        }

        return buildString {
            appendLine("=== $sectionTitle ===")
            appendLine("accountId: $ACCOUNT_ID")
            appendLine("调用方式: $callMode")
            appendLine("输入hosts: $inputHosts")
            appendLine("返回数量: ${results.size}")
            appendLine()

            results.forEachIndexed { index, result ->
                val extras = result.getExtras()
                appendLine("[$index] host=${result.getHost()}")
                appendLine("v4=${result.getIps().toList()}")
                appendLine("v6=${result.getIpv6s().toList()}")
                appendLine("ttl=${result.getTtl()} | expired=${result.isExpired()}")
                appendLine("cip(服务端回显): ${extras["cip"] ?: "<none>"}")
                appendLine("extras=$extras")
                appendLine()
            }

            appendLine("=== 缓存状态 ===")
            appendLine(cacheSummary())
            appendLine("缓存日志:")
            cacheTraceSnapshot().forEach { appendLine(it) }
            appendLine()

            appendLine("=== 用解析 IP 访问域名（示例：首个返回域名）===")
            appendLine(browserLikeText)
        }
    }

    private fun recordCacheTrace(message: String) {
        if (message.contains("cache miss", ignoreCase = true) ||
            message.contains("cache hit", ignoreCase = true) ||
            message.contains("cache write", ignoreCase = true)
        ) {
            cacheTraceLines.add(message)
        }
    }

    private fun clearCacheTrace() {
        cacheTraceLines.clear()
    }

    private fun cacheTraceSnapshot(): List<String> {
        return synchronized(cacheTraceLines) { cacheTraceLines.toList() }
    }

    private fun cacheSummary(): String {
        val snapshot = cacheTraceSnapshot()
        val hit = snapshot.count { it.contains("cache hit", ignoreCase = true) }
        val miss = snapshot.count { it.contains("cache miss", ignoreCase = true) }
        val write = snapshot.count { it.contains("cache write", ignoreCase = true) }
        return "hit=$hit, miss=$miss, write=$write"
    }

    private fun fetchByResolvedIp(host: String, ips: List<String>): String {
        if (ips.isEmpty()) {
            return "无可用解析 IP，跳过访问。"
        }

        val inetAddresses = ips.mapNotNull { ip ->
            runCatching { InetAddress.getByName(ip) }.getOrNull()
        }
        if (inetAddresses.isEmpty()) {
            return "解析 IP 无法转成 InetAddress，跳过访问。"
        }

        val accessTrace = AccessTrace()

        // 访问库：OkHttp。
        // 访问方式：URL 仍然写域名，但通过自定义 Dns 把这个域名解析成 HTTPDNS 返回的 IP 列表。
        // 这样 Host/SNI 还是域名语义，底层 TCP/TLS 连接会优先走这些 IP。
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    // 只接管当前要测试的域名，其他域名仍然走系统 DNS，避免影响页面里其他网络请求。
                    return if (hostname.equals(host, ignoreCase = true)) {
                        inetAddresses
                    } else {
                        Dns.SYSTEM.lookup(hostname)
                    }
                }
            })
            .eventListenerFactory {
                object : EventListener() {
                    override fun connectStart(
                        call: Call,
                        inetSocketAddress: InetSocketAddress,
                        proxy: Proxy,
                    ) {
                        accessTrace.lastConnectStartIp = inetSocketAddress.address?.hostAddress
                            ?: inetSocketAddress.hostString
                    }

                    override fun connectEnd(
                        call: Call,
                        inetSocketAddress: InetSocketAddress,
                        proxy: Proxy,
                        protocol: Protocol?,
                    ) {
                        accessTrace.connectedIp = inetSocketAddress.address?.hostAddress
                            ?: inetSocketAddress.hostString
                    }
                }
            }
            .build()

        val candidates = listOf("https://$host/", "http://$host/")
        val candidateIps = inetAddresses.mapNotNull { it.hostAddress }
        var lastError = ""
        for (url in candidates) {
            val request = Request.Builder().url(url).get().build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val body = response.body?.string().orEmpty()
                    val preview = body.take(1200)
                    val selectedIp = accessTrace.connectedIp
                        ?: accessTrace.lastConnectStartIp
                        ?: "<unknown>"
                    val schemeUsed = if (url.startsWith("https://")) "HTTPS" else "HTTP"
                    return "使用库: OkHttp\n访问策略: 优先 HTTPS，失败再回退 HTTP\n请求域名: $host\n请求URL: $url\n最终协议: $schemeUsed\n候选IP(HTTPDNS): $candidateIps\n实际连接IP: $selectedIp\nHTTP状态码: $code\n返回文本(前1200字符):\n$preview"
                }
            }.onFailure { error ->
                lastError = "${error.javaClass.simpleName}: ${error.message}"
            }
        }

        return "使用库: OkHttp\n访问策略: 优先 HTTPS，失败再回退 HTTP\n请求域名: $host\n候选协议: [HTTPS, HTTP]\n候选IP(HTTPDNS): $candidateIps\n请求失败: https/http 均未成功\n最后错误: $lastError"
    }
}

private data class AccessTrace(
    var lastConnectStartIp: String? = null,
    var connectedIp: String? = null,
)

@Composable
private fun ResolveDebugPage(
    modifier: Modifier = Modifier,
    onInit: (
        cip: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) -> Unit,
    onPreResolve: (
        hostList: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) -> Unit,
    onSync: (
        host: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) -> Unit,
    onAsync: (
        host: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) -> Unit,
    onNonBlocking: (
        host: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) -> Unit,
    onClearCache: (
        host: String,
        updateResult: (String) -> Unit,
        setLoading: (Boolean) -> Unit,
    ) -> Unit,
) {
    var cip by remember { mutableStateOf("") }
    var preResolveHosts by remember { mutableStateOf("www.baidu.com,www.google.com") }
    var syncHost by remember { mutableStateOf("www.baidu.com") }
    var asyncHost by remember { mutableStateOf("www.baidu.com") }
    var nonBlockingHost by remember { mutableStateOf("www.baidu.com") }
    var clearHost by remember { mutableStateOf("www.baidu.com") }
    var selectedTab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("先填 CIP，再点初始化") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "HTTPDNS 联调页", style = MaterialTheme.typography.headlineSmall)

        Text("1. 初始化")
        OutlinedTextField(
            value = cip,
            onValueChange = { cip = it },
            label = { Text("cip（可选，支持 CIDR）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = !loading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = {
                    onInit(cip, { output = it }, { loading = it })
                }
            ) {
                Text("初始化")
            }

            Button(
                enabled = !loading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = {
                    cip = ""
                    preResolveHosts = "www.baidu.com,www.google.com"
                    syncHost = "www.baidu.com"
                    asyncHost = "www.baidu.com"
                    nonBlockingHost = "www.baidu.com"
                    clearHost = "www.baidu.com"
                    selectedTab = 0
                    output = "已重置为初始化前状态"
                }
            ) {
                Text("重置页面")
            }

            Button(
                enabled = !loading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                onClick = {
                    onClearCache(clearHost, { output = it }, { loading = it })
                }
            ) {
                Text("清除缓存")
            }

            if (loading) {
                CircularProgressIndicator()
            }
        }

        Text("2. 四种能力测试")

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("预取") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("同步") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("异步") })
            Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("非阻塞") })
        }

        when (selectedTab) {
            0 -> {
                OutlinedTextField(
                    value = preResolveHosts,
                    onValueChange = { preResolveHosts = it },
                    label = { Text("预取域名列表（逗号/空格/换行分隔）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(enabled = !loading, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), onClick = {
                    onPreResolve(preResolveHosts, { output = it }, { loading = it })
                }) {
                    Text("预取")
                }
            }

            1 -> {
                OutlinedTextField(
                    value = syncHost,
                    onValueChange = { syncHost = it },
                    label = { Text("同步解析域名（支持逗号分隔）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(enabled = !loading, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), onClick = {
                    onSync(syncHost, { output = it }, { loading = it })
                }) {
                    Text("同步解析")
                }
            }

            2 -> {
                OutlinedTextField(
                    value = asyncHost,
                    onValueChange = { asyncHost = it },
                    label = { Text("异步解析域名（支持逗号分隔）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(enabled = !loading, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), onClick = {
                    onAsync(asyncHost, { output = it }, { loading = it })
                }) {
                    Text("异步解析")
                }
            }

            3 -> {
                OutlinedTextField(
                    value = nonBlockingHost,
                    onValueChange = { nonBlockingHost = it },
                    label = { Text("非阻塞解析域名（支持逗号分隔）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(enabled = !loading, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), onClick = {
                    onNonBlocking(nonBlockingHost, { output = it }, { loading = it })
                }) {
                    Text("非阻塞解析")
                }
            }
        }

        Text("3. 缓存清理")
        OutlinedTextField(
            value = clearHost,
            onValueChange = { clearHost = it },
            label = { Text("要清缓存的域名") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = output,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
