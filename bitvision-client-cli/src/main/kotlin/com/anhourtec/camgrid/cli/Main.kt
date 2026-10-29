package com.anhourtec.camgrid.cli

import com.anhourtec.camgrid.client.gateway.GatewaySession
import com.anhourtec.camgrid.client.media.FrameHeader
import com.anhourtec.camgrid.client.media.MediaSession
import com.anhourtec.camgrid.client.portal.LoginResult
import com.anhourtec.camgrid.client.portal.PortalClient
import com.anhourtec.camgrid.client.portal.SessionExpiredException
import com.anhourtec.camgrid.client.protocol.StreamQuality
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

private const val USAGE = """
Usage: camgrid-cli <command>

  login                                  Sign in (shows the CAPTCHA image; you type the code)
  devices                                List NVRs and channels
  stream <deviceId> <channel> [hd|sd] [seconds] [--save file.h265]
                                         Play one channel and print stream statistics
  logout                                 Sign out and delete the saved session

The session cookie is kept in ~/.config/camgrid-cli/session (mode 600). Passwords are never saved.
"""

private const val DEFAULT_SECONDS = 20
private const val MS_PER_SECOND = 1000.0

private val configDir = File(System.getProperty("user.home"), ".config/camgrid-cli")
private val sessionFile = File(configDir, "session")
private val OWNER_ONLY = PosixFilePermissions.fromString("rw-------")

fun main(args: Array<String>) {
    val http = OkHttpClient()
    val portal = PortalClient(http)
    val exit =
        try {
            runBlocking {
                when (args.firstOrNull()) {
                    "login" -> login(portal)
                    "devices" -> withSession(portal) { devices(portal) }
                    "stream" -> withSession(portal) { stream(http, portal, args.drop(1)) }
                    "logout" -> withSession(portal) { logout(portal) }
                    else -> fail(USAGE.trim())
                }
            }
        } catch (e: SessionExpiredException) {
            fail("Session expired. Run: camgrid-cli login (${e.message})")
        }
    http.dispatcher.executorService.shutdown()
    http.connectionPool.evictAll()
    exitProcess(exit)
}

private suspend fun login(portal: PortalClient): Int {
    val console = System.console() ?: return fail("Run from an interactive terminal so the password is not echoed.")
    val userName = console.readLine("BitVision account: ").trim()
    val password = console.readPassword("Password: ") ?: return fail("No password entered.")
    val captcha = portal.captchaImage()
    val image = saveCaptcha(captcha)
    println("CAPTCHA image: ${image.absolutePath}")
    runCatching { ProcessBuilder("open", image.absolutePath).start() }
    val code = console.readLine("Type the code shown in the image: ").trim()

    val result = portal.login(userName, String(password), code)
    password.fill(' ')
    image.delete()
    return when (result) {
        is LoginResult.Success -> {
            saveSession(portal.cookies.sessionId ?: return fail("Login succeeded but no session cookie was set."))
            println("Signed in.")
            0
        }

        is LoginResult.Failed -> {
            fail("Login failed (code ${result.code}): ${clean(result.message ?: "no message")}")
        }
    }
}

private suspend fun devices(portal: PortalClient): Int {
    val overview = portal.overview()
    overview.devices.forEach { d ->
        println(
            "${clean(
                d.deviceId,
            )}  ${clean(d.name)}  ${clean(d.typeName)}  ${if (d.online) "online" else "offline"}  ${d.channels.size} channels",
        )
        d.channels.forEach { println("    ${it.channelNo + 1}: ${clean(it.name)}") }
    }
    if (overview.devices.isEmpty()) println("No devices on this account.")
    return 0
}

private suspend fun stream(
    http: OkHttpClient,
    portal: PortalClient,
    args: List<String>,
): Int {
    val saveIndex = args.indexOf("--save")
    val saveTo = if (saveIndex >= 0) File(args.getOrNull(saveIndex + 1) ?: return fail("--save needs a file path")) else null
    val positional = if (saveIndex >= 0) args.take(saveIndex) + args.drop(saveIndex + 2) else args
    val deviceId = positional.getOrNull(0) ?: return fail(USAGE.trim())
    val channelNo = (positional.getOrNull(1)?.toIntOrNull() ?: return fail("Channel must be a number (1 = CH1).")) - 1
    val quality = if (positional.getOrNull(2)?.lowercase() == "hd") StreamQuality.HD else StreamQuality.SD
    val seconds = positional.getOrNull(3)?.toIntOrNull() ?: DEFAULT_SECONDS

    val steps = StepTimer()
    val account = steps.time("portal: account") { portal.overview().accountName }
    val device = steps.time("portal: device page") { portal.deviceDetail(deviceId) }
    val stats = StreamStats()
    val out = saveTo?.let { FileOutputStream(it) }
    saveTo?.let { println("Saving raw H.265 to ${it.absolutePath} (camera footage: keep it private).") }

    coroutineScope {
        val gateway = GatewaySession(http, device.service, account, this)
        steps.time("gateway: connect + register") { gateway.connect() }
        val link = steps.time("gateway: open device") { gateway.openDevice(device) }
        val started = System.nanoTime()
        val media =
            MediaSession(http, device.service, device.deviceId, this, { buf, header ->
                stats.add(header, started)
                out?.write(payload(buf, header))
            })
        val ack = steps.time("media: open + relay ack") { media.open() }
        steps.time("gateway: play (relay bound)") { gateway.playVideo(link, channelNo, quality.streamId, ack) }
        media.startStream(link, channelNo, quality.streamId)
        println("Streaming ${clean(device.name)} CH${channelNo + 1} ($quality) for $seconds s…")
        delay(seconds * MS_PER_SECOND.toLong())
        gateway.stopVideo(link, channelNo, quality.streamId, ack)
        media.close()
        gateway.close()
    }
    println(steps.report())
    out?.close()
    println(stats.report(seconds))
    return if (stats.frames.get() > 0) 0 else fail("No frames received.")
}

private suspend fun logout(portal: PortalClient): Int {
    portal.logout()
    sessionFile.delete()
    println("Signed out.")
    return 0
}

private suspend fun withSession(
    portal: PortalClient,
    block: suspend () -> Int,
): Int {
    val sessionId = sessionFile.takeIf { it.exists() }?.readText()?.trim()
    if (sessionId.isNullOrEmpty()) return fail("Not signed in. Run: camgrid-cli login")
    portal.cookies.restore(sessionId)
    return block()
}

private fun saveSession(sessionId: String) {
    // Create the directory and file with owner-only permissions from the start, then move the file
    // into place atomically, so the session is never readable by other users, even briefly.
    Files.createDirectories(configDir.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
    val tmp = Files.createTempFile(configDir.toPath(), "session", ".tmp", PosixFilePermissions.asFileAttribute(OWNER_ONLY))
    Files.writeString(tmp, sessionId)
    Files.move(tmp, sessionFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}

private fun saveCaptcha(bytes: ByteArray): File {
    configDir.mkdirs()
    val ext = if (bytes.size > 1 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "png" else "jpg"
    return File(configDir, "captcha.$ext").apply { writeBytes(bytes) }
}

private fun payload(
    buf: ByteBuffer,
    header: FrameHeader,
): ByteArray = ByteArray(header.payloadLength).also { buf.duplicate().position(header.payloadOffset).get(it) }

/** Removes control characters from portal-supplied text so it can't inject terminal escape sequences. */
private fun clean(text: String?): String = text.orEmpty().filterNot { it.isISOControl() }

private fun fail(message: String): Int {
    System.err.println(message)
    return 1
}

private class StepTimer {
    private val steps = mutableListOf<Pair<String, Long>>()

    suspend fun <T> time(
        name: String,
        block: suspend () -> T,
    ): T {
        val start = System.nanoTime()
        return block().also { steps += name to (System.nanoTime() - start) / NANOS_PER_MS_LONG }
    }

    fun report(): String = steps.joinToString("\n", prefix = "Timings:\n") { (name, ms) -> "  %-30s %6d ms".format(name, ms) }

    private companion object {
        const val NANOS_PER_MS_LONG = 1_000_000L
    }
}

private class StreamStats {
    val frames = AtomicLong()
    private val keyFrames = AtomicLong()
    private val bytes = AtomicLong()

    @Volatile private var firstFrameMs = -1.0

    @Volatile private var size = ""

    fun add(
        header: FrameHeader,
        startedNanos: Long,
    ) {
        if (!header.isVideo) return
        if (frames.incrementAndGet() == 1L) firstFrameMs = (System.nanoTime() - startedNanos) / NANOS_PER_MS
        if (header.isKeyFrame) keyFrames.incrementAndGet()
        bytes.addAndGet(header.payloadLength.toLong())
        size = "${header.width}x${header.height}"
    }

    fun report(seconds: Int): String =
        """
        Frames:      ${frames.get()} (${keyFrames.get()} key frames), ${"%.1f".format(frames.get().toDouble() / seconds)} fps
        Resolution:  $size
        Bitrate:     ${"%.0f".format(bytes.get() * BITS_PER_BYTE / seconds / MS_PER_SECOND)} kbit/s
        First frame: ${"%.0f".format(firstFrameMs)} ms after opening the media socket
        """.trimIndent()

    private companion object {
        const val NANOS_PER_MS = 1_000_000.0
        const val BITS_PER_BYTE = 8
    }
}
