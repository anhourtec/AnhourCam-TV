package com.anhourtec.camgrid.tv

import com.anhourtec.camgrid.client.gateway.DeviceLink
import com.anhourtec.camgrid.client.gateway.GatewaySession
import com.anhourtec.camgrid.client.page.DeviceDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * One signalling connection per gateway and one login per NVR, opened the first time a tile
 * needs them and shared by every tile of that NVR. Lets a grid mix cameras from several NVRs.
 */
class GatewayPool(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val accountName: String,
) {
    private val lock = Mutex()
    private val sessions = mutableMapOf<String, GatewaySession>()
    private val links = mutableMapOf<String, DeviceLink>()

    /** The gateway session and device link for [detail], opening them if needed. */
    suspend fun open(detail: DeviceDetail): Pair<GatewaySession, DeviceLink> =
        lock.withLock {
            val url = detail.service.userGateUrl
            val session =
                sessions[url] ?: GatewaySession(http, detail.service, accountName, scope).also {
                    it.connect()
                    sessions[url] = it
                }
            val link = links[detail.deviceId] ?: session.openDevice(detail).also { links[detail.deviceId] = it }
            session to link
        }

    /** Closes every connection. Call after stop commands have been sent. */
    fun close() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        links.clear()
    }
}
