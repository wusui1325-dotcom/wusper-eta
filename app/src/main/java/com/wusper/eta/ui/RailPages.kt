package com.wusper.eta.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.RailExit
import com.wusper.eta.data.RailFocus
import com.wusper.eta.data.atDestinationTerminus
import com.wusper.eta.data.uniformFit
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.hypot

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
    Column(modifier.fillMaxSize().background(Ink.paper)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton(Glyphs.back, stringResource(R.string.label_back), label = stringResource(R.string.label_back)) {
                model.closeRail()
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Ink.black),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(if (light) Glyphs.lightRail else Glyphs.mtr, null, Ink.paper, 14.dp)
            }
            Spacer(Modifier.width(8.dp))
            BasicText(
                stringResource(if (light) R.string.rail_lrt else R.string.rail_mtr),
                style = Ink.title,
                maxLines = 1,
            )
        }
        Hair()
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
            val dockShape = RoundedCornerShape(999.dp)
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(dockShape)
                    .background(Ink.paper.copy(alpha = 0.95f))
                    .border(Ink.lineWidth, Ink.line, dockShape)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InkButton(
                    Glyphs.mtr,
                    stringResource(R.string.rail_mtr),
                    selected = !light,
                    label = stringResource(R.string.rail_mtr),
                ) { model.showRailMap(false) }
                InkButton(
                    Glyphs.lightRail,
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
    Column(modifier.fillMaxSize().background(Ink.paper)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton(Glyphs.back, stringResource(R.string.label_back), label = stringResource(R.string.label_back)) {
                model.closeRail()
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Ink.black),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(if (heavyStation) Glyphs.mtr else Glyphs.lightRail, null, Ink.paper, 15.dp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                BasicText(name, style = Ink.detailTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (nameEn.isNotBlank() && !nameEn.equals(name, ignoreCase = true)) {
                    BasicText(nameEn, style = Ink.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Hair()
        if (lat != 0.0 && lng != 0.0) {
            StationMap(lat, lng, name, Modifier.fillMaxWidth().height(230.dp))
            Hair()
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
            ) {
                if (heavyStation || exits.isNotEmpty()) {
                    item(key = "exits", contentType = "exits") {
                        ExitBlock(exits, exitsOpen) { exitsOpen = !exitsOpen }
                    }
                }
                if (heavy != null) {
                    items(heavy.lines.zip(heavy.lineNames), key = { "line-${it.first}" }, contentType = { "route" }) { (code, label) ->
                        ServingRow(Glyphs.mtr, label, "") { model.openHeavyLine(code, label) }
                    }
                }
                if (stop != null) {
                    val routes = stop.routes.groupBy { it.route }.map { (_, visits) ->
                        visits.firstOrNull { !atDestinationTerminus(stop.name, it.destTc) } ?: visits.first()
                    }
                    items(routes, key = { "lrt-${it.route}" }, contentType = { "route" }) { visit ->
                        val bound = if (visit.destTc.isBlank()) "" else stringResource(R.string.bound_to, visit.destTc)
                        ServingRow(Glyphs.lightRail, visit.route, bound) {
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp)
            .clip(shape)
            .background(Ink.wash)
            .border(Ink.lineWidth, Ink.line, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Glyphs.exit, null, Ink.black, 18.dp)
            Spacer(Modifier.width(8.dp))
            BasicText(stringResource(R.string.rail_exit), style = Ink.title, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Ink.paper)
                    .border(Ink.lineWidth, Ink.line, CircleShape)
                    .graphicsLayer { rotationZ = if (open) 180f else 0f },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Glyphs.chevron, null, Ink.black, 15.dp)
            }
        }
        if (open) {
            Hair()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                exits.forEachIndexed { idx, exit ->
                    if (idx > 0) {
                        Spacer(Modifier.height(10.dp))
                        Hair()
                        Spacer(Modifier.height(10.dp))
                    }
                    StreamlinedPill(exit.exit, icon = Glyphs.exit, inverted = true)
                    exit.places.forEach { place ->
                        BasicText(place, style = Ink.meta, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServingRow(icon: ImageVector, title: String, bound: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(Ink.wash)
            .border(Ink.lineWidth, Ink.line, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Ink.black)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(icon, null, Ink.paper, 15.dp)
            Spacer(Modifier.width(5.dp))
            BasicText(title, style = Ink.route.copy(color = Ink.paper), maxLines = 1, softWrap = false)
        }
        if (bound.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            BasicText(
                bound,
                style = Ink.arrivalBound,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(8.dp))
        Glyph(Glyphs.chevronRight, null, Ink.mute, 16.dp)
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
    Bitmap.createBitmap(34, 34, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawCircle(
            17f,
            17f,
            11f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(11, 11, 12)
                style = Paint.Style.FILL
            },
        )
        canvas.drawCircle(
            17f,
            17f,
            9f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 3f
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
    val density = LocalDensity.current
    val onStationNow by rememberUpdatedState(onStation)
    val onLineNow by rememberUpdatedState(onLine)
    val image = remember(asset) {
        context.assets.open(asset).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
    }
    val art = remember(sheet) {
        val text = context.assets.open("rail_hits.json").bufferedReader().use { it.readText() }
        parseArt(JSONObject(text).getJSONObject(sheet))
    }
    var scale by remember(asset) { mutableFloatStateOf(1f) }
    var pan by remember(asset) { mutableStateOf(Offset.Zero) }
    var viewport by remember(asset) { mutableStateOf(IntSize.Zero) }
    var tapFeedback by remember(asset) { mutableStateOf<Offset?>(null) }

    LaunchedEffect(tapFeedback) {
        if (tapFeedback != null) {
            delay(320)
            tapFeedback = null
        }
    }

    fun zoomAround(targetScale: Float, pivot: Offset) {
        val vw = viewport.width.toFloat()
        val vh = viewport.height.toFloat()
        val clamped = targetScale.coerceIn(1f, 8f)
        if (vw <= 0f || vh <= 0f) {
            scale = clamped
            if (clamped == 1f) pan = Offset.Zero
            return
        }
        val fit = uniformFit(vw, vh, image.width.toFloat(), image.height.toFloat())
        val before = screenToImage(pivot, vw, vh, image.width, image.height, scale, pan)
        scale = clamped
        val after = screenToImage(pivot, vw, vh, image.width, image.height, scale, pan)
        val shifted = pan + Offset((after.x - before.x) * fit * scale, (after.y - before.y) * fit * scale)
        pan = clampMapPan(shifted, vw, vh, image.width, image.height, scale)
    }

    val stationScreenRadiusPx = with(density) { 26.dp.toPx() }
    val lineScreenRadiusPx = with(density) { 18.dp.toPx() }

    Box(modifier.onSizeChanged { viewport = it }) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(art, image) {
                awaitEachGesture {
                    val slop = viewConfiguration.touchSlop
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    var pinch = false
                    var previous = down.position
                    var lastPos = down.position
                    var span = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            event.changes.firstOrNull()?.let { lastPos = it.position }
                            break
                        }
                        val vw = size.width.toFloat()
                        val vh = size.height.toFloat()
                        val fit = uniformFit(vw, vh, image.width.toFloat(), image.height.toFloat())
                        if (pressed.size >= 2) {
                            pinch = true
                            val now = (pressed[0].position - pressed[1].position).getDistance()
                            if (span > 8f && now > 8f) {
                                val factor = (span / now).coerceIn(0.5f, 2f)
                                val mid = Offset(
                                    (pressed[0].position.x + pressed[1].position.x) / 2f,
                                    (pressed[0].position.y + pressed[1].position.y) / 2f,
                                )
                                val before = screenToImage(mid, vw, vh, image.width, image.height, scale, pan)
                                scale = (scale * (1f / factor)).coerceIn(1f, 8f)
                                val after = screenToImage(mid, vw, vh, image.width, image.height, scale, pan)
                                pan = clampMapPan(
                                    pan + Offset((after.x - before.x) * fit * scale, (after.y - before.y) * fit * scale),
                                    vw, vh, image.width, image.height, scale,
                                )
                            }
                            span = now
                        } else {
                            val point = pressed[0].position
                            lastPos = point
                            val delta = point - previous
                            if ((point - down.position).getDistance() > slop) moved = true
                            if (moved && !pinch) {
                                pan = clampMapPan(pan + delta, vw, vh, image.width, image.height, scale)
                            }
                            previous = point
                            span = 0f
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (!moved && !pinch) {
                        val vw = size.width.toFloat()
                        val vh = size.height.toFloat()
                        val fit = uniformFit(vw, vh, image.width.toFloat(), image.height.toFloat())
                        val effScale = (fit * scale).coerceAtLeast(0.001f)
                        val at = screenToImage(lastPos, vw, vh, image.width, image.height, scale, pan)
                        val stationRadius = (stationScreenRadiusPx / effScale).coerceIn(16f, 64f).toDouble()
                        val lineRadius = (lineScreenRadiusPx / effScale).coerceIn(12f, 38f).toDouble()

                        val station = art.stations.minByOrNull { hypot((it.x - at.x).toDouble(), (it.y - at.y).toDouble()) }
                        val stationDistance = station?.let { hypot((it.x - at.x).toDouble(), (it.y - at.y).toDouble()) } ?: Double.MAX_VALUE
                        if (station != null && stationDistance <= stationRadius) {
                            tapFeedback = Offset(station.x, station.y)
                            onStationNow(station.id)
                        } else {
                            var best = Double.MAX_VALUE
                            var chosen: ArtLine? = null
                            for (line in art.lines) {
                                val distance = if (line.segment) {
                                    hypotDistance(at, line.x1, line.y1, line.x2, line.y2)
                                } else {
                                    rectDistance(at, line.x, line.y, line.w, line.h)
                                }
                                if (distance < best) {
                                    best = distance
                                    chosen = line
                                }
                            }
                            if (chosen != null && best <= lineRadius) {
                                tapFeedback = at
                                onLineNow(chosen)
                            }
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
            tapFeedback?.let { hit ->
                val sx = ox + hit.x * fit * scale
                val sy = oy + hit.y * fit * scale
                drawCircle(Color(0x330B0B0C), radius = 26f, center = Offset(sx, sy))
                drawCircle(Color(0xFF0B0B0C), radius = 16f, center = Offset(sx, sy), style = Stroke(width = 3f))
            }
        }
        Column(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            InkButton(Glyphs.zoomIn, stringResource(R.string.cd_zoom_in), label = stringResource(R.string.cd_zoom_in)) {
                zoomAround(scale * 1.35f, Offset(viewport.width / 2f, viewport.height / 2f))
            }
            InkButton(Glyphs.zoomOut, stringResource(R.string.cd_zoom_out), label = stringResource(R.string.cd_zoom_out)) {
                zoomAround(scale / 1.35f, Offset(viewport.width / 2f, viewport.height / 2f))
            }
            if (scale > 1.01f || pan != Offset.Zero) {
                InkButton(Glyphs.center, stringResource(R.string.cd_center), label = stringResource(R.string.cd_center)) {
                    scale = 1f
                    pan = Offset.Zero
                }
            }
        }
        MapGestureHints(Modifier.align(Alignment.BottomStart))
    }
}

internal fun clampMapPan(
    candidate: Offset,
    viewW: Float,
    viewH: Float,
    imgW: Int,
    imgH: Int,
    scale: Float,
): Offset {
    if (viewW <= 0f || viewH <= 0f || imgW <= 0 || imgH <= 0) return Offset.Zero
    val fit = uniformFit(viewW, viewH, imgW.toFloat(), imgH.toFloat())
    val baseOx = (viewW - imgW * fit) / 2f
    val baseOy = (viewH - imgH * fit) / 2f
    val scaledW = imgW * fit * scale
    val scaledH = imgH * fit * scale
    val marginX = viewW * 0.12f
    val marginY = viewH * 0.12f
    val clampedX = if (scaledW <= viewW) {
        (viewW - scaledW) / 2f - baseOx
    } else {
        candidate.x.coerceIn(viewW - scaledW - marginX - baseOx, marginX - baseOx)
    }
    val clampedY = if (scaledH <= viewH) {
        (viewH - scaledH) / 2f - baseOy
    } else {
        candidate.y.coerceIn(viewH - scaledH - marginY - baseOy, marginY - baseOy)
    }
    return Offset(clampedX, clampedY)
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

private fun rectDistance(point: Offset, x: Float, y: Float, w: Float, h: Float): Double {
    val dx = when {
        point.x < x -> x - point.x
        point.x > x + w -> point.x - (x + w)
        else -> 0f
    }
    val dy = when {
        point.y < y -> y - point.y
        point.y > y + h -> point.y - (y + h)
        else -> 0f
    }
    return hypot(dx.toDouble(), dy.toDouble())
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
