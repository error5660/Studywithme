package com.studywithme.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.studywithme.app.AppContainer
import com.studywithme.app.data.Settings
import java.util.Locale

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer was not provided") }
val LocalSettings = compositionLocalOf { Settings() }

/** Full-screen screens that sit above the tabs. */
const val OVERLAY_LOCK = 2
const val OVERLAY_NOTEBOOK = 3
const val OVERLAY_SCORES = 4
val LocalOpenOverlay = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** Screen-size rules shared by every screen so phone/tablet and portrait/landscape stay consistent. */
class LayoutInfo(val widthDp: Int, val heightDp: Int) {
    val landscape get() = widthDp > heightDp
    /** Navigation moves to a side rail on wide landscape screens (tablet, phone sideways). */
    val rail get() = landscape && widthDp >= 600
    /** Content splits into two panes on wide screens. */
    val twoPane get() = widthDp >= 720
    val shortHeight get() = heightDp < 500
}

@Composable
fun rememberLayout(): LayoutInfo {
    val size = LocalWindowInfo.current.containerSize // the real window, correct in split screen too
    val density = LocalDensity.current
    return with(density) { LayoutInfo(size.width.toDp().value.toInt(), size.height.toDp().value.toInt()) }
}

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun formatHms(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
}

fun formatMs(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0) // round up so a countdown shows 00:01 until it really ends
    return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
}
