package app.honmani.reader.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.honmani.reader.R

/**
 * The home screen's sections, switched with the bar at the bottom (within thumb reach), in bar
 * order: most used first.
 */
enum class HomeTab(
    @StringRes val label: Int,
    @DrawableRes val drawable: Int? = null,
    val vector: ImageVector? = null,
    /** A kanji drawn as the icon: 本 (book) for the library, 語 (from 単語, word) for words. */
    val glyph: String? = null,
) {
    LIBRARY(R.string.nav_library, glyph = "本"),
    WORDS(R.string.nav_words, glyph = "語"),
    PROGRESS(R.string.nav_progress, drawable = R.drawable.ic_progress),
    SETTINGS(R.string.nav_settings, vector = Icons.Filled.Settings),
}

@Composable
private fun HomeTab.TabIcon() {
    if (glyph != null) {
        // Icon-sized whatever the font size, like the drawn icons beside it.
        val size = with(LocalDensity.current) { GLYPH_SIZE.toSp() }
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Text(glyph, fontSize = size, lineHeight = size, fontWeight = FontWeight.Medium)
        }
    } else {
        val painter: Painter = drawable?.let { painterResource(it) } ?: rememberVectorPainter(vector!!)
        Icon(painter, contentDescription = null)
    }
}

/** [badges]: tabs with something new, such as dictionary updates, marked with a dot. */
@Composable
fun HomeNavigationBar(selected: HomeTab, onSelect: (HomeTab) -> Unit, badges: Set<HomeTab> = emptySet()) {
    NavigationBar {
        // A little inset at both ends, so the outer labels don't sit at the screen edge.
        Spacer(modifier = Modifier.width(EDGE_INSET))
        for (tab in HomeTab.entries) {
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(badge = { if (tab in badges) Badge() }) {
                        tab.TabIcon()
                    }
                },
                label = { Text(stringResource(tab.label)) },
            )
        }
        Spacer(modifier = Modifier.width(EDGE_INSET))
    }
}

private val EDGE_INSET = 16.dp

private val GLYPH_SIZE = 21.dp
