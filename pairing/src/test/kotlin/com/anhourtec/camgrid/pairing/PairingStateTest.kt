package com.anhourtec.camgrid.pairing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PairingStateTest {
    private var now = 1_000_000L
    private val codes = mutableListOf<String>()
    private val state = PairingState(clock = { now }, onCodeChanged = { codes += it })

    @Test
    fun `code is six digits and announced`() {
        assertTrue(Regex("\\d{6}").matches(state.code))
        assertEquals(listOf(state.code), codes)
    }

    @Test
    fun `right code pairs the client and the code is single use`() {
        val code = state.code
        val result = state.submitCode(IP, code) as PairingState.CodeResult.Paired
        assertEquals(64, result.token.length)
        assertTrue(state.isPaired(result.token, IP))
        assertNotEquals(code, state.code)
        assertEquals(PairingState.CodeResult.Wrong, state.submitCode(IP, code))
    }

    @Test
    fun `token is bound to the client address and expires after five minutes`() {
        val token = (state.submitCode(IP, state.code) as PairingState.CodeResult.Paired).token
        assertFalse(state.isPaired(token, OTHER_IP))
        assertFalse(state.isPaired("0".repeat(64), IP))
        assertFalse(state.isPaired(null, IP))
        now += PairingState.TOKEN_LIFETIME_MS
        assertFalse(state.isPaired(token, IP))
    }

    @Test
    fun `five wrong codes rotate the code and lock out for thirty seconds`() {
        val original = state.code
        val wrong = if (original == "000000") "111111" else "000000"
        repeat(4) { assertEquals(PairingState.CodeResult.Wrong, state.submitCode(IP, wrong)) }
        assertTrue(state.submitCode(IP, wrong) is PairingState.CodeResult.LockedOut)
        assertNotEquals(original, state.code)

        val locked = state.submitCode(IP, state.code)
        assertTrue(locked is PairingState.CodeResult.LockedOut)
        now += PairingState.LOCKOUT_MS
        assertTrue(state.submitCode(IP, state.code) is PairingState.CodeResult.Paired)
    }

    @Test
    fun `five failed logins revoke the token and success clears all tokens`() {
        val token = (state.submitCode(IP, state.code) as PairingState.CodeResult.Paired).token
        repeat(4) { assertTrue(state.loginFailed(token, IP)) }
        assertFalse(state.loginFailed(token, IP))
        assertFalse(state.isPaired(token, IP))

        val second = (state.submitCode(IP, state.code) as PairingState.CodeResult.Paired).token
        state.loginSucceeded()
        assertFalse(state.isPaired(second, IP))
    }

    private companion object {
        const val IP = "10.20.30.20" // check-redaction:allow fake test address
        const val OTHER_IP = "10.20.30.99" // check-redaction:allow fake test address
    }
}
