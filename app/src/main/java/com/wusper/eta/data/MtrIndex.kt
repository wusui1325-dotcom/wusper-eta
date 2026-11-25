package com.wusper.eta.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MtrIndex(val stations: List<Station>) {
    private val bySta = stations.associateBy { it.sta }

    fun name(sta: String): String = bySta[sta]?.name ?: sta

    fun onLine(code: String): List<Station> {
        val have = stations.filter { code in it.lines }
        val rank = mtrLineOrder(code).withIndex().associate { it.value to it.index }
        return have.sortedWith(compareBy({ rank[it.sta] ?: 10_000 }, { it.name }))
    }

    fun near(lat: Double, lng: Double, radius: Int = 700): List<MtrStop> =
        stations.map { station ->
            val meters = haversineMeters(lat, lng, station.lat, station.lng).toInt()
            station to meters
        }.filter { it.second <= radius }
            .sortedBy { it.second }
            .take(4)
            .map { (station, meters) ->
                MtrStop(
                    sta = station.sta,
                    name = station.name,
                    meters = meters,
                    lat = station.lat,
                    lng = station.lng,
                    lines = station.lines.zip(station.lineNames).map { (code, label) ->
                        MtrLine(code, label, emptyList(), emptyList())
                    },
                )
            }

    data class Station(
        val sta: String,
        val name: String,
        val lines: List<String>,
        val lineNames: List<String>,
        val lat: Double,
        val lng: Double,
    )

    companion object {
        fun load(context: Context): MtrIndex {
            val text = context.assets.open("mtr_stations.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val stations = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val sta = o.optString("sta")
                    if (sta.isBlank()) continue
                    add(
                        Station(
                            sta = sta,
                            name = o.optString("name_tc").ifBlank { sta },
                            lines = o.optJSONArray("lines").toStrings(),
                            lineNames = o.optJSONArray("line_names_tc").toStrings(),
                            lat = o.optDouble("lat", 0.0),
                            lng = o.optDouble("lng", 0.0),
                        ),
                    )
                }
            }
            return MtrIndex(stations)
        }
    }
}

fun mtrLineOrder(code: String): List<String> = when (code) {
    "ISL" -> listOf("KET", "HKU", "SYP", "SHW", "CEN", "ADM", "WAC", "CAB", "TIH", "FOH", "NOP", "QUB", "TAK", "SWH", "SKW", "HFC", "CHW")
    "TWL" -> listOf("CEN", "ADM", "TST", "JOR", "YMT", "MOK", "PRE", "SSP", "CSW", "LCK", "MEF", "LAK", "KWF", "KWH", "TWH", "TSW")
    "KTL" -> listOf("WHA", "HOM", "YMT", "MOK", "PRE", "SKM", "KOT", "LOF", "WTS", "DIH", "CHH", "KOB", "NTK", "KWT", "LAT", "YAT", "TIK")
    "TKL" -> listOf("NOP", "QUB", "YAT", "TIK", "TKO", "HAH", "POA", "LHP")
    "EAL" -> listOf("ADM", "EXC", "HUH", "MKK", "KOT", "TAW", "SHT", "FOT", "UNI", "TAP", "TWO", "FAN", "SHS", "LOW", "LMC")
    "TML" -> listOf("TUM", "SIH", "TIS", "YUL", "LOP", "KSR", "TWW", "MEF", "NAC", "AUS", "ETS", "HUH", "HOM", "TKW", "SUW", "KAT", "DIH", "HIK", "TAW", "CKT", "STW", "CIO", "SHM", "TSH", "HEO", "MOS", "WKS")
    "TCL" -> listOf("HOK", "KOW", "OLY", "NAC", "LAK", "TSY", "SUN", "TUC")
    "AEL" -> listOf("HOK", "KOW", "TSY", "AIR", "AWE")
    "SIL" -> listOf("ADM", "OCP", "WCH", "LET", "SOH")
    "DRL" -> listOf("SUN", "DIS")
    else -> emptyList()
}

private fun JSONArray?.toStrings(): List<String> {
    val arr = this ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.isNotBlank()) add(s)
        }
    }
}
