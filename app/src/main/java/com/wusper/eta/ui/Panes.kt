package com.wusper.eta.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.Filter
import com.wusper.eta.data.Help
import com.wusper.eta.data.MtrLine
import com.wusper.eta.data.MtrStop
import com.wusper.eta.data.RideLeg
import com.wusper.eta.data.Route
import com.wusper.eta.data.SavedStop
import com.wusper.eta.data.Stop
import com.wusper.eta.data.TripPlan
import com.wusper.eta.data.accepts
import com.wusper.eta.data.formatMeters
import com.wusper.eta.data.toSaved

@Composable
fun NearbyPane(model: AppModel, inline: Boolean) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.locateGranted()
    }
    Column(Modifier.fillMaxSize()) {
        Bar(
            icon = Glyphs.nearby,
            title = stringResource(R.string.tab_nearby),
            help = Help.Nearby,
            onHelp = { model.help = it },
        ) {
            InkButton(Glyphs.locate, stringResource(R.string.cd_locate)) {
                if (model.permitted(context)) model.locateGranted() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            Spacer(Modifier.width(6.dp))
            InkButton(Glyphs.radiusIn, stringResource(R.string.cd_radius_in)) { model.radiusIn() }
            Spacer(Modifier.width(6.dp))
            InkButton(Glyphs.radiusOut, stringResource(R.string.cd_radius_out)) { model.radiusOut() }
            Spacer(Modifier.width(6.dp))
            InkButton(Glyphs.refresh, stringResource(R.string.cd_refresh)) { model.refresh() }
        }
        Filters(model)
        val shown = visibleStops(model)
        val open = shown.firstOrNull { it.key == model.openKey }
        val rest = shown.filter { it.key != model.openKey }
        val mtr = if (model.filter == Filter.All || model.filter == Filter.Mtr) model.mtr else emptyList()
        LazyColumn(Modifier.weight(1f)) {
            if (model.failed && shown.isEmpty()) {
                item { FailRow { model.refresh() } }
            }
            if (inline && open != null) {
                item(key = "board-${open.key}") { StopBoard(open, model, fav = true) }
            }
            if (inline) {
                items(mtr, key = { "m-${it.sta}" }) { station -> MtrBoard(station) { model.help = it } }
            } else {
                items(mtr, key = { "mc-${it.sta}" }) { station ->
                    CompactMtr(station) { model.openMtr(station) }
                }
            }
            items(if (inline) rest else shown, key = { it.key }) { stop ->
                CompactStop(stop, selected = stop.key == model.openKey) { model.open(stop.key) }
            }
        }
    }
}

@Composable
fun BoardPane(model: AppModel) {
    val stop = model.openStop
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Bar(Glyphs.stopPole, stop?.name ?: stringResource(R.string.sec_stops), Help.Minutes, { model.help = it }) {
                if (stop != null) {
                    val on = model.fav.any { it.key == stop.key }
                    InkButton(if (on) Glyphs.favOn else Glyphs.saved, stringResource(if (on) R.string.cd_fav_on else R.string.cd_fav_off)) {
                        model.toggleFav(stop)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                InkButton(Glyphs.refresh, stringResource(R.string.cd_refresh)) { model.refresh() }
            }
        }
        if (stop != null) {
            item { StopBody(stop) { model.help = it } }
        }
        items(model.mtr, key = { "b-${it.sta}" }) { station -> MtrBoard(station) { model.help = it } }
    }
}

@Composable
fun SearchPane(model: AppModel) {
    val routeMode = model.searchMode == com.wusper.eta.data.SearchMode.Route
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchModeText(stringResource(R.string.search_route), routeMode) {
                model.chooseSearch(com.wusper.eta.data.SearchMode.Route)
            }
            Spacer(Modifier.width(18.dp))
            SearchModeText(stringResource(R.string.search_stop), !routeMode) {
                model.chooseSearch(com.wusper.eta.data.SearchMode.StopName)
            }
        }
        Box(Modifier.padding(horizontal = 28.dp, vertical = 14.dp).fillMaxWidth()) {
            if (model.query.isEmpty()) {
                BasicText(
                    stringResource(if (routeMode) R.string.hint_route else R.string.hint_stop),
                    style = Ink.meta,
                )
            }
            BasicTextField(
                value = model.query,
                onValueChange = { model.onQuery(it) },
                textStyle = Ink.body,
                singleLine = true,
                cursorBrush = SolidColor(Ink.black),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.padding(horizontal = 28.dp).fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 28.dp)) {
            if (routeMode) {
                items(
                    model.foundRoutes,
                    key = { route -> "${route.key}|${route.dest}" },
                    contentType = { "route" },
                ) { route ->
                    SearchRouteRow(route) { model.openRoute(route) }
                }
            } else {
                items(model.foundStops, key = { "fs-${it.key}" }, contentType = { "stop" }) { stop ->
                    NameRow(stop.name, stop.nameEn) { model.openStop(stop) }
                }
                items(model.foundMtr, key = { "fm-${it.sta}" }, contentType = { "mtr" }) { station ->
                    NameRow(station.name, station.nameEn) { model.openMtr(station) }
                }
            }
        }
    }
}

@Composable
private fun SearchModeText(label: String, selected: Boolean, onClick: () -> Unit) {
    BasicText(
        label,
        style = if (selected) Ink.title else Ink.meta,
        modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
    )
}

@Composable
private fun SearchRouteRow(route: Route, onClick: () -> Unit) {
    val bound = if (route.dest.isBlank()) "" else stringResource(R.string.bound_to, route.dest)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(top = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(route.route, style = Ink.route, maxLines = 1, softWrap = false)
            if (bound.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                BasicText(
                    bound,
                    style = Ink.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
    }
}

@Composable
private fun NameRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(top = 14.dp),
    ) {
        BasicText(title, style = Ink.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank() && !subtitle.equals(title, ignoreCase = true)) {
            BasicText(subtitle, style = Ink.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
    }
}

@Composable
fun SavedPane(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        if (model.savedRoutes.isNotEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { FavoriteRoutes(model) }
        }
        if (model.recent.isNotEmpty()) {
            LazyColumn(Modifier.then(if (model.savedRoutes.isEmpty()) Modifier.weight(1f) else Modifier.heightIn(max = 240.dp))) {
                item { SectionPad(Glyphs.recent, stringResource(R.string.sec_recent)) }
                items(model.recent, key = { "rc-${it.key}" }) { saved ->
                    SavedRow(saved) { model.openStop(saved.asStop()) }
                }
            }
        }
    }
}

@Composable
fun NavPane(model: AppModel) {
    val ready = model.origin != null && model.dest != null && model.navHits.isEmpty()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxHeight()) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 8.dp)) {
                NavField(
                    label = stringResource(R.string.origin),
                    value = model.originText,
                    chosen = model.origin != null,
                    onFocus = { model.focusNav("o") },
                    onValue = { model.editNav("o", it) },
                )
                Spacer(Modifier.height(16.dp))
                NavField(
                    label = stringResource(R.string.dest),
                    value = model.destText,
                    chosen = model.dest != null,
                    onFocus = { model.focusNav("d") },
                    onValue = { model.editNav("d", it) },
                )
                if (ready) {
                    Spacer(Modifier.height(8.dp))
                    InkButton(
                        Glyphs.go,
                        stringResource(R.string.nav_confirm),
                        label = stringResource(R.string.nav_confirm),
                    ) { model.confirmNav() }
                }
            }
            val plan = model.plan
            when {
                model.navHits.isNotEmpty() -> {
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 28.dp)) {
                        itemsIndexed(model.navHits, key = { index, hit -> "$index|${hit.name}|${hit.lat}|${hit.lng}" }, contentType = { _, _ -> "stop" }) { _, hit ->
                            NameRow(hit.name, hit.nameEn) { model.pickNav(hit) }
                        }
                    }
                }
                model.planFailed -> FailRow { model.confirmNav() }
                model.planUnavailable || plan?.mock == true -> {
                    PlanUnavailable { model.help = Help.Mock }
                }
                plan != null -> RideResult(plan, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavField(
    label: String,
    value: String,
    chosen: Boolean,
    onFocus: () -> Unit,
    onValue: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = Ink.meta, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) {
            InkButton(Glyphs.close, stringResource(R.string.cd_clear), label = stringResource(R.string.cd_clear)) {
                onValue("")
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) BasicText(label, style = Ink.meta)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            textStyle = if (chosen) Ink.body else Ink.body,
            singleLine = true,
            cursorBrush = SolidColor(Ink.black),
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(if (chosen) Ink.black else Ink.line))
}

@Composable
private fun PlanUnavailable(onHelp: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(stringResource(R.string.plan_none), style = Ink.title)
        Spacer(Modifier.width(6.dp))
        HelpMark(onHelp)
    }
}

@Composable
private fun RideResult(plan: TripPlan, modifier: Modifier) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 8.dp),
    ) {
        val range = if (plan.minutes == plan.minutesMax) {
            stringResource(R.string.minutes_n, plan.minutes)
        } else {
            stringResource(R.string.nav_range, plan.minutes, plan.minutesMax)
        }
        BasicText(range, style = Ink.display)
        Spacer(Modifier.height(12.dp))
        plan.rides.forEach { ride ->
            RideRow(ride)
        }
        if (plan.walkMinutes > 0) {
            Spacer(Modifier.height(12.dp))
            BasicText(stringResource(R.string.nav_walk_min, plan.walkMinutes), style = Ink.meta)
        }
    }
}

@Composable
private fun RideRow(ride: RideLeg) {
    val kind = when (ride.kind) {
        "gmb" -> stringResource(R.string.nav_gmb)
        "rail" -> stringResource(R.string.nav_rail)
        else -> stringResource(R.string.nav_bus)
    }
    val time = if (ride.minutes == ride.minutesMax) {
        stringResource(R.string.minutes_n, ride.minutes)
    } else {
        stringResource(R.string.nav_range, ride.minutes, ride.minutesMax)
    }
    val path = when {
        ride.board.isNotBlank() && ride.alight.isNotBlank() -> "${ride.board} → ${ride.alight}"
        ride.board.isNotBlank() -> ride.board
        else -> ride.alight
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(kind, style = Ink.meta)
            Spacer(Modifier.width(8.dp))
            BasicText(ride.name, style = Ink.route, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            BasicText(time, style = Ink.minute)
        }
        if (path.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            BasicText(path, style = Ink.body)
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
    }
}

@Composable
private fun Bar(
    icon: ImageVector,
    title: String,
    help: Help?,
    onHelp: (Help) -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, null, Ink.black, 18.dp)
        Spacer(Modifier.width(8.dp))
        BasicText(title, style = Ink.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (help != null) {
            HelpMark { onHelp(help) }
            Spacer(Modifier.width(6.dp))
        }
        actions()
    }
}

@Composable
private fun Filters(model: AppModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(Glyphs.all, stringResource(R.string.op_all), model.filter == Filter.All, null, { model.choose(Filter.All) }) {}
        FilterChip(Glyphs.kmb, stringResource(R.string.op_kmb), model.filter == Filter.Kmb, null, { model.choose(Filter.Kmb) }) {}
        FilterChip(Glyphs.ctb, stringResource(R.string.op_ctb), model.filter == Filter.Ctb, null, { model.choose(Filter.Ctb) }) {}
        FilterChip(Glyphs.gmb, stringResource(R.string.op_gmb), model.filter == Filter.Gmb, Help.Gmb, { model.choose(Filter.Gmb) }) { model.help = it }
        FilterChip(Glyphs.mtr, stringResource(R.string.op_mtr), model.filter == Filter.Mtr, Help.MtrGeo, { model.choose(Filter.Mtr) }) { model.help = it }
    }
}

@Composable
private fun FilterChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    help: Help?,
    onClick: () -> Unit,
    onHelp: (Help) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .height(36.dp)
                .background(if (selected) Ink.black else Ink.paper)
                .border(1.dp, Ink.black)
                .clickable(source, null, onClick = onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(icon, label, if (selected) Ink.paper else Ink.black, 16.dp)
            Spacer(Modifier.width(4.dp))
            BasicText(label, style = if (selected) Ink.small.copy(color = Ink.paper) else Ink.small)
        }
        if (help != null) {
            Spacer(Modifier.width(4.dp))
            HelpMark { onHelp(help) }
        }
    }
}

@Composable
private fun SectionPad(icon: ImageVector, title: String, help: Help? = null, onHelp: (Help) -> Unit = {}) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionHead(icon, title, help, onHelp)
    }
}

@Composable
private fun StopBoard(stop: Stop, model: AppModel, fav: Boolean) {
    Column {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.stopPole, null, Ink.black, 18.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                BasicText(stop.name, style = Ink.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Glyphs.distance, null, Ink.mute, 14.dp)
                    Spacer(Modifier.width(4.dp))
                    BasicText(formatMeters(stop.meters), style = Ink.meta)
                }
            }
            if (fav) {
                val on = model.fav.any { it.key == stop.key }
                InkButton(if (on) Glyphs.favOn else Glyphs.saved, stringResource(if (on) R.string.cd_fav_on else R.string.cd_fav_off)) {
                    model.toggleFav(stop)
                }
                Spacer(Modifier.width(6.dp))
            }
            InkButton(Glyphs.mapPin, stringResource(R.string.cd_map_stop)) { model.openMap() }
        }
        StopBody(stop) { model.help = it }
    }
}

@Composable
private fun StopBody(stop: Stop, onHelp: (Help) -> Unit) {
    var more by remember(stop.key) { mutableStateOf(false) }
    val routes = if (more || stop.routes.size <= 6) stop.routes else stop.routes.take(6)
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.routeCol, null, Ink.mute, 14.dp)
            Spacer(Modifier.width(4.dp))
            BasicText(stringResource(R.string.col_route), style = Ink.meta)
            Spacer(Modifier.width(12.dp))
            Glyph(Glyphs.destCol, null, Ink.mute, 14.dp)
            Spacer(Modifier.width(4.dp))
            BasicText(stringResource(R.string.col_dest), style = Ink.meta)
            Spacer(Modifier.weight(1f))
            Glyph(Glyphs.minuteCol, null, Ink.mute, 14.dp)
            Spacer(Modifier.width(4.dp))
            BasicText(stringResource(R.string.col_min), style = Ink.meta)
            Spacer(Modifier.width(4.dp))
            HelpMark { onHelp(Help.Minutes) }
        }
        routes.forEach { route ->
            RouteLine(route, onHelp)
            Hair()
        }
        if (!more && stop.routes.size > 6) {
            InkButton(Glyphs.more, stringResource(R.string.cd_more)) { more = true }
        }
        val failed = stop.routes.isNotEmpty() && stop.routes.all { it.state == com.wusper.eta.data.EtaState.Failed }
        if (failed) BasicText(stringResource(R.string.fail), style = Ink.meta, modifier = Modifier.padding(vertical = 6.dp))
    }
}

@Composable
private fun RouteLine(route: Route, onHelp: (Help) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(opIcon(route.co), null, Ink.black, 16.dp)
            Spacer(Modifier.width(6.dp))
            BasicText(route.route, style = Ink.route)
            if (route.serviceType != "1") {
                Spacer(Modifier.width(6.dp))
                BasicText(stringResource(R.string.service_n, route.serviceType), style = Ink.meta)
                Spacer(Modifier.width(2.dp))
                HelpMark { onHelp(Help.Service) }
            }
            Spacer(Modifier.weight(1f))
            MinutesRow(route.state, route.etas, onHelp)
        }
        if (route.dest.isNotBlank()) {
            BasicText(route.dest, style = Ink.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactStop(stop: Stop, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (selected) Ink.wash else Ink.paper)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (selected) Ink.black else Ink.paper))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(stop.name, style = Ink.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (stop.meters > 0) BasicText(formatMeters(stop.meters), style = Ink.meta)
            }
            val chips = stop.routes.map { it.route }.distinct().take(6).joinToString("  ")
            if (chips.isNotBlank()) BasicText(chips, style = Ink.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Hair()
}

@Composable
private fun CompactMtr(station: MtrStop, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Glyphs.mtr, null, Ink.black, 16.dp)
        Spacer(Modifier.width(8.dp))
        BasicText(station.name, style = Ink.body, modifier = Modifier.weight(1f))
        BasicText(station.lines.joinToString(" ") { it.name }, style = Ink.meta, maxLines = 1)
        if (station.meters > 0) {
            Spacer(Modifier.width(8.dp))
            BasicText(formatMeters(station.meters), style = Ink.meta)
        }
    }
    Hair()
}

@Composable
private fun MtrBoard(station: MtrStop, onHelp: (Help) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.mtr, null, Ink.black, 18.dp)
            Spacer(Modifier.width(8.dp))
            BasicText(station.name, style = Ink.title, modifier = Modifier.weight(1f))
            if (station.meters > 0) BasicText(formatMeters(station.meters), style = Ink.meta)
            Spacer(Modifier.width(4.dp))
            HelpMark { onHelp(Help.MtrGeo) }
        }
        station.lines.forEach { line -> LineBoard(line, onHelp) }
    }
    Hair()
}

@Composable
private fun LineBoard(line: MtrLine, onHelp: (Help) -> Unit) {
    Column(Modifier.padding(top = 6.dp)) {
        BasicText(line.name, style = Ink.small)
        SideRow(stringResource(R.string.up), line.up, onHelp)
        SideRow(stringResource(R.string.down), line.down, onHelp)
    }
}

@Composable
private fun SideRow(label: String, trains: List<com.wusper.eta.data.MtrTrain>, onHelp: (Help) -> Unit) {
    if (trains.isEmpty()) return
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = Ink.meta)
        Spacer(Modifier.width(4.dp))
        HelpMark { onHelp(Help.MtrSide) }
        Spacer(Modifier.width(8.dp))
        BasicText(trains.first().dest, style = Ink.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        trains.take(3).forEach { train ->
            Spacer(Modifier.width(8.dp))
            BasicText(if (train.minutes == null) "–" else "${train.minutes}′", style = Ink.minute)
        }
    }
}

@Composable
private fun SavedRow(saved: SavedStop, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicText(saved.name, style = Ink.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (saved.label.isNotBlank()) BasicText(saved.label, style = Ink.meta)
    }
    Hair()
}

@Composable
private fun RouteHit(route: Route, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(opIcon(route.co), null, Ink.black, 16.dp)
        Spacer(Modifier.width(8.dp))
        BasicText(route.route, style = Ink.route)
        Spacer(Modifier.width(8.dp))
        BasicText(route.dest, style = Ink.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
    Hair()
}

@Composable
private fun FailRow(onRetry: () -> Unit) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(stringResource(R.string.fail), style = Ink.body)
        Spacer(Modifier.width(8.dp))
        InkButton(Glyphs.retry, stringResource(R.string.cd_retry), onClick = onRetry)
    }
}

private fun visibleStops(model: AppModel): List<Stop> {
    if (model.filter == Filter.Mtr) return emptyList()
    return model.stops.mapNotNull { stop ->
        val routes = stop.routes.filter { model.filter.accepts(it.co) }
        if (routes.isEmpty()) null else stop.copy(routes = routes)
    }
}

private fun opIcon(co: String): ImageVector = when (co) {
    "KMB" -> Glyphs.kmb
    "GMB" -> Glyphs.gmb
    "MTR" -> Glyphs.mtr
    else -> Glyphs.ctb
}

private fun SavedStop.asStop(): Stop = Stop(key, co, id, name, 0, lat, lng, emptyList())

@Composable
private fun Box(modifier: Modifier, content: @Composable () -> Unit = {}) {
    androidx.compose.foundation.layout.Box(modifier, contentAlignment = Alignment.TopStart) { content() }
}
