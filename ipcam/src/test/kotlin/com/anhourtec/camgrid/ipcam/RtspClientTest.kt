package com.anhourtec.camgrid.ipcam

import com.anhourtec.camgrid.client.media.FrameSink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** A tiny fake camera on the loopback interface: Digest login, SDP, then one interleaved RTP frame. */
class RtspClientTest {
    private val requests = CopyOnWriteArrayList<String>()

    private fun fakeCamera(
        password: String,
        server: ServerSocket,
    ) = thread(isDaemon = true) {
        server.accept().use { socket ->
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val out = socket.getOutputStream()
            while (true) {
                val lines = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                if (lines.isEmpty()) return@use
                val request = lines.joinToString("\n")
                requests += request
                val method = lines.first().substringBefore(' ')
                val cseq = lines.first { it.startsWith("CSeq") }.substringAfter(':').trim()
                val authorized =
                    lines.firstOrNull { it.startsWith("Authorization: Digest ") }?.let {
                        val p = RtspAuth.params(it.substringAfter("Digest "))
                        val ha1 = RtspAuth.md5("admin:cam:$password")
                        val ha2 = RtspAuth.md5("$method:${p["uri"]}")
                        p["username"] == "admin" && p["response"] == RtspAuth.md5("$ha1:abc123:$ha2")
                    } == true

                fun reply(
                    status: String,
                    headers: String = "",
                    body: String = "",
                ) {
                    val length = if (body.isEmpty()) "" else "Content-Length: ${body.length}\r\n"
                    out.write("RTSP/1.0 $status\r\nCSeq: $cseq\r\n$headers$length\r\n$body".toByteArray(Charsets.ISO_8859_1))
                    out.flush()
                }
                when {
                    method == "OPTIONS" -> {
                        reply("200 OK", "Public: OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN\r\n")
                    }

                    !authorized -> {
                        reply("401 Unauthorized", "WWW-Authenticate: Digest realm=\"cam\", nonce=\"abc123\"\r\n")
                    }

                    method == "DESCRIBE" -> {
                        val sdp =
                            "v=0\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 H264/90000\r\n" +
                                "a=fmtp:96 sprop-parameter-sets=Z00AHw==,aO48gA==\r\na=control:track1\r\n"
                        reply(
                            "200 OK",
                            "Content-Base: rtsp://127.0.0.1:${server.localPort}/live/\r\nContent-Type: application/sdp\r\n",
                            sdp,
                        )
                    }

                    method == "SETUP" -> {
                        reply("200 OK", "Session: 4711;timeout=60\r\nTransport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n")
                    }

                    method == "PLAY" -> {
                        reply("200 OK", "Session: 4711\r\n")
                        val rtp =
                            byteArrayOf(0x80.toByte(), (0x80 or 96).toByte(), 0, 1, 0, 0, 0x0B, 0xB8.toByte(), 0, 0, 0, 1, 0x65, 1, 2, 3)
                        out.write(byteArrayOf('$'.code.toByte(), 0, 0, rtp.size.toByte()) + rtp)
                        out.flush()
                    }

                    else -> {
                        reply("200 OK")
                    }
                }
            }
        }
    }

    @Test
    fun `logs in with digest, sets up over TCP and delivers a key frame`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            fakeCamera("good", server)
            val got = CountDownLatch(1)
            var key = false
            var bytes = 0
            val client =
                RtspClient(
                    "rtsp://admin:good@127.0.0.1:${server.localPort}/live",
                    null,
                    null,
                    FrameSink { _, header ->
                        key = header.isKeyFrame
                        bytes = header.payloadLength
                        got.countDown()
                    },
                )
            val track = client.open()
            assertFalse(track.hevc)
            assertEquals("rtsp://127.0.0.1:${server.localPort}/live/track1", track.control)
            val player = thread { runCatching { client.play() } }
            assertTrue(got.await(5, TimeUnit.SECONDS))
            client.close()
            player.join(2_000)
            assertTrue(key)
            // SPS + PPS from the SDP, then the IDR slice, each with a 4-byte start code.
            assertEquals(4 + 4 + 4 + 4 + 4 + 4, bytes)
            assertTrue(requests.none { it.lines().first().contains("good") }, "password must never be in a request line")
            assertTrue(requests.any { it.startsWith("SETUP") && "interleaved=0-1" in it })
        }
    }

    @Test
    fun `wrong password is reported as such`() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            fakeCamera("good", server)
            val client = RtspClient("rtsp://127.0.0.1:${server.localPort}/live", "admin", "bad", FrameSink { _, _ -> })
            val e = assertThrows<RtspException> { client.open() }
            assertEquals(401, e.status)
            client.close()
        }
    }
}
