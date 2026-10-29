package com.anhourtec.camgrid.tv

import android.os.SystemClock
import com.anhourtec.camgrid.client.ClientException
import com.anhourtec.camgrid.client.media.MediaSession
import com.anhourtec.camgrid.tv.data.DeviceDirectory
import com.anhourtec.camgrid.tv.video.DecoderBudget
import com.anhourtec.camgrid.tv.video.SnapshotCache
import com.anhourtec.camgrid.tv.video.TileDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Starts, stops and retries tile streams. A tile gets [Tile.MAX_ATTEMPTS] tries (one automatic
 * retry after [RETRY_S] s); after that it waits for OK. Stops always reach the NVR ([AppScope]).
 */
internal class TileRunner(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val directory: DeviceDirectory,
    private val pool: GatewayPool,
    private val budget: DecoderBudget,
    private val stills: SnapshotCache,
) {
    /** Tiles waiting for a decoder; the next one starts as soon as another tile frees one. */
    private val waiting = ArrayDeque<Tile>()

    /** Tiles that currently show still images instead of live video. */
    val waitingTiles: List<Tile> get() = waiting.filter { it.holder != null }

    fun start(tile: Tile) {
        val holder = tile.holder ?: return
        if (tile.camera == null) return
        val slot = budget.acquire()
        if (slot == null) {
            if (tile !in waiting) waiting.addLast(tile)
            val last = stills.get(tile.camera)
            if (last != null) tile.view?.showStill(last.bitmap) else tile.view?.showNoDecoder(budget.capacity)
            return
        }
        waiting.remove(tile)
        val decoder = TileDecoder(holder.surface, slot)
        tile.decoder = decoder
        tile.live = false
        tile.startedAt = SystemClock.elapsedRealtime()
        tile.view?.showConnecting(stills.get(tile.camera)?.bitmap)
        tile.job = scope.launch { stream(tile, decoder) }
    }

    private suspend fun stream(
        tile: Tile,
        decoder: TileDecoder,
    ) {
        val camera = tile.camera ?: return
        try {
            val detail = directory.detail(camera.deviceId)
            val (session, link) = pool.open(detail)
            tile.session = session
            tile.link = link
            val media = MediaSession(http, detail.service, detail.deviceId, scope, decoder)
            tile.media = media
            val ack = media.open()
            tile.ack = ack
            session.playVideo(link, camera.channelNo, tile.quality.streamId, ack)
            media.startStream(link, camera.channelNo, tile.quality.streamId)
        } catch (e: IOException) {
            tile.fail(e)
            retryLater(tile)
        } catch (e: ClientException) {
            tile.fail(e)
            retryLater(tile)
        }
    }

    fun stop(tile: Tile) {
        tile.retryJob?.cancel()
        waiting.remove(tile)
        val freed = tile.decoder != null
        val request = StreamStops.detach(tile)
        if (request != null) AppScope.launch { StreamStops.send(request) }
        if (freed) startNextWaiting()
    }

    private fun startNextWaiting() {
        val next = waiting.firstOrNull { it.holder != null } ?: return
        waiting.remove(next)
        start(next)
    }

    /** No video: keep the tile; retry once automatically, then wait for the user. */
    fun retryLater(tile: Tile) {
        stop(tile)
        tile.failures++
        if (tile.failures >= Tile.MAX_ATTEMPTS) {
            tile.view?.showNoSignalManual()
            return
        }
        tile.view?.showNoSignal(RETRY_S)
        tile.retryJob =
            scope.launch {
                delay(RETRY_S * MS_PER_S)
                start(tile)
            }
    }

    /** OK on a tile that gave up. */
    fun retryNow(tile: Tile) {
        tile.failures = 0
        start(tile)
    }

    /** Once a second: labels, and give up on tiles that connected but never showed a picture. */
    fun tick(
        tile: Tile,
        now: Long,
        debug: Boolean,
    ) {
        tile.refreshLabel(debug)
        if (tile.decoder != null && !tile.live && now - tile.startedAt > NO_PICTURE_MS) retryLater(tile)
    }

    private companion object {
        const val RETRY_S = 5
        const val MS_PER_S = 1000L
        const val NO_PICTURE_MS = 20_000L
    }
}
