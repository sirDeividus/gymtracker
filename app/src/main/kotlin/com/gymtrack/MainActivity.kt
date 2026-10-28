package com.gymtrack

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gymtrack.notify.RestNotifier
import com.gymtrack.ui.nav.GymTrackRoot
import com.gymtrack.ui.theme.GymTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { GymTrackTheme { GymTrackRoot() } }
    }

    override fun onStart() {
        super.onStart()
        RestNotifier.inForeground = true
    }

    override fun onStop() {
        RestNotifier.inForeground = false
        super.onStop()
    }
}
