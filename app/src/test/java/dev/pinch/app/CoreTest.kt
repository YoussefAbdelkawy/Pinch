package dev.pinch.app

import dev.pinch.app.art.Clawd
import dev.pinch.app.art.Frame
import dev.pinch.app.art.Pix
import dev.pinch.app.art.Scenes
import dev.pinch.app.art.Skins
import dev.pinch.app.art.Stage
import dev.pinch.app.art.Themes
import dev.pinch.app.data.Level
import dev.pinch.app.data.Usage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CoreTest {

    @Test fun parsesLimitsArray() {
        val json = JSONObject(
            """{"limits":[
                {"kind":"session","percent":42.4,"resets_at":"2026-10-22T18:00:00+00:00"},
                {"kind":"weekly_all","percent":31,"resets_at":"2026-10-26T05:59:00Z"},
                {"kind":"weekly_scoped","percent":19,"resets_at":null,"scope":{"model":{"display_name":"Fable"}}},
                {"kind":"weekly_scoped","percent":7,"scope":{"model":{"display_name":"Sonnet"}}}
            ]}"""
        )
        val u = Usage.parse(json, now = 0)
        assertEquals(42, u.session!!.percent)
        assertEquals(58, u.sessionLeft)
        assertEquals(31, u.weekly!!.percent)
        assertEquals("Fable", u.topModel!!.label)
        assertNull(u.models[0].resetsAt)
        assertEquals(4, u.heartHalves) // ceil(58 * 6 / 100)
        val back = Usage.fromCache(u.toJson())!!
        assertEquals(u.session, back.session)
        assertEquals(u.models, back.models)
    }

    @Test fun parsesLegacyFields() {
        val json = JSONObject(
            """{"five_hour":{"utilization":100.0,"resets_at":"2026-10-22T18:00:00Z"},
                "seven_day":{"utilization":12.6,"resets_at":null},
                "seven_day_opus":null,
                "extra_usage":{"is_enabled":true,"monthly_limit":20000}}"""
        )
        val u = Usage.parse(json)
        assertEquals(100, u.session!!.percent)
        assertEquals(0, u.heartHalves)
        assertEquals(13, u.weekly!!.percent)
        assertNull(u.weekly!!.resetsAt)
        assertEquals("Max 20x", u.plan)
        assertTrue(u.models.isEmpty())
    }

    @Test fun levels() {
        assertEquals(Level.OK, Level.of(59))
        assertEquals(Level.WARN, Level.of(60))
        assertEquals(Level.CRIT, Level.of(85))
        assertEquals(Level.DEAD, Level.of(100))
    }

    @Test fun seasons() {
        assertEquals("halloween", Themes.seasonal(LocalDate.of(2026, 10, 22)))
        assertEquals("christmas", Themes.seasonal(LocalDate.of(2026, 12, 24)))
        assertEquals("christmas", Themes.seasonal(LocalDate.of(2027, 1, 3)))
        assertEquals("valentine", Themes.seasonal(LocalDate.of(2027, 2, 14)))
        assertEquals("spring", Themes.seasonal(LocalDate.of(2027, 4, 1)))
        assertEquals("summer", Themes.seasonal(LocalDate.of(2027, 7, 1)))
        assertEquals("classic", Themes.seasonal(LocalDate.of(2027, 9, 10)))
        for (t in Themes.ALL) assertEquals(t.id, Themes.byId(t.id).id)
    }

    @Test fun everySkinParsesAndEveryThemeSkinExists() {
        val dir = listOf(File("src/main/assets/skins"), File("app/src/main/assets/skins")).first { it.isDirectory }
        val ids = dir.listFiles()!!.filter { it.name.endsWith(".txt") }.map { f ->
            val s = Skins.parse(f.nameWithoutExtension, f.readText())
            assertNotNull(f.name, s)
            assertTrue(f.name, s!!.layers.isNotEmpty())
            s.id
        }.toSet()
        for (t in Themes.ALL) assertTrue(t.skin, t.skin == "classic" || t.skin in ids)
    }

    @Test fun drawsEveryAnimationAndScene() {
        val crown = Skins.parse("crown", "---\nname: Crown\ng: #FBBF24\n---\n.....g...g...g....\n")!!
        val frames = Clawd.SEQS.values.flatten() + Clawd.celebrate() +
            Clawd.Mood.values().flatMap { m -> (0..6).flatMap { h -> Stage.loop(m, h, 40, 140).map { it.f } } }
        for (f in frames) {
            val p = Pix(Clawd.CANVAS_W, Clawd.CANVAS_H + Scenes.GROUND)
            Clawd.draw(p, f, crown, Clawd.ORIGIN_X, Clawd.ORIGIN_Y, 0xFFFFFFFF.toInt())
        }
        // The default pose: body colour on his chest, his eyes black, the crown on his head.
        val p = Clawd.render(Frame(), crown, 0)
        assertEquals(Skins.CLASSIC.body, p.px[(Clawd.ORIGIN_Y + 4) * p.w + Clawd.ORIGIN_X + 9])
        assertEquals(Skins.CLASSIC.eyes, p.px[(Clawd.ORIGIN_Y + 2) * p.w + Clawd.ORIGIN_X + 5])
        assertEquals(Pix.color("#FBBF24"), p.px[(Clawd.ORIGIN_Y - 1) * p.w + Clawd.ORIGIN_X + 5])
        for (t in Themes.ALL) for (h in intArrayOf(3, 12, 20)) {
            val bg = Scenes.background(t.scene, 140, h)
            val beats = Stage.loop(Clawd.Mood.IDLE, 6, 40, 140)
            for (k in beats.indices) Stage.compose(bg, t.scene, h, k, beats.size, beats[k], crown)
        }
    }

    /** Loops must be seamless: full length, and the crab ends where he started, standing normally. */
    @Test fun loopsAreSeamless() {
        for (m in Clawd.Mood.values()) for (h in 0..6) for (w in intArrayOf(40, 90, 160)) for (pct in intArrayOf(0, 50, 100)) {
            val home = Stage.home(w, pct)
            val b = Stage.loop(m, h, home, w)
            assertEquals(Stage.LOOP, b.size)
            assertEquals("$m $h $w $pct", b.first().x, b.last().x)
            for (beat in b) assertTrue(beat.x in 0..Stage.maxX(w))
            // Walking never takes both legs off the ground on consecutive frames.
            for (i in 1 until b.size) assertTrue(b[i - 1].f.feet == "both" || b[i].f.feet == "both" || b[i - 1].f.flip)
        }
    }

    @Test fun mergesBothApiShapes() {
        val json = JSONObject(
            """{"five_hour":{"utilization":12,"resets_at":"2026-10-22T18:00:00Z"},
                "seven_day":{"utilization":30,"resets_at":"2026-10-26T05:59:00Z"},
                "seven_day_opus":{"utilization":3,"resets_at":null},
                "seven_day_sonnet":null,
                "seven_day_oauth_apps":{"utilization":50},
                "limits":[{"kind":"weekly_scoped","percent":22,"resets_at":null,"scope":{"model":{"display_name":"Fable"}}}]}"""
        )
        val u = Usage.parse(json)
        assertEquals(12, u.session!!.percent)
        assertEquals(30, u.weekly!!.percent)
        assertEquals(listOf("Fable", "Opus"), u.models.map { it.label })
        assertEquals("Fable", u.topModel!!.label)
    }
}
