package com.wusper.eta.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wusper.eta.R
import com.wusper.eta.data.AppModel
import com.wusper.eta.data.Help
import com.wusper.eta.data.UiPrefs

@Composable
fun SettingsPane(model: AppModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.locateGranted()
    }
    val lang = UiPrefs.language(context)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(stringResource(R.string.settings), style = Ink.display, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            InkButton(Glyphs.locate, stringResource(R.string.label_locate), label = stringResource(R.string.label_locate)) {
                if (model.permitted(context)) model.locateGranted() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                model.closeSettings()
            }
            InkButton(Glyphs.map, stringResource(R.string.label_map), label = stringResource(R.string.label_map)) {
                model.closeSettings()
                model.openMap()
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.language, null, Ink.black, 18.dp)
                Spacer(Modifier.width(8.dp))
                BasicText(stringResource(R.string.language), style = Ink.title)
            }
            Spacer(Modifier.height(8.dp))
            LanguageChoice(Glyphs.language, "簡体中文", lang == UiPrefs.HANS) {
                applyLanguage(context, UiPrefs.HANS)
            }
            LanguageChoice(Glyphs.language, "繁體中文", lang == UiPrefs.HANT) {
                applyLanguage(context, UiPrefs.HANT)
            }
            LanguageChoice(Glyphs.language, "English", lang == UiPrefs.EN) {
                applyLanguage(context, UiPrefs.EN)
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(Glyphs.a11y, null, Ink.black, 18.dp)
                Spacer(Modifier.width(8.dp))
                BasicText(stringResource(R.string.a11y), style = Ink.title)
                Spacer(Modifier.width(6.dp))
                HelpMark { model.help = Help.A11y }
            }
            Spacer(Modifier.height(8.dp))
            InkButton(
                Glyphs.largeType,
                stringResource(R.string.a11y_large),
                selected = Ink.textScale > 1f,
                label = stringResource(R.string.a11y_large),
            ) { UiPrefs.setLargeText(context, Ink.textScale <= 1f) }
            InkButton(
                Glyphs.contrast,
                stringResource(R.string.a11y_contrast),
                selected = Ink.highContrast,
                label = stringResource(R.string.a11y_contrast),
            ) { UiPrefs.setHighContrast(context, !Ink.highContrast) }
            InkButton(
                Glyphs.still,
                stringResource(R.string.a11y_motion),
                selected = Ink.reduceMotion,
                label = stringResource(R.string.a11y_motion),
            ) { UiPrefs.setReduceMotion(context, !Ink.reduceMotion) }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().height(Ink.lineWidth).background(Ink.line))
        }
    }
}

@Composable
private fun LanguageChoice(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    InkButton(icon, label, selected = selected, label = label, onClick = onClick)
}

private fun applyLanguage(context: android.content.Context, tag: String) {
    if (UiPrefs.language(context) == tag) return
    UiPrefs.setLanguage(context, tag)
    (context as? Activity)?.recreate()
}
