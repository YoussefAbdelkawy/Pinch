package dev.clawdusage.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.clawdusage.app.art.Clawd
import dev.clawdusage.app.art.everyOther
import dev.clawdusage.app.data.Limit
import dev.clawdusage.app.data.Usage
import dev.clawdusage.app.data.UsageRepo
import dev.clawdusage.app.widget.ClawdWidget

/**
 * Debug builds only: paints the widget with made-up numbers, for screenshots and for
 * checking every mood without an account. Never part of a release APK.
 *
 *   adb shell am broadcast -n dev.clawdusage.app/.DemoReceiver --ei session 42 --ei week 31 [--es theme halloween] [--ez party true]
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
        val party = if (intent.getBooleanExtra("party", false)) Clawd.celebrate().everyOther() else null
        ClawdWidget.updateAll(context, state, party, signedIn = true)
    }
}
