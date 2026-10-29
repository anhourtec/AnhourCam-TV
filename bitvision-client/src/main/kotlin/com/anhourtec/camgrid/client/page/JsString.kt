package com.anhourtec.camgrid.client.page

private const val HEX_RADIX = 16
private const val UNICODE_ESCAPE_LENGTH = 4

/** Decodes the body of a JavaScript string literal (the text between the quotes). */
internal fun unescapeJs(literal: String): String {
    val out = StringBuilder(literal.length)
    var i = 0
    while (i < literal.length) {
        val c = literal[i]
        if (c != '\\' || i + 1 >= literal.length) {
            out.append(c)
            i++
            continue
        }
        val next = literal[i + 1]
        i += 2
        when (next) {
            'n' -> {
                out.append('\n')
            }

            'r' -> {
                out.append('\r')
            }

            't' -> {
                out.append('\t')
            }

            'b' -> {
                out.append('\b')
            }

            'u' -> {
                val hex = literal.substring(i, minOf(i + UNICODE_ESCAPE_LENGTH, literal.length))
                val code = hex.toIntOrNull(HEX_RADIX)
                if (hex.length == UNICODE_ESCAPE_LENGTH && code != null) {
                    out.append(code.toChar())
                    i += UNICODE_ESCAPE_LENGTH
                } else {
                    out.append('u')
                }
            }

            else -> {
                out.append(next)
            }
        }
    }
    return out.toString()
}

/** Decodes the HTML entities that appear in device and channel names. */
internal fun unescapeHtml(text: String): String =
    text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .replace("&amp;", "&")
