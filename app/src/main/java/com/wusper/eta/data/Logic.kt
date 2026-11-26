package com.wusper.eta.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val HK: ZoneId = ZoneId.of("Asia/Hong_Kong")

fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lng2 - lng1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * r * asin(sqrt(a))
}

fun minutesUntil(iso: String?, now: Instant = Instant.now()): Int? {
    if (iso.isNullOrBlank()) return null
    val instant = parseInstant(iso) ?: return null
    val mins = (Duration.between(now, instant).seconds / 60.0).roundToInt()
    if (mins < -1) return null
    return mins.coerceAtLeast(0)
}

fun parseInstant(raw: String): Instant? {
    val iso = raw.trim().replace(' ', 'T')
    return try {
        OffsetDateTime.parse(iso).toInstant()
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(iso).atZone(HK).toInstant()
        } catch (_: Exception) {
            null
        }
    }
}

fun isScheduledRemark(remark: String?): Boolean {
    if (remark.isNullOrBlank()) return false
    val lower = remark.lowercase()
    return "預定" in remark ||
        "预定" in remark ||
        "原定" in remark ||
        "未開出" in remark ||
        "未开出" in remark ||
        "scheduled" in lower
}

fun routeRank(route: String): Int {
    val digits = route.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }
    return digits.toIntOrNull() ?: 9_999
}

fun formatMeters(meters: Int): String {
    if (meters < 1000) return "${meters}m"
    val whole = meters / 1000
    val tenth = (meters / 100) % 10
    return "$whole.${tenth}km"
}

fun chooseWidgetRoute(
    favorites: List<FavRoute>,
    usage: List<Pair<FavRoute, Int>>,
    last: FavRoute?,
    nearby: FavRoute?,
): FavRoute? {
    if (favorites.isNotEmpty()) {
        val counts = usage.associate { it.first.id to it.second }
        return favorites.withIndex().maxWithOrNull(
            compareBy({ counts[it.value.id] ?: 0 }, { -it.index }),
        )?.value
    }
    val frequent = usage.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    if (frequent != null) return frequent
    return last ?: nearby
}

fun chooseSecondWidgetRoute(
    favorites: List<FavRoute>,
    usage: List<Pair<FavRoute, Int>>,
    last: FavRoute?,
    nearby: FavRoute?,
    exceptId: String,
): FavRoute? = chooseWidgetRoute(
    favorites.filter { it.id != exceptId },
    usage.filter { it.first.id != exceptId },
    last?.takeIf { it.id != exceptId },
    nearby?.takeIf { it.id != exceptId },
)

fun matchesStopName(query: String, nameZh: String, nameEn: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return false
    return nameZh.contains(q, ignoreCase = true) || nameEn.contains(q, ignoreCase = true)
}

fun stationHits(query: String, stops: List<Stop>, mtr: List<MtrStop>, geo: (String) -> Pair<Double, Double>?): List<NavHit> {
    val ranked = ArrayList<Triple<Int, Int, NavHit>>()
    val seen = HashSet<String>()
    var index = 0
    for (stop in stops) {
        if (!matchesStopName(query, stop.name, stop.nameEn)) continue
        val key = "${stop.name.lowercase()}|${stop.nameEn.lowercase()}|${"%.4f".format(stop.lat)}|${"%.4f".format(stop.lng)}"
        if (!seen.add(key)) continue
        val hit = NavHit(stop.name, stop.nameEn, stop.lat, stop.lng)
        ranked.add(Triple(nameRank(query, stop.name, stop.nameEn), index, hit))
        index += 1
    }
    for (station in mtr) {
        if (!matchesStopName(query, station.name, station.nameEn)) continue
        if (!seen.add("mtr|${station.sta}")) continue
        val placed = geo(station.sta)
        val hit = NavHit(
            station.name,
            station.nameEn,
            placed?.first ?: station.lat,
            placed?.second ?: station.lng,
        )
        ranked.add(Triple(nameRank(query, station.name, station.nameEn), index, hit))
        index += 1
    }
    return ranked.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }.take(16)
}

private fun nameRank(query: String, nameZh: String, nameEn: String): Int {
    val q = query.trim()
    if (nameZh.equals(q, ignoreCase = true) || nameEn.equals(q, ignoreCase = true)) return 0
    if (nameZh.startsWith(q, ignoreCase = true) || nameEn.startsWith(q, ignoreCase = true)) return 1
    return 2
}

fun minuteSpan(minSeconds: Double, maxSeconds: Double): Pair<Int, Int> {
    val min = roundedMinutes(minSeconds)
    val max = roundedMinutes(maxSeconds).coerceAtLeast(min)
    return min to max
}

fun roundedMinutes(seconds: Double): Int {
    if (seconds <= 0.0) return 0
    return (seconds / 60.0).roundToInt().coerceAtLeast(1)
}

fun vehicleKind(route: String, instruction: String, vehicle: String, co: String): String {
    val kind = vehicle.trim().uppercase()
    val company = co.trim().uppercase()
    val text = "$route $instruction"
    if (company == "GMB" || kind.contains("MINIBUS") || text.contains("小巴")) return "gmb"
    if (
        company == "MTR" ||
        kind in setOf("SUBWAY", "RAIL", "METRO_RAIL", "HEAVY_RAIL", "COMMUTER_TRAIN", "HIGH_SPEED_TRAIN", "MONORAIL", "TRAM") ||
        text.contains("港鐵") ||
        text.contains("地下鐵") ||
        route.contains("綫") ||
        route.contains("線")
    ) {
        return "rail"
    }
    return "bus"
}

fun oppositeDir(dir: String): String? = when (dir.trim().uppercase()) {
    "I", "INBOUND" -> "O"
    "O", "OUTBOUND" -> "I"
    "1" -> "2"
    "2" -> "1"
    else -> null
}

fun citybusOppositeDest(currentDir: String, currentDest: String, orig: String, dest: String): String {
    if (currentDest.isNotBlank() && currentDest == dest && orig.isNotBlank()) return orig
    if (currentDest.isNotBlank() && currentDest == orig && dest.isNotBlank()) return dest
    return if (currentDir == "I") dest else orig
}

fun otherBound(bounds: List<BoundHit>, route: Route): BoundHit? {
    return bounds.asSequence()
        .filter { it.co == route.co && it.route == route.route && it.serviceType == route.serviceType }
        .filter { it.dir.isNotBlank() && it.dir != route.dir }
        .filter { route.routeId.isBlank() || it.routeId.isBlank() || it.routeId == route.routeId }
        .filter { it.dest.isNotBlank() && it.stopId.isNotBlank() }
        .minByOrNull { it.meters }
}

fun atDestinationTerminus(stopName: String, dest: String): Boolean {
    val stop = stopName.replace(" ", "")
    val bound = dest.replace(" ", "")
    if (stop.length < 2 || bound.length < 2) return false
    if (stop == bound || stop.startsWith(bound) || bound.startsWith(stop)) return true
    val stopHead = headName(stop)
    val destHead = headName(bound)
    if (stopHead.length >= 2 && stopHead == destHead) return true
    val inner = innerName(bound)
    return inner.length >= 2 && (stop.contains(inner) || stopHead.contains(inner))
}

private fun headName(value: String): String =
    value.substringBefore('(').substringBefore('（').substringBefore(',').substringBefore('，')

private fun innerName(value: String): String {
    val open = value.indexOfAny(charArrayOf('(', '（'))
    val close = value.indexOfAny(charArrayOf(')', '）'))
    if (open < 0 || close <= open) return ""
    return value.substring(open + 1, close)
}

fun routeSearchRank(query: String, route: String, dest: String): Int? {
    val q = query.trim()
    if (q.isEmpty()) return null
    if (route.equals(q, ignoreCase = true)) return 0
    if (route.startsWith(q, ignoreCase = true)) return 1
    if (route.contains(q, ignoreCase = true)) return 2
    if (dest.contains(q, ignoreCase = true)) return 3
    return null
}

fun dedupeRouteHits(routes: List<Route>): List<Route> {
    val seen = LinkedHashSet<String>()
    val out = ArrayList<Route>()
    for (route in routes) {
        val key = "${route.co}|${route.route}|${route.dir}|${route.dest}"
        if (seen.add(key)) out.add(route)
    }
    return out
}

fun usefulMtrSide(lineCode: String, sta: String, fallback: String): String {
    val order = mtrLineOrder(lineCode)
    val at = order.indexOf(sta)
    if (at < 0) return fallback
    val northernEnd = when (lineCode) {
        "EAL" -> sta == "LOW" || sta == "LMC"
        "TKL" -> sta == "POA" || sta == "LHP"
        else -> false
    }
    return when {
        at == 0 -> "up"
        northernEnd || at == order.lastIndex -> "down"
        else -> fallback
    }
}

fun preferredMtrSide(up: List<MtrTrain>, down: List<MtrTrain>): String {
    val upMin = up.mapNotNull { it.minutes }.minOrNull()
    val downMin = down.mapNotNull { it.minutes }.minOrNull()
    return when {
        upMin == null && downMin != null -> "down"
        downMin != null && upMin != null && downMin < upMin -> "down"
        else -> "up"
    }
}

fun nearestAlong(stops: List<AlongStop>, lat: Double, lng: Double): AlongStop? {
    if (stops.isEmpty()) return null
    return stops.minBy { stop ->
        if (stop.lat != 0.0 && stop.lng != 0.0 && lat != 0.0 && lng != 0.0) {
            haversineMeters(lat, lng, stop.lat, stop.lng)
        } else {
            stop.seq.toDouble() * 1_000_000
        }
    }
}

fun matchesRouteQuery(query: String, route: String, dest: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return false
    return route.contains(q, ignoreCase = true) || dest.contains(q, ignoreCase = true)
}

fun coLabel(co: String): String = when (co) {
    "KMB" -> "九巴"
    "CTB" -> "城巴"
    "NWFB" -> "城巴"
    "GMB" -> "專小"
    "MTR" -> "港鐵"
    else -> co
}

fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    if (encoded.isEmpty()) return emptyList()
    val out = ArrayList<Pair<Double, Double>>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        val dLat = decodeChunk(encoded, index) ?: return out
        index = dLat.second
        lat += dLat.first
        val dLng = decodeChunk(encoded, index) ?: return out
        index = dLng.second
        lng += dLng.first
        out.add(lat / 1e5 to lng / 1e5)
    }
    return out
}

private fun decodeChunk(encoded: String, start: Int): Pair<Int, Int>? {
    var index = start
    var shift = 0
    var result = 0
    while (index < encoded.length) {
        val b = encoded[index++].code - 63
        result = result or ((b and 0x1f) shl shift)
        shift += 5
        if (b < 0x20) {
            val value = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            return value to index
        }
    }
    return null
}

fun parseStopSeq(raw: String?, fallback: Int): Int {
    val n = raw?.trim()?.toIntOrNull()
    return if (n == null || n <= 0) fallback else n
}

fun <T> List<T>.inStopOrder(seq: (T) -> Int): List<T> =
    withIndex()
        .sortedWith(compareBy({ seq(it.value) }, { it.index }))
        .map { it.value }

private val exitOnly = Regex("""(?i)^(出口|exit)\s*[A-Za-z]{0,2}\d?$|^[A-Za-z]{1,2}\d?\s*(出口|exit)?$""")

fun exitLabel(raw: String): Boolean = exitOnly.matches(raw.trim())

fun fallbackPlanTitle(transit: Boolean, route: String, towards: String): String {
    val line = route.trim()
    val dest = towards.trim()
    if (!transit) return "步行"
    if (line.isNotBlank() && dest.isNotBlank() && !exitLabel(dest)) return "$line 往 $dest"
    if (line.isNotBlank()) return line
    if (dest.isNotBlank() && !exitLabel(dest)) return dest
    return "乘車"
}

fun planCopy(lines: List<String>, transit: Boolean, route: String, towards: String): Pair<String, String> {
    val spoken = lines.map { it.trim() }.filter { it.isNotBlank() }
    if (spoken.isEmpty()) return fallbackPlanTitle(transit, route, towards) to ""
    val body = spoken.joinToString("\n")
    val extra = buildList {
        val line = route.trim()
        val dest = towards.trim()
        if (transit && line.isNotBlank() && !body.contains(line)) add(line)
        if (dest.isNotBlank() && !exitLabel(dest) && !body.contains(dest)) add("往 $dest")
    }
    return spoken.first() to (spoken.drop(1) + extra).joinToString("\n")
}

fun railStopKey(name: String): String {
    var text = name.trim()
    if (text.endsWith(" Station", ignoreCase = true)) text = text.dropLast(" Station".length).trim()
    if (text.endsWith("站") && text.length > 1) text = text.dropLast(1)
    return text.replace(" ", "")
}

fun fareBetween(table: Map<String, Double>, from: String, to: String): Double? {
    if (from.isBlank() || to.isBlank() || from == to) return null
    val price = table["$from|$to"] ?: return null
    if (price.isNaN() || price <= 0.0) return null
    return price
}

enum class RideFareKind { Bus, Heavy, Airport, Light }

fun rideFareKind(kind: String, name: String): RideFareKind {
    if (kind != "rail") return RideFareKind.Bus
    val text = name.replace("綫", "線").replace("线", "線")
    if (text.contains("輕鐵") || text.contains("Light Rail", ignoreCase = true)) return RideFareKind.Light
    if (Regex("""^\d{3}[A-Za-z]?$""").matches(text.trim())) return RideFareKind.Light
    if (text.contains("機場快") || text.contains("Airport Express", ignoreCase = true)) return RideFareKind.Airport
    return RideFareKind.Heavy
}

fun tripFare(prices: List<Double?>): Double? {
    if (prices.isEmpty()) return null
    var sum = 0.0
    for (price in prices) {
        if (price == null || price.isNaN() || price <= 0.0) return null
        sum += price
    }
    return sum
}

fun boardingFare(price: Double?): String? {
    if (price == null || price.isNaN() || price <= 0.0) return null
    val cents = kotlin.math.round(price * 100.0).toInt()
    if (cents <= 0) return null
    val whole = cents / 100
    val frac = cents % 100
    val body = when {
        frac == 0 -> whole.toString()
        frac % 10 == 0 -> "$whole.${frac / 10}"
        else -> "$whole.${frac.toString().padStart(2, '0')}"
    }
    return "$$body"
}

fun nearestStop(stops: List<DetailStop>, lat: Double, lng: Double): DetailStop? {
    val placed = stops.filter { it.lat != 0.0 && it.lng != 0.0 }
    if (placed.isEmpty()) return null
    if (lat == 0.0 && lng == 0.0) {
        return placed.minWith(compareBy({ if (it.seq > 0) it.seq else Int.MAX_VALUE }, { it.id }))
    }
    return placed.minBy { haversineMeters(lat, lng, it.lat, it.lng) }
}

fun walkMinutes(meters: Double): Int {
    val mins = (meters / 1.3 / 60.0).roundToInt()
    return mins.coerceAtLeast(1)
}
