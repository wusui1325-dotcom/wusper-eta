package com.wusper.eta.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TransitApi {
    suspend fun nearby(lat: Double, lng: Double, radius: Int): NearbyPage = withContext(Dispatchers.IO) {
        val url = "$WUSPER/nearby-routes?lat=$lat&lng=$lng&radius_m=$radius&include_gmb=1"
        val rows = JSONObject(get(url)).optJSONArray("routes")
        NearbyPage(group(rows), boundsOf(rows))
    }

    suspend fun otherBound(route: Route, lat: Double, lng: Double): BoundHit? = withContext(Dispatchers.IO) {
        val dir = oppositeDir(route.dir) ?: return@withContext null
        val dest = try {
            when (route.co) {
                "KMB" -> kmbDest(route.route, dir, route.serviceType)
                "CTB", "NWFB" -> ctbDest(route)
                "GMB" -> gmbDest(route, dir)
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
        val probe = route.copy(dir = dir, dest = dest)
        val stops = try {
            routeStops(probe)
        } catch (_: Exception) {
            emptyList()
        }
        val stop = nearestAlong(stops, lat, lng)
        val name = dest.ifBlank { stop?.name.orEmpty() }
        if (stop == null || name.isBlank()) return@withContext null
        val meters = if (stop.lat != 0.0 && lat != 0.0) {
            haversineMeters(lat, lng, stop.lat, stop.lng).toInt()
        } else {
            0
        }
        BoundHit(
            co = route.co,
            route = route.route,
            dir = dir,
            serviceType = route.serviceType,
            dest = name,
            stopId = stop.id,
            stopName = stop.name,
            routeId = route.routeId,
            region = route.region,
            meters = meters,
            lat = stop.lat,
            lng = stop.lng,
        )
    }

    suspend fun search(q: String): SearchBundle = withContext(Dispatchers.IO) {
        val url = "$WUSPER/search?q=${enc(q)}&limit=60"
        val o = JSONObject(get(url))
        SearchBundle(
            stops = (o.optJSONArray("stops") ?: JSONArray()).let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optJSONObject(i) ?: continue
                        val co = s.str("co")
                        val id = s.str("stop_id")
                        val nameZh = s.str("name_tc")
                        val nameEn = s.str("name_en")
                        if (id.isBlank() || (nameZh.isBlank() && nameEn.isBlank())) continue
                        add(
                            Stop(
                                key = "$co|$id",
                                co = co,
                                id = id,
                                name = nameZh.ifBlank { nameEn },
                                meters = 0,
                                lat = s.dbl("lat"),
                                lng = s.dbl("lng"),
                                routes = emptyList(),
                                nameEn = nameEn,
                            ),
                        )
                    }
                }
            },
            routes = (o.optJSONArray("routes") ?: JSONArray()).let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val r = arr.optJSONObject(i) ?: continue
                        val code = r.str("route")
                        if (code.isBlank()) continue
                        add(
                            Route(
                                co = r.str("co"),
                                route = code,
                                dir = r.str("dir"),
                                serviceType = r.str("service_type").ifBlank { "1" },
                                dest = r.str("dest_tc"),
                                stopId = "",
                                routeId = r.str("route_id"),
                                region = r.str("region"),
                                state = EtaState.Loading,
                                etas = emptyList(),
                            ),
                        )
                    }
                }
            },
            mtr = (o.optJSONArray("mtr_stations") ?: JSONArray()).let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optJSONObject(i) ?: continue
                        val sta = s.str("sta")
                        val nameZh = s.str("name_tc")
                        val nameEn = s.str("name_en")
                        if (sta.isBlank() || (nameZh.isBlank() && nameEn.isBlank())) continue
                        val codes = s.optJSONArray("lines").textList()
                        val names = s.optJSONArray("line_names_tc").textList()
                        add(
                            MtrStop(
                                sta = sta,
                                name = nameZh.ifBlank { nameEn },
                                meters = 0,
                                lat = 0.0,
                                lng = 0.0,
                                lines = codes.mapIndexed { index, code ->
                                    MtrLine(code, names.getOrElse(index) { code }, emptyList(), emptyList())
                                },
                                nameEn = nameEn,
                            ),
                        )
                    }
                }
            },
        )
    }

    suspend fun routesAt(lat: Double, lng: Double, stopId: String, co: String): Stop? =
        withContext(Dispatchers.IO) {
            val gmb = if (co == "GMB") "1" else "0"
            val url = "$WUSPER/routes-at?lat=$lat&lng=$lng&stop_id=${enc(stopId)}&co=${enc(co)}&include_gmb=$gmb"
            val o = JSONObject(get(url))
            val stop = o.optJSONObject("stop")
            val routes = parseRouteArray(o.optJSONArray("routes"), stopId)
            Stop(
                key = "$co|$stopId",
                co = co,
                id = stopId,
                name = stop?.str("name_tc").orEmpty().ifBlank { stopId },
                meters = 0,
                lat = stop?.dbl("lat") ?: lat,
                lng = stop?.dbl("lng") ?: lng,
                routes = routes,
            )
        }

    suspend fun routeStops(route: Route): List<AlongStop> = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$WUSPER/route-stops?co=${enc(route.co)}&route=${enc(route.route)}")
            append("&dir=${enc(route.dir)}&service_type=${enc(route.serviceType)}&limit=80")
            if (route.routeId.isNotBlank()) append("&route_id=${enc(route.routeId)}")
        }
        val arr = JSONObject(get(url)).optJSONArray("stops") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val id = s.str("stop_id")
                if (id.isBlank()) continue
                add(
                    AlongStop(
                        co = s.str("co").ifBlank { route.co },
                        id = id,
                        name = s.str("name_tc").ifBlank { id },
                        lat = s.dbl("lat"),
                        lng = s.dbl("lng"),
                        seq = readSeq(s, i + 1),
                    ),
                )
            }
        }.inStopOrder { it.seq }
    }

    suspend fun live(route: Route): List<EtaSlot> = withContext(Dispatchers.IO) {
        when (route.co) {
            "KMB" -> kmb(route)
            "CTB", "NWFB" -> ctb(route)
            "GMB" -> gmb(route)
            else -> listOf(EtaSlot(null, false))
        }
    }

    suspend fun mtr(line: String, sta: String, names: (String) -> String): Pair<List<MtrTrain>, List<MtrTrain>> =
        withContext(Dispatchers.IO) {
            val url = "https://rt.data.gov.hk/v1/transport/mtr/getSchedule.php?line=${enc(line)}&sta=${enc(sta)}"
            val root = JSONObject(get(url))
            if (root.optInt("status", 0) != 1) return@withContext emptyList<MtrTrain>() to emptyList()
            val block = root.optJSONObject("data")?.optJSONObject("$line-$sta")
                ?: return@withContext emptyList<MtrTrain>() to emptyList()
            block.optJSONArray("UP").trains(names) to block.optJSONArray("DOWN").trains(names)
        }

    private fun kmb(route: Route): List<EtaSlot> {
        val url = "https://data.etabus.gov.hk/v1/transport/kmb/eta/${enc(route.stopId)}/${enc(route.route)}/${enc(route.serviceType)}"
        val data = JSONObject(get(url)).optJSONArray("data") ?: JSONArray()
        return busSlots(data, route.dir)
    }

    private fun ctb(route: Route): List<EtaSlot> {
        val url = "https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/${enc(route.stopId)}/${enc(route.route)}"
        val data = JSONObject(get(url)).optJSONArray("data") ?: JSONArray()
        return busSlots(data, route.dir)
    }

    private fun gmb(route: Route): List<EtaSlot> {
        if (route.routeId.isBlank()) return listOf(EtaSlot(null, false))
        val url = "https://data.etagmb.gov.hk/eta/stop/${enc(route.stopId)}"
        val data = JSONObject(get(url, gmb = true)).optJSONArray("data") ?: JSONArray()
        val seq = route.dir.toIntOrNull()
        for (i in 0 until data.length()) {
            val row = data.optJSONObject(i) ?: continue
            if (row.optLong("route_id").toString() != route.routeId && row.str("route_id") != route.routeId) continue
            if (seq != null && row.optInt("route_seq", -1) != seq) continue
            if (!row.optBoolean("enabled", true)) return listOf(EtaSlot(null, false))
            val etas = row.optJSONArray("eta") ?: JSONArray()
            if (etas.length() == 0) return listOf(EtaSlot(null, false))
            return buildList {
                for (j in 0 until etas.length()) {
                    if (size >= 3) break
                    val e = etas.optJSONObject(j) ?: continue
                    val remark = e.str("remarks_tc")
                    val minutes = if (e.has("diff") && !e.isNull("diff")) e.optInt("diff") else minutesUntil(e.str("timestamp"))
                    if (minutes != null && minutes < 0) continue
                    add(EtaSlot(minutes, isScheduledRemark(remark)))
                }
                if (isEmpty()) add(EtaSlot(null, false))
            }
        }
        return listOf(EtaSlot(null, false))
    }

    private fun busSlots(data: JSONArray, dir: String): List<EtaSlot> {
        val rows = buildList {
            for (i in 0 until data.length()) {
                val row = data.optJSONObject(i) ?: continue
                val rowDir = row.str("dir")
                if (dir.isNotBlank() && rowDir.isNotBlank() && rowDir != dir) continue
                add(row)
            }
        }
        val slots = rows.mapNotNull { row ->
            val minutes = minutesUntil(row.str("eta")) ?: return@mapNotNull null
            EtaSlot(minutes, isScheduledRemark(row.str("rmk_tc")))
        }.take(3)
        if (slots.isNotEmpty()) return slots
        return listOf(EtaSlot(null, rows.any { isScheduledRemark(it.str("rmk_tc")) }))
    }

    private fun kmbDest(route: String, dir: String, serviceType: String): String {
        val word = if (dir == "I") "inbound" else "outbound"
        val url = "https://data.etabus.gov.hk/v1/transport/kmb/route/${enc(route)}/$word/${enc(serviceType.ifBlank { "1" })}"
        val data = JSONObject(get(url)).optJSONObject("data") ?: return ""
        return data.str("dest_tc")
    }

    private fun ctbDest(route: Route): String {
        val url = "https://rt.data.gov.hk/v2/transport/citybus/route/CTB/${enc(route.route)}"
        val data = JSONObject(get(url)).optJSONObject("data") ?: return ""
        return citybusOppositeDest(route.dir, route.dest, data.str("orig_tc"), data.str("dest_tc"))
    }

    private fun gmbDest(route: Route, dir: String): String {
        val regions = if (route.region.isNotBlank()) listOf(route.region) else listOf("HKI", "KLN", "NT")
        val seq = dir.toIntOrNull() ?: return ""
        for (region in regions) {
            val body = try {
                get("https://data.etagmb.gov.hk/route/${enc(region)}/${enc(route.route)}", gmb = true)
            } catch (_: Exception) {
                continue
            }
            val rows = JSONObject(body).optJSONArray("data") ?: continue
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                val id = item.optLong("route_id").toString()
                if (route.routeId.isNotBlank() && id != route.routeId && item.str("route_id") != route.routeId) continue
                val directions = item.optJSONArray("directions") ?: continue
                for (j in 0 until directions.length()) {
                    val bound = directions.optJSONObject(j) ?: continue
                    if (bound.optInt("route_seq", -1) != seq) continue
                    val dest = bound.str("dest_tc")
                    if (dest.isNotBlank()) return dest
                }
            }
        }
        return ""
    }

    private fun boundsOf(rows: JSONArray?): List<BoundHit> {
        if (rows == null) return emptyList()
        val out = ArrayList<BoundHit>(rows.length())
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val co = row.str("co")
            val id = row.str("stop_id")
            val code = row.str("route")
            val dir = row.str("dir")
            if (co.isBlank() || id.isBlank() || code.isBlank() || dir.isBlank()) continue
            val meters = if (row.has("distance_m") && !row.isNull("distance_m")) row.optDouble("distance_m").toInt() else 0
            out.add(
                BoundHit(
                    co = co,
                    route = code,
                    dir = dir,
                    serviceType = row.str("service_type").ifBlank { "1" },
                    dest = row.str("dest_tc"),
                    stopId = id,
                    stopName = row.str("stop_name_tc").ifBlank { id },
                    routeId = row.str("route_id"),
                    region = row.str("region"),
                    meters = meters,
                    lat = row.dbl("lat"),
                    lng = row.dbl("lng"),
                ),
            )
        }
        return out
    }

    private fun group(rows: JSONArray?): List<Stop> {
        if (rows == null) return emptyList()
        val order = LinkedHashMap<String, StopBuilder>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val co = row.str("co")
            val id = row.str("stop_id")
            val code = row.str("route")
            if (co.isBlank() || id.isBlank() || code.isBlank()) continue
            val key = "$co|$id"
            val meters = if (row.has("distance_m") && !row.isNull("distance_m")) row.optDouble("distance_m").toInt() else 0
            val stop = order.getOrPut(key) {
                StopBuilder(key, co, id, row.str("stop_name_tc").ifBlank { id }, meters)
            }
            val route = Route(
                co = co,
                route = code,
                dir = row.str("dir"),
                serviceType = row.str("service_type").ifBlank { "1" },
                dest = row.str("dest_tc"),
                stopId = id,
                routeId = row.str("route_id"),
                region = row.str("region"),
                state = EtaState.Loading,
                etas = emptyList(),
            )
            if (stop.routes.none { it.key == route.key }) stop.routes.add(route)
        }
        return order.values.map { builder ->
            Stop(
                key = builder.key,
                co = builder.co,
                id = builder.id,
                name = builder.name,
                meters = builder.meters,
                lat = 0.0,
                lng = 0.0,
                routes = builder.routes.sortedWith(compareBy({ routeRank(it.route) }, { it.route }, { it.serviceType })),
            )
        }.sortedBy { it.meters }.take(16)
    }

    private fun parseRouteArray(rows: JSONArray?, fallbackStop: String): List<Route> {
        if (rows == null) return emptyList()
        val out = ArrayList<Route>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val code = row.str("route")
            val co = row.str("co")
            if (code.isBlank() || co.isBlank()) continue
            val stopId = row.str("stop_id").ifBlank { fallbackStop }
            val route = Route(
                co = co,
                route = code,
                dir = row.str("dir"),
                serviceType = row.str("service_type").ifBlank { "1" },
                dest = row.str("dest_tc"),
                stopId = stopId,
                routeId = row.str("route_id"),
                region = row.str("region"),
                state = EtaState.Loading,
                etas = emptyList(),
            )
            if (out.none { it.key == route.key }) out.add(route)
        }
        return out.sortedWith(compareBy({ routeRank(it.route) }, { it.route }))
    }

    suspend fun computeRoute(origin: Place, dest: Place, mode: String): TripPlan? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("origin", endpoint(origin))
            .put("destination", endpoint(dest))
            .put("mode", if (mode == "walk") "walk" else "transit")
        val text = try {
            post("$WUSPER/compute-routes", body.toString())
        } catch (_: Exception) {
            return@withContext null
        }
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return@withContext null
        }
        if (!root.optBoolean("ok")) return@withContext null
        val minutes = (root.optDouble("duration_s", 0.0) / 60.0).toInt().coerceAtLeast(0)
        val meters = root.optInt("distance_m", root.optDouble("distance_m", 0.0).toInt())
        val steps = buildList {
            val arr = root.optJSONArray("steps") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val step = arr.optJSONObject(i) ?: continue
                val ride = step.optString("type") == "transit"
                val route = step.optString("route")
                val towards = step.optString("towards")
                val stepMin = (step.optDouble("duration_s", 0.0) / 60.0).toInt().coerceAtLeast(0)
                val (title, detail) = planCopy(spokenLines(step), ride, route, towards)
                add(PlanStep(if (ride) "transit" else "walk", title, detail, stepMin))
            }
        }
        TripPlan(false, minutes, meters, steps, "")
    }

    private fun readSeq(row: JSONObject, fallback: Int): Int {
        if (!row.has("seq") || row.isNull("seq")) return fallback
        val raw = row.opt("seq")
        val text = when (raw) {
            is Number -> raw.toInt().toString()
            else -> raw?.toString().orEmpty()
        }
        return parseStopSeq(text, fallback)
    }

    private fun spokenLines(step: JSONObject): List<String> {
        val lines = ArrayList<String>()
        fun add(raw: String?) {
            plainGoogle(raw).lineSequence().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                if (lines.none { it == line }) lines.add(line)
            }
        }
        add(step.optString("instruction"))
        for (key in listOf("lines", "details", "instructions", "sub_instructions", "substeps")) {
            when (val value = if (step.has(key)) step.opt(key) else null) {
                is String -> add(value)
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        when (val item = value.opt(i)) {
                            is String -> add(item)
                            is JSONObject -> {
                                add(item.optString("instruction"))
                                add(item.optString("text"))
                            }
                        }
                    }
                }
            }
        }
        return lines
    }

    private fun plainGoogle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    private fun endpoint(place: Place): JSONObject {
        return if (place.lat != 0.0 || place.lng != 0.0) {
            JSONObject().put("lat", place.lat).put("lng", place.lng)
        } else {
            JSONObject().put("query", place.name)
        }
    }

    private fun post(url: String, json: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; WusperETA) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            )
        }
        try {
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String, gmb: Boolean = false): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500
            readTimeout = 3500
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "zh-HK,zh-TW;q=0.9")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; WusperETA) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
            )
            if (gmb || url.contains("etagmb.gov.hk")) {
                setRequestProperty("Referer", "https://www.etagmb.gov.hk/")
                setRequestProperty("Origin", "https://www.etagmb.gov.hk")
            }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private class StopBuilder(
        val key: String,
        val co: String,
        val id: String,
        val name: String,
        val meters: Int,
        val routes: MutableList<Route> = mutableListOf(),
    )

    companion object {
        private const val WUSPER = "https://wusper.com/api/eta"
    }
}

private fun JSONArray?.trains(names: (String) -> String): List<MtrTrain> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            if (size >= 3) break
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("valid", "Y") == "N") continue
            val minutes = o.optString("ttnt").toIntOrNull()
            val dest = o.optString("dest")
            add(MtrTrain(minutes, names(dest), false, o.optString("plat")))
        }
    }
}

private fun JSONArray?.textList(): List<String> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) add(s)
        }
    }
}

private fun JSONObject.str(key: String): String = if (!has(key) || isNull(key)) "" else optString(key, "")

private fun JSONObject.dbl(key: String): Double {
    if (!has(key) || isNull(key)) return 0.0
    val n = optDouble(key, 0.0)
    return if (n.isNaN()) 0.0 else n
}

private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
