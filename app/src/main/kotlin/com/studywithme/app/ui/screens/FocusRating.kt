package com.studywithme.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.studywithme.app.R
import com.studywithme.app.timer.FocusWindow
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.launch

/** Quick "how was your focus?" sheet after a study session or a Pomodoro focus block. One tap, or skip. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusRatingHost() {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val scope = rememberCoroutineScope()
    val queue = remember { mutableStateListOf<FocusWindow>() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Only the visible activity listens, so the main app and the lock screen never both ask.
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { c.engine.focusEnded.collect { queue.add(it) } } }

    val current = queue.firstOrNull() ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun done(score: Int?) {
        scope.launch {
            // a few seconds of slack: the session row is stamped with the clock a moment after the window opened
            if (score != null) c.db.sessionDao().rate(current.fromMs - 2_000, current.toMs + 2_000, score)
            state.hide()
            queue.remove(current)
        }
    }
    ModalBottomSheet(onDismissRequest = { queue.remove(current) }, sheetState = state, containerColor = w.dialog, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.focus_title), style = AppText.title)
            Text(stringResource(R.string.focus_desc), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 4.dp, bottom = 20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (1..5).forEach { n ->
                    Box(
                        Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(14.dp)).background(w.accentSoft).clickable { done(n) },
                        contentAlignment = Alignment.Center,
                    ) { Text(n.toString(), style = AppText.number.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)), color = w.accent) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.focus_low), style = AppText.caption, color = w.inkSoft, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.focus_high), style = AppText.caption, color = w.inkSoft)
            }
            Spacer(Modifier.height(8.dp))
            TextAction(stringResource(R.string.focus_skip), { done(null) }, Modifier.align(Alignment.CenterHorizontally), color = w.inkSoft)
            Spacer(Modifier.height(12.dp))
        }
    }
}
