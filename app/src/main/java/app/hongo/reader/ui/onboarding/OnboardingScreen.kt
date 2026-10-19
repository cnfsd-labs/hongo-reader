package app.hongo.reader.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hongo.reader.R
import app.hongo.reader.data.dictionary.CatalogDictionary
import app.hongo.reader.data.dictionary.DictionaryCatalog
import app.hongo.reader.data.dictionary.ImportStatus
import app.hongo.reader.ui.ProgressBar
import app.hongo.reader.ui.dictionaries.CatalogItem
import app.hongo.reader.ui.dictionaries.DictionariesUiState
import app.hongo.reader.ui.dictionaries.DictionariesViewModel
import app.hongo.reader.ui.theme.HongoTheme
import app.hongo.reader.ui.rememberWithNotifications

/** Where the one-tap dictionary download stands. */
sealed interface StarterPackState {
    data object NotStarted : StarterPackState

    data class Downloading(val installed: Int, val total: Int) : StarterPackState

    data object Ready : StarterPackState

    data object Failed : StarterPackState

    companion object {
        /** The pack's state from the Dictionaries screen's state; [started] once requested. */
        fun of(pack: List<CatalogDictionary>, state: DictionariesUiState, started: Boolean): StarterPackState {
            val loaded = state as? DictionariesUiState.Loaded ?: return NotStarted
            val items = loaded.catalog.filter { it.dictionary in pack }
            val installed = items.count { it.state == CatalogItem.State.INSTALLED }
            return when {
                installed == pack.size -> Ready
                !started -> NotStarted
                loaded.imports.any { it is ImportStatus.Failed } -> Failed
                else -> Downloading(installed, pack.size)
            }
        }
    }
}

/**
 * The first-start screen: dictionaries with one tap, then a first book (the sample or the
 * reader's own). Shown once; skipping it leaves everything in the tabs.
 */
@Composable
fun OnboardingScreen(
    onTrySample: () -> Unit,
    onAddBook: () -> Unit,
    onSkip: () -> Unit,
    dictionaries: DictionariesViewModel = viewModel(factory = DictionariesViewModel.Factory),
) {
    val dictionariesState by dictionaries.uiState.collectAsStateWithLifecycle()
    var japanese by rememberSaveable { mutableStateOf(false) }
    var started by rememberSaveable { mutableStateOf(false) }
    val pack = DictionaryCatalog.starterPack(japanese)
    val withNotifications = rememberWithNotifications()
    OnboardingContent(
        japanese = japanese,
        onJapaneseChange = { japanese = it },
        sizeMb = pack.sumOf { it.sizeMb },
        pack = StarterPackState.of(pack, dictionariesState, started),
        onDownload = {
            started = true
            dictionaries.dismissFailures()
            withNotifications { pack.forEach(dictionaries::download) }
        },
        onTrySample = onTrySample,
        onAddBook = onAddBook,
        onSkip = onSkip,
    )
}

@Composable
private fun OnboardingContent(
    japanese: Boolean,
    onJapaneseChange: (Boolean) -> Unit,
    sizeMb: Int,
    pack: StarterPackState,
    onDownload: () -> Unit,
    onTrySample: () -> Unit,
    onAddBook: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            // The launcher icon's artwork, in a launcher-like rounded square.
            Image(
                painter = painterResource(R.drawable.ic_launcher_art),
                contentDescription = null,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )
            Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.onboarding_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Step(number = 1, title = stringResource(R.string.onboarding_dictionaries_title)) {
                Text(stringResource(R.string.onboarding_definitions_in), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val enabled = pack == StarterPackState.NotStarted
                    FilterChip(
                        selected = !japanese,
                        onClick = { onJapaneseChange(false) },
                        label = { Text(stringResource(R.string.onboarding_english)) },
                        enabled = enabled,
                    )
                    FilterChip(
                        selected = japanese,
                        onClick = { onJapaneseChange(true) },
                        label = { Text(stringResource(R.string.onboarding_japanese)) },
                        enabled = enabled,
                    )
                }
                Text(
                    stringResource(
                        if (japanese) R.string.onboarding_pack_japanese else R.string.onboarding_pack_english,
                        sizeMb,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (pack) {
                    StarterPackState.NotStarted -> Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.onboarding_download))
                    }
                    is StarterPackState.Downloading -> {
                        ProgressBar(
                            progress = { (pack.installed + 0.5f) / pack.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.onboarding_downloading, pack.installed + 1, pack.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StarterPackState.Ready -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.onboarding_ready))
                    }
                    StarterPackState.Failed -> {
                        Text(
                            stringResource(R.string.onboarding_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.onboarding_retry))
                        }
                    }
                }
            }

            Step(number = 2, title = stringResource(R.string.onboarding_book_title)) {
                Text(
                    stringResource(R.string.onboarding_book_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onTrySample, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_sample))
                }
                OutlinedButton(onClick = onAddBook, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_add_book))
                }
            }

            TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
        }
    }
}

@Composable
private fun Step(number: Int, title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.onboarding_step, number, title),
                style = MaterialTheme.typography.titleMedium,
            )
            content()
        }
    }
}

@Preview
@Composable
private fun OnboardingPreview() {
    HongoTheme {
        OnboardingContent(
            japanese = false,
            onJapaneseChange = {},
            sizeMb = 51,
            pack = StarterPackState.Downloading(1, 4),
            onDownload = {},
            onTrySample = {},
            onAddBook = {},
            onSkip = {},
        )
    }
}
