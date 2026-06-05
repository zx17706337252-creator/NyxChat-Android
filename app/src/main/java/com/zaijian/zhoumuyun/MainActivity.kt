package com.zaijian.zhoumuyun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zaijian.zhoumuyun.ui.screen.AppNavigation
import com.zaijian.zhoumuyun.ui.theme.AppTheme
import com.zaijian.zhoumuyun.ui.theme.ZaijianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge display: status bar and nav bar are transparent.
        enableEdgeToEdge()

        setContent {
            // Phase 1: follow the system theme.
            // In a later phase, read from UserPreferences / DataStore.
            ZaijianTheme(appTheme = AppTheme.SYSTEM) {
                AppNavigation()
            }
        }
    }
}
