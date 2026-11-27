package com.wusper.eta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.wusper.eta.BuildConfig
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.Help
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Plot(val key: String, val lat: Double, val lng: Double, val kind: Int)

@Composable
fun MapPane(model: AppModel) {
    val context = LocalContext.current
    val key = BuildConfig.MAPS_API_KEY
    var play by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(model.started, key) {
        if (!model.started || key.isBlank()) return@LaunchedEffect
        play = withContext(Dispatchers.IO) {
            try {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
            } catch (_: Throwable) {
                false
            }
        }
    }
    val google = key.isNotBlank() && play == true
    val help = if (key.isBlank() || play == false) Help.Schematic else null
    Column(Modifier.fillMaxSize()) {
        RowBar(model, help)
        Box(Modifier.weight(1f).fillMaxSize()) {
            if (google) {
                GoogleMapPane(model)
            } else {
                Schematic(model)
            }
        }
    }
}

@Composable
private fun RowBar(model: AppModel, help: Help?) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHead(Glyphs.map, stringResource(R.string.tab_map), help) { model.help = it }
        Spacer(Modifier.weight(1f))
        InkButton(Glyphs.close, stringResource(R.string.cd_close), label = stringResource(R.string.cd_close)) {
            model.closeMap()
        }
    }
    Hair()
}

@Composable
private fun Schematic(model: AppModel) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val plots = remember(model.stops, model.mtr, model.lat, model.lng) {
        buildList {
            model.stops.filter { it.lat != 0.0 && it.lng != 0.0 }.forEach { add(Plot(it.key, it.lat, it.lng, 0)) }
            model.mtr.filter { it.lat != 0.0 }.forEach { add(Plot("mtr:${it.sta}", it.lat, it.lng, 1)) }
            if (model.lat != 0.0) add(Plot("here", model.lat, model.lng, 2))
        }
    }
    Box(Modifier.fillMaxSize().background(Ink.paper)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(plots) {
                    detectTapGestures { tap ->
                        val hit = hitTest(plots, tap, size.width.toFloat(), size.height.toFloat(), scale, pan)
                        if (hit != null && !hit.startsWith("mtr:") && hit != "here") model.open(hit)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        pan += drag
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            for (x in 1..3) {
                drawLine(Ink.line, Offset(w * x / 4f, 0f), Offset(w * x / 4f, h), strokeWidth = 1f)
            }
            for (y in 1..3) {
                drawLine(Ink.line, Offset(0f, h * y / 4f), Offset(w, h * y / 4f), strokeWidth = 1f)
            }
            plots.forEach { plot ->
                val at = project(plots, plot.lat, plot.lng, w, h, scale, pan)
                when (plot.kind) {
                    1 -> drawCircle(Ink.black, 7f, at, style = Stroke(width = 2.2f))
                    2 -> {
                        drawCircle(Ink.black, 5f, at)
                        drawCircle(Ink.black, 12f, at, style = Stroke(width = 1.6f))
                    }
                    else -> drawCircle(Ink.black, 5f, at, style = Stroke(width = 2f))
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            InkButton(Glyphs.zoomIn, stringResource(R.string.cd_zoom_in), label = stringResource(R.string.cd_zoom_in)) { scale = (scale * 1.25f).coerceAtMost(6f) }
            InkButton(Glyphs.zoomOut, stringResource(R.string.cd_zoom_out), label = stringResource(R.string.cd_zoom_out)) { scale = (scale / 1.25f).coerceAtLeast(0.6f) }
            InkButton(Glyphs.center, stringResource(R.string.cd_center), label = stringResource(R.string.cd_center)) {
                scale = 1f
                pan = Offset.Zero
            }
        }
    }
}

private fun project(plots: List<Plot>, lat: Double, lng: Double, w: Float, h: Float, scale: Float, pan: Offset): Offset {
    if (plots.isEmpty()) return Offset(w / 2f, h / 2f)
    val minLat = plots.minOf { it.lat }
    val maxLat = plots.maxOf { it.lat }
    val minLng = plots.minOf { it.lng }
    val maxLng = plots.maxOf { it.lng }
    val latSpan = (maxLat - minLat).takeIf { it > 1e-6 } ?: 0.002
    val lngSpan = (maxLng - minLng).takeIf { it > 1e-6 } ?: 0.002
    val nx = ((lng - minLng) / lngSpan).toFloat()
    val ny = ((maxLat - lat) / latSpan).toFloat()
    val cx = w / 2f
    val cy = h / 2f
    val x = cx + (nx - 0.5f) * w * 0.72f * scale + pan.x
    val y = cy + (ny - 0.5f) * h * 0.72f * scale + pan.y
    return Offset(x, y)
}

private fun hitTest(plots: List<Plot>, tap: Offset, w: Float, h: Float, scale: Float, pan: Offset): String? {
    var best: String? = null
    var bestD = 40f * 40f
    plots.forEach { plot ->
        val at = project(plots, plot.lat, plot.lng, w, h, scale, pan)
        val dx = at.x - tap.x
        val dy = at.y - tap.y
        val d = dx * dx + dy * dy
        if (d < bestD) {
            bestD = d
            best = plot.key
        }
    }
    return best
}
