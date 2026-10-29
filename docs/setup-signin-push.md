# Sign-in and push: what to hand over, and where it goes

Written in M3-M19a (2026-10-25). The code is ready for real Google and Apple sign-in and for push. What is still
missing is the ids and files that only the account owner can create. This page lists each one: where it comes
from, which file and key it goes in, and how the app behaves until it is there. The backend's view of the same
values is in `../bahr-be/docs/keys.md`. The device test that follows is M3-M19.

**Every file named below is gitignored.** The committed `.example` next to each one holds placeholders only.
Never commit a real id, key or Firebase file.

## Environments at a glance

| Environment | Android | iOS | Bundle id / package | Backend |
|---|---|---|---|---|
| local | flavor `local` (`assembleLocalDebug`) | configuration `Debug`, scheme `iosApp` (`Local.xcconfig`) | `eg.bahr.app.local` | your laptop's api |
| **dev** | flavor `dev` (`assembleDevDebug`) | configuration **`Dev`**, scheme **`iosApp Dev`** (`Dev.xcconfig`) | `eg.bahr.app.dev` | `https://d1epegbe1ipzwy.cloudfront.net/api/v1/` |
| prod | flavor `prod` | configuration `Release`, scheme `iosApp` (`Prod.xcconfig`) | `eg.bahr.app` | not deployed yet |

The dev backend already accepts Apple tokens for `eg.bahr.app.dev` (`AUTH_APPLE_CLIENT_IDS = eg.bahr.app.dev`). It
has no `AUTH_GOOGLE_CLIENT_IDS` yet.

## What happens until a value is there

| Missing | Android | iOS |
|---|---|---|
| Google client ids | `local`: signs in with fake tokens. `dev`/`prod`: no provider, and the sheet says sign-in is not set up | Google button hidden, Apple only. `Debug` (Local): fake tokens |
| Apple capability on the App ID | n/a (Android has no Apple sign-in) | the build still compiles, but a device build can't be signed with the entitlement (see step 4) |
| Firebase file | builds and runs: no `FirebaseApp`, push off, the install registers without a token, no push pre-prompt | builds and runs: Firebase is not configured (`PushSetup`), push off, the same; the "Copy Firebase Config" phase logs `push is off in this build` |

Each one turns on by dropping the value or file in and rebuilding. No code or build-file change is needed.

## The checklist (dev first)

### 1. Google Cloud, dev project: OAuth consent screen (user, console)
`keys.md` → Manual steps 2. Set it to External, with scopes `openid`, `email` and `profile` only, and publish it
"In production". Nothing goes into the app.

### 2. Google Cloud, dev project: three OAuth clients (user, console)
`keys.md` → Manual steps 3. **Hand over:** the web client id and the iOS client id.

| Client | Created with | Value | Where it goes |
|---|---|---|---|
| Web application | no redirect URIs | `…apps.googleusercontent.com` | `mobile-app/local.properties`: `GOOGLE_SERVER_CLIENT_ID_DEV=<web id>` · backend: 1st entry of `AUTH_GOOGLE_CLIENT_IDS` |
| Android | package `eg.bahr.app.dev` + your debug SHA-1 (command below) | none: only Google checks it | nothing in the app |
| iOS | bundle id `eg.bahr.app.dev` | the client id, and the "iOS URL scheme" the console shows (`com.googleusercontent.apps.…`) | `mobile-app/iosApp/Configuration/SignIn.dev.private.xcconfig` (copy `SignIn.private.xcconfig.example`): `GOOGLE_IOS_CLIENT_ID` and `GOOGLE_IOS_REVERSED_CLIENT_ID` · backend: 2nd entry of `AUTH_GOOGLE_CLIENT_IDS` |

```bash
# The debug keystore's SHA-1 for the Android OAuth client. keytool ships with the JDK; -list -v prints the
# certificate's fingerprints; androiddebugkey / android are the standard debug alias and password.
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

For local builds, put the `eg.bahr.app.local` clients in the dev project too. Their ids go in
`GOOGLE_SERVER_CLIENT_ID_LOCAL` and `SignIn.local.private.xcconfig`. Setting either one turns off fake sign-in on
that platform's local build, so the local api must then run with `AUTH_VERIFICATION=jwks`.

### 3. Backend: accept the Google tokens (user only)
Add `AUTH_GOOGLE_CLIENT_IDS = "<web id>,<iOS id>"` to `parameters` in
`bahr-be/terraform/environments/dev/aws/terraform.tfvars`. Then run `terraform apply` and redeploy the api. Until
then, the dev api rejects every Google token.

### 4. Apple portal: App ID `eg.bahr.app.dev` (user, team 474WXSCVBC)
Under Certificates, IDs & Profiles → Identifiers → `eg.bahr.app.dev`, enable **Sign in with Apple**, **Push
Notifications** and **Associated Domains**. The Dev configuration signs with all three entitlements
(`iosApp/iosApp/iosApp.entitlements`). If the App ID lacks any of them, automatic signing fails on a device build.
You can also let Xcode turn them on: open the project, pick the `iosApp Dev` scheme, and see Signing & Capabilities,
or pass `-allowProvisioningUpdates` to `xcodebuild`. Nothing goes into the app. The Prod configuration uses the same
entitlements file, so `eg.bahr.app` needs the same three capabilities before a Prod device build.

The Sign in with Apple key and the APNs auth key (`.p8` files) are for the backend and Firebase, not for the app:
`keys.md` → Manual steps 4–5 and "Putting a secret value in".

### 5. Firebase files (Terraform outputs, from M3-M5)
**Hand over:** the two files, or let Claude run the commands below after `terraform apply` on the dev Google root.

```bash
# -chdir: run in the dev Google root without cd; -raw: the bare file contents, since these outputs are `sensitive`.
terraform -chdir=bahr-be/terraform/environments/dev/google output -raw google_services_json \
  > mobile-app/androidApp/src/dev/google-services.json
mkdir -p mobile-app/iosApp/Firebase/dev
terraform -chdir=bahr-be/terraform/environments/dev/google output -raw google_service_info_plist \
  > mobile-app/iosApp/Firebase/dev/GoogleService-Info.plist
```

| File | Goes to | Why there |
|---|---|---|
| `google-services.json` | `androidApp/src/<flavor>/google-services.json` (`local`, `dev`, `prod`) | each Firebase Android app is for one package, and the Google Services plugin refuses a file with no client for the variant's application id |
| `GoogleService-Info.plist` | `iosApp/Firebase/<env>/GoogleService-Info.plist` (`local`, `dev`, `prod`, from `APP_ENVIRONMENT` in the xcconfig) | a "Copy Firebase Config" build phase copies it into the app, and only when it is there. A normal Resources entry would fail the build without the file |

The placeholders `androidApp/google-services.json.example` and `iosApp/Firebase/GoogleService-Info.plist.example`
show each file's shape. Since M3-M5 the app uses them: Android's `firebase-messaging` (`:composeApp`'s
`AndroidPush.kt`, `:androidApp`'s `PushService`) and iOS's Firebase Messaging (Swift Package `firebase-ios-sdk`,
`iosApp/iosApp/Push.swift`). The install registers on every start (`POST /devices`, token only once notifications are
allowed); the push pre-prompt follows a waiting-list join; a tap opens the trip page on the push's date. Android's
default notification channel is `bahr_default` (`androidApp/src/main/res/values/strings.xml`, named in the manifest).

Uploading the APNs auth key to Firebase (`keys.md` step 5) is what makes iOS push deliver. It needs nothing in the
app.

## Check it (after dropping the values in)

```bash
# Android dev: BuildConfig has the web client id, and the plugin generated Firebase resources from the json.
./gradlew :androidApp:assembleDevDebug
grep GOOGLE_SERVER_CLIENT_ID androidApp/build/generated/source/buildConfig/dev/debug/eg/bahr/BuildConfig.java
grep google_app_id androidApp/build/generated/res/processDevDebugGoogleServices/values/values.xml

# iOS Dev on a simulator: -scheme/-configuration pick Dev; -derivedDataPath keeps the output at a known path.
xcodebuild -project iosApp/iosApp.xcodeproj -scheme "iosApp Dev" -configuration Dev -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -derivedDataPath build/dd build
plutil -p build/dd/Build/Products/Dev-iphonesimulator/Bahr.app/Info.plist | grep -E 'GIDClientID|CFBundleURLSchemes' -A1
ls build/dd/Build/Products/Dev-iphonesimulator/Bahr.app/GoogleService-Info.plist

# On the phones (M3-M19). -allowProvisioningUpdates lets Xcode register the device and fetch or update the
# profile for eg.bahr.app.dev on team 474WXSCVBC.
./gradlew :androidApp:installDevDebug
xcodebuild -project iosApp/iosApp.xcodeproj -scheme "iosApp Dev" -configuration Dev \
  -destination 'platform=iOS,id=<device udid from `xcrun devicectl list devices`>' -allowProvisioningUpdates build
```

## CI (MD-M1)
GitHub environment `dev` gets these secrets. The workflow turns them into the files above before building:

| Secret | Becomes |
|---|---|
| `GOOGLE_SERVER_CLIENT_ID_DEV` | an environment variable; the Android build reads it like `local.properties` |
| `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_IOS_REVERSED_CLIENT_ID` | `iosApp/Configuration/SignIn.dev.private.xcconfig` |
| `GOOGLE_SERVICES_JSON` | `androidApp/src/dev/google-services.json` |
| `GOOGLE_SERVICE_INFO_PLIST` | `iosApp/Firebase/dev/GoogleService-Info.plist` |
