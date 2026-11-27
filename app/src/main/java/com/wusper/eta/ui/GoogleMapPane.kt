package com.wusper.eta.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.wusper.eta.R
import com.wusper.eta.data.AppModel

@Composable
fun GoogleMapPane(model: AppModel) {
    val context = LocalContext.current
    var map by remember { mutableStateOf<GoogleMap?>(null) }
    var framed by remember { mutableStateOf(false) }
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
                google.setOnMarkerClickListener { marker ->
                    val key = marker.tag as? String
                    if (key != null && !key.startsWith("mtr:") && key != "here") model.open(key)
                    true
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
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            InkButton(Glyphs.zoomIn, stringResource(R.string.cd_zoom_in), label = stringResource(R.string.cd_zoom_in)) {
                map?.animateCamera(CameraUpdateFactory.zoomIn())
            }
            InkButton(Glyphs.zoomOut, stringResource(R.string.cd_zoom_out), label = stringResource(R.string.cd_zoom_out)) {
                map?.animateCamera(CameraUpdateFactory.zoomOut())
            }
            InkButton(Glyphs.center, stringResource(R.string.cd_center), label = stringResource(R.string.cd_center)) {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(model.lat, model.lng), 16f))
            }
        }
    }
    LaunchedEffect(map, model.stops, model.mtr, model.lat, model.lng) {
        val google = map ?: return@LaunchedEffect
        google.clear()
        val square = marker(false)
        val dot = marker(true)
        model.stops.filter { it.lat != 0.0 && it.lng != 0.0 }.forEach { stop ->
            google.addMarker(
                MarkerOptions().position(LatLng(stop.lat, stop.lng)).icon(square).anchor(0.5f, 0.5f).title(stop.name),
            )?.apply { tag = stop.key }
        }
        model.mtr.filter { it.lat != 0.0 }.forEach { station ->
            google.addMarker(
                MarkerOptions().position(LatLng(station.lat, station.lng)).icon(dot).anchor(0.5f, 0.5f).title(station.name),
            )?.apply { tag = "mtr:${station.sta}" }
        }
        if (!framed && model.lat != 0.0) {
            google.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(model.lat, model.lng), 16f))
            framed = true
        }
    }
}

private fun marker(filled: Boolean) = BitmapDescriptorFactory.fromBitmap(
    Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (filled) android.graphics.Color.rgb(11, 11, 12) else android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(11, 11, 12)
            style = Paint.Style.STROKE
            strokeWidth = 3.2f
        }
        if (filled) {
            canvas.drawCircle(16f, 16f, 10f, bg)
            canvas.drawCircle(16f, 16f, 8.5f, stroke.apply { color = android.graphics.Color.WHITE })
        } else {
            canvas.drawCircle(16f, 16f, 9f, bg)
            canvas.drawCircle(16f, 16f, 9f, stroke)
        }
    },
)
