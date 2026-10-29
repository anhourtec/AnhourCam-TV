package com.anhourtec.camgrid.pairing

import com.anhourtec.camgrid.layouts.Cell
import com.anhourtec.camgrid.layouts.Layout
import com.anhourtec.camgrid.layouts.Templates

/** The phone layout editor page. Plain HTML form, no script; every dynamic value is escaped. */
internal object EditorPage {
    /** The phone layout editor: name, grid choice (mini previews), numbered preview, a camera per tile. */
    fun layoutEditor(
        s: Strings,
        snapshot: LayoutSnapshot,
        layout: Layout,
        notice: String? = null,
    ): String {
        val template = layout.template
        val grids =
            Templates.ALL.joinToString("") { t ->
                val checked = if (t.id == template.id) " checked" else ""
                "<label class=\"gridopt\"><input type=\"radio\" name=\"template\" value=\"${Pages.esc(t.id)}\"$checked>" +
                    "<span class=\"mini\">${cells(t.cells, numbered = false)}</span><b>${t.tileCount}</b></label>"
            }
        val groups = snapshot.cameras.groupBy { it.group }
        val tiles =
            (0 until template.tileCount).joinToString("") { i ->
                val current = layout.cameraAt(i)
                val options =
                    groups.entries.joinToString("") { (group, cams) ->
                        "<optgroup label=\"${Pages.esc(group)}\">" +
                            cams.joinToString("") { c ->
                                val selected = if (c.ref == current) " selected" else ""
                                "<option value=\"${Pages.esc(c.key)}\"$selected>${Pages.esc("${c.group} · ${c.label}")}</option>"
                            } + "</optgroup>"
                    }
                "<li><label for=\"t$i\">${Pages.esc(s.tile(i + 1))}</label><select id=\"t$i\" name=\"t$i\">" +
                    "<option value=\"\">${Pages.esc(s.emptyOption)}</option>$options</select></li>"
            }
        return Pages.page(
            s,
            title = s.manageTitle,
            body =
                """
                <p class="step">${Pages.esc(s.step2)}</p>
                <h1>${Pages.esc(s.manageTitle)}</h1>
                <p class="lead">${Pages.esc(s.manageLead)}</p>
                ${notice?.let { "<p class=\"note\" role=\"status\">${Pages.esc(it)}</p>" }.orEmpty()}
                ${layoutPills(s, snapshot, layout)}
                <form method="post" action="/layout">
                  <input type="hidden" name="id" value="${Pages.esc(layout.id)}">
                  <input type="hidden" name="shown" value="${Pages.esc(template.id)}">
                  <label for="name">${Pages.esc(s.nameLabel)}</label>
                  <input id="name" name="name" maxlength="${Layout.MAX_NAME}" value="${Pages.esc(layout.name)}">
                  <fieldset><legend>${Pages.esc(s.gridLabel)}</legend><div class="grids">$grids</div></fieldset>
                  <button type="submit" name="action" value="grid" class="secondary">${Pages.esc(s.applyGrid)}</button>
                  <div class="preview" aria-hidden="true">${cells(template.cells, numbered = true)}</div>
                  <fieldset><legend>${Pages.esc(s.tilesLabel)}</legend><ol class="tiles">$tiles</ol></fieldset>
                  <button type="submit" name="action" value="${LayoutRoutes.ACTION_SAVE}">${Pages.esc(s.saveButton)}</button>
                  ${layoutActions(s, snapshot, layout)}
                </form>
                """,
        )
    }

    /** Links to every saved layout; the one being edited is highlighted, the default is marked. */
    private fun layoutPills(
        s: Strings,
        snapshot: LayoutSnapshot,
        editing: Layout,
    ): String {
        if (snapshot.state.layouts.size < 2) return ""
        val pills =
            snapshot.state.layouts.joinToString("") { l ->
                val current = if (l.id == editing.id) " aria-current=\"page\"" else ""
                val badge = if (l.id == snapshot.state.defaultId) " <small>${Pages.esc(s.defaultBadge)}</small>" else ""
                "<a class=\"pill\" href=\"/?layout=${Pages.esc(l.id)}\"$current>${Pages.esc(l.name)}$badge</a>"
            }
        return "<nav class=\"pills\" aria-label=\"${Pages.esc(s.layoutsLabel)}\">$pills</nav>"
    }

    private fun layoutActions(
        s: Strings,
        snapshot: LayoutSnapshot,
        layout: Layout,
    ): String {
        fun button(
            action: String,
            label: String,
            css: String,
        ) = "<button type=\"submit\" name=\"action\" value=\"$action\" class=\"$css\">${Pages.esc(label)}</button>"
        val buttons =
            listOfNotNull(
                button(LayoutRoutes.ACTION_NEW, s.newLayout, "secondary"),
                button(LayoutRoutes.ACTION_DEFAULT, s.makeDefault, "secondary").takeIf { layout.id != snapshot.state.defaultId },
                button(LayoutRoutes.ACTION_DELETE, s.deleteLayout, "danger").takeIf { snapshot.state.layouts.size > 1 },
            )
        return "<div class=\"actions\">${buttons.joinToString("")}</div>"
    }

    /** CSS-grid cells (inline styles only; the page has no script). */
    private fun cells(
        cells: List<Cell>,
        numbered: Boolean,
    ): String =
        cells.withIndex().joinToString("") { (i, c) ->
            val style = "grid-column:${c.col + 1}/span ${c.cols};grid-row:${c.row + 1}/span ${c.rows}"
            "<i style=\"$style\">${if (numbered) (i + 1).toString() else ""}</i>"
        }
}
