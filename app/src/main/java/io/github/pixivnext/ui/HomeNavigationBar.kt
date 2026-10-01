package io.github.pixivnext.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeNavigationBar(floating: Boolean, content: @Composable () -> Unit) {
    if (!floating) {
        ShortNavigationBar(
            modifier = Modifier.aboveWorkTransition(),
            arrangement = ShortNavigationBarArrangement.EqualWeight,
            content = content,
        )
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().aboveWorkTransition()
                .windowInsetsPadding(WindowInsets.navigationBars.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 6.dp,
            ) {
                ShortNavigationBar(
                    containerColor = Color.Transparent,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    arrangement = ShortNavigationBarArrangement.EqualWeight,
                    content = content,
                )
            }
        }
    }
}
