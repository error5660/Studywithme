package com.studywithme.app.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.lock.AdminReceiver
import com.studywithme.app.lock.LockPermissions
import com.studywithme.app.lock.LockPolicy
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.Chip
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.Hairline
import com.studywithme.app.ui.components.LabeledTextField
import com.studywithme.app.ui.components.PrimaryButton
import com.studywithme.app.ui.components.SectionTitle
import com.studywithme.app.ui.components.SoftButton
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.WarmDialog
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Durations = listOf(5 to R.string.lock_dur_5, 30 to R.string.lock_dur_30, 60 to R.string.lock_dur_60, 120 to R.string.lock_dur_120, 180 to R.string.lock_dur_180, 240 to R.string.lock_dur_240, 360 to R.string.lock_dur_360)

/** English letters only, at most 4. */
private val PasswordFilter = InputTransformation {
    if (!asCharSequence().all { it in 'a'..'z' || it in 'A'..'Z' }) revertAllChanges()
}.then(InputTransformation.maxLengthTrim(4))

private val RecoveryFilter = InputTransformation {
    if (!asCharSequence().all { it.isLetterOrDigit() || it == '-' }) revertAllChanges()
}.then(InputTransformation.maxLengthTrim(14))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockSetupScreen(onClose: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val layout = rememberLayout()
    val scope = rememberCoroutineScope()
    val lock by c.lock.state.collectAsStateWithLifecycle(null)
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val earlyCount by c.db.earlyUnlockDao().observeCount().collectAsStateWithLifecycle(0)
    val context = LocalContext.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val perms = remember(resumes) { LockPermissions.read(context) }

    var durationMin by rememberSaveable { mutableIntStateOf(60) }
    var subjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    var page by rememberSaveable { mutableIntStateOf(PAGE_MAIN) }
    var gateFor by remember { mutableStateOf<Int?>(null) } // page to open once the password is confirmed
    var recoveryCode by remember { mutableStateOf<String?>(null) }
    var showLimits by remember { mutableStateOf(false) }

    BackHandler { if (page != PAGE_MAIN) page = PAGE_MAIN else onClose() }
    val state = lock ?: return

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.lock_setup_title), style = AppText.title, modifier = Modifier.weight(1f))
            TextAction(stringResource(R.string.common_close), { if (page != PAGE_MAIN) page = PAGE_MAIN else onClose() })
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            when {
                !state.hasPassword -> PasswordForm(creating = true) { pw -> recoveryCode = c.lock.createPassword(pw) }
                page == PAGE_PASSWORD -> PasswordForm(creating = false) { pw -> c.lock.changePassword(pw); page = PAGE_MAIN }
                page == PAGE_ALLOWLIST -> AllowlistEditor(state.allowlist)
                layout.twoPane -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { StartCard(durationMin, { durationMin = it }, subjectId, { subjectId = it }, perms) { scope.launch { c.lock.start(durationMin, subjectId ?: subjects.firstOrNull()?.id); onClose() } } }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        PermissionCard(perms)
                        ManageCard(earlyCount, { gateFor = PAGE_ALLOWLIST }, { gateFor = PAGE_PASSWORD }, { showLimits = true })
                    }
                }
                else -> {
                    StartCard(durationMin, { durationMin = it }, subjectId, { subjectId = it }, perms) { scope.launch { c.lock.start(durationMin, subjectId ?: subjects.firstOrNull()?.id); onClose() } }
                    PermissionCard(perms)
                    ManageCard(earlyCount, { gateFor = PAGE_ALLOWLIST }, { gateFor = PAGE_PASSWORD }, { showLimits = true })
                }
            }
        }
    }

    gateFor?.let { target -> PasswordGateDialog(onDismiss = { gateFor = null }, onVerified = { page = target; gateFor = null }) }
    recoveryCode?.let { code ->
        WarmDialog(
            title = stringResource(R.string.recovery_title), onDismiss = {}, confirmText = stringResource(R.string.recovery_done), onConfirm = { recoveryCode = null },
            dismissText = "",
        ) {
            Text(stringResource(R.string.recovery_body), style = AppText.body, color = w.inkSoft)
            Text(code, style = AppText.hero.copy(fontSize = androidx.compose.ui.unit.TextUnit(30f, androidx.compose.ui.unit.TextUnitType.Sp)), modifier = Modifier.fillMaxWidth())
        }
    }
    if (showLimits) {
        WarmDialog(title = stringResource(R.string.limits_title), onDismiss = { showLimits = false }, confirmText = stringResource(R.string.common_close), onConfirm = { showLimits = false }, dismissText = "") {
            Text(stringResource(R.string.limits_body), style = AppText.body)
        }
    }
}

private const val PAGE_MAIN = 0
private const val PAGE_ALLOWLIST = 1
private const val PAGE_PASSWORD = 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartCard(
    durationMin: Int, onDuration: (Int) -> Unit,
    subjectId: Long?, onSubject: (Long?) -> Unit,
    perms: LockPermissions, onStart: () -> Unit,
) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.lock_duration_title))
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Durations.forEach { (min, label) -> Chip(stringResource(label), durationMin == min, { onDuration(min) }) }
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.lock_subject_title))
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEachIndexed { i, s -> Chip(s.name, (subjectId ?: subjects.first().id) == s.id, { onSubject(s.id) }) }
        }
        Spacer(Modifier.height(24.dp))
        if (perms.ready) {
            PrimaryButton(stringResource(R.string.lock_start), onStart, Modifier.fillMaxWidth())
            Text(stringResource(R.string.lock_start_hint), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 10.dp))
        } else {
            Text(stringResource(R.string.lock_start_blocked), style = AppText.bodyMedium, color = w.accent)
        }
    }
}

@Composable
private fun PermissionCard(perms: LockPermissions) {
    val context = LocalContext.current
    val adminExplain = stringResource(R.string.admin_disable_warning)
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.lock_permissions_title))
        PermissionStep(R.string.perm_restricted_title, R.string.perm_restricted_body, if (perms.accessibility) PermState.Ok else PermState.Unknown, R.string.perm_open_appinfo) {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        }
        Hairline()
        PermissionStep(R.string.perm_a11y_title, R.string.perm_a11y_body, if (perms.accessibility) PermState.Ok else PermState.Todo, R.string.perm_open_a11y) {
            context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        Hairline()
        PermissionStep(R.string.perm_admin_title, R.string.perm_admin_body, if (perms.admin) PermState.Ok else PermState.Todo, R.string.perm_open_admin) {
            context.startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, AdminReceiver::class.java))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, adminExplain),
            )
        }
        Hairline()
        PermissionStep(R.string.perm_notif_title, R.string.perm_notif_body, if (perms.notifications) PermState.Ok else PermState.Todo, null) {}
        Hairline()
        PermissionStep(R.string.perm_battery_title, R.string.perm_battery_body, PermState.Unknown, R.string.perm_open_appinfo) {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        }
    }
}

private enum class PermState { Ok, Todo, Unknown }

@Composable
private fun PermissionStep(title: Int, body: Int, state: PermState, actionLabel: Int?, onAction: () -> Unit) {
    val w = LocalWarm.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(title), style = AppText.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(when (state) { PermState.Ok -> R.string.perm_ok; PermState.Todo -> R.string.perm_todo; PermState.Unknown -> R.string.perm_check_yourself }),
                style = AppText.caption, color = if (state == PermState.Todo) w.accent else w.inkSoft,
            )
        }
        if (state != PermState.Ok) {
            Text(stringResource(body), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 4.dp))
            if (actionLabel != null) TextAction(stringResource(actionLabel), onAction, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ManageCard(earlyCount: Int, onAllowlist: () -> Unit, onPassword: () -> Unit, onLimits: () -> Unit) {
    val w = LocalWarm.current
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        ManageRow(stringResource(R.string.lock_edit_allowlist), onAllowlist)
        Hairline()
        ManageRow(stringResource(R.string.lock_change_password), onPassword)
        Hairline()
        ManageRow(stringResource(R.string.lock_limits_open), onLimits)
        Text(stringResource(R.string.lock_early_count, earlyCount), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun ManageRow(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = AppText.body)
    }
}

/** Create (first time) or change the password: type it twice. */
@Composable
private fun PasswordForm(creating: Boolean, onSubmit: suspend (String) -> Unit) {
    val w = LocalWarm.current
    val scope = rememberCoroutineScope()
    val first = rememberTextFieldState()
    val second = rememberTextFieldState()
    var error by remember { mutableIntStateOf(0) } // 0 none, 1 invalid, 2 mismatch
    GlassCard(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
        Text(stringResource(if (creating) R.string.pw_create_title else R.string.lock_change_password), style = AppText.bodyMedium)
        Text(stringResource(R.string.pw_create_desc), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        LabeledTextField(stringResource(R.string.pw_label), first, transformation = PasswordFilter, keyboardType = KeyboardType.Password)
        Spacer(Modifier.height(16.dp))
        LabeledTextField(stringResource(R.string.pw_confirm_label), second, transformation = PasswordFilter, keyboardType = KeyboardType.Password)
        if (error != 0) Text(stringResource(if (error == 1) R.string.pw_invalid else R.string.pw_mismatch), style = AppText.caption, color = w.accent, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(20.dp))
        PrimaryButton(stringResource(R.string.pw_confirm), {
            val a = first.text.toString()
            error = when {
                a.length != 4 -> 1
                a != second.text.toString() -> 2
                else -> 0
            }
            if (error == 0) scope.launch { onSubmit(a) }
        }, Modifier.fillMaxWidth())
    }
}

/** Asks for the password (or the recovery code) and calls [onVerified] when it is right. */
@Composable
fun PasswordGateDialog(onDismiss: () -> Unit, onVerified: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val scope = rememberCoroutineScope()
    val field = rememberTextFieldState()
    var recovery by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var lockout by remember { mutableIntStateOf(c.lock.lockoutSeconds()) }
    LaunchedEffect(lockout) { if (lockout > 0) { delay(1000); lockout = c.lock.lockoutSeconds() } }

    WarmDialog(
        title = stringResource(R.string.pw_gate_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.pw_confirm),
        confirmEnabled = lockout == 0 && field.text.isNotBlank(),
        onConfirm = {
            scope.launch {
                if (c.lock.verify(field.text.toString())) onVerified() else { wrong = true; lockout = c.lock.lockoutSeconds() }
            }
        },
    ) {
        LabeledTextField(
            stringResource(if (recovery) R.string.pw_recovery_label else R.string.pw_label), field,
            transformation = if (recovery) RecoveryFilter else PasswordFilter, keyboardType = KeyboardType.Password,
        )
        if (lockout > 0) Text(stringResource(R.string.pw_locked_out, lockout), style = AppText.caption, color = w.accent)
        else if (wrong) Text(stringResource(R.string.pw_wrong), style = AppText.caption, color = w.accent)
        TextAction(
            stringResource(if (recovery) R.string.pw_use_password else R.string.pw_forgot),
            { recovery = !recovery; field.edit { replace(0, length, "") }; wrong = false },
            color = w.inkSoft,
        )
    }
}

@Composable
private fun AllowlistEditor(saved: Set<String>) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(saved) }
    val apps by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .filter { it.first != context.packageName && it.first !in LockPolicy.NEVER_ALLOWED && it.first !in LockPolicy.ALWAYS_ALLOWED }
                .sortedBy { it.second }
        }
    }
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Text(stringResource(R.string.allow_title), style = AppText.bodyMedium)
        Text(stringResource(R.string.allow_desc), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
        Text(stringResource(R.string.allow_builtin), style = AppText.body, color = w.accent, modifier = Modifier.padding(vertical = 12.dp))
        val list = apps
        if (list != null && list.isEmpty()) Text(stringResource(R.string.allow_none), style = AppText.caption, color = w.inkSoft)
        list?.forEach { (pkg, label) ->
            Hairline()
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).clickable {
                selected = if (pkg in selected) selected - pkg else selected + pkg
                scope.launch { c.lock.setAllowlist(selected) }
            }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(pkg in selected, null, colors = CheckboxDefaults.colors(checkedColor = w.accent, uncheckedColor = w.inkFaint, checkmarkColor = w.onAccent))
                Text(label, style = AppText.body, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
