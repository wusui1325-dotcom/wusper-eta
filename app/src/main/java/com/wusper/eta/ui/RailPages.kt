package com.wusper.eta.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.LightRailIndex
import com.wusper.eta.data.RailExit
import com.wusper.eta.data.RailFocus
import com.wusper.eta.data.UiPrefs
import com.wusper.eta.data.atDestinationTerminus
import kotlin.math.min

@Composable
fun RailPane(model: AppModel, modifier: Modifier = Modifier) {
    when (val page = model.rail) {
        is RailFocus.Map -> SystemMap(model, page.light, modifier)
        is RailFocus.Station -> StationPane(model, page, modifier)
        RailFocus.Nation -> MetroCityPane(model, modifier)
        is RailFocus.Metro -> MetroMapPane(model, page, modifier)
        null -> Unit
    }
}

@Composable
private fun SystemMap(model: AppModel, light: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val english = remember { UiPrefs.language(context) == UiPrefs.EN }
    val atlas = model.atlas
    val heavy = model.heavyStations()
    val lrt = model.lightStops()
    val dots: List<MapDot>
    val paths: List<MapPath>
    if (light) {
        dots = lrt.filter { it.lat != 0.0 && it.lng != 0.0 }.map { stop ->
            val name = if (english && stop.nameEn.isNotBlank()) stop.nameEn else stop.name
            val busy = stop.routes.map { it.route }.distinct().size >= 4
            MapDot(stop.id, name, stop.lat, stop.lng, busy)
        }
        val byId = dots.associateBy { it.id }
        paths = lightPaths(lrt, byId)
    } else {
        dots = heavy.filter { it.lat != 0.0 && it.lng != 0.0 }.map { station ->
            val name = if (english && station.nameEn.isNotBlank()) station.nameEn else station.name
            MapDot(station.sta, name, station.lat, station.lng, station.lines.size > 1)
        }
        val byId = dots.associateBy { it.id }
        paths = atlas?.lines.orEmpty().flatMap { line ->
            val name = if (english && line.nameEn.isNotBlank()) line.nameEn else line.nameTc.ifBlank { line.code }
            line.paths.mapNotNull { ids ->
                val points = ids.mapNotNull { byId[it] }
                if (points.size < 2) null else MapPath(line.code, name, points, light = false, dir = "", dest = "")
            }
        }
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton(Glyphs.back, stringResource(R.string.label_back), label = stringResource(R.string.label_back)) {
                model.closeRail()
            }
            Spacer(Modifier.width(8.dp))
            BasicText(
                stringResource(if (light) R.string.rail_lrt else R.string.rail_mtr),
                style = Ink.title,
                maxLines = 1,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SchematicMap(
                dots,
                paths,
                onDot = { dot -> model.openRailStation(if (light) "LRT" else "MTR", dot.id) },
                onPath = { path ->
                    if (path.light) {
                        model.openLightRoute(path.id, path.dir, path.dest, path.points.first().id)
                    } else {
                        model.openHeavyLine(path.id, path.name)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkButton(
                    Glyphs.mtr,
                    stringResource(R.string.rail_mtr),
                    selected = !light,
                    label = stringResource(R.string.rail_mtr),
                ) { model.showRailMap(false) }
                Spacer(Modifier.width(8.dp))
                InkButton(
                    Glyphs.along,
                    stringResource(R.string.rail_lrt),
                    selected = light,
                    label = stringResource(R.string.rail_lrt),
                ) { model.showRailMap(true) }
            }
        }
    }
}

@Composable
private fun StationPane(model: AppModel, page: RailFocus.Station, modifier: Modifier) {
    val heavy = if (page.kind == "LRT") null else model.heavyStation(page.id)
    val stop = if (page.kind == "LRT") model.lightStop(page.id) else null
    val name = heavy?.name ?: stop?.name ?: ""
    val nameEn = heavy?.nameEn ?: stop?.nameEn.orEmpty()
    val lat = heavy?.lat ?: stop?.lat ?: 0.0
    val lng = heavy?.lng ?: stop?.lng ?: 0.0
    val exits = model.exitsOf(if (page.kind == "LRT") "" else page.id)
    var exitsOpen by remember(page.id) { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton(Glyphs.back, stringResource(R.string.label_back), label = stringResource(R.string.label_back)) {
                model.closeRail()
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                BasicText(name, style = Ink.detailTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nameEn.isNotBlank() && !nameEn.equals(name, ignoreCase = true)) {
                    BasicText(nameEn, style = Ink.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (lat != 0.0 && lng != 0.0) {
            StationMap(lat, lng, name, Modifier.fillMaxWidth().height(240.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 28.dp),
        ) {
            if (exits.isNotEmpty()) {
                item(key = "exits", contentType = "exits") {
                    ExitBlock(exits, exitsOpen) { exitsOpen = !exitsOpen }
                }
            }
            if (heavy != null) {
                items(heavy.lines.zip(heavy.lineNames), key = { "line-${it.first}" }, contentType = { "route" }) { (code, label) ->
                    ServingRow(label, "") { model.openHeavyLine(code, label) }
                }
            }
            if (stop != null) {
                val routes = stop.routes.groupBy { it.route }.map { (_, visits) ->
                    visits.firstOrNull { !atDestinationTerminus(stop.name, it.destTc) } ?: visits.first()
                }
                items(routes, key = { "lrt-${it.route}" }, contentType = { "route" }) { visit ->
                    val bound = if (visit.destTc.isBlank()) "" else stringResource(R.string.bound_to, visit.destTc)
                    ServingRow(visit.route, bound) {
                        model.openLightRoute(visit.route, visit.dir, visit.destTc, stop.id)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ExitBlock(exits: List<RailExit>, open: Boolean, onToggle: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Glyphs.chevron, null, Ink.black, 22.dp)
            Spacer(Modifier.width(8.dp))
            BasicText(stringResource(R.string.rail_exit), style = Ink.title)
        }
        if (open) {
            exits.forEach { exit ->
                BasicText(exit.exit, style = Ink.body, modifier = Modifier.padding(top = 8.dp))
                exit.places.forEach { place ->
                    BasicText(place, style = Ink.meta, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
            }
        } else {
            Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
        }
    }
}

@Composable
private fun ServingRow(title: String, bound: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = Ink.arrivalRoute, maxLines = 1, softWrap = false)
            if (bound.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                BasicText(
                    bound,
                    style = Ink.arrivalBound,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
    }
}

@Composable
private fun StationMap(lat: Double, lng: Double, name: String, modifier: Modifier) {
    val context = LocalContext.current
    val mapView = remember(lat, lng) {
        MapView(context).apply {
            onCreate(Bundle())
            onStart()
            onResume()
            getMapAsync { google ->
                google.uiSettings.apply {
                    isZoomControlsEnabled = false
                    isMapToolbarEnabled = false
                    isCompassEnabled = false
                    isMyLocationButtonEnabled = false
                }
                google.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f))
                google.addMarker(
                    MarkerOptions().position(LatLng(lat, lng)).icon(stationDot()).anchor(0.5f, 0.5f).title(name),
                )
            }
        }
    }
    DisposableEffect(mapView) {
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun stationDot() = BitmapDescriptorFactory.fromBitmap(
    Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888).also { bitmap ->
        Canvas(bitmap).drawCircle(
            14f,
            14f,
            8f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                style = Paint.Style.FILL
            },
        )
    },
)

private data class MapDot(val id: String, val name: String, val lat: Double, val lng: Double, val prominent: Boolean)

private data class MapPath(
    val id: String,
    val name: String,
    val points: List<MapDot>,
    val light: Boolean,
    val dir: String,
    val dest: String,
)

private data class GeoFrame(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)

private fun lightPaths(stops: List<LightRailIndex.Stop>, byId: Map<String, MapDot>): List<MapPath> {
    val seen = HashSet<String>()
    val pairs = stops.flatMap { it.routes }.map { it.route to it.dir }.distinct()
    return buildList {
        for ((route, dir) in pairs) {
            val along = stops.mapNotNull { stop ->
                val visit = stop.routes.firstOrNull { it.route == route && it.dir == dir } ?: return@mapNotNull null
                stop to visit
            }.sortedBy { it.second.seq }
            val ids = ArrayList<String>()
            for ((stop, _) in along) {
                if (ids.lastOrNull() != stop.id && byId.containsKey(stop.id)) ids.add(stop.id)
            }
            if (ids.size < 2) continue
            val key = ids.joinToString(">")
            if (!seen.add(key) || ids.asReversed().joinToString(">") in seen) continue
            val points = ids.mapNotNull { byId[it] }
            if (points.size < 2) continue
            val dest = along.lastOrNull()?.second?.destTc.orEmpty()
            add(MapPath(route, route, points, light = true, dir = dir, dest = dest))
        }
    }
}

@Composable
private fun SchematicMap(
    dots: List<MapDot>,
    paths: List<MapPath>,
    onDot: (MapDot) -> Unit,
    onPath: (MapPath) -> Unit,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    var scale by remember(dots) { mutableFloatStateOf(1f) }
    var pan by remember(dots) { mutableStateOf(Offset.Zero) }
    val frame = remember(dots) { frameOf(dots) }
    val label = TextStyle(color = Ink.black, fontSize = 11.sp)
    val lineLabel = TextStyle(color = Ink.black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val ink = Ink.black
    Box(modifier) {
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxSize().pointerInput(dots, paths, frame) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var moved = false
                var pinch = false
                var previous = down.position
                var span = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        pinch = true
                        val now = (pressed[0].position - pressed[1].position).getDistance()
                        if (span > 0f && now > 0f) {
                            scale = (scale * (now / span)).coerceIn(1f, 8f)
                            if (scale == 1f) pan = Offset.Zero
                        }
                        span = now
                    } else {
                        val point = pressed[0].position
                        val delta = point - previous
                        if (delta.getDistance() > 6f) moved = true
                        if (moved && !pinch) pan += delta
                        previous = point
                        span = 0f
                    }
                    event.changes.forEach { it.consume() }
                }
                if (!moved && !pinch && frame != null) {
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val hitDot = dots.minByOrNull { dot ->
                        (project(width, height, frame, scale, pan, dot.lat, dot.lng) - down.position).getDistance()
                    }
                    val dotDistance = hitDot?.let {
                        (project(width, height, frame, scale, pan, it.lat, it.lng) - down.position).getDistance()
                    } ?: Float.MAX_VALUE
                    if (hitDot != null && dotDistance <= 28f) {
                        onDot(hitDot)
                    } else {
                        var best = Float.MAX_VALUE
                        var chosen: MapPath? = null
                        for (path in paths) {
                            val placed = path.points.map { project(width, height, frame, scale, pan, it.lat, it.lng) }
                            for (i in 0 until placed.lastIndex) {
                                val distance = segmentDistance(down.position, placed[i], placed[i + 1])
                                if (distance < best) {
                                    best = distance
                                    chosen = path
                                }
                            }
                        }
                        if (chosen != null && best <= 22f) onPath(chosen)
                    }
                }
            }
        },
    ) {
        val geo = frame ?: return@Canvas
        val width = size.width
        val height = size.height
        for (path in paths) {
            val placed = path.points.map { project(width, height, geo, scale, pan, it.lat, it.lng) }
            for (i in 0 until placed.lastIndex) {
                drawLine(ink, placed[i], placed[i + 1], strokeWidth = if (path.light) 2.2f else 3f)
            }
            val mid = placed[placed.size * 2 / 5]
            val text = measurer.measure(path.name, lineLabel)
            drawText(text, topLeft = Offset(mid.x + 8f, mid.y - text.size.height - 4f))
        }
        for (dot in dots) {
            val at = project(width, height, geo, scale, pan, dot.lat, dot.lng)
            drawCircle(ink, radius = if (dot.prominent) 6.5f else 4.5f, center = at)
            if (scale >= 1.7f || dot.prominent) {
                val text = measurer.measure(dot.name, label)
                drawText(text, topLeft = Offset(at.x + 8f, at.y + 4f))
            }
        }
    }
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            InkButton(Glyphs.zoomIn, stringResource(R.string.cd_zoom_in), label = stringResource(R.string.cd_zoom_in)) {
                scale = (scale * 1.25f).coerceIn(1f, 8f)
            }
            Spacer(Modifier.height(4.dp))
            InkButton(Glyphs.zoomOut, stringResource(R.string.cd_zoom_out), label = stringResource(R.string.cd_zoom_out)) {
                val next = (scale * 0.8f).coerceIn(1f, 8f)
                scale = next
                if (next == 1f) pan = Offset.Zero
            }
        }
    }
}

private fun frameOf(dots: List<MapDot>): GeoFrame? {
    if (dots.isEmpty()) return null
    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLng = Double.POSITIVE_INFINITY
    var maxLng = Double.NEGATIVE_INFINITY
    for (dot in dots) {
        if (dot.lat < minLat) minLat = dot.lat
        if (dot.lat > maxLat) maxLat = dot.lat
        if (dot.lng < minLng) minLng = dot.lng
        if (dot.lng > maxLng) maxLng = dot.lng
    }
    return GeoFrame(minLat, maxLat, minLng, maxLng)
}

private fun project(width: Float, height: Float, frame: GeoFrame, scale: Float, pan: Offset, lat: Double, lng: Double): Offset {
    val padX = 48f
    val padTop = 28f
    val padBottom = 108f
    val spanLng = (frame.maxLng - frame.minLng).coerceAtLeast(1e-4)
    val spanLat = (frame.maxLat - frame.minLat).coerceAtLeast(1e-4)
    val availW = (width - padX * 2f).coerceAtLeast(1f)
    val availH = (height - padTop - padBottom).coerceAtLeast(1f)
    val fit = min(availW / spanLng.toFloat(), availH / spanLat.toFloat())
    val contentW = spanLng.toFloat() * fit
    val contentH = spanLat.toFloat() * fit
    val originX = (width - contentW) / 2f
    val originY = padTop + (availH - contentH) / 2f
    val x = originX + ((lng - frame.minLng) / spanLng).toFloat() * contentW
    val y = originY + ((frame.maxLat - lat) / spanLat).toFloat() * contentH
    val cx = width / 2f
    val cy = height / 2f
    return Offset(cx + (x - cx) * scale + pan.x, cy + (y - cy) * scale + pan.y)
}

private fun segmentDistance(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = dx * dx + dy * dy
    if (len <= 1f) return (point - start).getDistance()
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / len).coerceIn(0f, 1f)
    val projected = Offset(start.x + dx * t, start.y + dy * t)
    return (point - projected).getDistance()
}
