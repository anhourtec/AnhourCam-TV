package com.anhourtec.camgrid.pairing

import com.anhourtec.camgrid.layouts.Cell
import com.anhourtec.camgrid.layouts.Layout
import com.anhourtec.camgrid.layouts.Templates

/** The pairing pages: plain HTML forms, no scripts. Every dynamic value goes through [esc]. */
internal object Pages {
    fun code(
        s: Strings,
        error: String? = null,
        manage: Boolean = false,
    ): String =
        page(
            s,
            title = s.codeTitle,
            body =
                """
                <p class="step">${esc(s.step1)}</p>
                <h1>${esc(s.codeTitle)}</h1>
                <p class="lead">${esc(if (manage) s.codeLeadManage else s.codeLead)}</p>
                ${alert(error)}
                <form method="post" action="/pair" autocomplete="off">
                  <label class="sr" for="code">${esc(s.codeLabel)}</label>
                  <input id="code" name="code" class="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6"
                         autocomplete="one-time-code" placeholder="••••••" required autofocus>
                  <button type="submit">${esc(s.continueButton)}</button>
                </form>
                """,
        )

    fun login(
        s: Strings,
        captchaNonce: Int,
        email: String = "",
        error: String? = null,
    ): String =
        page(
            s,
            title = s.loginTitle,
            body =
                """
                <p class="step">${esc(s.step2)}</p>
                <h1>${esc(s.loginTitle)}</h1>
                <p class="lead">${esc(s.loginLead)}</p>
                ${alert(error)}
                <form method="post" action="/login">
                  <label for="email">${esc(s.email)}</label>
                  <input id="email" name="email" type="text" inputmode="email" autocomplete="username"
                         autocapitalize="off" spellcheck="false" value="${esc(email)}" required autofocus>
                  <label for="password">${esc(s.password)}</label>
                  <input id="password" name="password" type="password" autocomplete="current-password" required>
                  <label for="captcha">${esc(s.captchaLabel)}</label>
                  <div class="captcha">
                    <img src="/captcha?n=$captchaNonce" alt="${esc(s.captchaAlt)}" width="200" height="72">
                    <a href="/?n=${captchaNonce + 1}" class="ghost">${esc(s.newPicture)}</a>
                  </div>
                  <input id="captcha" name="captcha" inputmode="numeric" maxlength="6" autocomplete="off"
                         placeholder="${esc(s.captchaPlaceholder)}" required>
                  <button type="submit">${esc(s.signIn)}</button>
                </form>
                """,
        )

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
                "<label class=\"gridopt\"><input type=\"radio\" name=\"template\" value=\"${esc(t.id)}\"$checked>" +
                    "<span class=\"mini\">${cells(t.cells, numbered = false)}</span><b>${t.tileCount}</b></label>"
            }
        val groups = snapshot.cameras.groupBy { it.group }
        val tiles =
            (0 until template.tileCount).joinToString("") { i ->
                val current = layout.cameraAt(i)
                val options =
                    groups.entries.joinToString("") { (group, cams) ->
                        "<optgroup label=\"${esc(group)}\">" +
                            cams.joinToString("") { c ->
                                val selected = if (c.ref == current) " selected" else ""
                                "<option value=\"${esc(c.key)}\"$selected>${esc("${c.group} · ${c.label}")}</option>"
                            } + "</optgroup>"
                    }
                "<li><label for=\"t$i\">${esc(s.tile(i + 1))}</label><select id=\"t$i\" name=\"t$i\">" +
                    "<option value=\"\">${esc(s.emptyOption)}</option>$options</select></li>"
            }
        return page(
            s,
            title = s.manageTitle,
            body =
                """
                <p class="step">${esc(s.step2)}</p>
                <h1>${esc(s.manageTitle)}</h1>
                <p class="lead">${esc(s.manageLead)}</p>
                ${notice?.let { "<p class=\"note\" role=\"status\">${esc(it)}</p>" }.orEmpty()}
                <form method="post" action="/layout">
                  <input type="hidden" name="shown" value="${esc(template.id)}">
                  <label for="name">${esc(s.nameLabel)}</label>
                  <input id="name" name="name" maxlength="${Layout.MAX_NAME}" value="${esc(layout.name)}">
                  <fieldset><legend>${esc(s.gridLabel)}</legend><div class="grids">$grids</div></fieldset>
                  <button type="submit" name="action" value="grid" class="secondary">${esc(s.applyGrid)}</button>
                  <div class="preview" aria-hidden="true">${cells(template.cells, numbered = true)}</div>
                  <fieldset><legend>${esc(s.tilesLabel)}</legend><ol class="tiles">$tiles</ol></fieldset>
                  <button type="submit" name="action" value="${LayoutRoutes.ACTION_SAVE}">${esc(s.saveButton)}</button>
                </form>
                """,
        )
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

    fun done(s: Strings): String =
        page(
            s,
            title = s.doneTitle,
            body =
                """
                <div class="ok" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="40" height="40"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none"
                    stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </div>
                <h1>${esc(s.doneTitle)}</h1>
                <p class="lead">${esc(s.doneLead)}</p>
                """,
        )

    fun message(
        s: Strings,
        heading: String,
        text: String,
    ): String = page(s, heading, "<h1>${esc(heading)}</h1><p class=\"lead\">${esc(text)}</p>")

    private fun alert(error: String?) = error?.let { "<p class=\"alert\" role=\"alert\">${esc(it)}</p>" }.orEmpty()

    /** A dropdown like the portal's "Language: English" menu, built with <details> (no script). */
    private fun languages(s: Strings): String =
        Strings.ALL.joinToString(
            separator = "",
            prefix = "<details class=\"lang\"><summary>${esc(s.language)}: ${esc(s.name)}</summary><ul>",
            postfix = "</ul></details>",
        ) { l ->
            val current = if (l.code == s.code) " aria-current=\"true\"" else ""
            "<li><a href=\"/?lang=${l.code}\" lang=\"${l.htmlLang}\"$current>${esc(l.name)}</a></li>"
        }

    fun esc(text: String): String =
        buildString(text.length) {
            text.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(c)
                }
            }
        }

    private fun page(
        s: Strings,
        title: String,
        body: String,
    ) = """
        <!doctype html>
        <html lang="${s.htmlLang}">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
        <meta name="color-scheme" content="dark">
        <meta name="theme-color" content="#07090c">
        <link rel="icon" href="/favicon.svg" type="image/svg+xml">
        <title>${esc(title)} · CamGrid TV</title>
        <style>$STYLE</style>
        </head>
        <body>
        <main class="card">
        <header class="top">
        <div class="brand"><span class="mark" aria-hidden="true"><i></i><i></i><i></i><i></i></span>CamGrid TV</div>
        ${languages(s)}
        </header>
        $body
        </main>
        <p class="foot">${esc(s.footer)}</p>
        </body>
        </html>
        """.trimIndent()

    private const val STYLE = """
:root{--bg:#07090c;--card:#12161c;--line:#232a33;--text:#eef1f4;--dim:#98a2ad;--accent:#c8fbd0;--accent-ink:#06210d;--danger:#ffb4ab}
*{box-sizing:border-box}
html,body{margin:0;min-height:100%}
body{display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;min-height:100dvh;
padding:max(24px,env(safe-area-inset-top)) max(16px,env(safe-area-inset-right)) max(24px,env(safe-area-inset-bottom)) max(16px,env(safe-area-inset-left));
background:radial-gradient(1200px 600px at 50% -10%,#16301f 0%,transparent 60%),var(--bg);color:var(--text);
font:16px/1.5 ui-sans-serif,-apple-system,"Segoe UI",Roboto,"Helvetica Neue",Arial,sans-serif;-webkit-font-smoothing:antialiased}
.card{width:100%;max-width:420px;background:linear-gradient(180deg,#151a21,var(--card));border:1px solid var(--line);
border-radius:24px;padding:32px 28px;box-shadow:0 30px 80px -30px rgba(0,0,0,.8)}
.top{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12px;margin-bottom:28px}
.brand{display:flex;align-items:center;gap:10px;font-weight:600;letter-spacing:.2px;color:var(--dim)}
.lang{position:relative;font-size:14px}
.lang summary{list-style:none;cursor:pointer;padding:7px 12px;border:1px solid var(--line);border-radius:999px;color:var(--dim)}
.lang summary::-webkit-details-marker{display:none}
.lang summary::after{content:"";display:inline-block;margin-left:8px;border:4px solid transparent;border-top-color:currentColor;transform:translateY(2px)}
.lang[open] summary{color:var(--text);border-color:var(--accent)}
.lang ul{position:absolute;right:0;top:calc(100% + 6px);z-index:2;margin:0;padding:6px;list-style:none;min-width:180px;
background:#0f1318;border:1px solid var(--line);border-radius:14px;box-shadow:0 20px 40px -10px rgba(0,0,0,.7)}
.lang a{display:block;padding:9px 12px;border-radius:10px;color:var(--text);text-decoration:none}
.lang a:hover,.lang a:focus{background:#1a2028;outline:none}
.lang a[aria-current]{color:var(--accent);font-weight:600}
.mark{display:grid;grid-template-columns:repeat(2,9px);gap:3px}.mark i{width:9px;height:9px;border-radius:2px;background:var(--accent)}
.step{margin:0 0 6px;color:var(--accent);font-size:13px;font-weight:600;letter-spacing:.08em;text-transform:uppercase}
h1{margin:0 0 10px;font-size:clamp(22px,6.4vw,28px);line-height:1.15;letter-spacing:-.02em;overflow-wrap:anywhere}
.lead{margin:0 0 24px;color:var(--dim)}
label{display:block;margin:16px 0 6px;font-size:14px;color:var(--dim)}
.sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}
input{width:100%;padding:14px 16px;border-radius:14px;border:1px solid var(--line);background:#0b0e12;color:var(--text);
font:inherit;font-size:17px;outline:none;transition:border-color .15s,box-shadow .15s}
input:focus{border-color:var(--accent);box-shadow:0 0 0 4px rgba(200,251,208,.15)}
input.code{text-align:center;font-size:clamp(26px,9vw,34px);letter-spacing:.35em;padding:16px 0 16px .35em;font-variant-numeric:tabular-nums}
button{width:100%;margin-top:24px;padding:15px 18px;border:0;border-radius:999px;background:var(--accent);color:var(--accent-ink);
font:inherit;font-size:17px;font-weight:650;cursor:pointer;transition:transform .1s,filter .15s}
button:hover{filter:brightness(1.05)}button:active{transform:scale(.99)}
.captcha{display:flex;flex-wrap:wrap;align-items:center;gap:10px 14px;margin-bottom:10px}
.captcha img{background:#fff;border-radius:12px;padding:4px;height:64px;width:auto}
.ghost{color:var(--accent);text-decoration:none;font-weight:600;font-size:15px}
.alert{margin:0 0 8px;padding:12px 14px;border-radius:12px;background:rgba(255,180,171,.1);border:1px solid rgba(255,180,171,.3);color:var(--danger)}
.ok{display:grid;place-items:center;width:72px;height:72px;border-radius:50%;background:rgba(200,251,208,.12);color:var(--accent);margin-bottom:20px}
.foot{margin-top:20px;color:#5f6873;font-size:13px;text-align:center}
fieldset{border:0;margin:18px 0 0;padding:0}legend{font-size:14px;color:var(--dim);margin-bottom:8px}
.grids{display:grid;grid-template-columns:repeat(auto-fill,minmax(76px,1fr));gap:8px}
.gridopt{display:flex;flex-direction:column;align-items:center;gap:6px;margin:0;padding:8px;border:1px solid var(--line);border-radius:14px;cursor:pointer;color:var(--text)}
.gridopt input{position:absolute;opacity:0;pointer-events:none}
.gridopt b{font-size:14px}
.gridopt:focus-within{box-shadow:0 0 0 3px rgba(200,251,208,.35)}
.gridopt:has(input:checked){border-color:var(--accent);background:rgba(200,251,208,.08)}
.mini,.preview{display:grid;grid-template-columns:repeat(60,1fr);grid-template-rows:repeat(60,1fr);aspect-ratio:16/9;width:100%}
.mini i,.preview i{background:#3a434e;margin:1px;border-radius:3px}
.gridopt input:checked+.mini i{background:var(--accent)}
.preview{margin:16px 0 4px;border-radius:12px;overflow:hidden}
.preview i{display:grid;place-items:center;background:#1c232b;color:var(--dim);font-size:clamp(9px,2.6vw,14px);font-style:normal;font-weight:600;margin:2px}
button.secondary{background:transparent;color:var(--text);border:1px solid var(--line);margin-top:12px}
.tiles{list-style:none;margin:0;padding:0;display:grid;gap:10px}
.tiles li{display:grid;grid-template-columns:72px 1fr;align-items:center;gap:10px}
.tiles label{margin:0;font-size:14px}
select{width:100%;padding:12px 14px;border-radius:12px;border:1px solid var(--line);background:#0b0e12;color:var(--text);font:inherit;font-size:16px}
select:focus{outline:none;border-color:var(--accent);box-shadow:0 0 0 4px rgba(200,251,208,.15)}
.note{margin:0 0 8px;padding:12px 14px;border-radius:12px;background:rgba(200,251,208,.1);border:1px solid rgba(200,251,208,.35);color:var(--accent)}
@media (min-width:768px){.card:has(.tiles){max-width:640px}}
@media (max-width:400px){.card{padding:24px 18px;border-radius:20px}.top{margin-bottom:20px}.lead{margin-bottom:18px}.lang ul{left:0;right:auto}}
@media (max-height:560px) and (orientation:landscape){body{justify-content:flex-start}}
@media (min-width:768px){.card{padding:40px 36px}}
@media (prefers-reduced-motion:reduce){*{transition:none!important}}
"""
}
