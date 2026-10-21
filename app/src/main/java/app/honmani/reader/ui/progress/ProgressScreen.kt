package app.honmani.reader.ui.progress

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.honmani.reader.R
import app.honmani.reader.data.stats.Milestone
import app.honmani.reader.data.stats.MilestoneKind
import app.honmani.reader.data.stats.ReadingLevel
import app.honmani.reader.ui.HonmaniTopAppBar
import app.honmani.reader.ui.ProgressBar
import app.honmani.reader.ui.stats.characters
import app.honmani.reader.ui.stats.number
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The reading level, the next milestones and all milestones, from the library's toolbar. */
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory),
) {
    BackHandler(onBack = onBack)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { HonmaniTopAppBar(title = stringResource(R.string.progress_title), onBack = onBack) },
    ) { padding ->
        val current = state ?: return@Scaffold
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (!current.anyReading) {
                Text(
                    text = stringResource(R.string.progress_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LevelCard(current)
            if (current.next.isNotEmpty()) NextMilestones(current.next)
            AllMilestones(current.milestones)
            Text(
                text = stringResource(R.string.progress_how),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Level 14" beside a ring that fills towards the next level, with the streak below. */
@Composable
private fun LevelCard(state: ProgressUiState) {
    val level = state.level
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                LevelRing(level)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.progress_level, level.level),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(characters(level.characters), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.progress_to_next, number(level.nextLevelAt - level.characters), level.level + 1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(
                        R.string.progress_streak,
                        pluralStringResource(R.plurals.stats_days, state.streak, state.streak),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.progress_this_week, state.daysThisWeek),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LevelRing(level: ReadingLevel) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(88.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (level.fraction > 0f) {
                drawArc(
                    fill,
                    startAngle = -90f,
                    sweepAngle = 360f * level.fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text("${level.level}", style = MaterialTheme.typography.headlineSmall)
    }
}

/** The closest goals, each with how far along it is. */
@Composable
private fun NextMilestones(next: List<Milestone>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.progress_next),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (milestone in next) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(milestoneTitle(milestone), style = MaterialTheme.typography.bodyLarge)
                    ProgressBar(progress = { milestone.fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.progress_of_goal, number(milestone.current), number(milestone.goal)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Every milestone by kind: reached ones with their date, the others greyed with their goal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllMilestones(milestones: List<Milestone>) {
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.progress_milestones),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for ((kind, goals) in milestones.groupBy { it.kind }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(kindName(kind)), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (milestone in goals) {
                        val date = milestone.reachedOn?.format(dateFormat)
                        val title = milestoneTitle(milestone)
                        val description = date?.let { "$title, ${stringResource(R.string.progress_reached_on, it)}" } ?: title
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (milestone.reached) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            contentColor = if (milestone.reached) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier
                                .width(96.dp)
                                .clearAndSetSemantics { contentDescription = description },
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(goalLabel(milestone), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(date ?: "–", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun kindName(kind: MilestoneKind): Int = when (kind) {
    MilestoneKind.CHARACTERS -> R.string.progress_kind_characters
    MilestoneKind.HOURS -> R.string.progress_kind_hours
    MilestoneKind.BOOKS -> R.string.progress_kind_books
    MilestoneKind.DAYS -> R.string.progress_kind_days
    MilestoneKind.WORDS_LEARNED -> R.string.progress_kind_words
    MilestoneKind.ANKI -> R.string.progress_kind_anki
    MilestoneKind.SPEED -> R.string.progress_kind_speed
}

/** The goal, short enough for a tile: "250k", "1M", "First card". */
@Composable
private fun goalLabel(milestone: Milestone): String = when {
    milestone.kind == MilestoneKind.ANKI -> stringResource(R.string.progress_first_card)
    milestone.goal >= 1_000_000 && milestone.goal % 1_000_000 == 0L -> "${milestone.goal / 1_000_000}M"
    (milestone.goal >= 10_000 || milestone.kind == MilestoneKind.SPEED) && milestone.goal % 1_000 == 0L ->
        "${milestone.goal / 1_000}k"
    else -> number(milestone.goal)
}

/** "100,000 characters read", "First Anki card". */
@Composable
internal fun milestoneTitle(milestone: Milestone): String {
    val goal = number(milestone.goal)
    val quantity = milestone.goal.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return when (milestone.kind) {
        MilestoneKind.CHARACTERS -> pluralStringResource(R.plurals.milestone_characters, quantity, goal)
        MilestoneKind.HOURS -> pluralStringResource(R.plurals.milestone_hours, quantity, goal)
        MilestoneKind.BOOKS -> pluralStringResource(R.plurals.milestone_books, quantity, goal)
        MilestoneKind.DAYS -> pluralStringResource(R.plurals.milestone_days, quantity, goal)
        MilestoneKind.WORDS_LEARNED -> pluralStringResource(R.plurals.milestone_words, quantity, goal)
        MilestoneKind.ANKI -> stringResource(R.string.milestone_anki)
        MilestoneKind.SPEED -> stringResource(R.string.milestone_speed, goal)
    }
}
