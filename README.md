# Egypt Experiences — mobile app

Kotlin Multiplatform app for booking attractions and day trips in Egypt.
Android and iOS share one Compose Multiplatform UI.

Client of the Burlus backend (`../be`). One `/api/v1/**` contract serves this app
and the web client — no route exists for one client only.

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
├── composeApp/            # The app: entry points, DI wiring, nav graph
├── core/
│   ├── common/            # AppResult, AppError, Money, AppLanguage
│   ├── network/           # ApiEnvelope, callApi, HttpClient factory
│   ├── designsystem/      # Colour, type, spacing, shape, shared components
│   ├── localization/      # ar/en strings, RTL provider, error messages
│   └── datastore/         # Persisted settings
├── feature/
│   ├── splash/
│   ├── trips/             # GET /trips, /trips/{slug}, /trips/{slug}/departures
│   └── booking/           # POST /bookings, GET+cancel /bookings/{ref}
└── docs/design-language.md
```

A `core` module never depends on a `feature` module.

## Getting started

Prerequisites: JDK 17+, Android Studio (Ladybug+), Xcode for iOS.

```bash
cp local.properties.example local.properties   # then set sdk.dir
./gradlew installGitHooks                      # pre-commit runs spotlessCheck
./gradlew build
```

Run Android against a local backend — start the api deployable first
(`cd ../be && make infra-up` then run `api`, which listens on **8084**):

```bash
./gradlew :composeApp:installLocalDebug
```

`10.0.2.2` is the host machine as seen from the Android emulator. On a physical
device, set `LOCAL_BASE_URL` in `local.properties` to your machine's LAN address.

### Flavors

| Flavor | Base URL |
|---|---|
| `local` | `LOCAL_BASE_URL` from `local.properties`, default `http://10.0.2.2:8084/api/v1/` |
| `dev` | placeholder — no dev host deployed yet |
| `prod` | placeholder — no prod host deployed yet |

### iOS

The shared framework builds today (`./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`).
The Xcode app shell is not checked in yet — add it with Android Studio's KMP
plugin, then call:

```swift
MainViewControllerKt.MainViewController(baseUrl: "…", isDebug: true)
```

## Known gaps

- **No payment step.** `BookingScreen` goes from a seat *hold* straight to the
  confirmation screen, because the backend has no payment endpoint yet. Until
  Paymob lands, a "confirmed" booking is an unpaid hold.
- **No `iosApp/` Xcode shell.** The shared framework builds and the Swift entry
  point exists; the app target is not checked in (see iOS above).
- **Launch is unverified.** `./gradlew build` compiles both platforms and runs
  the tests; nothing has yet resolved the Koin graph or opened the DataStore on
  a device. First run is `:composeApp:installLocalDebug` against a live backend.
- **Design tokens are provisional** until the claude.ai/design system is synced
  — see `docs/design-language.md`.

## Contributing

1. Branch: `git checkout -b feature/thing`.
2. `./gradlew spotlessApply && ./gradlew build`.
3. Read `AGENTS.md` — especially the RTL, seat-inventory and hold-timer rules.
