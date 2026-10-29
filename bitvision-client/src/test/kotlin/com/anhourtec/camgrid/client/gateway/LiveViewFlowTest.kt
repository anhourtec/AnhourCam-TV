package com.anhourtec.camgrid.client.gateway

import com.anhourtec.camgrid.client.media.FrameHeader
import com.anhourtec.camgrid.client.media.MediaSession
import com.anhourtec.camgrid.client.media.RelayAck
import com.anhourtec.camgrid.client.page.DeviceDetail
import com.anhourtec.camgrid.client.page.NvrCredentials
import com.anhourtec.camgrid.client.page.ServiceAddress
import com.anhourtec.camgrid.client.protocol.StreamQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LiveViewFlowTest {
    private val gatewayServer = MockWebServer()
    private val mediaServer = MockWebServer()
    private val http = OkHttpClient()
    private val scope = CoroutineScope(Job())

    @BeforeEach
    fun start() {
        gatewayServer.start()
        mediaServer.start()
    }

    @AfterEach
    fun stop() {
        scope.cancel()
        http.connectionPool.evictAll()
        gatewayServer.close()
        mediaServer.close()
        http.dispatcher.executorService.shutdown()
    }

    @Test
    fun `opens a device, binds a relay and receives frames`() =
        runBlocking {
            val gateway = FakeGatewayListener()
            val media = FakeMediaListener()
            gatewayServer.enqueue(MockResponse.Builder().webSocketUpgrade(gateway).build())
            mediaServer.enqueue(MockResponse.Builder().webSocketUpgrade(media).build())
            val device = device()

            val frames = CopyOnWriteArrayList<Pair<Int, Long>>()
            val gotFrames = CountDownLatch(2)
            val session = GatewaySession(http, device.service, "user@example.com", scope)
            session.connect()
            assertEquals(FakeGatewayListener.OWN_ID, session.ownClientId)

            val link = session.openDevice(device)
            assertEquals(FakeGatewayListener.DEVICE_CLIENT_ID, link.route.deviceClientId)
            val login =
                gateway.received
                    .last()["param"]!!
                    .jsonObject["cmdData"]!!
                    .jsonObject["msg_data"]!!
                    .jsonObject
            assertEquals("secret", login["devPasswd"]!!.jsonPrimitive.content)
            assertEquals("7", login["cookie"]!!.jsonPrimitive.content)

            val stream =
                MediaSession(http, device.service, device.deviceId, scope, { _, header: FrameHeader ->
                    frames += header.type to header.sequence
                    gotFrames.countDown()
                })
            val ack = stream.open()
            assertEquals(RelayAck(0xC000_0001L, 89), ack)

            session.playVideo(link, channelNo = 0, streamId = StreamQuality.SD.streamId, ack = ack)
            stream.startStream(link, channelNo = 0, streamId = StreamQuality.SD.streamId)

            assertTrue(gotFrames.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(1 to 0L, 2 to 1L), frames.toList())
            assertTrue(media.received.last().contains("\"msg_id\":3"))

            session.stopVideo(link, 0, StreamQuality.SD.streamId, ack)
            stream.close()
            session.close()
        }

    @Test
    fun `reports a refused device login`() =
        runBlocking {
            gatewayServer.enqueue(MockResponse.Builder().webSocketUpgrade(FakeGatewayListener(refuseDeviceLogin = true)).build())
            val session = GatewaySession(http, device().service, "user@example.com", scope)
            session.connect()
            assertThrows<DeviceLoginException> { runBlocking { session.openDevice(device()) } }
            session.close()
        }

    private fun device() =
        DeviceDetail(
            deviceId = "DEV",
            name = "Test NVR",
            model = "NVR_16",
            typeName = "NVR",
            channelCount = 16,
            online = true,
            credentials = NvrCredentials("admin", "secret"),
            service =
                ServiceAddress(
                    userGateUrl = gatewayServer.url("/userGateWay/1").toString(),
                    streamUrl = mediaServer.url("/stream/1").toString(),
                    userGatewayIp = "192.0.2.10",
                    streamRelayIp = "192.0.2.11",
                    streamPort = 35009,
                ),
        )
}
