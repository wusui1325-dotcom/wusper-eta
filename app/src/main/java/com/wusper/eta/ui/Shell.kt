package com.wusper.eta.ui

import android.Manifest
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
            } else {
                TopBar(model)
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
    BackHandler(enabled = model.help != null || model.detail != null || model.showSettings || model.showMap) {
        when {
            model.help != null -> model.help = null
            model.detail != null -> model.closeDetail()
            model.showSettings -> model.closeSettings()
            model.showMap -> model.closeMap()
        }
    }
}

@Composable
private fun TopBar(model: AppModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("Wusper ETA", style = Ink.title, modifier = Modifier.weight(1f))
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
                val travel = 56
                (fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(420, easing = glide)) { if (forward) travel else -travel })
                    .togetherWith(
                        fadeOut(tween(220, easing = FastOutSlowInEasing)) +
                            slideOutHorizontally(tween(320, easing = glide)) { if (forward) -travel / 2 else travel / 2 },
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
    dampingRatio = 0.92f,
    stiffness = Spring.StiffnessLow,
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
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
    ) {
        val barWidth = if (maxWidth > 720.dp) 640.dp else maxWidth
        val itemWidth = barWidth / capsuleItems.size
        val shape = RoundedCornerShape(36.dp)
        val shadow = if (Ink.night) Color.White else Color.Black
        Box(
            Modifier
                .width(barWidth)
                .height(72.dp)
                .align(Alignment.Center)
                .shadow(
                    elevation = 18.dp,
                    shape = shape,
                    ambientColor = shadow.copy(alpha = if (Ink.night) 0.55f else 0.28f),
                    spotColor = shadow.copy(alpha = if (Ink.night) 0.35f else 0.42f),
                )
                .clip(shape)
                .background(Ink.bar),
        ) {
            val density = LocalDensity.current
            val inset = 5.dp
            val pillWidth = itemWidth - inset * 2
            val pillHeight = 62.dp
            val step = with(density) { itemWidth.toPx() }
            val insetPx = with(density) { inset.toPx() }
            val topPx = with(density) { ((72.dp - pillHeight) / 2).toPx() }
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = shift.value * step + insetPx
                        translationY = topPx
                    }
                    .width(pillWidth)
                    .height(pillHeight)
                    .background(Ink.barInk, RoundedCornerShape(31.dp)),
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
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Glyph(item.icon, stringResource(item.label), color, 20.dp)
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
