# LiftBook

A single-user Android workout tracker in Kotlin. Fully offline: all data lives on-device,
there are no accounts, no sync and no backend.

**The spec is `docs/requirement.md`.** Read it before implementing anything. It is the source
of truth for behaviour, and every requirement has a stable ID (`FR-x.y`, `NFR-n`). Reference
those IDs in commit messages, TODOs and PR descriptions — e.g. `feat(workout): rest timer (FR-3.5)`.
Section 8 of the spec defines MVP vs Phase 2 scope; do not build Phase 2 items unless asked.

**The architecture is `docs/architecture.md`** — package layout, data entities, navigation graph
and ViewModel structure, with the reasoning behind each. Follow it; if a slice needs to deviate,
update that document in the same change rather than letting the code drift from it. Its §7 is the
build order.

---

## Stack decisions

| Area | Decision |
| --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 (`material3`, not `material`) |
| Min / target SDK | `minSdk = 26` (NFR-4), `targetSdk` = latest stable |
| Architecture | MVVM — ViewModel + `StateFlow`, unidirectional data flow, immutable UI state |
| Navigation | Navigation Compose, single `MainActivity`, type-safe routes |
| Persistence | **Room** (SQLite) for exercises / routines / workouts / sets |
| Settings | **Preferences DataStore** for units, theme, rest defaults, reminder toggles |
| DI | Hilt |
| Async | Coroutines + Flow; DAOs return `Flow`, never blocking calls on the main thread |
| Timer / reminders | `AlarmManager` exact alarms + `NotificationManager` |
| Charts | Hand-rolled Compose `Canvas` (Phase 2). Prefer no chart library; if one is needed, Vico |
| Build | Gradle Kotlin DSL + `gradle/libs.versions.toml` version catalog — add deps to the catalog, never inline version strings |
| Tests | JUnit4 for domain logic; Compose UI tests for the active-workout screen |

Known gaps in the scaffold to fix when first touched: `minSdk` is still `24` (must be `26`),
and `ui/theme/` still holds the Android Studio template's purple placeholder palette.

### Hard constraints

- **No `INTERNET` permission in the manifest, ever (NFR-1).** This rules out downloadable
  fonts, remote images, crash reporting and analytics SDKs. The app must be fully usable in
  airplane mode.
- Room entities stay in `data/`. Domain models are plain Kotlin data classes with no
  framework annotations; map between them at the repository boundary.
- Weights are stored in **one canonical unit (kilograms)** and converted only for display
  (FR-6.1). Never persist a converted value.
- Lists are `LazyColumn` + Paging or `Flow`-backed windows. History must stay smooth at
  1,000+ workouts (NFR-2).
- Every interactive control needs a TalkBack `contentDescription` or semantics label (NFR-5).

---

## Package structure

```
com.example.liftbook
├── LiftBookApplication.kt
├── MainActivity.kt
├── core/                  # formatters, date/time helpers, unit conversion, Result types
├── domain/
│   ├── model/             # Exercise, Routine, Workout, WorkoutSet, PersonalRecord…
│   ├── repository/        # repository interfaces
│   └── usecase/           # volume, estimated 1RM, PR detection, weekly summary
├── data/
│   ├── local/
│   │   ├── entity/        # Room @Entity
│   │   ├── dao/
│   │   ├── LiftBookDatabase.kt
│   │   ├── Converters.kt
│   │   └── seed/          # preloaded exercise library (FR-1.1)
│   ├── preferences/       # DataStore settings
│   ├── backup/            # versioned JSON export / import (FR-6.3, 6.4, NFR-6)
│   └── repository/        # repository implementations
├── notification/          # rest timer + workout reminder alarms, receivers, channels
└── ui/
    ├── theme/             # color, type, shape, spacing tokens
    ├── components/        # the shared design system — reuse before writing a new one
    ├── navigation/
    └── feature/
        ├── exercises/  routines/  workout/  history/  progress/  settings/  reminders/
```

One package per feature; each holds its screen composables, its ViewModel and its UI state.
Cross-feature UI goes in `ui/components/`, cross-feature logic in `core/` or `domain/`.

---

## Domain rules — get these exactly right

These are easy to get subtly wrong, so they live in `domain/usecase/` as pure functions with
unit tests, not inline in ViewModels or composables.

- **Volume** = Σ (weight × reps) over completed, non-warm-up sets.
- **Estimated 1RM (Epley)** = `weight × (1 + reps / 30.0)`. Use `Double`, never `Int` division.
- **Personal records (FR-5.2)**: heaviest weight; most reps at a given weight; best estimated 1RM.
- **Warm-up sets are excluded from volume and PR calculations (FR-3.10).**
- Editing or deleting a past workout **recomputes** volume and PRs (FR-4.2).
- Deleting a custom exercise **archives** it; historical sets that reference it stay intact (FR-1.3).
- Only one workout may be active at a time (FR-3.1), and it autosaves on every change (FR-3.7).

---

## Commands

```bash
./gradlew assembleDebug                # build
./gradlew installDebug                 # build + install on the connected device
./gradlew testDebugUnitTest            # domain unit tests
./gradlew connectedDebugAndroidTest    # instrumented / Compose UI tests
./gradlew lint                         # Android lint
```

Prefer a real device over the emulator when testing the rest timer and reminders — Doze and
exact-alarm behaviour differ (NFR-3).

---

## Design mandate — this is not optional

**LiftBook must look and feel like a senior product designer built it.** Modern, beautiful,
aesthetic, and genuinely pleasant to use. It must never read as "an AI generated this app".
When a screen is implemented, its visual design gets the same care as its logic — a screen
that works but looks like a default Material template is *not* done.

Hold every screen to this bar: *would this pass as a screen from a well-funded, design-led
fitness app?* If not, iterate before moving on.

### The tells to avoid

These are what makes an app look machine-made. None of them belong here:

- Stock template colours (the scaffold's `Purple40` / `PurpleGrey80` / `Pink80` placeholders).
- Everything wrapped in a `Card` with a visible border and a drop shadow — a page of stacked boxes.
- Default `Icons.Filled.*` used indiscriminately, at inconsistent sizes.
- Arbitrary padding — `13.dp` here, `17.dp` there.
- Emoji as UI iconography.
- Loud gradients, neon accents, glassmorphism, or a "hero" banner on every screen.
- Centre-aligned body text, or text crammed edge-to-edge with no breathing room.
- A `CircularProgressIndicator` alone in the middle of a blank screen as the loading state.
- Blank screens showing nothing but "No data" when a list is empty.
- Filler copy: "Welcome to your fitness journey!", "Track your gains!" — write like a person.

### What to do instead

**Layout & rhythm.** Use a single 4dp-based spacing scale defined as tokens in `ui/theme/`
(4 / 8 / 12 / 16 / 24 / 32 / 48) and use nothing else. 16dp screen gutters. Group related
content with whitespace and subtle surface-tone shifts before reaching for a card or a divider.
Let screens breathe; generous vertical spacing reads as considered, cramped reads as generated.

**Colour.** Build a small, hand-tuned LiftBook palette in `ui/theme/Color.kt` — a confident
neutral base with one accent, used sparingly for the primary action and for progress/PR moments.
Drive everything from `MaterialTheme.colorScheme`; never hardcode a hex value in a composable.
Light and dark are both first-class and must each be checked, not assumed (NFR-5). Colour alone
never carries meaning — pair it with an icon or a label. Keep dynamic color off by default so
the app has a consistent identity of its own.

**Hierarchy.** Every screen has one clear primary action. Use weight, size and colour — in that
order — to establish hierarchy; reach for a divider or an outline last. The numbers the user
came to see (weight, reps, volume, PRs) get the strongest treatment on the screen; labels recede.

**Motion.** Subtle and purposeful. Animate state changes (a set completing, a PR landing, a row
being removed), never decoration. Use Compose's standard easing and short durations
(~150–250 ms). If an animation is noticeable *as an animation*, it is too much.

**Density & touch.** This app is used mid-set, one-handed, with sweaty hands and the phone
propped on a bench. Primary controls sit in the lower two-thirds of the screen, are at least
48 dp, and are hard to mis-tap. The set-logging row is the single most-used control in the app —
it deserves more design iteration than anything else in the codebase.

**Empty, loading and error states are designed screens, not afterthoughts.** An empty state gets
one short line of real copy explaining what belongs there, plus the action that fills it. Loading
uses skeletons shaped like the content that is coming. Errors say what happened and what to do next.

**Copy.** Short, plain, specific. "3 sets left", not "You have 3 sets remaining in this exercise!".
No exclamation marks, no hype, no motivational filler.

**Icons.** Pick one family and stay in it — Material Symbols (`androidx.compose.material.icons`,
outlined variants) at consistent sizes (20 dp inline, 24 dp actions). Never mix filled and
outlined arbitrarily.

### Typography — use the system font

**Always use the device's system font. Never bundle, download or hardcode a typeface.**

Many OEMs (Samsung One UI, Xiaomi HyperOS/MIUI, OnePlus OxygenOS and others) let the user choose
a system font, and LiftBook must render in whatever the user picked. That happens automatically
when — and only when — the app uses the platform default typeface.

Concretely:

- In `ui/theme/Type.kt`, every `TextStyle` uses `fontFamily = FontFamily.Default`, or simply omits
  `fontFamily`, which resolves to the same thing. `FontFamily.Default` maps to the platform's
  `Typeface.DEFAULT`, which is exactly what an OEM font override replaces.
- **Never** use `FontFamily(Font(R.font.…))`, `GoogleFont` / downloadable fonts, or
  `FontFamily.SansSerif` / `Serif` / `Monospace` / `Cursive` for body or display text. Each of
  these pins the app to a specific typeface and discards the user's choice. (A downloadable font
  is impossible anyway under NFR-1, and `res/font/` should stay empty.)
- Define the type scale by **weight, size, line height and letter spacing only** — never by family.
  Build a complete `Typography` (display / headline / title / body / label) rather than overriding
  one style and leaving the rest at Material defaults.
- Numerals in tables, timers and set rows use tabular figures where the font supports them
  (`TextStyle(fontFeatureSettings = "tnum")`) so columns don't jitter as values change.
- Respect the user's font-size and display-size settings: size text in `sp`, never `dp`, and make
  sure layouts survive a large font scale without clipping.

---

## Conventions

- Composables are stateless and take state + lambdas; state lives in the ViewModel.
- One immutable `data class …UiState` per screen, exposed as a `StateFlow`.
- Name files after what they contain: `WorkoutScreen.kt`, `WorkoutViewModel.kt`, `WorkoutUiState.kt`.
- All user-facing strings go in `res/values/strings.xml`. No string literals in composables.
- Every screen gets `@Preview` composables for **both** light and dark theme.
- Add new dependencies to `gradle/libs.versions.toml`, then reference them via `libs.…`.
- Don't add a library for something the platform or Compose already does well.
