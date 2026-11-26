package com.studywithme.app.ui

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.StudyApp
import com.studywithme.app.data.Settings
import com.studywithme.app.timer.TimerStatus
import com.studywithme.app.ui.components.WarmBackground
import com.studywithme.app.ui.components.LocalHaze
import com.studywithme.app.ui.components.glass
import com.studywithme.app.ui.screens.HomeScreen
import com.studywithme.app.ui.screens.PlaceholderScreen
import com.studywithme.app.ui.screens.SettingsScreen
import com.studywithme.app.ui.screens.TimerScreen
import com.studywithme.app.ui.screens.ToolsScreen
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import com.studywithme.app.ui.theme.StudyTheme
import dev.chrisbanes.haze.HazeState

@Composable
fun StudyRoot() {
    val container = (LocalContext.current.applicationContext as StudyApp).container
    // Wait for the saved settings so night-mode users never see a light flash.
    val settings: Settings = container.settingsRepo.flow.collectAsStateWithLifecycle(null).value ?: return

    StudyTheme(night = settings.night) {
        val w = LocalWarm.current
        val haze = remember { HazeState() }
        val view = LocalView.current
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = !w.night
            ctl.isAppearanceLightNavigationBars = !w.night
        }
        CompositionLocalProvider(LocalContainer provides container, LocalSettings provides settings, LocalHaze provides haze) {
            KeepScreenOnWhileRunning()
            Box(Modifier.fillMaxSize()) {
                WarmBackground(haze)
                AppContent()
                PulseOverlay()
            }
        }
    }
}

/** Screen stays on only while the timer is actually running. */
@Composable
private fun KeepScreenOnWhileRunning() {
    val container = LocalContainer.current
    val running = container.engine.state.collectAsStateWithLifecycle().value.running
    val view = LocalView.current
    DisposableEffect(running) {
        val window = view.context.findActivity()?.window
        if (running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
private fun AppContent() {
    val container = LocalContainer.current
    val status = container.engine.state.collectAsStateWithLifecycle().value.status
    var timerOpen by rememberSaveable { mutableStateOf(false) }
    // The timer screen only makes sense while a session exists.
    val showTimer = timerOpen && status != TimerStatus.Idle

    MainScaffold(onOpenTimer = { timerOpen = true })
    AnimatedVisibility(showTimer, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        TimerScreen(onClose = { timerOpen = false })
    }
    BackHandler(showTimer) { timerOpen = false }
}

private val TabLabels = listOf(R.string.tab_home, R.string.tab_calendar, R.string.tab_log, R.string.tab_tools, R.string.tab_settings)

@Composable
private fun MainScaffold(onOpenTimer: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val layout = rememberLayout()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val dir = LocalLayoutDirection.current

    if (layout.rail) {
        Row(Modifier.fillMaxSize()) {
            GlassRail(
                tab, { tab = it },
                Modifier.padding(start = insets.calculateLeftPadding(dir) + 12.dp, top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + 12.dp)
                    .width(112.dp).fillMaxHeight(),
            )
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                TabContent(
                    tab,
                    PaddingValues(start = 20.dp, end = insets.calculateRightPadding(dir) + 20.dp, top = insets.calculateTopPadding() + 16.dp, bottom = insets.calculateBottomPadding() + 16.dp),
                    onOpenTimer,
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            TabContent(
                tab,
                PaddingValues(
                    start = insets.calculateLeftPadding(dir) + 20.dp, end = insets.calculateRightPadding(dir) + 20.dp,
                    top = insets.calculateTopPadding() + 12.dp, bottom = insets.calculateBottomPadding() + BottomBarHeight + 36.dp,
                ),
                onOpenTimer,
            )
            GlassBottomBar(
                tab, { tab = it },
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = insets.calculateLeftPadding(dir) + 16.dp, end = insets.calculateRightPadding(dir) + 16.dp, bottom = insets.calculateBottomPadding() + 12.dp),
            )
        }
    }
}

@Composable
private fun TabContent(tab: Int, padding: PaddingValues, onOpenTimer: () -> Unit) {
    Crossfade(tab, animationSpec = tween(220), modifier = Modifier.widthIn(max = 1100.dp)) { t ->
        when (t) {
            0 -> HomeScreen(padding, onOpenTimer)
            1 -> PlaceholderScreen(R.string.tab_calendar, padding)
            2 -> PlaceholderScreen(R.string.tab_log, padding)
            3 -> ToolsScreen(padding)
            else -> SettingsScreen(padding)
        }
    }
}

private val BottomBarHeight = 60.dp

@Composable
private fun GlassBottomBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().widthIn(max = 640.dp).height(BottomBarHeight).glass()) {
        TabLabels.forEachIndexed { i, res -> TabItem(stringResource(res), i == selected, { onSelect(i) }, Modifier.weight(1f).fillMaxHeight()) }
    }
}

@Composable
private fun GlassRail(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.glass().padding(vertical = 12.dp), verticalArrangement = Arrangement.Center) {
        TabLabels.forEachIndexed { i, res -> TabItem(stringResource(res), i == selected, { onSelect(i) }, Modifier.fillMaxWidth().height(56.dp)) }
    }
}

/** Text-only tab: no icons, the selected one is orange with a small dot. */
@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val w = LocalWarm.current
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = if (selected) AppText.bodyMedium else AppText.body.copy(fontSize = AppText.label.fontSize), color = if (selected) w.accent else w.inkSoft, maxLines = 1)
        Box(Modifier.padding(top = 4.dp).width(4.dp).height(4.dp).background(if (selected) w.accent else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(50)))
    }
}

/** A soft orange glow around the screen edges: the quiet-mode alert for when sound and vibration are off. */
@Composable
private fun PulseOverlay() {
    val container = LocalContainer.current
    val w = LocalWarm.current
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        container.alerter.pulses.collect {
            repeat(3) {
                alpha.animateTo(1f, tween(450))
                alpha.animateTo(0f, tween(750))
            }
        }
    }
    Box(Modifier.fillMaxSize().drawBehind {
        if (alpha.value > 0f) {
            drawRect(
                Brush.radialGradient(listOf(Color.Transparent, w.accent.copy(alpha = 0.5f * alpha.value)), radius = maxOf(size.width, size.height) * 0.75f),
            )
        }
    })
}
