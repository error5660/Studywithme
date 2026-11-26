package com.studywithme.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.studywithme.app.lock.LockActivity
import com.studywithme.app.ui.StudyRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        // Ask for the panel's highest refresh rate at the current resolution (120 Hz on the S23) for smoother motion.
        display?.let { d ->
            val cur = d.mode
            d.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?.let { window.attributes = window.attributes.apply { preferredDisplayModeId = it.modeId } }
        }
        // While the focus lock is on, the main app steps aside for the lock screen.
        val container = (application as StudyApp).container
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.lock.active.collect { if (it) startActivity(LockActivity.intent(this@MainActivity)) }
            }
        }
        setContent { StudyRoot() }
    }
}
