package com.wusper.eta.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.DetailStop
import com.wusper.eta.data.EtaSlot
import com.wusper.eta.data.RouteFocus
import com.wusper.eta.data.boardingFare
import com.wusper.eta.data.haversineMeters
import com.wusper.eta.data.nearestStop
import kotlinx.coroutines.launch

@Composable
fun RouteDetailPane(model: AppModel, modifier: Modifier = Modifier) {
    val focus = model.detail ?: return
    val saved = focus.favId in model.routeFav
    val expanded = remember(focus.favId) { mutableStateMapOf<String, Boolean>() }
    var showCodes by remember(focus.favId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusIcon = when (focus) {
        is RouteFocus.Mtr -> Glyphs.mtr
        is RouteFocus.Bus -> when (focus.route.co) {
            "KMB" -> Glyphs.kmb
            "CTB" -> Glyphs.ctb
            "GMB" -> Glyphs.gmb
            "LRT" -> Glyphs.lightRail
            else -> Glyphs.bus
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Ink.paper)) {
        val header = (maxHeight * 0.07f).coerceAtLeast(52.dp)
        val tools = (maxHeight * 0.10f).coerceAtLeast(62.dp)
        val map = maxHeight * 0.34f
        val list = (maxHeight - header - tools - map).coerceAtLeast(120.dp)
        fun jump(index: Int) {
            if (model.detailStops.isEmpty()) return
            val at = index.coerceIn(0, model.detailStops.lastIndex)
            scope.launch {
                if (Ink.reduceMotion) listState.scrollToItem(at) else listState.animateScrollToItem(at)
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(header)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkButton(Glyphs.back, stringResource(R.string.label_back), label = stringResource(R.string.label_back)) {
                    model.closeDetail()
                }
                val nearestFare = nearestStop(model.detailStops, model.lat, model.lng)?.let { boardingFare(model.detailFares[it.id]) }
                Row(
                    Modifier.weight(1f).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Ink.black),
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(focusIcon, null, Ink.paper, 14.dp)
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicText(
                        when (focus) {
                            is RouteFocus.Bus -> focus.route.route
                            is RouteFocus.Mtr -> focus.name
                        },
                        style = Ink.detailTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (nearestFare != null) {
                        Spacer(Modifier.width(8.dp))
                        StreamlinedPill(nearestFare, icon = Glyphs.fare)
                    }
                }
                InkButton(
                    if (saved) Glyphs.favOn else Glyphs.saved,
                    stringResource(if (saved) R.string.cd_fav_on else R.string.cd_fav_off),
                    selected = saved,
                    label = stringResource(if (saved) R.string.label_saved else R.string.cd_fav_off),
                ) { model.toggleRouteFav() }
            }
            Hair()
            RouteStopMap(
                model.detailStops,
                hereLat = model.lat,
                hereLng = model.lng,
                onStop = { key ->
                    val index = model.detailStops.indexOfFirst { "${it.seq}|${it.id}" == key }
                    if (index >= 0) jump(index)
                },
                modifier = Modifier.fillMaxWidth().height(map),
            )
            Hair()
            Box(Modifier.fillMaxWidth().height(list)) {
                if (model.detailReady && model.detailStops.isEmpty()) {
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        FailLine { model.openDetail(focus) }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    ) {
                        itemsIndexed(
                            model.detailStops,
                            key = { _, stop -> "${stop.seq}-${stop.id}" },
                            contentType = { _, _ -> "stop" },
                        ) { index, stop ->
                            val open = expanded[stop.id] == true
                            StopEtaRow(
                                stop = stop,
                                isFirst = index == 0,
                                isLast = index == model.detailStops.lastIndex,
                                next = model.detailNext[stop.id],
                                times = model.detailTimes[stop.id].orEmpty(),
                                transfers = model.detailTransfers[stop.id],
                                fare = boardingFare(model.detailFares[stop.id]),
                                open = open,
                                showCode = showCodes,
                                onToggle = {
                                    val next = !open
                                    expanded[stop.id] = next
                                    if (next) model.ensureTransfers(stop.id)
                                },
                            )
                        }
                    }
                }
            }
            Hair()
            DetailTools(
                Modifier.fillMaxWidth().height(tools).background(Ink.bar).padding(horizontal = 8.dp, vertical = 6.dp),
                codesOn = showCodes,
                onFlip = { model.switchDetailDirection() },
                onTop = { jump(0) },
                onEnd = { jump(model.detailStops.lastIndex) },
                onNear = {
                    val hereLat = model.lat
                    val hereLng = model.lng
                    val index = model.detailStops.indices.minByOrNull { i ->
                        val stop = model.detailStops[i]
                        if (stop.lat == 0.0 || stop.lng == 0.0 || hereLat == 0.0) Double.MAX_VALUE
                        else haversineMeters(hereLat, hereLng, stop.lat, stop.lng)
                    } ?: 0
                    jump(index)
                },
                onCodes = { showCodes = !showCodes },
            )
        }
    }
}

@Composable
private fun DetailTools(
    modifier: Modifier,
    codesOn: Boolean,
    onFlip: () -> Unit,
    onTop: () -> Unit,
    onEnd: () -> Unit,
    onNear: () -> Unit,
    onCodes: () -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ToolAction(Glyphs.bothWays, stringResource(R.string.tool_flip), false, Modifier.weight(1f), onFlip)
        ToolAction(Glyphs.toTop, stringResource(R.string.tool_top), false, Modifier.weight(1f), onTop)
        ToolAction(Glyphs.toBottom, stringResource(R.string.tool_end), false, Modifier.weight(1f), onEnd)
        ToolAction(Glyphs.locate, stringResource(R.string.tool_near), false, Modifier.weight(1f), onNear)
        ToolAction(Glyphs.codes, stringResource(R.string.tool_code), codesOn, Modifier.weight(1f), onCodes)
    }
}

@Composable
private fun ToolAction(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val on = selected || pressed
    val fg = if (on) Ink.paper else Ink.black
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (on) Ink.black else Ink.wash)
            .border(Ink.lineWidth, if (on) Ink.black else Ink.line, shape)
            .clickable(source, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(icon, label, fg, 18.dp)
        Spacer(Modifier.height(2.dp))
        BasicText(
            label,
            style = if (on) Ink.toolLabelOn else Ink.toolLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StopEtaRow(
    stop: DetailStop,
    isFirst: Boolean,
    isLast: Boolean,
    next: EtaSlot?,
    times: List<EtaSlot>,
    transfers: List<String>?,
    fare: String?,
    open: Boolean,
    showCode: Boolean,
    onToggle: () -> Unit,
) {
    val none = stringResource(R.string.none)
    val more = stringResource(R.string.cd_more_eta)
    val eta = when {
        next == null -> ""
        next.minutes == null -> none
        else -> "${next.minutes}′"
    }
    val upcoming = times.mapNotNull { it.minutes }.take(3)
    val rowClick = remember(stop.id) { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(rowClick, indication = null, onClick = onToggle),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (open || isFirst || isLast) Ink.black else Ink.wash)
                        .border(Ink.lineWidth, if (open || isFirst || isLast) Ink.black else Ink.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        if (stop.seq > 0) stop.seq.toString() else "",
                        style = Ink.small.copy(
                            color = if (open || isFirst || isLast) Ink.paper else Ink.black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BasicText(stop.name, style = Ink.detailStop, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showCode) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(Glyphs.codes, null, Ink.mute, 12.dp)
                        Spacer(Modifier.width(4.dp))
                        BasicText(stop.id, style = Ink.detailMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (eta.isNotEmpty()) {
                if (next?.minutes == null) {
                    StreamlinedPill(eta)
                } else {
                    BasicText(eta, style = Ink.detailEta)
                }
            }
            Spacer(Modifier.width(10.dp))
            val source = remember(stop.id) { MutableInteractionSource() }
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (open) Ink.black else Ink.wash)
                    .border(Ink.lineWidth, if (open) Ink.black else Ink.line, CircleShape)
                    .graphicsLayer { rotationZ = if (open) 180f else 0f }
                    .semantics {
                        contentDescription = more
                        role = Role.Button
                    }
                    .clickable(source, indication = null, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Glyphs.chevron, null, if (open) Ink.paper else Ink.black, 16.dp)
            }
        }
        if (open) {
            val drawerShape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 44.dp, bottom = 12.dp)
                    .clip(drawerShape)
                    .background(Ink.wash)
                    .border(Ink.lineWidth, Ink.line, drawerShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                val extra = when {
                    next == null -> ""
                    upcoming.isEmpty() -> none
                    else -> upcoming.joinToString("   ") { "$it′" }
                }
                if (extra.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(Glyphs.clock, null, Ink.black, 15.dp)
                        Spacer(Modifier.width(6.dp))
                        BasicText(extra, style = Ink.detailEta)
                    }
                }
                if (fare != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(Glyphs.fare, null, Ink.mute, 14.dp)
                        Spacer(Modifier.width(6.dp))
                        BasicText(stringResource(R.string.fare_line, fare), style = Ink.detailMeta)
                    }
                }
                if (!transfers.isNullOrEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Glyph(Glyphs.transfer, null, Ink.mute, 14.dp)
                        Spacer(Modifier.width(6.dp))
                        BasicText(
                            transfers.joinToString("   "),
                            style = Ink.detailMeta,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Hair()
    }
}

@Composable
private fun RouteStopMap(
    stops: List<DetailStop>,
    hereLat: Double,
    hereLng: Double,
    onStop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    GoogleRouteMap(stops, hereLat, hereLng, onStop, modifier)
}

@Composable
private fun GoogleRouteMap(
    stops: List<DetailStop>,
    hereLat: Double,
    hereLng: Double,
    onStop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    var followed by remember(stops) { mutableStateOf(true) }
    val icons = remember { HashMap<Int, com.google.android.gms.maps.model.BitmapDescriptor>() }
    val onStopNow = rememberUpdatedState(onStop)
    val mapView = remember {
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
                google.setOnCameraMoveStartedListener { reason ->
                    if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) followed = false
                }
                map = google
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
    LaunchedEffect(map, stops) {
        val google = map ?: return@LaunchedEffect
        placeStops(google, stops, icons) { onStopNow.value(it) }
    }
    LaunchedEffect(map, stops, hereLat, hereLng, followed) {
        val google = map ?: return@LaunchedEffect
        if (!followed) return@LaunchedEffect
        val stop = nearestStop(stops, hereLat, hereLng) ?: return@LaunchedEffect
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(stop.lat, stop.lng), 16f)
        try {
            google.moveCamera(update)
        } catch (_: Exception) {
            google.setOnMapLoadedCallback {
                if (followed) {
                    try {
                        google.moveCamera(update)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}

private fun placeStops(
    google: GoogleMap,
    stops: List<DetailStop>,
    icons: MutableMap<Int, com.google.android.gms.maps.model.BitmapDescriptor>,
    onStop: (String) -> Unit,
) {
    google.clear()
    google.setOnMarkerClickListener { marker ->
        (marker.tag as? String)?.let(onStop)
        true
    }
    stops.filter { it.lat != 0.0 && it.lng != 0.0 }.forEach { stop ->
        val order = if (stop.seq > 0) stop.seq else 0
        val icon = icons.getOrPut(order) { numberedMarker(order) }
        google.addMarker(
            MarkerOptions()
                .position(LatLng(stop.lat, stop.lng))
                .icon(icon)
                .anchor(0.5f, 0.5f)
                .title(stop.name)
                .zIndex(2f),
        )?.apply { tag = "${stop.seq}|${stop.id}" }
    }
}

private fun numberedMarker(order: Int) = BitmapDescriptorFactory.fromBitmap(
    Bitmap.createBitmap(148, 148, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(11, 11, 12)
            style = Paint.Style.FILL
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        canvas.drawCircle(74f, 74f, 60f, fill)
        canvas.drawCircle(74f, 74f, 54f, ring)
        val label = if (order > 0) order.toString() else ""
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = if (label.length >= 3) 42f else 54f
            isFakeBoldText = true
        }
        val baseline = 74f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, 74f, baseline, text)
    },
)
