package app.hongo.reader.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hongo.reader.R

/**
 * The home screen's sections, switched with the bar at the bottom (within thumb reach), in bar
 * order: most used first.
 */
enum class HomeTab(
    @StringRes val label: Int,
    @DrawableRes val drawable: Int? = null,
    val vector: ImageVector? = null,
) {
    LIBRARY(R.string.nav_library, drawable = R.drawable.ic_library),
    WORDS(R.string.nav_words, vector = Icons.AutoMirrored.Filled.List),
    DICTIONARIES(R.string.nav_dictionaries, drawable = R.drawable.ic_dictionary),
    SETTINGS(R.string.nav_settings, vector = Icons.Filled.Settings),
}

@Composable
private fun HomeTab.icon(): Painter = drawable?.let { painterResource(it) } ?: rememberVectorPainter(vector!!)

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
                        Icon(tab.icon(), contentDescription = null)
                    }
                },
                label = { Text(stringResource(tab.label)) },
            )
        }
        Spacer(modifier = Modifier.width(EDGE_INSET))
    }
}

private val EDGE_INSET = 16.dp
