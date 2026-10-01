package io.github.pixivnext.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.Text
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeNavigationBar(
    floating: Boolean,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    icon: @Composable (Int, Boolean) -> Unit,
) {
    if (!floating) {
        ShortNavigationBar(
            modifier = Modifier.aboveWorkTransition(),
            arrangement = ShortNavigationBarArrangement.EqualWeight,
        ) {
            labels.forEachIndexed { index, title ->
                ShortNavigationBarItem(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) },
                    icon = { icon(index, selectedIndex == index) },
                    label = { Text(title) },
                )
            }
        }
    } else {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().aboveWorkTransition()
                .windowInsetsPadding(WindowInsets.navigationBars.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val selectedWidth = (maxWidth - 16.dp - 48.dp * (labels.size - 1)).coerceAtLeast(48.dp)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    labels.forEachIndexed { index, title ->
                        val selected = selectedIndex == index
                        Surface(
                            modifier = Modifier.height(56.dp)
                                .widthIn(min = 48.dp, max = if (selected) selectedWidth else 48.dp)
                                .clip(CircleShape)
                                .selectable(selected, role = Role.Tab, onClick = { onSelect(index) })
                                .semantics { contentDescription = title },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = if (selected) 12.dp else 0.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                icon(index, selected)
                                if (selected) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(title, style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
