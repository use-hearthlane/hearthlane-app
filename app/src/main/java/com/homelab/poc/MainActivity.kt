package com.homelab.poc

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.homelab.poc.core.frigate.FrigateConfig
import com.homelab.poc.ui.HomeScreen
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Live video must not be interrupted by the screen timeout while the
        // app is in the foreground. Manual lock still works; the lifecycle
        // observer in HomeScreen re-establishes playback on unlock.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val stateDir = File(filesDir, "tailscale").absolutePath
        val frigateConfig = FrigateConfig(
            localBaseUrl = BuildConfig.FRIGATE_BASE_URL,
            tailscaleBaseUrl = BuildConfig.FRIGATE_BASE_URL,
        )
        setContent {
            HomeScreen(
                hostname = "poc-camera",
                stateDir = stateDir,
                frigateConfig = frigateConfig,
            )
        }
    }
}
