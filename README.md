# Clawd Usage for Android

Your Claude limits on your home screen, with Clawd: the crab from the Claude Code welcome screen.

An Android port of [clawd-usage-winwidget](https://github.com/Enhed/clawd-usage-winwidget): the same Clawd, the same click animations, the same skins and seasonal themes. It signs in the way [claude-pulse-android](https://github.com/G-biggy/claude-pulse-android) does, straight from the phone, with no server in between.

<p align="center">
  <img src="docs/widget.png" width="520" alt="Clawd Usage widget, Halloween theme" />
</p>

## What it shows

- **Session**: the 5-hour window, as a big percentage, **what's left**, and a live countdown to the reset
- **Week**: the 7-day limit across all models, and when it resets
- **Top model**: the per-model weekly limit you've used most (Fable, Sonnet…)
- **Clawd's health**: three hearts with halves, one half per ~16.7% of the session left
- **Service status** from status.claude.com, and when the numbers were last fetched
- Bars turn yellow from 60% and red from 85%, as in Clawd

## Clawd

Clawd lives on a little animated stage inside the widget and walks along it as your session fills up.

| Session | Clawd |
|---|---|
| No session running | Burrowed in the sand, only his eyes out, "z"s |
| Normal | Looks around, blinks, waves, taps his feet |
| 85% and up | Squints and sweats |
| 100% | Belly-up with X eyes |
| The 5-hour window resets | Jumps for joy with confetti |
| **Tap him** | One of Claude Code's own click animations (jump, spin, peekaboo, wink, boop, turn…) |

The app has a big Clawd too. Tap him for tricks.

## Themes

**By season** (the default) changes with the calendar:

| Dates | Theme | Scene | Clawd wears |
|---|---|---|---|
| Oct 1 – Nov 1 | Halloween | Graveyard, moon, bats, jack-o'-lanterns, a ghost | Witch hat |
| Dec 1 – Jan 6 | Christmas | Snowfall, a tree with blinking lights, a snowman | Santa hat |
| Feb 7 – 15 | Valentine | Floating hearts | Bow |
| Mar 20 – May 31 | Spring | Tulips, daisies, butterflies | Flower shorts |
| Jun – Aug | Summer | Beach, palms, gulls, waves | Shades |
| Rest of the year | Classic | Clawd's beach; the sky follows the time of day | — |

You can also pick one: Classic, OLED black, Midnight, Space, Underwater or Cyberpunk. All 27 of Clawd's skins are there too, or let the theme choose.

<p align="center"><img src="docs/themes.jpg" width="720" alt="Themes and moods" /></p>

## Install

1. Download `clawd-usage-<version>.apk` from [Releases](../../releases/latest) on your phone and open it. Android will ask you to allow installs from your browser; that is normal for apps outside the Play Store.
2. Open **Clawd Usage** → **Sign in with Claude**. Approve in the browser, then copy the code the page shows.
3. Back in the app, tap **Connect**. You can paste the code, or leave the field empty and it reads the clipboard.
4. Tap **Add the widget to the home screen**, or long-press the home screen → Widgets → Clawd Usage. Resize it as you like: smaller sizes hide the extra rows.

Android 8.0 or newer. It refreshes every 15 minutes by default (you can pick 30 or 60). Tap ⟳ on the widget to refresh now.

**Verify the APK.** Release APKs are signed with this certificate (SHA-256):
`39:D7:53:0A:8C:27:D9:C9:0A:B5:35:A9:7C:F2:8C:40:D1:BC:E6:5D:EA:88:A7:BC:58:94:63:26:77:6C:DD:52`.
Check it with `apksigner verify --print-certs clawd-usage-*.apk`.

## Privacy and security

- **Direct to Anthropic.** The app talks only to `api.anthropic.com` (usage), `platform.claude.com` (sign-in) and `status.claude.com` (public status, no credentials). Those hosts are hard-coded in an allow-list, HTTPS-only, with redirects off.
- **Tokens are encrypted** with AES-256-GCM under a key kept in the Android Keystore, which can't be exported. They are never logged, never shown and never leave the phone except to Anthropic.
- **No backups.** Android backup and device-to-device transfer are turned off for the app's data.
- **No user-installed CAs.** The app trusts only the system's certificate authorities, so a certificate added to the phone can't be used to intercept your token.
- **The sign-in code** is protected while you handle it. The field doesn't feed the keyboard's learning or autofill. The screen is hidden from screenshots while the code is on it. The clipboard is cleared after a successful connect.
- **Sign-in is the PKCE "paste the code" flow** Claude Code itself uses, with the state checked, so the phone gets its own session and never borrows your computer's.
- **No analytics, ads, crash reporters or servers of our own.** Permissions: Internet and network state, plus what WorkManager adds for background refreshes (wake lock, reschedule after reboot).
- Widget buttons go to a private receiver; other apps can't trigger them.
- **Sign out** wipes the tokens and cached numbers.

See [SECURITY.md](SECURITY.md) to report a problem.

## Build from source

```bash
git clone <this repo>
cd clawd-usage-android
./gradlew assembleDebug        # needs JDK 17 and the Android SDK (platform 34)
./gradlew testDebugUnitTest
```

For a signed release, create `keystore.properties` in the repo root (it is git-ignored):

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

then run `./gradlew assembleRelease`.

Debug builds include a demo hook for screenshots, which is not in release builds:
`adb shell am broadcast -n dev.clawdusage.app/.DemoReceiver --ei session 91 --ei week 72 --es theme christmas`.

`tools/` holds the scripts that generate the wallpaper tiles, level bars and the icon.

## How it works

- `auth/`: PKCE sign-in, token refresh and the Keystore-encrypted store
- `data/`: the HTTPS client (allow-listed), usage parsing and cache, service status
- `art/`: Clawd's sprite and animations (ported glyph for glyph from Claude Code via clawd-usage-winwidget), skins, themes and scenes, drawn as logical pixels and scaled crisp
- `widget/`: the widget. Home-screen widgets can't run animation code, so every moving part is a `ViewFlipper` of pre-drawn frames that the launcher flips on its own: Clawd at 120 ms, the scene at 450 ms, and the hearts when they beat.

The usage endpoint (`/api/oauth/usage`) is **undocumented** and may change. If it does, the widget keeps the last numbers and marks them stale.

## Credits

- Clawd's sprite, animations, skins, theme palettes and the Halloween scene are from [clawd-usage-winwidget](https://github.com/Enhed/clawd-usage-winwidget) by Venger Sergey (MIT). Clawd himself is from Claude Code.
- The sign-in and usage approach is from [claude-pulse-android](https://github.com/G-biggy/claude-pulse-android) (MIT).

## Disclaimer

Not affiliated with, endorsed by or supported by Anthropic. It uses Claude Code's public OAuth client and an undocumented endpoint to read your own account's usage. Use at your own discretion.

MIT licensed; see [LICENSE](LICENSE).
