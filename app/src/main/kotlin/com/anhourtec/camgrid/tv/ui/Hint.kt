package com.anhourtec.camgrid.tv.ui

import android.view.View
import android.widget.TextView

/** The small pill at the bottom of the live screen. Hides itself after a few seconds unless sticky. */
class Hint(
    private val view: TextView,
) {
    private var sticky = false
    private val hide = Runnable { if (!sticky) view.visibility = View.GONE }

    fun show(
        text: String,
        sticky: Boolean = false,
    ) {
        this.sticky = sticky
        view.text = text
        view.visibility = View.VISIBLE
        view.removeCallbacks(hide)
        if (!sticky) view.postDelayed(hide, SHOW_MS)
    }

    fun hide() {
        sticky = false
        view.visibility = View.GONE
    }

    private companion object {
        const val SHOW_MS = 5000L
    }
}
