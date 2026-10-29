package com.anhourtec.camgrid.tv.data

import android.content.Context
import com.anhourtec.camgrid.layouts.LayoutCodec
import com.anhourtec.camgrid.layouts.LayoutState
import java.io.File
import java.io.IOException

/**
 * The user's layouts, saved in the app's private storage (SPEC §7.3a). Writes go to a temporary
 * file that then replaces the old one, so a power cut never leaves a half-written file.
 */
class LayoutRepository(
    context: Context,
) {
    private val file = File(context.filesDir, FILE)

    fun load(): LayoutState =
        try {
            if (file.exists()) LayoutCodec.decode(file.readText()) ?: LayoutState() else LayoutState()
        } catch (_: IOException) {
            LayoutState()
        }

    fun save(state: LayoutState) {
        val tmp = File(file.parentFile, "$FILE.tmp")
        tmp.writeText(LayoutCodec.encode(state))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val FILE = "layouts.json"
    }
}
