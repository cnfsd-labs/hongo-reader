package app.honmani.reader.data.difficulty

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.honmani.reader.HonmaniApplication

/** Grades books in the background; see [BookGrader]. */
class DifficultyWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as HonmaniApplication).container
        container.bookGrader.gradeAll()
        return Result.success()
    }

    companion object {
        private const val NAME = "grade-books"

        /** Grades new books (or all, after a dictionary changed); a run already going is followed by another. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<DifficultyWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
