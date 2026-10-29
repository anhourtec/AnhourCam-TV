package com.anhourtec.camgrid.tv.data

import com.anhourtec.camgrid.ipcam.BrandPreset
import com.anhourtec.camgrid.ipcam.CameraProbe
import com.anhourtec.camgrid.ipcam.CameraScanner
import com.anhourtec.camgrid.ipcam.IpCamera
import com.anhourtec.camgrid.ipcam.IpCameraList
import com.anhourtec.camgrid.ipcam.OnvifClient
import com.anhourtec.camgrid.ipcam.OnvifException
import com.anhourtec.camgrid.ipcam.ProbeResult
import com.anhourtec.camgrid.ipcam.RtspException
import com.anhourtec.camgrid.ipcam.RtspUrl
import com.anhourtec.camgrid.pairing.AddMode
import com.anhourtec.camgrid.pairing.BrandOption
import com.anhourtec.camgrid.pairing.CameraBackend
import com.anhourtec.camgrid.pairing.CameraEntry
import com.anhourtec.camgrid.pairing.CameraError
import com.anhourtec.camgrid.pairing.CameraForm
import com.anhourtec.camgrid.pairing.CamerasSnapshot
import com.anhourtec.camgrid.pairing.FoundCamera
import com.anhourtec.camgrid.pairing.SaveOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Inet4Address
import java.util.UUID

/**
 * The dashboard's IP camera backend on the TV (runs on pairing-server threads, may block). A camera
 * is only saved after its video opened with the given login. [onChanged] gets the new list.
 */
class IpCameraManager(
    private val http: OkHttpClient,
    private val store: IpCameraStore,
    private val onChanged: (IpCameraList, changedId: String?) -> Unit,
) : CameraBackend {
    private val probe = CameraProbe(http)

    override fun cameras(): CamerasSnapshot {
        val list = store.load()
        return CamerasSnapshot(
            cameras =
                list.cameras.map {
                    CameraEntry(it.id, it.name, it.host, it.ownLogin, it.user, it.ownLogin && store.ownPassword(it.id) != null)
                },
            sharedUser = list.sharedUser,
            sharedPasswordSet = store.sharedPassword != null,
            brands = BrandPreset.entries.map { BrandOption(it.name, it.label) },
        )
    }

    override fun form(id: String): CameraForm? {
        val camera = store.load().find(id) ?: return null
        val brand = camera.source.removePrefix(SOURCE_BRAND).takeIf { camera.source.startsWith(SOURCE_BRAND) }
        val mode =
            when {
                brand != null -> AddMode.BRAND
                camera.source == SOURCE_ONVIF -> AddMode.ONVIF
                else -> AddMode.URL
            }
        return CameraForm(
            id = camera.id,
            mode = mode,
            name = camera.name,
            host = camera.host,
            brand = brand?.substringBefore(':'),
            channel = brand?.substringAfter(':', "1")?.toIntOrNull() ?: 1,
            rtspUrl = camera.mainUrl,
            snapshotUrl = camera.snapshotUrl.orEmpty(),
            ownLogin = camera.ownLogin,
            user = camera.user,
            password = "",
        )
    }

    override fun scan(): List<FoundCamera> {
        val local = LanAddress.find() as? Inet4Address ?: return emptyList()
        val saved =
            store
                .load()
                .cameras
                .map { it.host.substringBefore(':') }
                .toSet()
        return runBlocking { CameraScanner().scan(local) }.map {
            FoundCamera(it.host, it.rtspPort, it.onvifUrl != null, it.host in saved)
        }
    }

    override fun save(form: CameraForm): SaveOutcome {
        val list = store.load()
        val existing = form.id?.let(list::find)
        val id = existing?.id ?: UUID.randomUUID().toString()
        // Credentials typed into an RTSP address become the camera's own login, never part of the URL.
        val typed = if (form.mode == AddMode.URL) RtspUrl.parse(form.rtspUrl, null, null) else null
        if (keepsPasswordForNewAddress(form, existing)) return SaveOutcome.Failed(CameraError.PASSWORD_REQUIRED)
        val login = login(form, typed, existing, list) ?: return SaveOutcome.Failed(CameraError.NO_SHARED_PASSWORD)
        val streams = streams(form, typed, login)
        if (streams.error != null) return SaveOutcome.Failed(streams.error)
        val tested = test(streams, login)
        val result = tested.result ?: return SaveOutcome.Failed(tested.error ?: CameraError.UNREACHABLE)
        val camera =
            IpCamera(
                id = id,
                name = form.name,
                host = typed?.host ?: form.host,
                mainUrl = streams.main,
                subUrl = tested.sub,
                snapshotUrl = streams.snapshot,
                ownLogin = login.own,
                user = if (login.own) login.user else "",
                basicLogin = result.usedBasic,
                source = source(form),
            )
        if (login.own) store.setPassword(id, login.password) else store.removePassword(id)
        val next = list.put(camera)
        store.save(next)
        onChanged(next, id)
        return SaveOutcome.Saved(result.usedBasic)
    }

    /**
     * [typed]: the password came with this request. Only then may it go out with Basic login
     * (readable); saved passwords are only ever answered with Digest.
     */
    private class Login(
        val own: Boolean,
        val user: String,
        val password: String,
        val typed: Boolean,
    )

    /** A saved password would be reused for a camera whose address changed. */
    private fun keepsPasswordForNewAddress(
        form: CameraForm,
        existing: IpCamera?,
    ): Boolean {
        val saved = existing?.takeIf { it.ownLogin } ?: return false
        return form.ownLogin && form.password.isEmpty() && !sameAddress(form, saved)
    }

    /** True when the edited camera still points at the same place as the saved one. */
    private fun sameAddress(
        form: CameraForm,
        saved: IpCamera,
    ): Boolean =
        when (form.mode) {
            AddMode.URL -> RtspUrl.parse(form.rtspUrl, null, null).url == saved.mainUrl
            AddMode.ONVIF -> form.host == saved.host && saved.source == SOURCE_ONVIF
            AddMode.BRAND -> form.host == saved.host && saved.source == "$SOURCE_BRAND${form.brand}:${form.channel}"
        }

    /** The login to use; null when the shared one is needed but has no password yet. */
    private fun login(
        form: CameraForm,
        typed: RtspUrl?,
        existing: IpCamera?,
        list: IpCameraList,
    ): Login? =
        when {
            form.ownLogin -> {
                val kept = if (existing?.ownLogin == true) store.ownPassword(existing.id).orEmpty() else ""
                Login(true, form.user, form.password.ifEmpty { kept }, typed = form.password.isNotEmpty())
            }

            else -> {
                typed?.user?.let { Login(true, it, typed.password.orEmpty(), typed = true) }
                    ?: store.sharedPassword?.let { Login(false, list.sharedUser, it, typed = false) }
            }
        }

    private fun streams(
        form: CameraForm,
        typed: RtspUrl?,
        login: Login,
    ): Streams =
        when (form.mode) {
            AddMode.ONVIF -> {
                onvifStreams(form.host, login.user, login.password)
            }

            AddMode.BRAND -> {
                BrandPreset.byId(form.brand)?.urls(form.host, form.channel)?.let { Streams(it.main, it.sub, it.snapshot) }
                    ?: Streams.failed(CameraError.UNREACHABLE)
            }

            AddMode.URL -> {
                typed?.let { Streams(it.url, null, form.snapshotUrl.ifEmpty { null }) } ?: Streams.failed(CameraError.UNREACHABLE)
            }
        }

    override fun delete(id: String) {
        val next = store.load().remove(id)
        store.save(next)
        store.removePassword(id)
        onChanged(next, id)
    }

    override fun setShared(
        user: String,
        password: String,
    ) {
        val next = store.load().copy(sharedUser = user.ifEmpty { DEFAULT_USER })
        store.save(next)
        if (password.isNotEmpty()) store.setSharedPassword(password)
        onChanged(next, null)
    }

    private class Streams(
        val main: String,
        val sub: String?,
        val snapshot: String?,
        val error: CameraError? = null,
    ) {
        companion object {
            fun failed(error: CameraError) = Streams("", null, null, error)
        }
    }

    private fun onvifStreams(
        host: String,
        user: String,
        password: String,
    ): Streams {
        val found =
            try {
                OnvifClient(http, "http://$host/onvif/device_service", user, password).streams()
            } catch (e: OnvifException) {
                return Streams.failed(if (e.message == WRONG_LOGIN) CameraError.WRONG_LOGIN else CameraError.NO_ONVIF)
            } catch (_: IOException) {
                emptyList()
            }
        if (found.isEmpty()) return Streams.failed(CameraError.NO_ONVIF)
        val clean = { url: String -> RtspUrl.parse(url, null, null).url }
        return Streams(
            clean(found.first().rtspUrl),
            found
                .drop(1)
                .lastOrNull()
                ?.rtspUrl
                ?.let(clean),
            found.firstNotNullOfOrNull { it.snapshotUrl },
        )
    }

    private class Tested(
        val result: ProbeResult?,
        val error: CameraError?,
        val sub: String?,
    )

    /**
     * Opens the grid stream (the sub-stream, else the main one). A sub-stream that fails for any
     * reason but the login is dropped if the main stream works.
     */
    private fun test(
        streams: Streams,
        login: Login,
    ): Tested {
        val first = streams.sub?.let { attempt(it, login) }
        val loginRefused = first is Attempt.Failed && first.error in LOGIN_ERRORS
        val chosen = if (first != null && (first is Attempt.Ok || loginRefused)) first else attempt(streams.main, login)
        return when (chosen) {
            is Attempt.Ok -> Tested(chosen.result, null, streams.sub.takeIf { chosen === first })
            is Attempt.Failed -> Tested(null, chosen.error, null)
        }
    }

    private sealed interface Attempt {
        class Ok(
            val result: ProbeResult,
        ) : Attempt

        class Failed(
            val error: CameraError,
        ) : Attempt
    }

    private fun attempt(
        url: String,
        login: Login,
    ): Attempt =
        try {
            Attempt.Ok(probe.test(url, login.user, login.password, allowBasic = login.typed))
        } catch (e: RtspException) {
            Attempt.Failed(
                when (e.status) {
                    UNAUTHORIZED -> CameraError.WRONG_LOGIN
                    RtspException.NO_VIDEO -> CameraError.NO_VIDEO
                    RtspException.BASIC_ONLY -> CameraError.BASIC_NEEDS_TYPED_PASSWORD
                    else -> CameraError.UNREACHABLE
                },
            )
        } catch (_: IOException) {
            Attempt.Failed(CameraError.UNREACHABLE)
        }

    private fun source(form: CameraForm): String =
        when (form.mode) {
            AddMode.ONVIF -> SOURCE_ONVIF
            AddMode.BRAND -> "$SOURCE_BRAND${form.brand}:${form.channel}"
            AddMode.URL -> "url"
        }

    private companion object {
        const val SOURCE_ONVIF = "onvif"
        const val SOURCE_BRAND = "brand:"
        const val DEFAULT_USER = "admin"
        const val WRONG_LOGIN = "wrong user name or password"
        const val UNAUTHORIZED = 401
        val LOGIN_ERRORS = setOf(CameraError.WRONG_LOGIN, CameraError.BASIC_NEEDS_TYPED_PASSWORD)
    }
}
