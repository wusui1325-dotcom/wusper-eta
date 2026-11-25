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

fun matchesStopName(query: String, nameZh: String, nameEn: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return false
    return nameZh.contains(q, ignoreCase = true) || nameEn.contains(q, ignoreCase = true)
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
    if (stop == bound || stop.startsWith(bound)) return true
    val stopHead = stop.substringBefore('(').substringBefore('（')
    val destHead = bound.substringBefore('(').substringBefore('（')
    return stopHead.length >= 2 && stopHead == destHead
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

fun walkMinutes(meters: Double): Int {
    val mins = (meters / 1.3 / 60.0).roundToInt()
    return mins.coerceAtLeast(1)
}
