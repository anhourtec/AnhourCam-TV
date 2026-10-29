package com.anhourtec.camgrid.pairing

/**
 * The IP camera pages under /cameras (Phase 6). Only for a paired browser, like the layout
 * editor; unpaired requests go back to the code page. Every field is validated here before the
 * backend sees it; passwords are passed on, never echoed back into a page.
 */
internal class CameraRoutes(
    private val backend: CameraBackend,
    private val state: PairingState,
) {
    fun handle(
        s: Strings,
        request: HttpRequest,
        token: String?,
        ip: String,
    ): HttpResponse? {
        if (!request.path.startsWith(PREFIX)) return null
        if (!state.isPaired(token, ip)) return HttpResponse.redirect("/")
        val c = CameraStrings.forCode(s.code)
        val page =
            when (request.method to request.path) {
                "GET" to PREFIX -> ok(CameraPages.list(s, backend.cameras()))
                "GET" to "$PREFIX/scan" -> ok(CameraPages.scan(s, backend.scan()))
                "GET" to "$PREFIX/add" -> ok(addPage(s, request))
                "POST" to "$PREFIX/save" -> ok(save(s, c, request.form()))
                "POST" to "$PREFIX/delete" -> ok(delete(s, c, request.form()))
                "POST" to "$PREFIX/shared" -> ok(shared(s, c, request.form()))
                else -> throw HttpException(STATUS_NOT_FOUND)
            }
        if (token == null) return page
        state.touch(token, ip)
        return HttpResponse(page.status, page.contentType, page.body, page.extraHeaders + Responses.tokenCookie(token))
    }

    private fun addPage(
        s: Strings,
        request: HttpRequest,
    ): String {
        val snapshot = backend.cameras()
        val mode = request.queryParam("mode")
        val saved = request.queryParam("id")?.let(backend::form)
        val host = request.queryParam("host")?.takeIf { HOST.matches(it) }.orEmpty()
        val form =
            saved?.let { if (mode != null) it.copy(mode = AddMode.of(mode)) else it }
                ?: blank(AddMode.of(mode), host, snapshot)
        return CameraPages.form(s, snapshot, form)
    }

    private fun save(
        s: Strings,
        c: CameraStrings,
        fields: Map<String, String>,
    ): String {
        val snapshot = backend.cameras()
        val form = parse(fields, snapshot)
        if (form == null) {
            val shown = blank(AddMode.of(fields["mode"]), fields["host"]?.takeIf { HOST.matches(it) }.orEmpty(), snapshot)
            return CameraPages.form(s, snapshot, shown.copy(id = known(fields["id"], snapshot)), c.errInvalid)
        }
        return when (val outcome = backend.save(form)) {
            is SaveOutcome.Saved -> {
                val note = if (outcome.basicLogin) "${c.saved} ${c.basicWarning}" else c.saved
                CameraPages.list(s, backend.cameras(), notice = note)
            }

            is SaveOutcome.Failed -> {
                CameraPages.form(s, snapshot, form.copy(password = ""), c.error(outcome.error))
            }
        }
    }

    private fun delete(
        s: Strings,
        c: CameraStrings,
        fields: Map<String, String>,
    ): String {
        known(fields["id"], backend.cameras())?.let(backend::delete)
        return CameraPages.list(s, backend.cameras(), notice = c.deleted)
    }

    private fun shared(
        s: Strings,
        c: CameraStrings,
        fields: Map<String, String>,
    ): String {
        val user = fields["user"].orEmpty().trim()
        val password = fields["password"].orEmpty()
        if (!text(user, MAX_USER) || !text(password, MAX_PASSWORD)) {
            return CameraPages.list(s, backend.cameras(), error = c.errInvalid)
        }
        backend.setShared(user, password)
        return CameraPages.list(s, backend.cameras(), notice = c.sharedSaved)
    }

    /** The posted form if every field is valid for its mode, else null. */
    private fun parse(
        fields: Map<String, String>,
        snapshot: CamerasSnapshot,
    ): CameraForm? {
        val mode = AddMode.of(fields["mode"])
        val name = fields["name"].orEmpty().trim()
        val host = fields["host"].orEmpty().trim()
        val rtsp = fields["rtsp"].orEmpty().trim()
        val snap = fields["snapshot"].orEmpty().trim()
        val brand = fields["brand"]?.takeIf { id -> snapshot.brands.any { it.id == id } }
        val channel = fields["channel"]?.trim()?.toIntOrNull() ?: 1
        val user = fields["user"].orEmpty().trim()
        val password = fields["password"].orEmpty()
        val valid =
            name.isNotEmpty() && text(name, MAX_NAME) && text(user, MAX_USER) && text(password, MAX_PASSWORD) &&
                channel in 1..MAX_CHANNEL && addressValid(mode, host, brand, rtsp, snap)
        if (!valid) return null
        return CameraForm(
            id = known(fields["id"], snapshot),
            mode = mode,
            name = name,
            host = host,
            brand = brand,
            channel = channel,
            rtspUrl = rtsp,
            snapshotUrl = snap,
            ownLogin = fields["login"] == "own",
            user = user,
            password = password,
        )
    }

    private fun addressValid(
        mode: AddMode,
        host: String,
        brand: String?,
        rtsp: String,
        snap: String,
    ): Boolean =
        when (mode) {
            AddMode.ONVIF -> HOST.matches(host)
            AddMode.BRAND -> HOST.matches(host) && brand != null
            AddMode.URL -> url(rtsp, "rtsp://") && snapshotUrl(snap)
        }

    private fun blank(
        mode: AddMode,
        host: String,
        snapshot: CamerasSnapshot,
    ) = CameraForm(
        id = null,
        mode = mode,
        name = host,
        host = host,
        brand = snapshot.brands.firstOrNull()?.id,
        channel = 1,
        rtspUrl = "",
        snapshotUrl = "",
        ownLogin = false,
        user = snapshot.sharedUser,
        password = "",
    )

    private fun ok(page: String) = HttpResponse.html(STATUS_OK, page)

    /** Only ids of saved cameras are accepted. */
    private fun known(
        id: String?,
        snapshot: CamerasSnapshot,
    ): String? = id?.takeIf { wanted -> snapshot.cameras.any { it.id == wanted } }

    companion object {
        const val MAX_NAME = 40
        const val MAX_HOST = 253
        const val MAX_USER = 64
        const val MAX_PASSWORD = 128
        const val MAX_URL = 512
        private const val MAX_CHANNEL = 64
        private const val PREFIX = "/cameras"

        /** Hostname, IPv4, or bracketed IPv6, with an optional port. */
        private val HOST = Regex("""^(?:[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?|\[[0-9A-Fa-f:.]+])(?::\d{1,5})?$""")

        private fun text(
            value: String,
            max: Int,
        ) = value.length <= max && value.none { it.isISOControl() }

        private fun url(
            value: String,
            scheme: String,
        ) = value.length in (scheme.length + 1)..MAX_URL &&
            value.startsWith(scheme, ignoreCase = true) &&
            value.none { it.isWhitespace() || it.isISOControl() }

        /** Snapshot URLs may not carry a login: it would be stored readable. */
        private fun snapshotUrl(value: String) =
            value.isEmpty() ||
                ((url(value, "http://") || url(value, "https://")) && '@' !in value.substringAfter("://").substringBefore('/'))
    }
}
