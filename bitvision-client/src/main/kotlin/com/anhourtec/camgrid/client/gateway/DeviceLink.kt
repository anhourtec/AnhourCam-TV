package com.anhourtec.camgrid.client.gateway

import com.anhourtec.camgrid.client.page.DeviceDetail
import com.anhourtec.camgrid.client.protocol.Route
import java.util.concurrent.atomic.AtomicInteger

/** A device opened on a [GatewaySession]. Holds the routing info and the per-device message counter. */
class DeviceLink internal constructor(
    val route: Route,
    val device: DeviceDetail,
) {
    private val msgIds = AtomicInteger(0)

    internal fun nextMsgId(): Int = msgIds.incrementAndGet()
}
