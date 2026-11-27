package com.wusper.eta.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object Glyphs {
    val nearby: ImageVector by lazy { stroke("M12,12 m-1.4,0 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0 M12,5.5 A6.5,6.5 0 0,1 18.5,12 M12,2.5 A9.5,9.5 0 0,1 21.5,12") }
    val bus: ImageVector by lazy { stroke("M5,7 C5,5.3 6.3,4 8,4 H16 C17.7,4 19,5.3 19,7 V16.5 C19,17.3 18.3,18 17.5,18 H6.5 C5.7,18 5,17.3 5,16.5 Z M5,12 H19 M8,18 V20 M16,18 V20 M8.5,15 H9 M15,15 H15.5") }
    val nav: ImageVector by lazy { stroke("M12,3.5 L16.2,14.5 L12,12.4 L7.8,14.5 Z M12,12 m-8.5,0 a8.5,8.5 0 1,0 17,0 a8.5,8.5 0 1,0 -17,0") }
    val search: ImageVector by lazy { stroke("M10.8,10.8 m-5.8,0 a5.8,5.8 0 1,0 11.6,0 a5.8,5.8 0 1,0 -11.6,0 M15.2,15.2 L20,20") }
    val map: ImageVector by lazy { stroke("M4,6.5 L9.5,4.5 L14.5,6.5 L20,4.5 V17.5 L14.5,19.5 L9.5,17.5 L4,19.5 Z M9.5,4.5 V17.5 M14.5,6.5 V19.5") }
    val plan: ImageVector by lazy { stroke("M5.5,17.5 C9.5,17.5 8.5,6.5 12,6.5 C15.5,6.5 14.5,13 18.5,13 M5.5,17.5 m-1.8,0 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0 M18.5,13 m-1.8,0 a1.8,1.8 0 1,0 3.6,0 a1.8,1.8 0 1,0 -3.6,0") }
    val saved: ImageVector by lazy { stroke("M6.5,4.5 C6.5,3.7 7.2,3 8,3 H16 C16.8,3 17.5,3.7 17.5,4.5 V20.5 L12,16.8 L6.5,20.5 Z") }
    val favOn: ImageVector by lazy { fill("M6.5,4.5 C6.5,3.7 7.2,3 8,3 H16 C16.8,3 17.5,3.7 17.5,4.5 V20.5 L12,16.8 L6.5,20.5 Z") }
    val locate: ImageVector by lazy { stroke("M12,3 V6 M12,18 V21 M3,12 H6 M18,12 H21 M12,12 m-5,0 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0 M12,12 m-1.4,0 a1.4,1.4 0 1,0 2.8,0 a1.4,1.4 0 1,0 -2.8,0") }
    val radiusIn: ImageVector by lazy { stroke("M9,4.5 H4.5 V9 M15,4.5 H19.5 V9 M4.5,15 V19.5 H9 M15,19.5 H19.5 V15") }
    val radiusOut: ImageVector by lazy { stroke("M4.5,9 V4.5 H9 M19.5,9 V4.5 H15 M4.5,15 V19.5 H9 M19.5,15 V19.5 H15") }
    val refresh: ImageVector by lazy { stroke("M19.5,12 A7.5,7.5 0 1,1 12,4.5 M12,2 V6.5 H16.5") }
    val close: ImageVector by lazy { stroke("M6.5,6.5 L17.5,17.5 M17.5,6.5 L6.5,17.5") }
    val back: ImageVector by lazy { stroke("M15,5.5 L8.5,12 L15,18.5") }
    val chevron: ImageVector by lazy { stroke("M6.5,9.5 L12,15 L17.5,9.5") }
    val chevronRight: ImageVector by lazy { stroke("M9.5,6.5 L15,12 L9.5,17.5") }
    val arrowRight: ImageVector by lazy { stroke("M5,12 H19 M13.5,6.5 L19,12 L13.5,17.5") }
    val toTop: ImageVector by lazy { stroke("M5.5,6 H18.5 M7.5,15.5 L12,11 L16.5,15.5 M12,11 V19") }
    val toBottom: ImageVector by lazy { stroke("M5.5,18 H18.5 M7.5,8.5 L12,13 L16.5,8.5 M12,5 V13") }
    val codes: ImageVector by lazy { stroke("M5.5,7 H18.5 M5.5,12 H18.5 M5.5,17 H13.5") }
    val mapPin: ImageVector by lazy { stroke("M12,21 C12,21 6,14.2 6,9.2 A6,6 0 0,1 18,9.2 C18,14.2 12,21 12,21 Z M12,9.2 m-2.1,0 a2.1,2.1 0 1,0 4.2,0 a2.1,2.1 0 1,0 -4.2,0") }
    val clear: ImageVector by lazy { stroke("M12,3.5 L20.5,12 L12,20.5 L3.5,12 Z M9.2,9.2 L14.8,14.8 M14.8,9.2 L9.2,14.8") }
    val swap: ImageVector by lazy { stroke("M7,8 H17.5 L14,4.5 M17,16 H6.5 L10,19.5") }
    val bothWays: ImageVector by lazy { stroke("M5,9 H19 M15.5,5.5 L19,9 L15.5,12.5 M19,15 H5 M8.5,11.5 L5,15 L8.5,18.5") }
    val go: ImageVector by lazy { stroke("M5,12 H16.5 M12,7.5 L17,12 L12,16.5 M20,5.5 V18.5") }
    val hereOrigin: ImageVector by lazy { stroke("M12,12 m-6.5,0 a6.5,6.5 0 1,0 13,0 a6.5,6.5 0 1,0 -13,0 M12,12 m-2.2,0 a2.2,2.2 0 1,0 4.4,0 a2.2,2.2 0 1,0 -4.4,0") }
    val hereDest: ImageVector by lazy { stroke("M12,20.5 C12,20.5 6.5,14 6.5,9.5 A5.5,5.5 0 0,1 17.5,9.5 C17.5,14 12,20.5 12,20.5 Z M12,9.5 m-2,0 a2,2 0 1,0 4,0 a2,2 0 1,0 -4,0") }
    val retry: ImageVector by lazy { stroke("M8,7.5 H4.5 V4 M4.5,7.5 A8,8 0 1,1 6.5,17.8") }
    val zoomIn: ImageVector by lazy { stroke("M5.5,12 H18.5 M12,5.5 V18.5") }
    val zoomOut: ImageVector by lazy { stroke("M5.5,12 H18.5") }
    val center: ImageVector by lazy { stroke("M12,3.5 V7 M12,17 V20.5 M3.5,12 H7 M17,12 H20.5 M12,12 m-2.6,0 a2.6,2.6 0 1,0 5.2,0 a2.6,2.6 0 1,0 -5.2,0") }
    val more: ImageVector by lazy { stroke("M4.5,7.5 H19.5 M4.5,12 H15 M4.5,16.5 H10.5") }
    val all: ImageVector by lazy { stroke("M4.5,4.5 H10.2 V10.2 H4.5 Z M13.8,4.5 H19.5 V10.2 H13.8 Z M4.5,13.8 H10.2 V19.5 H4.5 Z M13.8,13.8 H19.5 V19.5 H13.8 Z") }
    val widget: ImageVector by lazy { stroke("M4.5,5.5 C4.5,4.7 5.2,4 6,4 H10.2 C11,4 11.5,4.7 11.5,5.5 V10 C11.5,10.8 11,11.3 10.2,11.3 H6 C5.2,11.3 4.5,10.8 4.5,10 Z M13.8,5.5 C13.8,4.7 14.3,4 15.1,4 H18 C18.8,4 19.5,4.7 19.5,5.5 V10 C19.5,10.8 18.8,11.3 18,11.3 H15.1 C14.3,11.3 13.8,10.8 13.8,10 Z M4.5,14.8 C4.5,14 5.2,13.5 6,13.5 H18 C18.8,13.5 19.5,14 19.5,14.8 V18.5 C19.5,19.3 18.8,20 18,20 H6 C5.2,20 4.5,19.3 4.5,18.5 Z") }
    val kmb: ImageVector by lazy { stroke("M5.5,5 C5.5,3.9 6.4,3 7.5,3 H16.5 C17.6,3 18.5,3.9 18.5,5 V18 C18.5,18.8 17.8,19.5 17,19.5 H7 C6.2,19.5 5.5,18.8 5.5,18 Z M5.5,11.2 H18.5 M8.5,7.2 H11 M13,7.2 H15.5 M8.5,15 H11 M13,15 H15.5 M8,19.5 V21 M16,19.5 V21") }
    val ctb: ImageVector by lazy { stroke("M4,8.5 C4,7.1 5.1,6 6.5,6 H17.5 C18.9,6 20,7.1 20,8.5 V16.5 C20,17.3 19.3,18 18.5,18 H5.5 C4.7,18 4,17.3 4,16.5 Z M4,12.5 H20 M7.5,9.5 H10.5 M13.5,9.5 H16.5 M7.5,18 V20 M16.5,18 V20") }
    val gmb: ImageVector by lazy { stroke("M3.5,14.5 L6.2,8.5 C6.6,7.6 7.4,7 8.4,7 H15 C16.1,7 17,7.5 17.6,8.4 L20.5,13.5 V16.8 C20.5,17.5 19.9,18 19.2,18 H4.8 C4.1,18 3.5,17.5 3.5,16.8 Z M7,18 V20 M17,18 V20 M6.5,13.5 H18.5") }
    val mtr: ImageVector by lazy { stroke("M6,16.5 V7 C6,5.3 7.3,4 9,4 H15 C16.7,4 18,5.3 18,7 V16.5 C18,17.3 17.3,18 16.5,18 H7.5 C6.7,18 6,17.3 6,16.5 Z M6,11.5 H18 M9,18 L7,21 M15,18 L17,21 M9.2,14.8 H9.7 M14.3,14.8 H14.8") }
    val lightRail: ImageVector by lazy { stroke("M5.5,16.5 V9.2 C5.5,7.6 6.8,6.5 8.4,6.5 H15.6 C17.2,6.5 18.5,7.6 18.5,9.2 V16.5 C18.5,17.3 17.8,18 17,18 H7 C6.2,18 5.5,17.3 5.5,16.5 Z M5.5,12.5 H18.5 M9,6.5 L12,3.5 L15,6.5 M8.5,18 V20.5 M15.5,18 V20.5") }
    val routeCol: ImageVector by lazy { stroke("M6,4.5 C6,3.7 6.7,3 7.5,3 H16.5 C17.3,3 18,3.7 18,4.5 V19.5 C18,20.3 17.3,21 16.5,21 H7.5 C6.7,21 6,20.3 6,19.5 Z M9,8.5 H15 M9,12.5 H15 M9,16.5 H13") }
    val destCol: ImageVector by lazy { stroke("M4.5,12 H18.5 M13.5,7 L18.5,12 L13.5,17") }
    val minuteCol: ImageVector by lazy { stroke("M7,19 V9 M12,19 V5 M17,19 V12") }
    val clock: ImageVector by lazy { stroke("M12,12 m-8.2,0 a8.2,8.2 0 1,0 16.4,0 a8.2,8.2 0 1,0 -16.4,0 M12,7.5 V12 L15,14") }
    val fare: ImageVector by lazy { stroke("M12,4 V20 M15.4,8.2 C15.4,6.8 13.9,6 12,6 C10.1,6 8.6,6.8 8.6,8.2 C8.6,9.8 10.1,10.5 12,11 C13.9,11.5 15.4,12.2 15.4,14 C15.4,15.6 13.9,16.5 12,16.5 C10.1,16.5 8.6,15.6 8.6,14") }
    val exit: ImageVector by lazy { stroke("M9.5,4.5 H6 C5.2,4.5 4.5,5.2 4.5,6 V18 C4.5,18.8 5.2,19.5 6,19.5 H9.5 M14,8 L18.5,12 L14,16 M18.5,12 H9.5") }
    val transfer: ImageVector by lazy { stroke("M6,8 H18 L14.5,4.5 M18,16 H6 L9.5,19.5") }
    val check: ImageVector by lazy { stroke("M5.5,12.5 L10,17 L18.5,7.5") }
    val globe: ImageVector by lazy { stroke("M12,12 m-8.2,0 a8.2,8.2 0 1,0 16.4,0 a8.2,8.2 0 1,0 -16.4,0 M3.8,12 H20.2 M12,3.8 C14.6,6.8 14.6,17.2 12,20.2 M12,3.8 C9.4,6.8 9.4,17.2 12,20.2") }
    val city: ImageVector by lazy { stroke("M4.5,20 V8.5 L10,5.5 V20 M10,20 V10.5 H15.5 V20 M15.5,20 V13.5 H19.5 V20 M3,20 H21") }
    val info: ImageVector by lazy { stroke("M12,12 m-8.2,0 a8.2,8.2 0 1,0 16.4,0 a8.2,8.2 0 1,0 -16.4,0 M12,11 V16 M12,8.2 V8.4") }
    val distance: ImageVector by lazy { stroke("M4.5,16 H19.5 M4.5,16 V12 M19.5,16 V12 M12,16 V13") }
    val stopPole: ImageVector by lazy { stroke("M8,3.5 V20.5 M8,6 H16.5 C17.3,6 18,6.7 18,7.5 V12 C18,12.8 17.3,13.5 16.5,13.5 H8") }
    val recent: ImageVector by lazy { stroke("M12,7.5 V12 L15.5,14.2 M12,4 A8,8 0 1,0 19.2,8.5") }
    val along: ImageVector by lazy { stroke("M6,7 m-1.7,0 a1.7,1.7 0 1,0 3.4,0 a1.7,1.7 0 1,0 -3.4,0 M12,12 m-1.7,0 a1.7,1.7 0 1,0 3.4,0 a1.7,1.7 0 1,0 -3.4,0 M18,17 m-1.7,0 a1.7,1.7 0 1,0 3.4,0 a1.7,1.7 0 1,0 -3.4,0 M7.5,8.2 L10.5,10.8 M13.5,13.2 L16.5,15.8") }
    val result: ImageVector by lazy { stroke("M6,4.5 H18 V19.5 H6 Z M9,8.5 H15 M9,12.5 H15 M9,16.5 H12.5") }
    val stepWalk: ImageVector by lazy { stroke("M11.5,4.8 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 M9,20 L11,13.5 L14,15.5 L15,20 M8,11.5 L11.5,9 L15,11") }
    val stepRide: ImageVector by lazy { stroke("M4.5,15.5 H19.5 M6,15.5 V9.5 H15 L19,15.5 M12,9.5 V5.5 M8,15.5 V18.5 M16,15.5 V18.5") }
    val settings: ImageVector by lazy { stroke("M4.5,7 H19.5 M4.5,12 H19.5 M4.5,17 H19.5 M8.5,5 V9 M15.5,10 V14 M11,15 V19") }
    val language: ImageVector by lazy { stroke("M5,6.5 H19 M12,6.5 V18.5 M7.5,10.5 H16.5 M8.5,14.5 H15.5") }
    val a11y: ImageVector by lazy { stroke("M12,4.5 m-1.6,0 a1.6,1.6 0 1,0 3.2,0 a1.6,1.6 0 1,0 -3.2,0 M6,9.5 H18 M12,9.5 V14.5 M12,14.5 L8.5,19.5 M12,14.5 L15.5,19.5") }
    val largeType: ImageVector by lazy { stroke("M5.5,18 L10,6 H12 L16.5,18 M7.8,13.5 H14.2 M18,9.5 V14.5 M15.5,12 H20.5") }
    val contrast: ImageVector by lazy { stroke("M12,4 A8,8 0 1,0 12,20 A8,8 0 1,0 12,4 M12,4 V20") }
    val still: ImageVector by lazy { stroke("M7.5,6 V18 M12,8.5 V15.5 M16.5,6 V18") }
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
        strokeLineWidth = 1.85f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
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
