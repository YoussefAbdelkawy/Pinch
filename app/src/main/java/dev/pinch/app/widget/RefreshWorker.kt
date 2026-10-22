package dev.pinch.app.widget

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pinch.app.Settings
import dev.pinch.app.art.Clawd
import dev.pinch.app.auth.ClaudeAuth
import dev.pinch.app.data.UsageRepo
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Fetches usage in the background and redraws the widgets. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!ClaudeAuth.isSignedIn(ctx)) {
            ClawdWidget.updateAll(ctx)
            return Result.success()
        }
        val state = UsageRepo.refresh(ctx)
        if (state.justReset) {
            // The 5-hour window reset: Clawd jumps for joy with confetti, then settles.
            val party = Clawd.celebrate()
            ClawdWidget.updateAll(ctx, state, party)
            delay(party.size * Clawd.FRAME_MS + 300)
        }
        ClawdWidget.updateAll(ctx, state)
        return when (state.error) {
            null, "auth_error" -> Result.success()
            else -> Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "clawd_periodic_refresh"
        private const val NOW = "clawd_refresh_now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context, replace: Boolean = false) {
            val minutes = Settings.intervalMinutes(context).toLong().coerceAtLeast(15)
            val work = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }

        fun refreshNow(context: Context) {
            val work = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, work)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
