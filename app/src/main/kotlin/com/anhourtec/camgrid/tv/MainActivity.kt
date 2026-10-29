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
import com.anhourtec.camgrid.tv.data.IpCameraStore
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
        when {
            session != null -> {
                portal.cookies.restore(session)
                showLive()
            }

            ipOnly -> {
                showLive()
            }

            else -> {
                showLogin()
            }
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

    /** No BitVision account: only IP cameras (Phase 6). Remembered until the user signs in. */
    private var ipOnly: Boolean
        get() = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(IP_ONLY_KEY, false)
        set(value) = getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(IP_ONLY_KEY, value).apply()

    private fun showLive() {
        pairing.stop()
        val account = if (ipOnly && store.get(SESSION_KEY) == null) null else portal
        live = LiveScreen(this, http, account, IpCameraStore(this, store), scope, ::onSignedOut).also { it.show() }
    }

    private fun onSignedIn() {
        portal.cookies.sessionId?.let { store.put(SESSION_KEY, it) }
        ipOnly = false
        showLive()
    }

    /** Sign out of BitVision, or (IP cameras only) go to the sign-in screen to add an account. */
    private fun onSignedOut() {
        live?.stop()
        live = null
        ipOnly = false
        store.remove(SESSION_KEY)
        portal.cookies.clear()
        portal.cookies.setLanguage(AppLanguage.current(this).code)
        showLogin()
    }

    private fun showLogin() {
        setContentView(R.layout.activity_login)
        setUpLanguageButton()
        TvLoginForm(this, portal, scope, ::onSignedIn).setUp()
        findViewById<Button>(R.id.ip_only).setOnClickListener {
            ipOnly = true
            showLive()
        }
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
        const val PREFS = "app_mode"
        const val IP_ONLY_KEY = "ip_cameras_only"
    }
}
