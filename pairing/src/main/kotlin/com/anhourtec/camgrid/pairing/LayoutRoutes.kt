package com.anhourtec.camgrid.pairing

import com.anhourtec.camgrid.layouts.Layout
import com.anhourtec.camgrid.layouts.Templates
import java.util.UUID

/**
 * The phone layout editor (SPEC §5.2). One form per layout: grid choice, a camera per tile, name;
 * plus layout actions (new, make default, delete). "Apply grid" only redraws; every other action
 * is saved and shown on the TV. Only built-in grids, known layouts and the account's cameras are
 * accepted.
 */
internal class LayoutRoutes(
    private val backend: LayoutBackend,
    private val state: PairingState,
) {
    /**
     * GET / and POST /layout. A paired browser gets the editor (and its token and cookie renewed,
     * since editing can take a while); an unpaired one posting the form goes back to the code page.
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
                GET_HOME -> page(s, request.queryParam("layout"))
                POST_LAYOUT -> post(s, request)
                else -> null
            }
        if (response == null || token == null) return response
        state.touch(token, ip)
        // Renew the browser's cookie too, or it would still expire 5 minutes after pairing.
        return HttpResponse(response.status, response.contentType, response.body, response.extraHeaders + Responses.tokenCookie(token))
    }

    private fun page(
        s: Strings,
        layoutId: String?,
    ): HttpResponse {
        val snapshot = backend.snapshot()
        val layout = layoutId?.let { snapshot.state.byId(it) } ?: snapshot.state.current ?: return HttpResponse.redirect("/")
        return HttpResponse.html(STATUS_OK, EditorPage.layoutEditor(s, snapshot, layout))
    }

    private fun post(
        s: Strings,
        request: HttpRequest,
    ): HttpResponse {
        val form = request.form()
        val snapshot = backend.snapshot()
        val base = form["id"]?.let { snapshot.state.byId(it) } ?: snapshot.state.current ?: return HttpResponse.redirect("/")
        val edited = fromForm(form, base, snapshot)
        val (next, shown, notice) =
            when (form["action"]) {
                ACTION_SAVE -> {
                    Triple(snapshot.state.saved(edited), edited, s.savedNote)
                }

                ACTION_NEW -> {
                    val created =
                        snapshot.state.create(
                            UUID.randomUUID().toString(),
                            snapshot.state.nextName(s.newLayoutName),
                            snapshot.cameras.map { it.ref },
                        )
                    Triple(created, created.current, s.createdNote)
                }

                ACTION_DEFAULT -> {
                    Triple(snapshot.state.saved(edited).withDefault(edited.id), edited, s.defaultNote)
                }

                ACTION_DELETE -> {
                    snapshot.state.delete(base.id).let { Triple(it, it.current, s.deletedNote) }
                }

                else -> {
                    Triple(null, edited, null)
                } // "Apply grid": redraw only
            }
        next?.let(backend::apply)
        val shownLayout = shown ?: return HttpResponse.redirect("/")
        return HttpResponse.html(
            STATUS_OK,
            EditorPage.layoutEditor(s, LayoutSnapshot(next ?: snapshot.state, snapshot.cameras), shownLayout, notice),
        )
    }

    /**
     * The posted tiles belong to the grid the form was drawn with; a different `template` then
     * switches grid the same way the TV does (keeping choices, filling new tiles).
     */
    private fun fromForm(
        form: Map<String, String>,
        base: Layout,
        snapshot: LayoutSnapshot,
    ): Layout {
        val known = snapshot.cameras.associateBy { it.key }
        val shown = form["shown"]?.let { id -> Templates.ALL.firstOrNull { it.id == id } } ?: base.template
        val posted = List(shown.tileCount) { i -> known[form["t$i"]]?.ref }
        val extras = base.cameras.drop(shown.tileCount)
        val drawn = base.copy(templateId = shown.id, cameras = (posted + extras).dropLastWhile { it == null })
        val target = form["template"]?.takeIf { id -> Templates.ALL.any { it.id == id } } ?: shown.id
        return drawn.withTemplate(target, snapshot.cameras.map { it.ref }).rename(form["name"].orEmpty())
    }

    companion object {
        const val ACTION_SAVE = "save"
        const val ACTION_NEW = "new"
        const val ACTION_DEFAULT = "default"
        const val ACTION_DELETE = "delete"
        private val GET_HOME = "GET" to "/"
        private val POST_LAYOUT = "POST" to "/layout"
    }
}
