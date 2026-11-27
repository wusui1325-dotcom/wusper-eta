package com.wusper.eta.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.wusper.eta.data.boardingFare
import com.wusper.eta.data.formatMeters

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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkButton(
                    icon = Glyphs.routeCol,
                    cd = stringResource(R.string.search_route),
                    selected = routeMode,
                    label = stringResource(R.string.search_route),
                ) {
                    model.chooseSearch(com.wusper.eta.data.SearchMode.Route)
                }
                InkButton(
                    icon = Glyphs.stopPole,
                    cd = stringResource(R.string.search_stop),
                    selected = !routeMode,
                    label = stringResource(R.string.search_stop),
                ) {
                    model.chooseSearch(com.wusper.eta.data.SearchMode.StopName)
                }
            }
            val fieldShape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clip(fieldShape)
                    .background(Ink.wash)
                    .border(Ink.lineWidth, Ink.line, fieldShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Glyphs.search, null, Ink.mute, 18.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
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
                if (model.query.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Ink.surface)
                            .clickable(remember { MutableInteractionSource() }, null) { model.onQuery("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(Glyphs.close, stringResource(R.string.cd_clear), Ink.black, 14.dp)
                    }
                }
            }
            val listed = if (routeMode) model.foundRoutes.isNotEmpty() else model.foundStops.isNotEmpty() || model.foundMtr.isNotEmpty()
            LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                if (model.searching && !listed) {
                    item { SearchNote(stringResource(R.string.search_loading)) }
                } else if (model.searchFailed && !listed) {
                    item { FailLine { model.retrySearch() } }
                } else if (model.searchFailed) {
                    item { SearchNote(stringResource(R.string.search_cached), retry = { model.retrySearch() }) }
                } else if (!model.searching && model.query.isNotBlank() && !listed) {
                    item { SearchNote(stringResource(R.string.search_empty)) }
                }
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
                        NameRow(Glyphs.stopPole, stop.name, stop.nameEn) { model.openStop(stop) }
                    }
                    items(model.foundMtr, key = { "fm-${it.sta}" }, contentType = { "mtr" }) { station ->
                        NameRow(Glyphs.mtr, station.name, station.nameEn) { model.openMtr(station) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchNote(text: String, retry: (() -> Unit)? = null) {
    Row(Modifier.padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Glyphs.info, null, Ink.mute, 15.dp)
        Spacer(Modifier.width(8.dp))
        BasicText(text, style = Ink.body, modifier = Modifier.weight(1f))
        if (retry != null) {
            Spacer(Modifier.width(8.dp))
            InkButton(Glyphs.retry, stringResource(R.string.cd_retry), label = stringResource(R.string.label_retry), onClick = retry)
        }
    }
}

@Composable
private fun SearchRouteRow(route: Route, onClick: () -> Unit) {
    val bound = if (route.dest.isBlank()) "" else stringResource(R.string.bound_to, route.dest)
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(Ink.wash)
            .border(Ink.lineWidth, Ink.line, shape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Ink.black)
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(opIcon(route.co), null, Ink.paper, 14.dp)
            Spacer(Modifier.width(5.dp))
            BasicText(
                route.route,
                style = Ink.route.copy(color = Ink.paper, fontSize = 16.sp),
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.width(10.dp))
        BasicText(
            bound,
            style = Ink.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Glyph(Glyphs.chevronRight, null, Ink.mute, 16.dp)
    }
}

@Composable
private fun NameRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(Ink.wash)
            .border(Ink.lineWidth, Ink.line, shape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Ink.paper)
                .border(Ink.lineWidth, Ink.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, null, Ink.black, 15.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Ink.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank() && !subtitle.equals(title, ignoreCase = true)) {
                BasicText(subtitle, style = Ink.meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Glyph(Glyphs.chevronRight, null, Ink.mute, 16.dp)
    }
}

@Composable
fun SavedPane(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        if (model.savedRoutes.isNotEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { FavoriteRoutes(model) }
        }
        if (model.recent.isNotEmpty()) {
            LazyColumn(
                Modifier
                    .then(if (model.savedRoutes.isEmpty()) Modifier.weight(1f) else Modifier.heightIn(max = 240.dp))
                    .padding(horizontal = 20.dp),
            ) {
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
            val cardShape = RoundedCornerShape(20.dp)
            Column(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Ink.wash)
                    .border(Ink.lineWidth, Ink.line, cardShape)
                    .padding(14.dp),
            ) {
                NavField(
                    icon = Glyphs.hereOrigin,
                    label = stringResource(R.string.origin),
                    value = model.originText,
                    chosen = model.origin != null,
                    onFocus = { model.focusNav("o") },
                    onValue = { model.editNav("o", it) },
                )
                Spacer(Modifier.height(10.dp))
                Hair()
                Spacer(Modifier.height(10.dp))
                NavField(
                    icon = Glyphs.hereDest,
                    label = stringResource(R.string.dest),
                    value = model.destText,
                    chosen = model.dest != null,
                    onFocus = { model.focusNav("d") },
                    onValue = { model.editNav("d", it) },
                )
                if (ready) {
                    Spacer(Modifier.height(12.dp))
                    InkButton(
                        Glyphs.go,
                        stringResource(R.string.nav_confirm),
                        selected = true,
                        label = stringResource(R.string.nav_confirm),
                    ) { model.confirmNav() }
                }
            }
            val plan = model.plan
            when {
                model.navHits.isNotEmpty() -> {
                    LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
                        itemsIndexed(model.navHits, key = { index, hit -> "$index|${hit.name}|${hit.lat}|${hit.lng}" }, contentType = { _, _ -> "stop" }) { _, hit ->
                            NameRow(Glyphs.mapPin, hit.name, hit.nameEn) { model.pickNav(hit) }
                        }
                    }
                }
                model.planFailed -> Box(Modifier.padding(horizontal = 20.dp)) { FailLine { model.confirmNav() } }
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
    icon: ImageVector,
    label: String,
    value: String,
    chosen: Boolean,
    onFocus: () -> Unit,
    onValue: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (chosen) Ink.black else Ink.paper)
                .border(Ink.lineWidth, if (chosen) Ink.black else Ink.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, null, if (chosen) Ink.paper else Ink.black, 14.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(label, style = Ink.meta)
            Box(Modifier.fillMaxWidth().heightIn(min = 30.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) BasicText(label, style = Ink.meta)
                BasicTextField(
                    value = value,
                    onValueChange = onValue,
                    textStyle = Ink.body.copy(fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal),
                    singleLine = true,
                    cursorBrush = SolidColor(Ink.black),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
                )
            }
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Ink.surface)
                    .clickable(remember { MutableInteractionSource() }, null) { onValue("") },
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Glyphs.close, stringResource(R.string.cd_clear), Ink.black, 14.dp)
            }
        }
    }
}

@Composable
private fun PlanUnavailable(onHelp: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Glyphs.info, null, Ink.black, 18.dp)
        Spacer(Modifier.width(8.dp))
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        val range = if (plan.minutes == plan.minutesMax) {
            stringResource(R.string.minutes_n, plan.minutes)
        } else {
            stringResource(R.string.nav_range, plan.minutes, plan.minutesMax)
        }
        val summaryShape = RoundedCornerShape(18.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(summaryShape)
                .background(Ink.black)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.clock, null, Ink.paper, 20.dp)
                Spacer(Modifier.width(8.dp))
                BasicText(range, style = Ink.display.copy(color = Ink.paper))
            }
            val fare = boardingFare(plan.fare)
            if (fare != null || plan.walkMinutes > 0) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (fare != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(Glyphs.fare, null, Ink.paper.copy(alpha = 0.8f), 14.dp)
                            Spacer(Modifier.width(4.dp))
                            BasicText(stringResource(R.string.fare_total, fare), style = Ink.small.copy(color = Ink.paper))
                        }
                    }
                    if (plan.walkMinutes > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(Glyphs.stepWalk, null, Ink.paper.copy(alpha = 0.8f), 14.dp)
                            Spacer(Modifier.width(4.dp))
                            BasicText(stringResource(R.string.nav_walk_min, plan.walkMinutes), style = Ink.small.copy(color = Ink.paper))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        plan.rides.forEach { ride ->
            RideRow(ride)
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
    val kindIcon = when (ride.kind) {
        "gmb" -> Glyphs.gmb
        "rail" -> Glyphs.mtr
        else -> Glyphs.bus
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
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(Ink.wash)
            .border(Ink.lineWidth, Ink.line, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreamlinedPill(kind, icon = kindIcon, inverted = true)
            Spacer(Modifier.width(8.dp))
            BasicText(ride.name, style = Ink.route, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            BasicText(time, style = Ink.minute)
        }
        if (path.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.along, null, Ink.mute, 14.dp)
                Spacer(Modifier.width(6.dp))
                BasicText(path, style = Ink.body)
            }
        }
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
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
    val shape = RoundedCornerShape(999.dp)
    Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .height(34.dp)
                .clip(shape)
                .background(if (selected) Ink.black else Ink.wash)
                .border(Ink.lineWidth, if (selected) Ink.black else Ink.line, shape)
                .clickable(source, null, onClick = onClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(icon, label, if (selected) Ink.paper else Ink.black, 15.dp)
            Spacer(Modifier.width(5.dp))
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
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Glyph(Glyphs.stopPole, null, Ink.mute, 14.dp)
                Spacer(Modifier.width(6.dp))
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
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(Ink.wash)
            .border(Ink.lineWidth, Ink.line, shape)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(Glyphs.stopPole, null, Ink.black, 16.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(saved.name, style = Ink.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (saved.label.isNotBlank()) BasicText(saved.label, style = Ink.meta)
        }
        Glyph(Glyphs.chevronRight, null, Ink.mute, 15.dp)
    }
}

@Composable
private fun FailRow(onRetry: () -> Unit) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(Glyphs.info, null, Ink.black, 16.dp)
        Spacer(Modifier.width(8.dp))
        BasicText(stringResource(R.string.fail), style = Ink.body)
        Spacer(Modifier.width(8.dp))
        InkButton(Glyphs.retry, stringResource(R.string.cd_retry), label = stringResource(R.string.label_retry), onClick = onRetry)
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
    "LRT" -> Glyphs.lightRail
    "MTR" -> Glyphs.mtr
    else -> Glyphs.ctb
}

private fun SavedStop.asStop(): Stop = Stop(key, co, id, name, 0, lat, lng, emptyList())
