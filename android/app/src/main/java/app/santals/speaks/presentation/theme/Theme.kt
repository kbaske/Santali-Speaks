package app.santals.speaks.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.santals.speaks.R
import app.santals.speaks.data.local.ThemeMode

/** Noto Sans Ol Chiki (SIL OFL 1.1, bundled). Used for all Ol Chiki text so glyphs always render. */
val OlChikiFont: FontFamily = FontFamily(
    Font(R.font.noto_sans_ol_chiki_regular, FontWeight.Normal),
    Font(R.font.noto_sans_ol_chiki_bold, FontWeight.Bold),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFB5523B),
    onPrimary = Color.White,
    secondary = Color(0xFFD9A441),
    background = Color(0xFFF6EFE3),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF2A211B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFF0A08A),
    onPrimary = Color(0xFF4A1A10),
    secondary = Color(0xFFD9A441),
    background = Color(0xFF1B1A17),
    surface = Color(0xFF26231F),
    onSurface = Color(0xFFEDE3D3),
)

@Composable
fun SantaliTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = olChikiTypography(),
        content = content,
    )
}

private fun olChikiTypography(): Typography {
    val base = Typography()
    return base.copy(
        displaySmall = base.displaySmall.copy(fontFamily = OlChikiFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = OlChikiFont),
        titleLarge = base.titleLarge.copy(fontFamily = OlChikiFont),
        titleMedium = base.titleMedium.copy(fontFamily = OlChikiFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = OlChikiFont, fontSize = 18.sp, lineHeight = 28.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = OlChikiFont),
    )
}
