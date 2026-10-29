package com.anhourtec.camgrid.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.anhourtec.camgrid.client.portal.LoginResult
import com.anhourtec.camgrid.client.portal.PortalClient
import com.anhourtec.camgrid.tv.data.AppLanguage
import com.anhourtec.camgrid.tv.data.SecretStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException

class MainActivity : Activity() {
    private val scope = MainScope()
    private val http = OkHttpClient()
    private val portal = PortalClient(http)
    private lateinit var store: SecretStore
    private var live: LiveScreen? = null
    private var resumeLiveOnReturn = false
    private var restartPairingOnReturn = false
    private val pairing = PairingPanel(this, portal, scope) { onSignedIn() }

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecretStore(this)
        portal.cookies.setLanguage(AppLanguage.current(this).code)
        val session = store.get(SESSION_KEY)
        if (session == null) {
            showLogin()
        } else {
            portal.cookies.restore(session)
            showLive()
        }
    }

    override fun onDestroy() {
        pairing.stop()
        live?.stop()
        scope.cancel()
        super.onDestroy()
    }

    /** Streams only run while the app is on screen (Home, screensaver or another app stops them). */
    override fun onStop() {
        super.onStop()
        // The pairing server only runs while its screen is actually visible.
        if (pairing.isRunning) {
            pairing.stop()
            restartPairingOnReturn = true
        }
        live?.let {
            it.stop()
            live = null
            resumeLiveOnReturn = true
        }
    }

    override fun onRestart() {
        super.onRestart()
        if (restartPairingOnReturn) {
            restartPairingOnReturn = false
            pairing.start()
        }
        if (resumeLiveOnReturn) {
            resumeLiveOnReturn = false
            showLive()
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = live?.onKeyDown(keyCode, event) == true || super.onKeyDown(keyCode, event)

    override fun onKeyLongPress(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = live?.onKeyLongPress(keyCode) == true || super.onKeyLongPress(keyCode, event)

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = live?.onKeyUp(keyCode) == true || super.onKeyUp(keyCode, event)

    private fun showLive() {
        pairing.stop()
        live = LiveScreen(this, http, portal, scope, ::onSignedOut, ::manageFromPhone).also { it.show() }
    }

    /** Phase 4 step 5 wires the phone layout editor here. */
    private fun manageFromPhone() = Unit

    private fun onSignedIn() {
        portal.cookies.sessionId?.let { store.put(SESSION_KEY, it) }
        showLive()
    }

    private fun onSignedOut() {
        live?.stop()
        live = null
        store.remove(SESSION_KEY)
        portal.cookies.clear()
        portal.cookies.setLanguage(AppLanguage.current(this).code)
        showLogin()
    }

    private fun showLogin() {
        setContentView(R.layout.activity_login)
        setUpLanguageButton()
        TvLoginForm(this, portal, scope, ::onSignedIn).setUp()
        pairing.start()
        findViewById<EditText>(R.id.email).requestFocus()
    }

    private fun setUpLanguageButton() {
        val button = findViewById<Button>(R.id.language)
        val current = AppLanguage.current(this)
        button.text = getString(R.string.language_button, current.displayName)
        button.setOnClickListener {
            val languages = AppLanguage.entries
            AlertDialog
                .Builder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(languages.map { it.displayName }.toTypedArray(), languages.indexOf(current)) { dialog, which ->
                    dialog.dismiss()
                    if (languages[which] != current) {
                        AppLanguage.save(this, languages[which])
                        recreate()
                    }
                }.show()
        }
    }

    private companion object {
        const val SESSION_KEY = "portal_session"
    }
}
