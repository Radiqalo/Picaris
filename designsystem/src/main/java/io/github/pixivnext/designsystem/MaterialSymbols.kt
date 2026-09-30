package io.github.pixivnext.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

enum class MaterialSymbol(val resource: Int) {
    Home(R.drawable.ms_home),
    Image(R.drawable.ms_image),
    Book(R.drawable.ms_menu_book),
    Lock(R.drawable.ms_lock),
    LockOpen(R.drawable.ms_lock_open),
    Inbox(R.drawable.ms_inbox),
    Calendar(R.drawable.ms_calendar_month),
}

@Composable
fun materialSymbol(symbol: MaterialSymbol): ImageVector =
    ImageVector.vectorResource(symbol.resource)
