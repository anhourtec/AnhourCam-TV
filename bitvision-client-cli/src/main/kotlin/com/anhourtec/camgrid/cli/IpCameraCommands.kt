package com.anhourtec.camgrid.cli

import com.anhourtec.camgrid.client.media.FrameSink
import com.anhourtec.camgrid.ipcam.CameraScanner
import com.anhourtec.camgrid.ipcam.OnvifClient
import com.anhourtec.camgrid.ipcam.RtspClient
import com.anhourtec.camgrid.ipcam.RtspUrl
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.concurrent.thread

/** IP camera commands: test scanning, ONVIF and RTSP against real cameras from a computer. */
internal object IpCameraCommands {
    private const val DEFAULT_SECONDS = 10
    private const val MS_PER_SECOND = 1000L
    private const val JOIN_TIMEOUT_MS = 3_000L

    suspend fun scan(): Int {
        val local = localAddress() ?: return fail("No private IPv4 network found on this computer.")
        println("Scanning ${local.hostAddress.substringBeforeLast('.')}.0/24 for cameras…")
        val found = CameraScanner().scan(local)
        found.forEach {
            val rtsp = it.rtspPort?.let { port -> "RTSP :$port" } ?: "no RTSP port"
            val onvif = it.onvifUrl?.let { url -> "ONVIF ${clean(url)}" } ?: "ONVIF not announced"
            println("  %-15s  %-12s  %s".format(it.host, rtsp, onvif))
        }
        println(if (found.isEmpty()) "No cameras found." else "${found.size} found.")
        return 0
    }

    fun onvif(
        http: OkHttpClient,
        args: List<String>,
    ): Int {
        val host = args.getOrNull(0) ?: return fail("Usage: onvif <host> [user]")
        val (user, password) = login(args.getOrNull(1)) ?: return 1
        val client = OnvifClient(http, OnvifClient.deviceServiceUrl(host), user, password)
        val streams = client.streams()
        if (streams.isEmpty()) return fail("The camera answered but offered no H.264/H.265 stream.")
        streams.forEach {
            val p = it.profile
            println("${clean(p.name)}  ${p.width}x${p.height}  ${clean(p.encoding)}")
            println("    RTSP:     ${clean(withoutLogin(it.rtspUrl))}")
            it.snapshotUrl?.let { url -> println("    Snapshot: ${clean(withoutLogin(url))}") }
        }
        return 0
    }

    fun rtsp(args: List<String>): Int {
        val saveIndex = args.indexOf("--save")
        val saveTo = if (saveIndex >= 0) File(args.getOrNull(saveIndex + 1) ?: return fail("--save needs a file path")) else null
        val positional = if (saveIndex >= 0) args.take(saveIndex) + args.drop(saveIndex + 2) else args
        val url = positional.getOrNull(0) ?: return fail("Usage: rtsp <rtsp-url> [user] [seconds] [--save file]")
        val (user, password) = login(positional.getOrNull(1)) ?: return 1
        val seconds = positional.getOrNull(2)?.toIntOrNull() ?: DEFAULT_SECONDS
        val stats = StreamStats()
        val out = saveTo?.let { FileOutputStream(it) }
        saveTo?.let { println("Saving raw video to ${it.absolutePath} (camera footage: keep it private).") }
        val started = System.nanoTime()
        var codec = "?"
        val client =
            RtspClient(
                url,
                user.ifEmpty { null },
                password.ifEmpty { null },
                FrameSink { buf, header ->
                    stats.add(header, started)
                    out?.write(payload(buf, header))
                },
            )
        try {
            val track = client.open()
            codec = if (track.hevc) "H.265" else "H.264"
            println("Playing ${clean(RtspUrl.parse(url, null, null).url)} ($codec) for $seconds s…")
            val player =
                thread(isDaemon = true) { runCatching { client.play() }.onFailure { System.err.println("Stopped: ${clean(it.message)}") } }
            Thread.sleep(seconds * MS_PER_SECOND)
            client.close()
            player.join(JOIN_TIMEOUT_MS)
        } finally {
            client.close()
            out?.close()
        }
        println("Codec:       $codec (frame size shown below is the decoder default until known)")
        println(stats.report(seconds))
        return if (stats.frames.get() > 0) 0 else fail("No frames received.")
    }

    /** User from the argument or a prompt; password always from a hidden prompt (may be empty). */
    private fun login(userArg: String?): Pair<String, String>? {
        val console = System.console()
        if (console == null) {
            fail("Run from an interactive terminal so the password is not echoed.")
            return null
        }
        val user = userArg ?: console.readLine("User name (empty for none): ").trim()
        val chars = if (user.isEmpty()) CharArray(0) else console.readPassword("Password: ") ?: CharArray(0)
        val password = String(chars)
        chars.fill(' ')
        return user to password
    }

    private fun withoutLogin(url: String): String = url.replace(Regex("""(://)[^/@\s]*@"""), "$1")

    private fun localAddress(): Inet4Address? =
        NetworkInterface
            .getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
}
