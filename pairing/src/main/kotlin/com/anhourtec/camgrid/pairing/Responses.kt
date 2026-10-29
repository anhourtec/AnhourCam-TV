package com.anhourtec.camgrid.pairing

/** Cookie and content-type helpers for [PairingServer]. */
internal object Responses {
    const val TOKEN_COOKIE = "pair"
    const val LANG_COOKIE = "lang"
    private const val MS_PER_S = 1000L
    private const val TOKEN_MAX_AGE_S = PairingState.TOKEN_LIFETIME_MS / MS_PER_S
    private const val LANG_MAX_AGE_S = 365L * 24 * 3600
    private const val PNG_FIRST_BYTE = 0x89.toByte()

    fun tokenCookie(token: String) = "Set-Cookie" to "$TOKEN_COOKIE=$token; Path=/; HttpOnly; SameSite=Strict; Max-Age=$TOKEN_MAX_AGE_S"

    fun clearTokenCookie() = "Set-Cookie" to "$TOKEN_COOKIE=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"

    fun langCookie(s: Strings) = "Set-Cookie" to "$LANG_COOKIE=${s.code}; Path=/; SameSite=Strict; Max-Age=$LANG_MAX_AGE_S"

    fun imageType(bytes: ByteArray): String =
        if (bytes.size > 1 && bytes[0] == PNG_FIRST_BYTE && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"

    fun secondsCeil(ms: Long) = (ms + MS_PER_S - 1) / MS_PER_S

    /** The app's 2×2 grid mark on a dark rounded square. Served by the TV (the page loads nothing external). */
    const val FAVICON_SVG =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="14" fill="#07090c"/>""" +
            """<g fill="#c8fbd0"><rect x="14" y="14" width="16" height="16" rx="3"/><rect x="34" y="14" width="16" height="16" rx="3"/>""" +
            """<rect x="14" y="34" width="16" height="16" rx="3"/><rect x="34" y="34" width="16" height="16" rx="3"/></g></svg>"""

    /** Routes that need no pairing state: the favicon, and stale steps sent back to the start. */
    fun staticRoute(request: HttpRequest): HttpResponse? =
        when {
            request.method != "GET" -> {
                null
            }

            request.path == "/favicon.svg" || request.path == "/favicon.ico" -> {
                HttpResponse(STATUS_OK, "image/svg+xml", Responses.FAVICON_SVG.toByteArray(Charsets.UTF_8))
            }

            request.path == "/login" || request.path == "/pair" -> {
                HttpResponse.redirect("/")
            }

            else -> {
                null
            }
        }
}
