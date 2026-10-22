package dev.pinch.app.art

import java.time.LocalDate
import java.time.MonthDay

/**
 * Colour themes, after clawd-usage-winwidget's lib/theme.ps1. Each has a scene (the animated
 * strip Clawd walks on), a wallpaper pattern and the skin Clawd wears when the skin is "auto".
 */
data class Theme(
    val id: String,
    val name: String,
    val bg: Int,
    val card: Int,
    val textHi: Int,
    val text: Int,
    val muted: Int,
    val faint: Int,
    val accent: Int,
    val scene: String,
    val skin: String,
    /** Name of the tiled wallpaper drawable (res/drawable/pat_*.xml), or null. */
    val pattern: String?
)

object Themes {
    private fun c(hex: String) = Pix.color(hex)

    private fun theme(
        id: String, name: String, bg: String, card: String, textHi: String, text: String,
        muted: String, faint: String, accent: String, scene: String, skin: String, pattern: String? = null
    ) = Theme(id, name, c(bg), c(card), c(textHi), c(text), c(muted), c(faint), c(accent), scene, skin, pattern)

    val ALL: List<Theme> = listOf(
        theme("classic", "Classic", "#1A1A1E", "#26262C", "#F4F4F5", "#E4E4E7", "#8B8B94", "#52525B", "#60A5FA", "beach", "classic"),
        theme("oled", "OLED black", "#000000", "#141414", "#F4F4F5", "#E4E4E7", "#8B8B94", "#52525B", "#60A5FA", "beach", "classic"),
        theme("midnight", "Midnight", "#0F172A", "#1E293B", "#F1F5F9", "#E2E8F0", "#8494AB", "#475569", "#38BDF8", "beach", "classic"),
        theme("halloween", "Halloween", "#1C1326", "#2A1D38", "#FFF7ED", "#F5E6DA", "#A08B9C", "#5B4A63", "#F97316", "halloween", "witch", "pat_halloween"),
        theme("christmas", "Christmas", "#0E1A2B", "#16273D", "#F8FAFC", "#E2E8F0", "#94A3B8", "#475569", "#EF4444", "christmas", "santa", "pat_christmas"),
        theme("valentine", "Valentine", "#24101C", "#36182A", "#FFF1F2", "#FCE7F3", "#B48AA0", "#6B3A55", "#F472B6", "valentine", "bow", "pat_valentine"),
        theme("spring", "Spring", "#121A14", "#1C2A21", "#F0F5F1", "#E1EAE3", "#86998B", "#4B5B50", "#F9A8D4", "spring", "flowershorts", "pat_spring"),
        theme("summer", "Summer", "#0B1E2B", "#12304A", "#F0F9FF", "#E0F2FE", "#86A9BF", "#456679", "#FBBF24", "summer", "shades", "pat_summer"),
        theme("space", "Space", "#0E0820", "#1C1436", "#F5F3FF", "#E9E5F8", "#9A90BE", "#544A78", "#A78BFA", "space", "astronaut", "pat_space"),
        theme("ocean", "Underwater", "#0A2236", "#0F3149", "#F0F9FF", "#E0F2FE", "#86A9BF", "#456679", "#22D3EE", "ocean", "squarepants", "pat_ocean"),
        theme("cyberpunk", "Cyberpunk", "#0C0B16", "#17152A", "#F2FBFF", "#E3EEF5", "#8494A8", "#474F66", "#00E5FF", "cyberpunk", "netrunner", "pat_cyberpunk")
    )

    fun byId(id: String): Theme = ALL.firstOrNull { it.id == id } ?: ALL[0]

    /** "auto" follows the calendar; anything else is the theme with that id. */
    fun resolve(choice: String, today: LocalDate = LocalDate.now()): Theme =
        if (choice == "auto") byId(seasonal(today)) else byId(choice)

    /** The theme the calendar picks: spooky in October, festive in December, and so on. */
    fun seasonal(today: LocalDate): String {
        val d = MonthDay.from(today)
        fun inRange(from: MonthDay, to: MonthDay) = !d.isBefore(from) && !d.isAfter(to)
        return when {
            inRange(MonthDay.of(10, 1), MonthDay.of(11, 1)) -> "halloween"
            inRange(MonthDay.of(12, 1), MonthDay.of(12, 31)) || inRange(MonthDay.of(1, 1), MonthDay.of(1, 6)) -> "christmas"
            inRange(MonthDay.of(2, 7), MonthDay.of(2, 15)) -> "valentine"
            inRange(MonthDay.of(3, 20), MonthDay.of(5, 31)) -> "spring"
            inRange(MonthDay.of(6, 1), MonthDay.of(8, 31)) -> "summer"
            else -> "classic"
        }
    }

    /** Level colours, not themed: green, yellow, red, and grey when the limit is spent. */
    val OK = c("#4ADE80")
    val WARN = c("#FBBF24")
    val CRIT = c("#F87171")
    val DEAD = c("#6B7280")
}
