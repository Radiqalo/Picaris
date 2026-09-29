package io.github.pixivnext

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import dagger.hilt.android.AndroidEntryPoint
import io.github.pixivnext.ui.PixivApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var incoming by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = intent
        setContent { PixivApp(vm, incoming) { incoming = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = intent
    }
}
