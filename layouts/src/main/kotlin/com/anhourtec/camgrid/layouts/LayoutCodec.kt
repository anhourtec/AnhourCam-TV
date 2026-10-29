package com.anhourtec.camgrid.layouts

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** JSON for [LayoutState]. Unknown fields are ignored so older and newer app versions can share the file. */
object LayoutCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(state: LayoutState): String = json.encodeToString(LayoutState.serializer(), state)

    /** Returns null for unreadable data instead of throwing, so a corrupt file never blocks the app. */
    fun decode(text: String): LayoutState? =
        try {
            json.decodeFromString(LayoutState.serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /**
     * A first layout: the first cameras, in order, on the smallest grid that fits at most
     * [maxTiles] of them. Kept small so a modest TV can play every tile; the user grows it.
     */
    fun firstLayout(
        id: String,
        name: String,
        cameras: List<CameraRef>,
        maxTiles: Int = FIRST_LAYOUT_TILES,
    ): Layout {
        val template = Templates.fitting(minOf(cameras.size, maxTiles))
        return Layout(id, name, template.id, cameras.take(template.tileCount))
    }

    const val FIRST_LAYOUT_TILES = 4
}
