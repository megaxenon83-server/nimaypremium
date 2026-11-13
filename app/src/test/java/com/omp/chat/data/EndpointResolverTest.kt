package com.omp.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class EndpointResolverTest {

    private fun profile(baseUrl: String, endpoints: List<String> = emptyList()) = PcProfile(
        name = "Test-PC",
        baseUrl = baseUrl,
        token = "token",
        endpoints = endpoints
    )

    /** Minimal HTTP-server uten ekstra testavhengigheter (svarer på én forespørsel). */
    private class OneShotServer(private val body: String) : AutoCloseable {
        private val socket = ServerSocket(0)
        val port: Int = socket.localPort
        private val worker = thread(isDaemon = true, name = "test-http") {
            runCatching {
                socket.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    reader.readLine()
                    while (true) {
                        val line = reader.readLine()
                        if (line.isNullOrBlank()) break
                    }
                    val bytes = body.toByteArray()
                    val out = client.getOutputStream()
                    out.write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()
                    )
                    out.write(bytes)
                    out.flush()
                }
            }
        }

        override fun close() {
            runCatching { socket.close() }
            worker.join(1000)
        }
    }

    @Test
    fun `candidates keep primary first and remove duplicates`() {
        val pc = profile(
            "http://192.168.50.3:7777",
            listOf("http://100.105.157.25:7777", "192.168.50.3:7777/", "")
        )
        assertEquals(
            listOf("http://192.168.50.3:7777", "http://100.105.157.25:7777"),
            pc.candidateUrls()
        )
    }

    @Test
    fun `mergeHosts adds discovered addresses with the same port`() {
        val merged = EndpointResolver.mergeHosts(
            profile("http://192.168.50.3:7777"),
            listOf("192.168.50.3", "100.105.157.25")
        )
        assertEquals("http://192.168.50.3:7777", merged.baseUrl)
        assertEquals(listOf("http://100.105.157.25:7777"), merged.endpoints)
    }

    @Test
    fun `mergeHosts keeps existing endpoints and ignores unknown hosts`() {
        val merged = EndpointResolver.mergeHosts(
            profile("http://192.168.50.3:7777", listOf("http://100.105.157.25:7777")),
            listOf("100.105.157.25")
        )
        assertEquals(listOf("http://100.105.157.25:7777"), merged.endpoints)
    }

    @Test
    fun `mergeHosts is a no-op without hosts`() {
        val original = profile("http://192.168.50.3:7777")
        assertEquals(original, EndpointResolver.mergeHosts(original, emptyList()))
    }

    @Test
    fun `hostLabel shows host and port`() {
        assertEquals("192.168.50.3:7777", EndpointResolver.hostLabel("http://192.168.50.3:7777"))
        assertEquals("100.105.157.25:7777", EndpointResolver.hostLabel("100.105.157.25:7777"))
    }

    @Test
    fun `bridge falls back to the next address when primary refuses`() {
        val server = OneShotServer("""{"ok":true,"version":"test-bridge","hosts":["127.0.0.1"]}""")
        val deadPort = ServerSocket(0).use { it.localPort }
        var resolved = ""
        val client = BridgeClient(
            baseUrlProvider = { "http://127.0.0.1:$deadPort" },
            tokenProvider = { "token" },
            candidatesProvider = { listOf("http://127.0.0.1:${server.port}") },
            onEndpointResolved = { resolved = it }
        )
        try {
            val result = client.callJson("GET", "/api/ping")
            assertEquals("test-bridge", result.str("version"))
            assertEquals("http://127.0.0.1:${server.port}", resolved)
        } finally {
            server.close()
        }
    }

    @Test
    fun `bridge reports failure when no address answers`() {
        val deadPort = ServerSocket(0).use { it.localPort }
        val client = BridgeClient(
            baseUrlProvider = { "http://127.0.0.1:$deadPort" },
            tokenProvider = { "token" },
            candidatesProvider = { emptyList() }
        )
        var failed = false
        try {
            client.callJson("GET", "/api/ping")
        } catch (error: java.io.IOException) {
            failed = true
            assertFalse(error.message.isNullOrBlank())
        }
        assertTrue(failed)
    }
}
