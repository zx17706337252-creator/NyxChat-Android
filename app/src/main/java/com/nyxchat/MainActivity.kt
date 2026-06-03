package com.nyxchat

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nyxchat.ui.theme.LocalNyxColors
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nyxchat.ui.components.BottomNav
import com.nyxchat.ui.screens.*
import com.nyxchat.ui.theme.NyxChatTheme
import com.nyxchat.viewmodel.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Android 13+ notification permission launcher
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS on Android 13+ before any proactive notification fires
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            val vm: ChatViewModel = viewModel()
            val isDark by vm.isDarkMode.collectAsState()

            // Bug 2 fix: keep system bar icons readable after theme switch
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowInsetsControllerCompat(window, view).apply {
                        isAppearanceLightStatusBars     = !isDark
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
            }

            // Bug fix: 将 vm 向下传递，而非在 AppContent 中再次调用 viewModel()
            // 两次调用返回同一实例（Hilt Activity 作用域），但显式传参更清晰，避免误解
            NyxChatTheme(isDarkMode = isDark) { AppContent(vm) }
        }
    }
}

@Composable
fun AppContent(vm: ChatViewModel = viewModel()) {
    var route by remember { mutableStateOf("chat") }

    Column(
        Modifier
            .fillMaxSize()
            .background(LocalNyxColors.current.Background) // Bug 1 fix: seal window bleed-through
            .systemBarsPadding()
    ) {
        Box(Modifier.weight(1f)) {
            when (route) {
                "chat"      -> ChatScreen(vm)
                "chars"     -> CharactersScreen(vm)
                "relations" -> RelationshipScreen(vm)
                "settings"  -> SettingsScreen(vm)
            }
        }
        BottomNav(currentRoute = route, onNavigate = { route = it })
    }
}
