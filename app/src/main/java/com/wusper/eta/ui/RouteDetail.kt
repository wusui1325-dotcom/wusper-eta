package com.wusper.eta.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.wusper.eta.data.haversineMeters
import kotlinx.coroutines.launch

private val detailTitle
    get() = androidx.compose.ui.text.TextStyle(
        color = Ink.black,
        fontSize = (18f * Ink.textScale).sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
    )
private val detailStop
    get() = androidx.compose.ui.text.TextStyle(
        color = Ink.black,
        fontSize = (22f * Ink.textScale).sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
    )
private val detailEta
    get() = androidx.compose.ui.text.TextStyle(
        color = Ink.black,
        fontSize = (20f * Ink.textScale).sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )
private val detailMeta
    get() = androidx.compose.ui.text.TextStyle(
        color = Ink.mute,
        fontSize = (15f * Ink.textScale).sp,
    )

@Composable
fun RouteDetailPane(model: AppModel, modifier: Modifier = Modifier) {
    val focus = model.detail ?: return
    val saved = focus.favId in model.routeFav
    var expanded by remember(focus.favId) { mutableStateOf(setOf<String>()) }
    var showCodes by remember(focus.favId) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val header = maxHeight * 0.05f
        val tools = maxHeight * 0.10f
        val map = maxHeight * 0.35f
        val list = maxHeight - header - tools - map
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
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkButton(Glyphs.back, stringResource(R.string.label_back), label = stringResource(R.string.label_back)) {
                    model.closeDetail()
                }
                BasicText(
                    when (focus) {
                        is RouteFocus.Bus -> focus.route.route
                        is RouteFocus.Mtr -> focus.name
                    },
                    style = detailTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                InkButton(
                    if (saved) Glyphs.favOn else Glyphs.saved,
                    stringResource(if (saved) R.string.cd_fav_on else R.string.cd_fav_off),
                    label = stringResource(if (saved) R.string.label_saved else R.string.cd_fav_off),
                ) { model.toggleRouteFav() }
            }
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
            Box(Modifier.fillMaxWidth().height(list)) {
                if (model.detailReady && model.detailStops.isEmpty()) {
                    FailLine { model.openDetail(focus) }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    ) {
                        items(model.detailStops, key = { "${it.seq}-${it.id}" }) { stop ->
                            val open = stop.id in expanded
                            StopEtaRow(
                                stop = stop,
                                next = model.detailNext[stop.id],
                                times = model.detailTimes[stop.id].orEmpty(),
                                transfers = model.detailTransfers[stop.id],
                                open = open,
                                showCode = showCodes,
                                onToggle = {
                                    expanded = if (open) expanded - stop.id else expanded + stop.id
                                    if (!open) model.ensureTransfers(stop.id)
                                },
                            )
                        }
                    }
                }
            }
            DetailTools(
                Modifier.fillMaxWidth().height(tools),
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
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
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
    val fg = if (selected) Ink.paper else Ink.black
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .background(if (selected) Ink.black else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(source, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Glyph(icon, label, fg, 22.dp)
        Spacer(Modifier.height(2.dp))
        BasicText(
            label,
            style = Ink.small.copy(color = fg, fontSize = (13f * Ink.textScale).sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StopEtaRow(
    stop: DetailStop,
    next: EtaSlot?,
    times: List<EtaSlot>,
    transfers: List<String>?,
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
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                if (stop.seq > 0) stop.seq.toString() else "",
                style = detailStop,
                modifier = Modifier.width(36.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                BasicText(stop.name, style = detailStop, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showCode) {
                    BasicText(stop.id, style = detailMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            BasicText(eta, style = detailEta)
            Spacer(Modifier.width(12.dp))
            val source = remember(stop.id) { MutableInteractionSource() }
            Box(
                Modifier
                    .size(36.dp)
                    .graphicsLayer { rotationZ = if (open) 180f else 0f }
                    .semantics {
                        contentDescription = more
                        role = Role.Button
                    }
                    .clickable(source, indication = null, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Glyphs.chevron, null, Ink.black, 22.dp)
            }
        }
        if (open) {
            Column(Modifier.padding(bottom = 16.dp)) {
                BasicText(
                    if (upcoming.isEmpty()) none else upcoming.joinToString("    ") { "$it′" },
                    style = detailEta,
                )
                if (!transfers.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        transfers.joinToString("    "),
                        style = detailMeta,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
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
    var followed by remember { mutableStateOf(true) }
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
    LaunchedEffect(map, hereLat, hereLng, followed) {
        val google = map ?: return@LaunchedEffect
        if (!followed || hereLat == 0.0 || hereLng == 0.0) return@LaunchedEffect
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(hereLat, hereLng), 16f)
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
    Bitmap.createBitmap(168, 168, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            style = Paint.Style.FILL
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 14f
        }
        canvas.drawCircle(84f, 84f, 70f, fill)
        canvas.drawCircle(84f, 84f, 52f, ring)
        val label = if (order > 0) order.toString() else ""
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = if (label.length >= 3) 48f else 64f
            isFakeBoldText = true
        }
        val baseline = 84f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, 84f, baseline, text)
    },
)
