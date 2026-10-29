package com.anhourtec.camgrid.client.portal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Keeps the portal session from idling out by making a light authenticated request every
 * [intervalMs]. Live view does not need the portal session (the gateway never sees the cookie),
 * so this only protects device-list refreshes. Network errors are retried on the next tick;
 * [onExpired] runs once if the server has dropped the session anyway.
 */
class SessionKeeper(
    private val portal: PortalClient,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val onExpired: () -> Unit,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job =
            scope.launch {
                while (isActive) {
                    delay(intervalMs)
                    val alive =
                        try {
                            portal.keepAlive()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: IOException) {
                            true // offline or server error: not proof the session is gone; try again next tick
                        }
                    if (!alive) {
                        onExpired()
                        break
                    }
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        /** Well under PHP's default 24-minute session idle timeout. */
        const val DEFAULT_INTERVAL_MS = 10 * 60 * 1000L
    }
}
