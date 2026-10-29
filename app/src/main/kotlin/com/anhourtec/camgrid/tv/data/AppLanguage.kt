package com.anhourtec.camgrid.tv.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's language, chosen in-app (default English). Codes are the portal's own (cn, en, jp,
 * por, py) so the same value goes to BitVision's `language` cookie.
 */
enum class AppLanguage(
    val code: String,
    val locale: Locale,
    val displayName: String,
) {
    CN("cn", Locale.SIMPLIFIED_CHINESE, "中文（简体）"),
    EN("en", Locale.ENGLISH, "English"),
    JP("jp", Locale.JAPANESE, "日本語"),
    POR("por", Locale.forLanguageTag("pt"), "Português"),
    PY("py", Locale.forLanguageTag("ru"), "Русский"),
    ;

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "language"

        fun current(context: Context): AppLanguage {
            val code = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, EN.code)
            return entries.firstOrNull { it.code == code } ?: EN
        }

        fun save(
            context: Context,
            language: AppLanguage,
        ) = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, language.code)
            .apply()

        /** Wraps [base] so resources load in the chosen language. */
        fun wrap(base: Context): Context {
            val config = Configuration(base.resources.configuration)
            config.setLocale(current(base).locale)
            return base.createConfigurationContext(config)
        }
    }
}
