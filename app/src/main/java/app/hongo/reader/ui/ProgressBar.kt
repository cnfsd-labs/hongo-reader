package app.hongo.reader.ui

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A progress bar without Material's dot at the end of the track (distracting next to the text). */
@Composable
fun ProgressBar(progress: () -> Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(progress = progress, modifier = modifier, drawStopIndicator = {})
}
