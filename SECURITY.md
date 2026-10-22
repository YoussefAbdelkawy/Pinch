# Security

## Reporting

Please report vulnerabilities privately through GitHub's **Report a vulnerability** button on this repository (Security tab), not in a public issue.

## What the app protects, and how

| Asset | Protection |
|---|---|
| OAuth refresh and access tokens | AES-256-GCM, key in the Android Keystore (non-exportable); never logged; excluded from backup and device transfer; wiped on sign-out |
| Tokens in transit | HTTPS only; host allow-list (`api.anthropic.com`, `platform.claude.com`, `status.claude.com`); system CAs only; no redirects; response size capped |
| The pasted sign-in code | PKCE (S256) and a constant-time state check; no keyboard learning or autofill; `FLAG_SECURE` while the code is on screen; clipboard cleared after connect; Connect ignores touches through overlays |
| Widget actions | A non-exported receiver behind immutable, explicit PendingIntents |
| Release builds | R8-minified; all `android.util.Log` calls stripped; the debug demo receiver is not included |

Usage percentages, reset times and look settings are kept in ordinary app-private storage. They are not secrets.

## Known limits

- The usage endpoint and the OAuth client belong to Claude Code and are undocumented; Anthropic may change or restrict them.
- The token is requested with Claude Code's standard scopes (`user:profile user:inference`), which are broader than reading usage needs. The app only ever calls the usage endpoint.
- A rooted or compromised phone can read any app's memory; nothing here defends against that.
