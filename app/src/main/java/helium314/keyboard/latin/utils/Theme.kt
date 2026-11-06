// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R

// Muninn "Rune & Feather" colours. Gold is the accent; it marks what is active or what Muninn brings back.
object MuninnColors {
    val Raven = Color(0xFF0B0E16)
    val Night1 = Color(0xFF10141F)
    val Night2 = Color(0xFF141A28)
    val Key = Color(0xFF1C2334)
    val Line = Color(0xFF232B3D)
    val Ink = Color(0xFFE9ECF3)
    val Mist = Color(0xFFA9B0C0)
    val Gold = Color(0xFFE8B04B)
    val GoldText = Color(0xFFF0C46E)
    val OnGold = Color(0xFF1A1405)
    val Sheen = Color(0xFF8FA3E0)

    // Daylight (light theme)
    val Paper = Color(0xFFF4F5F8)
    val PaperKey = Color(0xFFFFFFFF)
    val PaperLine = Color(0xFFD3D7E0)
    val InkDark = Color(0xFF151A26)
    val Slate = Color(0xFF5F6778)
    val GoldDeep = Color(0xFF8A5A10) // gold dark enough to read on light backgrounds
}

private val RuneNight = darkColorScheme(
    primary = MuninnColors.Gold,
    onPrimary = MuninnColors.OnGold,
    primaryContainer = Color(0xFF3A2E14),
    onPrimaryContainer = MuninnColors.GoldText,
    inversePrimary = MuninnColors.GoldDeep,
    secondary = MuninnColors.Sheen,
    onSecondary = MuninnColors.Raven,
    secondaryContainer = Color(0xFF2A3456),
    onSecondaryContainer = MuninnColors.Ink,
    tertiary = Color(0xFF8FB0FF),
    onTertiary = MuninnColors.Raven,
    background = MuninnColors.Raven,
    onBackground = MuninnColors.Ink,
    surface = MuninnColors.Raven,
    onSurface = MuninnColors.Ink,
    surfaceVariant = MuninnColors.Key,
    onSurfaceVariant = MuninnColors.Mist,
    surfaceTint = MuninnColors.Gold,
    surfaceBright = MuninnColors.Key,
    surfaceDim = MuninnColors.Raven,
    surfaceContainerLowest = Color(0xFF080A10),
    surfaceContainerLow = Color(0xFF0E121C),
    surfaceContainer = MuninnColors.Night1,
    surfaceContainerHigh = MuninnColors.Night2,
    surfaceContainerHighest = MuninnColors.Key,
    outline = Color(0xFF3A4459),
    outlineVariant = MuninnColors.Line,
    inverseSurface = MuninnColors.Ink,
    inverseOnSurface = MuninnColors.Raven,
    error = Color(0xFFFF8A80),
    onError = MuninnColors.Raven,
)

private val Daylight = lightColorScheme(
    primary = MuninnColors.GoldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E3BC),
    onPrimaryContainer = Color(0xFF3A2600),
    inversePrimary = MuninnColors.Gold,
    secondary = Color(0xFF2A3456),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE3F3),
    onSecondaryContainer = MuninnColors.InkDark,
    tertiary = Color(0xFF2F56B0),
    onTertiary = Color.White,
    background = MuninnColors.Paper,
    onBackground = MuninnColors.InkDark,
    surface = MuninnColors.Paper,
    onSurface = MuninnColors.InkDark,
    surfaceVariant = Color(0xFFE3E6EC),
    onSurfaceVariant = MuninnColors.Slate,
    surfaceTint = MuninnColors.GoldDeep,
    surfaceBright = MuninnColors.PaperKey,
    surfaceDim = Color(0xFFE3E6EC),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color(0xFFEEF0F4),
    surfaceContainerHigh = Color(0xFFE9ECF1),
    surfaceContainerHighest = Color(0xFFE3E6EC),
    outline = Color(0xFFB9BFCB),
    outlineVariant = MuninnColors.PaperLine,
    inverseSurface = MuninnColors.InkDark,
    inverseOnSurface = MuninnColors.Paper,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/** Instrument Serif italic: screen titles and the wordmark */
val MuninnSerif = FontFamily(Font(R.font.muninn_instrument_serif_italic, FontWeight.Normal, FontStyle.Italic))

/** Onest: everything else. A variable font; the weight axis is set for each weight (Android 8+). */
@OptIn(ExperimentalTextApi::class)
val MuninnSans = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.muninn_onest, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
) else FontFamily(Font(R.font.muninn_onest))

/** Noto Sans Runic: ᛗᚢᚾᛁᚾᚾ, used only as decoration */
val MuninnRunic = FontFamily(Font(R.font.muninn_runic))

@Composable
fun Theme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.sans(weight: FontWeight? = null) = copy(fontFamily = MuninnSans, fontWeight = weight ?: fontWeight)
    val serifTitle = base.titleLarge.copy(fontFamily = MuninnSerif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 34.sp)
    MaterialTheme(
        colorScheme = if (dark) RuneNight else Daylight,
        typography = Typography(
            displayLarge = base.displayLarge.copy(fontFamily = MuninnSerif, fontStyle = FontStyle.Italic),
            displayMedium = base.displayMedium.copy(fontFamily = MuninnSerif, fontStyle = FontStyle.Italic),
            displaySmall = base.displaySmall.copy(fontFamily = MuninnSerif, fontStyle = FontStyle.Italic),
            headlineLarge = base.headlineLarge.copy(fontFamily = MuninnSerif, fontStyle = FontStyle.Italic),
            headlineMedium = base.headlineMedium.copy(fontFamily = MuninnSerif, fontStyle = FontStyle.Italic),
            headlineSmall = base.headlineSmall.copy(fontFamily = MuninnSerif, fontStyle = FontStyle.Italic),
            titleLarge = serifTitle,
            titleMedium = base.titleMedium.sans(FontWeight.SemiBold),
            titleSmall = base.titleSmall.sans(FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.sans(),
            bodyMedium = base.bodyMedium.sans(),
            bodySmall = base.bodySmall.sans(),
            labelLarge = base.labelLarge.sans(FontWeight.SemiBold),
            labelMedium = base.labelMedium.sans(FontWeight.Medium),
            labelSmall = base.labelSmall.sans(FontWeight.Medium),
        ),
        content = content
    )
}

const val previewDark = true
