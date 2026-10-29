package com.anhourtec.camgrid.pairing

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The LAN pairing server (docs/SPEC.md §5.2). Runs only while the TV's sign-in screen is open and
 * for at most [maxLifetimeMs]; binds to the TV's LAN address only; serves plain-HTML pages; lets a
 * phone or laptop on the same network sign the TV in. No request or form content is ever logged.
 */
class PairingServer(
    private val bindAddress: InetAddress,
    private val requestedPort: Int = DEFAULT_PORT,
    private val backend: PairingBackend,
    private val state: PairingState,
    private val guard: NetworkGuard = NetworkGuard(),
    private val maxLifetimeMs: Long = MAX_LIFETIME_MS,
    private val onLanguage: (String) -> Unit = {},
    private val onSignedIn: () -> Unit,
) : Closeable {
    private val ownIp = bindAddress.hostAddress
    private val captchaNonce = AtomicInteger()
    private val workers =
        ThreadPoolExecutor(MAX_CONNECTIONS, MAX_CONNECTIONS, IDLE_SECONDS, TimeUnit.SECONDS, SynchronousQueue())
    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile private var running = false

    /** The bound port (differs from the requested one only when that was 0). */
    val port: Int get() = server?.localPort ?: requestedPort

    /** The address to show on the TV. */
    val url: String get() = "http://$ownIp:$port/"

    fun start() {
        val socket = ServerSocket(requestedPort, BACKLOG, bindAddress).apply { soTimeout = ACCEPT_POLL_MS }
        server = socket
        running = true
        val deadline = System.currentTimeMillis() + maxLifetimeMs
        acceptThread =
            Thread({ acceptLoop(socket, deadline) }, "pairing-accept").apply {
                isDaemon = true
                start()
            }
    }

    override fun close() {
        running = false
        runCatching { server?.close() }
        workers.shutdownNow()
    }

    private fun acceptLoop(
        socket: ServerSocket,
        deadline: Long,
    ) {
        while (running && System.currentTimeMillis() < deadline) {
            accept(socket)?.let(::dispatch)
        }
        close()
    }

    /** Waits up to a second for a client; null on timeout or once the socket is closed. */
    private fun accept(socket: ServerSocket): Socket? =
        try {
            socket.accept()
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: SocketException) {
            running = false
            null
        }

    private fun dispatch(client: Socket) {
        try {
            workers.execute { handle(client) }
        } catch (_: RejectedExecutionException) {
            runCatching { client.close() } // too many connections at once
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            if (!NetworkGuard.isPrivate(socket.inetAddress)) return
            val ip = socket.inetAddress.hostAddress
            socket.soTimeout = READ_TIMEOUT_MS
            val out = socket.getOutputStream()
            var signedIn = false
            val response =
                try {
                    if (!guard.allow(ip)) throw HttpException(STATUS_TOO_MANY)
                    val request = HttpParser.read(socket.getInputStream())
                    route(request, ip).also { signedIn = it.second }.first
                } catch (e: HttpException) {
                    HttpResponse(e.status, contentType = "text/plain; charset=utf-8")
                } catch (_: IOException) {
                    return
                }
            runCatching { response.writeTo(out) }
            if (signedIn) {
                running = false
                onSignedIn()
            }
        }
    }

    /** Returns the response and whether the TV is now signed in. */
    private fun route(
        request: HttpRequest,
        ip: String,
    ): Pair<HttpResponse, Boolean> {
        val chosen = request.queryParam("lang")?.let(Strings::forCode)
        val s = chosen ?: Strings.forCode(request.cookie(Responses.LANG_COOKIE))
        if (!NetworkGuard.isExpectedHost(request.header("host"), ownIp, port)) {
            return HttpResponse.html(STATUS_FORBIDDEN, Pages.message(s, s.wrongHostTitle, s.wrongHostText)) to false
        }
        Responses.staticRoute(request)?.let { return it to false }
        val token = request.cookie(Responses.TOKEN_COOKIE)
        val paired = state.isPaired(token, ip)
        return when (request.method to request.path) {
            "GET" to "/" -> {
                home(s, chosen, paired) to false
            }

            "POST" to "/pair" -> {
                pair(s, request, ip) to false
            }

            "GET" to "/captcha" -> {
                captcha(paired) to false
            }

            "POST" to "/login" -> {
                if (paired) login(s, request, ip, token!!) else HttpResponse.redirect("/") to false
            }

            else -> {
                throw HttpException(if (request.path in KNOWN_PATHS) STATUS_METHOD_NOT_ALLOWED else STATUS_NOT_FOUND)
            }
        }
    }

    /** Step 1 (code) or step 2 (sign-in form), with an optional language switch. */
    private fun home(
        s: Strings,
        chosen: Strings?,
        paired: Boolean,
    ): HttpResponse {
        // Any site could load "/?lang=…" in the background, so BitVision's language only
        // follows the page once this browser has paired.
        if (paired && chosen != null) onLanguage(chosen.code)
        val headers = chosen?.let { listOf(Responses.langCookie(it)) }.orEmpty()
        val page = if (paired) Pages.login(s, captchaNonce.incrementAndGet()) else Pages.code(s)
        return HttpResponse.html(STATUS_OK, page, headers)
    }

    private fun pair(
        s: Strings,
        request: HttpRequest,
        ip: String,
    ): HttpResponse =
        when (val result = state.submitCode(ip, request.form()["code"].orEmpty())) {
            is PairingState.CodeResult.Paired -> {
                HttpResponse.redirect("/", listOf(Responses.tokenCookie(result.token)))
            }

            PairingState.CodeResult.Wrong -> {
                HttpResponse.html(STATUS_OK, Pages.code(s, s.wrongCode))
            }

            is PairingState.CodeResult.LockedOut -> {
                HttpResponse.html(STATUS_OK, Pages.code(s, s.lockedOut(Responses.secondsCeil(result.retryInMs))))
            }
        }

    private fun captcha(paired: Boolean): HttpResponse {
        if (!paired) throw HttpException(STATUS_FORBIDDEN)
        val image =
            try {
                backend.captcha()
            } catch (_: IOException) {
                throw HttpException(STATUS_BAD_GATEWAY)
            }
        return HttpResponse(STATUS_OK, contentType = Responses.imageType(image), body = image)
    }

    private fun login(
        s: Strings,
        request: HttpRequest,
        ip: String,
        token: String,
    ): Pair<HttpResponse, Boolean> {
        val form = request.form()
        val email = form["email"].orEmpty().trim()
        val outcome =
            try {
                backend.login(email, form["password"].orEmpty(), form["captcha"].orEmpty().trim())
            } catch (_: IOException) {
                LoginOutcome.Failed(s.portalUnreachable)
            }
        return when (outcome) {
            LoginOutcome.Success -> {
                state.loginSucceeded()
                HttpResponse.html(STATUS_OK, Pages.done(s), listOf(Responses.clearTokenCookie())) to true
            }

            is LoginOutcome.Failed -> {
                if (state.loginFailed(token, ip)) {
                    HttpResponse.html(STATUS_OK, Pages.login(s, captchaNonce.incrementAndGet(), email, outcome.message)) to false
                } else {
                    HttpResponse.html(STATUS_OK, Pages.code(s, s.tooManyLogins), listOf(Responses.clearTokenCookie())) to false
                }
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 8743
        const val MAX_LIFETIME_MS = 10 * 60_000L
        private const val MAX_CONNECTIONS = 4
        private const val IDLE_SECONDS = 30L
        private const val BACKLOG = 8
        private const val ACCEPT_POLL_MS = 1000
        private const val READ_TIMEOUT_MS = 10_000
        private const val STATUS_BAD_GATEWAY = 502
        private val KNOWN_PATHS = setOf("/", "/pair", "/captcha", "/login", "/favicon.svg", "/favicon.ico")
    }
}
