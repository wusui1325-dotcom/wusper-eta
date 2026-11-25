package com.wusper.eta.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object Glyphs {
    val nearby: ImageVector by lazy { stroke("M12,12 m-1.3,0 a1.3,1.3 0 1,0 2.6,0 a1.3,1.3 0 1,0 -2.6,0 M12,5 A7,7 0 0,1 19,12 M12,2.2 A9.8,9.8 0 0,1 21.8,12") }
    val bus: ImageVector by lazy { stroke("M4,9 H17 L21,13 V17 H4 Z M7,17 V20 M16,17 V20 M7,12 H12 M4,13 H21") }
    val nav: ImageVector by lazy { stroke("M12,3 L16.2,14.2 L12,12.2 L7.8,14.2 Z M12,12 m-8.2,0 a8.2,8.2 0 1,0 16.4,0 a8.2,8.2 0 1,0 -16.4,0") }
    val search: ImageVector by lazy { stroke("M11,11 m-5.2,0 a5.2,5.2 0 1,0 10.4,0 a5.2,5.2 0 1,0 -10.4,0 M15,15 L20,20") }
    val map: ImageVector by lazy { stroke("M4,6 H15 L20,11 V20 H4 Z M15,6 V11 H20") }
    val plan: ImageVector by lazy { stroke("M5,17 C9,17 8,7 12,7 C16,7 15,13 19,13 M5,17 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 M19,13 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0") }
    val saved: ImageVector by lazy { stroke("M7,3 H17 V21 L12,17 L7,21 Z") }
    val favOn: ImageVector by lazy { fill("M7,3 H17 V21 L12,17 L7,21 Z") }
    val locate: ImageVector by lazy { stroke("M4,9 V4 H9 M15,4 H20 V9 M20,15 V20 H15 M9,20 H4 V15 M12,8 V16 M8,12 H16") }
    val radiusIn: ImageVector by lazy { stroke("M9,4 H4 V9 M15,4 H20 V9 M4,15 V20 H9 M15,20 H20 V15") }
    val radiusOut: ImageVector by lazy { stroke("M4,9 V4 H9 M20,9 V4 H15 M4,15 V20 H9 M20,15 V20 H15") }
    val refresh: ImageVector by lazy { stroke("M19,12 A7,7 0 1,1 12,5 M12,2 V6 H16") }
    val close: ImageVector by lazy { stroke("M6,6 L18,18 M18,6 L6,18") }
    val back: ImageVector by lazy { stroke("M15,5 L7,12 L15,19") }
    val chevron: ImageVector by lazy { stroke("M6,9 L12,15 L18,9") }
    val toTop: ImageVector by lazy { stroke("M5,6 H19 M8,16 L12,10 L16,16") }
    val toBottom: ImageVector by lazy { stroke("M5,18 H19 M8,8 L12,14 L16,8") }
    val codes: ImageVector by lazy { stroke("M6,6 H18 M6,12 H18 M6,18 H14") }
    val mapPin: ImageVector by lazy { stroke("M12,21 C12,21 6,14 6,9 A6,6 0 0,1 18,9 C18,14 12,21 12,21 Z M12,9 m-2,0 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0") }
    val clear: ImageVector by lazy { stroke("M12,3 L21,12 L12,21 L3,12 Z M9,9 L15,15 M15,9 L9,15") }
    val swap: ImageVector by lazy { stroke("M8,7 H18 L14,3 M16,17 H6 L10,21") }
    val bothWays: ImageVector by lazy { stroke("M4,12 H20 M4,12 L8,8 M4,12 L8,16 M20,12 L16,8 M20,12 L16,16") }
    val go: ImageVector by lazy { stroke("M4,12 H16 M12,7 L18,12 L12,17 M20,5 V19") }
    val hereOrigin: ImageVector by lazy { stroke("M12,3 V7 M12,17 V21 M3,12 H7 M17,12 H21 M12,12 m-4,0 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0") }
    val hereDest: ImageVector by lazy { stroke("M12,3 V7 M12,17 V21 M3,12 H7 M17,12 H21 M8,8 H16 V16 H8 Z") }
    val retry: ImageVector by lazy { stroke("M8,7 H4 V3 M4,7 A8,8 0 1,1 6,18") }
    val zoomIn: ImageVector by lazy { stroke("M5,12 H19 M12,5 V19") }
    val zoomOut: ImageVector by lazy { stroke("M5,12 H19") }
    val center: ImageVector by lazy { stroke("M12,3 V7 M12,17 V21 M3,12 H7 M17,12 H21 M12,12 m-2.2,0 a2.2,2.2 0 1,0 4.4,0 a2.2,2.2 0 1,0 -4.4,0") }
    val more: ImageVector by lazy { stroke("M4,7 H20 M4,12 H15 M4,17 H10") }
    val all: ImageVector by lazy { stroke("M4,4 H10 V10 H4 Z M14,4 H20 V10 H14 Z M4,14 H10 V20 H4 Z M14,14 H20 V20 H14 Z") }
    val kmb: ImageVector by lazy { stroke("M5,3 H19 V21 H5 Z M5,12 H19 M8,7 H11 M13,7 H16 M8,16 H11 M13,16 H16") }
    val ctb: ImageVector by lazy { stroke("M3,8 H21 V17 H3 Z M6,11 H10 M14,11 H18 M7,17 V20 M17,17 V20") }
    val gmb: ImageVector by lazy { stroke("M3,15 L6,8 H15 L21,15 V18 H3 Z M6,18 V20.5 M17,18 V20.5 M7,15 H18") }
    val mtr: ImageVector by lazy { stroke("M3,16 H21 M6,16 V11 H18 V16 M8,11 V8 H16 V11 M7,19 H10 M14,19 H17") }
    val routeCol: ImageVector by lazy { stroke("M6,3 H18 V21 H6 Z M9,8 H15 M9,12 H15 M9,16 H13") }
    val destCol: ImageVector by lazy { stroke("M4,12 H18 M13,7 L19,12 L13,17") }
    val minuteCol: ImageVector by lazy { stroke("M7,19 V8 M12,19 V4 M17,19 V11") }
    val distance: ImageVector by lazy { stroke("M4,16 H20 M4,16 V12 M20,16 V12 M12,16 V13") }
    val stopPole: ImageVector by lazy { stroke("M8,3 V21 M8,6 H17 V13 H8") }
    val recent: ImageVector by lazy { stroke("M12,7 V12 L16,14.5 M12,4 A8,8 0 1,0 19,8") }
    val along: ImageVector by lazy { stroke("M6,7 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 M12,12 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 M18,17 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 M7.4,8.2 L10.6,10.8 M13.4,13.2 L16.6,15.8") }
    val result: ImageVector by lazy { stroke("M6,4 H18 V20 H6 Z M9,8 H15 M9,12 H15 M9,16 H12") }
    val stepWalk: ImageVector by lazy { stroke("M9,4 m-1.5,0 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0 M8,8 L11,14 L8,20 M11,14 L14,12 L17,15 M14,12 L12,20") }
    val stepRide: ImageVector by lazy { stroke("M4,15 H20 M6,15 V9 H15 L19,15 M12,9 V5 M8,15 V18.5 M16,15 V18.5") }
    val settings: ImageVector by lazy { stroke("M4,7 H20 M4,12 H20 M4,17 H20 M8,5 V9 M15,10 V14 M11,15 V19") }
    val language: ImageVector by lazy { stroke("M5,6 H19 M12,6 V18 M7,10 H17 M8,14 H16") }
    val a11y: ImageVector by lazy { stroke("M12,4 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 M6,9 H18 M12,9 V20 M12,13 L8,17 M12,13 L16,17") }
    val largeType: ImageVector by lazy { stroke("M6,18 L10,6 H12 L16,18 M8,13 H14") }
    val contrast: ImageVector by lazy { stroke("M12,4 A8,8 0 1,0 12,20 A8,8 0 1,0 12,4 M12,4 V20") }
    val still: ImageVector by lazy { stroke("M8,5 V19 M12,8 V16 M16,6 V18") }
}

private fun stroke(d: String): ImageVector = ImageVector.Builder(
    name = "g",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(d).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f,
        strokeLineCap = StrokeCap.Square,
        strokeLineJoin = StrokeJoin.Miter,
    )
}.build()

private fun fill(d: String): ImageVector = ImageVector.Builder(
    name = "f",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
}.build()
