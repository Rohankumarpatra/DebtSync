# EmiGo

A native Material 3 Android app (Kotlin + Jetpack Compose) for tracking personal loan EMIs with **TOTP Authenticator Lock**, **Due-Date EMI Reminders**, **Payoff Simulator**, **In-App GitHub Release Update Checker**, and a **Gemini-themed Dark/Light UI**.

## Key Features
- **Zero Pre-Loaded Data & Unlimited Months**: Starts empty so anyone can add loans of any length (3, 12, 24, 36, or 60+ months) from any start month.
- **Smart Loan Builder**: Enter Monthly EMI + Months Count (or quick presets `3m/6m/12m/24m/36m`) with live total/date-range preview and optional per-month customization.
- **TOTP Authenticator Sync**: Standard RFC 6238 6-digit authentication (compatible with Google Authenticator, Microsoft Authenticator, Authy, etc.) with QR code setup, manual Base32 key, Keystore-encrypted storage, and a one-time backup recovery code.
- **EMI Due-Date Reminders**: Automatic in-app pop-up reminder on login plus scheduled daily notifications before each loan's set due day (configurable 1–14 days ahead).
- **Overview, Matrix & Simulator**: Smooth animated charts & progress bars, searchable/filterable EMI matrix, Snowball & Cashflow Relief payoff simulator, and `$` / `₹` currency toggle.
- **Dark / Light Theme + Gemini Palette**: Soft Gemini aurora background and matching Gemini-styled popups with automatic system theme detection and manual toggle.
- **Direct In-App Updates**: Automatically checks GitHub Releases for newer signed APKs (and includes **Check for Updates** in the menu).

## Shipping Updates
Every push to `main` or `v*` tag triggers [android-build.yml](.github/workflows/android-build.yml) to build a **release-signed APK** (`EmiGo.apk`) using the repository's release keystore secrets, and `v*` tags automatically publish a GitHub Release:
1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Commit and push to `main`, then create & push a tag (e.g., `git tag v2.4 && git push origin v2.4`).
3. Existing users can tap **Download Update** in the app (or download `EmiGo.apk` from Releases) and update in-place without losing saved loans or Authenticator settings.
