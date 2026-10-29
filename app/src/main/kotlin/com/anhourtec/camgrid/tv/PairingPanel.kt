package com.anhourtec.camgrid.tv

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.anhourtec.camgrid.client.portal.PortalClient
import com.anhourtec.camgrid.pairing.PairingServer
import com.anhourtec.camgrid.pairing.PairingState
import com.anhourtec.camgrid.tv.data.LanAddress
import com.anhourtec.camgrid.tv.data.PortalPairingBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetAddress

/**
 * "Sign in from your phone": runs the LAN pairing server while the sign-in screen is open
 * (10 minutes at most) and shows its address and code.
 */
class PairingPanel(
    private val activity: Activity,
    private val portal: PortalClient,
    private val scope: CoroutineScope,
    private val onSignedIn: () -> Unit,
) {
    private var server: PairingServer? = null

    val isRunning: Boolean get() = server != null
    private var expiry: Job? = null

    fun start() {
        stop()
        val url = activity.findViewById<TextView>(R.id.pair_url)
        val code = activity.findViewById<TextView>(R.id.pair_code)
        val note = activity.findViewById<TextView>(R.id.pair_note)
        val newCode = activity.findViewById<Button>(R.id.new_code)
        newCode.setOnClickListener { start() }
        newCode.visibility = View.GONE
        note.setText(R.string.phone_note)

        val address = LanAddress.find()
        val started = address?.let { startServer(it, code) }
        if (started == null) {
            url.text = ""
            code.visibility = View.GONE
            note.setText(R.string.phone_no_network)
            return
        }
        code.visibility = View.VISIBLE
        url.text = started.url.removeSuffix("/")
        server = started
        expiry =
            scope.launch {
                delay(PairingServer.MAX_LIFETIME_MS)
                stop()
                code.setText(R.string.pair_code_expired)
                note.setText(R.string.phone_expired)
                newCode.visibility = View.VISIBLE
            }
    }

    fun stop() {
        expiry?.cancel()
        expiry = null
        server?.close()
        server = null
    }

    private fun startServer(
        address: InetAddress,
        code: TextView,
    ): PairingServer? {
        val state =
            PairingState(onCodeChanged = { c ->
                activity.runOnUiThread { code.text = activity.getString(R.string.pair_code_format, c.take(3), c.drop(3)) }
            })
        val backend =
            PortalPairingBackend(portal) { c -> activity.getString(R.string.login_failed_code, c?.toString().orEmpty()) }
        val pairing =
            PairingServer(
                bindAddress = address,
                backend = backend,
                state = state,
                onLanguage = { lang -> portal.cookies.setLanguage(lang) },
                onSignedIn = { activity.runOnUiThread(onSignedIn) },
            )
        return try {
            pairing.start()
            pairing
        } catch (_: IOException) {
            null // port in use or network gone: the panel shows the no-network hint
        }
    }
}
