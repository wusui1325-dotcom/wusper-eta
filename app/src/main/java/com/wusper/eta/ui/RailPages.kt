package com.wusper.eta.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.graphics.drawscope.withTransform
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.RailExit
import com.wusper.eta.data.uniformFit
import org.json.JSONObject
import kotlin.math.hypot
import com.wusper.eta.data.RailFocus
import com.wusper.eta.data.UiPrefs
import com.wusper.eta.data.atDestinationTerminus

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
            OfficialArt(
                asset = if (light) "lrt_system_map.png" else "mtr_system_map.png",
                sheet = if (light) "lrt" else "mtr",
                onStation = { id -> model.openRailStation(if (light) "LRT" else "MTR", id) },
                onLine = { line ->
                    if (light) model.openLightRoute(line.code, line.dir, line.dest, line.stop)
                    else model.openHeavyLine(line.code, line.name.ifBlank { line.code })
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
    val heavyStation = page.kind != "LRT"
    val exits = model.exitsOf(if (heavyStation) page.id else "")
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
            if (heavyStation || exits.isNotEmpty()) {
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

private data class ArtStation(val id: String, val x: Float, val y: Float)

private data class ArtLine(
    val code: String,
    val name: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val segment: Boolean,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val dir: String,
    val dest: String,
    val stop: String,
)

private data class ArtSheet(val stations: List<ArtStation>, val lines: List<ArtLine>)

@Composable
private fun OfficialArt(
    asset: String,
    sheet: String,
    onStation: (String) -> Unit,
    onLine: (ArtLine) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val image = remember(asset) {
        context.assets.open(asset).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    val art = remember(sheet) {
        val text = context.assets.open("rail_hits.json").bufferedReader().use { it.readText() }
        parseArt(JSONObject(text).getJSONObject(sheet))
    }
    var scale by remember(asset) { mutableFloatStateOf(1f) }
    var pan by remember(asset) { mutableStateOf(Offset.Zero) }
    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(art, image) {
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
                        val fit = uniformFit(size.width.toFloat(), size.height.toFloat(), image.width.toFloat(), image.height.toFloat())
                        if (pressed.size >= 2) {
                            pinch = true
                            val now = (pressed[0].position - pressed[1].position).getDistance()
                            if (span > 8f && now > 8f) {
                                val factor = (span / now).coerceIn(0.5f, 2f)
                                val mid = Offset(
                                    (pressed[0].position.x + pressed[1].position.x) / 2f,
                                    (pressed[0].position.y + pressed[1].position.y) / 2f,
                                )
                                val before = screenToImage(mid, size.width.toFloat(), size.height.toFloat(), image.width, image.height, scale, pan)
                                scale = (scale * (1f / factor)).coerceIn(1f, 8f)
                                val after = screenToImage(mid, size.width.toFloat(), size.height.toFloat(), image.width, image.height, scale, pan)
                                pan += Offset((after.x - before.x) * fit * scale, (after.y - before.y) * fit * scale)
                            }
                            span = now
                        } else {
                            val point = pressed[0].position
                            val delta = point - previous
                            if (delta.getDistance() > 5f) moved = true
                            if (moved && !pinch) pan += delta
                            previous = point
                            span = 0f
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (!moved && !pinch) {
                        val at = screenToImage(down.position, size.width.toFloat(), size.height.toFloat(), image.width, image.height, scale, pan)
                        val station = art.stations.minByOrNull { hypot((it.x - at.x).toDouble(), (it.y - at.y).toDouble()) }
                        val stationDistance = station?.let { hypot((it.x - at.x).toDouble(), (it.y - at.y).toDouble()) } ?: Double.MAX_VALUE
                        if (station != null && stationDistance <= 36.0) {
                            onStation(station.id)
                        } else {
                            var best = Double.MAX_VALUE
                            var chosen: ArtLine? = null
                            for (line in art.lines) {
                                val distance = if (line.segment) {
                                    hypotDistance(at, line.x1, line.y1, line.x2, line.y2)
                                } else if (at.x in (line.x - 12f)..(line.x + line.w + 12f) && at.y in (line.y - 12f)..(line.y + line.h + 12f)) {
                                    0.0
                                } else {
                                    Double.MAX_VALUE
                                }
                                if (distance < best) {
                                    best = distance
                                    chosen = line
                                }
                            }
                            if (chosen != null && best <= 22.0) onLine(chosen)
                        }
                    }
                }
            },
        ) {
            val fit = uniformFit(size.width, size.height, image.width.toFloat(), image.height.toFloat())
            val ox = (size.width - image.width * fit) / 2f + pan.x
            val oy = (size.height - image.height * fit) / 2f + pan.y
            withTransform({
                translate(left = ox, top = oy)
                scale(fit * scale, fit * scale, pivot = Offset.Zero)
            }) {
                drawImage(image)
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
        MapGestureHints(Modifier.align(Alignment.BottomStart))
    }
}

private fun parseArt(root: JSONObject): ArtSheet {
    val stations = buildList {
        val arr = root.optJSONArray("stations") ?: return@buildList
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val id = row.optString("id")
            if (id.isBlank()) continue
            add(ArtStation(id, row.optDouble("x").toFloat(), row.optDouble("y").toFloat()))
        }
    }
    val lines = buildList {
        val arr = root.optJSONArray("lines") ?: return@buildList
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val code = row.optString("code")
            if (code.isBlank()) continue
            val segment = row.has("x1")
            add(
                ArtLine(
                    code = code,
                    name = row.optString("name"),
                    x = row.optDouble("x").toFloat(),
                    y = row.optDouble("y").toFloat(),
                    w = row.optDouble("w").toFloat(),
                    h = row.optDouble("h").toFloat(),
                    segment = segment,
                    x1 = row.optDouble("x1").toFloat(),
                    y1 = row.optDouble("y1").toFloat(),
                    x2 = row.optDouble("x2").toFloat(),
                    y2 = row.optDouble("y2").toFloat(),
                    dir = row.optString("dir").ifBlank { "1" },
                    dest = row.optString("dest"),
                    stop = row.optString("stop"),
                ),
            )
        }
    }
    return ArtSheet(stations, lines)
}

private fun screenToImage(point: Offset, viewW: Float, viewH: Float, imgW: Int, imgH: Int, scale: Float, pan: Offset): Offset {
    val fit = uniformFit(viewW, viewH, imgW.toFloat(), imgH.toFloat())
    val ox = (viewW - imgW * fit) / 2f + pan.x
    val oy = (viewH - imgH * fit) / 2f + pan.y
    return Offset((point.x - ox) / (fit * scale), (point.y - oy) / (fit * scale))
}

private fun hypotDistance(point: Offset, x1: Float, y1: Float, x2: Float, y2: Float): Double {
    val dx = x2 - x1
    val dy = y2 - y1
    val len = dx * dx + dy * dy
    if (len <= 1f) return hypot((point.x - x1).toDouble(), (point.y - y1).toDouble())
    val t = (((point.x - x1) * dx + (point.y - y1) * dy) / len).coerceIn(0f, 1f)
    val px = x1 + dx * t
    val py = y1 + dy * t
    return hypot((point.x - px).toDouble(), (point.y - py).toDouble())
}
