import SwiftUI
import Combine
import Network
import Security
import ScloudHTTPDNS

struct ContentView: View {
    @StateObject private var model = HttpDnsDemoViewModel()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("HTTPDNS Demo")
                    .font(.largeTitle.bold())

                GroupBox("Config") {
                    VStack(spacing: 12) {
                        labeledField("Host / Hosts", text: $model.host)
                        labeledField("Client IP", text: $model.clientIp)
                    }
                }

                GroupBox("Actions") {
                    VStack(alignment: .leading, spacing: 16) {
                        actionSection(title: "Init & Cache") {
                            actionButton("Init") { model.initialize() }
                            actionButton("Reset") { model.reset() }
                            actionButton("ClearCache") { model.clearCache() }
                        }

                        actionSection(title: "Resolve") {
                            actionButton("PreResolve") { model.preResolve() }
                            actionButton("Sync") { model.resolveSync() }
                            actionButton("Async") { model.resolveAsync() }
                            actionButton("NonBlocking") { model.resolveNonBlocking() }
                        }
                    }
                }

                GroupBox("Result") {
                    TextEditor(text: $model.output)
                        .font(.system(.footnote, design: .monospaced))
                        .frame(minHeight: 420)
                }

                GroupBox("Browser-like Access") {
                    TextEditor(text: $model.browserOutput)
                        .font(.system(.footnote, design: .monospaced))
                        .frame(minHeight: 240)
                }

                GroupBox("Logs") {
                    TextEditor(text: $model.logsText)
                        .font(.system(.footnote, design: .monospaced))
                        .frame(minHeight: 260)
                }
            }
            .padding()
        }
    }

    private func labeledField(_ title: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            TextField(title, text: text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .textFieldStyle(.roundedBorder)
        }
    }

    private func buttonRow<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        HStack(spacing: 12) {
            content()
        }
    }

    private func actionSection<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)

            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12)
                ],
                spacing: 12
            ) {
                content()
            }
        }
    }

    private func actionButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.regular)
            .frame(maxWidth: .infinity)
            .frame(height: 44)
    }
}

final class HttpDnsDemoViewModel: ObservableObject {
    // 这个 demo 的目标是“看清 HTTPDNS 的解析和访问效果”，
    // 所以把账号信息固定在代码里，页面上只保留联调时最常改的输入项。
    @Published var host = "www.baidu.com"
    @Published var clientIp = ""
    @Published var output = "Ready. Tap Init first."
    @Published var browserOutput = "Run a resolve first."
    @Published var logsText = ""

    private let accountID = "430992419037876224"
    private let aesKey = "78eb9332f1fc18a45597dbf332858ff4"

    private var service: HttpDnsService?
    private var browserTaskID = UUID()
    private lazy var logger = DemoLogger { [weak self] message in
        DispatchQueue.main.async {
            self?.appendLog(message)
        }
    }

    func initialize() {
        browserTaskID = UUID()
        // 第一步：初始化 SDK。
        // 这里和 Android demo 一样，初始化完成后，后面的预取 / 同步 / 异步 / 非阻塞能力才能正常调用。
        let trimmedClientIp = clientIp.trimmingCharacters(in: .whitespacesAndNewlines)
        service = rebuildService(clientIp: trimmedClientIp.isEmpty ? nil : trimmedClientIp)
        let clientIpText = trimmedClientIp.isEmpty ? "<none>" : trimmedClientIp

        output = [
            "Init done",
            "clientIp: \(clientIpText)",
            "cache: enabled",
            "https: default"
        ].joined(separator: "\n")
        browserOutput = "Run a resolve first."
        appendLog("Init done: accountId=\(accountID), clientIp=\(clientIpText)")
    }

    func reset() {
        browserTaskID = UUID()
        let trimmedClientIp = clientIp.trimmingCharacters(in: .whitespacesAndNewlines)

        // Reset 要做三件事：
        // 1. 清掉内存/持久化里的全部 host 缓存
        // 2. 丢掉当前 service 状态
        // 3. 按当前输入重新初始化一遍 SDK
        service?.cleanHostCache(nil)
        service = rebuildService(clientIp: trimmedClientIp.isEmpty ? nil : trimmedClientIp)
        service?.cleanHostCache(nil)

        let clientIpText = trimmedClientIp.isEmpty ? "<none>" : trimmedClientIp
        output = [
            "Reset done",
            "clientIp: \(clientIpText)",
            "cache: cleared",
            "sdk: reinitialized"
        ].joined(separator: "\n")
        browserOutput = "Run a resolve first."
        logsText = ""
        appendLog("Reset done: cache cleared, sdk reinitialized, clientIp=\(clientIpText)")
    }

    func preResolve() {
        performSingleHostAction(title: "PreResolve") { service, _ in
            let hostList = parseHosts(host)
            guard !hostList.isEmpty else {
                output = "Please enter hosts first."
                appendLog("PreResolve skipped: no hosts")
                return
            }

            service.setPreResolveHosts(hostList, byIPType: .both)
            service.setPreResolveHosts(hostList)
            output = "Pre-resolve triggered for: \(hostList)"
            appendLog("PreResolve triggered: \(hostList)")
            browserOutput = "Pre-resolve completed. Run Sync / Async / NonBlocking to populate browser-like access."
        }
    }

    func resolveSync() {
        performSingleHostAction(title: "Sync") { service, host in
            let hostList = parseHosts(host)
            if hostList.count > 1 {
                let results = service.getHttpDnsResultForHostSync(hostList, byIPType: .both)
                output = formatBatchResult(title: "Sync", results: results)
                appendLog("Sync batch resolved: \(hostList.count) hosts")
                updateBrowserAccess(host: host, results: results)
            } else {
                let result = service.getHttpDnsResultForHostSync(host, byIPType: .both)
                output = formatResult(title: "Sync", result: result)
                appendLog("Sync resolved host: \(host)")
                updateBrowserAccess(host: host, results: [result])
            }
        }
    }

    func resolveAsync() {
        guard let service else {
            output = "Tap Init first."
            return
        }

        let hostList = parseHosts(host)
        guard !hostList.isEmpty else {
            output = "Please enter hosts first."
            return
        }

        output = "Async started..."
        if hostList.count > 1 {
            service.getHttpDnsResultForHostAsync(hostList, byIPType: .both) { [weak self] results in
                DispatchQueue.main.async {
                    self?.output = self?.formatBatchResult(title: "Async", results: results) ?? ""
                    self?.appendLog("Async batch completed: \(results.count) hosts")
                    self?.updateBrowserAccess(host: hostList.first ?? hostList[0], results: results)
                }
            }
        } else {
            service.getHttpDnsResultForHostAsync(hostList[0], byIPType: .both) { [weak self] result in
                DispatchQueue.main.async {
                    self?.output = self?.formatResult(title: "Async", result: result) ?? ""
                    self?.appendLog("Async resolved host: \(result.host)")
                    self?.updateBrowserAccess(host: result.host, results: [result])
                }
            }
        }
    }

    func resolveNonBlocking() {
        performSingleHostAction(title: "NonBlocking") { service, host in
            let hostList = parseHosts(host)
            if hostList.count > 1 {
                let results = service.getHttpDnsResultForHostSyncNonBlocking(hostList, byIPType: .both)
                output = formatBatchResult(title: "NonBlocking", results: results)
                appendLog("NonBlocking batch resolved: \(hostList.count) hosts")
                updateBrowserAccess(host: host, results: results)
            } else {
                let result = service.getHttpDnsResultForHostSyncNonBlocking(host, byIPType: .both)
                output = formatResult(title: "NonBlocking", result: result)
                appendLog("NonBlocking resolved host: \(host)")
                updateBrowserAccess(host: host, results: [result])
            }
        }
    }

    func clearCache() {
        guard let service else {
            output = "Tap Init first."
            return
        }

        let hostValue = host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !hostValue.isEmpty else {
            output = "Please enter a host first."
            appendLog("ClearCache skipped: empty host")
            return
        }

        service.cleanHostCache([hostValue])
        output = "Cleared cache for: \(hostValue)"
        appendLog("Cache cleared: \(hostValue)")
    }

    private func performSingleHostAction(title: String, work: (HttpDnsService, String) -> Void) {
        guard let service else {
            output = "Tap Init first."
            appendLog("\(title) skipped: service not initialized")
            return
        }

        let hostValue = host.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !hostValue.isEmpty else {
            output = "Please enter a host first."
            appendLog("\(title) skipped: empty host")
            return
        }

        work(service, hostValue)
    }

    private func parseHosts(_ raw: String) -> [String] {
        // 页面上只有一个 Host / Hosts 输入框。
        // 这里把它统一解析成列表，这样：
        // 1. 输入单个域名时，可以直接测单域名接口
        // 2. 输入多个域名时，可以直接测批量接口
        // 3. 支持逗号 / 分号 / 换行，方便直接粘贴测试数据
        raw
            .replacingOccurrences(of: "\n", with: ",")
            .replacingOccurrences(of: "\t", with: ",")
            .replacingOccurrences(of: ";", with: ",")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
            .filter { !$0.isEmpty }
    }

    private func formatResult(title: String, result: HttpdnsResult) -> String {
        let lines = [
            "=== \(title) ===",
            "host: \(result.host)",
            "v4: \(result.ips)",
            "v6: \(result.ipv6s)",
            "ttl: \(result.ttl)",
            "expired: \(result.expired)",
            "extras: \(result.extras)"
        ]
        return lines.joined(separator: "\n")
    }

    private func formatBatchResult(title: String, results: [HttpdnsResult]) -> String {
        var lines = ["=== \(title) ===", "count: \(results.count)"]
        for (index, result) in results.enumerated() {
            lines.append("[\(index)] host: \(result.host)")
            lines.append("v4: \(result.ips)")
            lines.append("v6: \(result.ipv6s)")
            lines.append("ttl: \(result.ttl) expired: \(result.expired)")
            lines.append("extras: \(result.extras)")
        }
        return lines.joined(separator: "\n")
    }

    private func updateBrowserAccess(host: String, results: [HttpdnsResult]) {
        // 这块对应 Android demo 里“解析完成后，再拿解析出来的 IP 去实际访问一次”。
        // 这么做的意义是：
        // 1. 不只看 SDK 返回了哪些 IP
        // 2. 还要看“用这些 IP 去访问网页时，最终到底连到了哪一条 IP、拿回了什么内容”
        let candidateIps = (results.first?.ips ?? []) + (results.first?.ipv6s ?? [])
        guard !candidateIps.isEmpty else {
            browserOutput = "No resolved IPs available, skipping browser-like access."
            appendLog("Browser access skipped: no IPs")
            return
        }

        browserOutput = "Fetching via resolved IP..."
        let taskID = UUID()
        browserTaskID = taskID
        Task {
            let report = await fetchByResolvedIp(host: host, ips: candidateIps)
            await MainActor.run {
                guard self.browserTaskID == taskID else { return }
                self.browserOutput = report
                self.appendLog("Browser access updated: \(host)")
            }
        }
    }

    private func fetchByResolvedIp(host: String, ips: [String]) async -> String {
        // 这一层只负责把“像浏览器一样访问”的结果整理成展示文案。
        // 真正的关键逻辑在 fetchViaResolvedIp：
        // 它会做一件和 curl --resolve 很像的事情。
        let resolvedIps = ips.compactMap { ip -> String? in
            let trimmed = ip.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        guard !resolvedIps.isEmpty else {
            return "No resolved IPs available, skipping browser-like access."
        }

        do {
            let result = try await fetchViaResolvedIp(host: host, ips: resolvedIps)
            return [
                "=== Browser-like Access ===",
                "request host: \(host)",
                "request URL: https://\(host)/",
                "selected ip: \(result.selectedIP)",
                "final URL: \(result.finalURL)",
                "final IP: \(result.finalIP)",
                "redirects: \(result.redirects)",
                "HTTP status: \(result.statusCode)",
                "response prefix (first 1200 bytes):",
                result.bodyPreview
            ].joined(separator: "\n")
        } catch {
            let lastError = "\(error.localizedDescription)"
            return [
                "=== Browser-like Access ===",
                "request host: \(host)",
                "candidate IPs: \(resolvedIps)",
                "request failed: https/http both failed",
                "last error: \(lastError)"
            ].joined(separator: "\n")
        }
    }

    private func fetchViaResolvedIp(host: String, ips: [String]) async throws -> BrowserAccessResult {
        // 访问策略和 curl --resolve / OkHttp DNS override 是同一个思路：
        // 1. URL 看起来还是 https://host/
        // 2. Host 头还是原始域名
        // 3. TLS SNI 还是原始域名
        // 4. 但底层真正建立 TCP/TLS 连接时，连的是 HTTPDNS 返回的 IP
        //
        // 这样做的结果就是：
        // “浏览器语义”还是域名，但“连接地址”已经被我们替换成了 HTTPDNS 的结果。
        var currentURL = URL(string: "https://\(host)/")!
        var redirects: [String] = []
        var firstSelectedIP = "<unknown>"
        var lastError: Error?

        for _ in 0..<6 {
            let attempt = ResolveAttempt(url: currentURL)
            let hop = try await performResolvedHop(host: host, ips: ips, attempt: attempt)
            if firstSelectedIP == "<unknown>" {
                firstSelectedIP = hop.selectedIP
            }

            if let nextURL = resolveRedirect(from: hop, baseURL: currentURL),
               nextURL.host?.lowercased() == host.lowercased() {
                redirects.append("\(hop.statusCode) -> \(nextURL.absoluteString)")
                currentURL = nextURL
                continue
            }

            return BrowserAccessResult(
                statusCode: hop.statusCode,
                bodyPreview: hop.bodyPreview,
                finalURL: currentURL.absoluteString,
                finalIP: hop.selectedIP,
                selectedIP: firstSelectedIP,
                redirects: redirects
            )
        }

        if let lastError {
            throw lastError
        }
        throw NSError(domain: "HTTPDNSDemo", code: -3, userInfo: [NSLocalizedDescriptionKey: "Too many redirects"])
    }

    private func performResolvedHop(host: String, ips: [String], attempt: ResolveAttempt) async throws -> BrowserHopResult {
        // Android 用 OkHttp 的 Dns 接口就能“替换域名解析结果”。
        // iOS 这里没有一个同等级、同体验的高层接口，
        // 所以我们退一步：拿到候选 IP 后，手动逐个尝试，谁能连通就用谁。
        var lastError: Error?

        for ip in ips {
            do {
                return try await performResolvedHop(host: host, ip: ip, attempt: attempt)
            } catch {
                lastError = error
            }
        }

        throw lastError ?? NSError(domain: "HTTPDNSDemo", code: -4, userInfo: [NSLocalizedDescriptionKey: "No candidate IP succeeded"])
    }

    private func performResolvedHop(host: String, ip: String, attempt: ResolveAttempt) async throws -> BrowserHopResult {
        try await withCheckedThrowingContinuation { continuation in
            let tcpOptions = NWProtocolTCP.Options()
            let connection: NWConnection

            if attempt.usesTLS {
                // 这里是整个 demo 最关键的一步：
                // 底层连接目标是“解析出来的 IP”，
                // 但 TLS 握手里的服务器名字（SNI）仍然是“原始域名”。
                //
                // 为什么必须这么做？
                // 因为 HTTPS 站点通常是按域名来做证书和虚拟主机路由的。
                // 如果你只是“直接访问 IP”，很多站点就不会按正常网页流量返回内容。
                let tlsOptions = NWProtocolTLS.Options()
                sec_protocol_options_set_tls_server_name(tlsOptions.securityProtocolOptions, host)
                sec_protocol_options_add_tls_application_protocol(tlsOptions.securityProtocolOptions, "http/1.1")
                let parameters = NWParameters(tls: tlsOptions, tcp: tcpOptions)
                connection = NWConnection(host: NWEndpoint.Host(ip), port: NWEndpoint.Port(rawValue: attempt.port)!, using: parameters)
            } else {
                let parameters = NWParameters(tls: nil, tcp: tcpOptions)
                connection = NWConnection(host: NWEndpoint.Host(ip), port: NWEndpoint.Port(rawValue: attempt.port)!, using: parameters)
            }

            let queue = DispatchQueue(label: "httpdns.browser.access")
            let accumulator = ResponseAccumulator()
            var finished = false

            func finish(_ result: Result<BrowserHopResult, Error>) {
                guard !finished else { return }
                finished = true
                connection.cancel()
                continuation.resume(with: result)
            }

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    // 连接 ready 之后，再把 HTTP 请求报文发出去。
                    // 这里请求里的 Host 头仍然写域名，不写 IP。
                    // 这和 curl --resolve 的行为是一致的：
                    // “请求自己看起来像访问域名，但底层连接已经被定向到指定 IP”。
                    let request = Self.makeHTTPRequest(host: host, url: attempt.url)
                    guard let requestData = request.data(using: .utf8) else {
                        finish(.failure(NSError(domain: "HTTPDNSDemo", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to encode request"])))
                        return
                    }

                    connection.send(content: requestData, completion: .contentProcessed { error in
                        if let error {
                            finish(.failure(error))
                            return
                        }

                        self.receiveAll(connection: connection, accumulator: accumulator) { result in
                            switch result {
                            case .success(let data):
                                let responseText = String(decoding: data, as: UTF8.self)
                                let parsed = Self.parseHTTPResponse(responseText)
                                finish(.success(BrowserHopResult(statusCode: parsed.statusCode, headers: parsed.headers, body: parsed.body, bodyPreview: String(parsed.body.prefix(1200)), selectedIP: ip)))
                            case .failure(let error):
                                finish(.failure(error))
                            }
                        }
                    })
                case .failed(let error):
                    finish(.failure(error))
                case .cancelled:
                    if !finished {
                        finish(.failure(NSError(domain: "HTTPDNSDemo", code: -2, userInfo: [NSLocalizedDescriptionKey: "Connection cancelled"])))
                    }
                default:
                    break
                }
            }

            connection.start(queue: queue)
        }
    }

    private func receiveAll(connection: NWConnection, accumulator: ResponseAccumulator, completion: @escaping (Result<Data, Error>) -> Void) {
        // 这里把响应流完整读出来。
        // 读完之后我们再自己拆：状态行 / headers / body。
        connection.receive(minimumIncompleteLength: 1, maximumLength: 8192) { data, _, isComplete, error in
            if let data, !data.isEmpty {
                accumulator.data.append(data)
            }
            if let error {
                completion(.failure(error))
                return
            }
            if isComplete {
                completion(.success(accumulator.data))
                return
            }
            self.receiveAll(connection: connection, accumulator: accumulator, completion: completion)
        }
    }

    private func resolveRedirect(from hop: BrowserHopResult, baseURL: URL) -> URL? {
        // 这里专门处理“为什么明明请求成功了，却只拿到一个跳转页”的问题。
        // 常见有两类：
        // 1. 标准 HTTP 3xx + Location
        // 2. 页面里自己用 JS / meta refresh 跳转
        //
        // 像百度这种站点，经常第二类更多，所以这里只看 3xx 不够。
        if let location = hop.headers["location"] {
            if let absolute = URL(string: location) {
                return absolute
            }
            return URL(string: location, relativeTo: baseURL)?.absoluteURL
        }

        let lowerBody = hop.body.lowercased()
        if lowerBody.contains("location.replace(location.href.replace(\"https://\",\"http://\"))") {
            var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false)
            components?.scheme = "http"
            components?.port = nil
            return components?.url
        }

        if let range = lowerBody.range(of: #"content=["']0;url=([^"']+)["']"#, options: .regularExpression) {
            let matched = String(lowerBody[range])
            if let urlRange = matched.range(of: #"url=([^"']+)"#, options: .regularExpression) {
                let value = String(matched[urlRange].dropFirst(4))
                if let absolute = URL(string: value) {
                    return absolute
                }
                return URL(string: value, relativeTo: baseURL)?.absoluteURL
            }
        }

        return nil
    }

    private static func makeHTTPRequest(host: String, url: URL) -> String {
        // 请求头尽量贴近正常浏览器，减少某些站点把我们识别成“异常脚本请求”的概率。
        var path = url.path.isEmpty ? "/" : url.path
        if let query = url.query, !query.isEmpty {
            path += "?\(query)"
        }
        return [
            "GET \(path) HTTP/1.1",
            "Host: \(host)",
            "Connection: close",
            "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language: zh-CN,zh-Hans;q=0.9,en;q=0.8",
            "Upgrade-Insecure-Requests: 1",
            "User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
            "",
            ""
        ].joined(separator: "\r\n")
    }

    private static func parseHTTPResponse(_ responseText: String) -> (statusCode: Int, headers: [String: String], body: String) {
        // 这里只做 demo 需要的最小解析：
        // 状态码、headers、body。
        // 我们不是要实现完整浏览器，只是要把“这次访问最终发生了什么”展示清楚。
        let pieces = responseText.components(separatedBy: "\r\n\r\n")
        let headerText = pieces.first ?? ""
        let body = pieces.dropFirst().joined(separator: "\r\n\r\n")
        let headerLines = headerText.components(separatedBy: "\r\n")

        var statusCode = -1
        if let statusLine = headerLines.first {
            let parts = statusLine.split(separator: " ")
            if parts.count >= 2 {
                statusCode = Int(parts[1]) ?? -1
            }
        }

        var headers: [String: String] = [:]
        for line in headerLines.dropFirst() {
            let parts = line.split(separator: ":", maxSplits: 1).map(String.init)
            guard parts.count == 2 else { continue }
            headers[parts[0].trimmingCharacters(in: .whitespacesAndNewlines).lowercased()] = parts[1].trimmingCharacters(in: .whitespacesAndNewlines)
        }

        return (statusCode, headers, body)
    }

    private func appendLog(_ message: String) {
        let line = "HTTPDNS \(message)"
        if logsText.isEmpty {
            logsText = line
        } else {
            logsText.append("\n" + line)
        }
    }

    private func rebuildService(clientIp: String?) -> HttpDnsService {
        let service = HttpDnsService.initWithAccountID(accountID, aesSecretKey: aesKey, logger: logger)
        service.setPersistentCacheIPEnabled(true)
        service.setClientIp(clientIp)
        return service
    }
}

private struct BrowserAccessResult {
    let statusCode: Int
    let bodyPreview: String
    let finalURL: String
    let finalIP: String
    let selectedIP: String
    let redirects: [String]
}

private struct ResolveAttempt {
    // 一次 ResolveAttempt 就代表“当前这一跳到底要访问哪个 URL”。
    // 每次跳转之后，都会生成新的 attempt，这样端口 / scheme / TLS 开关也能一起更新。
    let url: URL

    var usesTLS: Bool { url.scheme?.lowercased() != "http" }
    var port: UInt16 { usesTLS ? 443 : 80 }
}

private struct BrowserHopResult {
    // 单跳结果：
    // 用某一个候选 IP 完成一次请求后，我们关心的最小结果集合。
    let statusCode: Int
    let headers: [String: String]
    let body: String
    let bodyPreview: String
    let selectedIP: String
}

private final class ResponseAccumulator {
    // NWConnection 是分块回调的，这里只是一个很简单的累加器。
    var data = Data()
}

final class DemoLogger: NSObject, HttpdnsLogger {
    private let onLog: (String) -> Void

    init(onLog: @escaping (String) -> Void) {
        self.onLog = onLog
    }

    func log(_ message: String) {
        onLog(message)
    }
}

#Preview {
    ContentView()
}
