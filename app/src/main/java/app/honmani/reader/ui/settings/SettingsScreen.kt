package app.honmani.reader.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.honmani.reader.HonmaniApplication
import app.honmani.reader.R
import app.honmani.reader.data.opds.OpdsCatalog
import app.honmani.reader.data.settings.Accent
import app.honmani.reader.ui.HonmaniTopAppBar
import app.honmani.reader.ui.theme.HonmaniTheme
import kotlinx.coroutines.launch

/**
 * Settings: the accent colour, book servers, Anki cards, the way into ttu's reader settings, and
 * diagnostics.
 */
@Composable
fun SettingsScreen(
    bottomBar: @Composable () -> Unit,
    onOpenReaderSettings: () -> Unit,
    onShareDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as HonmaniApplication).container }
    val appearance = container.appearanceSettings
    val accent by appearance.accent.collectAsStateWithLifecycle()
    val catalogs by container.opdsRepository.catalogs.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    }
    var showingAbout by rememberSaveable { mutableStateOf(false) }
    if (showingAbout) {
        AboutScreen(version, onBack = { showingAbout = false })
        return
    }
    SettingsContent(
        version = version,
        accent = accent,
        onAccentChange = appearance::setAccent,
        catalogs = catalogs,
        onAutoImportChange = { catalog, enabled ->
            scope.launch {
                container.opdsRepository.setAutoImport(catalog.id, enabled)
                if (enabled) container.opdsAutoImport.start(force = true)
            }
        },
        bottomBar = bottomBar,
        onOpenReaderSettings = onOpenReaderSettings,
        onShareDiagnostics = onShareDiagnostics,
        onOpenAbout = { showingAbout = true },
        anki = { AnkiSettingsSection() },
        sync = { ProgressSyncSection() },
    )
}

@Composable
private fun SettingsContent(
    version: String,
    accent: Accent,
    onAccentChange: (Accent) -> Unit,
    catalogs: List<OpdsCatalog>,
    onAutoImportChange: (OpdsCatalog, Boolean) -> Unit,
    bottomBar: @Composable () -> Unit,
    onOpenReaderSettings: () -> Unit,
    onShareDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    anki: @Composable () -> Unit = {},
    sync: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = { HonmaniTopAppBar(title = stringResource(R.string.settings_title)) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_accent)) },
                    supportingContent = { AccentPicker(accent, onAccentChange) },
                )
            }
            if (catalogs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings_book_servers),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    )
                }
                items(catalogs, key = { it.id }) { catalog ->
                    ListItem(
                        headlineContent = { Text(catalog.title) },
                        supportingContent = { Text(stringResource(R.string.catalog_auto_import_details)) },
                        trailingContent = {
                            Switch(
                                checked = catalog.autoImport,
                                onCheckedChange = { onAutoImportChange(catalog, it) },
                            )
                        },
                        modifier = Modifier.clickable { onAutoImportChange(catalog, !catalog.autoImport) },
                    )
                }
            }
            item { anki() }
            item { sync() }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_reader)) },
                    supportingContent = { Text(stringResource(R.string.settings_reader_details)) },
                    modifier = Modifier.clickable(onClick = onOpenReaderSettings),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.action_share_diagnostics)) },
                    supportingContent = { Text(stringResource(R.string.settings_diagnostics_details)) },
                    modifier = Modifier.clickable(onClick = onShareDiagnostics),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_about_details, version)) },
                    modifier = Modifier.clickable(onClick = onOpenAbout),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: Accent, onSelect: (Accent) -> Unit) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        for (accent in Accent.entries) {
            val swatch = accent.color?.let { Color(it) }
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (dark) dynamicDarkColorScheme(context).primary else dynamicLightColorScheme(context).primary
                } else {
                    continue
                }
            AccentSwatch(
                color = swatch,
                label = stringResource(accent.label()),
                isSelected = accent == selected,
                onClick = { onSelect(accent) },
            )
        }
    }
}

@Composable
private fun AccentSwatch(color: Color, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .semantics { selected = isSelected }
            .clickable(onClickLabel = label, role = Role.RadioButton, onClick = onClick),
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = label,
                tint = if (color.luminance() > 0.4f) Color.Black else Color.White,
            )
        }
    }
}

private fun Accent.label(): Int = when (this) {
    Accent.PURPLE -> R.string.accent_purple
    Accent.BLUE -> R.string.accent_blue
    Accent.TEAL -> R.string.accent_teal
    Accent.GREEN -> R.string.accent_green
    Accent.ORANGE -> R.string.accent_orange
    Accent.PINK -> R.string.accent_pink
    Accent.SYSTEM -> R.string.accent_system
}

@Preview
@Composable
private fun SettingsPreview() {
    HonmaniTheme {
        SettingsContent(
            version = "0.1",
            accent = Accent.PURPLE,
            onAccentChange = {},
            catalogs = listOf(OpdsCatalog(1, "Calibre-Web", "https://books.example/opds", "me", true)),
            onAutoImportChange = { _, _ -> },
            bottomBar = {},
            onOpenReaderSettings = {},
            onShareDiagnostics = {},
            onOpenAbout = {},
        )
    }
}
