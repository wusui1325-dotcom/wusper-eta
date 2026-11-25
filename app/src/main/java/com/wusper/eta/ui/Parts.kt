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
import androidx.compose.ui.draw.clip
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
        val paper = if (night) androidx.compose.ui.graphics.Color(0xFF111111) else androidx.compose.ui.graphics.Color(0xFFFFFFFF)
        val black = if (night) androidx.compose.ui.graphics.Color(0xFFFFFFFF) else androidx.compose.ui.graphics.Color(0xFF111111)
        val mute = if (contrast) black else if (night) androidx.compose.ui.graphics.Color(0xFF9A9A9A) else androidx.compose.ui.graphics.Color(0xFF6A6A6A)
        val line = if (contrast) black else if (night) androidx.compose.ui.graphics.Color(0xFF2A2A2A) else androidx.compose.ui.graphics.Color(0xFFE4E4E4)
        val wash = if (night) androidx.compose.ui.graphics.Color(0xFF1A1A1A) else androidx.compose.ui.graphics.Color(0xFFF6F6F6)
        val bar = if (night) androidx.compose.ui.graphics.Color(0xFF000000) else androidx.compose.ui.graphics.Color(0xFFFFFFFF)
        val barInk = if (night) androidx.compose.ui.graphics.Color(0xFFFFFFFF) else androidx.compose.ui.graphics.Color(0xFF111111)
        val word = TextStyle(color = black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = sans, letterSpacing = 0.4.sp)
        val title = TextStyle(color = black, fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFamily = sans)
        val body = TextStyle(color = black, fontSize = 15.sp, fontFamily = sans)
        val display = TextStyle(color = black, fontSize = 26.sp, fontWeight = FontWeight.Medium, fontFamily = sans, letterSpacing = (-0.4).sp)
        val route = TextStyle(color = black, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = sans, fontFeatureSettings = "tnum")
        val minute = TextStyle(color = black, fontSize = 20.sp, fontWeight = FontWeight.Medium, fontFamily = sans, fontFeatureSettings = "tnum")
        val arrivalRoute = TextStyle(color = black, fontSize = (32f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans, fontFeatureSettings = "tnum", letterSpacing = (-0.4).sp)
        val arrivalBound = TextStyle(color = black, fontSize = (18f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans)
        val stopLine = TextStyle(color = black, fontSize = (18f * scale).sp, fontFamily = sans)
        val stopEta = TextStyle(color = black, fontSize = (18f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans, fontFeatureSettings = "tnum")
        val arrivalStop = TextStyle(color = mute, fontSize = (16f * scale).sp, fontFamily = sans)
        val arrivalEta = TextStyle(color = black, fontSize = (26f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans, fontFeatureSettings = "tnum")
        val meta = TextStyle(color = mute, fontSize = 12.sp, fontFamily = sans, fontFeatureSettings = "tnum")
        val small = TextStyle(color = black, fontSize = 12.sp, fontFamily = sans)
        val detailTitle = TextStyle(color = black, fontSize = (18f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans)
        val detailStop = TextStyle(color = black, fontSize = (22f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans)
        val detailEta = TextStyle(color = black, fontSize = (20f * scale).sp, fontWeight = FontWeight.Medium, fontFamily = sans, fontFeatureSettings = "tnum")
        val detailMeta = TextStyle(color = mute, fontSize = (15f * scale).sp, fontFamily = sans)
        val toolLabel = TextStyle(color = black, fontSize = (13f * scale).sp, fontFamily = sans)
        val toolLabelOn = TextStyle(color = paper, fontSize = (13f * scale).sp, fontFamily = sans)
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
    val lineWidth get() = if (highContrast) 2.dp else 1.dp
    val wash get() = type().wash
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
fun Glyph(icon: ImageVector, cd: String?, tint: androidx.compose.ui.graphics.Color, size: Dp = 22.dp) {
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
    Row(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) Ink.black else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, cd, fg, 18.dp)
        Spacer(Modifier.width(4.dp))
        BasicText(text, style = Ink.small.copy(color = fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun HelpMark(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(16.dp)
            .border(1.dp, Ink.black, CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("?", style = TextStyle(color = Ink.black, fontSize = 10.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
fun SectionHead(icon: ImageVector, name: String, help: Help? = null, onHelp: (Help) -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, null, Ink.black, 18.dp)
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
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.line))
}

@Composable
fun Rule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.black))
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
    BasicText(text, style = Ink.meta)
}

@Composable
fun FieldLine(value: String, hint: String, onValue: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) {
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
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.black))
}

@Composable
fun MinutesRow(state: EtaState, etas: List<EtaSlot>, onHelp: (Help) -> Unit) {
    when (state) {
        EtaState.Loading, EtaState.Failed -> BasicText("–", style = Ink.minute)
        EtaState.Ready -> {
            val shown = etas.take(3)
            if (shown.isEmpty() || shown.all { it.minutes == null }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(stringResource(R.string.none), style = Ink.small)
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
        },
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0x66000000))
            .clickable(remember { MutableInteractionSource() }, null) { onClose() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Ink.paper)
                .padding(20.dp)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(title, style = Ink.title, modifier = Modifier.weight(1f))
                InkButton(Glyphs.close, stringResource(R.string.cd_close), onClick = onClose)
            }
            Spacer(Modifier.height(10.dp))
            BasicText(body, style = Ink.body)
        }
    }
}
