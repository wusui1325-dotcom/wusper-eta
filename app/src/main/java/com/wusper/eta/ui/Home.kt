package com.wusper.eta.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.EtaSlot
import com.wusper.eta.data.EtaState
import com.wusper.eta.data.Help
import com.wusper.eta.data.MtrLine
import com.wusper.eta.data.Route
import com.wusper.eta.data.RouteFocus
import com.wusper.eta.data.Stop

private data class Arrival(
    val key: String,
    val route: String,
    val dest: String,
    val stop: String,
    val state: EtaState,
    val etas: List<EtaSlot>,
    val help: Help?,
    val flip: Flip?,
    val focus: RouteFocus?,
    val meters: Int,
)

private sealed class Flip {
    data class Bus(val route: Route) : Flip()
    data class Mtr(val sta: String, val line: String) : Flip()
}

@Composable
fun FavoriteRoutes(model: AppModel) {
    val rows = remember(model.savedRoutes, model.favEta) {
        model.savedRoutes.map { fav ->
            val slots = model.favEta[fav.id]
            Arrival(
                key = fav.id,
                route = fav.title,
                dest = fav.dest,
                stop = fav.stopName,
                state = if (slots == null) EtaState.Loading else EtaState.Ready,
                etas = slots.orEmpty(),
                help = null,
                flip = null,
                focus = if (fav.line.isNotBlank()) {
                    RouteFocus.Mtr(fav.line, fav.title, fav.side)
                } else {
                    RouteFocus.Bus(fav.asRoute())
                },
                meters = 0,
            )
        }.byLiveEtaThenDistance()
    }
    ArrivalColumn {
        items(rows, key = { it.key }) { row ->
            ArrivalLine(row, {}, {}) { row.focus?.let { model.openDetail(it) } }
        }
    }
}

@Composable
fun BusHome(model: AppModel) {
    val rows = remember(model.stops, model.lat, model.lng, model.shownBound, model.boundState, model.boundEta) {
        busArrivals(model.stops, model.lat, model.lng, model.shownBound, model.boundState, model.boundEta)
    }
    ArrivalColumn {
        item {
            QuietHead { model.help = it }
        }
        if (model.failed && rows.isEmpty()) {
            item { FailLine { model.refresh() } }
        }
        items(rows, key = { it.key }) { row ->
            ArrivalLine(row, { model.help = it }, { flipOf(model, it) }) {
                row.focus?.let { model.openDetail(it) }
            }
        }
    }
}

@Composable
fun MtrHome(model: AppModel) {
    val rows = remember(model.mtr, model.lat, model.lng, model.mtrSide) {
        mtrArrivals(model.mtr, model.lat, model.lng, model.mtrSide)
    }
    ArrivalColumn {
        item {
            Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(stringResource(R.string.tab_mtr), style = Ink.meta)
                    Spacer(Modifier.width(6.dp))
                    HelpMark { model.help = Help.MtrGeo }
                }
            }
        }
        items(rows, key = { it.key }) { row ->
            ArrivalLine(row, { model.help = it }, { flipOf(model, it) }) {
                row.focus?.let { model.openDetail(it) }
            }
        }
    }
}

private fun flipOf(model: AppModel, flip: Flip) {
    when (flip) {
        is Flip.Bus -> model.flipBus(flip.route)
        is Flip.Mtr -> model.flipMtr(flip.sta, flip.line)
    }
}

private fun busArrivals(
    stops: List<Stop>,
    lat: Double,
    lng: Double,
    shown: Map<String, com.wusper.eta.data.BoundHit>,
    states: Map<String, EtaState>,
    etas: Map<String, List<EtaSlot>>,
): List<Arrival> {
    val ranked = stops
        .filter { it.co != "MTR" && it.routes.isNotEmpty() }
        .sortedBy { distanceOf(it.lat, it.lng, it.meters, lat, lng) }
    return ranked.flatMap { stop ->
        val meters = distanceOf(stop.lat, stop.lng, stop.meters, lat, lng)
        stop.routes.map { route ->
            val hit = shown[route.key]
            Arrival(
                key = route.key,
                route = route.route,
                dest = hit?.dest ?: route.dest,
                stop = hit?.stopName ?: stop.name,
                state = if (hit == null) route.state else states[route.key] ?: EtaState.Loading,
                etas = if (hit == null) route.etas else etas[route.key].orEmpty(),
                help = when {
                    route.co == "GMB" -> Help.Gmb
                    route.serviceType != "1" -> Help.Service
                    else -> null
                },
                flip = Flip.Bus(route),
                focus = RouteFocus.Bus(hit?.asRoute() ?: route),
                meters = meters,
            )
        }
    }.byLiveEtaThenDistance()
}

private fun distanceOf(lat: Double, lng: Double, fallback: Int, userLat: Double, userLng: Double): Int {
    if (lat != 0.0 && lng != 0.0 && userLat != 0.0 && userLng != 0.0) {
        return com.wusper.eta.data.haversineMeters(userLat, userLng, lat, lng).toInt()
    }
    return fallback
}

private fun mtrArrivals(
    stations: List<com.wusper.eta.data.MtrStop>,
    lat: Double,
    lng: Double,
    sides: Map<String, String>,
): List<Arrival> {
    return stations.sortedBy { distanceOf(it.lat, it.lng, it.meters, lat, lng) }.flatMap { station ->
        val meters = distanceOf(station.lat, station.lng, station.meters, lat, lng)
        if (station.lines.isEmpty()) {
            listOf(
                Arrival(
                    key = "${station.sta}-wait",
                    route = station.name,
                    dest = "",
                    stop = station.name,
                    state = EtaState.Loading,
                    etas = emptyList(),
                    help = null,
                    flip = null,
                    focus = null,
                    meters = meters,
                ),
            )
        } else {
            station.lines.map { line -> mtrLine(station.sta, station.name, line, sides, meters) }
        }
    }.byLiveEtaThenDistance()
}

private fun mtrLine(sta: String, stop: String, line: MtrLine, sides: Map<String, String>, meters: Int): Arrival {
    val preferred = com.wusper.eta.data.preferredMtrSide(line.up, line.down)
    var side = sides["$sta|${line.code}"] ?: com.wusper.eta.data.usefulMtrSide(line.code, sta, preferred)
    var trains = if (side == "down") line.down else line.up
    val heading = trains.firstOrNull()?.dest.orEmpty()
    if (heading.isNotBlank() && com.wusper.eta.data.atDestinationTerminus(stop, heading)) {
        val flipped = if (side == "down") "up" else "down"
        val other = if (flipped == "down") line.down else line.up
        val otherDest = other.firstOrNull()?.dest.orEmpty()
        if (otherDest.isBlank() || !com.wusper.eta.data.atDestinationTerminus(stop, otherDest)) {
            side = flipped
            trains = other
        }
    }
    val waiting = line.up.isEmpty() && line.down.isEmpty()
    return Arrival(
        key = "$sta-${line.code}",
        route = line.name,
        dest = trains.firstOrNull()?.dest.orEmpty(),
        stop = stop,
        state = if (waiting) EtaState.Loading else EtaState.Ready,
        etas = trains.map { EtaSlot(it.minutes, it.scheduled) },
        help = null,
        flip = Flip.Mtr(sta, line.code),
        focus = RouteFocus.Mtr(line.code, line.name, side),
        meters = meters,
    )
}

private fun List<Arrival>.byLiveEtaThenDistance(): List<Arrival> =
    sortedWith(compareBy({ if (it.noDeparture) 1 else 0 }, { it.meters }))

private val Arrival.noDeparture: Boolean
    get() = state == EtaState.Ready && etas.none { it.minutes != null }

@Composable
private fun ArrivalColumn(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            content = content,
        )
    }
}

@Composable
private fun QuietHead(onHelp: (Help) -> Unit) {
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(stringResource(R.string.tab_nearby), style = Ink.meta)
            Spacer(Modifier.width(6.dp))
            HelpMark { onHelp(Help.Nearby) }
        }
    }
}

@Composable
private fun ArrivalLine(row: Arrival, onHelp: (Help) -> Unit, onFlip: (Flip) -> Unit, onOpen: () -> Unit) {
    val bound = if (row.dest.isBlank()) "" else stringResource(R.string.bound_to, row.dest)
    val open = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(open, indication = null, onClick = onOpen),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(row.route, style = Ink.arrivalRoute, maxLines = 1, softWrap = false)
                    if (bound.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
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
                    if (row.flip != null) {
                        Spacer(Modifier.width(6.dp))
                        BoundButton { onFlip(row.flip) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        row.stop,
                        style = Ink.arrivalStop,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (row.help != null) {
                        Spacer(Modifier.width(6.dp))
                        HelpMark { onHelp(row.help) }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            MinuteCluster(row.state, row.etas, onHelp)
        }
        Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
    }
}

@Composable
private fun BoundButton(onClick: () -> Unit) {
    val cd = stringResource(R.string.cd_flip)
    val diameter = with(LocalDensity.current) { (32f * Ink.textScale).sp.toDp() }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = if (pressed) Ink.black else androidx.compose.ui.graphics.Color.Transparent
    val glyph = if (pressed) Ink.paper else Ink.black
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(fill)
            .border(Ink.lineWidth, Ink.black, CircleShape)
            .semantics {
                contentDescription = cd
                role = Role.Button
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(Glyphs.bothWays, null, glyph, diameter * 0.46f)
    }
}

@Composable
private fun MinuteCluster(state: EtaState, etas: List<EtaSlot>, onHelp: (Help) -> Unit) {
    val noneLabel = stringResource(R.string.none)
    val label = when (state) {
        EtaState.Loading, EtaState.Failed -> ""
        EtaState.Ready -> {
            val mins = etas.mapNotNull { it.minutes }
            if (mins.isEmpty()) noneLabel else mins.take(2).joinToString("  ") { "$it′" }
        }
    }
    val scheduled = state == EtaState.Ready && etas.any { it.scheduled && it.minutes != null }
    val none = state == EtaState.Ready && etas.none { it.minutes != null }
    if (Ink.reduceMotion) {
        EtaText(label, scheduled, none, onHelp)
    } else {
        AnimatedContent(label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "min") { text ->
            EtaText(text, scheduled, none, onHelp)
        }
    }
}

@Composable
private fun EtaText(text: String, scheduled: Boolean, none: Boolean, onHelp: (Help) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(text, style = Ink.arrivalEta)
        if (scheduled || none) {
            Spacer(Modifier.width(4.dp))
            HelpMark { onHelp(if (none) Help.None else Help.Scheduled) }
        }
    }
}

@Composable
internal fun FailLine(onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
        BasicText(stringResource(R.string.fail), style = Ink.body)
        InkButton(Glyphs.retry, stringResource(R.string.cd_retry), label = stringResource(R.string.label_retry), onClick = onRetry)
    }
}

