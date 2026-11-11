package io.github.hiraking.nodoff.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** DESIGN.md「色」のトークン。値はそこから写す。差し色は accent の 1 色だけ。 */
object NodOffColors {
    val background = Color(0xFF000000)

    /** 右上からのにじみ：backgroundGlow（0%）→ backgroundGlowMid（55%）→ background（100%） */
    val backgroundGlow = Color(0xFF161C3A)
    val backgroundGlowMid = Color(0xFF06070D)

    val surface = Color(0xFF0C0F1C)
    val surfaceBorder = Color(0xFF161A2B)
    val divider = Color(0xFF151827)
    val chip = Color(0xFF10131F)
    val sheet = Color(0xFF0E1120)
    val accent = Color(0xFFB9C3FF)
    val onAccent = Color(0xFF0B0E1F)
    val textPrimary = Color(0xFFE4E6F0)
    val textSecondary = Color(0xFF7D84A3)
    val textTertiary = Color(0xFF6E7596)
}

/** Material3 の darkColorScheme を DESIGN.md の色で上書きしたもの。OS の設定にかかわらず常にこれを使う */
private val NodOffColorScheme = darkColorScheme(
    primary = NodOffColors.accent,
    onPrimary = NodOffColors.onAccent,
    secondary = NodOffColors.accent,
    onSecondary = NodOffColors.onAccent,
    tertiary = NodOffColors.accent,
    onTertiary = NodOffColors.onAccent,
    background = NodOffColors.background,
    onBackground = NodOffColors.textPrimary,
    surface = NodOffColors.surface,
    onSurface = NodOffColors.textPrimary,
    surfaceVariant = NodOffColors.chip,
    onSurfaceVariant = NodOffColors.textSecondary,
    surfaceContainerLow = NodOffColors.sheet,
    surfaceContainer = NodOffColors.sheet,
    surfaceContainerHigh = NodOffColors.sheet,
    outline = NodOffColors.surfaceBorder,
    outlineVariant = NodOffColors.divider,
)

@Composable
fun NodOffTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NodOffColorScheme, content = content)
}
