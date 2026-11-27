# Debt Sync (Clean Edition)

A native Material 3 Android app (Kotlin + Jetpack Compose) for tracking personal loan EMIs with **TOTP Authenticator Lock**, **Due-Date EMI Reminders**, **Payoff Simulator**, and a **Gemini-themed Dark/Light UI**.

## Key Features
- **Zero Pre-Loaded Data**: Starts completely empty so anyone can add their own loans and monthly EMI schedules.
- **TOTP Authenticator Sync**: Standard RFC 6238 6-digit authentication (compatible with Google Authenticator, Microsoft Authenticator, Authy, etc.) with QR code setup, manual Base32 key, Keystore-encrypted storage, and a one-time backup recovery code.
- **EMI Due-Date Reminders**: Automatic in-app pop-up reminder on login plus scheduled daily notifications before each loan's set due day (configurable 1–14 days ahead).
- **Overview, Matrix & Simulator**: Live KPIs, monthly stacked chart, searchable/filterable EMI matrix, Snowball & Cashflow Relief payoff simulator, CSV export, and `$` / `₹` currency toggle.
- **Dark / Light Theme + Gemini Palette**: Soft Gemini aurora background with automatic system theme detection and manual toggle.

## How Updates Work
To update the installed app in-place without losing saved loan data:
1. Keep `applicationId` (`com.rohan.debtsync.clean`) unchanged in `app/build.gradle.kts`.
2. Increment `versionCode` and `versionName` in `app/build.gradle.kts`.
3. Sign with the same keystore (for CI releases, add your keystore secrets in GitHub repository settings) and install the new APK over the existing app.
