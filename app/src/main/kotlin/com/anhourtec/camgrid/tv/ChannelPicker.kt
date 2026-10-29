package com.anhourtec.camgrid.tv

/**
 * Pages through all of a device's channels in grid-sized steps. Every channel stays listed:
 * the user decides what the grid shows (custom layouts arrive with the layout editor).
 */
internal class ChannelPicker(
    private val channelCount: Int,
) {
    private var first = 0

    fun page(count: Int): List<Int> = List(minOf(count, channelCount)) { (first + it) % channelCount }

    /** Makes [channelNo] the first channel of the next page (e.g. the tile opened fullscreen). */
    fun startAt(channelNo: Int) {
        if (channelNo in 0 until channelCount) first = channelNo
    }

    fun shift(by: Int) {
        if (channelCount > 0) first = ((first + by) % channelCount + channelCount) % channelCount
    }
}
