package dev.pinch.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pinch.app.art.Clawd
import dev.pinch.app.data.History
import dev.pinch.app.data.Limit
import dev.pinch.app.data.Usage
import dev.pinch.app.data.UsageRepo
import dev.pinch.app.widget.ClawdWidget

/**
 * Debug builds only: paints the widget with made-up numbers, for screenshots and for
 * checking every mood without an account. Never part of a release APK.
 *
 *   adb shell am broadcast -n dev.pinch.app/.DemoReceiver --ei session 42 --ei week 31 [--es theme halloween] [--es skin chef] [--ez party true] [--es trick spin] [--ez app true] [--ei reset_s 90] [--ez history true]
 *     [--es account a2 --es name Work] [--es plan "Max 20x"] [--ez signedout true]
 *
 * With --es account, the numbers go to that account (a1 to a3), which is added to the list if
 * it is not there yet: run it once per account for a widget with several.
 */
class DemoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val account = intent.getStringExtra("account") ?: Accounts.FIRST
        if (intent.hasExtra("account")) {
            Accounts.keep(context)
            Accounts.add(context, account)
        }
        intent.getStringExtra("name")?.let { Accounts.setName(context, account, it) }
        intent.getStringExtra("theme")?.let { Settings.setThemeChoice(context, account, it) }
        intent.getStringExtra("skin")?.let { Settings.setSkinChoice(context, account, it) }
        val now = System.currentTimeMillis()
        val session = intent.getIntExtra("session", 42)
        val week = intent.getIntExtra("week", 31)
        val usage = Usage(
            session = Limit("Session", session, when {
                intent.getBooleanExtra("idle", false) -> null
                intent.hasExtra("reset_s") -> now + intent.getIntExtra("reset_s", 60) * 1000L
                else -> now + 2 * 3_600_000L + 14 * 60_000L
            }),
            weekly = Limit("Week", week, now + 3 * 86_400_000L + 4 * 3_600_000L),
            models = listOf(Limit("Fable", (week * 0.6).toInt(), null), Limit("Sonnet", week / 4, null)),
            plan = intent.getStringExtra("plan") ?: "Max 5x",
            fetchedAt = now
        )
        val error = if (intent.getBooleanExtra("signedout", false)) "auth_error" else null
        val state = UsageRepo.State(usage, error, UsageRepo.ServiceStatus("none", "All Systems Operational"))
        // --ez app true: the app itself also shows these numbers, as if signed in.
        if (intent.hasExtra("app")) {
            val on = intent.getBooleanExtra("app", false)
            Settings.setDemo(context, on)
            if (on) UsageRepo.store(context, account, usage, state.status, error)
        }
        // --ez history true: a made-up week of history, for the charts.
        if (intent.getBooleanExtra("history", false)) {
            History.clear(context, account)
            val rnd = java.util.Random(7)
            val h = 3_600_000L
            var t = now - 7 * 24 * h
            var windowEnd = t + 5 * h
            var pct = 0
            var wk = 0
            while (t < now) {
                if (t >= windowEnd) { windowEnd = t + 5 * h; pct = 0 }
                val busy = java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneId.systemDefault()).hour in 9..23
                if (busy) { pct = (pct + rnd.nextInt(14)).coerceAtMost(100); wk = (wk + rnd.nextInt(3)).coerceAtMost(99) }
                History.record(context, account, Usage(Limit("Session", pct, windowEnd), Limit("Week", wk, now + 3 * 86_400_000L), emptyList(), "Pro", t), t)
                t += 20 * 60_000L
            }
        }
        dev.pinch.app.widget.FastRefresh.armReset(context, usage.nextReset)
        val act = when {
            intent.getBooleanExtra("party", false) -> Clawd.celebrate()
            else -> intent.getStringExtra("trick")?.let { Clawd.SEQS[it] }
        }
        ClawdWidget.updateAll(context, mapOf(account to state), act, actFor = setOf(account))
    }
}
