package app.honmani.reader.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.honmani.reader.R
import app.honmani.reader.ui.ProgressBar
import app.honmani.reader.ui.stats.BookStatistics
import app.honmani.reader.ui.stats.StatisticsViewModel
import app.honmani.reader.ui.stats.duration
import app.honmani.reader.ui.stats.number
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Everything about one book, from a long press on its cover: the cover and progress, a button
 * into the book, its statistics, then what can be done with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSheet(
    book: LibraryBook,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onMarkFinished: () -> Unit,
    onResetProgress: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onRemove: () -> Unit,
    viewModel: StatisticsViewModel = viewModel(factory = StatisticsViewModel.Factory),
) {
    val flow = remember(book.id) { viewModel.book(book.id) }
    val stats by flow.collectAsStateWithLifecycle(null)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The handle is drawn inside the header, so the cover's colour reaches the sheet's top.
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(book)
            Button(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(
                        when (book.status) {
                            ReadingStatus.NOT_STARTED -> R.string.book_start_reading
                            ReadingStatus.READING -> R.string.library_continue_reading
                            ReadingStatus.FINISHED -> R.string.book_open_again
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            stats?.let { Statistics(it) }
            Actions(
                book = book,
                onMarkFinished = onMarkFinished,
                onResetProgress = onResetProgress,
                onSetHidden = onSetHidden,
                onRemove = onRemove,
            )
        }
    }
}

/**
 * The cover, large, on a soft wash of its own colour, beside the title, author, status and
 * progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(book: LibraryBook) {
    val tint by produceState<Color?>(null, book.cover) { value = CoverCache.averageColor(book.cover) }
    // Without a cover, the placeholder's binding; with one, nothing until it is loaded.
    val wash = tint ?: if (book.cover == null) CoverTitle.binding(book.title) else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(wash.copy(alpha = 0.32f), Color.Transparent))),
    ) {
        BottomSheetDefaults.DragHandle()
        HeaderRow(book)
    }
}

@Composable
private fun HeaderRow(book: LibraryBook) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .shadow(10.dp, RoundedCornerShape(6.dp)),
        ) {
            Cover(book)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            book.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(book)
            if (book.status != ReadingStatus.NOT_STARTED) {
                val progress = if (book.status == ReadingStatus.FINISHED) 1f else book.progress
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(progress = { progress }, modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.library_progress, (progress * 100).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (book.hidden) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_hidden),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.book_hidden),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(book: LibraryBook) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(painter = statusIcon(book.status), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(stringResource(statusLabel(book.status)), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Four numbers in two rows, then when the book was read. */
@Composable
private fun Statistics(stats: BookStatistics) {
    val reading = stats.reading
    Column(
        modifier = Modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (reading.seconds == 0L && stats.lookups == 0) {
            Text(
                text = stringResource(R.string.book_nothing_counted),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            Fact(
                label = stringResource(R.string.stats_book_time),
                value = duration(reading.seconds),
                detail = pluralStringResource(R.plurals.book_on_days, reading.daysRead, reading.daysRead),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Fact(
                label = stringResource(R.string.book_read_so_far),
                value = number(stats.position),
                detail = stats.bookCharacters?.let { stringResource(R.string.book_of_characters, number(it)) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
            Fact(
                label = stringResource(R.string.stats_speed),
                value = reading.charactersPerHour?.let { number(it) } ?: "–",
                detail = reading.charactersPerHour?.let { stringResource(R.string.book_per_hour) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            Fact(
                label = stringResource(R.string.stats_lookups),
                value = number(stats.lookups.toLong()),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        val context = LocalContext.current
        val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
        val dates = listOfNotNull(
            reading.firstDay?.let { stringResource(R.string.book_first_read, it.format(dateFormat)) },
            stats.finishedAt?.let {
                stringResource(
                    R.string.book_finished_on,
                    DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR),
                )
            },
        )
        if (dates.isNotEmpty()) {
            Text(
                text = dates.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The book's actions as a row of tiles, removing last and in the error colour. */
@Composable
private fun Actions(
    book: LibraryBook,
    onMarkFinished: () -> Unit,
    onResetProgress: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .height(IntrinsicSize.Min),
    ) {
        val tile = Modifier.weight(1f).fillMaxHeight()
        if (book.status != ReadingStatus.FINISHED) {
            ActionTile(rememberVectorPainter(Icons.Filled.CheckCircle), stringResource(R.string.action_mark_finished), onMarkFinished, tile)
        }
        if (book.status != ReadingStatus.NOT_STARTED) {
            ActionTile(rememberVectorPainter(Icons.Filled.Refresh), stringResource(R.string.action_reset_progress), onResetProgress, tile)
        }
        ActionTile(
            icon = painterResource(if (book.hidden) R.drawable.ic_visible else R.drawable.ic_hidden),
            label = stringResource(if (book.hidden) R.string.action_unhide_book else R.string.action_hide_book),
            onClick = { onSetHidden(!book.hidden) },
            modifier = tile,
        )
        ActionTile(
            icon = rememberVectorPainter(Icons.Filled.Delete),
            label = stringResource(R.string.action_remove),
            onClick = onRemove,
            modifier = tile,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ActionTile(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
        ) {
            Icon(painter = icon, contentDescription = null, tint = color)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
