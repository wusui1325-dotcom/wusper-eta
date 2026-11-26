package com.wusper.eta.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object RouteMemory {
    private const val FILE = "route-memory.json"
    private const val MAX_BYTES = 200_000L

    fun read(context: Context): List<Route> {
        val file = File(context.filesDir, FILE)
        if (!file.isFile || file.length() !in 2..MAX_BYTES) return emptyList()
        return try {
            decode(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun write(context: Context, routes: List<Route>) {
        val dir = context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, "$FILE.tmp")
        tmp.writeText(encode(routes))
        val dest = File(dir, FILE)
        if (!tmp.renameTo(dest)) {
            dest.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun encode(routes: List<Route>): String {
        val arr = JSONArray()
        for (route in routes) {
            if (route.route.isBlank()) continue
            arr.put(
                JSONObject()
                    .put("co", route.co)
                    .put("route", route.route)
                    .put("dir", route.dir)
                    .put("st", route.serviceType)
                    .put("dest", route.dest)
                    .put("rid", route.routeId)
                    .put("region", route.region),
            )
        }
        return arr.toString()
    }

    fun decode(text: String): List<Route> {
        val arr = JSONArray(text)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("route")
                if (code.isBlank()) continue
                add(
                    Route(
                        co = o.optString("co"),
                        route = code,
                        dir = o.optString("dir"),
                        serviceType = o.optString("st").ifBlank { "1" },
                        dest = o.optString("dest"),
                        stopId = "",
                        routeId = o.optString("rid"),
                        region = o.optString("region"),
                        state = EtaState.Loading,
                        etas = emptyList(),
                    ),
                )
            }
        }
    }
}
