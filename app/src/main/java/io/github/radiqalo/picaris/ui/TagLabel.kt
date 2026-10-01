package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.radiqalo.picaris.core.Tag

internal val LocalTagTranslationEnabled = staticCompositionLocalOf { true }

@Composable
internal fun TagLabel(tag: Tag, translationFirst: Boolean = true) {
    val name = tag.name.trim()
    val enabled = LocalTagTranslationEnabled.current
    val translation = tag.translated_name?.trim()?.takeIf {
        enabled && it.isNotEmpty() && !it.equals(name, ignoreCase = true)
    }
    if (translation == null) {
        Text("#$name", maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(if (translationFirst) translation else "#$name",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (translationFirst) "#$name" else translation,
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.78f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
