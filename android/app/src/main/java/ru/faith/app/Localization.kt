package ru.faith.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

private const val PreferencesName = "faith_preferences"
private const val LanguageKey = "interface_language"

fun Context.savedLanguage(): String? =
    getSharedPreferences(PreferencesName, Context.MODE_PRIVATE).getString(LanguageKey, null)

fun Context.saveLanguage(language: String) {
    getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        .edit()
        .putString(LanguageKey, language)
        .apply()
}

fun Context.withLanguage(language: String): Context {
    val locale = Locale.forLanguageTag(language)
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    configuration.setLayoutDirection(locale)
    return createConfigurationContext(configuration)
}
