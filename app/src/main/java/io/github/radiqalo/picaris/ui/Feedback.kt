package io.github.radiqalo.picaris.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.radiqalo.picaris.designsystem.AppIcon

/** Platform feedback respects the user's system haptic preference. */
@Composable
fun toggleFeedback(): (Boolean) -> Unit {
    val haptic = LocalHapticFeedback.current
    return { enabled -> haptic.performHapticFeedback(
        if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff) }
}

@Composable
fun selectionFeedback(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) }
}

@Composable
fun FeedbackSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val feedback = toggleFeedback()
    Switch(checked, { next -> feedback(next); onCheckedChange(next) })
}

@Composable
fun Modifier.expressivePress(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .9f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun FeedbackIcon(vector: ImageVector, description: String?, selected: Boolean,
    modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f,
        MaterialTheme.motionScheme.defaultSpatialSpec(), label = "selection")
    val color by animateColorAsState(tint,
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "icon color")
    Crossfade(vector, modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(), label = "icon") {
        AppIcon(it, description, tint = color)
    }
}
