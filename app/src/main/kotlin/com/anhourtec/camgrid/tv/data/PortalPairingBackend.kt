package com.anhourtec.camgrid.tv.data

import com.anhourtec.camgrid.client.portal.LoginResult
import com.anhourtec.camgrid.client.portal.PortalClient
import com.anhourtec.camgrid.pairing.LoginOutcome
import com.anhourtec.camgrid.pairing.PairingBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Lets the pairing server use the TV's own portal session (the CAPTCHA is bound to it). */
class PortalPairingBackend(
    private val portal: PortalClient,
    private val fallbackError: (Int?) -> String,
) : PairingBackend {
    override fun captcha(): ByteArray = runBlocking(Dispatchers.IO) { portal.captchaImage() }

    override fun login(
        user: String,
        password: String,
        captcha: String,
    ): LoginOutcome =
        runBlocking(Dispatchers.IO) {
            when (val result = portal.login(user, password, captcha)) {
                LoginResult.Success -> LoginOutcome.Success
                is LoginResult.Failed -> LoginOutcome.Failed(result.message ?: fallbackError(result.code))
            }
        }
}
