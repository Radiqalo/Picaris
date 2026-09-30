package io.github.pixivnext.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

enum class MaterialSymbol(val resource: Int) {
    Home(R.drawable.ms_home),
    HomeFilled(R.drawable.ms_home_filled),
    Explore(R.drawable.ms_explore),
    ExploreFilled(R.drawable.ms_explore_filled),
    Feed(R.drawable.ms_dynamic_feed),
    FeedFilled(R.drawable.ms_dynamic_feed_filled),
    Person(R.drawable.ms_person),
    PersonFilled(R.drawable.ms_person_filled),
    Image(R.drawable.ms_image),
    Book(R.drawable.ms_menu_book),
    Lock(R.drawable.ms_lock),
    LockOpen(R.drawable.ms_lock_open),
    Inbox(R.drawable.ms_inbox),
    Calendar(R.drawable.ms_calendar_month),
    Theme(R.drawable.ms_dark_mode),
    Wallpaper(R.drawable.ms_wallpaper),
    Palette(R.drawable.ms_palette),
    Contrast(R.drawable.ms_contrast),
    Adult(R.drawable.ms_18_up_rating),
    Ai(R.drawable.ms_smart_toy),
    BlockedTag(R.drawable.ms_label_off),
    BlockedUser(R.drawable.ms_person_off),
    Network(R.drawable.ms_public),
    ClearCache(R.drawable.ms_cleaning_services),
}

@Composable
fun materialSymbol(symbol: MaterialSymbol): ImageVector =
    ImageVector.vectorResource(symbol.resource)
