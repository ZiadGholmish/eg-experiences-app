# Bahr — mobile app

Kotlin Multiplatform app for booking day trips in Egypt (package root `eg.bahr`).
Android and iOS share one Compose Multiplatform UI.

Client of the Bahr backend (`../bahr-be`). One `/api/v1/**` contract serves this app
and the web client — no route exists for one client only. The contract is
`../bahr-be/docs/api/openapi.yaml` (`../docs/api/openapi.yaml` is a symlink to it);
the plan is `../docs/PLAN.md`.

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.3 |
| UI | Compose Multiplatform 1.10 |
| Targets | Android (minSdk 24), iOS (arm64 + simulator arm64) |
| DI | Koin |
| Networking | Ktor 3 (OkHttp on Android, Darwin on iOS) |
| Navigation | AndroidX Navigation Compose, type-safe routes |
| Storage | DataStore Preferences |
| Images | Coil 3 |
| Format | Spotless + ktlint |

## Project structure

```
mobile-app/
├── build-logic/           # Convention plugins: bahr.kmp.library / .feature / .app, …
├── composeApp/            # The app: entry points, DI wiring, nav graph (no screens)
├── core/
│   ├── common/            # AppResult, AppError, AppLanguage
│   ├── network/           # ApiEnvelope, MoneyDto, callApi, HttpClient factory
│   ├── designsystem/      # BahrTheme, tokens, BahrFormat, shared components
│   ├── localization/      # ar/en strings, RTL provider, error messages
│   ├── datastore/         # Persisted settings
│   └── testing/           # Test-only: runViewModelTest, screenshot helper
├── feature/
│   ├── splash/
│   ├── trips/             # GET /trips, /trips/{slug}, /trips/{slug}/departures
│   └── booking/           # POST /bookings, GET+cancel /bookings/{ref}
├── architecture-tests/    # JVM: ModuleGraphTest (Konsist), the module rules
└── docs/design-language.md
```

A `core` module never depends on a `feature` module, and a feature never depends
on another feature; `./gradlew :architecture-tests:test` enforces the whole graph
(see `../.claude/skills/bahr-modularization/SKILL.md`).

## Getting started

Prerequisites: JDK 17+, Android Studio (Ladybug+), Xcode for iOS.

```bash
cp local.properties.example local.properties   # then set sdk.dir
./gradlew installGitHooks                      # pre-commit runs spotlessCheck
./gradlew build
```

Run Android against a local backend — start the api deployable first, which
listens on **8084** (see `../bahr-be/AGENTS.md`):

```bash
cd ../bahr-be && make infra-up
SECURITY_JWT_SECRET='local-dev-signing-key-at-least-32-bytes!!' ./gradlew :api:bootRun
```

Then:

```bash
./gradlew :composeApp:installLocalDebug
```

`10.0.2.2` is the host machine as seen from the Android emulator. On a physical
device, set `LOCAL_BASE_URL` in `local.properties` to your machine's LAN address.

### Flavors

| Flavor | Base URL | Share-link host (App Links) |
|---|---|---|
| `local` | `LOCAL_BASE_URL` from `local.properties`, default `http://10.0.2.2:8084/api/v1/` | `LOCAL_APP_LINK_HOST`, default `localhost`; http and https |
| `dev` | placeholder — no dev host deployed yet | placeholder `dev.example.invalid` |
| `prod` | placeholder — no prod host deployed yet | `bahr.eg` |

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode, pick a simulator and run. The `Compile
Kotlin Framework` build phase calls
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` before Swift
compiles, so there is no separate Gradle step. From the command line:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build
```

`Configuration/*.xcconfig` are the iOS counterpart of the Android product
flavors: `Debug` builds against `Local.xcconfig`, `Release` against
`Prod.xcconfig`. `BASE_URL` reaches Swift through `Info.plist`, and
`ContentView` hands it to the shared entry point:

```swift
MainViewControllerKt.MainViewController(baseUrl: "…", isDebug: true, appLinkHost: "…")
```

> The simulator reaches a local backend at **`localhost:8084`**, not
> `10.0.2.2` — that alias only means anything to the Android emulator.

### Running on a physical iPhone

A phone cannot reach the Mac's `localhost`, so point it at the Mac's LAN
address — the same problem `LOCAL_BASE_URL` solves for Android:

```bash
cp iosApp/Configuration/Local.private.xcconfig.example \
   iosApp/Configuration/Local.private.xcconfig
ipconfig getifaddr en0     # put this address in the file you just copied
```

`Local.xcconfig` pulls it in with an optional `#include?`, so the simulator
still builds when the file is absent. It is gitignored, and the address is
DHCP — it changes when the router reassigns it.

Signing is automatic against `DEVELOPMENT_TEAM = 474WXSCVBC`. Simulator builds
are ad-hoc signed (`CODE_SIGNING_ALLOWED[sdk=iphonesimulator*] = NO`), so
anyone can run one without that team; a device build needs their own.

## Deep links

`https://<host>/t/{slug}` opens the trip page, with the trip list under it so back goes to the list.
Android claims it with an App Links intent filter (`autoVerify="true"`), iOS with the Associated
Domains entitlement (`applinks:<host>`). The backend proves the app owns the host by serving
`/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association` (bahr-be M1-B5).

- **One parser for both platforms**: `composeApp/.../deeplink/DeepLink.kt`. It accepts only the
  flavor's host (any port), https (plus http in `local`), `/t/{slug}` with an optional trailing slash,
  and a slug that passes the backend's `chk_trip_slug` rule (`^[a-z0-9]+(-[a-z0-9]+)*$`, at most 160
  characters). It reads `?lang=` (`ar`/`en`) but does not switch the app's language. Anything else
  opens the trip list.
- **Host per environment**: the `appLinks(...)` call per flavor in `composeApp/build.gradle.kts`
  fills both the manifest placeholder and `BuildConfig.APP_LINK_HOST`. On iOS it is `APP_LINK_HOST`
  in `Configuration/*.xcconfig`, which fills both `Info.plist` and `iosApp/iosApp.entitlements`.
- **Cold and warm start**: `MainActivity` is `singleTask`, so a link tapped while the app runs
  arrives in `onNewIntent`. The link waits in `DeepLinkInbox` until splash has handed over to the
  list, and `AppNavHost` opens it then.

### Trying it on the emulator

With the api running locally (it serves the share page itself at `http://localhost:8084/t/{slug}`):

```bash
./gradlew :composeApp:installLocalDebug
# -W waits for the launch to finish; -a/-d are the VIEW action and the link;
# the trailing package name sends the intent straight to this app, so it works
# before (and without) App Links verification.
adb shell am start -W -a android.intent.action.VIEW \
  -d "https://localhost/t/burullus-dawn" eg.bahr.app.local
```

The trip page opens over the list; back goes to the list. Run it again while the app is open and it
navigates there (`am start` reports the intent was delivered to the running instance). A bad slug
(`/t/Bad_Slug`) opens the list. `/b/…` is not claimed by the filter at all, so `am start` fails to
resolve it.

### Verifying App Links (`autoVerify`) locally

Without verification Android shows a chooser or opens the browser for a link that does not name the
package. Verification needs three things to match:

1. **The fingerprint.** Local builds are signed with the debug keystore. Get its SHA-256:

   ```bash
   ./gradlew :composeApp:signingReport   # the `localDebug` variant's "SHA-256" line
   ```

   and start the api with it (the package name already defaults to `eg.bahr.app.local` in
   `api-local.yml`):

   ```bash
   SITE_APP_LINKS_ANDROID_SHA256_CERT_FINGERPRINTS='AB:CD:…' \
   SECURITY_JWT_SECRET='local-dev-signing-key-at-least-32-bytes!!' ./gradlew :api:bootRun
   ```

   Check it: `curl -s localhost:8084/.well-known/assetlinks.json`.
2. **A public https host on port 443.** Android fetches `https://<host>/.well-known/assetlinks.json`
   with no redirects, so `localhost`, `10.0.2.2` and plain http never verify. Put the api behind an
   https tunnel (e.g. `ngrok http 8084`), start it with `SITE_PUBLIC_BASE_URL=https://<tunnel host>`,
   and set `LOCAL_APP_LINK_HOST=<tunnel host>` in `local.properties`, then reinstall.
3. **Ask Android to verify**, then read the result:

   ```bash
   adb shell pm verify-app-links --re-verify eg.bahr.app.local
   adb shell pm get-app-links eg.bahr.app.local      # want: <tunnel host>: verified
   ```

To skip verification while developing, approve the host by hand instead:
`adb shell pm set-app-links-user-selection --user 0 --package eg.bahr.app.local true localhost`.

### Universal Links (iOS)

Universal Links need a public https host serving the AASA with the real team id
(`SITE_APP_LINKS_IOS_TEAM_ID`; `api-local.yml` ships `TEAMID0000`) and bundle id, and a signing
team whose profile has the Associated Domains capability. So only `Dev.xcconfig` and
`Prod.xcconfig` set `CODE_SIGN_ENTITLEMENTS`; the local Debug build has no entitlement, which keeps
it signable by a personal team. `ContentView` hands every URL from `onOpenURL` /
`onContinueUserActivity` to `MainViewControllerKt.onDeepLink(url:)`.

## Testing

| What | Command |
|---|---|
| All unit tests, Android JVM + iOS simulator | `./gradlew allTests` |
| Screenshot check against the committed goldens | `./gradlew verifyRoborazziDebug` |
| Re-record goldens after an intended UI change | `./gradlew recordRoborazziDebug` (then review the PNG diff before staging) |
| Coverage, `core:*` + view models | `./gradlew koverHtmlReportMobile` → `build/reports/kover/htmlMobile/index.html` |
| Coverage gate | `./gradlew koverVerifyMobile` (also part of `check`) |
| String-resource lint (`\'`, bare `%s`/`%d`) | `./gradlew checkStringResources` (every module; also part of `check`) |
| Module graph, no screens in `:composeApp`, no design literals in features, feature layout + `internal` | `./gradlew :architecture-tests:test` (also part of `build`) |

- **View models:** `commonTest`, hand-written fake repositories, and
  `runViewModelTest { … }` from `core:testing`, which points `Dispatchers.Main`
  at the test scheduler. Template: `feature/trips/.../TripListViewModelTest.kt`
  with `FakeTripRepository`.
- **Screenshots:** Roborazzi on Robolectric (Android JVM, native graphics), in
  each module's `src/androidUnitTest/`; goldens are committed in
  `src/androidUnitTest/screenshots/`. The shots render through
  `ProvideAppLanguage` + `BahrTheme` like the app, on an English "device", so the
  Arabic goldens also prove the app language beats the device language and that
  the real Manrope / IBM Plex Sans Arabic faces load. Plain `testDebugUnitTest`
  (part of `build`) renders them without comparing; only `verify…` fails on a
  pixel difference.
- **Transient errors** (the screen stays usable): inject `AppErrorController`
  into the view model and call `show(error)`; the app root's `BahrErrorHost`
  shows it as a localized snackbar. A message leaves the queue only when its
  snackbar times out, so one on screen during a language switch or an Activity
  recreation is shown again (in the new language, for a fresh 4 s); process
  death drops the queue. Errors that *are* the screen's state stay in
  the `UiState`.

## Known gaps

- **No payment step.** `BookingScreen` goes from a seat *hold* straight to the
  confirmation screen, because the backend has no payment endpoint yet. Until
  Paymob lands, a "confirmed" booking is an unpaid hold.
- **No deployed hosts.** The `dev` and `prod` base URLs are `example.invalid`
  placeholders on both platforms, so only the `local` flavor talks to anything.
- **Trip photos 404.** `AssetUrlResolver` builds `/assets/<key>` URLs, but the
  api deployable serves no such route and answers 401, so every card renders an
  empty image box.
- **Neither app is signable for a store.** The Android release build uses the
  debug keystore, and the iOS target has an empty `AppIcon` asset.
- **No install referrer.** The Play Store link carries `referrer=slug%3D<slug>`, but the app does not
  read it yet, so a first launch after installing from a share link opens the list, not the trip.
- **`/b/{ref}` opens the app on iOS.** The AASA claims `/t/*` and `/b/*`; the app has no
  confirmation-from-link yet, so a `/b/` link opens the trip list instead of the web page. Android
  claims only `/t/`.
- **Design tokens are provisional** until the claude.ai/design system is synced
  — see `docs/design-language.md`.

## Contributing

1. Branch: `git checkout -b feature/thing`.
2. `./gradlew spotlessApply && ./gradlew build`.
3. Read `AGENTS.md` — especially the RTL, seat-inventory and hold-timer rules.
