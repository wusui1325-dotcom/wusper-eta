package com.wusper.eta.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.wusper.eta.MainActivity
import com.wusper.eta.R
import com.wusper.eta.data.DetailStop
import com.wusper.eta.data.FavRoute
import com.wusper.eta.data.MtrIndex
import com.wusper.eta.data.RouteUsage
import com.wusper.eta.data.SnapshotStore
import com.wusper.eta.data.TransitApi
import com.wusper.eta.data.UiPrefs
import com.wusper.eta.data.WidgetChoice
import com.wusper.eta.data.WidgetFace
import com.wusper.eta.data.WidgetPrefs
import com.wusper.eta.data.chooseWidgetRoute
import com.wusper.eta.data.nearestStop
import kotlinx.coroutines.runBlocking

object EtaWidgets {
    const val EXTRA = "wusper_widget"

    private val sizes = listOf(
        Triple(EtaWidgetTiny::class.java, "1x1", R.layout.widget_1x1),
        Triple(EtaWidgetTall::class.java, "1x2", R.layout.widget_1x2),
        Triple(EtaWidgetSquare::class.java, "2x2", R.layout.widget_2x2),
    )

    fun pin(activity: Activity, size: String) {
        val manager = AppWidgetManager.getInstance(activity)
        if (Build.VERSION.SDK_INT < 26 || !manager.isRequestPinAppWidgetSupported) return
        val provider = ComponentName(activity, provider(size))
        manager.requestPinAppWidget(provider, null, null)
    }

    fun pinSupported(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported
    }

    fun placed(context: Context): List<PlacedWidget> {
        val manager = AppWidgetManager.getInstance(context)
        return sizes.flatMap { (cls, size, _) ->
            manager.getAppWidgetIds(ComponentName(context, cls)).map { PlacedWidget(it, size, cls) }
        }
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        placed(context).forEach { item ->
            push(context, manager, item.id, item.size, item.cls, WidgetPrefs.face(context, item.id))
            refreshOne(context, item.id, item.size, item.cls)
        }
    }

    fun pushCached(context: Context, manager: AppWidgetManager, id: Int, size: String, cls: Class<*>) {
        push(context, manager, id, size, cls, WidgetPrefs.face(context, id))
    }

    fun refreshOne(context: Context, id: Int, size: String, cls: Class<*>) {
        val previous = WidgetPrefs.face(context, id)
        val face = try {
            runBlocking { resolve(context, id, previous) }
        } catch (_: Exception) {
            previous.copy(settled = false, minutes = if (previous.settled) previous.minutes else "")
        }
        WidgetPrefs.saveFace(context, id, face)
        val manager = AppWidgetManager.getInstance(context)
        push(context, manager, id, size, cls, face)
    }

    private suspend fun resolve(context: Context, id: Int, previous: WidgetFace): WidgetFace {
        val choice = WidgetPrefs.choice(context, id)
        val route = if (choice.manual && choice.route != null) {
            choice.route
        } else {
            chooseWidgetRoute(
                UiPrefs.savedRoutes(context),
                RouteUsage.read(context),
                RouteUsage.last(context),
                nearbyRoute(context),
            )
        } ?: return WidgetFace()
        val here = lastFix(context)
        val lat = here?.first ?: 0.0
        val lng = here?.second ?: 0.0
        val api = TransitApi()
        val localized = UiPrefs.wrap(context)
        val none = localized.getString(R.string.none)
        return if (route.line.isNotBlank()) {
            val index = MtrIndex.load(context)
            val stations = index.onLine(route.line).mapIndexed { i, station ->
                DetailStop(station.sta, station.name, station.lat, station.lng, i + 1)
            }
            val stop = if (choice.stopId.isNotBlank()) {
                stations.firstOrNull { it.id == choice.stopId }
            } else {
                nearestStop(stations, lat, lng)
            }
            if (stop == null) {
                return face(route, choice.stopName, previous, settled = false, minutes = "")
            }
            val board = api.mtrReady(route.line, stop.id, index::name)
                ?: return face(route, stop.name, previous, settled = false, minutes = if (previous.settled) previous.minutes else "")
            val trains = if (route.side == "down") board.second else board.first
            val minute = trains.firstOrNull { it.minutes != null }?.minutes
            face(route, stop.name, previous, settled = true, minutes = if (minute == null) none else "$minute′")
        } else {
            val stops = try {
                api.routeStops(route.asRoute()).map { DetailStop(it.id, it.name, it.lat, it.lng, it.seq) }
            } catch (_: Exception) {
                emptyList()
            }
            val stop = if (choice.stopId.isNotBlank()) {
                stops.firstOrNull { it.id == choice.stopId } ?: DetailStop(choice.stopId, choice.stopName, 0.0, 0.0, 0)
            } else {
                nearestStop(stops, lat, lng)
            }
            if (stop == null || stop.id.isBlank()) {
                return face(route, choice.stopName, previous, settled = false, minutes = "")
            }
            val slots = try {
                api.live(route.asRoute().copy(stopId = stop.id))
            } catch (_: Exception) {
                null
            }
            if (slots == null) {
                face(route, stop.name, previous, settled = false, minutes = if (previous.settled) previous.minutes else "")
            } else {
                val minute = slots.firstOrNull { it.minutes != null }?.minutes
                face(route, stop.name, previous, settled = true, minutes = if (minute == null) none else "$minute′")
            }
        }
    }

    private fun face(route: FavRoute, stop: String, previous: WidgetFace, settled: Boolean, minutes: String): WidgetFace {
        val bound = route.dest
        return WidgetFace(
            route = route.title.ifBlank { route.route.ifBlank { route.line } },
            bound = bound,
            stop = stop.ifBlank { previous.stop },
            minutes = minutes,
            settled = settled,
        )
    }

    private fun nearbyRoute(context: Context): FavRoute? {
        val snap = SnapshotStore.read(context)
        val stop = snap.stops.firstOrNull { it.routes.isNotEmpty() }
        if (stop != null) {
            val route = stop.routes.first()
            return FavRoute(
                id = "bus|${route.co}|${route.route}|${route.dir}|${route.serviceType}|${route.routeId}",
                title = route.route,
                dest = route.dest,
                stopName = stop.name,
                co = route.co,
                route = route.route,
                dir = route.dir,
                serviceType = route.serviceType,
                routeId = route.routeId,
                region = route.region,
                stopId = route.stopId.ifBlank { stop.id },
            )
        }
        val station = snap.mtr.firstOrNull { it.lines.isNotEmpty() } ?: return null
        val line = station.lines.first()
        return FavRoute(
            id = "mtr|${line.code}|up",
            title = line.name.ifBlank { line.code },
            dest = "",
            stopName = station.name,
            stopId = station.sta,
            line = line.code,
            side = "up",
        )
    }

    @SuppressLint("MissingPermission")
    private fun lastFix(context: Context): Pair<Double, Double>? {
        val fine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            coarse != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val fix = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            }
        }.maxByOrNull { it.time } ?: return null
        return fix.latitude to fix.longitude
    }

    private fun push(context: Context, manager: AppWidgetManager, id: Int, size: String, cls: Class<*>, face: WidgetFace) {
        val layout = sizes.first { it.second == size }.third
        val views = RemoteViews(context.packageName, layout)
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val paper = if (night) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        val ink = if (night) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
        val mute = if (night) 0xFF9A9A9A.toInt() else 0xFF6A6A6A.toInt()
        views.setInt(R.id.widget_root, "setBackgroundColor", paper)
        views.setTextColor(R.id.widget_route, ink)
        views.setTextColor(R.id.widget_minutes, ink)
        views.setTextViewText(R.id.widget_route, face.route)
        views.setTextViewText(R.id.widget_minutes, face.minutes)
        if (size != "1x1") {
            val bound = if (face.bound.isBlank()) "" else UiPrefs.wrap(context).getString(R.string.bound_to, face.bound)
            views.setTextColor(R.id.widget_bound, mute)
            views.setTextColor(R.id.widget_stop, mute)
            views.setTextViewText(R.id.widget_bound, bound)
            views.setTextViewText(R.id.widget_stop, face.stop)
            views.setViewVisibility(R.id.widget_bound, if (bound.isBlank()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_stop, if (face.stop.isBlank()) View.GONE else View.VISIBLE)
        }
        val route = activeRoute(context, id)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (route != null) {
                putExtra(EXTRA, route.id)
                putExtra("id", route.id)
                putExtra("title", route.title)
                putExtra("dest", route.dest)
                putExtra("co", route.co)
                putExtra("route", route.route)
                putExtra("dir", route.dir)
                putExtra("st", route.serviceType)
                putExtra("rid", route.routeId)
                putExtra("region", route.region)
                putExtra("sid", route.stopId)
                putExtra("line", route.line)
                putExtra("side", route.side)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getActivity(context, id, intent, flags)
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        manager.updateAppWidget(id, views)
    }

    fun activeRoute(context: Context, id: Int): FavRoute? {
        val choice = WidgetPrefs.choice(context, id)
        if (choice.manual && choice.route != null) return choice.route
        return chooseWidgetRoute(
            UiPrefs.savedRoutes(context),
            RouteUsage.read(context),
            RouteUsage.last(context),
            nearbyRoute(context),
        )
    }

    fun provider(size: String): Class<*> = sizes.first { it.second == size }.first

    data class PlacedWidget(val id: Int, val size: String, val cls: Class<*>)
}

class EtaWidgetTiny : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        deliver(context, appWidgetManager, appWidgetIds, "1x1")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }
}

class EtaWidgetTall : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        deliver(context, appWidgetManager, appWidgetIds, "1x2")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }
}

class EtaWidgetSquare : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        deliver(context, appWidgetManager, appWidgetIds, "2x2")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }
}

private fun AppWidgetProvider.deliver(context: Context, manager: AppWidgetManager, ids: IntArray, size: String) {
    val cls = EtaWidgets.provider(size)
    ids.forEach { EtaWidgets.pushCached(context, manager, it, size, cls) }
    val pending = goAsync()
    Thread {
        try {
            ids.forEach { EtaWidgets.refreshOne(context, it, size, cls) }
        } finally {
            pending.finish()
        }
    }.start()
}
