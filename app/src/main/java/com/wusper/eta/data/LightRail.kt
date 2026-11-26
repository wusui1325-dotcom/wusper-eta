package com.wusper.eta.data

import android.content.Context
import org.json.JSONArray

class LightRailIndex(val stops: List<Stop>) {
    private val byId = stops.associateBy { it.id }

    fun byId(id: String): Stop? = byId[id]

    fun near(lat: Double, lng: Double, radius: Int = 1500): List<Pair<Stop, Int>> {
        if (lat == 0.0 || lng == 0.0) return emptyList()
        return stops
            .filter { it.lat != 0.0 && it.lng != 0.0 }
            .map { stop -> stop to haversineMeters(lat, lng, stop.lat, stop.lng).toInt() }
            .filter { it.second <= radius }
            .sortedBy { it.second }
            .take(4)
    }

    fun stopsOn(route: String, dir: String): List<RouteStop> {
        return stops.mapNotNull { stop ->
            val visit = stop.routes.firstOrNull { it.route == route && it.dir == dir } ?: return@mapNotNull null
            RouteStop(stop, visit.seq, visit.destTc, visit.destEn)
        }.sortedBy { it.seq }
    }

    fun opposite(stopId: String, route: String, dir: String): Visit? {
        val stop = byId[stopId] ?: return null
        return stop.routes.firstOrNull { it.route == route && it.dir != dir && it.dir != "S" }
    }

    fun searchRoutes(query: String): List<Route> {
        val seen = LinkedHashSet<String>()
        val ranked = ArrayList<Pair<Int, Route>>()
        for (stop in stops) {
            for (visit in stop.routes) {
                val key = "${visit.route}|${visit.dir}|${visit.destTc}"
                if (!seen.add(key)) continue
                val rank = routeSearchRank(query, visit.route, visit.destTc) ?: continue
                ranked.add(
                    rank to Route(
                        co = "LRT",
                        route = visit.route,
                        dir = visit.dir,
                        serviceType = "1",
                        dest = visit.destTc,
                        stopId = stop.id,
                        routeId = "",
                        region = "",
                        state = EtaState.Loading,
                        etas = emptyList(),
                    ),
                )
            }
        }
        return ranked.sortedWith(compareBy({ it.first }, { it.second.route.length }, { it.second.route })).map { it.second }
    }

    fun searchStops(query: String): List<com.wusper.eta.data.Stop> {
        return stops.mapNotNull { stop ->
            if (!matchesStopName(query, stop.name, stop.nameEn)) return@mapNotNull null
            com.wusper.eta.data.Stop(
                key = "LRT|${stop.id}",
                co = "LRT",
                id = stop.id,
                name = stop.name,
                meters = 0,
                lat = stop.lat,
                lng = stop.lng,
                routes = stop.routes.distinctBy { "${it.route}|${it.dir}" }.map { visit ->
                    Route(
                        co = "LRT",
                        route = visit.route,
                        dir = visit.dir,
                        serviceType = "1",
                        dest = visit.destTc,
                        stopId = stop.id,
                        routeId = "",
                        region = "",
                        state = EtaState.Loading,
                        etas = emptyList(),
                    )
                },
                nameEn = stop.nameEn,
            )
        }
    }

    data class Visit(
        val route: String,
        val dir: String,
        val seq: Int,
        val destTc: String,
        val destEn: String,
    )

    data class Stop(
        val id: String,
        val code: String,
        val name: String,
        val nameEn: String,
        val lat: Double,
        val lng: Double,
        val routes: List<Visit>,
    )

    data class RouteStop(val stop: Stop, val seq: Int, val destTc: String, val destEn: String)

    companion object {
        fun load(context: Context): LightRailIndex {
            val text = context.assets.open("lrt_stops.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val stops = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    val routes = buildList {
                        val items = o.optJSONArray("routes") ?: JSONArray()
                        for (j in 0 until items.length()) {
                            val r = items.optJSONObject(j) ?: continue
                            val code = r.optString("route")
                            if (code.isBlank()) continue
                            add(
                                Visit(
                                    route = code,
                                    dir = r.optString("dir").ifBlank { "1" },
                                    seq = r.optInt("seq", j + 1),
                                    destTc = r.optString("dest_tc"),
                                    destEn = r.optString("dest_en"),
                                ),
                            )
                        }
                    }
                    add(
                        Stop(
                            id = id,
                            code = o.optString("code"),
                            name = o.optString("name_tc").ifBlank { id },
                            nameEn = o.optString("name_en"),
                            lat = o.optDouble("lat", 0.0),
                            lng = o.optDouble("lng", 0.0),
                            routes = routes,
                        ),
                    )
                }
            }
            return LightRailIndex(stops)
        }
    }
}

fun lrtDestMatch(live: String, catalogue: String): Boolean {
    val a = live.replace(" ", "")
    val b = catalogue.replace(" ", "")
    if (a.isEmpty() || b.isEmpty()) return false
    return a == b || a.contains(b) || b.contains(a)
}

fun lrtMinutes(raw: String): Int? {
    val text = raw.trim()
    if (text.isEmpty() || text == "-" || text == "–" || text == "—") return null
    if (text.contains("即將") || text.contains("抵達") || text.contains("離開") ||
        text.contains("arriv", ignoreCase = true) || text.contains("depart", ignoreCase = true)
    ) {
        return 0
    }
    val digits = Regex("""\d+""").find(text)?.value?.toIntOrNull() ?: return null
    return digits.coerceAtLeast(0)
}
