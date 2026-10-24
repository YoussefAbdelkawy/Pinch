package dev.pinch.app.widget

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
        // Keeps a 5-minute alarm alive, e.g. after a reboot (alarms do not survive one).
        if (Settings.intervalMinutes(ctx) < 15) FastRefresh.arm(ctx)
        val state = UsageRepo.refresh(ctx)
        if (state.justReset) {
            // The 5-hour window reset: Clawd jumps for joy with confetti, then settles.
            val party = Clawd.celebrate()
            ClawdWidget.updateAll(ctx, state, party)
            delay(party.size * Clawd.FRAME_MS + 300)
        }
        ClawdWidget.updateAll(ctx, state)
        // A tap already showed its answer (an error line); retrying it would only spin quietly.
        if (inputData.getBoolean(KEY_TAP, false)) return Result.success()
        return when (state.error) {
            null, "auth_error" -> Result.success()
            else -> Result.retry()
        }
    }

    companion object {
        private const val PERIODIC = "clawd_periodic_refresh"
        private const val NOW = "clawd_refresh_now"
        private const val TAP = "clawd_refresh_tap"
        private const val KEY_TAP = "tap"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context, replace: Boolean = false) {
            FastRefresh.arm(context)
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

        /**
         * A background refresh (the 5-minute alarm, a new widget). KEEP, not REPLACE: replacing
         * cancelled a refresh that was already halfway through its network call.
         */
        fun refreshNow(context: Context) {
            val work = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, work)
        }

        /**
         * The widget's refresh button. A plain job waits for the system's next batch, which on
         * Battery Saver or in Doze can be an hour, and a broadcast receiver gets no network there.
         * Android 12+ runs an expedited job right away, with network, even on Battery Saver.
         * (Below 12 an expedited job needs a foreground notification, so it stays a plain job.)
         * No network constraint: offline, the worker still runs, stops the spinner and says so.
         */
        fun refreshFromTap(context: Context) {
            val work = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(workDataOf(KEY_TAP to true))
                .apply { if (Build.VERSION.SDK_INT >= 31) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(TAP, ExistingWorkPolicy.REPLACE, work)
        }

        fun cancel(context: Context) {
            FastRefresh.cancel(context)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
