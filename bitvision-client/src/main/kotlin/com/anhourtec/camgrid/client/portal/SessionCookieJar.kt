package com.anhourtec.camgrid.client.portal

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * In-memory cookie jar for the portal host only. The session is the `PHPSESSID` cookie;
 * [sessionId] and [restore] let the app persist it (encrypted) across restarts.
 */
class SessionCookieJar(
    private val host: String = DEFAULT_HOST,
) : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        // Cookies from any other host (e.g. during a redirect) never replace the portal session.
        if (url.host != host) return
        cookies.filter { it.matches(url) }.forEach { this.cookies[it.name] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (url.host ==
            host
        ) {
            cookies.values.filter { it.matches(url) }
        } else {
            emptyList()
        }

    /** The current session cookie value, or null if not logged in. */
    @get:Synchronized
    val sessionId: String? get() = cookies[SESSION_COOKIE]?.value

    @Synchronized
    fun restore(sessionId: String) {
        cookies[SESSION_COOKIE] =
            Cookie
                .Builder()
                .name(SESSION_COOKIE)
                .value(sessionId)
                .hostOnlyDomain(host)
                .path("/")
                .secure()
                .httpOnly()
                .build()
    }

    @Synchronized
    fun clear() = cookies.clear()

    companion object {
        const val SESSION_COOKIE = "PHPSESSID"
        const val DEFAULT_HOST = "bitvision.app"
    }
}
