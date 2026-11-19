> **Repository rename needed (one minute):** GitHub → Settings → General → Repository name → rename `vitacreate-ai` to **`floatnote`**. This repo *is* FloatNote; it was extracted from `daily-motivation-ai` where it was nested by mistake. Package id `io.github.sachin102492.floatnote` is unchanged for Play Store.

> **Re-add Play secrets** on the new repo (Settings → Secrets → Actions): `FLOATNOTE_KEYSTORE_BASE64`, `FLOATNOTE_KEYSTORE_PASSWORD`, `FLOATNOTE_KEY_ALIAS`, `FLOATNOTE_KEY_PASSWORD`, and optional `PLAY_SERVICE_ACCOUNT_JSON`.

# FloatNote: floating notepad for Android

A free, open-source notepad bubble that floats over any app and **autosaves on every keystroke**.
Your notes never leave your device. The Google Play build shows one small banner ad on the notes list, which any supporter tip removes; the FOSS build has no ads and no Internet permission.

## Features

| | |
|---|---|
| **Chat-head bubble** | Drag it anywhere. It moves on spring physics (like Messenger chat heads). |
| **Fling & snap** | Fling it and it springs, with a little bounce, to the nearest screen edge. |
| **Tucks into the side** | After 2.5 s idle it slides half off the edge and fades, so it stays out of the way. Touch it and it comes back. |
| **Magnetic dismiss** | While you drag, a ✕ target rises from the bottom. Get close and the bubble snaps into it with a haptic tick. Let go to hide it. |
| **Grow-from-bubble panel** | Tap the bubble and the notepad scales out of it with an overshoot. Tap outside or press Back and it shrinks back in. |
| **Autosave** | Saves 400 ms after you stop typing, and again when the panel closes, the app pauses, or you switch notes. Files are written atomically, so a crash never corrupts them. |
| **Multiple notes** | Page between notes in the bubble with slide transitions, start a new one, copy, or open it in the app. |
| **Pop-out** | Use "Float this note" in the editor to pop any note into the bubble (like picture-in-picture for text). |
| **Quick Settings tile** | Toggle the bubble from the notification shade. |
| **Material You design** | Amber sticky-note theme with light and dark modes and edge-to-edge layouts. Swipe a note away to delete it, with Undo. |

## Download

Every push builds the app in GitHub Actions (**Actions → FloatNote Android → Artifacts**):

- `FloatNote.apk` is the installable Play-flavour build.
- `FloatNote-foss.apk` is the Google-free build with all colours unlocked.
- `FloatNote.aab` is the App Bundle for Google Play.

To publish a GitHub Release with the APK attached, push a tag:

```bash
git tag floatnote-v1.0.0 && git push origin floatnote-v1.0.0
```

## Build locally

You need Android Studio (or JDK 17 with the Android SDK):

```bash
cd floating-notepad
./gradlew assemblePlayDebug   # app/build/outputs/apk/play/debug/app-play-debug.apk
```

## Release signing

Without signing secrets, CI signs release builds with the debug key. They install fine, but
Google Play will not accept them. To sign properly:

```bash
keytool -genkey -v -keystore floatnote.jks -keyalg RSA -keysize 2048 -validity 10000 -alias floatnote
base64 -w0 floatnote.jks   # paste as a secret
```

Add these repository secrets: `FLOATNOTE_KEYSTORE_BASE64`, `FLOATNOTE_KEYSTORE_PASSWORD`,
`FLOATNOTE_KEY_ALIAS`, `FLOATNOTE_KEY_PASSWORD`. **Back up the keystore.** You can never
update the app on Play without it.

## Google Play checklist

The full step-by-step guide is in **[PLAY_STORE.md](PLAY_STORE.md)**.

The app is built to pass review with the least friction:

- **Target SDK 36**, which Play currently requires for new apps.
- **Display over other apps** is a normal, user-granted permission. The app shows an
  in-app disclosure explaining why before it opens the system setting.
- **Foreground service (`specialUse`)**: in Play Console → *App content → Foreground service
  permissions*, declare **Special use** and describe it as "Keeps the user-enabled floating
  notepad bubble on screen." Include a short video of the bubble in use.
- **Data safety**: the Play build contains the Google Mobile Ads SDK, so declare the data it collects (see [PLAY_STORE.md](PLAY_STORE.md)). Notes are not collected.
- **Privacy policy**: host [`PRIVACY.md`](PRIVACY.md), for example with GitHub Pages, and link it.
- **Store listing text** is in [`fastlane/metadata`](fastlane/metadata/android/en-US). F-Droid
  reads the same folder.

## Revenue: optional supporter tips

FloatNote stays 100% free and ad-free. Revenue comes from **optional one-time tips**:

- **Play build** (`play` flavour): a Support screen (♥ in the toolbar) sells three one-time
  in-app products through **Google Play Billing**: `supporter_coffee`,
  `supporter_lunch` and `supporter_hero`. Any tip unlocks extra bubble colours as a thank-you,
  and it restores automatically on reinstall. This is the Play-approved way to take money in
  an app. Purchases are acknowledged, and refunds revoke the perk.
- **FOSS build** (`foss` flavour, for F-Droid and GitHub): no Google code, every colour is free,
  and a Donate button links to GitHub Sponsors. Change `DONATE_URL` in `app/build.gradle.kts`
  to your Ko-fi/Liberapay page if you prefer.

The FOSS build has no ad SDK and its INTERNET permission is stripped from the manifest. The Play
build shows one banner ad on the notes list (never in the bubble or the editor) and removes it
for supporters; see `app/src/play/.../ads/Ads.kt`. Create the products in Play Console as described in
[PLAY_STORE.md](PLAY_STORE.md).

## Project layout

```
app/src/main/java/io/github/sachin102492/floatnote/
├── data/          Note model + atomic JSON NoteRepository
├── overlay/       FloatingService, OverlayController (bubble physics, panel), QS tile
├── support/       Perks (bubble colours) + SupportBackend (Play Billing / donations per flavour)
├── ui/            MainActivity (notes list, bubble switch), EditorActivity, SupportActivity
└── util/          AutoSaver (debounced autosave), insets helper
```

Licensed under MIT. Contributions welcome.
