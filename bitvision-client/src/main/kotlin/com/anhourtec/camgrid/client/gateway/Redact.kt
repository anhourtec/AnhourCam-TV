package com.anhourtec.camgrid.client.gateway

/** Masks credentials and account names in protocol text before it is shown anywhere. */
object Redact {
    private val fieldNames =
        listOf(
            "devPasswd",
            "dev_passwd",
            "devUserName",
            "dev_username",
            "password",
            "passwd",
            "pwd",
            "token",
            "session",
            "sessionId",
            "sid",
            "cookie",
            "app_secret",
            "appSecret",
        ).joinToString("|")

    // The value pattern understands JSON escapes, so a password containing \" is masked completely.
    private val secretFields = Regex(""""($fieldNames)"\s*:\s*"(?:\\.|[^"\\])*"""")
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    fun secrets(text: String): String = email.replace(secretFields.replace(text) { "\"${it.groupValues[1]}\":\"<redacted>\"" }, "<email>")
}
