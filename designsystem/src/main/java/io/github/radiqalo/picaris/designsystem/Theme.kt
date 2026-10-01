package io.github.radiqalo.picaris.designsystem

import android.app.Activity
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Shared layout rhythm; component shapes and colors come from Material 3 Expressive. */
object PixivSpacing {
    val tight = 4.dp
    val compact = 8.dp
    val related = 12.dp
    val content = 16.dp
    val section = 24.dp
}

/** Grouped controls sit above the page background in the surface hierarchy. */
object PixivContainerDefaults {
    @Composable
    fun listItemColors() = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
}

internal fun seededColors(seed: Long, dark: Boolean) =
    com.materialkolor.dynamicColorScheme(
        seedColor = Color(seed),
        isDark = dark,
        style = com.materialkolor.PaletteStyle.Expressive,
        specVersion = com.materialkolor.dynamiccolor.ColorSpec.SpecVersion.SPEC_2025,
    )

@Composable
fun PixivTheme(
    theme: String = "system",
    dynamic: Boolean = true,
    seed: Long = 0xFF6256CA,
    pureBlackDarkTheme: Boolean = false,
    darkSystemBarIcons: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark =
        when (theme) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    val activity = LocalContext.current as? Activity
    SideEffect {
        val mask =
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        activity
            ?.window
            ?.insetsController
            ?.setSystemBarsAppearance(if (darkSystemBarIcons ?: !dark) mask else 0, mask)
    }
    val baseColors =
        if (dynamic) {
            if (dark) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        } else androidx.compose.runtime.remember(seed, dark) { seededColors(seed, dark) }
    val colors = androidx.compose.runtime.remember(baseColors, pureBlackDarkTheme, dark) {
        val surfaceColors = if (dark && pureBlackDarkTheme) {
            baseColors.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceDim = Color.Black,
                surfaceBright = Color(0xFF242424),
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF080808),
                surfaceContainer = Color(0xFF0D0D0D),
                surfaceContainerHigh = Color(0xFF141414),
                surfaceContainerHighest = Color(0xFF1B1B1B),
            )
        } else baseColors
        surfaceColors.copy(
            secondary = surfaceColors.primary,
            onSecondary = surfaceColors.onPrimary,
            secondaryContainer = surfaceColors.primaryContainer,
            onSecondaryContainer = surfaceColors.onPrimaryContainer,
            tertiary = surfaceColors.primary,
            onTertiary = surfaceColors.onPrimary,
            tertiaryContainer = surfaceColors.primaryContainer,
            onTertiaryContainer = surfaceColors.onPrimaryContainer,
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = Typography(),
        content = content,
    )
}
