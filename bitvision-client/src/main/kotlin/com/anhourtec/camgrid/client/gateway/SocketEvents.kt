package com.anhourtec.camgrid.client.gateway

import kotlinx.coroutines.CompletableDeferred
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Shared open/close bookkeeping for the gateway sockets. */
internal abstract class SocketEvents : WebSocketListener() {
    val opened = CompletableDeferred<Unit>()

    /** Completes with null on a clean close, or with the failure. */
    val closed = CompletableDeferred<Throwable?>()

    override fun onOpen(
        webSocket: WebSocket,
        response: Response,
    ) {
        opened.complete(Unit)
    }

    override fun onClosed(
        webSocket: WebSocket,
        code: Int,
        reason: String,
    ) {
        closed.complete(null)
    }

    override fun onFailure(
        webSocket: WebSocket,
        t: Throwable,
        response: Response?,
    ) {
        opened.completeExceptionally(t)
        closed.complete(t)
    }
}
