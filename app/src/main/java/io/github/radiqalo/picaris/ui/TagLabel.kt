package io.github.radiqalo.picaris.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
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
internal val LocalTagLongPress = staticCompositionLocalOf<(Tag) -> Unit> { {} }

private fun Modifier.tagLongPress(tag: Tag, onLongPress: (Tag) -> Unit): Modifier =
    pointerInput(tag.name, onLongPress) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress != null) {
                longPress.consume()
                onLongPress(tag)
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }

@Composable
internal fun TagLabel(tag: Tag, translationFirst: Boolean = true) {
    val name = tag.name.trim()
    val enabled = LocalTagTranslationEnabled.current
    val onLongPress = LocalTagLongPress.current
    val translation = tag.translated_name?.trim()?.takeIf {
        enabled && it.isNotEmpty() && !it.equals(name, ignoreCase = true)
    }
    if (!enabled) {
        Text(
            "#$name",
            Modifier.tagLongPress(tag, onLongPress),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Column(
        Modifier.padding(vertical = 4.dp).tagLongPress(tag, onLongPress),
    ) {
        if (translation != null) {
            Text(
                if (translationFirst) translation else "#$name",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (translationFirst) "#$name" else translation,
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Box(
                Modifier.defaultMinSize(minHeight =
                    MaterialTheme.typography.labelMedium.lineHeight.value.dp +
                        MaterialTheme.typography.labelSmall.lineHeight.value.dp
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "#$name",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
