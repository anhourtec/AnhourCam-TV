package com.anhourtec.camgrid.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.anhourtec.camgrid.client.ClientException
import com.anhourtec.camgrid.client.media.MediaSession
import com.anhourtec.camgrid.client.protocol.StreamQuality
import com.anhourtec.camgrid.ipcam.CameraProbe
import com.anhourtec.camgrid.ipcam.IpCamera
import com.anhourtec.camgrid.tv.data.CameraLogin
import com.anhourtec.camgrid.tv.data.DeviceDirectory
import com.anhourtec.camgrid.tv.video.DecoderBudget
import com.anhourtec.camgrid.tv.video.KeyFrameCatcher
import com.anhourtec.camgrid.tv.video.KeyFrameDecoder
import com.anhourtec.camgrid.tv.video.SnapshotCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Still images for tiles the TV can't play live: one at a time, round-robin, oldest picture
 * first. Each opens the camera's stream just long enough for its first key frame, stops it
 * properly, and decodes that frame off the live decoder budget.
 */
internal class SnapshotRunner(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val directory: DeviceDirectory,
    private val pool: GatewayPool,
    budget: DecoderBudget,
    private val cache: SnapshotCache,
    private val probe: CameraProbe,
    private val login: (IpCamera) -> CameraLogin,
) {
    private val softwareDecoder = budget.decoders.firstOrNull { !it.hardware }?.name
    private var job: Job? = null

    /** Starts refreshing the tiles [candidates] returns (tiles waiting for a decoder). */
    fun start(candidates: () -> List<Tile>) {
        if (job != null) return
        val decoder = softwareDecoder ?: return
        job =
            scope.launch {
                while (isActive) {
                    val tile =
                        candidates()
                            .filter { it.camera != null && it.decoder == null }
                            .minByOrNull { t -> t.camera?.let { cache.get(it)?.takenAt } ?: 0L }
                    if (tile == null) {
                        delay(IDLE_MS)
                    } else {
                        grab(tile, decoder)
                        delay(BETWEEN_MS)
                    }
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun grab(
        tile: Tile,
        decoderName: String,
    ) {
        val camera = tile.camera ?: return
        if (camera.deviceId.startsWith(IpCamera.REF_PREFIX)) {
            directory.ipCamera(camera)?.let { grabIp(tile, it) }
            return
        }
        val catcher = KeyFrameCatcher()
        var stop: StreamStops.Request? = null
        try {
            val detail = directory.detail(camera.deviceId)
            val (session, link) = pool.open(detail)
            val media = MediaSession(http, detail.service, detail.deviceId, scope, catcher)
            val ack = media.open()
            stop = StreamStops.Request(tile.name, camera.channelNo, StreamQuality.SD.streamId, ack, media, session, link)
            session.playVideo(link, camera.channelNo, StreamQuality.SD.streamId, ack)
            media.startStream(link, camera.channelNo, StreamQuality.SD.streamId)
            val frame = withTimeout(FRAME_TIMEOUT_MS) { catcher.frame.await() }
            stop.let { AppScope.launch { StreamStops.send(it) } }
            stop = null
            val bitmap = withContext(Dispatchers.Default) { KeyFrameDecoder.decode(frame, decoderName) } ?: return
            cache.put(camera, SnapshotCache.Entry(bitmap, SystemClock.elapsedRealtime()))
            if (tile.decoder == null && !tile.live) tile.view?.showStill(bitmap)
        } catch (e: IOException) {
            Log.w(TAG, "${tile.name}: no still image (${e.message})")
        } catch (e: ClientException) {
            Log.w(TAG, "${tile.name}: no still image (${e.message})")
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "${tile.name}: no key frame in time", e)
        } finally {
            stop?.let { AppScope.launch { StreamStops.send(it) } }
        }
    }

    /** IP cameras: the camera's own JPEG still image (cameras without one keep their last picture). */
    private suspend fun grabIp(
        tile: Tile,
        ip: IpCamera,
    ) {
        val url = ip.snapshotUrl ?: return
        val camera = tile.camera ?: return
        val bitmap =
            withContext(Dispatchers.IO) {
                try {
                    val credentials = login(ip)
                    probe.snapshot(url, credentials.user, credentials.password)?.let(::decodeSmall)
                } catch (e: IOException) {
                    Log.w(TAG, "${tile.name}: no still image (${e.message})")
                    null
                }
            } ?: return
        cache.put(camera, SnapshotCache.Entry(bitmap, SystemClock.elapsedRealtime()))
        if (tile.decoder == null && !tile.live) tile.view?.showStill(bitmap)
    }

    /** Decodes a JPEG at most [MAX_STILL_WIDTH] wide: tiles are small and the cache is limited. */
    private fun decodeSmall(jpeg: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_STILL_WIDTH) sample *= 2
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val MAX_STILL_WIDTH = 640
        const val TAG = "CamGrid"
        const val IDLE_MS = 2_000L
        const val BETWEEN_MS = 500L
        const val FRAME_TIMEOUT_MS = 8_000L
    }
}
