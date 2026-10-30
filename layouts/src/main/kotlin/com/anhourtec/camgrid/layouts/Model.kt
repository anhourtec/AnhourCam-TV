package com.anhourtec.camgrid.layouts

import kotlinx.serialization.Serializable

/** A camera: an NVR (by its device ID) and a 0-based channel number. */
@Serializable
data class CameraRef(
    val deviceId: String,
    val channelNo: Int,
)

/**
 * A rectangle on the [Grid.UNITS]×[Grid.UNITS] layout grid. The grid maps onto the whole screen,
 * so a square of units is a 16:9 tile on a 16:9 TV.
 */
@Serializable
data class Cell(
    val col: Int,
    val row: Int,
    val cols: Int,
    val rows: Int,
)

/**
 * A saved grid: a template (tile shapes) and the camera in each tile, in order. [cameras] may be
 * longer than the number of tiles: choosing a smaller grid keeps the extra choices, so switching
 * back restores them.
 */
@Serializable
data class Layout(
    val id: String,
    val name: String,
    val templateId: String,
    val cameras: List<CameraRef?> = emptyList(),
) {
    val template: Template get() = Templates.byId(templateId)

    /** The camera in tile [index], or null for an empty tile. */
    fun cameraAt(index: Int): CameraRef? = cameras.getOrNull(index)

    /**
     * Switches to another grid. Cameras keep their tiles; when the grid grows, empty tiles are
     * filled from [fillFrom] (the account's cameras, in order), skipping cameras already placed.
     */
    fun withTemplate(
        templateId: String,
        fillFrom: List<CameraRef> = emptyList(),
    ): Layout {
        val template = Templates.byId(templateId)
        val list = cameras.toMutableList()
        while (list.size < template.tileCount) list += null
        val unused = fillFrom.filterNot { it in list }.iterator()
        for (i in 0 until template.tileCount) {
            if (list[i] == null && unused.hasNext()) list[i] = unused.next()
        }
        return copy(templateId = template.id, cameras = list.dropLastWhile { it == null })
    }

    fun assign(
        index: Int,
        camera: CameraRef?,
    ): Layout {
        require(index in 0 until template.cells.size) { "no tile $index" }
        val list = cameras.toMutableList()
        while (list.size <= index) list += null
        list[index] = camera
        return copy(cameras = list.trimTrailingEmpty())
    }

    /** Swaps the cameras of two tiles (the "move" action). */
    fun swap(
        a: Int,
        b: Int,
    ): Layout {
        val first = cameraAt(a)
        return assign(a, cameraAt(b)).assign(b, first)
    }

    fun rename(name: String) = copy(name = name.trim().take(MAX_NAME).ifEmpty { this.name })

    private fun List<CameraRef?>.trimTrailingEmpty(): List<CameraRef?> = dropLastWhile { it == null }

    companion object {
        const val MAX_NAME = 40
    }
}

/** Everything the TV remembers about layouts (SPEC §7.3a). */
@Serializable
data class LayoutState(
    val layouts: List<Layout> = emptyList(),
    val defaultId: String? = null,
    val lastId: String? = null,
) {
    /** The layout to open on launch: the last one watched, else the default, else the first. */
    val current: Layout? get() = byId(lastId) ?: byId(defaultId) ?: layouts.firstOrNull()

    fun byId(id: String?): Layout? = layouts.firstOrNull { it.id == id }

    fun put(layout: Layout): LayoutState =
        copy(layouts = if (byId(layout.id) == null) layouts + layout else layouts.map { if (it.id == layout.id) layout else it })

    fun remove(id: String): LayoutState =
        copy(
            layouts = layouts.filterNot { it.id == id },
            defaultId = defaultId.takeIf { it != id },
            lastId = lastId.takeIf { it != id },
        )

    /** Stores [layout] (new or changed) and shows it. */
    fun saved(layout: Layout): LayoutState = put(layout).copy(lastId = layout.id)

    /** A camera that no longer exists: its tiles become empty in every layout. */
    fun withoutCamera(ref: CameraRef): LayoutState =
        copy(layouts = layouts.map { l -> l.copy(cameras = l.cameras.map { it.takeIf { c -> c != ref } }.dropLastWhile { it == null }) })

    /** Shows layout [id] (unknown ids are ignored). */
    fun select(id: String): LayoutState = if (byId(id) != null) copy(lastId = id) else this

    fun withDefault(id: String): LayoutState = if (byId(id) != null) copy(defaultId = id) else this

    /** Deletes [id] unless it is the only layout; then shows the default (or the first) layout. */
    fun delete(id: String): LayoutState {
        if (layouts.size <= 1 || byId(id) == null) return this
        val rest = remove(id)
        val fallback = rest.layouts.first().id
        // There is always a default: deleting it hands the role to the first remaining layout.
        return rest.copy(defaultId = rest.defaultId ?: fallback, lastId = rest.lastId ?: rest.defaultId ?: fallback)
    }

    /** The next (or previous, for negative [by]) layout after the current one, wrapping around. */
    fun cycle(by: Int): LayoutState {
        val current = current ?: return this
        if (layouts.size < 2) return this
        val index = layouts.indexOf(current)
        return copy(lastId = layouts[((index + by) % layouts.size + layouts.size) % layouts.size].id)
    }

    /** Adds a layout (first cameras on a small grid, see [LayoutCodec.firstLayout]) and shows it. */
    fun create(
        id: String,
        name: String,
        cameras: List<CameraRef>,
    ): LayoutState {
        val layout = LayoutCodec.firstLayout(id, name, cameras)
        return put(layout).copy(lastId = layout.id, defaultId = defaultId ?: layout.id)
    }

    /** "Layout 2", "Layout 3"… using [pattern] with `{n}`, skipping names already taken. */
    fun nextName(pattern: String): String =
        generateSequence(layouts.size + 1) { it + 1 }
            .map { pattern.replace("{n}", it.toString()) }
            .first { name -> layouts.none { it.name == name } }
}
