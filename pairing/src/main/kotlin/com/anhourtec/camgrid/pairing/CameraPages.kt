package com.anhourtec.camgrid.pairing

/** The IP camera pages of the dashboard. Plain HTML forms, no script; every dynamic value is escaped. */
internal object CameraPages {
    const val HELP_URL = "https://github.com/anhourtec/AnhourCam-TV/issues"

    /** "Layouts | IP cameras" switch shown on every dashboard page. */
    fun tabs(
        s: Strings,
        camerasActive: Boolean,
    ): String {
        val c = CameraStrings.forCode(s.code)

        fun tab(
            href: String,
            label: String,
            active: Boolean,
        ) = "<a href=\"$href\"${if (active) " aria-current=\"page\"" else ""}>${Pages.esc(label)}</a>"
        return "<nav class=\"tabs\">${tab("/", c.tabLayouts, !camerasActive)}${tab("/cameras", c.tabCameras, camerasActive)}</nav>"
    }

    fun list(
        s: Strings,
        snapshot: CamerasSnapshot,
        notice: String? = null,
        error: String? = null,
    ): String {
        val c = CameraStrings.forCode(s.code)
        val rows =
            if (snapshot.cameras.isEmpty()) {
                "<p class=\"lead\">${Pages.esc(c.none)}</p>"
            } else {
                "<ul class=\"rows\">" +
                    snapshot.cameras.joinToString("") { cam ->
                        val own = if (cam.ownLogin) " · ${Pages.esc(c.ownLoginBadge)}" else ""
                        "<li><span><b>${Pages.esc(cam.name)}</b><small>${Pages.esc(cam.address)}$own</small></span>" +
                            "<a class=\"small\" href=\"/cameras/add?id=${Pages.esc(cam.id)}\">${Pages.esc(c.edit)}</a></li>"
                    } + "</ul>"
            }
        val passwordHint = if (snapshot.sharedPasswordSet) Pages.esc(c.passwordSaved) else ""
        return Pages.page(
            s,
            title = c.title,
            body =
                """
                ${tabs(s, camerasActive = true)}
                <h1>${Pages.esc(c.title)}</h1>
                <p class="lead">${Pages.esc(c.lead)}</p>
                ${notice(notice, error)}
                $rows
                <a class="btn primary" href="/cameras/scan">${Pages.esc(c.scan)}</a>
                <a class="btn" href="/cameras/add">${Pages.esc(c.add)}</a>
                <form method="post" action="/cameras/shared">
                  <fieldset><legend>${Pages.esc(c.sharedTitle)}</legend>
                  <p class="lead">${Pages.esc(c.sharedLead)}</p>
                  <label for="suser">${Pages.esc(c.user)}</label>
                  <input id="suser" name="user" autocomplete="off" maxlength="${CameraRoutes.MAX_USER}" value="${Pages.esc(
                    snapshot.sharedUser,
                )}">
                  <label for="spass">${Pages.esc(c.password)}</label>
                  <input id="spass" name="password" type="password" autocomplete="new-password" maxlength="${CameraRoutes.MAX_PASSWORD}"
                    placeholder="$passwordHint">
                  <button type="submit" class="secondary">${Pages.esc(c.saveShared)}</button>
                  </fieldset>
                </form>
                <a class="help" href="$HELP_URL" target="_blank" rel="noopener noreferrer">${Pages.esc(c.help)}</a>
                """,
        )
    }

    fun scan(
        s: Strings,
        found: List<FoundCamera>,
    ): String {
        val c = CameraStrings.forCode(s.code)
        val rows =
            if (found.isEmpty()) {
                "<p class=\"alert\" role=\"status\">${Pages.esc(c.scanEmpty)}</p>"
            } else {
                "<ul class=\"rows\">" +
                    found.joinToString("") { f ->
                        val kinds = listOfNotNull(f.rtspPort?.let { "RTSP :$it" }, "ONVIF".takeIf { f.onvif }).joinToString(" · ")
                        val action =
                            if (f.added) {
                                "<small>${Pages.esc(c.added)}</small>"
                            } else {
                                val mode = if (f.onvif) AddMode.ONVIF else AddMode.BRAND
                                "<a class=\"small\" href=\"/cameras/add?host=${Pages.esc(
                                    f.host,
                                )}&amp;mode=${mode.id}\">${Pages.esc(c.addThis)}</a>"
                            }
                        "<li><span><b>${Pages.esc(f.host)}</b><small>${Pages.esc(kinds)}</small></span>$action</li>"
                    } + "</ul>"
            }
        return Pages.page(
            s,
            title = c.scanTitle,
            body =
                """
                ${tabs(s, camerasActive = true)}
                <h1>${Pages.esc(c.scanTitle)}</h1>
                <p class="lead">${Pages.esc(c.scanLead)}</p>
                $rows
                <a class="btn" href="/cameras/add">${Pages.esc(c.add)}</a>
                <a class="btn" href="/cameras">${Pages.esc(c.back)}</a>
                """,
        )
    }

    /** Add or edit: how to find the stream (ONVIF / brand / URL), the address, and the login. */
    @Suppress("LongMethod")
    fun form(
        s: Strings,
        snapshot: CamerasSnapshot,
        form: CameraForm,
        error: String? = null,
    ): String {
        val c = CameraStrings.forCode(s.code)
        val editing = form.id != null
        val idQuery = form.id?.let { "&amp;id=${Pages.esc(it)}" }.orEmpty()
        val hostQuery =
            form.host
                .takeIf { it.isNotEmpty() }
                ?.let { "&amp;host=${Pages.esc(it)}" }
                .orEmpty()
        val modes =
            listOf(AddMode.ONVIF to c.modeOnvif, AddMode.BRAND to c.modeBrand, AddMode.URL to c.modeUrl).joinToString("") { (m, label) ->
                val current = if (m == form.mode) " aria-current=\"page\"" else ""
                "<a class=\"pill\" href=\"/cameras/add?mode=${m.id}$idQuery$hostQuery\"$current>${Pages.esc(label)}</a>"
            }
        val address = address(c, snapshot, form)
        val shared = if (!form.ownLogin) " checked" else ""
        val own = if (form.ownLogin) " checked" else ""
        val passwordHint = if (editing) Pages.esc(c.passwordSaved) else ""
        val delete =
            form.id
                ?.let {
                    """
                <form method="post" action="/cameras/delete"><input type="hidden" name="id" value="${Pages.esc(it)}">
                <button type="submit" class="danger">${Pages.esc(c.delete)}</button></form>
                """
                }.orEmpty()
        return Pages.page(
            s,
            title = if (editing) c.editTitle else c.addTitle,
            body =
                """
                ${tabs(s, camerasActive = true)}
                <h1>${Pages.esc(if (editing) c.editTitle else c.addTitle)}</h1>
                ${notice(null, error)}
                <nav class="pills">$modes</nav>
                <form method="post" action="/cameras/save">
                  ${form.id?.let { "<input type=\"hidden\" name=\"id\" value=\"${Pages.esc(it)}\">" }.orEmpty()}
                  <input type="hidden" name="mode" value="${form.mode.id}">
                  <label for="cname">${Pages.esc(c.name)}</label>
                  <input id="cname" name="name" maxlength="${CameraRoutes.MAX_NAME}" value="${Pages.esc(form.name)}">
                  $address
                  <fieldset><legend>${Pages.esc(c.login)}</legend>
                  <label class="choice"><input type="radio" name="login" value="shared"$shared>${Pages.esc(c.useShared)}</label>
                  <label class="choice"><input type="radio" name="login" value="own"$own>${Pages.esc(c.useOwn)}</label>
                  <div class="own">
                  <label for="cuser">${Pages.esc(c.user)}</label>
                  <input id="cuser" name="user" autocomplete="off" maxlength="${CameraRoutes.MAX_USER}" value="${Pages.esc(form.user)}">
                  <label for="cpass">${Pages.esc(c.password)}</label>
                  <input id="cpass" name="password" type="password" autocomplete="new-password" maxlength="${CameraRoutes.MAX_PASSWORD}"
                    placeholder="$passwordHint">
                  </div>
                  </fieldset>
                  <button type="submit">${Pages.esc(c.testSave)}</button>
                </form>
                $delete
                <a class="btn" href="/cameras">${Pages.esc(c.back)}</a>
                """,
        )
    }

    private fun address(
        c: CameraStrings,
        snapshot: CamerasSnapshot,
        form: CameraForm,
    ): String =
        when (form.mode) {
            AddMode.URL -> {
                """
                <label for="rtsp">${Pages.esc(c.rtsp)}</label>
                <input id="rtsp" name="rtsp" inputmode="url" maxlength="${CameraRoutes.MAX_URL}" placeholder="${Pages.esc(c.rtspHint)}"
                  value="${Pages.esc(form.rtspUrl)}">
                <label for="snap">${Pages.esc(c.snapshot)}</label>
                <input id="snap" name="snapshot" inputmode="url" maxlength="${CameraRoutes.MAX_URL}" value="${Pages.esc(
                    form.snapshotUrl,
                )}">
                """
            }

            AddMode.BRAND -> {
                val options =
                    snapshot.brands.joinToString("") { b ->
                        val selected = if (b.id == form.brand) " selected" else ""
                        "<option value=\"${Pages.esc(b.id)}\"$selected>${Pages.esc(b.label)}</option>"
                    }
                """
                ${hostField(c, form)}
                <label for="brand">${Pages.esc(c.brand)}</label>
                <select id="brand" name="brand">$options</select>
                <label for="channel">${Pages.esc(c.channel)}</label>
                <input id="channel" name="channel" inputmode="numeric" maxlength="2" value="${form.channel}">
                """
            }

            AddMode.ONVIF -> {
                hostField(c, form)
            }
        }

    private fun hostField(
        c: CameraStrings,
        form: CameraForm,
    ) = """
        <label for="host">${Pages.esc(c.host)}</label>
        <input id="host" name="host" inputmode="url" autocapitalize="off" maxlength="${CameraRoutes.MAX_HOST}" value="${Pages.esc(
        form.host,
    )}">
        """

    private fun notice(
        notice: String?,
        error: String?,
    ) = notice?.let { "<p class=\"note\" role=\"status\">${Pages.esc(it)}</p>" }.orEmpty() +
        error?.let { "<p class=\"alert\" role=\"alert\">${Pages.esc(it)}</p>" }.orEmpty()
}
