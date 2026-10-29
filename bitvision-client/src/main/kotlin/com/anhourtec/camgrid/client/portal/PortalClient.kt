package com.anhourtec.camgrid.client.portal

import com.anhourtec.camgrid.client.page.AccountOverview
import com.anhourtec.camgrid.client.page.DeviceDetail
import com.anhourtec.camgrid.client.page.DeviceListParser
import com.anhourtec.camgrid.client.page.DevicePageParser
import com.anhourtec.camgrid.client.page.PageFormatException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * The web portal's HTTP side: CAPTCHA, login, device list, device page, logout.
 * The session lives in [cookies]; persist [SessionCookieJar.sessionId] to restore it later.
 */
class PortalClient(
    http: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    val cookies: SessionCookieJar = SessionCookieJar(),
) {
    private val http = http.newBuilder().cookieJar(cookies).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Fetches a CAPTCHA image (bound to the current session) for the user to read. */
    suspend fun captchaImage(): ByteArray = call(get("home/account/yzm", "t" to System.currentTimeMillis().toString())) { it.body.bytes() }

    /**
     * Logs in. [captcha] is the code the user read from [captchaImage]; it is never solved
     * automatically. The password is sent once and not kept.
     */
    suspend fun login(
        userName: String,
        password: String,
        captcha: String,
    ): LoginResult {
        // The portal reports "no plugin" mode before every login.
        call(post("home/account/update-unit-type", FormBody.Builder().add("unitType", UNIT_TYPE_NO_PLUGIN).build())) { }
        val body =
            FormBody
                .Builder()
                .add("userName", userName)
                .add("password", password)
                .add("yzm", captcha)
                .build()
        return call(post("home/account/ajax-login", body)) { response ->
            val root = runCatching { json.parseToJsonElement(response.body.string()).jsonObject }.getOrNull()
            val code = (root?.get("code") as? JsonPrimitive)?.intOrNull
            when {
                code == CODE_SUCCESS || code == CODE_LV_LOGIN_FAILED -> LoginResult.Success
                code == null -> LoginResult.Failed(null, "unexpected login response")
                else -> LoginResult.Failed(code, portalMessage(root))
            }
        }
    }

    /** The account name and all devices with their channels. */
    suspend fun overview(): AccountOverview {
        val html = page(get("home/space"))
        val name = DeviceListParser.accountName(html) ?: throw PageFormatException("account name not found on /home/space")
        return AccountOverview(name, DeviceListParser.parse(html))
    }

    /** Device data and gateway endpoints needed to start live view. */
    suspend fun deviceDetail(deviceId: String): DeviceDetail = DevicePageParser.parse(page(get("home/video/show", "id" to deviceId)))

    /** Cheap authenticated request that keeps the server-side session alive. Returns false if expired. */
    suspend fun keepAlive(): Boolean =
        runCatching { page(get("home/space")) }
            .map { true }
            .recover { if (it is SessionExpiredException) false else throw it }
            .getOrThrow()

    suspend fun logout() {
        call(get("home/account/exits")) { }
        cookies.clear()
    }

    private suspend fun page(request: Request): String =
        call(request) { response ->
            val html = response.body.string()
            if (response.request.url.encodedPath
                    .contains("/account/login") || DeviceListParser.isLoginPage(html)
            ) {
                throw SessionExpiredException()
            }
            html
        }

    private fun get(
        path: String,
        vararg query: Pair<String, String>,
    ): Request {
        val url = baseUrl.newBuilder().addPathSegments(path)
        query.forEach { (k, v) -> url.addQueryParameter(k, v) }
        return Request
            .Builder()
            .url(url.build())
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
    }

    private fun post(
        path: String,
        body: FormBody,
    ): Request =
        Request
            .Builder()
            .url(baseUrl.newBuilder().addPathSegments(path).build())
            .header("X-Requested-With", "XMLHttpRequest")
            .post(body)
            .build()

    private suspend fun <T> call(
        request: Request,
        handle: (Response) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${request.url.encodedPath}")
                handle(response)
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://bitvision.app/"
        private const val CODE_SUCCESS = 0

        // The portal treats this code as a successful login as well.
        private const val CODE_LV_LOGIN_FAILED = 2056
        private const val UNIT_TYPE_NO_PLUGIN = "2"
    }
}

sealed interface LoginResult {
    data object Success : LoginResult

    /** [message] is the portal's own text, e.g. a wrong CAPTCHA or password. */
    data class Failed(
        val code: Int?,
        val message: String?,
    ) : LoginResult
}

/** The portal's own error text in a JSON reply, if any. */
private fun portalMessage(root: JsonObject?): String? = ((root?.get("message") ?: root?.get("msg")) as? JsonPrimitive)?.content

class SessionExpiredException : IOException("BitVision session expired; log in again")
