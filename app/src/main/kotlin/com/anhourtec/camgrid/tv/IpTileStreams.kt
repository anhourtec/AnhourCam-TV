package com.anhourtec.camgrid.tv

import com.anhourtec.camgrid.client.protocol.StreamQuality
import com.anhourtec.camgrid.ipcam.CameraProbe
import com.anhourtec.camgrid.ipcam.IpCamera
import com.anhourtec.camgrid.ipcam.RtspClient
import com.anhourtec.camgrid.tv.data.CameraLogin
import com.anhourtec.camgrid.tv.video.TileDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Plays IP camera tiles: RTSP over TCP straight into the tile's decoder (sub-stream in grids,
 * main stream fullscreen). Blocks an IO thread until the tile is stopped, which closes [Tile.rtsp].
 */
internal class IpTileStreams(
    private val login: (IpCamera) -> CameraLogin,
) {
    suspend fun play(
        tile: Tile,
        camera: IpCamera,
        decoder: TileDecoder,
    ) = withContext(Dispatchers.IO) {
        val credentials = login(camera)
        val url = camera.streamUrl(fullscreen = tile.quality == StreamQuality.HD)
        CameraProbe.withRtspPortFallback(url) { candidate ->
            val client =
                RtspClient(
                    candidate,
                    credentials.user.ifEmpty { null },
                    credentials.password.ifEmpty { null },
                    decoder,
                    allowBasic = camera.basicLogin,
                )
            tile.rtsp = client
            // Stopped while connecting: the stop could not see this client yet.
            if (!isActive) {
                client.close()
                return@withRtspPortFallback
            }
            client.open()
            client.play()
        }
    }
}
