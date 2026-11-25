package com.wusper.eta.data

import com.wusper.eta.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.json.JSONObject

object Planner {
    fun plan(origin: Place, dest: Place, mode: String): TripPlan {
        val key = BuildConfig.ROUTES_API_KEY
        if (key.isBlank()) return mock(origin, dest)
        val body = JSONObject().apply {
            put("origin", point(origin))
            put("destination", point(dest))
            put("travelMode", if (mode == "walk") "WALK" else "TRANSIT")
            put("languageCode", "zh-HK")
            put("units", "METRIC")
            put("regionCode", "HK")
            if (mode != "walk") {
                put("departureTime", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(30).toString())
            }
        }
        val conn = (URL("https://routes.googleapis.com/directions/v2:computeRoutes").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 6000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", key)
            setRequestProperty("X-Goog-FieldMask", FIELD_MASK)
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code")
            return parse(text) ?: error("empty routes")
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String): TripPlan? {
        val route = JSONObject(text).optJSONArray("routes")?.optJSONObject(0) ?: return null
        val meters = route.optInt("distanceMeters", 0)
        val minutes = seconds(route.optString("duration"))
        val poly = route.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()
        val steps = ArrayList<PlanStep>()
        val legs = route.optJSONArray("legs")
        if (legs != null) {
            for (i in 0 until legs.length()) {
                val legSteps = legs.optJSONObject(i)?.optJSONArray("steps") ?: continue
                for (j in 0 until legSteps.length()) {
                    val step = legSteps.optJSONObject(j) ?: continue
                    val mode = step.optString("travelMode")
                    val stepMin = seconds(step.optString("staticDuration").ifBlank { step.optString("duration") })
                    val transit = step.optJSONObject("transitDetails")
                    if (transit != null) {
                        val line = transit.optJSONObject("transitLine")
                        val short = line?.optString("nameShort").orEmpty().ifBlank { line?.optString("name").orEmpty() }
                        val head = transit.optString("headsign")
                        val from = transit.optJSONObject("stopDetails")?.optJSONObject("departureStop")?.optString("name").orEmpty()
                        val to = transit.optJSONObject("stopDetails")?.optJSONObject("arrivalStop")?.optString("name").orEmpty()
                        val detail = listOf(from, to).filter { it.isNotBlank() }.joinToString(" → ")
                        steps.add(PlanStep("transit", listOf(short, head).filter { it.isNotBlank() }.joinToString(" "), detail, stepMin))
                    } else {
                        val instruction = step.optJSONObject("navigationInstruction")?.optString("instructions").orEmpty()
                        steps.add(PlanStep("walk", instruction.ifBlank { "步行" }, "", stepMin))
                    }
                }
            }
        }
        return TripPlan(false, minutes, meters, steps, poly)
    }

    private fun seconds(raw: String): Int {
        val n = raw.removeSuffix("s").toDoubleOrNull() ?: return 0
        return (n / 60.0).toInt().coerceAtLeast(0)
    }

    private fun point(place: Place): JSONObject = JSONObject().put(
        "location",
        JSONObject().put("latLng", JSONObject().put("latitude", place.lat).put("longitude", place.lng)),
    )

    private fun mock(origin: Place, dest: Place): TripPlan {
        val meters = haversineMeters(origin.lat, origin.lng, dest.lat, dest.lng).toInt()
        val minutes = walkMinutes(meters.toDouble())
        return TripPlan(
            mock = true,
            minutes = minutes,
            meters = meters,
            steps = listOf(PlanStep("walk", "直線", "", minutes)),
            poly = "",
        )
    }

    private const val FIELD_MASK =
        "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline," +
            "routes.legs.steps.staticDuration,routes.legs.steps.travelMode," +
            "routes.legs.steps.navigationInstruction.instructions," +
            "routes.legs.steps.transitDetails.headsign," +
            "routes.legs.steps.transitDetails.transitLine.name," +
            "routes.legs.steps.transitDetails.transitLine.nameShort," +
            "routes.legs.steps.transitDetails.stopDetails.departureStop.name," +
            "routes.legs.steps.transitDetails.stopDetails.arrivalStop.name"
}
