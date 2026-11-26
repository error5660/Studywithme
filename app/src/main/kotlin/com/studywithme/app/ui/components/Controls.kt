package com.studywithme.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.studywithme.app.R
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm

private val ButtonShape = RoundedCornerShape(14.dp)

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val w = LocalWarm.current
    Box(
        modifier
            .height(52.dp)
            .clip(ButtonShape)
            .background(Brush.verticalGradient(listOf(w.accentTop, w.accent)))
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppText.bodyMedium, color = w.onAccent) }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val w = LocalWarm.current
    Box(
        modifier
            .height(52.dp)
            .clip(ButtonShape)
            .background(w.accentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppText.bodyMedium, color = w.accent) }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalWarm.current.accent) {
    Box(
        modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppText.label, color = color) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val w = LocalWarm.current
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) w.accent else w.accentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = AppText.label, color = if (selected) w.onAccent else w.accent) }
}

@Composable
fun WarmSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val w = LocalWarm.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = w.onAccent, checkedTrackColor = w.accent, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = w.inkFaint, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: @Composable () -> Unit,
) {
    val w = LocalWarm.current
    Row(modifier.defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, style = AppText.bodyMedium)
            if (description != null) Text(description, style = AppText.caption, color = w.inkSoft)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun SelectRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val w = LocalWarm.current
    Row(
        Modifier.defaultMinSize(minHeight = 48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = w.accent, unselectedColor = w.inkFaint))
        Spacer(Modifier.width(12.dp))
        Text(text, style = AppText.body)
    }
}

@Composable
fun Stepper(label: String, valueText: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val w = LocalWarm.current
    Row(Modifier.defaultMinSize(minHeight = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppText.body, modifier = Modifier.weight(1f))
        TextAction(stringResource(R.string.common_minus), onMinus)
        Text(valueText, style = AppText.number.copy(fontSize = AppText.body.fontSize), color = w.ink,
            modifier = Modifier.defaultMinSize(minWidth = 64.dp), textAlign = TextAlign.Center)
        TextAction(stringResource(R.string.common_plus), onPlus)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = AppText.section, color = LocalWarm.current.inkSoft, modifier = modifier)
}

@Composable
fun ColorDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.width(size).height(size).clip(RoundedCornerShape(50)).background(color))
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalWarm.current.inkFaint.copy(alpha = 0.35f)))
}
