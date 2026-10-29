package com.anhourtec.camgrid.pairing

import com.anhourtec.camgrid.layouts.Layout
import com.anhourtec.camgrid.layouts.Templates

/**
 * The phone layout editor (SPEC §5.2): one form with the grid choice, a camera per tile and the
 * layout name. "Apply grid" redraws the form for another grid without saving; "Save to TV" saves.
 * Only cameras the account has and built-in grids are accepted.
 */
internal class LayoutRoutes(
    private val backend: LayoutBackend,
    private val state: PairingState,
) {
    /**
     * GET / and POST /layout. A paired browser gets the editor (and its token renewed, since
     * editing can take a while); an unpaired one posting the form goes back to the code page.
     * Returns null for everything else.
     */
    fun handle(
        s: Strings,
        request: HttpRequest,
        token: String?,
        ip: String,
    ): HttpResponse? {
        val route = request.method to request.path
        if (!state.isPaired(token, ip)) return if (route == POST_LAYOUT) HttpResponse.redirect("/") else null
        val response =
            when (route) {
                GET_HOME -> page(s)
                POST_LAYOUT -> post(s, request)
                else -> null
            }
        if (response == null || token == null) return response
        state.touch(token, ip)
        // Renew the browser's cookie too, or it would still expire 5 minutes after pairing.
        return HttpResponse(response.status, response.contentType, response.body, response.extraHeaders + Responses.tokenCookie(token))
    }

    private fun page(s: Strings): HttpResponse {
        val snapshot = backend.snapshot()
        return HttpResponse.html(STATUS_OK, Pages.layoutEditor(s, snapshot, snapshot.layout))
    }

    private fun post(
        s: Strings,
        request: HttpRequest,
    ): HttpResponse {
        val form = request.form()
        val snapshot = backend.snapshot()
        val layout = fromForm(form, snapshot)
        return if (form["action"] == ACTION_SAVE) {
            backend.save(layout)
            HttpResponse.html(STATUS_OK, Pages.layoutEditor(s, snapshot, layout, notice = s.savedNote))
        } else {
            HttpResponse.html(STATUS_OK, Pages.layoutEditor(s, snapshot, layout))
        }
    }

    /**
     * The posted tiles belong to the grid the form was drawn with; a different `template` then
     * switches grid the same way the TV does (keeping choices, filling new tiles).
     */
    private fun fromForm(
        form: Map<String, String>,
        snapshot: LayoutSnapshot,
    ): Layout {
        val known = snapshot.cameras.associateBy { it.key }
        val shown = form["shown"]?.let { id -> Templates.ALL.firstOrNull { it.id == id } } ?: snapshot.layout.template
        val posted = List(shown.tileCount) { i -> known[form["t$i"]]?.ref }
        val extras = snapshot.layout.cameras.drop(shown.tileCount)
        val base = snapshot.layout.copy(templateId = shown.id, cameras = (posted + extras).dropLastWhile { it == null })
        val target = form["template"]?.takeIf { id -> Templates.ALL.any { it.id == id } } ?: shown.id
        return base.withTemplate(target, snapshot.cameras.map { it.ref }).rename(form["name"].orEmpty())
    }

    companion object {
        const val ACTION_SAVE = "save"
        private val GET_HOME = "GET" to "/"
        private val POST_LAYOUT = "POST" to "/layout"
    }
}
