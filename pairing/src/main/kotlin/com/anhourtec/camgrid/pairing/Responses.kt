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

    /** The AnhourCam TV mark (rounded square with a 2×2 camera grid). Served by the TV; the page loads nothing external. */
    const val FAVICON_SVG =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="14" """ +
            """fill="#07090c"/><path d="M22.0,9.14 H42.0 A12.86,12.86 0 0 1 54.86,22.0 V42.0 A12.86,12.86 0 0 1 """ +
            """42.0,54.86 H22.0 A12.86,12.86 0 0 1 9.14,42.0 V22.0 A12.86,12.86 0 0 1 22.0,9.14 Z" fill="none" """ +
            """stroke="#38CEFF" stroke-width="4.5"/><path d="M22.0,19.86 H27.71 A2.14,2.14 0 0 1 29.86,22.0 V27.71 """ +
            """A2.14,2.14 0 0 1 27.71,29.86 H22.0 A2.14,2.14 0 0 1 19.86,27.71 V22.0 A2.14,2.14 0 0 1 22.0,19.86 Z """ +
            """M22.0,34.14 H27.71 A2.14,2.14 0 0 1 29.86,36.29 V42 A2.14,2.14 0 0 1 27.71,44.14 H22.0 A2.14,2.14 0 """ +
            """0 1 19.86,42 V36.29 A2.14,2.14 0 0 1 22.0,34.14 Z M36.29,19.86 H42 A2.14,2.14 0 0 1 44.14,22.0 """ +
            """V27.71 A2.14,2.14 0 0 1 42,29.86 H36.29 A2.14,2.14 0 0 1 34.14,27.71 V22.0 A2.14,2.14 0 0 1 """ +
            """36.29,19.86 Z M36.29,34.14 H42 A2.14,2.14 0 0 1 44.14,36.29 V42 A2.14,2.14 0 0 1 42,44.14 H36.29 """ +
            """A2.14,2.14 0 0 1 34.14,42 V36.29 A2.14,2.14 0 0 1 36.29,34.14 Z" fill="#38CEFF"/></svg>"""

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
