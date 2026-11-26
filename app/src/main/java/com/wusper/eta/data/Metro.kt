package com.wusper.eta.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

data class MetroCity(val id: String, val key: String, val name: String)

data class MetroNames(val zh: String, val hant: String, val en: String) {
    fun pick(lang: String): String = when (lang) {
        UiPrefs.EN -> en.ifBlank { zh.ifBlank { hant } }
        UiPrefs.HANS -> zh.ifBlank { hant }
        else -> hant.ifBlank { zh }
    }.ifBlank { zh }
}

data class MetroStation(
    val name: String,
    val names: MetroNames,
    val transfer: Boolean,
    val x: Double?,
    val y: Double?,
)

data class MetroLine(
    val id: String,
    val name: String,
    val names: MetroNames,
    val stations: List<MetroStation>,
    val path: List<Pair<Double, Double>>,
    val color: String = "#5B7CFA",
)

data class MetroNetwork(
    val id: String,
    val key: String,
    val city: String,
    val lines: List<MetroLine>,
)

data class MetroSeg(
    val lineId: String,
    val lineName: String,
    val from: String,
    val to: String,
    val path: List<String>,
)

data class MetroRide(val transfers: Int, val stops: Int, val segments: List<MetroSeg>)

data class MetroCache(val savedAt: Long, val network: MetroNetwork)

const val METRO_CACHE_MS: Long = 30L * 24 * 60 * 60 * 1000

fun metroCacheFresh(savedAt: Long, now: Long): Boolean =
    savedAt > 0L && now >= savedAt && now - savedAt < METRO_CACHE_MS

fun planMetro(network: MetroNetwork, fromName: String, toName: String): MetroRide? {
    if (fromName.isBlank() || fromName == toName) return null
    val lines = network.lines
    val lineById = lines.associateBy { it.id }
    val linesAt = HashMap<String, ArrayList<Pair<String, Int>>>()
    for (line in lines) {
        line.stations.forEachIndexed { index, station ->
            linesAt.getOrPut(station.name) { ArrayList() }.add(line.id to index)
        }
    }
    val starts = linesAt[fromName] ?: return null
    if (toName !in linesAt) return null
    data class Cost(val transfers: Int, val stops: Int)
    data class Step(val prevKey: String?, val via: String, val fromSt: String, val toSt: String, val lineId: String)
    fun keyOf(station: String, lineId: String) = "$station\u0000$lineId"
    val best = HashMap<String, Cost>()
    val prev = HashMap<String, Step>()
    val heap = ArrayList<Quad>()
    fun push(transfers: Int, stops: Int, station: String, lineId: String, meta: Step?) {
        val key = keyOf(station, lineId)
        val current = best[key]
        if (current != null && (current.transfers < transfers || (current.transfers == transfers && current.stops <= stops))) return
        best[key] = Cost(transfers, stops)
        if (meta != null) prev[key] = meta
        heap.add(Quad(transfers, stops, station, lineId))
    }
    for ((lineId, _) in starts) push(0, 0, fromName, lineId, null)
    var goalKey: String? = null
    var goal: Cost? = null
    while (heap.isNotEmpty()) {
        var bestIndex = 0
        for (i in 1 until heap.size) {
            val a = heap[i]
            val b = heap[bestIndex]
            if (a.transfers < b.transfers || (a.transfers == b.transfers && a.stops < b.stops)) bestIndex = i
        }
        val node = heap.removeAt(bestIndex)
        val key = keyOf(node.station, node.lineId)
        val current = best[key] ?: continue
        if (current.transfers != node.transfers || current.stops != node.stops) continue
        if (node.station == toName) {
            goalKey = key
            goal = current
            break
        }
        val line = lineById[node.lineId] ?: continue
        val indexes = linesAt[node.station].orEmpty().filter { it.first == node.lineId }
        for ((_, index) in indexes) {
            for (dir in intArrayOf(-1, 1)) {
                val next = index + dir
                if (next !in line.stations.indices) continue
                val nextName = line.stations[next].name
                push(
                    node.transfers,
                    node.stops + 1,
                    nextName,
                    node.lineId,
                    Step(key, "ride", node.station, nextName, node.lineId),
                )
            }
        }
        for ((other, _) in linesAt[node.station].orEmpty()) {
            if (other == node.lineId) continue
            push(node.transfers + 1, node.stops, node.station, other, Step(key, "xfer", node.station, node.station, other))
        }
    }
    val found = goal ?: return null
    val goalAt = goalKey ?: return null
    val steps = ArrayList<Step>()
    var cursor: String? = goalAt
    while (cursor != null && prev.containsKey(cursor)) {
        val step = prev.getValue(cursor)
        steps.add(step)
        cursor = step.prevKey
    }
    steps.reverse()
    val segments = ArrayList<MetroSeg>()
    var current: MetroSeg? = null
    for (step in steps) {
        if (step.via == "xfer") {
            current?.let { segments.add(it) }
            current = null
            continue
        }
        val riding = current
        if (riding == null || riding.lineId != step.lineId) {
            riding?.let { segments.add(it) }
            val line = lineById[step.lineId] ?: continue
            current = MetroSeg(step.lineId, line.names.hant.ifBlank { line.name }, step.fromSt, step.toSt, listOf(step.fromSt, step.toSt))
        } else {
            current = riding.copy(to = step.toSt, path = riding.path + step.toSt)
        }
    }
    current?.let { segments.add(it) }
    if (segments.isEmpty()) return null
    return MetroRide(found.transfers, found.stops, segments)
}

private data class Quad(val transfers: Int, val stops: Int, val station: String, val lineId: String)

fun metroSvg(network: MetroNetwork, lang: String): String {
    val points = ArrayList<Pair<Double, Double>>()
    for (line in network.lines) {
        if (line.path.size >= 2) points.addAll(line.path) else {
            line.stations.forEach { station ->
                if (station.x != null && station.y != null) points.add(station.x to station.y)
            }
        }
    }
    if (points.isEmpty()) return ""
    var minX = Double.POSITIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY
    var maxX = Double.NEGATIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    for ((x, y) in points) {
        if (x < minX) minX = x
        if (y < minY) minY = y
        if (x > maxX) maxX = x
        if (y > maxY) maxY = y
    }
    val pad = 48.0
    val vb = "${minX - pad} ${minY - pad} ${(maxX - minX) + pad * 2} ${(maxY - minY) + pad * 2}"
    val body = buildString {
        for (line in network.lines) {
            val pts = if (line.path.size >= 2) line.path else line.stations.mapNotNull { station ->
                if (station.x == null || station.y == null) null else station.x to station.y
            }
            if (pts.size < 2) continue
            append("<polyline data-line=\"")
            append(xml(line.id))
            append("\" points=\"")
            append(pts.joinToString(" ") { "${it.first},${it.second}" })
            append("\" fill=\"none\" stroke=\"")
            append(xml(line.color.ifBlank { "#5B7CFA" }))
            append("\" stroke-width=\"8\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>")
        }
        val seen = HashSet<String>()
        for (line in network.lines) {
            for (station in line.stations) {
                if (station.x == null || station.y == null) continue
                if (!seen.add(station.name)) continue
                val r = if (station.transfer) 7 else 5
                append("<circle data-station=\"")
                append(xml(station.name))
                append("\" cx=\"${station.x}\" cy=\"${station.y}\" r=\"$r\" fill=\"#ffffff\" stroke=\"#222222\" stroke-width=\"2\"/>")
                append("<text x=\"${station.x + 8}\" y=\"${station.y - 8}\" font-size=\"14\" fill=\"#1A1A1A\">")
                append(xml(station.names.pick(lang)))
                append("</text>")
            }
        }
    }
    return """<svg xmlns="http://www.w3.org/2000/svg" viewBox="$vb">$body</svg>"""
}

private fun xml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")

object MetroStore {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; WusperETA) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    fun bundledCities(context: Context): List<MetroCity> {
        val text = context.assets.open("metro_cities.json").bufferedReader().use { it.readText() }
        return parseCities(text)
    }

    fun cities(): List<MetroCity>? = try {
        parseCities(get("https://wusper.com/api/metro/cities"))
    } catch (_: Exception) {
        null
    }

    fun read(context: Context, id: String, key: String, now: Long): MetroNetwork? {
        val file = file(context, id, key)
        if (!file.isFile) return null
        return try {
            val root = JSONObject(file.readText())
            val saved = root.optLong("savedAt", 0L)
            if (!metroCacheFresh(saved, now)) return null
            parseNetwork(root.optJSONObject("network") ?: return null)
        } catch (_: Exception) {
            null
        }
    }

    fun write(context: Context, network: MetroNetwork, now: Long) {
        val file = file(context, network.id, network.key)
        file.parentFile?.mkdirs()
        val root = JSONObject()
            .put("savedAt", now)
            .put("network", network.json())
        file.writeText(root.toString())
    }

    fun fetch(id: String, key: String): MetroNetwork? = try {
        val url = "https://wusper.com/api/metro/network?id=${enc(id)}&key=${enc(key)}"
        val root = JSONObject(get(url))
        if (!root.optBoolean("ok")) null else parseNetwork(root)?.takeIf { it.lines.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    private fun file(context: Context, id: String, key: String): File {
        val safe = "${id}_${key}".replace(Regex("[^A-Za-z0-9_-]"), "")
        return File(File(context.filesDir, "metro"), "$safe.json")
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", UA)
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

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}

fun parseCities(text: String): List<MetroCity> {
    val root = JSONObject(text)
    if (root.has("ok") && !root.optBoolean("ok")) return emptyList()
    val arr = root.optJSONArray("cities") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val id = row.optString("id")
            val key = row.optString("key")
            val name = row.optString("name")
            if (id.isBlank() || key.isBlank() || name.isBlank()) continue
            add(MetroCity(id, key, name))
        }
    }
}

fun parseNetwork(root: JSONObject): MetroNetwork? {
    if (root.has("ok") && !root.optBoolean("ok")) return null
    val lines = root.optJSONArray("lines") ?: return null
    val parsed = buildList {
        for (i in 0 until lines.length()) {
            val row = lines.optJSONObject(i) ?: continue
            val stations = row.optJSONArray("stations").stations()
            if (stations.size < 2) continue
            add(
                MetroLine(
                    id = row.optString("id").ifBlank { row.optString("name") },
                    name = row.optString("name"),
                    names = row.optJSONObject("names").names(row.optString("name")),
                    stations = stations,
                    path = row.optJSONArray("path").path(),
                    color = row.optString("color").ifBlank { "#5B7CFA" }.let { if (it.startsWith("#")) it else "#$it" },
                ),
            )
        }
    }
    if (parsed.isEmpty()) return null
    return MetroNetwork(
        id = root.optString("id"),
        key = root.optString("key"),
        city = root.optString("city"),
        lines = parsed,
    )
}

private fun JSONArray?.stations(): List<MetroStation> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val name = row.optString("name")
            if (name.isBlank()) continue
            val pos = row.optJSONArray("pos")
            val x = pos?.optDouble(0, Double.NaN)
            val y = pos?.optDouble(1, Double.NaN)
            add(
                MetroStation(
                    name = name,
                    names = row.optJSONObject("names").names(name),
                    transfer = row.optBoolean("transfer"),
                    x = x?.takeIf { !it.isNaN() },
                    y = y?.takeIf { !it.isNaN() },
                ),
            )
        }
    }
}

private fun JSONArray?.path(): List<Pair<Double, Double>> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val pt = arr.optJSONArray(i) ?: continue
            val x = pt.optDouble(0, Double.NaN)
            val y = pt.optDouble(1, Double.NaN)
            if (!x.isNaN() && !y.isNaN()) add(x to y)
        }
    }
}

private fun JSONObject?.names(fallback: String): MetroNames {
    val obj = this
    val zh = obj?.optString("zh").orEmpty().ifBlank { fallback }
    val hant = obj?.optString("hant").orEmpty().ifBlank { zh }
    val en = obj?.optString("en").orEmpty().ifBlank { zh }
    return MetroNames(zh, hant, en)
}

private fun MetroNetwork.json(): JSONObject = JSONObject()
    .put("ok", true)
    .put("id", id)
    .put("key", key)
    .put("city", city)
    .put(
        "lines",
        JSONArray().apply {
            lines.forEach { line ->
                put(
                    JSONObject()
                        .put("id", line.id)
                        .put("name", line.name)
                        .put("color", line.color)
                        .put("names", line.names.json())
                        .put(
                            "stations",
                            JSONArray().apply {
                                line.stations.forEach { station ->
                                    val row = JSONObject()
                                        .put("name", station.name)
                                        .put("names", station.names.json())
                                        .put("transfer", station.transfer)
                                    if (station.x != null && station.y != null) {
                                        row.put("pos", JSONArray().put(station.x).put(station.y))
                                    }
                                    put(row)
                                }
                            },
                        )
                        .put(
                            "path",
                            JSONArray().apply {
                                line.path.forEach { (x, y) -> put(JSONArray().put(x).put(y)) }
                            },
                        ),
                )
            }
        },
    )

private fun MetroNames.json(): JSONObject = JSONObject().put("zh", zh).put("hant", hant).put("en", en)
