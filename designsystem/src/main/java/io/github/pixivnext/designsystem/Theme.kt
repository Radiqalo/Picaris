package io.github.pixivnext.designsystem

import android.app.Activity
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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
    val referenceColors =
        if (dark) colors
        else
            colors.copy(
                primary = Color(0xFF356783),
                onPrimary = Color.White,
                background = Color(0xFFF7F8FC),
                surface = Color(0xFFF7F8FC),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFF0F3F9),
                surfaceContainer = Color(0xFFEDF1F8),
                surfaceContainerHigh = Color(0xFFE7EDF5),
                surfaceContainerHighest = Color(0xFFE1E8F1),
                primaryContainer = Color(0xFFD7EAF8),
                onPrimaryContainer = Color(0xFF17394E),
                secondaryContainer = Color(0xFFD7EAF8),
                onSecondaryContainer = Color(0xFF17394E),
            )

    MaterialExpressiveTheme(
        colorScheme = referenceColors,
        motionScheme = MotionScheme.expressive(),
        typography =
            Typography(
                headlineLarge =
                    Typography()
                        .headlineLarge
                        .copy(fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp),
                headlineMedium =
                    Typography()
                        .headlineMedium
                        .copy(fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp),
                titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
                titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
            ),
        content = content,
    )
}
