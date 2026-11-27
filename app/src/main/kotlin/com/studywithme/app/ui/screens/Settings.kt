package com.studywithme.app.ui.screens

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.studywithme.app.R
import com.studywithme.app.data.QuietMode
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.LocalSettings
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.Hairline
import com.studywithme.app.ui.components.PrimaryButton
import com.studywithme.app.ui.components.SectionTitle
import com.studywithme.app.ui.components.SoftButton
import kotlinx.coroutines.launch
import com.studywithme.app.ui.components.SelectRow
import com.studywithme.app.ui.components.SettingRow
import com.studywithme.app.ui.components.Stepper
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.WarmSwitch
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm

@Composable
fun SettingsScreen(padding: PaddingValues, onOpenLock: () -> Unit) {
    val layout = rememberLayout()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = AppText.title)
        if (layout.twoPane) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { LockCard(onOpenLock); GoalCard(); DisplayCard(); MusicCard(); NotificationCard(); BackupCard(); TestAlertCard() }
                Column(Modifier.weight(1f)) { QuietCard() }
            }
        } else {
            LockCard(onOpenLock)
            GoalCard()
            DisplayCard()
            QuietCard()
            MusicCard()
            NotificationCard()
            BackupCard()
            TestAlertCard()
        }
    }
}

@Composable
private fun DisplayCard() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    GlassCard(Modifier.fillMaxWidth()) {
        SettingRow(stringResource(R.string.settings_night_mode), description = stringResource(R.string.settings_night_mode_desc)) {
            WarmSwitch(s.night) { on -> c.updateSettings { it.copy(night = on) } }
        }
    }
}

@Composable
private fun QuietCard() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val w = LocalWarm.current
    var showGuide by remember { mutableStateOf(false) }
    // Re-check the permission whenever the user comes back from the system settings page.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val granted = remember(resumes) { c.dnd.hasAccess() }

    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.settings_quiet_title))
        Text(stringResource(R.string.settings_quiet_desc), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 4.dp))
        SelectRow(stringResource(R.string.quiet_normal), s.quiet == QuietMode.Normal) { c.updateSettings { it.copy(quiet = QuietMode.Normal) } }
        SelectRow(stringResource(R.string.quiet_vibrate), s.quiet == QuietMode.Vibrate) { c.updateSettings { it.copy(quiet = QuietMode.Vibrate) } }
        SelectRow(stringResource(R.string.quiet_silent), s.quiet == QuietMode.Silent) { c.updateSettings { it.copy(quiet = QuietMode.Silent) } }
        Hairline(Modifier.padding(vertical = 8.dp))
        SettingRow(stringResource(R.string.settings_dnd), description = stringResource(R.string.settings_dnd_desc)) {
            WarmSwitch(s.autoDnd) { on ->
                c.updateSettings { it.copy(autoDnd = on) }
                if (on && !granted) showGuide = true
            }
        }
        if (s.autoDnd) {
            Row2(granted, w.accent, w.inkSoft) { showGuide = true }
        }
    }
    if (showGuide) DndGuideDialog { showGuide = false }
}

@Composable
private fun Row2(granted: Boolean, accent: androidx.compose.ui.graphics.Color, soft: androidx.compose.ui.graphics.Color, onGuide: () -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            stringResource(if (granted) R.string.dnd_status_granted else R.string.dnd_status_needed),
            style = AppText.caption, color = if (granted) soft else accent, modifier = Modifier.weight(1f),
        )
        if (!granted) TextAction(stringResource(R.string.dnd_guide_open), onGuide)
    }
}

@Composable
private fun DndGuideDialog(onDismiss: () -> Unit) {
    val w = LocalWarm.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = w.dialog, titleContentColor = w.ink, textContentColor = w.ink,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        title = { Text(stringResource(R.string.dnd_guide_title), style = AppText.bodyMedium) },
        text = { Text(stringResource(R.string.dnd_guide_steps), style = AppText.body) },
        confirmButton = {
            TextAction(stringResource(R.string.dnd_open_settings), {
                context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                onDismiss()
            })
        },
        dismissButton = { TextAction(stringResource(R.string.common_close), onDismiss, color = w.inkSoft) },
    )
}

@Composable
private fun TestAlertCard() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val w = LocalWarm.current
    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_test_alert_desc), style = AppText.caption, color = w.inkSoft)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(stringResource(R.string.settings_test_alert), { c.alerter.alert(s.quiet) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun NotificationCard() {
    val context = LocalContext.current
    val w = LocalWarm.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val on = remember(resumes) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.settings_notif_title))
        Text(stringResource(R.string.settings_notif_desc), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
        Text(stringResource(if (on) R.string.settings_notif_on else R.string.settings_notif_off), style = AppText.body, color = if (on) w.ink else w.accent)
        if (!on) {
            TextAction(stringResource(R.string.settings_notif_open), {
                context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
            })
        }
    }
}

@Composable
private fun GoalCard() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val w = LocalWarm.current
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.settings_goal_title))
        Text(stringResource(R.string.settings_goal_desc), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 4.dp))
        val minutes = s.dailyGoalMin
        Stepper(
            stringResource(R.string.settings_goal_title), stringResource(R.string.duration_hm, minutes / 60, minutes % 60),
            { c.updateSettings { it.copy(dailyGoalMin = (it.dailyGoalMin - 30).coerceAtLeast(30)) } },
            { c.updateSettings { it.copy(dailyGoalMin = (it.dailyGoalMin + 30).coerceAtMost(720)) } },
        )
    }
}

@Composable
private fun BackupCard() {
    val c = LocalContainer.current
    val w = LocalWarm.current
    var status by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) c.scope.launch { busy = true; status = if (runCatching { c.backup.export(uri) }.isSuccess) R.string.backup_export_done else R.string.backup_export_fail; busy = false }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.scope.launch { busy = true; status = if (runCatching { c.backup.import(uri) }.isSuccess) R.string.backup_import_done else R.string.backup_import_fail; busy = false }
    }
    val fileName = stringResource(R.string.backup_file_name, java.time.LocalDate.now().toString())
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.backup_title))
        Text(stringResource(R.string.backup_desc), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(stringResource(R.string.backup_export), { if (!busy) export.launch(fileName) }, Modifier.weight(1f))
            SoftButton(stringResource(R.string.backup_import), { if (!busy) confirmImport = true }, Modifier.weight(1f))
        }
        if (busy) Text(stringResource(R.string.backup_working), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 10.dp))
        status?.let { if (!busy) Text(stringResource(it), style = AppText.caption, color = if (it == R.string.backup_import_fail || it == R.string.backup_export_fail) w.accent else w.inkSoft, modifier = Modifier.padding(top = 10.dp)) }
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            containerColor = w.dialog, titleContentColor = w.ink, textContentColor = w.inkSoft,
            title = { Text(stringResource(R.string.backup_confirm_title), style = AppText.bodyMedium) },
            text = { Text(stringResource(R.string.backup_confirm_body), style = AppText.body) },
            confirmButton = { TextAction(stringResource(R.string.backup_import), { confirmImport = false; import.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }) },
            dismissButton = { TextAction(stringResource(R.string.common_cancel), { confirmImport = false }, color = w.inkSoft) },
        )
    }
}

/** Notification access lets the timer show the song playing in Spotify; this card explains and opens the setting. */
@Composable
private fun MusicCard() {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val granted = remember(resumes) { c.nowPlaying.hasAccess() }
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.music_title))
        Text(stringResource(R.string.music_desc), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
        Text(stringResource(if (granted) R.string.music_on else R.string.music_off), style = AppText.body, color = if (granted) w.ink else w.accent)
        if (!granted) {
            Text(stringResource(R.string.music_steps), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 8.dp))
            TextAction(stringResource(R.string.music_open), {
                val component = android.content.ComponentName(context, com.studywithme.app.music.MediaListener::class.java).flattenToString()
                val direct = Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(AndroidSettings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
                runCatching { context.startActivity(direct) }.onFailure { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            })
        }
    }
}
