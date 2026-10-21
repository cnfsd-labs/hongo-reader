package app.honmani.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.honmani.reader.R
import app.honmani.reader.data.difficulty.DifficultyBand

/** "Upper intermediate · ≈ JLPT N2", for a score on the 1–100 difficulty scale (books and skill). */
@Composable
fun difficultyBand(score: Int): String {
    val band = DifficultyBand.of(score)
    val name = stringResource(
        when (band) {
            DifficultyBand.N5 -> R.string.difficulty_n5
            DifficultyBand.N4 -> R.string.difficulty_n4
            DifficultyBand.N3 -> R.string.difficulty_n3
            DifficultyBand.N2 -> R.string.difficulty_n2
            DifficultyBand.N1 -> R.string.difficulty_n1
            DifficultyBand.BEYOND_N1 -> R.string.difficulty_beyond_n1
        },
    )
    val jlpt = if (band == DifficultyBand.BEYOND_N1) {
        stringResource(R.string.difficulty_jlpt_beyond)
    } else {
        stringResource(R.string.difficulty_jlpt, band.name)
    }
    return stringResource(R.string.difficulty_band, name, jlpt)
}
