package com.anhourtec.camgrid.pairing

/** What the TV does on behalf of the paired phone. Calls are blocking and run on server threads. */
interface PairingBackend {
    /** A CAPTCHA image fetched with the TV's own portal session. */
    fun captcha(): ByteArray

    fun login(
        user: String,
        password: String,
        captcha: String,
    ): LoginOutcome
}

sealed interface LoginOutcome {
    data object Success : LoginOutcome

    /** [message] is shown to the user, e.g. the portal's "wrong verification code". */
    data class Failed(
        val message: String,
    ) : LoginOutcome
}
