package io.github.pixivnext.designsystem

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
    val colors =
        if (dynamic) {
            if (dark) dynamicDarkColorScheme(LocalContext.current)
            else dynamicLightColorScheme(LocalContext.current)
        } else androidx.compose.runtime.remember(seed, dark) { seededColors(seed, dark) }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = Typography(),
        content = content,
    )
}
