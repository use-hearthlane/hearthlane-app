package com.homelab.poc

import android.os.Bundle
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
