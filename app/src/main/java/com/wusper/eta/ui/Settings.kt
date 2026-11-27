package com.wusper.eta.ui

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    if (model.widgetPage) {
        WidgetAdminPane(model)
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_lockup),
                contentDescription = "Wusper ETA",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(84.dp),
            )
            Spacer(Modifier.height(6.dp))
            SectionLabel(Glyphs.language, stringResource(R.string.language), null)
            LanguageChoice("繁體中文", lang == UiPrefs.HANT) { applyLanguage(context, UiPrefs.HANT) }
            LanguageChoice("簡体中文", lang == UiPrefs.HANS) { applyLanguage(context, UiPrefs.HANS) }
            LanguageChoice("English", lang == UiPrefs.EN) { applyLanguage(context, UiPrefs.EN) }
            Spacer(Modifier.height(8.dp))
            Hair()
            Spacer(Modifier.height(6.dp))
            SectionLabel(Glyphs.a11y, stringResource(R.string.a11y)) { model.help = Help.A11y }
            StreamlinedRowButton(
                icon = Glyphs.largeType,
                label = stringResource(R.string.a11y_large),
                selected = Ink.textScale > 1f,
                trailing = null,
            ) { UiPrefs.setLargeText(context, Ink.textScale <= 1f) }
            StreamlinedRowButton(
                icon = Glyphs.contrast,
                label = stringResource(R.string.a11y_contrast),
                selected = Ink.highContrast,
                trailing = null,
            ) { UiPrefs.setHighContrast(context, !Ink.highContrast) }
            StreamlinedRowButton(
                icon = Glyphs.still,
                label = stringResource(R.string.a11y_motion),
                selected = Ink.reduceMotion,
                trailing = null,
            ) { UiPrefs.setReduceMotion(context, !Ink.reduceMotion) }
            Spacer(Modifier.height(8.dp))
            Hair()
            Spacer(Modifier.height(6.dp))
            StreamlinedRowButton(
                icon = Glyphs.locate,
                label = stringResource(R.string.label_locate),
            ) {
                if (model.permitted(context)) model.locateGranted() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                model.closeSettings()
            }
            StreamlinedRowButton(
                icon = Glyphs.map,
                label = stringResource(R.string.label_map),
            ) {
                model.closeSettings()
                model.openMap()
            }
            Spacer(Modifier.height(8.dp))
            Hair()
            Spacer(Modifier.height(6.dp))
            StreamlinedRowButton(
                icon = Glyphs.widget,
                label = stringResource(R.string.widget_section),
            ) {
                model.widgetPage = true
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, label: String, onHelp: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
        SectionHead(icon, label, null)
        if (onHelp != null) {
            Spacer(Modifier.width(6.dp))
            HelpMark(onHelp)
        }
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    StreamlinedRowButton(
        icon = Glyphs.language,
        label = label,
        selected = selected,
        trailing = null,
        onClick = onClick,
    )
}

private fun applyLanguage(context: android.content.Context, tag: String) {
    if (UiPrefs.language(context) == tag) return
    UiPrefs.setLanguage(context, tag)
    (context as? Activity)?.recreate()
}
