package com.wusper.eta.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wusper.eta.R
import com.wusper.eta.data.EtaSlot
import com.wusper.eta.data.EtaState
import com.wusper.eta.data.Help
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Ink {
    var night by mutableStateOf(false)
    var textScale by mutableStateOf(1f)
    var highContrast by mutableStateOf(false)
    var reduceMotion by mutableStateOf(false)
    private val sans = FontFamily.SansSerif

    private class Type(val night: Boolean, val contrast: Boolean, val scale: Float) {
        val paper = if (night) Color(0xFF0B0B0C) else Color(0xFFFFFFFF)
        val black = if (night) Color(0xFFFFFFFF) else Color(0xFF0B0B0C)
        val mute = if (contrast) black else if (night) Color(0xFF98989D) else Color(0xFF6E6E73)
        val line = if (contrast) black else if (night) Color(0xFF27272A) else Color(0xFFE4E4E7)
        val wash = if (night) Color(0xFF161618) else Color(0xFFF5F5F7)
        val surface = if (night) Color(0xFF1E1E22) else Color(0xFFECECF0)
        val bar = if (night) Color(0xFF141416) else Color(0xFFFFFFFF)
        val barInk = if (night) Color(0xFFFFFFFF) else Color(0xFF0B0B0C)
        val word = TextStyle(color = black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = sans, letterSpacing = 0.2.sp)
        val title = TextStyle(color = black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = sans, letterSpacing = (-0.2).sp)
        val body = TextStyle(color = black, fontSize = 15.sp, fontWeight = FontWeight.Normal, fontFamily = sans)
        val display = TextStyle(color = black, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = sans, letterSpacing = (-0.6).sp)
        val route = TextStyle(color = black, fontSize = 19.sp, fontWeight = FontWeight.Bold, fontFamily = sans, fontFeatureSettings = "tnum", letterSpacing = (-0.3).sp)
        val minute = TextStyle(color = black, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, fontFamily = sans, fontFeatureSettings = "tnum")
        val arrivalRoute = TextStyle(color = black, fontSize = (24f * scale).sp, fontWeight = FontWeight.Bold, fontFamily = sans, fontFeatureSettings = "tnum", letterSpacing = (-0.4).sp)
        val arrivalBound = TextStyle(color = black, fontSize = (16f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans)
        val stopLine = TextStyle(color = black, fontSize = (17f * scale).sp, fontFamily = sans)
        val stopEta = TextStyle(color = black, fontSize = (17f * scale).sp, fontWeight = FontWeight.SemiBold, fontFamily = sans, fontFeatureSettings = "tnum")
        val arrivalStop = TextStyle(color = mute, fontSize = (14f * scale).sp, fontFamily = sans)
        val arrivalEta = TextStyle(color = black, fontSize = (23f * scale).sp, fontWeight = FontWeight.Bold, fontFamily = sans, fontFeatureSettings = "tnum", letterSpacing = (-0.3).sp)
        val meta = TextStyle(color = mute, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = sans, fontFeatureSettings = "tnum")
        val small = TextStyle(color = black, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = sans)
        val detailTitle = TextStyle(color = black, fontSize = (18f * scale).sp, fontWeight = FontWeight.Bold, fontFamily = sans, letterSpacing = (-0.2).sp)
        val detailStop = TextStyle(color = black, fontSize = (18f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans)
        val detailEta = TextStyle(color = black, fontSize = (18f * scale).sp, fontWeight = FontWeight.Bold, fontFamily = sans, fontFeatureSettings = "tnum")
        val detailMeta = TextStyle(color = mute, fontSize = (14f * scale).sp, fontFamily = sans)
        val toolLabel = TextStyle(color = black, fontSize = (11.5f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans)
        val toolLabelOn = TextStyle(color = paper, fontSize = (11.5f * scale).sp, fontWeight = FontWeight.SemiBold, fontFamily = sans)
    }

    private var cached = Type(false, false, 1f)

    private fun type(): Type {
        val mode = night
        val contrast = highContrast
        val scale = textScale
        val current = cached
        if (current.night != mode || current.contrast != contrast || current.scale != scale) {
            cached = Type(mode, contrast, scale)
        }
        return cached
    }

    val paper get() = type().paper
    val black get() = type().black
    val mute get() = type().mute
    val line get() = type().line
    val lineWidth get() = if (highContrast) 1.75.dp else 1.dp
    val wash get() = type().wash
    val surface get() = type().surface
    val bar get() = type().bar
    val barInk get() = type().barInk
    val word get() = type().word
    val title get() = type().title
    val body get() = type().body
    val display get() = type().display
    val route get() = type().route
    val minute get() = type().minute
    val arrivalRoute get() = type().arrivalRoute
    val arrivalBound get() = type().arrivalBound
    val stopLine get() = type().stopLine
    val stopEta get() = type().stopEta
    val arrivalStop get() = type().arrivalStop
    val arrivalEta get() = type().arrivalEta
    val meta get() = type().meta
    val small get() = type().small
    val detailTitle get() = type().detailTitle
    val detailStop get() = type().detailStop
    val detailEta get() = type().detailEta
    val detailMeta get() = type().detailMeta
    val toolLabel get() = type().toolLabel
    val toolLabelOn get() = type().toolLabelOn
}

@Composable
fun MapGestureHints(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.paper.copy(alpha = 0.94f))
            .border(Ink.lineWidth, Ink.line, RoundedCornerShape(16.dp))
            .clickable(source, indication = null) { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(Glyphs.info, null, Ink.black, 14.dp)
            Spacer(Modifier.width(5.dp))
            BasicText("點擊站點 / 縮放手勢", style = Ink.small)
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            BasicText("點擊任意站或線路查看", style = Ink.meta)
            BasicText("雙指捏合或伸開以縮放", style = Ink.meta, modifier = Modifier.padding(top = 2.dp))
            BasicText("單指按住並拖動以移動", style = Ink.meta, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
fun Glyph(icon: ImageVector, cd: String?, tint: Color, size: Dp = 20.dp) {
    Image(
        imageVector = icon,
        contentDescription = cd,
        modifier = Modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun InkButton(
    icon: ImageVector,
    cd: String,
    selected: Boolean = false,
    label: String? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val on = selected || pressed
    val text = label ?: cd
    val fg = if (on) Ink.paper else Ink.black
    val bg = if (on) Ink.black else Ink.wash
    val strokeColor = if (on) Ink.black else Ink.line
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(bg)
            .border(Ink.lineWidth, strokeColor, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, cd, fg, 16.dp)
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(5.dp))
            BasicText(text, style = Ink.small.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun StreamlinedRowButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    trailing: ImageVector? = Glyphs.chevronRight,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val on = selected || pressed
    val fg = if (on) Ink.paper else Ink.black
    val subFg = if (on) Ink.paper.copy(alpha = 0.72f) else Ink.mute
    val bg = if (on) Ink.black else Ink.wash
    val strokeColor = if (on) Ink.black else Ink.line
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(bg)
            .border(Ink.lineWidth, strokeColor, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (on) Ink.paper.copy(alpha = 0.16f) else Ink.paper),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, null, fg, 16.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(label, style = Ink.body.copy(color = fg, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                BasicText(subtitle, style = Ink.meta.copy(color = subFg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val endIcon = if (selected) Glyphs.check else trailing
        if (endIcon != null) {
            Spacer(Modifier.width(8.dp))
            Glyph(endIcon, null, if (on) fg else Ink.mute, 16.dp)
        }
    }
}

@Composable
fun StreamlinedPill(
    text: String,
    icon: ImageVector? = null,
    inverted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val bg = if (inverted) Ink.black else Ink.wash
    val fg = if (inverted) Ink.paper else Ink.black
    val borderColor = if (inverted) Ink.black else Ink.line
    Row(
        modifier
            .clip(shape)
            .background(bg)
            .border(Ink.lineWidth, borderColor, shape)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Glyph(icon, null, fg, 13.dp)
            Spacer(Modifier.width(4.dp))
        }
        BasicText(text, style = Ink.small.copy(color = fg, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
fun HelpMark(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Ink.wash)
            .border(Ink.lineWidth, Ink.line, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("?", style = TextStyle(color = Ink.mute, fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun SectionHead(icon: ImageVector, name: String, help: Help? = null, onHelp: (Help) -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Ink.wash)
                .border(Ink.lineWidth, Ink.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, null, Ink.black, 14.dp)
        }
        Spacer(Modifier.width(8.dp))
        BasicText(name, style = Ink.title)
        if (help != null) {
            Spacer(Modifier.width(6.dp))
            HelpMark { onHelp(help) }
        }
    }
}

@Composable
fun Hair() {
    Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
}

@Composable
fun Rule() {
    Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.black))
}

@Composable
fun HkClock() {
    fun now() = DateTimeFormatter.ofPattern("HH:mm").format(ZonedDateTime.now(ZoneId.of("Asia/Hong_Kong")))
    var text by remember { mutableStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            text = now()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(Glyphs.clock, null, Ink.mute, 13.dp)
        Spacer(Modifier.width(4.dp))
        BasicText(text, style = Ink.meta)
    }
}

@Composable
fun FieldLine(value: String, hint: String, onValue: (String) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(Ink.wash)
            .border(Ink.lineWidth, Ink.line, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) BasicText(hint, style = Ink.meta)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                textStyle = Ink.body,
                singleLine = true,
                cursorBrush = SolidColor(Ink.black),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun MinutesRow(state: EtaState, etas: List<EtaSlot>, onHelp: (Help) -> Unit) {
    when (state) {
        EtaState.Loading, EtaState.Failed -> BasicText("–", style = Ink.minute)
        EtaState.Ready -> {
            val shown = etas.take(3)
            if (shown.isEmpty() || shown.all { it.minutes == null }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreamlinedPill(stringResource(R.string.none))
                    Spacer(Modifier.width(4.dp))
                    HelpMark { onHelp(Help.None) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { slot ->
                        if (slot.minutes == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(stringResource(R.string.none), style = Ink.small)
                                Spacer(Modifier.width(2.dp))
                                HelpMark { onHelp(Help.None) }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText("${slot.minutes}′", style = Ink.minute)
                                if (slot.scheduled) {
                                    Spacer(Modifier.width(2.dp))
                                    HelpMark { onHelp(Help.Scheduled) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HelpSheet(topic: Help, onClose: () -> Unit) {
    val title = stringResource(
        when (topic) {
            Help.Nearby -> R.string.help_nearby_t
            Help.Gmb -> R.string.help_gmb_t
            Help.Scheduled -> R.string.help_sched_t
            Help.None -> R.string.help_none_t
            Help.MtrSide -> R.string.help_side_t
            Help.Service -> R.string.help_svc_t
            Help.Minutes -> R.string.help_min_t
            Help.Mock -> R.string.help_mock_t
            Help.Schematic -> R.string.help_map_t
            Help.MtrGeo -> R.string.help_geo_t
            Help.A11y -> R.string.help_a11y_t
            Help.WidgetAuto -> R.string.help_widget_auto_t
            Help.WidgetPin -> R.string.help_widget_pin_t
        },
    )
    val body = stringResource(
        when (topic) {
            Help.Nearby -> R.string.help_nearby_b
            Help.Gmb -> R.string.help_gmb_b
            Help.Scheduled -> R.string.help_sched_b
            Help.None -> R.string.help_none_b
            Help.MtrSide -> R.string.help_side_b
            Help.Service -> R.string.help_svc_b
            Help.Minutes -> R.string.help_min_b
            Help.Mock -> R.string.help_mock_b
            Help.Schematic -> R.string.help_map_b
            Help.MtrGeo -> R.string.help_geo_b
            Help.A11y -> R.string.help_a11y_b
            Help.WidgetAuto -> R.string.help_widget_auto_b
            Help.WidgetPin -> R.string.help_widget_pin_b
        },
    )
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x73000000))
            .clickable(remember { MutableInteractionSource() }, null) { onClose() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(sheetShape)
                .background(Ink.paper)
                .border(Ink.lineWidth, Ink.line, sheetShape)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ink.line),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.info, null, Ink.black, 18.dp)
                Spacer(Modifier.width(8.dp))
                BasicText(title, style = Ink.title, modifier = Modifier.weight(1f))
                InkButton(Glyphs.close, stringResource(R.string.cd_close), label = stringResource(R.string.cd_close), onClick = onClose)
            }
            Spacer(Modifier.height(12.dp))
            BasicText(body, style = Ink.body)
            Spacer(Modifier.height(12.dp))
        }
    }
}
