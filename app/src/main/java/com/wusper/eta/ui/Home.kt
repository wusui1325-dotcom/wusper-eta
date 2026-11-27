package com.wusper.eta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import com.wusper.eta.data.TrainBoard

private enum class Live { None, Bus, Fav }

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
    val icon: ImageVector,
    val live: Live = Live.None,
    val boardKey: String? = null,
)

private sealed class Flip {
    data class Bus(val route: Route) : Flip()
    data class Mtr(val sta: String, val line: String) : Flip()
}

private fun modeIcon(co: String): ImageVector = when (co) {
    "KMB" -> Glyphs.kmb
    "CTB" -> Glyphs.ctb
    "GMB" -> Glyphs.gmb
    "LRT" -> Glyphs.lightRail
    "MTR" -> Glyphs.mtr
    else -> Glyphs.bus
}

@Composable
fun FavoriteRoutes(model: AppModel) {
    val rows = remember(model.savedRoutes, model.favOrder) {
        model.savedRoutes.map { fav ->
            val slots = model.favPlain[fav.id]
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
                icon = if (fav.line.isNotBlank()) Glyphs.mtr else modeIcon(fav.co),
                live = Live.Fav,
            )
        }.byLiveEtaThenDistance()
    }
    ArrivalColumn {
        items(rows, key = { it.key }, contentType = { "arrival" }) { row ->
            ArrivalLine(row.shown(model), {}, {}) { row.focus?.let { model.openDetail(it) } }
        }
    }
}

@Composable
fun BusHome(model: AppModel) {
    val rows = remember(model.stops, model.lat, model.lng, model.shownBound, model.boundState, model.boundEta, model.etaOrder) {
        busArrivals(model.stops, model.lat, model.lng, model.shownBound, model.boundState, model.boundEta, model.statePlain, model.etaPlain)
    }
    ArrivalColumn {
        item {
            QuietHead { model.help = it }
        }
        if (model.failed && rows.isEmpty()) {
            item { FailLine { model.refresh() } }
        }
        items(rows, key = { it.key }, contentType = { "arrival" }) { row ->
            ArrivalLine(row.shown(model), { model.help = it }, { flipOf(model, it) }) {
                row.focus?.let { model.openDetail(it) }
            }
        }
    }
}

@Composable
fun MtrHome(model: AppModel) {
    val heavy = remember(model.mtr, model.lat, model.lng, model.mtrOrder) {
        mtrArrivals(model.mtr, model.lat, model.lng, model.sidePlain)
    }
    val lightRows = remember(model.lrt, model.lat, model.lng, model.shownBound, model.boundState, model.boundEta, model.etaOrder) {
        busArrivals(model.lrt, model.lat, model.lng, model.shownBound, model.boundState, model.boundEta, model.statePlain, model.etaPlain)
    }
    val rows = remember(heavy, lightRows) { (heavy + lightRows).byLiveEtaThenDistance() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkButton(Glyphs.map, stringResource(R.string.rail_pair), label = stringResource(R.string.rail_pair)) {
                    model.openLocalMap()
                }
                Spacer(Modifier.width(8.dp))
                InkButton(Glyphs.globe, stringResource(R.string.rail_nation), label = stringResource(R.string.rail_nation)) {
                    model.openNation()
                }
                Spacer(Modifier.weight(1f))
                HelpMark { model.help = Help.MtrGeo }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            ArrivalColumn {
                items(rows, key = { it.key }, contentType = { "arrival" }) { row ->
                    ArrivalLine(row.shown(model), { model.help = it }, { flipOf(model, it) }) {
                        row.focus?.let { model.openDetail(it) }
                    }
                }
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

private fun Arrival.shown(model: AppModel): Arrival {
    val boardKey = this.boardKey
    if (boardKey != null) {
        val board = model.mtrLive[boardKey] ?: return this
        return projectMtr(this, board, model.mtrSide[boardKey])
    }
    return when (live) {
        Live.Bus -> {
            val state = model.etaState[key] ?: return this
            overlay(state, model.etaSlots[key].orEmpty())
        }
        Live.Fav -> {
            val slots = model.favEta[key] ?: return this
            overlay(EtaState.Ready, slots)
        }
        Live.None -> this
    }
}

private fun Arrival.overlay(state: EtaState, etas: List<EtaSlot>): Arrival {
    return if (this.state == state && this.etas == etas) this else copy(state = state, etas = etas)
}

private fun projectMtr(row: Arrival, board: TrainBoard, side: String?): Arrival {
    val key = row.boardKey ?: return row
    val line = MtrLine(key.substringAfter('|'), row.route, board.up, board.down)
    val sides = if (side == null) emptyMap() else mapOf(key to side)
    val next = mtrLine(key.substringBefore('|'), row.stop, line, sides, row.meters).copy(boardKey = key)
    return if (next.dest == row.dest && next.state == row.state && next.etas == row.etas && next.focus == row.focus) row else next
}

private fun busArrivals(
    stops: List<Stop>,
    lat: Double,
    lng: Double,
    shown: Map<String, com.wusper.eta.data.BoundHit>,
    states: Map<String, EtaState>,
    etas: Map<String, List<EtaSlot>>,
    liveState: Map<String, EtaState>,
    liveEta: Map<String, List<EtaSlot>>,
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
                state = if (hit != null) {
                    states[route.key] ?: EtaState.Loading
                } else {
                    liveState[route.key] ?: route.state
                },
                etas = if (hit != null) etas[route.key].orEmpty() else liveEta[route.key] ?: route.etas,
                help = when {
                    route.co == "GMB" -> Help.Gmb
                    route.serviceType != "1" -> Help.Service
                    else -> null
                },
                flip = Flip.Bus(route),
                focus = RouteFocus.Bus(hit?.asRoute() ?: route),
                meters = meters,
                icon = modeIcon(route.co),
                live = if (hit == null) Live.Bus else Live.None,
            )
        }
    }.distinctBy { it.key }.byLiveEtaThenDistance()
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
                    icon = Glyphs.mtr,
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
        icon = Glyphs.mtr,
        boardKey = "$sta|${line.code}",
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
            state = androidx.compose.foundation.lazy.rememberLazyListState(),
            contentPadding = PaddingValues(bottom = 12.dp),
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            content = content,
        )
    }
}

@Composable
private fun QuietHead(onHelp: (Help) -> Unit) {
    Row(
        Modifier.padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Glyphs.nearby, null, Ink.mute, 15.dp)
        Spacer(Modifier.width(6.dp))
        BasicText(stringResource(R.string.tab_nearby), style = Ink.meta)
        Spacer(Modifier.width(6.dp))
        HelpMark { onHelp(Help.Nearby) }
    }
}

@Composable
private fun ArrivalLine(row: Arrival, onHelp: (Help) -> Unit, onFlip: (Flip) -> Unit, onOpen: () -> Unit) {
    val bound = if (row.dest.isBlank()) "" else stringResource(R.string.bound_to, row.dest)
    val open = remember { MutableInteractionSource() }
    val pressed by open.collectIsPressedAsState()
    val cardShape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(cardShape)
            .background(if (pressed) Ink.surface else Ink.wash)
            .border(Ink.lineWidth, Ink.line, cardShape)
            .clickable(open, indication = null, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Ink.black)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(row.icon, null, Ink.paper, 14.dp)
                        Spacer(Modifier.width(5.dp))
                        BasicText(
                            row.route,
                            style = Ink.route.copy(color = Ink.paper, fontSize = (17f * Ink.textScale).sp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (bound.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
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
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Glyphs.stopPole, null, Ink.mute, 13.dp)
                    Spacer(Modifier.width(5.dp))
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
            Spacer(Modifier.width(10.dp))
            MinuteCluster(row.state, row.etas, onHelp)
        }
    }
}

@Composable
private fun BoundButton(onClick: () -> Unit) {
    val cd = stringResource(R.string.cd_flip)
    val diameter = with(LocalDensity.current) { (30f * Ink.textScale).sp.toDp() }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = if (pressed) Ink.black else Ink.paper
    val glyph = if (pressed) Ink.paper else Ink.black
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(fill)
            .border(Ink.lineWidth, if (pressed) Ink.black else Ink.line, CircleShape)
            .semantics {
                contentDescription = cd
                role = Role.Button
            }
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(Glyphs.bothWays, null, glyph, diameter * 0.52f)
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
    EtaText(label, scheduled, none, onHelp)
}

@Composable
private fun EtaText(text: String, scheduled: Boolean, none: Boolean, onHelp: (Help) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (none) {
            StreamlinedPill(text)
        } else if (text.isNotEmpty()) {
            BasicText(text, style = Ink.arrivalEta.copy(fontWeight = FontWeight.Bold))
        }
        if (scheduled || none) {
            Spacer(Modifier.width(4.dp))
            HelpMark { onHelp(if (none) Help.None else Help.Scheduled) }
        }
    }
}

@Composable
internal fun FailLine(onRetry: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(shape)
            .background(Ink.wash)
            .border(Ink.lineWidth, Ink.line, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Glyph(Glyphs.info, null, Ink.black, 16.dp)
        Spacer(Modifier.width(8.dp))
        BasicText(stringResource(R.string.fail), style = Ink.body, modifier = Modifier.weight(1f))
        InkButton(Glyphs.retry, stringResource(R.string.cd_retry), label = stringResource(R.string.label_retry), onClick = onRetry)
    }
}
