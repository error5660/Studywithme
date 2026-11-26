package com.studywithme.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.studywithme.app.ui.StudyRoot

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
        setContent { StudyRoot() }
    }
}
