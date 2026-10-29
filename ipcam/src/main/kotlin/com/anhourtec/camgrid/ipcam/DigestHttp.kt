package com.anhourtec.camgrid.ipcam

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * HTTP requests to a camera with Digest login: the first request goes without credentials, a
 * Digest challenge is answered once. Basic is never used, so the password never travels readable.
 */
class DigestHttp(
    private val http: OkHttpClient,
    user: String,
    password: String,
) {
    private val auth = RtspAuth(user, password, allowBasic = false)

    /** Executes [request] (answering one Digest challenge). The caller closes the response. */
    fun execute(request: Request): Response {
        val first = http.newCall(authorized(request)).execute()
        if (first.code != UNAUTHORIZED) return first
        val challenge = first.headers("WWW-Authenticate").firstOrNull { it.startsWith("Digest", ignoreCase = true) }
        if (challenge == null || !auth.accept(challenge)) return first
        first.close()
        return http.newCall(authorized(request)).execute()
    }

    private fun authorized(request: Request): Request {
        if (!auth.ready) return request
        val url = request.url.toString().toHttpUrl()
        val uri = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        val header = auth.header(request.method, uri) ?: return request
        return request.newBuilder().header("Authorization", header).build()
    }

    companion object {
        const val UNAUTHORIZED = 401
    }
}
