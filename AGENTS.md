# Agent instructions — Egypt Experiences mobile app

Kotlin Multiplatform + Compose Multiplatform. Android and iOS from one codebase.
Client of the Burlus backend at `../be` — booking platform for attractions and
day trips in Egypt. Currency EGP, locales `ar` (default, RTL) and `en`.

The backend's plan is `../be/docs/plan/implementation-plan.md` and its rules are
`../be/AGENTS.md`. Read both before changing anything that touches the contract.

## Build and test

- `./gradlew build` — everything, both platforms.
- `./gradlew :composeApp:assembleLocalDebug` — Android against a locally running
  backend. Set `LOCAL_BASE_URL` in `local.properties` first (see
  `local.properties.example`); it defaults to `http://10.0.2.2:8084/api/v1/`,
  which is the host machine as seen from the emulator.
- `./gradlew spotlessApply` before every commit. `./gradlew installGitHooks`
  once after cloning installs a pre-commit hook that enforces it.
- `./gradlew allTests` — common tests on both targets.

## The rules that matter most

1. **One API contract, and this app does not get its own endpoints.** The
   backend serves `/api/v1/**` to this app *and* to the web client, and no route
   is ever added for one client only. If a screen needs a shape the web does not
   have, ask for a field or a query parameter on the existing endpoint. See
   `../be/AGENTS.md` rule 1.

2. **Arabic is the default locale, and RTL is the default layout direction.**
   Never `left`/`right` — always `start`/`end`. Every string goes through
   `core:localization`; a literal in a composable is a bug. `Accept-Language` is
   set from the stored language on every request, and it is what decides which
   translation of a trip comes back.

3. **Seat counts from the server are display hints, never decisions.**
   `seatsRemaining` is stale the moment it is read. The authoritative check is
   the conditional update the backend runs when placing a hold, so a hold can
   fail against a departure that looked bookable. Handle `SEATS_UNAVAILABLE`.

4. **The hold countdown runs against the server's `holdExpiresAt`.** Never a
   client-side duration: a backgrounded app, a slow network and a wrong device
   clock all make one lie. When it hits zero, re-read — do not assume.

5. **Branch on the error `code`, never the message.** `ApiErrorCodes` mirrors the
   backend's stable codes. An unmapped code shows the generic string, never a raw
   server message.

6. **Money is `{ amount, currency }`, never a bare number, and the client never
   does money arithmetic.** Totals come from the server, which computes them
   against a ledger. `MoneyFormatter` only formats.

7. **No design literals in feature modules.** Colours, spacing, radii and type
   come from `core:designsystem`. See `docs/design-language.md`.

## Conventions

- Modules: `core:*` is shared infrastructure, `feature:*` is one area of the
  contract. **A core module never depends on a feature module.** Feature-to-
  feature dependencies are allowed but should be rare and one-directional.
- Inside a feature: `model/` (wire DTOs), `data/` (api service + repository),
  `presentation/` (screen + view model, `components/` for parts), `navigation/`
  (type-safe routes), `di/` (one Koin module).
- DTOs are `@Serializable` data classes, field-for-field with the backend's
  records. New fields are added nullable with a default — the parser ignores
  unknown keys, so an old app keeps working, and a non-nullable addition breaks
  it instead.
- Repositories return `AppResult<T>`. `callApi` is the only place that catches
  transport failures; `CancellationException` is always rethrown.
- View models expose one `StateFlow<…UiState>` and no other public state.
- ktlint via Spotless, 4-space indent, trailing commas. Comments explain *why*.
- Do not commit; leave changes staged for the human to review.
