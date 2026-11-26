package com.wusper.eta.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.MetroCity
import com.wusper.eta.data.MetroNetwork
import com.wusper.eta.data.MetroRide
import com.wusper.eta.data.MetroStore
import com.wusper.eta.data.RailFocus
import com.wusper.eta.data.UiPrefs
import com.wusper.eta.data.MetroBounds
import com.wusper.eta.data.isRasterImage
import com.wusper.eta.data.metroBounds
import com.wusper.eta.data.metroLabelUserSize
import com.wusper.eta.data.planMetro
import com.wusper.eta.data.uniformFit
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MetroCityPane(model: AppModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var cities by remember { mutableStateOf<List<MetroCity>>(emptyList()) }
    LaunchedEffect(Unit) {
        val local = withContext(Dispatchers.IO) { MetroStore.bundledCities(context) }
        cities = local
        val remote = withContext(Dispatchers.IO) { MetroStore.cities() }
        if (!remote.isNullOrEmpty()) cities = remote
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
            BasicText(stringResource(R.string.rail_nation), style = Ink.title, maxLines = 1)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize().padding(horizontal = 28.dp)) {
                items(cities, key = { it.id }, contentType = { "city" }) { city ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(remember { MutableInteractionSource() }, indication = null) {
                                model.openMetro(city.id, city.key, city.name)
                            }
                            .padding(top = 14.dp),
                    ) {
                        BasicText(city.name, style = Ink.title, maxLines = 1, modifier = Modifier.padding(bottom = 12.dp))
                        Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
                    }
                }
            }
        }
    }
}

@Composable
fun MetroMapPane(model: AppModel, page: RailFocus.Metro, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lang = remember { UiPrefs.language(context) }
    var network by remember(page.id) { mutableStateOf<MetroNetwork?>(null) }
    var poster by remember(page.id) { mutableStateOf<ImageBitmap?>(null) }
    var planning by remember(page.id) { mutableStateOf(false) }
    var start by remember(page.id) { mutableStateOf<String?>(null) }
    var end by remember(page.id) { mutableStateOf<String?>(null) }
    var picked by remember(page.id) { mutableStateOf<String?>(null) }
    var emphasis by remember(page.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(page.id, page.key) {
        val now = System.currentTimeMillis()
        val cached = withContext(Dispatchers.IO) { MetroStore.read(context, page.id, page.key, now) }
        if (cached != null) {
            network = cached
            return@LaunchedEffect
        }
        val fresh = withContext(Dispatchers.IO) { MetroStore.fetch(page.id, page.key) }
        if (fresh != null) {
            withContext(Dispatchers.IO) { MetroStore.write(context, fresh, System.currentTimeMillis()) }
            network = fresh
        } else {
            network = null
        }
    }
    LaunchedEffect(page.id, page.key) {
        val spec = withContext(Dispatchers.IO) { MetroStore.poster(context, page.id) } ?: return@LaunchedEffect
        val assetName = spec.asset
        if (assetName != null) {
            val bytes = withContext(Dispatchers.IO) {
                context.assets.open(assetName).use { it.readBytes() }
            }
            poster = decodePoster(bytes)
            return@LaunchedEffect
        }
        val url = spec.url ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        val cached = withContext(Dispatchers.IO) { MetroStore.readPoster(context, page.id, page.key, now) }
        if (cached != null) {
            poster = decodePoster(cached)
            return@LaunchedEffect
        }
        val fresh = withContext(Dispatchers.IO) { MetroStore.fetchPoster(url) }
        if (fresh != null) {
            withContext(Dispatchers.IO) { MetroStore.writePoster(context, page.id, page.key, fresh, System.currentTimeMillis()) }
            poster = decodePoster(fresh)
        }
    }
    val ride = remember(network, start, end, planning) {
        val net = network
        val from = start
        val to = end
        if (!planning || net == null || from == null || to == null) null else planMetro(net, from, to)
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton(Glyphs.back, stringResource(R.string.label_back), label = stringResource(R.string.label_back)) {
                model.closeRail()
            }
            Spacer(Modifier.width(8.dp))
            BasicText(page.name, style = Ink.title, maxLines = 1, modifier = Modifier.weight(1f))
            InkButton(
                Glyphs.locate,
                stringResource(R.string.metro_browse),
                selected = !planning,
                label = stringResource(R.string.metro_browse),
            ) {
                planning = false
                start = null
                end = null
            }
            InkButton(
                Glyphs.plan,
                stringResource(R.string.metro_plan),
                selected = planning,
                label = stringResource(R.string.metro_plan),
            ) {
                planning = true
                picked = null
                emphasis = null
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF4F6F8))) {
            val art = poster
            val net = network
            if (art != null) {
                PosterArt(art, Modifier.fillMaxSize())
            } else if (net != null) {
                MetroSchematic(
                    net,
                    lang,
                    emphasis,
                    ride,
                    start,
                    end,
                    picked,
                    Modifier.fillMaxSize(),
                ) { name ->
                    if (!planning) {
                        picked = name
                        start = null
                        end = null
                    } else if (start == null || name == start) {
                        start = name
                        end = null
                    } else if (end == null) {
                        end = name
                    } else {
                        start = name
                        end = null
                    }
                }
            }
        }
        val info = network
        if (info != null && !planning && picked != null) {
            StationCard(info, lang, picked!!, emphasis) { lineId ->
                emphasis = if (emphasis == lineId) null else lineId
            }
        } else if (info != null && planning && start != null && end != null && ride != null) {
            RideCard(info, lang, ride)
        }
    }
}

@Composable
private fun MetroSchematic(
    network: MetroNetwork,
    lang: String,
    emphasis: String?,
    ride: MetroRide?,
    start: String?,
    end: String?,
    picked: String?,
    modifier: Modifier,
    onStation: (String) -> Unit,
) {
    val bounds = remember(network) { metroBounds(network) } ?: return
    val measurer = rememberTextMeasurer()
    var scale by remember(bounds) { mutableFloatStateOf(1f) }
    var pan by remember(bounds) { mutableStateOf(Offset.Zero) }
    val pickStation by rememberUpdatedState(onStation)
    val labels = remember(network, lang) {
        val seen = HashSet<String>()
        buildList {
            for (line in network.lines) {
                for (station in line.stations) {
                    if (station.x == null || station.y == null) continue
                    if (!seen.add(station.name)) continue
                    var hash = 0
                    for (ch in station.name) hash = (hash * 31 + ch.code) and 0x7fffffff
                    val text = station.names.pick(lang).ifBlank { station.name }
                    add(Triple(station.name, hash, text))
                }
            }
        }
    }
    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(network, bounds) {
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
                        val fit = uniformFit(size.width.toFloat(), size.height.toFloat(), bounds.width, bounds.height)
                        if (pressed.size >= 2) {
                            pinch = true
                            val now = (pressed[0].position - pressed[1].position).getDistance()
                            if (span > 8f && now > 8f) {
                                val factor = (span / now).coerceIn(0.5f, 2f)
                                val mid = Offset(
                                    (pressed[0].position.x + pressed[1].position.x) / 2f,
                                    (pressed[0].position.y + pressed[1].position.y) / 2f,
                                )
                                val before = schematicPoint(mid, size.width.toFloat(), size.height.toFloat(), bounds, scale, pan)
                                scale = (scale * (1f / factor)).coerceIn(1f, 8f)
                                val after = schematicPoint(mid, size.width.toFloat(), size.height.toFloat(), bounds, scale, pan)
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
                        val at = schematicPoint(down.position, size.width.toFloat(), size.height.toFloat(), bounds, scale, pan)
                        val fit = uniformFit(size.width.toFloat(), size.height.toFloat(), bounds.width, bounds.height)
                        val radius = 28f / (fit * scale).coerceAtLeast(0.001f)
                        val hit = nearestSchematic(network, at, radius)
                        if (hit != null) pickStation(hit)
                    }
                }
            },
        ) {
            drawRect(Color(0xFFF4F6F8))
            val fit = uniformFit(size.width, size.height, bounds.width, bounds.height)
            fun at(x: Float, y: Float): Offset {
                val ox = (size.width - bounds.width * fit) / 2f + pan.x
                val oy = (size.height - bounds.height * fit) / 2f + pan.y
                return Offset(ox + (x - bounds.minX) * fit * scale, oy + (y - bounds.minY) * fit * scale)
            }
            for (line in network.lines) {
                val pts = if (line.path.size >= 2) {
                    line.path
                } else {
                    line.stations.mapNotNull { station ->
                        if (station.x == null || station.y == null) null else station.x to station.y
                    }
                }
                if (pts.size < 2) continue
                val thick = line.id == emphasis || ride?.segments?.any { it.lineId == line.id } == true
                val user = if (thick) 10f else 6f
                val stroke = (user * fit * scale).coerceAtLeast(if (thick) 3.2f else 2.2f)
                val color = parseHex(line.color)
                for (i in 0 until pts.lastIndex) {
                    drawLine(
                        color,
                        at(pts[i].first.toFloat(), pts[i].second.toFloat()),
                        at(pts[i + 1].first.toFloat(), pts[i + 1].second.toFloat()),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
            val seen = HashSet<String>()
            for (line in network.lines) {
                for (station in line.stations) {
                    val x = station.x ?: continue
                    val y = station.y ?: continue
                    if (!seen.add(station.name)) continue
                    val chosen = station.name == start || station.name == end || station.name == picked
                    val radius = ((if (station.transfer) 7f else 5f) * fit * scale).coerceAtLeast(if (chosen) 6f else 3f)
                    val center = at(x.toFloat(), y.toFloat())
                    drawCircle(if (chosen) Color(0xFF111111) else Color.White, radius, center)
                    drawCircle(Color(0xFF111111), radius, center, style = Stroke(1.6f))
                }
            }
            val fontPx = (metroLabelUserSize(bounds.width, scale) * fit * scale).coerceAtLeast(10f)
            val dark = TextStyle(color = Color(0xFF111111), fontSize = fontPx.toSp(), fontWeight = FontWeight.Bold)
            val halo = TextStyle(color = Color.White, fontSize = fontPx.toSp(), fontWeight = FontWeight.Bold)
            val worldPerPx = 1f / (fit * scale).coerceAtLeast(0.001f)
            val baseOff = maxOf(5f, 7f * worldPerPx)
            for ((name, hash, text) in labels) {
                val station = network.lines.firstNotNullOfOrNull { line ->
                    line.stations.firstOrNull { it.name == name && it.x != null && it.y != null }
                } ?: continue
                val nudgeX = ((hash % 5) - 2) * worldPerPx * 0.8f
                val nudgeY = (((hash shr 3) % 5) - 2) * worldPerPx * 0.8f
                val origin = at(
                    station.x!!.toFloat() + baseOff * 0.6f + nudgeX,
                    station.y!!.toFloat() - baseOff * 0.45f + nudgeY,
                )
                val light = measurer.measure(text, halo)
                val ink = measurer.measure(text, dark)
                for (shift in arrayOf(Offset(-1f, 0f), Offset(1f, 0f), Offset(0f, -1f), Offset(0f, 1f))) {
                    drawText(light, topLeft = origin + shift)
                }
                drawText(ink, topLeft = origin)
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

private fun schematicPoint(point: Offset, viewW: Float, viewH: Float, bounds: MetroBounds, scale: Float, pan: Offset): Offset {
    val fit = uniformFit(viewW, viewH, bounds.width, bounds.height)
    val ox = (viewW - bounds.width * fit) / 2f + pan.x
    val oy = (viewH - bounds.height * fit) / 2f + pan.y
    return Offset(bounds.minX + (point.x - ox) / (fit * scale), bounds.minY + (point.y - oy) / (fit * scale))
}

private fun nearestSchematic(network: MetroNetwork, at: Offset, radius: Float): String? {
    var best = Float.MAX_VALUE
    var name: String? = null
    for (line in network.lines) {
        for (station in line.stations) {
            val x = station.x ?: continue
            val y = station.y ?: continue
            val dist = hypot((at.x - x.toFloat()).toDouble(), (at.y - y.toFloat()).toDouble()).toFloat()
            if (dist < best) {
                best = dist
                name = station.name
            }
        }
    }
    return if (best <= radius) name else null
}

private fun parseHex(raw: String): Color {
    val hex = raw.trim().removePrefix("#")
    val body = when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("")
        6 -> hex
        8 -> hex
        else -> return Color(0xFF5B7CFA)
    }
    val value = body.toLongOrNull(16) ?: return Color(0xFF5B7CFA)
    return if (body.length == 8) Color(value) else Color(0xFF000000 or value)
}

@Composable
private fun PosterArt(image: ImageBitmap, modifier: Modifier) {
    var scale by remember(image) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var pan by remember(image) { mutableStateOf(Offset.Zero) }
    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(image) {
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
                                val before = posterPoint(mid, size.width.toFloat(), size.height.toFloat(), image.width, image.height, scale, pan)
                                scale = (scale * (1f / factor)).coerceIn(1f, 8f)
                                val after = posterPoint(mid, size.width.toFloat(), size.height.toFloat(), image.width, image.height, scale, pan)
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
    }
}

private fun posterPoint(point: Offset, viewW: Float, viewH: Float, imgW: Int, imgH: Int, scale: Float, pan: Offset): Offset {
    val fit = uniformFit(viewW, viewH, imgW.toFloat(), imgH.toFloat())
    val ox = (viewW - imgW * fit) / 2f + pan.x
    val oy = (viewH - imgH * fit) / 2f + pan.y
    return Offset((point.x - ox) / (fit * scale), (point.y - oy) / (fit * scale))
}

private fun decodePoster(bytes: ByteArray): ImageBitmap? {
    if (!isRasterImage(bytes)) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > 3200 || bounds.outHeight / sample > 3200) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    return bitmap.asImageBitmap()
}

@Composable
private fun StationCard(
    network: MetroNetwork,
    lang: String,
    name: String,
    emphasis: String?,
    onLine: (String) -> Unit,
) {
    val rows = network.lines.mapNotNull { line ->
        val index = line.stations.indexOfFirst { it.name == name }
        if (index < 0) null else line to index
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        val shown = rows.firstOrNull()?.let { (line, index) -> line.stations[index].names.pick(lang) } ?: name
        BasicText(shown, style = Ink.title, maxLines = 1)
        rows.forEach { (line, index) ->
            val title = line.names.pick(lang)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onLine(line.id) }
                    .padding(top = 10.dp),
            ) {
                BasicText(title, style = if (emphasis == line.id) Ink.arrivalRoute else Ink.body, maxLines = 1)
                val prev = line.stations.getOrNull(index - 1)
                val next = line.stations.getOrNull(index + 1)
                if (prev != null) {
                    BasicText(stringResource(R.string.metro_towards, line.stations.first().names.pick(lang)), style = Ink.meta)
                    BasicText(stringResource(R.string.metro_next, prev.names.pick(lang)), style = Ink.body)
                }
                if (next != null) {
                    BasicText(stringResource(R.string.metro_towards, line.stations.last().names.pick(lang)), style = Ink.meta)
                    BasicText(stringResource(R.string.metro_next, next.names.pick(lang)), style = Ink.body)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(Ink.lineWidth)
                    .background(Ink.line),
            )
        }
    }
}

@Composable
private fun RideCard(network: MetroNetwork, lang: String, ride: MetroRide) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        BasicText(stringResource(R.string.metro_xfer, ride.transfers), style = Ink.meta)
        ride.segments.forEach { seg ->
            val line = network.lines.firstOrNull { it.id == seg.lineId }
            val title = line?.names?.pick(lang) ?: seg.lineName
            BasicText(title, style = Ink.arrivalRoute, maxLines = 1, modifier = Modifier.padding(top = 10.dp))
            val from = line?.stations?.firstOrNull { it.name == seg.from }?.names?.pick(lang) ?: seg.from
            val to = line?.stations?.firstOrNull { it.name == seg.to }?.names?.pick(lang) ?: seg.to
            BasicText("$from → $to", style = Ink.body, maxLines = 2)
            BasicText(stringResource(R.string.metro_stops, seg.path.size), style = Ink.meta)
        }
    }
}

