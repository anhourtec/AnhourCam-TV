package com.anhourtec.camgrid.client.portal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SessionKeeperTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()
    private val scope = CoroutineScope(Job())

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() {
        scope.cancel()
        server.close()
        http.dispatcher.executorService.shutdown()
    }

    @Test
    fun `pings while the session is alive and reports expiry once`() =
        runBlocking {
            server.enqueue(MockResponse.Builder().body(LOGGED_IN).build())
            server.enqueue(MockResponse.Builder().code(503).build()) // transient error: keep going
            server.enqueue(MockResponse.Builder().body(LOGGED_IN).build())
            server.enqueue(MockResponse.Builder().body("""<form><input name="yzm"></form>""").build())
            val expired = CompletableDeferred<Unit>()
            var calls = 0
            val keeper =
                SessionKeeper(PortalClient(http, server.url("/")), intervalMs = 10) {
                    calls++
                    expired.complete(Unit)
                }

            keeper.start(scope)
            withTimeout(5_000) { expired.await() }

            assertEquals(4, server.requestCount)
            assertEquals("/home/space", server.takeRequest().url.encodedPath)
            assertEquals(1, calls)
            keeper.stop()
        }

    @Test
    fun `stop cancels pinging`() =
        runBlocking {
            val keeper = SessionKeeper(PortalClient(http, server.url("/")), intervalMs = 60_000) { }
            keeper.start(scope)
            keeper.stop()
            assertFalse(server.requestCount > 0)
        }

    private companion object {
        const val LOGGED_IN = """<a class="J-username">u</a><a href="/home/video/show?id=1">x</a>"""
    }
}
