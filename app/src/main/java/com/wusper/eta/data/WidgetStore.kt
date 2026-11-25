package com.wusper.eta.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WidgetChoice(
    val manual: Boolean = false,
    val route: FavRoute? = null,
    val stopId: String = "",
    val stopName: String = "",
    val manualB: Boolean = false,
    val routeB: FavRoute? = null,
    val stopIdB: String = "",
    val stopNameB: String = "",
)

data class WidgetFace(
    val route: String = "",
    val bound: String = "",
    val stop: String = "",
    val minutes: String = "",
    val settled: Boolean = false,
    val routeB: String = "",
    val boundB: String = "",
    val stopB: String = "",
    val minutesB: String = "",
    val settledB: Boolean = false,
)

object RouteUsage {
    private const val FILE = "wusper_usage"
    private const val ROWS = "rows"
    private const val LAST = "last"

    fun record(context: Context, route: FavRoute) {
        if (route.id.isBlank()) return
        val rows = read(context).toMutableList()
        val index = rows.indexOfFirst { it.first.id == route.id }
        if (index >= 0) {
            val current = rows[index]
            rows[index] = current.first.copy(
                title = route.title.ifBlank { current.first.title },
                dest = route.dest.ifBlank { current.first.dest },
                co = route.co.ifBlank { current.first.co },
                route = route.route.ifBlank { current.first.route },
                dir = route.dir.ifBlank { current.first.dir },
                serviceType = route.serviceType.ifBlank { current.first.serviceType },
                routeId = route.routeId.ifBlank { current.first.routeId },
                stopId = route.stopId.ifBlank { current.first.stopId },
                line = route.line.ifBlank { current.first.line },
                side = route.side.ifBlank { current.first.side },
            ) to (current.second + 1)
        } else {
            rows.add(route to 1)
        }
        val arr = JSONArray()
        rows.take(40).forEach { (item, count) ->
            arr.put(item.json().put("n", count))
        }
        context.prefs().edit().putString(ROWS, arr.toString()).putString(LAST, route.id).apply()
    }

    fun read(context: Context): List<Pair<FavRoute, Int>> {
        val raw = context.prefs().getString(ROWS, null) ?: return emptyList()
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val route = o.route() ?: continue
                add(route to o.optInt("n", 0))
            }
        }
    }

    fun last(context: Context): FavRoute? {
        val id = context.prefs().getString(LAST, null) ?: return null
        return read(context).firstOrNull { it.first.id == id }?.first
    }

    private fun Context.prefs() = getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

object WidgetPrefs {
    private const val FILE = "wusper_widgets"

    fun choice(context: Context, id: Int): WidgetChoice {
        val o = context.prefs().getString(key(id), null)?.let { raw ->
            try {
                JSONObject(raw)
            } catch (_: Exception) {
                null
            }
        } ?: return WidgetChoice()
        return WidgetChoice(
            manual = o.optBoolean("manual", false),
            route = o.optJSONObject("route")?.route(),
            stopId = o.optString("stopId"),
            stopName = o.optString("stopName"),
            manualB = o.optBoolean("manualB", false),
            routeB = o.optJSONObject("routeB")?.route(),
            stopIdB = o.optString("stopIdB"),
            stopNameB = o.optString("stopNameB"),
        )
    }

    fun saveChoice(context: Context, id: Int, choice: WidgetChoice) {
        val o = JSONObject()
            .put("manual", choice.manual)
            .put("stopId", choice.stopId)
            .put("stopName", choice.stopName)
            .put("manualB", choice.manualB)
            .put("stopIdB", choice.stopIdB)
            .put("stopNameB", choice.stopNameB)
        if (choice.route != null) o.put("route", choice.route.json())
        if (choice.routeB != null) o.put("routeB", choice.routeB.json())
        context.prefs().edit().putString(key(id), o.toString()).apply()
    }

    fun face(context: Context, id: Int): WidgetFace {
        val o = context.prefs().getString(faceKey(id), null)?.let { raw ->
            try {
                JSONObject(raw)
            } catch (_: Exception) {
                null
            }
        } ?: return WidgetFace()
        return WidgetFace(
            route = o.optString("route"),
            bound = o.optString("bound"),
            stop = o.optString("stop"),
            minutes = o.optString("minutes"),
            settled = o.optBoolean("settled", false),
            routeB = o.optString("routeB"),
            boundB = o.optString("boundB"),
            stopB = o.optString("stopB"),
            minutesB = o.optString("minutesB"),
            settledB = o.optBoolean("settledB", false),
        )
    }

    fun saveFace(context: Context, id: Int, face: WidgetFace) {
        context.prefs().edit().putString(
            faceKey(id),
            JSONObject()
                .put("route", face.route)
                .put("bound", face.bound)
                .put("stop", face.stop)
                .put("minutes", face.minutes)
                .put("settled", face.settled)
                .put("routeB", face.routeB)
                .put("boundB", face.boundB)
                .put("stopB", face.stopB)
                .put("minutesB", face.minutesB)
                .put("settledB", face.settledB)
                .toString(),
        ).apply()
    }

    fun remove(context: Context, id: Int) {
        context.prefs().edit().remove(key(id)).remove(faceKey(id)).apply()
    }

    private fun key(id: Int) = "w$id"

    private fun faceKey(id: Int) = "f$id"

    private fun Context.prefs() = getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

fun FavRoute.json(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("dest", dest)
    .put("stop", stopName)
    .put("co", co)
    .put("route", route)
    .put("dir", dir)
    .put("st", serviceType)
    .put("rid", routeId)
    .put("region", region)
    .put("sid", stopId)
    .put("line", line)
    .put("side", side)

fun JSONObject.route(): FavRoute? {
    val id = optString("id")
    if (id.isBlank()) return null
    return FavRoute(
        id = id,
        title = optString("title"),
        dest = optString("dest"),
        stopName = optString("stop"),
        co = optString("co"),
        route = optString("route"),
        dir = optString("dir"),
        serviceType = optString("st").ifBlank { "1" },
        routeId = optString("rid"),
        region = optString("region"),
        stopId = optString("sid"),
        line = optString("line"),
        side = optString("side"),
    )
}

fun RouteFocus.asUsage(): FavRoute = when (this) {
    is RouteFocus.Bus -> FavRoute(
        id = favId,
        title = route.route,
        dest = route.dest,
        stopName = "",
        co = route.co,
        route = route.route,
        dir = route.dir,
        serviceType = route.serviceType,
        routeId = route.routeId,
        region = route.region,
        stopId = route.stopId,
    )
    is RouteFocus.Mtr -> FavRoute(
        id = favId,
        title = name,
        dest = "",
        stopName = "",
        line = code,
        side = side,
    )
}
