package com.wusper.eta.data

import android.content.Context
import android.content.res.Configuration
import com.wusper.eta.ui.Ink
import java.util.Locale

object UiPrefs {
    private const val FILE = "wusper_ui"
    private const val LANG = "lang"
    private const val LARGE = "large_text"
    private const val CONTRAST = "high_contrast"
    private const val MOTION = "reduce_motion"
    private const val ROUTES = "route_fav"
    const val HANT = "zh-TW"
    const val HANS = "zh-CN"
    const val EN = "en"

    fun wrap(base: Context): Context {
        val lang = base.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(LANG, HANT) ?: HANT
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    fun language(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(LANG, HANT) ?: HANT

    fun loadIntoInk(context: Context) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        Ink.textScale = if (prefs.getBoolean(LARGE, false)) 1.28f else 1f
        Ink.highContrast = prefs.getBoolean(CONTRAST, false)
        Ink.reduceMotion = prefs.getBoolean(MOTION, false)
    }

    fun setLanguage(context: Context, tag: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(LANG, tag).commit()
    }

    fun setLargeText(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(LARGE, on).apply()
        Ink.textScale = if (on) 1.28f else 1f
    }

    fun setHighContrast(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(CONTRAST, on).apply()
        Ink.highContrast = on
    }

    fun routeFavorites(context: Context): Set<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(ROUTES, emptySet())?.toSet().orEmpty()

    fun setRouteFavorites(context: Context, ids: Set<String>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(ROUTES, HashSet(ids)).apply()
    }

    fun setReduceMotion(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(MOTION, on).apply()
        Ink.reduceMotion = on
    }
}
