package com.wusper.eta.data

enum class Tab { Saved, Bus, Mtr, Search, Nav }

enum class SearchMode { Route, StopName }

enum class Filter { All, Kmb, Ctb, Gmb, Mtr }

enum class EtaState { Loading, Ready, Failed }

enum class Help {
    Nearby, Gmb, Scheduled, None, MtrSide, Service, Minutes, Mock, Schematic, MtrGeo, A11y,
}

data class EtaSlot(val minutes: Int?, val scheduled: Boolean)

data class BoundHit(
    val co: String,
    val route: String,
    val dir: String,
    val serviceType: String,
    val dest: String,
    val stopId: String,
    val stopName: String,
    val routeId: String,
    val region: String,
    val meters: Int,
    val lat: Double,
    val lng: Double,
) {
    fun asRoute(): Route = Route(
        co = co,
        route = route,
        dir = dir,
        serviceType = serviceType,
        dest = dest,
        stopId = stopId,
        routeId = routeId,
        region = region,
        state = EtaState.Loading,
        etas = emptyList(),
    )
}

data class NearbyPage(val stops: List<Stop>, val bounds: List<BoundHit>)

data class Route(
    val co: String,
    val route: String,
    val dir: String,
    val serviceType: String,
    val dest: String,
    val stopId: String,
    val routeId: String,
    val region: String,
    val state: EtaState,
    val etas: List<EtaSlot>,
) {
    val key: String get() = "$co|$route|$dir|$serviceType|$routeId|$stopId"
}

data class Stop(
    val key: String,
    val co: String,
    val id: String,
    val name: String,
    val meters: Int,
    val lat: Double,
    val lng: Double,
    val routes: List<Route>,
    val nameEn: String = "",
)

data class MtrTrain(val minutes: Int?, val dest: String, val scheduled: Boolean, val platform: String)

data class MtrLine(
    val code: String,
    val name: String,
    val up: List<MtrTrain>,
    val down: List<MtrTrain>,
)

data class TrainBoard(val up: List<MtrTrain>, val down: List<MtrTrain>)

data class MtrStop(
    val sta: String,
    val name: String,
    val meters: Int,
    val lat: Double,
    val lng: Double,
    val lines: List<MtrLine>,
    val nameEn: String = "",
)

data class FavRoute(
    val id: String,
    val title: String,
    val dest: String,
    val stopName: String,
    val co: String = "",
    val route: String = "",
    val dir: String = "",
    val serviceType: String = "1",
    val routeId: String = "",
    val region: String = "",
    val stopId: String = "",
    val line: String = "",
    val side: String = "",
) {
    fun asRoute(): Route = Route(co, route, dir, serviceType, dest, stopId, routeId, region, EtaState.Loading, emptyList())
}

data class SavedStop(
    val key: String,
    val co: String,
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val label: String,
)

data class Place(val name: String, val lat: Double, val lng: Double)

data class NavHit(val name: String, val nameEn: String, val lat: Double, val lng: Double)

data class PlanStep(val mode: String, val title: String, val detail: String, val minutes: Int)

data class RideLeg(
    val kind: String,
    val name: String,
    val board: String,
    val alight: String,
    val minutes: Int,
    val minutesMax: Int = minutes,
)

data class TripPlan(
    val mock: Boolean,
    val minutes: Int,
    val meters: Int,
    val steps: List<PlanStep>,
    val poly: String,
    val minutesMax: Int = minutes,
    val rides: List<RideLeg> = emptyList(),
    val walkMinutes: Int = 0,
)

sealed class RouteFocus {
    abstract val favId: String

    data class Bus(val route: Route) : RouteFocus() {
        override val favId: String = "bus|${route.co}|${route.route}|${route.dir}|${route.serviceType}|${route.routeId}"
    }

    data class Mtr(val code: String, val name: String, val side: String) : RouteFocus() {
        override val favId: String = "mtr|$code|$side"
    }
}

data class DetailStop(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val seq: Int,
)

data class AlongStop(
    val co: String,
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val seq: Int,
)

data class SearchBundle(
    val stops: List<Stop>,
    val routes: List<Route>,
    val mtr: List<MtrStop>,
)

data class Snapshot(
    val at: Long,
    val tab: Tab,
    val lat: Double,
    val lng: Double,
    val radius: Int,
    val openKey: String,
    val stops: List<Stop>,
    val mtr: List<MtrStop>,
    val fav: List<SavedStop>,
    val recent: List<SavedStop>,
    val origin: Place?,
    val dest: Place?,
    val planMode: String,
    val plan: TripPlan?,
) {
    fun cleared(): Snapshot = copy(
        at = 0,
        stops = stops.map { stop ->
            stop.copy(routes = stop.routes.map { it.copy(state = EtaState.Loading, etas = emptyList()) })
        },
        mtr = mtr.map { station ->
            station.copy(lines = station.lines.map { it.copy(up = emptyList(), down = emptyList()) })
        },
    )
}

fun Stop.toSaved(): SavedStop = SavedStop(
    key = key,
    co = co,
    id = id,
    name = name,
    lat = lat,
    lng = lng,
    label = routes.map { it.route }.distinct().take(4).joinToString("  "),
)

fun Filter.accepts(co: String): Boolean = when (this) {
    Filter.All -> true
    Filter.Kmb -> co == "KMB"
    Filter.Ctb -> co == "CTB" || co == "NWFB"
    Filter.Gmb -> co == "GMB"
    Filter.Mtr -> false
}
