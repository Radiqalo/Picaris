package io.github.radiqalo.picaris

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.radiqalo.picaris.ui.PixivApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var incoming by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        incoming = intent
        setContent { PixivApp(vm, incoming) { incoming = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = intent
    }
}
