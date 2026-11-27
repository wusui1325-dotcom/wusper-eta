package com.wusper.eta.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.DetailStop
import com.wusper.eta.data.FavRoute
import com.wusper.eta.data.Help
import com.wusper.eta.data.MtrIndex
import com.wusper.eta.data.RouteUsage
import com.wusper.eta.data.TransitApi
import com.wusper.eta.data.UiPrefs
import com.wusper.eta.data.WidgetPrefs
import com.wusper.eta.widget.EtaWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WidgetSizeRow(onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    Column(Modifier.fillMaxWidth().background(Ink.paper)) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("1x1" to R.string.widget_1x1, "2x1" to R.string.widget_2x1, "2x2" to R.string.widget_2x2).forEach { (size, label) ->
                InkButton(Glyphs.widget, stringResource(label), label = stringResource(label)) {
                    if (activity != null) EtaWidgets.pin(activity, size)
                    onDone()
                }
            }
        }
        Hair()
    }
}

@Composable
fun WidgetAdminPane(model: AppModel) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var openKey by remember { mutableStateOf("") }
    var stops by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val placed = remember(revision) { EtaWidgets.placed(context) }
    val favorites = remember(revision) { UiPrefs.savedRoutes(context) }
    val usage = remember(revision) { RouteUsage.read(context) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkButton(Glyphs.back, stringResource(R.string.label_back), label = stringResource(R.string.label_back)) {
                    model.widgetPage = false
                }
                Spacer(Modifier.width(10.dp))
                SectionHead(Glyphs.widget, stringResource(R.string.widget_section))
            }
            placed.forEach { item ->
                val choice = WidgetPrefs.choice(context, item.id)
                val sizeLabel = when (item.size) {
                    "2x1" -> stringResource(R.string.widget_2x1)
                    "2x2" -> stringResource(R.string.widget_2x2)
                    else -> stringResource(R.string.widget_1x1)
                }
                val slots = if (item.size == "2x2") listOf(0, 1) else listOf(0)
                val cardShape = RoundedCornerShape(18.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(Ink.wash)
                        .border(Ink.lineWidth, Ink.line, cardShape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StreamlinedPill(sizeLabel, icon = Glyphs.widget, inverted = true)
                    slots.forEachIndexed { idx, slot ->
                        if (idx > 0) Hair()
                        val manual = if (slot == 0) choice.manual else choice.manualB
                        val picked = if (slot == 0) choice.route else choice.routeB
                        val stopName = if (slot == 0) choice.stopName else choice.stopNameB
                        val route = if (manual && picked != null) {
                            picked
                        } else {
                            EtaWidgets.activeRoute(context, item.id, slot)
                        }
                        val key = "${item.id}:$slot"
                        if (item.size == "2x2") {
                            BasicText(
                                stringResource(if (slot == 0) R.string.widget_upper else R.string.widget_lower),
                                style = Ink.meta,
                            )
                        }
                        BasicText(
                            route?.let { it.title.ifBlank { it.route.ifBlank { it.line } } }
                                ?: stringResource(R.string.widget_auto),
                            style = Ink.route,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!manual) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(stringResource(R.string.widget_auto), style = Ink.meta)
                                Spacer(Modifier.width(6.dp))
                                HelpMark { model.help = Help.WidgetAuto }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Glyph(Glyphs.stopPole, null, Ink.mute, 14.dp)
                            Spacer(Modifier.width(6.dp))
                            BasicText(
                                stopName.ifBlank { stringResource(R.string.widget_nearest) },
                                style = Ink.body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        InkButton(Glyphs.routeCol, stringResource(R.string.col_route), selected = openKey == key, label = stringResource(R.string.col_route)) {
                            openKey = if (openKey == key) "" else key
                            stops = emptyList()
                        }
                        if (openKey == key) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StreamlinedRowButton(Glyphs.all, stringResource(R.string.widget_auto), selected = !manual, trailing = null) {
                                    writeSlot(context, item.id, slot, manual = false, route = null, stopId = "", stopName = "")
                                    refresh(context, item.id, item.size, item.cls)
                                    revision += 1
                                }
                                options(favorites, usage).forEach { candidate ->
                                    val label = candidate.title.ifBlank { candidate.route.ifBlank { candidate.line } }
                                    StreamlinedRowButton(
                                        icon = if (candidate.line.isNotBlank()) Glyphs.mtr else Glyphs.routeCol,
                                        label = label,
                                        selected = manual && picked?.id == candidate.id,
                                        trailing = null,
                                    ) {
                                        writeSlot(context, item.id, slot, manual = true, route = candidate, stopId = "", stopName = "")
                                        refresh(context, item.id, item.size, item.cls)
                                        revision += 1
                                    }
                                }
                                StreamlinedRowButton(Glyphs.locate, stringResource(R.string.widget_nearest), trailing = null) {
                                    val current = WidgetPrefs.choice(context, item.id)
                                    writeSlot(
                                        context,
                                        item.id,
                                        slot,
                                        manual = if (slot == 0) current.manual else current.manualB,
                                        route = if (slot == 0) current.route else current.routeB,
                                        stopId = "",
                                        stopName = "",
                                    )
                                    refresh(context, item.id, item.size, item.cls)
                                    revision += 1
                                }
                                if (route != null) {
                                    InkButton(Glyphs.stopPole, stringResource(R.string.sec_stops), label = stringResource(R.string.sec_stops)) {
                                        scope.launch {
                                            val loaded = withContext(Dispatchers.IO) { loadStops(context, route) }
                                            if (openKey == key) stops = loaded
                                        }
                                    }
                                    stops.forEach { (stopId, name) ->
                                        StreamlinedRowButton(Glyphs.stopPole, name, trailing = null) {
                                            val current = WidgetPrefs.choice(context, item.id)
                                            writeSlot(
                                                context,
                                                item.id,
                                                slot,
                                                manual = if (slot == 0) current.manual else current.manualB,
                                                route = if (slot == 0) current.route else current.routeB,
                                                stopId = stopId,
                                                stopName = name,
                                            )
                                            refresh(context, item.id, item.size, item.cls)
                                            revision += 1
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun writeSlot(
    context: android.content.Context,
    id: Int,
    slot: Int,
    manual: Boolean,
    route: FavRoute?,
    stopId: String,
    stopName: String,
) {
    val current = WidgetPrefs.choice(context, id)
    val next = if (slot == 0) {
        current.copy(manual = manual, route = route, stopId = stopId, stopName = stopName)
    } else {
        current.copy(manualB = manual, routeB = route, stopIdB = stopId, stopNameB = stopName)
    }
    WidgetPrefs.saveChoice(context, id, next)
}

private fun options(favorites: List<FavRoute>, usage: List<Pair<FavRoute, Int>>): List<FavRoute> {
    val seen = HashSet<String>()
    val out = ArrayList<FavRoute>()
    (favorites + usage.map { it.first }).forEach { route ->
        if (route.id.isNotBlank() && seen.add(route.id)) out.add(route)
    }
    return out
}

private fun refresh(context: android.content.Context, id: Int, size: String, cls: Class<*>) {
    Thread { EtaWidgets.refreshOne(context, id, size, cls) }.start()
}

private suspend fun loadStops(context: android.content.Context, route: FavRoute): List<Pair<String, String>> {
    return if (route.line.isNotBlank()) {
        val index = MtrIndex.load(context)
        index.onLine(route.line).mapIndexed { i, station ->
            DetailStop(station.sta, station.name, station.lat, station.lng, i + 1)
        }.map { it.id to it.name }
    } else {
        try {
            TransitApi().routeStops(route.asRoute()).map { it.id to it.name }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
