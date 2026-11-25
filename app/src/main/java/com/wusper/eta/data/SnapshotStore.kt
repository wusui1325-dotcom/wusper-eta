package com.wusper.eta.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object SnapshotStore {
    private const val FILE = "board.json"
    private const val MAX_BYTES = 100_000
    private const val FRESH_MS = 180_000L

    fun read(context: Context): Snapshot {
        val file = File(context.filesDir, FILE)
        if (file.isFile && file.length() in 32..MAX_BYTES) {
            parse(readText(file))?.let { return paint(it) }
        }
        return centralBoard()
    }

    fun write(context: Context, snapshot: Snapshot) {
        val dir = context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(toJson(snapshot).toString())
        val dest = File(dir, FILE)
        if (!tmp.renameTo(dest)) {
            dest.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun readText(file: File): String = file.readText()

    private fun paint(snapshot: Snapshot): Snapshot {
        val fresh = snapshot.at > 0 && System.currentTimeMillis() - snapshot.at < FRESH_MS
        return if (fresh) snapshot else snapshot.cleared()
    }

    fun parse(text: String): Snapshot? = try {
        val o = JSONObject(text)
        Snapshot(
            at = o.optLong("at", 0),
            tab = tabOf(o.s("tab")),
            lat = o.d("lat"),
            lng = o.d("lng"),
            radius = o.optInt("radius", 400).coerceIn(200, 1200),
            openKey = o.s("open"),
            stops = o.optJSONArray("stops").stops(),
            mtr = o.optJSONArray("mtr").mtr(),
            fav = o.optJSONArray("fav").saved(),
            recent = o.optJSONArray("recent").saved(),
            origin = o.optJSONObject("origin")?.place(),
            dest = o.optJSONObject("dest")?.place(),
            planMode = o.s("planMode").ifBlank { "transit" },
            plan = o.optJSONObject("plan")?.plan(),
        )
    } catch (_: Exception) {
        null
    }

    fun toJson(snapshot: Snapshot): JSONObject = JSONObject().apply {
        put("v", 1)
        put("at", snapshot.at)
        put("tab", snapshot.tab.name.lowercase())
        put("lat", snapshot.lat)
        put("lng", snapshot.lng)
        put("radius", snapshot.radius)
        put("open", snapshot.openKey)
        put("stops", JSONArray().apply { snapshot.stops.take(10).forEach { put(it.json()) } })
        put("mtr", JSONArray().apply { snapshot.mtr.take(4).forEach { put(it.json()) } })
        put("fav", JSONArray().apply { snapshot.fav.take(12).forEach { put(it.json()) } })
        put("recent", JSONArray().apply { snapshot.recent.take(8).forEach { put(it.json()) } })
        put("origin", snapshot.origin?.json() ?: JSONObject.NULL)
        put("dest", snapshot.dest?.json() ?: JSONObject.NULL)
        put("planMode", snapshot.planMode)
        put("plan", snapshot.plan?.json() ?: JSONObject.NULL)
    }

}

private fun tabOf(raw: String): Tab = when (raw.lowercase()) {
    "saved", "fav" -> Tab.Saved
    "mtr" -> Tab.Mtr
    "search" -> Tab.Search
    "plan", "nav" -> Tab.Nav
    else -> Tab.Bus
}

private fun JSONArray?.stops(): List<Stop> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val co = o.s("co")
            val id = o.s("id")
            if (co.isBlank() || id.isBlank()) continue
            val routes = o.optJSONArray("routes").routes(id)
            add(
                Stop(
                    key = o.s("key").ifBlank { "$co|$id" },
                    co = co,
                    id = id,
                    name = o.s("name").ifBlank { id },
                    meters = o.optInt("m", 0),
                    lat = o.d("lat"),
                    lng = o.d("lng"),
                    routes = routes,
                ),
            )
        }
    }
}

private fun JSONArray?.routes(parentStop: String): List<Route> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.s("r")
            if (code.isBlank()) continue
            val state = when (o.s("state")) {
                "ready" -> EtaState.Ready
                "failed" -> EtaState.Failed
                else -> if (o.isNull("etas") || !o.has("etas")) EtaState.Loading else EtaState.Ready
            }
            val etas = if (o.isNull("etas")) emptyList() else o.optJSONArray("etas").etas()
            add(
                Route(
                    co = o.s("co"),
                    route = code,
                    dir = o.s("dir"),
                    serviceType = o.s("st").ifBlank { "1" },
                    dest = o.s("dest"),
                    stopId = o.s("stopId").ifBlank { parentStop },
                    routeId = o.s("rid"),
                    region = o.s("region"),
                    state = if (etas.isEmpty() && state == EtaState.Ready) EtaState.Loading else state,
                    etas = etas,
                ),
            )
        }
    }
}

private fun JSONArray?.etas(): List<EtaSlot> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val minutes = if (!o.has("min") || o.isNull("min")) null else o.optInt("min")
            add(EtaSlot(minutes, o.optBoolean("sch", false)))
        }
    }
}

private fun JSONArray?.mtr(): List<MtrStop> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val sta = o.s("sta")
            if (sta.isBlank()) continue
            val codes = o.optJSONArray("lines").strings()
            val names = o.optJSONArray("lineNames").strings()
            val lines = if (o.has("boards")) {
                o.optJSONArray("boards").boards()
            } else {
                codes.mapIndexed { index, code ->
                    MtrLine(code, names.getOrElse(index) { code }, emptyList(), emptyList())
                }
            }
            add(
                MtrStop(
                    sta = sta,
                    name = o.s("name").ifBlank { sta },
                    meters = o.optInt("m", 0),
                    lat = o.d("lat"),
                    lng = o.d("lng"),
                    lines = lines,
                ),
            )
        }
    }
}

private fun JSONArray?.boards(): List<MtrLine> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                MtrLine(
                    code = o.s("code"),
                    name = o.s("name"),
                    up = o.optJSONArray("up").trains(),
                    down = o.optJSONArray("down").trains(),
                ),
            )
        }
    }
}

private fun JSONArray?.trains(): List<MtrTrain> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val minutes = if (!o.has("min") || o.isNull("min")) null else o.optInt("min")
            add(MtrTrain(minutes, o.s("dest"), o.optBoolean("sch", false), o.s("plat")))
        }
    }
}

private fun JSONArray?.saved(): List<SavedStop> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.s("key")
            if (key.isBlank()) continue
            add(
                SavedStop(
                    key = key,
                    co = o.s("co"),
                    id = o.s("id"),
                    name = o.s("name"),
                    lat = o.d("lat"),
                    lng = o.d("lng"),
                    label = o.s("label"),
                ),
            )
        }
    }
}

private fun JSONArray?.strings(): List<String> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) add(arr.optString(i))
    }
}

private fun JSONObject.place(): Place? {
    val name = s("name")
    if (name.isBlank()) return null
    return Place(name, d("lat"), d("lng"))
}

private fun JSONObject.plan(): TripPlan {
    val steps = optJSONArray("steps")
    val parsed = buildList {
        if (steps != null) {
            for (i in 0 until steps.length()) {
                val s = steps.optJSONObject(i) ?: continue
                add(PlanStep(s.s("mode"), s.s("title"), s.s("detail"), s.optInt("min", 0)))
            }
        }
    }
    return TripPlan(optBoolean("mock", false), optInt("min", 0), optInt("m", 0), parsed, s("poly"))
}

private fun Stop.json(): JSONObject = JSONObject().apply {
    put("key", key)
    put("co", co)
    put("id", id)
    put("name", name)
    put("m", meters)
    put("lat", lat)
    put("lng", lng)
    put("routes", JSONArray().apply { routes.take(6).forEach { put(it.json()) } })
}

private fun Route.json(): JSONObject = JSONObject().apply {
    put("co", co)
    put("r", route)
    put("dir", dir)
    put("st", serviceType)
    put("dest", dest)
    put("stopId", stopId)
    put("rid", routeId)
    put("region", region)
    put("state", state.name.lowercase())
    put(
        "etas",
        JSONArray().apply {
            etas.take(3).forEach { slot ->
                put(
                    JSONObject()
                        .put("min", slot.minutes ?: JSONObject.NULL)
                        .put("sch", slot.scheduled),
                )
            }
        },
    )
}

private fun MtrStop.json(): JSONObject = JSONObject().apply {
    put("sta", sta)
    put("name", name)
    put("m", meters)
    put("lat", lat)
    put("lng", lng)
    put("lines", JSONArray().apply { lines.forEach { put(it.code) } })
    put("lineNames", JSONArray().apply { lines.forEach { put(it.name) } })
    put("boards", JSONArray().apply { lines.forEach { put(it.json()) } })
}

private fun MtrLine.json(): JSONObject = JSONObject().apply {
    put("code", code)
    put("name", name)
    put("up", JSONArray().apply { up.take(3).forEach { put(it.json()) } })
    put("down", JSONArray().apply { down.take(3).forEach { put(it.json()) } })
}

private fun MtrTrain.json(): JSONObject = JSONObject()
    .put("min", minutes ?: JSONObject.NULL)
    .put("dest", dest)
    .put("sch", scheduled)
    .put("plat", platform)

private fun SavedStop.json(): JSONObject = JSONObject()
    .put("key", key)
    .put("co", co)
    .put("id", id)
    .put("name", name)
    .put("lat", lat)
    .put("lng", lng)
    .put("label", label)

private fun Place.json(): JSONObject = JSONObject().put("name", name).put("lat", lat).put("lng", lng)

private fun TripPlan.json(): JSONObject = JSONObject().apply {
    put("mock", mock)
    put("min", minutes)
    put("m", meters)
    put("poly", poly)
    put(
        "steps",
        JSONArray().apply {
            steps.take(12).forEach { step ->
                put(
                    JSONObject()
                        .put("mode", step.mode)
                        .put("title", step.title)
                        .put("detail", step.detail)
                        .put("min", step.minutes),
                )
            }
        },
    )
}

private fun JSONObject.s(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return optString(key, "")
}

private fun JSONObject.d(key: String): Double {
    if (!has(key) || isNull(key)) return 0.0
    val n = optDouble(key, 0.0)
    return if (n.isNaN()) 0.0 else n
}
