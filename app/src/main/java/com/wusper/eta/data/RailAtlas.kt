package com.wusper.eta.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NamedStop(val id: String, val zh: String, val en: String)

data class RailLine(val code: String, val nameTc: String, val nameEn: String, val paths: List<List<String>>)

data class RailExit(val exit: String, val places: List<String>)

class RailAtlas(
    val lines: List<RailLine>,
    val heavy: Map<String, Double>,
    val ael: Map<String, Double>,
    val lrt: Map<String, Double>,
    val exits: Map<String, List<RailExit>>,
) {
    fun ridePrice(ride: RideLeg, heavyStops: List<NamedStop>, lightStops: List<NamedStop>): Double? {
        return when (rideFareKind(ride.kind, ride.name)) {
            RideFareKind.Bus -> ride.fare?.takeIf { !it.isNaN() && it > 0.0 }
            RideFareKind.Light -> {
                val from = matchStop(ride.board, lightStops) ?: return null
                val to = matchStop(ride.alight, lightStops) ?: return null
                fareBetween(lrt, from.id, to.id)
            }
            RideFareKind.Airport -> {
                val from = matchStop(ride.board, heavyStops) ?: return null
                val to = matchStop(ride.alight, heavyStops) ?: return null
                fareBetween(ael, from.id, to.id)
            }
            RideFareKind.Heavy -> {
                val from = matchStop(ride.board, heavyStops) ?: return null
                val to = matchStop(ride.alight, heavyStops) ?: return null
                if (from.id == "AIR" || from.id == "AWE" || to.id == "AIR" || to.id == "AWE") {
                    fareBetween(ael, from.id, to.id)
                } else {
                    fareBetween(heavy, from.id, to.id)
                }
            }
        }
    }

    companion object {
        fun load(context: Context): RailAtlas {
            val text = context.assets.open("rail_atlas.json").bufferedReader().use { it.readText() }
            val root = JSONObject(text)
            return RailAtlas(
                lines = root.optJSONArray("lines").toLines(),
                heavy = root.optJSONObject("heavy").toPrices(),
                ael = root.optJSONObject("ael").toPrices(),
                lrt = root.optJSONObject("lrt").toPrices(),
                exits = root.optJSONObject("exits").toExits(),
            )
        }
    }
}

fun matchStop(query: String, stops: List<NamedStop>): NamedStop? {
    val key = railStopKey(query)
    if (key.isEmpty()) return null
    return stops.firstOrNull { stop ->
        railStopKey(stop.zh).equals(key, ignoreCase = true) || railStopKey(stop.en).equals(key, ignoreCase = true)
    }
}

private fun JSONObject?.toPrices(): Map<String, Double> {
    val obj = this ?: return emptyMap()
    val out = HashMap<String, Double>(obj.length())
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = obj.optDouble(key, Double.NaN)
        if (!value.isNaN() && value > 0.0) out[key] = value
    }
    return out
}

private fun JSONArray?.toLines(): List<RailLine> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val paths = row.optJSONArray("paths")
            val built = buildList {
                if (paths == null) return@buildList
                for (p in 0 until paths.length()) {
                    val path = paths.optJSONArray(p) ?: continue
                    val ids = buildList {
                        for (s in 0 until path.length()) {
                            val id = path.optString(s)
                            if (id.isNotBlank()) add(id)
                        }
                    }
                    if (ids.size >= 2) add(ids)
                }
            }
            val code = row.optString("code")
            if (code.isBlank()) continue
            add(RailLine(code, row.optString("name_tc"), row.optString("name_en"), built))
        }
    }
}

private fun JSONObject?.toExits(): Map<String, List<RailExit>> {
    val obj = this ?: return emptyMap()
    val out = HashMap<String, List<RailExit>>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val arr = obj.optJSONArray(key) ?: continue
        val rows = buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val exit = row.optString("exit")
                if (exit.isBlank()) continue
                val places = buildList {
                    val raw = row.optJSONArray("places") ?: return@buildList
                    for (p in 0 until raw.length()) {
                        val name = raw.optString(p)
                        if (name.isNotBlank()) add(name)
                    }
                }
                add(RailExit(exit, places))
            }
        }
        if (rows.isNotEmpty()) out[key] = rows
    }
    return out
}
