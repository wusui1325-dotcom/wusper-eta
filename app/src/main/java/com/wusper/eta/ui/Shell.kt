package com.wusper.eta.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.Tab
import kotlin.math.abs

private data class CapsuleItem(val tab: Tab, val icon: ImageVector, val label: Int)

private val capsuleItems = listOf(
    CapsuleItem(Tab.Saved, Glyphs.saved, R.string.tab_saved),
    CapsuleItem(Tab.Bus, Glyphs.bus, R.string.tab_bus),
    CapsuleItem(Tab.Mtr, Glyphs.mtr, R.string.tab_mtr),
    CapsuleItem(Tab.Search, Glyphs.search, R.string.tab_search),
    CapsuleItem(Tab.Nav, Glyphs.nav, R.string.tab_nav),
)

@Composable
fun Root(model: AppModel) {
    val dark = isSystemInDarkTheme()
    if (Ink.night != dark) Ink.night = dark
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.locateGranted()
    }
    LaunchedEffect(model.started) {
        if (!model.started) return@LaunchedEffect
        if (model.permitted(context)) model.locateGranted() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    Box(Modifier.fillMaxSize().background(Ink.paper)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            if (model.detail != null) {
                RouteDetailPane(model, Modifier.weight(1f))
            } else if (model.rail != null) {
                RailPane(model, Modifier.weight(1f))
            } else {
                TopBar(model)
                if (model.widgetSizes) WidgetSizeRow { model.widgetSizes = false }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        model.showSettings -> SettingsPane(model)
                        model.showMap -> MapPane(model)
                        else -> SectionBody(model)
                    }
                }
                if (!model.showSettings) {
                    CapsuleBar(model.tab) { model.select(it) }
                }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
        val help = model.help
        if (help != null) HelpSheet(help) { model.help = null }
    }
    BackHandler(enabled = model.help != null || model.detail != null || model.rail != null || model.widgetSizes || model.widgetPage || model.showSettings || model.showMap) {
        when {
            model.help != null -> model.help = null
            model.detail != null -> model.closeDetail()
            model.rail != null -> model.closeRail()
            model.widgetSizes -> model.widgetSizes = false
            model.widgetPage -> model.widgetPage = false
            model.showSettings -> model.closeSettings()
            model.showMap -> model.closeMap()
        }
    }
}

@Composable
private fun TopBar(model: AppModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Ink.black),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Glyphs.bus, null, Ink.paper, 16.dp)
            }
            Spacer(Modifier.width(8.dp))
            BasicText("Wusper ETA", style = Ink.title)
            Spacer(Modifier.weight(1f))
            InkButton(
                Glyphs.widget,
                stringResource(R.string.widget_add),
                selected = model.widgetSizes,
                label = stringResource(R.string.widget_add),
            ) { model.widgetSizes = !model.widgetSizes }
            if (!com.wusper.eta.widget.EtaWidgets.pinSupported(LocalContext.current)) {
                Spacer(Modifier.width(4.dp))
                HelpMark { model.help = com.wusper.eta.data.Help.WidgetPin }
            }
            Spacer(Modifier.width(6.dp))
            if (model.showSettings) {
                InkButton(Glyphs.close, stringResource(R.string.cd_close), label = stringResource(R.string.cd_close)) {
                    model.closeSettings()
                }
            } else {
                InkButton(Glyphs.settings, stringResource(R.string.settings), label = stringResource(R.string.settings)) {
                    model.openSettings()
                }
            }
        }
        Hair()
    }
}

@Composable
private fun SectionBody(model: AppModel) {
    AnimatedContent(
        targetState = model.tab,
        transitionSpec = {
            if (Ink.reduceMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val forward = targetState.ordinal >= initialState.ordinal
                val glide = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                val travel = 48
                (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(340, easing = glide)) { if (forward) travel else -travel })
                    .togetherWith(
                        fadeOut(tween(180, easing = FastOutSlowInEasing)) +
                            slideOutHorizontally(tween(260, easing = glide)) { if (forward) -travel / 2 else travel / 2 },
                    )
            }
        },
        label = "section",
    ) { tab ->
        when (tab) {
            Tab.Saved -> SavedPane(model)
            Tab.Bus -> BusHome(model)
            Tab.Mtr -> MtrHome(model)
            Tab.Search -> SearchPane(model)
            Tab.Nav -> NavPane(model)
        }
    }
}

private val capsuleSpring = spring<Float>(
    dampingRatio = 0.88f,
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = 0.001f,
)

@Composable
private fun CapsuleBar(tab: Tab, onSelect: (Tab) -> Unit) {
    val index = capsuleItems.indexOfFirst { it.tab == tab }.coerceAtLeast(0)
    val shift = remember { Animatable(index.toFloat()) }
    LaunchedEffect(index, Ink.reduceMotion) {
        if (Ink.reduceMotion) shift.snapTo(index.toFloat())
        else shift.animateTo(index.toFloat(), capsuleSpring)
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
    ) {
        val barWidth = if (maxWidth > 720.dp) 640.dp else maxWidth
        val itemWidth = barWidth / capsuleItems.size
        val outerShape = RoundedCornerShape(999.dp)
        val innerShape = RoundedCornerShape(999.dp)
        Box(
            Modifier
                .width(barWidth)
                .height(68.dp)
                .align(Alignment.Center)
                .clip(outerShape)
                .background(Ink.bar)
                .border(Ink.lineWidth, Ink.line, outerShape),
        ) {
            val density = LocalDensity.current
            val inset = 5.dp
            val pillWidth = itemWidth - inset * 2
            val pillHeight = 58.dp
            val step = with(density) { itemWidth.toPx() }
            val insetPx = with(density) { inset.toPx() }
            val topPx = with(density) { ((68.dp - pillHeight) / 2).toPx() }
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = shift.value * step + insetPx
                        translationY = topPx
                    }
                    .width(pillWidth)
                    .height(pillHeight)
                    .clip(innerShape)
                    .background(Ink.barInk),
            )
            Row(Modifier.fillMaxSize()) {
                capsuleItems.forEachIndexed { i, item ->
                    val cover = selectionCover(shift.value, i)
                    val source = remember(item.tab) { MutableInteractionSource() }
                    val color = lerp(Ink.barInk, Ink.bar, cover)
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(source, indication = null) { onSelect(item.tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Glyph(item.icon, stringResource(item.label), color, 19.dp)
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            stringResource(item.label),
                            style = Ink.small.copy(color = color),
                        )
                    }
                }
            }
        }
    }
}

private fun selectionCover(position: Float, index: Int): Float {
    val span = (1f - abs(position - index)).coerceIn(0f, 1f)
    return span * span * (3f - 2f * span)
}
