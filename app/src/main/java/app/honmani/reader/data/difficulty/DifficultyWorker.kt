package app.honmani.reader.data.difficulty

import android.content.Context
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
            // No battery constraint: a library is graded in minutes, once. With one, nothing was
            // graded while the battery was low (on many phones until it is back above about 25%),
            // and the library looked as if grading were broken.
            val request = OneTimeWorkRequestBuilder<DifficultyWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
