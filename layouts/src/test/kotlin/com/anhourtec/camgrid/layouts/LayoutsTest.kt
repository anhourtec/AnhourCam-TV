package com.anhourtec.camgrid.layouts

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LayoutsTest {
    private val a = CameraRef("NVR_A", 0)
    private val b = CameraRef("NVR_B", 3)
    private val c = CameraRef("NVR_A", 5)

    @Test
    fun `every template tiles inside the grid without overlaps`() {
        Templates.ALL.forEach { t ->
            val covered = Array(Grid.UNITS) { BooleanArray(Grid.UNITS) }
            t.cells.forEach { cell ->
                assertTrue(cell.col >= 0 && cell.row >= 0 && cell.col + cell.cols <= Grid.UNITS && cell.row + cell.rows <= Grid.UNITS, t.id)
                for (x in cell.col until cell.col + cell.cols) {
                    for (y in cell.row until cell.row + cell.rows) {
                        assertTrue(!covered[x][y], "overlap in ${t.id}")
                        covered[x][y] = true
                    }
                }
            }
            assertEquals(t.id.toInt(), t.tileCount, "tile count of ${t.id}")
        }
    }

    @Test
    fun `assign, swap, clear and rename`() {
        val layout =
            Layout("l1", "Home", "4")
                .assign(0, a)
                .assign(2, b)
                .swap(0, 2)
        assertEquals(listOf(b, null, a), layout.cameras)
        assertEquals(listOf(b), layout.assign(2, null).cameras)
        assertEquals("Garden", layout.rename("  Garden ").name)
        assertEquals("Home", layout.rename("   ").name)
        assertThrows<IllegalArgumentException> { layout.assign(4, c) }
    }

    @Test
    fun `smaller grid keeps the extra cameras for later`() {
        val nine = Layout("l1", "Home", "9", listOf(a, b, c))
        val one = nine.withTemplate("1")
        assertEquals(a, one.cameraAt(0))
        assertEquals(listOf(a, b, c), one.withTemplate("9").cameras)
    }

    @Test
    fun `growing the grid fills new tiles with the next unused cameras`() {
        val all = List(12) { CameraRef("NVR", it) }
        val four = Layout("l1", "Home", "4", listOf(all[0], all[1], all[2], null))
        val nine = four.withTemplate("9", all)
        assertEquals(listOf(all[0], all[1], all[2], all[3], all[4], all[5], all[6], all[7], all[8]), nine.cameras)
        val chosen = Layout("l2", "x", "4", listOf(all[5])).withTemplate("4", all)
        assertEquals(listOf(all[5], all[0], all[1], all[2]), chosen.cameras)
    }

    @Test
    fun `state remembers default and last layout and survives JSON`() {
        val first = LayoutCodec.firstLayout("l1", "My cameras", listOf(a, b, c))
        assertEquals("4", first.templateId)
        val many = List(25) { CameraRef("NVR", it) }
        assertEquals(4, LayoutCodec.firstLayout("l9", "x", many).cameras.size)
        assertEquals("1", LayoutCodec.firstLayout("l0", "x", listOf(a)).templateId)
        val second = Layout("l2", "Garden", "1", listOf(c))
        val state = LayoutState().put(first).put(second).copy(defaultId = "l1", lastId = "l2")
        assertEquals(second, state.current)
        assertEquals(first, state.remove("l2").current)

        val restored = LayoutCodec.decode(LayoutCodec.encode(state))
        assertEquals(state, restored)
        assertNull(LayoutCodec.decode("{not json"))
        assertEquals(LayoutState(), LayoutCodec.decode("""{"future":1}"""))
    }

    @Test
    fun `unknown template falls back to 2x2`() {
        assertEquals("4", Layout("l", "x", "99").template.id)
    }
}
