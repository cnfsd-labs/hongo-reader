package app.honmani.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import app.honmani.reader.data.settings.Accent
import app.honmani.reader.data.settings.customAccentColor

@Composable
fun HonmaniTheme(
    accent: Accent = Accent.DEFAULT,
    customHue: Float = 0f,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val color = if (accent == Accent.CUSTOM) customAccentColor(customHue) else accent.color
    val colorScheme = when {
        color != null -> accentColorScheme(Color(color), darkTheme)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> accentColorScheme(Color(Accent.DEFAULT.color!!), darkTheme)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * A colour scheme around one accent colour: buttons, the selected tab, chips and progress bars use
 * it; the containers are the accent mixed with the background, so they stay calm.
 */
fun accentColorScheme(accent: Color, darkTheme: Boolean): ColorScheme {
    val onAccent = if (accent.luminance() > 0.4f) Color.Black else Color.White
    return if (darkTheme) {
        val container = lerp(accent, Color.Black, 0.55f)
        val onContainer = lerp(accent, Color.White, 0.8f)
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            tertiary = accent,
            onTertiary = onAccent,
        )
    } else {
        // A little darker on light backgrounds, for contrast with white text and the background.
        val primary = lerp(accent, Color.Black, 0.15f)
        // Light accents (amber, a custom yellow) keep dark text.
        val onPrimary = if (primary.luminance() > 0.4f) Color.Black else Color.White
        val container = lerp(accent, Color.White, 0.8f)
        val onContainer = lerp(accent, Color.Black, 0.6f)
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            tertiary = primary,
            onTertiary = onPrimary,
        )
    }
}
