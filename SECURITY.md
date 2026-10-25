# Security

## Reporting

Please report vulnerabilities privately through GitHub's **Report a vulnerability** button on this repository (Security tab), not in a public issue.

## What never touches the app

**Your Claude password.** You type it only on claude.com, in your own browser. Pinch receives a one-time code, which it trades for tokens. Those tokens let it read your usage and nothing about your password.

## Threat model: how an attacker would try, and what stops them

| Attack | Defence |
|---|---|
| Steal the tokens from storage | AES-256-GCM under a non-exportable Android Keystore key (StrongBox chip where the phone has one). On disk there is only ciphertext, in app-private storage. |
| Pull them out through a backup or "transfer to new phone" | Backup and device transfer are switched off for all app data. |
| Read them in logs or crash reports | No logging in the app; release builds strip every `android.util.Log` call. No crash reporter, no analytics. |
| Intercept the network (rogue Wi-Fi, a certificate installed on the phone) | HTTPS only; only the system's certificate authorities are trusted, so user-installed ones are ignored; host allow-list (`api.anthropic.com`, `platform.claude.com`, `status.claude.com`); redirects off; responses size-capped. |
| A fake sign-in page from an app that claims claude.com links | Sign-in opens directly in your default browser, not through whatever app claims the link. |
| Swap in their own sign-in code (log you into an attacker's account) | PKCE (S256) with a state value that must match the sign-in this phone started (constant-time check). |
| Read the code from the screen, the clipboard or the keyboard | `FLAG_SECURE` while it is on screen; the field is marked accessibility-sensitive (Android 14+); incognito keyboard (no personalised learning, no suggestions, no autofill); the clipboard is cleared after connecting. |
| Overlay or tapjacking over the sign-in form | Connect ignores touches that pass through another app's overlay. |
| Hijack the app's task with a look-alike screen (StrandHogg) | Empty task affinity on the only activity. |
| Trigger the widget's actions from another app | The widget's buttons, the refresh alarm and the reset-alert alarm go to receivers that are not exported, through immutable explicit PendingIntents. The debug demo hook exists only in debug builds and needs a system-only permission. |
| Ship a poisoned build (a swapped Gradle or library) | The Gradle wrapper jar is the official 8.7 one, and CI validates it on every push. The Gradle download is pinned by SHA-256. All 287 build dependencies are pinned by SHA-256 in `gradle/verification-metadata.xml`, so a changed artifact fails the build. |
| Ship a fake APK as an "update" | Releases are signed. Android refuses an update signed with a different key, and the certificate fingerprint is in the README so you can check it yourself. |

Usage percentages, reset times and look settings live in ordinary app-private storage; they are not secrets.

Permissions are kept to the minimum: internet, network state, the wake lock WorkManager needs for background refresh, vibration (the tick when the widget's refresh button is tapped), and notifications (usage alerts; Android 13+ asks only when the user switches an alert on, and every alert is off by default). The boot and foreground-service permissions WorkManager adds by default are removed, and class names are not obfuscated, because both are common antivirus triggers for sideloaded apps.

## Deliberately not done (they would cost you in day-to-day use)

- **Certificate pinning.** If Anthropic changed certificate authorities, every installed copy would stop updating until an app update arrived. System CAs only, with user CAs ignored, already blocks the realistic interception attacks.
- **Requiring the phone to be unlocked to use the token.** The widget refreshes in the background while the phone is locked; that would freeze it.
- **App lock, root detection.** Friction for you, and trivially bypassed by the kind of attacker they target.

## Known limits

- The usage endpoint and the OAuth client belong to Claude Code and are undocumented; Anthropic may change them.
- The token carries Claude Code's standard scopes (`user:profile user:inference`), broader than reading usage needs. The app only ever calls the usage endpoint, and only on the allow-listed hosts.
- A rooted or otherwise compromised phone can read any app's memory; no app can defend against that.
- **For maintainers:** the release signing key is the crown jewel. Whoever holds it can ship "updates" that phones accept. Keep it and its password offline (a password manager, an encrypted USB stick), never in the repository.
