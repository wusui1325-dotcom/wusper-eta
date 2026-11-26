package com.wusper.eta.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
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
import com.wusper.eta.data.metroSvg
import com.wusper.eta.data.planMetro
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val net = network
            if (net != null) {
                MetroSvg(
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
private fun MetroSvg(
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
    val doc = remember(network, lang) { parseSvg(metroSvg(network, lang)) }
    val measurer = rememberTextMeasurer()
    var vx by remember(doc) { mutableFloatStateOf(doc?.minX ?: 0f) }
    var vy by remember(doc) { mutableFloatStateOf(doc?.minY ?: 0f) }
    var vw by remember(doc) { mutableFloatStateOf(doc?.width ?: 1f) }
    var vh by remember(doc) { mutableFloatStateOf(doc?.height ?: 1f) }
    val ink = Ink.black
    val paper = Ink.paper
    val label = TextStyle(color = ink, fontSize = 12.sp)
    if (doc == null) return
    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(doc, network) {
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
                            if (span > 8f && now > 8f) {
                                val factor = (span / now).coerceIn(0.5f, 2f)
                                val mid = Offset(
                                    (pressed[0].position.x + pressed[1].position.x) / 2f,
                                    (pressed[0].position.y + pressed[1].position.y) / 2f,
                                )
                                val anchor = toSvg(mid, size.width.toFloat(), size.height.toFloat(), vx, vy, vw, vh)
                                val nextW = (vw * factor).coerceIn(doc.width * 0.05f, doc.width * 6f)
                                val nextH = (vh * factor).coerceIn(doc.height * 0.05f, doc.height * 6f)
                                vx = anchor.x - mid.x / size.width * nextW
                                vy = anchor.y - mid.y / size.height * nextH
                                vw = nextW
                                vh = nextH
                            }
                            span = now
                        } else {
                            val point = pressed[0].position
                            val delta = point - previous
                            if (delta.getDistance() > 5f) moved = true
                            if (moved && !pinch) {
                                val sx = vw / size.width.coerceAtLeast(1)
                                val sy = vh / size.height.coerceAtLeast(1)
                                vx -= delta.x * sx
                                vy -= delta.y * sy
                            }
                            previous = point
                            span = 0f
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (!moved && !pinch) {
                        val at = toSvg(down.position, size.width.toFloat(), size.height.toFloat(), vx, vy, vw, vh)
                        val hit = nearestStation(network, at, 22f * (vw / size.width.coerceAtLeast(1)))
                        if (hit != null) onStation(hit)
                    }
                }
            },
        ) {
            val width = size.width
            val height = size.height
            fun screen(x: Float, y: Float) = toScreen(x, y, width, height, vx, vy, vw, vh)
            for (line in doc.lines) {
                val thick = line.id == emphasis || ride?.segments?.any { it.lineId == line.id } == true
                for (i in 0 until line.points.lastIndex) {
                    drawLine(ink, screen(line.points[i].x, line.points[i].y), screen(line.points[i + 1].x, line.points[i + 1].y), strokeWidth = if (thick) 7f else 3.2f)
                }
            }
            for (dot in doc.dots) {
                val at = screen(dot.x, dot.y)
                val chosen = dot.name == start || dot.name == end || dot.name == picked
                drawCircle(if (chosen) ink else paper, radius = if (chosen) 7f else dot.r, center = at)
                drawCircle(ink, radius = if (chosen) 7f else dot.r, center = at, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f))
            }
            if (doc.labels.size <= 48 || vw < doc.width * 0.72f) {
                for (labelAt in doc.labels) {
                    val at = screen(labelAt.x, labelAt.y)
                    drawText(measurer.measure(labelAt.text, label), topLeft = at)
                }
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            InkButton(Glyphs.zoomIn, stringResource(R.string.cd_zoom_in), label = stringResource(R.string.cd_zoom_in)) {
                zoom(doc, vx, vy, vw, vh, 0.8f) { x, y, w, h -> vx = x; vy = y; vw = w; vh = h }
            }
            Spacer(Modifier.height(4.dp))
            InkButton(Glyphs.zoomOut, stringResource(R.string.cd_zoom_out), label = stringResource(R.string.cd_zoom_out)) {
                zoom(doc, vx, vy, vw, vh, 1.25f) { x, y, w, h -> vx = x; vy = y; vw = w; vh = h }
            }
        }
    }
}

private fun zoom(
    doc: SvgDoc,
    vx: Float,
    vy: Float,
    vw: Float,
    vh: Float,
    factor: Float,
    apply: (Float, Float, Float, Float) -> Unit,
) {
    val nextW = (vw * factor).coerceIn(doc.width * 0.05f, doc.width * 6f)
    val nextH = (vh * factor).coerceIn(doc.height * 0.05f, doc.height * 6f)
    val cx = vx + vw / 2f
    val cy = vy + vh / 2f
    apply(cx - nextW / 2f, cy - nextH / 2f, nextW, nextH)
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

private data class SvgDoc(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float,
    val lines: List<SvgLine>,
    val dots: List<SvgDot>,
    val labels: List<SvgLabel>,
)

private data class SvgLine(val id: String, val points: List<Offset>)
private data class SvgDot(val name: String, val x: Float, val y: Float, val r: Float)
private data class SvgLabel(val text: String, val x: Float, val y: Float)

private fun parseSvg(svg: String): SvgDoc? {
    if (!svg.contains("<svg")) return null
    val box = Regex("""viewBox="([^"]+)"""").find(svg)?.groupValues?.get(1)?.split(Regex("\\s+")) ?: return null
    if (box.size < 4) return null
    val minX = box[0].toFloatOrNull() ?: return null
    val minY = box[1].toFloatOrNull() ?: return null
    val width = box[2].toFloatOrNull() ?: return null
    val height = box[3].toFloatOrNull() ?: return null
    val lines = Regex("""<polyline data-line="([^"]*)" points="([^"]*)"""").findAll(svg).map { match ->
        val pts = match.groupValues[2].split(" ").mapNotNull { pair ->
            val xy = pair.split(",")
            if (xy.size != 2) null else {
                val x = xy[0].toFloatOrNull() ?: return@mapNotNull null
                val y = xy[1].toFloatOrNull() ?: return@mapNotNull null
                Offset(x, y)
            }
        }
        SvgLine(unescape(match.groupValues[1]), pts)
    }.filter { it.points.size >= 2 }.toList()
    val dots = Regex("""<circle data-station="([^"]*)" cx="([^"]+)" cy="([^"]+)" r="([^"]+)"""").findAll(svg).map { match ->
        SvgDot(
            unescape(match.groupValues[1]),
            match.groupValues[2].toFloatOrNull() ?: 0f,
            match.groupValues[3].toFloatOrNull() ?: 0f,
            match.groupValues[4].toFloatOrNull() ?: 5f,
        )
    }.toList()
    val labels = Regex("""<text x="([^"]+)" y="([^"]+)"[^>]*>([^<]*)</text>""").findAll(svg).map { match ->
        SvgLabel(
            unescape(match.groupValues[3]),
            match.groupValues[1].toFloatOrNull() ?: 0f,
            match.groupValues[2].toFloatOrNull() ?: 0f,
        )
    }.toList()
    if (lines.isEmpty() && dots.isEmpty()) return null
    return SvgDoc(minX, minY, width, height, lines, dots, labels)
}

private fun unescape(value: String) = value.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

private fun toScreen(x: Float, y: Float, width: Float, height: Float, vx: Float, vy: Float, vw: Float, vh: Float): Offset {
    return Offset((x - vx) / vw * width, (y - vy) / vh * height)
}

private fun toSvg(point: Offset, width: Float, height: Float, vx: Float, vy: Float, vw: Float, vh: Float): Offset {
    return Offset(vx + point.x / width * vw, vy + point.y / height * vh)
}

private fun nearestStation(network: MetroNetwork, at: Offset, radius: Float): String? {
    var best = Float.MAX_VALUE
    var name: String? = null
    for (line in network.lines) {
        for (station in line.stations) {
            val x = station.x ?: continue
            val y = station.y ?: continue
            val dx = at.x - x.toFloat()
            val dy = at.y - y.toFloat()
            val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (dist < best) {
                best = dist
                name = station.name
            }
        }
    }
    return if (best <= radius) name else null
}
