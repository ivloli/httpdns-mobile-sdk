package com.scloud.httpdns.sdk.internal

import android.content.Context
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal class HttpTransport(
    private val context: Context,
    private val timeoutMillis: Int,
    private val enableHttps: Boolean,
    private val useCronet: Boolean,
) {
    companion object {
        @Volatile
        private var cronetEngine: CronetEngine? = null

        private fun getCronetEngine(context: Context): CronetEngine {
            val cached = cronetEngine
            if (cached != null) return cached

            synchronized(this) {
                val existing = cronetEngine
                if (existing != null) return existing
                val created = CronetEngine.Builder(context.applicationContext).build()
                cronetEngine = created
                return created
            }
        }
    }

    fun get(host: String, pathWithQuery: String, connectIp: String? = null, portOverride: Int? = null): String {
        if (enableHttps && useCronet && connectIp.isNullOrBlank()) {
            return cronetGet(host, pathWithQuery, portOverride)
        }

        val scheme = if (enableHttps) "https" else "http"
        val defaultPort = if (enableHttps) 443 else 80
        val port = portOverride ?: defaultPort
        val portSuffix = if (port == defaultPort) "" else ":$port"
        val actualHost = connectIp ?: host
        val url = URL("$scheme://$actualHost$portSuffix$pathWithQuery")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            setRequestProperty("Connection", "close")
            if (!connectIp.isNullOrBlank()) {
                setRequestProperty("Host", host)
            }
        }

        if (connection is HttpsURLConnection) {
            if (!connectIp.isNullOrBlank()) {
                connection.sslSocketFactory = SniSocketFactory(connection.sslSocketFactory, host)
            }
            connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session ->
                getDefaultHostnameVerifier().verify(host, session)
            }
        }

        return try {
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("HTTP $code: $err")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun cronetGet(host: String, pathWithQuery: String, portOverride: Int?): String {
        val defaultPort = 443
        val port = portOverride ?: defaultPort
        val portSuffix = if (port == defaultPort) "" else ":$port"
        val requestUrl = "https://$host$portSuffix$pathWithQuery"

        val engine = getCronetEngine(context)
        val executor = Executors.newSingleThreadExecutor()
        val latch = CountDownLatch(1)
        val bodyStream = ByteArrayOutputStream()
        val state = CronetState()

        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                state.statusCode = info.httpStatusCode
                request.read(ByteBuffer.allocateDirect(8 * 1024))
            }

            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.flip()
                val bytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(bytes)
                bodyStream.write(bytes)
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                state.statusCode = info.httpStatusCode
                latch.countDown()
            }

            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                state.error = error
                latch.countDown()
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                state.error = IOException("cronet request canceled")
                latch.countDown()
            }
        }

        try {
            val request = engine.newUrlRequestBuilder(requestUrl, callback, executor)
                .setHttpMethod("GET")
                .addHeader("Connection", "close")
                .build()

            request.start()

            val completed = latch.await((timeoutMillis + 1000).toLong(), TimeUnit.MILLISECONDS)
            if (!completed) {
                request.cancel()
                throw IOException("cronet request timeout")
            }

            state.error?.let { throw it }

            val body = bodyStream.toString(Charsets.UTF_8.name())
            val code = state.statusCode
            if (code in 200..299) {
                return body
            }
            throw IOException("HTTP $code: $body")
        } finally {
            executor.shutdownNow()
        }
    }

    private class CronetState {
        @Volatile
        var statusCode: Int = -1

        @Volatile
        var error: Throwable? = null
    }

    private class SniSocketFactory(
        private val delegate: SSLSocketFactory,
        private val sniHost: String,
    ) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(s: java.net.Socket?, host: String?, port: Int, autoClose: Boolean) =
            patch(delegate.createSocket(s, host, port, autoClose) as SSLSocket)

        override fun createSocket(host: String?, port: Int) =
            patch(delegate.createSocket(host, port) as SSLSocket)

        override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int) =
            patch(delegate.createSocket(host, port, localHost, localPort) as SSLSocket)

        override fun createSocket(host: java.net.InetAddress?, port: Int) =
            patch(delegate.createSocket(host, port) as SSLSocket)

        override fun createSocket(
            address: java.net.InetAddress?,
            port: Int,
            localAddress: java.net.InetAddress?,
            localPort: Int,
        ) = patch(delegate.createSocket(address, port, localAddress, localPort) as SSLSocket)

        private fun patch(socket: SSLSocket): SSLSocket {
            val p: SSLParameters = socket.sslParameters
            setSniIfSupported(socket, p, sniHost)
            socket.sslParameters = p
            return socket
        }

        private fun setSniIfSupported(socket: SSLSocket, sslParameters: SSLParameters, host: String) {
            val appliedViaStandardApi = runCatching {
                val sniClass = Class.forName("javax.net.ssl.SNIHostName")
                val sni = sniClass.getConstructor(String::class.java).newInstance(host)
                val setServerNames = SSLParameters::class.java.getMethod("setServerNames", List::class.java)
                setServerNames.invoke(sslParameters, listOf(sni))
                true
            }.getOrDefault(false)

            if (!appliedViaStandardApi) {
                runCatching {
                    val setHostname = socket.javaClass.getMethod("setHostname", String::class.java)
                    setHostname.invoke(socket, host)
                }
            }
        }
    }
}
