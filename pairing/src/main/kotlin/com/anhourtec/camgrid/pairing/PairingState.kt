package com.anhourtec.camgrid.pairing

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The pairing rules from docs/SPEC.md §5.2, independent of any network code:
 * a 6-digit code shown on the TV; 5 wrong tries → new code + 30 s lockout; a successful code gives
 * the client a 256-bit token, valid 5 minutes, bound to its IP, used up by a successful login
 * (and dropped after 5 failed logins). All comparisons are constant-time.
 */
class PairingState(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    private val onCodeChanged: (String) -> Unit = {},
) {
    sealed interface CodeResult {
        data class Paired(
            val token: String,
        ) : CodeResult

        data object Wrong : CodeResult

        data class LockedOut(
            val retryInMs: Long,
        ) : CodeResult
    }

    private class Pairing(
        val ip: String,
        var expiresAt: Long,
        var failedLogins: Int = 0,
    )

    @get:Synchronized
    var code: String = newCode()
        private set

    private var failedCodes = 0
    private var lockedUntil = 0L
    private val pairings = mutableMapOf<String, Pairing>()

    init {
        onCodeChanged(code)
    }

    @Synchronized
    fun submitCode(
        ip: String,
        attempt: String,
    ): CodeResult {
        val now = clock()
        return when {
            now < lockedUntil -> CodeResult.LockedOut(lockedUntil - now)
            constantTimeEquals(attempt.trim(), code) -> pair(ip, now)
            ++failedCodes >= MAX_CODE_ATTEMPTS -> lockOut(now)
            else -> CodeResult.Wrong
        }
    }

    private fun pair(
        ip: String,
        now: Long,
    ): CodeResult {
        failedCodes = 0
        rotateCode() // the code is single-use
        val token = newToken()
        pairings[token] = Pairing(ip, now + TOKEN_LIFETIME_MS)
        return CodeResult.Paired(token)
    }

    private fun lockOut(now: Long): CodeResult {
        failedCodes = 0
        lockedUntil = now + LOCKOUT_MS
        rotateCode()
        return CodeResult.LockedOut(LOCKOUT_MS)
    }

    /** True if [token] was issued to [ip] and has not expired or been used. */
    @Synchronized
    fun isPaired(
        token: String?,
        ip: String,
    ): Boolean = find(token, ip) != null

    /** Call after a failed login with [token]; returns false once the token has been revoked. */
    @Synchronized
    fun loginFailed(
        token: String,
        ip: String,
    ): Boolean {
        val pairing = find(token, ip) ?: return false
        if (++pairing.failedLogins >= MAX_LOGIN_ATTEMPTS) {
            pairings.entries.removeAll { constantTimeEquals(it.key, token) }
            return false
        }
        return true
    }

    /** Keeps an active editing session alive: another [TOKEN_LIFETIME_MS] from now. */
    @Synchronized
    fun touch(
        token: String?,
        ip: String,
    ) {
        find(token, ip)?.let { it.expiresAt = clock() + TOKEN_LIFETIME_MS }
    }

    /** Call after a successful login: every token is used up. */
    @Synchronized
    fun loginSucceeded() = pairings.clear()

    private fun find(
        token: String?,
        ip: String,
    ): Pairing? {
        if (token == null) return null
        val now = clock()
        pairings.entries.removeAll { it.value.expiresAt <= now }
        // Compare against every entry so timing does not depend on which token matched.
        var match: Pairing? = null
        pairings.forEach { (t, p) -> if (constantTimeEquals(t, token) && p.ip == ip) match = p }
        return match
    }

    private fun rotateCode() {
        code = newCode()
        onCodeChanged(code)
    }

    private fun newCode(): String = random.nextInt(CODE_SPACE).toString().padStart(CODE_DIGITS, '0')

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val CODE_DIGITS = 6
        private const val CODE_SPACE = 1_000_000
        const val MAX_CODE_ATTEMPTS = 5
        const val LOCKOUT_MS = 30_000L
        const val TOKEN_LIFETIME_MS = 5 * 60_000L
        const val MAX_LOGIN_ATTEMPTS = 5
        private const val TOKEN_BYTES = 32

        fun constantTimeEquals(
            a: String,
            b: String,
        ): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}
