package com.anhourtec.camgrid.tv

import android.app.Activity
import android.graphics.BitmapFactory
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.anhourtec.camgrid.client.portal.LoginResult
import com.anhourtec.camgrid.client.portal.PortalClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.IOException

/** "Or sign in here on the TV": email, password and the portal's CAPTCHA, typed with the remote. */
class TvLoginForm(
    private val activity: Activity,
    private val portal: PortalClient,
    private val scope: CoroutineScope,
    private val onSignedIn: () -> Unit,
) {
    fun setUp() {
        val email = activity.findViewById<EditText>(R.id.email)
        val password = activity.findViewById<EditText>(R.id.password)
        val code = activity.findViewById<EditText>(R.id.code)
        val captcha = activity.findViewById<ImageView>(R.id.captcha)
        val status = activity.findViewById<TextView>(R.id.status)
        val signIn = activity.findViewById<Button>(R.id.sign_in)

        fun loadCaptcha() {
            code.text.clear()
            scope.launch {
                runCatching { portal.captchaImage() }
                    .onSuccess { bytes -> captcha.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) }
                    .onFailure { status.setText(R.string.login_captcha_error) }
            }
        }

        fun submit() {
            signIn.isEnabled = false
            status.setText(R.string.login_signing_in)
            scope.launch {
                val result =
                    try {
                        portal.login(email.text.toString().trim(), password.text.toString(), code.text.toString().trim())
                    } catch (e: IOException) {
                        LoginResult.Failed(null, e.message)
                    }
                signIn.isEnabled = true
                password.text.clear()
                when (result) {
                    LoginResult.Success -> {
                        onSignedIn()
                    }

                    is LoginResult.Failed -> {
                        status.text =
                            result.message?.let { activity.getString(R.string.login_failed, it) }
                                ?: activity.getString(R.string.login_failed_code, result.code?.toString().orEmpty())
                        loadCaptcha()
                        password.requestFocus()
                    }
                }
            }
        }

        activity.findViewById<Button>(R.id.new_captcha).setOnClickListener { loadCaptcha() }
        signIn.setOnClickListener { submit() }
        code.setOnEditorActionListener { _, action, _ ->
            (action == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        loadCaptcha()
    }
}
