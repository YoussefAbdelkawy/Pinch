package dev.pinch.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pinch.app.art.Clawd
import dev.pinch.app.data.Limit
import dev.pinch.app.data.Usage
import dev.pinch.app.data.UsageRepo
import dev.pinch.app.widget.ClawdWidget

/**
 * Debug builds only: paints the widget with made-up numbers, for screenshots and for
 * checking every mood without an account. Never part of a release APK.
 *
 *   adb shell am broadcast -n dev.pinch.app/.DemoReceiver --ei session 42 --ei week 31 [--es theme halloween] [--es skin chef] [--ez party true] [--es trick spin] [--ez app true]
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("theme")?.let { Settings.setThemeChoice(context, it) }
        intent.getStringExtra("skin")?.let { Settings.setSkinChoice(context, it) }
        val now = System.currentTimeMillis()
        val session = intent.getIntExtra("session", 42)
        val week = intent.getIntExtra("week", 31)
        val usage = Usage(
            session = Limit("Session", session, if (intent.getBooleanExtra("idle", false)) null else now + 2 * 3_600_000L + 14 * 60_000L),
            weekly = Limit("Week", week, now + 3 * 86_400_000L + 4 * 3_600_000L),
            models = listOf(Limit("Fable", (week * 0.6).toInt(), null), Limit("Sonnet", week / 4, null)),
            plan = "Max 5x",
            fetchedAt = now
        )
        val state = UsageRepo.State(usage, null, UsageRepo.ServiceStatus("none", "All Systems Operational"))
        // --ez app true: the app itself also shows these numbers, as if signed in.
        if (intent.hasExtra("app")) {
            val on = intent.getBooleanExtra("app", false)
            Settings.setDemo(context, on)
            if (on) UsageRepo.store(context, usage, state.status)
        }
        val act = when {
            intent.getBooleanExtra("party", false) -> Clawd.celebrate()
            else -> intent.getStringExtra("trick")?.let { Clawd.SEQS[it] }
        }
        ClawdWidget.updateAll(context, state, act, signedIn = true)
    }
}
