package com.anhourtec.camgrid.layouts

/** A grid shape: tile rectangles in reading order. */
data class Template(
    val id: String,
    val cells: List<Cell>,
) {
    val tileCount: Int get() = cells.size
}

object Grid {
    /** Divisible by 1–6, so 1×1 up to 6×6 grids tile exactly. */
    const val UNITS = 60
}

/** The built-in grids from SPEC §7.1. */
object Templates {
    private const val U = Grid.UNITS
    private const val HALF = U / 2
    private const val QUARTER = U / 4
    private const val THIRD = U / 3
    private const val SMALL_IN_TEN = 8
    private const val PER_ROW_IN_TEN = 4
    private const val SIDE_OF_SIXTEEN = 4
    private const val SIDE_OF_NINE = 3

    private fun uniform(side: Int): List<Cell> {
        val size = U / side
        return List(side * side) { Cell((it % side) * size, (it / side) * size, size, size) }
    }

    /** One big tile of [big]×[big] small cells in the top-left of a [side]×[side] grid, the rest around it. */
    private fun oneLarge(side: Int): List<Cell> {
        val size = U / side
        val big = side - 1
        val large = Cell(0, 0, big * size, big * size)
        val right = List(big) { Cell(big * size, it * size, size, size) }
        val bottom = List(side) { Cell(it * size, big * size, size, size) }
        return listOf(large) + right + bottom
    }

    private val TWO = listOf(Cell(0, QUARTER, HALF, HALF), Cell(HALF, QUARTER, HALF, HALF))
    private val TEN =
        listOf(Cell(0, 0, HALF, HALF), Cell(HALF, 0, HALF, HALF)) +
            List(SMALL_IN_TEN) {
                Cell((it % PER_ROW_IN_TEN) * QUARTER, HALF + (it / PER_ROW_IN_TEN) * QUARTER, QUARTER, QUARTER)
            }
    private val THIRTEEN =
        listOf(Cell(QUARTER, QUARTER, HALF, HALF)) +
            uniform(SIDE_OF_SIXTEEN).filterNot { it.col in QUARTER until U - QUARTER && it.row in QUARTER until U - QUARTER }

    val ALL: List<Template> =
        listOf(
            Template("1", uniform(1)),
            Template("2", TWO),
            Template("4", uniform(2)),
            Template("6", oneLarge(3)),
            Template("8", oneLarge(4)),
            Template("9", uniform(3)),
            Template("10", TEN),
            Template("13", THIRTEEN),
            Template("16", uniform(4)),
            Template("25", uniform(5)),
            Template("36", uniform(6)),
        )

    fun byId(id: String): Template = ALL.firstOrNull { it.id == id } ?: ALL.first { it.id == "4" }

    /** The smallest built-in grid that fits [cameras] tiles (used for the first layout). */
    fun fitting(cameras: Int): Template =
        listOf("1", "4", "9", "16", "25", "36").map(::byId).firstOrNull { it.tileCount >= cameras } ?: byId("36")

    init {
        check(THIRD * SIDE_OF_NINE == U)
    }
}
