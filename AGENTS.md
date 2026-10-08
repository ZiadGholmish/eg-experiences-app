# Agent instructions — Bahr mobile app

Kotlin Multiplatform + Compose Multiplatform. Android and iOS from one codebase.
Client of the Bahr backend at `../bahr-be` — booking day trips in Egypt.
Currency EGP, locales `ar` (default, RTL) and `en`. Package root `eg.bahr`.

Read before changing anything:
- `../docs/PLAN.md` — the approved plan (§5b modules, §6 mobile architecture).
  Work on exactly one step at a time.
- `../bahr-be/AGENTS.md` — the backend's rules.
- `../bahr-be/docs/api/openapi.yaml` — the API contract and its source of truth.
  `../docs/api/openapi.yaml` is a symlink to it; never edit a copy.
- `../.claude/skills/bahr-modularization/SKILL.md` — module boundaries: the
  allowed dependency graph and the layout inside a feature.

The Java backend in `../be/` is a superseded, read-only reference.

## Build and test

- `./gradlew build` — everything, both platforms.
- `./gradlew :composeApp:assembleLocalDebug` — Android against a locally running
  backend. Set `LOCAL_BASE_URL` in `local.properties` first (see
  `local.properties.example`); it defaults to `http://10.0.2.2:8084/api/v1/`,
  which is the host machine as seen from the emulator.
- `./gradlew spotlessApply` before every commit. `./gradlew installGitHooks`
  once after cloning installs a pre-commit hook that enforces it.
- `./gradlew allTests` — common tests on both targets.
- `./gradlew verifyRoborazziDebug` — screenshot diffs against the committed
  goldens; `./gradlew recordRoborazziDebug` re-records them after an intended UI
  change. `./gradlew koverHtmlReportMobile` — coverage. See README → Testing.

Running the backend locally (in `../bahr-be`, see its `AGENTS.md`):

```bash
make infra-up    # Postgres 17 on localhost:5433
SECURITY_JWT_SECRET='local-dev-signing-key-at-least-32-bytes!!' ./gradlew :api:bootRun   # api on :8084
```

## The rules that matter most

1. **One API contract, and this app does not get its own endpoints.** The
   backend serves `/api/v1/**` to this app *and* to the web client, and no route
   is ever added for one client only. If a screen needs a shape the web does not
   have, ask for a field or a query parameter on the existing endpoint. See
   `../bahr-be/AGENTS.md` rule 1.

2. **Arabic is the default locale, and RTL is the default layout direction.**
   Never `left`/`right` — always `start`/`end`. Every string goes through
   `core:localization`; a literal in a composable is a bug. `Accept-Language` is
   set from the stored language on every request, and it is what decides which
   translation of a trip comes back.

3. **Seat counts from the server are display hints, never decisions.**
   `seatsRemaining` is stale the moment it is read. The authoritative check is
   the conditional update the backend runs when placing a hold, so a hold can
   fail against a departure that looked bookable. Handle `NO_SEATS_AVAILABLE`.

4. **The hold countdown runs against the server's `holdExpiresAt`.** Never a
   client-side duration: a backgrounded app, a slow network and a wrong device
   clock all make one lie. When it hits zero, re-read — do not assume.

5. **Branch on the error `code`, never the message.** `ApiErrorCodes` mirrors the
   `ApiError.code` enum in `openapi.yaml`. An unmapped code shows the generic
   string, never a raw server message.

6. **Money is `{ amount, currency }`, never a bare number, and the client never
   does money arithmetic.** Totals come from the server, which computes them
   against a ledger. `BahrFormat.money` only formats.

7. **No design literals in feature modules.** Colours, spacing, radii and type
   come from `core:designsystem`. See `docs/design-language.md`.

## Conventions

- Modules: `core:*` is shared infrastructure, `feature:*` is one area of the
  contract. **A core module never depends on a feature module, and a feature
  never depends on another feature** — cross-feature navigation goes through
  lambdas wired in `:composeApp`, which holds no screens. The full graph is in
  the `bahr-modularization` skill; a new edge is a plan change.
- **Known deviations (fixed in M0-M5).** Today's code breaks the graph in two
  places, so don't copy either one:
  - `feature:booking → feature:trips` (`feature/booking/build.gradle.kts:48`).
    No source uses it; M0-M5 deletes the line.
  - `ApiErrorCodes` lives in `core:common`. The graph puts it in `core:network`.
- Inside a feature: `model/` (wire DTOs), `data/` (api service + repository),
  `presentation/` (screen + view model, `components/` for parts), `navigation/`
  (type-safe routes), `di/` (one Koin module).
- DTOs are `@Serializable` data classes, field-for-field with the schemas in
  `openapi.yaml`. New fields are added nullable with a default — the parser
  ignores unknown keys, so an old app keeps working, and a non-nullable addition
  breaks it instead.
- Repositories return `AppResult<T>`. `callApi` is the only place that catches
  transport failures; `CancellationException` is always rethrown.
- View models expose one `StateFlow<…UiState>` and no other public state.
- ktlint via Spotless, 4-space indent, trailing commas. Comments explain *why*.
- Do not commit; leave changes staged for the human to review.
