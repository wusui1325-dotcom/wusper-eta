package com.wusper.eta.ui

import android.app.Activity
import androidx.compose.foundation.background
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
import com.wusper.eta.data.WidgetChoice
import com.wusper.eta.data.WidgetPrefs
import com.wusper.eta.data.chooseWidgetRoute
import com.wusper.eta.widget.EtaWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WidgetSizeRow(onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("1x1" to R.string.widget_1x1, "1x2" to R.string.widget_1x2, "2x2" to R.string.widget_2x2).forEach { (size, label) ->
            InkButton(Glyphs.all, stringResource(label), label = stringResource(label)) {
                if (activity != null) EtaWidgets.pin(activity, size)
                onDone()
            }
        }
    }
}

@Composable
fun WidgetAdminPane(model: AppModel) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    var openId by remember { mutableIntStateOf(0) }
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
                .padding(horizontal = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InkButton(Glyphs.back, stringResource(R.string.label_back), label = stringResource(R.string.label_back)) {
                    model.widgetPage = false
                }
                BasicText(stringResource(R.string.widget_section), style = Ink.title)
            }
            Spacer(Modifier.height(12.dp))
            placed.forEach { item ->
                val choice = WidgetPrefs.choice(context, item.id)
                val route = if (choice.manual && choice.route != null) {
                    choice.route
                } else {
                    chooseWidgetRoute(favorites, usage, RouteUsage.last(context), null)
                }
                val sizeLabel = when (item.size) {
                    "1x2" -> stringResource(R.string.widget_1x2)
                    "2x2" -> stringResource(R.string.widget_2x2)
                    else -> stringResource(R.string.widget_1x1)
                }
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    BasicText(sizeLabel, style = Ink.meta)
                    BasicText(
                        route?.let { it.title.ifBlank { it.route.ifBlank { it.line } } }
                            ?: stringResource(R.string.widget_auto),
                        style = Ink.route,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!choice.manual) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(stringResource(R.string.widget_auto), style = Ink.meta)
                            Spacer(Modifier.width(6.dp))
                            HelpMark { model.help = Help.WidgetAuto }
                        }
                    }
                    val stopLabel = choice.stopName.ifBlank { stringResource(R.string.widget_nearest) }
                    BasicText(stopLabel, style = Ink.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    InkButton(Glyphs.routeCol, stringResource(R.string.col_route), label = stringResource(R.string.col_route)) {
                        openId = if (openId == item.id) 0 else item.id
                        stops = emptyList()
                    }
                    if (openId == item.id) {
                        InkButton(Glyphs.all, stringResource(R.string.widget_auto), label = stringResource(R.string.widget_auto)) {
                            WidgetPrefs.saveChoice(context, item.id, WidgetChoice())
                            refresh(context, item.id, item.size, item.cls)
                            revision += 1
                        }
                        options(favorites, usage).forEach { candidate ->
                            InkButton(
                                Glyphs.routeCol,
                                candidate.title.ifBlank { candidate.route },
                                label = candidate.title.ifBlank { candidate.route },
                            ) {
                                WidgetPrefs.saveChoice(
                                    context,
                                    item.id,
                                    WidgetChoice(manual = true, route = candidate),
                                )
                                refresh(context, item.id, item.size, item.cls)
                                revision += 1
                            }
                        }
                        InkButton(Glyphs.stopPole, stringResource(R.string.widget_nearest), label = stringResource(R.string.widget_nearest)) {
                            val current = WidgetPrefs.choice(context, item.id)
                            WidgetPrefs.saveChoice(context, item.id, current.copy(stopId = "", stopName = ""))
                            refresh(context, item.id, item.size, item.cls)
                            revision += 1
                        }
                        if (route != null) {
                            InkButton(Glyphs.stopPole, stringResource(R.string.sec_stops), label = stringResource(R.string.sec_stops)) {
                                scope.launch {
                                    val loaded = withContext(Dispatchers.IO) { loadStops(context, route) }
                                    stops = loaded
                                }
                            }
                            stops.forEach { (id, name) ->
                                InkButton(Glyphs.stopPole, name, label = name) {
                                    val current = WidgetPrefs.choice(context, item.id)
                                    WidgetPrefs.saveChoice(context, item.id, current.copy(stopId = id, stopName = name))
                                    refresh(context, item.id, item.size, item.cls)
                                    revision += 1
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
                }
            }
        }
    }
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
