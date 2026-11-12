# WishWell

An offline-first Android wishlist and price tracker. Save the things you want, watch their
prices drop, and get notified when they hit your target or a new all-time low.

- **Package:** `com.wishwell.app`
- **Version:** 2.0 (versionCode 2)
- **Platform:** Android 8.0+ (minSdk 26, targetSdk 34)

## Features

- **Add items** by pasting a product URL (metadata and price are scraped with OkHttp + Jsoup),
  via the system share sheet, or manually.
- **Price history** per item with a chart, current/list price, and target price.
- **Price alerts** — notifications when an item reaches your target price or hits a new
  all-time low, plus a "check all prices now" action.
- **Scheduled checks** — periodic background price refresh via WorkManager (interval set in
  Settings); a one-shot quick check replaces any queued check instead of stacking work.
- **Collections and tags** — many-to-many categories with colors and emoji, drag-to-reorder.
- **Purchased / archived** states so bought or dormant items stay out of the active list.
- **Store comparison** — "compare elsewhere" search links across ~35 stores (Amazon, eBay,
  Walmart, Daraz, Flipkart, IKEA, Nepal-region shops, …) with regional defaults per currency.
  No API keys are required; comparison links are built locally.
- **Theming** — light / dark / system, custom accent colors, and a glass-style navbar.
- **Backup and restore** — export the wishlist to a JSON file through the Storage Access
  Framework, with optional automatic backups (6h / 12h / daily / weekly / monthly).

## Home-screen widget

The single home-screen widget is **Wishlisted items**, built with Jetpack Glance
(`androidx.glance:glance-appwidget` + `glance-material3`).

| | |
|---|---|
| Provider | `widget.WishlistedItemsWidgetReceiver` |
| Layout | `ui/widget/WidgetUi.kt`, `WishlistedItemsWidget.kt` |
| Default size | 3×2 cells (min 180×110dp, resizable both axes) |
| Refresh | `updatePeriodMillis` 30 min, plus explicit syncs after price checks and backups |

Behaviour:

- **Configuration screen** — `widget.WidgetConfigActivity` is registered as the receiver's
  `android:configure` activity, so first placement opens a settings screen (with a live
  preview) and long-press → Edit reopens it. Options: text size, item thumbnails on/off,
  background opacity, border style (none / accent / custom) with color and width, and accent
  color. Options are stored in DataStore through `UserPreferences`.
- **Loading states** — the widget shows skeleton placeholder blocks and a sync spinner
  (`widget_skeleton_body.xml`, `widget_sync_spinner.xml`) instead of an empty card while data
  loads, and falls back to an "initial" layout before the first refresh.
- **Sync button** — broadcasts `com.wishwell.app.action.SYNC_WIDGET`, handled by
  `WidgetSyncReceiver`, which triggers a price check and repaints the widget.
- Updates are pushed from the app via `WishwellWidgetUpdater`; WorkManager workers
  (`PriceCheckWorker`, `BackupWorker`) repaint the widget after they finish.

Note: the widget needs a few seconds after being placed to populate; it is not a
`ListView`/`RemoteViewsService` collection, it is redrawn from the current wishlist snapshot.

## Tech stack

Kotlin · Jetpack Compose + Material 3 · Navigation Compose · Room (KSP) · DataStore
Preferences · WorkManager · Glance AppWidget · Coil · OkHttp · Jsoup · Coroutines.
AGP 8.5.2, Kotlin 1.9.24, Compose compiler 1.5.14.

## Project layout

```
app/src/main/java/com/wishwell/app/
├── data/          Room entities/DAOs, repository, DataStore preferences, scraping, store registry
├── di/            AppContainer — manual dependency container
├── ui/            Compose screens (wishlist, tracking, collections, add/edit/detail, settings),
│                  navigation, theme, shared components
├── widget/        Glance widget, configuration activity, sync receiver, widget data/updater
├── work/          PriceCheckWorker and BackupWorker (plus WorkScheduler)
├── notify/        Notification channel and price-alert notifications
└── util/          Formatters, palette helpers
```

## Building

Requirements:

- JDK 17
- Android SDK with platform 34 and build-tools 34.0.0
- Gradle 8.7+ (`settings.gradle.kts` has no committed wrapper — see below)

Create `local.properties` in the project root with your SDK path:

```properties
sdk.dir=/path/to/Android/sdk
```

Then:

```bash
gradle :app:assembleDebug        # debug APK
gradle :app:assembleRelease      # signed release APK
gradle :app:signingReport        # verify which key signed the build
```

Outputs land in `app/build/outputs/apk/{debug,release}/`.

**No Gradle wrapper is committed.** Android Studio can still import the project and use its
own Gradle, but for command-line builds use an installed Gradle 8.7+, or generate a wrapper
once with `gradle wrapper --gradle-version 8.7`. (This checkout's toolchain lives under
`.toolchain/`, so `./.toolchain/gradle-8.7/bin/gradle :app:assembleRelease` also works there.)

## Release signing

Release builds are signed when signing credentials are present, and left unsigned otherwise
(so fresh clones can still run `assembleDebug`).

1. Generate a keystore (once, never commit it):

   ```bash
   keytool -genkeypair -v -keystore wishwell-release.jks -alias wishwell \
     -keyalg RSA -keysize 2048 -validity 10950 -storetype PKCS12 \
     -dname "CN=WishWell, OU=Mobile, O=WishWell, L=Kathmandu, C=NP"
   ```

2. Put the credentials in `local.properties` (already git-ignored):

   ```properties
   RELEASE_STORE_FILE=wishwell-release.jks      # relative to the project root
   RELEASE_STORE_PASSWORD=…
   RELEASE_KEY_ALIAS=wishwell
   RELEASE_KEY_PASSWORD=…
   ```

   The same four names are read from environment variables when the properties file does not
   define them, which is the path for CI.

3. Build and verify:

   ```bash
   gradle :app:assembleRelease
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```

`*.jks` / `*.keystore` are git-ignored. **Back up the keystore and its password** — losing
them means you can never ship an update that installs over an existing installation.
Current release certificate SHA-256:
`53:2D:88:CC:24:30:09:4B:A6:59:CF:33:31:F8:BA:1D:15:2F:B4:04:66:9C:68:86:D2:A4:55:AC:D2:E3:2D:C5`

## Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Build the signed APK (above) and smoke-test it on a device.
3. Commit, tag, push, and publish:

   ```bash
   git tag -a v2.0.0 -m "WishWell 2.0"
   git push origin main --follow-tags
   gh release create v2.0.0 --title "WishWell 2.0" --notes-file notes.md
   gh release upload v2.0.0 app/build/outputs/apk/release/app-release.apk
   ```

Versions tagged so far: `v1.0.0`, `v2.0.0`.

## Data and privacy

Everything is stored on-device (Room + DataStore). The app fetches product pages directly from
the stores you add — there is no backend and no account. Scraping is best-effort and will break
when a store changes its markup; price/discount detection currently special-cases Amazon and
Daraz.
