package com.wusper.eta.data

import android.content.Context
import android.content.res.Configuration
import com.wusper.eta.ui.Ink
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object UiPrefs {
    private const val FILE = "wusper_ui"
    private const val LANG = "lang"
    private const val LARGE = "large_text"
    private const val CONTRAST = "high_contrast"
    private const val MOTION = "reduce_motion"
    private const val ROUTES = "route_fav"
    private const val ROUTE_ROWS = "route_fav_rows"
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

    fun savedRoutes(context: Context): List<FavRoute> {
        val raw = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ROUTE_ROWS, null) ?: return emptyList()
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                add(
                    FavRoute(
                        id = id,
                        title = o.optString("title"),
                        dest = o.optString("dest"),
                        stopName = o.optString("stop"),
                        co = o.optString("co"),
                        route = o.optString("route"),
                        dir = o.optString("dir"),
                        serviceType = o.optString("st").ifBlank { "1" },
                        routeId = o.optString("rid"),
                        region = o.optString("region"),
                        stopId = o.optString("sid"),
                        line = o.optString("line"),
                        side = o.optString("side"),
                    ),
                )
            }
        }
    }

    fun setSavedRoutes(context: Context, routes: List<FavRoute>) {
        val arr = JSONArray()
        routes.forEach { route ->
            arr.put(
                JSONObject()
                    .put("id", route.id)
                    .put("title", route.title)
                    .put("dest", route.dest)
                    .put("stop", route.stopName)
                    .put("co", route.co)
                    .put("route", route.route)
                    .put("dir", route.dir)
                    .put("st", route.serviceType)
                    .put("rid", route.routeId)
                    .put("region", route.region)
                    .put("sid", route.stopId)
                    .put("line", route.line)
                    .put("side", route.side),
            )
        }
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(ROUTE_ROWS, arr.toString()).apply()
    }

    fun setReduceMotion(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(MOTION, on).apply()
        Ink.reduceMotion = on
    }
}
