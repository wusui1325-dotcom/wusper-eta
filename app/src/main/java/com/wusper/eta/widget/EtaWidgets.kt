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
import com.wusper.eta.data.chooseSecondWidgetRoute
import com.wusper.eta.data.chooseWidgetRoute
import com.wusper.eta.data.nearestStop
import kotlinx.coroutines.runBlocking

object EtaWidgets {
    const val EXTRA = "wusper_widget"

    private val sizes = listOf(
        Triple(EtaWidgetTiny::class.java, "1x1", R.layout.widget_1x1),
        Triple(EtaWidgetWide::class.java, "2x1", R.layout.widget_2x1),
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
            runBlocking { resolve(context, id, size, previous) }
        } catch (_: Exception) {
            previous.copy(settled = false, minutes = if (previous.settled) previous.minutes else "")
        }
        WidgetPrefs.saveFace(context, id, face)
        val manager = AppWidgetManager.getInstance(context)
        push(context, manager, id, size, cls, face)
    }

    private suspend fun resolve(context: Context, id: Int, size: String, previous: WidgetFace): WidgetFace {
        val choice = WidgetPrefs.choice(context, id)
        val primary = fillSlot(
            context,
            choice.manual,
            choice.route,
            choice.stopId,
            choice.stopName,
            except = null,
            previousRoute = previous.route,
            previousStop = previous.stop,
            previousMinutes = previous.minutes,
            previousSettled = previous.settled,
        )
        if (size != "2x2") {
            return WidgetFace(
                route = primary.route,
                bound = primary.bound,
                stop = primary.stop,
                minutes = primary.minutes,
                settled = primary.settled,
            )
        }
        val second = fillSlot(
            context,
            choice.manualB,
            choice.routeB,
            choice.stopIdB,
            choice.stopNameB,
            except = primary.id,
            previousRoute = previous.routeB,
            previousStop = previous.stopB,
            previousMinutes = previous.minutesB,
            previousSettled = previous.settledB,
        )
        return WidgetFace(
            route = primary.route,
            bound = primary.bound,
            stop = primary.stop,
            minutes = primary.minutes,
            settled = primary.settled,
            routeB = second.route,
            boundB = second.bound,
            stopB = second.stop,
            minutesB = second.minutes,
            settledB = second.settled,
        )
    }

    private data class SlotFace(
        val id: String,
        val route: String,
        val bound: String,
        val stop: String,
        val minutes: String,
        val settled: Boolean,
    )

    private suspend fun fillSlot(
        context: Context,
        manual: Boolean,
        chosen: FavRoute?,
        stopId: String,
        stopName: String,
        except: String?,
        previousRoute: String,
        previousStop: String,
        previousMinutes: String,
        previousSettled: Boolean,
    ): SlotFace {
        val route = if (manual && chosen != null) {
            chosen
        } else if (except == null) {
            chooseWidgetRoute(
                UiPrefs.savedRoutes(context),
                RouteUsage.read(context),
                RouteUsage.last(context),
                fallbackRoutes(context).firstOrNull(),
            )
        } else {
            chooseSecondWidgetRoute(
                UiPrefs.savedRoutes(context),
                RouteUsage.read(context),
                RouteUsage.last(context),
                fallbackRoutes(context).firstOrNull { it.id != except },
                except,
            )
        } ?: return SlotFace("", previousRoute, "", previousStop, if (previousSettled) previousMinutes else "", false)
        val here = lastFix(context)
        val lat = here?.first ?: 0.0
        val lng = here?.second ?: 0.0
        val api = TransitApi()
        val none = UiPrefs.wrap(context).getString(R.string.none)
        val title = route.title.ifBlank { route.route.ifBlank { route.line } }
        val kept = if (previousSettled) previousMinutes else ""
        return if (route.line.isNotBlank()) {
            val index = MtrIndex.load(context)
            val stations = index.onLine(route.line).mapIndexed { i, station ->
                DetailStop(station.sta, station.name, station.lat, station.lng, i + 1)
            }
            val stop = if (stopId.isNotBlank()) stations.firstOrNull { it.id == stopId } else nearestStop(stations, lat, lng)
            if (stop == null) return SlotFace(route.id, title, route.dest, stopName.ifBlank { previousStop }, "", false)
            val board = api.mtrReady(route.line, stop.id, index::name)
                ?: return SlotFace(route.id, title, route.dest, stop.name, kept, false)
            val trains = if (route.side == "down") board.second else board.first
            val minute = trains.firstOrNull { it.minutes != null }?.minutes
            SlotFace(route.id, title, route.dest, stop.name, if (minute == null) none else "$minute′", true)
        } else {
            val stops = try {
                api.routeStops(route.asRoute()).map { DetailStop(it.id, it.name, it.lat, it.lng, it.seq) }
            } catch (_: Exception) {
                emptyList()
            }
            val stop = if (stopId.isNotBlank()) {
                stops.firstOrNull { it.id == stopId } ?: DetailStop(stopId, stopName, 0.0, 0.0, 0)
            } else {
                nearestStop(stops, lat, lng)
            }
            if (stop == null || stop.id.isBlank()) {
                return SlotFace(route.id, title, route.dest, stopName.ifBlank { previousStop }, "", false)
            }
            val slots = try {
                api.live(route.asRoute().copy(stopId = stop.id))
            } catch (_: Exception) {
                null
            }
            if (slots == null) {
                SlotFace(route.id, title, route.dest, stop.name, kept, false)
            } else {
                val minute = slots.firstOrNull { it.minutes != null }?.minutes
                SlotFace(route.id, title, route.dest, stop.name, if (minute == null) none else "$minute′", true)
            }
        }
    }

    private fun fallbackRoutes(context: Context): List<FavRoute> {
        val snap = SnapshotStore.read(context)
        val out = ArrayList<FavRoute>()
        snap.stops.forEach { stop ->
            stop.routes.take(2).forEach { route ->
                out.add(
                    FavRoute(
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
                    ),
                )
            }
        }
        snap.mtr.take(2).forEach { station ->
            val line = station.lines.firstOrNull() ?: return@forEach
            out.add(
                FavRoute(
                    id = "mtr|${line.code}|up",
                    title = line.name.ifBlank { line.code },
                    dest = "",
                    stopName = station.name,
                    stopId = station.sta,
                    line = line.code,
                    side = "up",
                ),
            )
        }
        return out.distinctBy { it.id }
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
        val localized = UiPrefs.wrap(context)
        val bound = boundText(localized, face.bound)
        views.setInt(R.id.widget_root, "setBackgroundColor", paper)
        paintBlock(views, R.id.widget_route, R.id.widget_minutes, R.id.widget_bound, ink, mute, face.route, face.minutes, bound)
        if (size == "1x1") {
            views.setTextColor(R.id.widget_nearest, mute)
            views.setTextViewText(R.id.widget_nearest, localized.getString(R.string.widget_nearest))
        } else {
            views.setTextColor(R.id.widget_stop, mute)
            views.setTextViewText(R.id.widget_stop, face.stop)
            views.setViewVisibility(R.id.widget_stop, if (face.stop.isBlank()) View.GONE else View.VISIBLE)
        }
        if (size == "2x2") {
            val boundB = boundText(localized, face.boundB)
            paintBlock(views, R.id.widget_route_b, R.id.widget_minutes_b, R.id.widget_bound_b, ink, mute, face.routeB, face.minutesB, boundB)
            views.setTextColor(R.id.widget_stop_b, mute)
            views.setTextViewText(R.id.widget_stop_b, face.stopB)
            views.setViewVisibility(R.id.widget_stop_b, if (face.stopB.isBlank()) View.GONE else View.VISIBLE)
            views.setInt(R.id.widget_rule, "setBackgroundColor", mute)
            views.setOnClickPendingIntent(R.id.widget_top, openIntent(context, id * 2, activeRoute(context, id, 0)))
            views.setOnClickPendingIntent(R.id.widget_bottom, openIntent(context, id * 2 + 1, activeRoute(context, id, 1)))
        } else {
            views.setOnClickPendingIntent(R.id.widget_root, openIntent(context, id, activeRoute(context, id, 0)))
        }
        manager.updateAppWidget(id, views)
    }

    private fun paintBlock(
        views: RemoteViews,
        routeId: Int,
        minutesId: Int,
        boundId: Int,
        ink: Int,
        mute: Int,
        route: String,
        minutes: String,
        bound: String,
    ) {
        views.setTextColor(routeId, ink)
        views.setTextColor(minutesId, ink)
        views.setTextColor(boundId, mute)
        views.setTextViewText(routeId, route)
        views.setTextViewText(minutesId, minutes)
        views.setTextViewText(boundId, bound)
        views.setViewVisibility(boundId, if (bound.isBlank()) View.GONE else View.VISIBLE)
    }

    private fun boundText(context: Context, dest: String): String {
        if (dest.isBlank()) return ""
        return context.getString(R.string.bound_to, dest)
    }

    private fun openIntent(context: Context, request: Int, route: FavRoute?): PendingIntent {
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
        return PendingIntent.getActivity(context, request, intent, flags)
    }

    fun activeRoute(context: Context, id: Int, slot: Int = 0): FavRoute? {
        val choice = WidgetPrefs.choice(context, id)
        val manual = if (slot == 0) choice.manual else choice.manualB
        val chosen = if (slot == 0) choice.route else choice.routeB
        if (manual && chosen != null) return chosen
        val favorites = UiPrefs.savedRoutes(context)
        val usage = RouteUsage.read(context)
        val last = RouteUsage.last(context)
        val fallback = fallbackRoutes(context)
        return if (slot == 0) {
            chooseWidgetRoute(favorites, usage, last, fallback.firstOrNull())
        } else {
            val first = activeRoute(context, id, 0)?.id.orEmpty()
            chooseSecondWidgetRoute(favorites, usage, last, fallback.firstOrNull { it.id != first }, first)
        }
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

class EtaWidgetWide : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        deliver(context, appWidgetManager, appWidgetIds, "2x1")
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
