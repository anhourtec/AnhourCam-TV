package com.anhourtec.camgrid.tv

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.anhourtec.camgrid.layouts.Layout
import com.anhourtec.camgrid.pairing.LayoutBackend
import com.anhourtec.camgrid.pairing.LayoutSnapshot
import com.anhourtec.camgrid.pairing.PairingServer
import com.anhourtec.camgrid.pairing.PairingState
import com.anhourtec.camgrid.tv.data.LanAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * "Edit layout from phone or computer": a corner card over the live grid with the address and a
 * fresh code, backed by the pairing server in layout mode. Saves apply to the grid immediately.
 * Runs only while the card is shown (10 minutes at most); Back closes it.
 */
internal class ManagePanel(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val snapshot: () -> LayoutSnapshot,
    private val onSave: (Layout) -> Unit,
) {
    private val panel: View = activity.findViewById(R.id.manage_panel)
    private var server: PairingServer? = null
    private var expiry: Job? = null

    val isOpen: Boolean get() = server != null

    fun start() {
        stop()
        val url = activity.findViewById<TextView>(R.id.manage_url)
        val code = activity.findViewById<TextView>(R.id.manage_code)
        val address = LanAddress.find()
        val state =
            PairingState(onCodeChanged = { c ->
                activity.runOnUiThread { code.text = activity.getString(R.string.pair_code_format, c.take(3), c.drop(3)) }
            })
        val readSnapshot = snapshot
        val backend =
            object : LayoutBackend {
                override fun snapshot() = readSnapshot()

                override fun save(layout: Layout) = activity.runOnUiThread { onSave(layout) }
            }
        val started =
            address?.let {
                try {
                    PairingServer(it, layouts = backend, state = state, onSignedIn = {}).also(PairingServer::start)
                } catch (_: IOException) {
                    null
                }
            }
        if (started == null) {
            url.setText(R.string.phone_no_network)
            code.visibility = View.GONE
        } else {
            url.text = started.url.removeSuffix("/")
            code.visibility = View.VISIBLE
        }
        server = started
        panel.visibility = View.VISIBLE
        expiry =
            scope.launch {
                delay(PairingServer.MAX_LIFETIME_MS)
                stop()
            }
    }

    fun stop() {
        expiry?.cancel()
        expiry = null
        server?.close()
        server = null
        panel.visibility = View.GONE
    }
}
