package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.input.*
import androidx.navigation3.runtime.NavKey
import io.github.radiqalo.picaris.AppViewModel
import io.github.radiqalo.picaris.core.*
import io.github.radiqalo.picaris.designsystem.*

@Composable
fun UtilityScreen(
    page: String,
    vm: AppViewModel,
    navigate: (NavKey) -> Unit,
    back: () -> Unit,
) {
    when (page) {
        "bookmarks" -> BookmarkScreen(vm, navigate, back)
        "settings" -> SettingsScreen(vm, back)
        "accounts" -> AccountScreen(vm, back)
        "downloads" -> DownloadsScreen(vm, navigate, back)
        "history" -> HistoryScreen(vm, navigate, back)
        else -> AboutScreen(back)
    }
}
