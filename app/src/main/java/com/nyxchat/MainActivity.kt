package com.nyxchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nyxchat.ui.components.BottomNav
import com.nyxchat.ui.screens.*
import com.nyxchat.ui.theme.NyxChatTheme
import com.nyxchat.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NyxChatTheme {
                NyxApp()
            }
        }
    }
}

@Composable
fun NyxApp() {
    val vm: ChatViewModel = viewModel()
    var currentRoute by remember { mutableStateOf("chat") }

    val memories by vm.memories.collectAsState()

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Box(modifier = Modifier.weight(1f)) {
            when (currentRoute) {
                "chat"     -> ChatScreen(vm)
                "chars"    -> CharactersScreen(vm)
                "memory"   -> MemoryScreen(vm)
                "settings" -> SettingsScreen(vm)
            }
        }

        BottomNav(
            currentRoute = currentRoute,
            memoryCount = memories.size,
            onNavigate = { currentRoute = it }
        )
    }
}
