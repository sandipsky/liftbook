# LiftBook — Architecture Plan

Status: **in use.** Implemented so far: the foundation this document describes (§7 step 1, less
what later slices need), the exercise library (FR-1.1–1.5), routines (FR-2.1–2.4), the active
workout (FR-3.1–3.10, FR-3.10 pulled forward from Phase 2) with its summary, history
(FR-4.1–4.4, the calendar FR-4.4 pulled forward from Phase 2), progress and stats
(FR-5.1–5.4, Phase 2, built on request), and settings and data (FR-6.2–6.5, on request, with
kilograms and kilometres as the only units — FR-6.1's toggle isn't built). Where the
implementation settled a detail or deviated, the relevant section says so.
Scope: package layout, data entities, navigation graph, ViewModel structure.
Spec: [`requirement.md`](requirement.md). Stack decisions: [`../CLAUDE.md`](../CLAUDE.md).

---

## 0. Guiding decisions

Five choices shape everything below. Each is called out where it applies.

1. **Single Gradle module (`:app`), layered by package.** Multi-module buys parallel builds and
   enforced boundaries; at this size it costs more Gradle plumbing than it returns. Packages are
   arranged so `domain/` and `data/` could be lifted into modules later without moving logic.
2. **`domain/` has zero Android and zero Room dependencies.** It is pure Kotlin. That makes every
   formula in §3 testable on the JVM with plain JUnit — no Robolectric, no emulator. This directly
   serves the "unit-test the domain logic" step in `guide.txt`. The one library it may use is
   `androidx.paging`'s `PagingData` in repository signatures: paging-common is platform-free
   Kotlin, and paged history (NFR-2) has to cross the domain boundary somehow.
3. **The active workout lives in the database, not in a ViewModel.** Every edit writes through to
   Room immediately; the ViewModel is a projection over a `Flow` from the DB. Autosave and
   crash-resume (FR-3.7) then fall out of the architecture instead of being bolted on, and process
   death needs no `SavedStateHandle` gymnastics.
4. **Derived values are never stored.** Volume, PRs, 1RM, last-performed dates and weekly summaries
   are computed by query or by a pure function. There is no cache to drift out of sync when a past
   workout is edited (FR-4.2). §2.9 covers the one escape hatch if profiling demands it.
5. **Timers and elapsed time are computed from a stored timestamp, never counted.** Nothing ticks a
   counter that a process kill could lose. §6.1 covers this.

---

## 1. Package layout

```
com.example.liftbook
│
├── LiftBookApplication.kt            @HiltAndroidApp
├── MainActivity.kt                   single activity, hosts the NavHost
│
├── core/                             no dependency on data/ or ui/
│   ├── time/                         Clock provider, elapsed-time Flow, week boundaries
│   ├── format/                       weight/duration/distance/date formatters (unit-aware)
│   ├── unit/                         kg↔lb, m↔km/mi conversion
│   └── result/                       AppResult / error types
│
├── domain/                           PURE KOTLIN — no Android, no Room, no Compose
│   ├── model/
│   │   ├── Exercise.kt               + MuscleGroup, Equipment, ExerciseType enums
│   │   ├── Routine.kt                + RoutineExercise
│   │   ├── Workout.kt                + WorkoutExercise, WorkoutSet, SetType
│   │   ├── SetMetrics.kt             sealed: Strength | Cardio | Bodyweight
│   │   ├── PersonalRecord.kt         + PrType
│   │   ├── WorkoutSchedule.kt
│   │   ├── BodyWeightEntry.kt
│   │   └── UserPreferences.kt        + WeightUnit, ThemeMode, FirstDayOfWeek
│   ├── repository/                   INTERFACES ONLY (dependency inversion)
│   │   ├── ExerciseRepository.kt
│   │   ├── RoutineRepository.kt
│   │   ├── WorkoutRepository.kt
│   │   ├── ProgressRepository.kt
│   │   ├── ScheduleRepository.kt
│   │   ├── SettingsRepository.kt
│   │   └── BackupRepository.kt
│   ├── calculator/                   PURE FUNCTIONS — the unit-test target
│   │   ├── ExerciseSearch.kt         library search + filters, ranked (FR-1.4)
│   │   ├── ExerciseNames.kt          name normalising + validation (FR-1.2, 1.3)
│   │   ├── VolumeCalculator.kt
│   │   ├── OneRepMaxCalculator.kt    Epley
│   │   ├── PersonalRecordDetector.kt
│   │   └── WeeklySummaryCalculator.kt
│   └── usecase/                      only where it earns its place (see §4.3)
│       ├── StartWorkoutUseCase.kt
│       ├── FinishWorkoutUseCase.kt
│       ├── ExportDataUseCase.kt
│       ├── ImportDataUseCase.kt
│       └── RescheduleRemindersUseCase.kt
│
├── data/
│   ├── local/
│   │   ├── LiftBookDatabase.kt
│   │   ├── Converters.kt             enums↔String, Instant↔Long, LocalDate↔epochDay
│   │   ├── entity/                   @Entity classes — flat, SQL-shaped
│   │   ├── dao/                      ExerciseDao, RoutineDao, WorkoutDao, SetDao,
│   │   │                             ProgressDao, ScheduleDao, BodyWeightDao
│   │   ├── projection/               @Embedded / POJO query results (list rows, aggregates)
│   │   └── seed/                     ExerciseSeed.kt — preloaded library (FR-1.1)
│   ├── preferences/                  SettingsDataStore + UserPreferences mapping
│   ├── backup/                       versioned JSON envelope, DTOs, SAF read/write
│   ├── mapper/                       entity ⇄ domain model (both directions)
│   └── repository/                   the implementations of domain/repository
│
├── notification/
│   ├── channel/                      NotificationChannels — rest timer, reminders
│   ├── rest/                         RestTimerScheduler, RestTimerReceiver
│   ├── reminder/                     ReminderScheduler, ReminderReceiver, BootReceiver
│   └── ReminderActionReceiver.kt     Start now / Snooze / Skip (FR-7.4)
│
├── di/                               Hilt modules
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt           @Binds interface → impl
│   ├── DispatcherModule.kt           @IoDispatcher / @DefaultDispatcher qualifiers
│   ├── ClockModule.kt                injectable java.time.Clock — testable "now"
│   └── NotificationModule.kt
│
└── ui/
    ├── theme/                        Color, Type, Shape, Spacing, Dimens tokens
    ├── components/                   the shared design system (see §5.4)
    ├── navigation/
    │   ├── LiftBookNavHost.kt
    │   ├── Route.kt                  @Serializable type-safe route definitions
    │   ├── TopLevelDestination.kt    the four bottom-bar entries
    │   └── DeepLinks.kt              notification → screen (FR-7.3)
    └── feature/
        ├── home/                     start a workout, routine list, resume banner
        ├── workout/                  ACTIVE WORKOUT — the core screen
        │   └── components/           set row, exercise block, rest timer bar
        ├── summary/                  post-workout summary (FR-3.8)
        ├── history/                  list, calendar, past-workout detail (its editor is in workout/, §5.2)
        ├── exercises/                library, detail, editor, archived list
        ├── routines/                 detail, editor
        ├── progress/                 dashboard, per-exercise charts, body weight
        └── settings/                 settings, reminders, data management
```

**Dependency direction is strictly one-way:** `ui → domain ← data`, with `core` usable by all.
`ui` never imports from `data`; `domain` imports nothing from either. If a `ui` file ever needs an
`import ...data.local.entity`, the mapper is missing.

---

## 2. Data entities

### 2.1 Key & type conventions

- **Primary keys are `String` UUIDs**, not auto-increment `Long`. Reason: FR-6.4 merge-import. Two
  devices generating `id = 7` for different workouts makes merge unsolvable; UUIDs make it a
  dictionary union. Seeded exercises get **hardcoded, stable UUIDs** so an export from one device
  resolves against the seed library on another. The cost at this scale is negligible.
- **Enums are stored as `String`**, not ordinal. Readable in the DB inspector, survives reordering
  of the enum, and exports directly to JSON.
- **`Instant` is stored as epoch millis (`Long`)**, `LocalDate` as epoch day (`Long`), `LocalTime`
  as minutes-of-day (`Int`).
- `minSdk = 26` means **`java.time` is available natively** — no core library desugaring needed.
- **Weight is stored in kilograms as `Double`**, distance in metres, duration in seconds (FR-6.1).
  Conversion happens in `core/format` at display time only. Never round on write; round only on
  display, or lb→kg→lb round-trips will drift.
  *As built:* LiftBook shows kilograms and kilometres only, on request, so FR-6.1's toggle isn't
  built. `WeightUnit` and the display code that takes it stay — always `KG` — so adding pounds
  later is a setting, not a rewrite.
- Timestamps use an **injected `Clock`** (§1, `di/ClockModule`), never `Instant.now()` inline, so
  date-sensitive logic (PRs, weekly summaries, "completed earlier today") is testable.

### 2.2 `exercises`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | String PK | stable UUID for seeded rows |
| `name` | String | indexed — search (FR-1.4) |
| `primaryMuscle` | String enum | indexed — filter (FR-1.4), muscle-group stats (FR-5.3) |
| `equipment` | String enum | indexed — filter (FR-1.4) |
| `type` | String enum | `STRENGTH` / `CARDIO` / `BODYWEIGHT` — drives which set fields show (FR-3.3) |
| `isCustom` | Boolean | seeded rows are not editable (FR-1.2, 1.3) |
| `isArchived` | Boolean | **soft delete** — past logs stay intact (FR-1.3) |
| `defaultRestSeconds` | Int? | per-exercise rest override; null → global default (FR-3.5) |
| `notes` | String? | |
| `createdAt` | Long | |

Every library query filters `isArchived = 0`. History queries do not — that is the whole point of
archiving rather than deleting.

*As built:*
- **Seeding.** `data/local/seed/ExerciseSeed.kt` holds 129 built-ins, inserted by a
  `RoomDatabase.Callback.onCreate` inside the database-creation transaction, so the library is
  never observed empty and a crash can't half-seed it. Ids were generated once as name-based UUIDs
  and are now fixed data; renaming a built-in must not change its id. `onCreate` only runs on a
  fresh install, so adding built-ins after release needs a migration that inserts them.
- **The type locks once sets exist.** Changing an exercise's type would reinterpret every set logged
  against it (weight × reps read as duration, say). `ExerciseRepository.updateCustomExercise`
  rejects a type change when any `workout_sets` row references the exercise; the editor shows the
  field locked. Name, muscle group and equipment stay editable.
- **Built-ins are read-only, custom exercises editable (FR-1.3)** — enforced in the repository, not
  just hidden in the UI.

### 2.3 `routines` / `routine_exercises`

| `routines` | Type | Notes |
| --- | --- | --- |
| `id` | String PK | |
| `name` | String | |
| `notes` | String? | |
| `createdAt` / `updatedAt` | Long | |

Last-performed date (FR-2.4) is **not a column** — it is `MAX(startedAt)` over workouts whose
`routineId` matches. Storing it would need a write on every workout finish and would go stale when
history is edited.

| `routine_exercises` | Type | Notes |
| --- | --- | --- |
| `id` | String PK | |
| `routineId` | String FK → routines | `ON DELETE CASCADE` |
| `exerciseId` | String FK → exercises | `ON DELETE RESTRICT` — exercises archive, never delete |
| `position` | Int | ordering (FR-2.2); index on `(routineId, position)` |
| `targetSets` | Int | |
| `targetReps` | Int? | |
| `targetWeightKg` | Double? | |
| `targetDurationSeconds` | Int? | cardio |
| `targetDistanceMeters` | Double? | cardio |
| `restSecondsOverride` | Int? | |
| `notes` | String? | |

Reordering rewrites `position` for the affected rows inside one `@Transaction`.

*As built:*
- **Last performed counts finished workouts only.** A workout still in progress hasn't been
  performed yet. The routine query computes it as a correlated subquery, using the index on
  `workouts.routineId`.
- **Saving an edit is one transaction that keeps rows.** The editor saves the whole routine; the
  draft carries each exercise's original row id, so kept exercises are updated in place — keeping
  the columns the editor doesn't show yet, like `restSecondsOverride` — removed ones are deleted,
  and new ones inserted. Nothing refers to `routine_exercises.id` today, but a merge-import
  (FR-6.4) keyed by UUID would.
- **Targets are flat, like the table, not sealed like `SetMetrics`.** A custom exercise's type can
  still change while no sets reference it, and a routine alone doesn't lock it. So `SetTarget`
  keeps every value and `applicableTo(type)` picks the ones the type records, when the target is
  shown or a workout starts. The editor shows only those fields; hidden values survive a type change.
- **Routine names are unique, ignoring case** (`domain/calculator/RoutineNames.kt`), as exercise
  names are — two "Push" routines would be indistinguishable in the list and in history.
- **Duplicate (FR-2.2) names the copy by number:** "Push" → "Push 2", "Day 1" → "Day 2". It reads
  the same in every language, which a "(copy)" suffix wouldn't, and it's usually the name wanted.
- **Delete asks first, and has no undo.** `ON DELETE SET NULL` unlinks the workouts done from the
  routine, so restoring it couldn't restore its last-performed date. Archived exercises stay in
  the routines that use them.

### 2.4 `workouts`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | String PK | |
| `name` | String | defaults to the routine name, else a time-of-day name |
| `routineId` | String? FK → routines | `ON DELETE SET NULL` — deleting a routine must not delete history |
| `startedAt` | Long | indexed **DESC** — drives FR-4.1 and the calendar |
| `finishedAt` | Long? | **`NULL` ⇒ this is the active workout** |
| `note` | String? | FR-3.9 |
| `restStartedAt` / `restEndsAt` | Long? | the rest running now (FR-3.5); both `NULL` when none. Schema v2 |

There is no `status` column and no `duration` column: active is `finishedAt IS NULL`, duration is
`finishedAt - startedAt`. Two fewer invariants to keep in sync. The rest timer is likewise two
instants, not a countdown (§6.1); the progress line on screen is `now` between them.

**"Only one active workout" (FR-3.1)** is enforced in `WorkoutRepository` inside a transaction that
asserts no other row has `finishedAt IS NULL` before inserting. Room cannot express the partial
unique index that would enforce it at the schema level.

*As built:* `WorkoutRepository.startFromRoutine` (FR-2.3) does the check and the inserts in one
transaction: the workout, one `workout_exercises` row per routine exercise in order, and one
uncompleted `NORMAL` set per target set, holding the target's applicable values as real, editable
values. If a workout is already active it starts nothing and says which: `Resumed` when it came
from the same routine, `OtherWorkoutActive` otherwise. That is why there is no
`StartWorkoutUseCase` — the rule has to live inside the transaction, so a use case would only
forward the call.

*As built, step 4:*
- **Empty workouts (FR-3.1)** start through `startEmpty(name)` under the same check; any workout
  in progress blocks it (`OtherWorkoutActive`). The name is the time of day in the user's language
  ("Evening workout"), resolved by the UI and stored, since workout names are stored.
- **Pre-fill (FR-3.4)** is `domain/calculator/SetPrefill`, called inside the insert transaction
  with the exercise's last finished session. A routine's values win and last time fills only its
  blanks, set i from working set i (§8 Q9, as assumed). An exercise added mid-workout repeats last
  time's sets, types included; one never done gets three empty sets. A set added to an exercise
  copies its last working set. Completing a set also fills the *empty* later sets of the same type
  with its values ("carry forward"), so logging 80 × 8 once readies the next sets.
- **Completion needs what the type records (FR-3.3):** weight (0 allowed) and ≥ 1 rep, ≥ 1 rep, or
  a duration. `SetValues.metricsFor(type)` is the one rule, used by the row and the ViewModel.
- **Finishing (FR-3.8)** drops sets never marked done, then exercises left with no sets and no
  note, sets `finishedAt` and clears the rest — one transaction. The screen asks first only when
  that loses something: with nothing done it offers Discard instead.
- **Undoing a set stops the rest it started.** Completing stamps the set's `completedAt` and the
  rest's `restStartedAt` with the same instant, so a match identifies the rest without a column.

### 2.5 `workout_exercises`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | String PK | |
| `workoutId` | String FK → workouts | `ON DELETE CASCADE` |
| `exerciseId` | String FK → exercises | `ON DELETE RESTRICT`; indexed — per-exercise history (FR-4.3) |
| `position` | Int | index `(workoutId, position)` — FR-3.2 reorder |
| `note` | String? | FR-3.9 |
| `restSecondsOverride` | Int? | |

*As built:* rest after a set is `workout_exercises.restSecondsOverride` (copied from the routine)
?: `exercises.defaultRestSeconds` ?: the global default in DataStore (90 s; 0 turns it off). Setting
an exercise's rest from the workout writes `exercises.defaultRestSeconds` — a preference about the
exercise, so built-ins take it too — and clears this workout's override so it applies at once.

### 2.6 `workout_sets`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | String PK | |
| `workoutExerciseId` | String FK | `ON DELETE CASCADE` |
| `workoutId` | String | **denormalised** — see below |
| `exerciseId` | String | **denormalised** — see below |
| `position` | Int | index `(workoutExerciseId, position)` |
| `setType` | String enum | `NORMAL` / `WARMUP` / `DROP` / `FAILURE` (FR-3.10) |
| `isCompleted` | Boolean | FR-3.3 |
| `weightKg` | Double? | strength |
| `reps` | Int? | strength, bodyweight |
| `durationSeconds` | Int? | cardio |
| `distanceMeters` | Double? | cardio |
| `completedAt` | Long? | index `(exerciseId, completedAt)` |

**The two denormalised columns are a deliberate trade-off.** `workoutId` and `exerciseId` are both
reachable by joining through `workout_exercises`, but the two hottest queries in the app — "every
set ever logged for this exercise" (FR-4.3, FR-5.1, FR-5.2) and "total volume for this workout"
(FR-4.1) — become a single index range scan instead of a three-table join. They cannot drift: a set
row belongs to one workout-exercise for its entire life, and that never moves between workouts.
The mappers set both on insert; nothing else ever writes them.

This table is the one that grows without bound (1,000 workouts ≈ 30,000 rows), so it carries the
indexes that NFR-2 depends on — plus one on `workoutId` alone, which the per-workout volume
subquery in §2.9 (`WHERE s.workoutId = w.id`) needs.

### 2.7 `workout_schedules` (FR-7)

| Column | Type | Notes |
| --- | --- | --- |
| `id` | String PK | |
| `dayOfWeek` | Int | ISO 1–7; indexed |
| `startTimeMinutes` | Int | minutes from midnight |
| `routineId` | String? FK | `ON DELETE SET NULL` (FR-7.1) |
| `leadTimeMinutes` | Int? | null → global default of 10 (FR-7.2) |
| `isEnabled` | Boolean | FR-7.7 |
| `snoozedUntil` | Long? | FR-7.4, Phase 2 |
| `skippedOnDate` | Long? | epoch day; FR-7.4, Phase 2 |

### 2.8 `body_weight_entries` (FR-5.4, Phase 2)

`id` PK · `weightKg` Double · `recordedOn` Long (epoch day, **unique** — one per day) · `note` String?

*As built (schema v3):* as planned. `LocalDate` gets its epoch-day converter here, its first use.
Logging a day that already has a weigh-in updates that row in place, keeping its id, inside one
transaction — the unique index is what makes "one per day" hold. `note` isn't edited yet; it's in
the table so an export (FR-6.3) can carry one without a migration. Body weight has its own
`BodyWeightRepository` rather than living in `ProgressRepository`: it's the one part of FR-5 that
writes, and the rest is read-only.

### 2.9 What is *not* an entity

**Personal records have no table.** They are derived by indexed aggregate queries over
`workout_sets` — `MAX(weightKg)`, `MAX(reps)` at a weight, `MAX(weight × (1 + reps/30))`. This is
what makes FR-4.2 ("edits recompute PRs") free rather than a cache-invalidation problem, and it is
what lets a past workout show which sets were PRs *at the time* (`... AND completedAt < :setTime`).

*Escape hatch, only if profiling shows a problem:* add a `personal_records` table that is fully
**recomputed** for an exercise whenever that exercise's sets change — never incrementally mutated.
It stays a cache, rebuildable from `workout_sets` at any time. Do not build it pre-emptively.

**Workout totals have no columns.** The history list (FR-4.1) uses correlated scalar subqueries per
row, so a paged query only aggregates the ~20 rows it actually fetches:

```
SELECT w.*,
  (SELECT IFNULL(SUM(s.weightKg * s.reps), 0) FROM workout_sets s
     WHERE s.workoutId = w.id AND s.isCompleted AND s.setType <> 'WARMUP') AS totalVolumeKg,
  (SELECT COUNT(*) FROM workout_sets s
     WHERE s.workoutId = w.id AND s.isCompleted) AS completedSets
FROM workouts w
WHERE w.finishedAt IS NOT NULL
ORDER BY w.startedAt DESC
```

`WHERE ... setType <> 'WARMUP'` is FR-3.10 enforced at the source. The same predicate must appear in
every volume and PR query — it is the single easiest thing to get wrong in this app.

*As built* (`WorkoutDao.FINISHED_WORKOUTS`): the volume subquery also joins `exercises` and leaves
out `CARDIO`, so it gives exactly what `VolumeCalculator` gives (§8 Q1, Q2) even for a cardio set
that has a weight in it; `WorkoutHistoryRepositoryTest` checks the list's volume against the
summary's. The row also carries its exercises' names through a `@Relation`, for the list to show
what was done. The calendar (FR-4.4) runs the same query over one month's range of `startedAt`.

### 2.10 Settings — DataStore, not Room

Preferences DataStore, wrapped by `SettingsRepository`, exposed as `Flow<UserPreferences>`. Keys:
`weightUnit`, `themeMode`, `firstDayOfWeek`, `defaultRestSeconds` (90), `restTimerVibrate`,
`remindersEnabled`, `defaultReminderLeadMinutes` (10), `exportFormatVersion`.

Preferences DataStore rather than Proto: a typed `UserPreferences` domain class mapped in the
repository gives the same type safety at the call site without the protobuf build plumbing.

*As built (FR-6.2):* `defaultRestSeconds`, `firstDayOfWeek` (`MONDAY` / `SUNDAY`) and `themeMode`
(`SYSTEM` / `LIGHT` / `DARK`), stored by name; an unknown value reads as its default. Until the
user picks, the week starts where the locale starts it, or Monday where that's neither.
`weightUnit` is read but never written (§2.1). `lastExportedAt` lives in the same file but isn't a
setting: `BackupRepository` owns it, and it isn't exported. There's no `exportFormatVersion` key —
the version is a constant in the code that writes the file (§6.3). The reminder keys arrive with
FR-7.

### 2.11 Domain modelling note — `SetMetrics`

The Room row is flat with four nullable columns because that is what SQL wants. The **domain**
model is a sealed interface, so an invalid set cannot be constructed:

- `Strength(weightKg, reps)`
- `Cardio(durationSeconds, distanceMeters?)`
- `Bodyweight(reps, addedWeightKg?)`

`data/mapper` bridges the two. This is what stops "cardio set with a weight and no duration" from
being representable anywhere above the data layer, and it makes the set-row composable a clean
`when` over three cases rather than four nullability checks.

### 2.12 Migrations

`exportSchema = true`, schema JSON committed to `app/schemas/`. Explicit `Migration` objects from
v1 — never `fallbackToDestructiveMigration()` in a release build, since this app is the only copy
of the user's data. Schema changes ship with a migration test against the committed JSON.

*As built:* v1 holds the core tables — `exercises`, `routines`, `routine_exercises`, `workouts`,
`workout_exercises`, `workout_sets` — because the exercise history (FR-1.5) reads workouts, and
`workouts.routineId` references routines. `workout_schedules` and `body_weight_entries` arrive
with their slices as additive changes, which Room's declared `AutoMigration` handles.

**v2** adds `workouts.restStartedAt` / `restEndsAt` through `AutoMigration(1, 2)`.
**v3** adds `body_weight_entries` (FR-5.4) through `AutoMigration(2, 3)`.
`LiftBookDatabaseMigrationTest` builds the old database from the committed `1.json` / `2.json`,
inserts rows, and opens it with Room, which runs the migrations and validates the result against
the current schema. `room-testing`'s `MigrationTestHelper` isn't used: it reads schemas from instrumentation
assets, which JVM (Robolectric) tests don't have.

---

## 3. Domain calculators

Pure, injected nowhere, called directly. This is the unit-test surface.

| Function | Rule |
| --- | --- |
| `oneRepMax(weightKg, reps)` | Epley: `weightKg * (1 + reps / 30.0)`. `30.0` — integer division here is the classic silent bug. |
| `volume(sets)` | `Σ (weightKg × reps)` over sets that are **completed** and **not `WARMUP`** (FR-3.10). |
| `detectPersonalRecords(history, candidate)` | Heaviest weight; most reps at a given weight; best estimated 1RM (FR-5.2). Warm-ups excluded. |
| `weeklySummary(workouts, muscleSets, today, firstDayOfWeek, zone)` | Workout count, total volume, sets per muscle group, current + previous week (FR-5.3). Week boundary comes from the user's `firstDayOfWeek` setting (FR-6.2). |
| `kgToLb` / `lbToKg` | `1 lb = 0.45359237 kg` exactly. |

*As built* in `domain/calculator/`: `volume` (bodyweight counts added weight only, cardio nothing —
§8 Q1, Q2), `oneRepMax`, `detectPersonalRecords`, plus `SetPrefill` (§2.4), `RestTimes` (§2.5) and
`WorkoutProgress.kt` — live progress, the next set for the rest alert's text, `summarize` for the
summary, and `TimeOfDay`. PR rules settled: nothing is a record the first time an exercise is done
(every number would be), "most reps" only counts at a weight done before, heaviest weight and 1RM
apply to weighted sets, most reps to bodyweight sets too (at their added weight), and cardio has
none. Weights are compared to the gram, so the same plates typed in pounds match. The history they
read is a single query of earlier working sets per exercise, loaded once per summary.

*As built, FR-5:*
- **Which set holds a record** — `detectRecordSets` is `detectPersonalRecords` that also names the
  set that set each record (the first to reach it, on a tie), and `Workout.recordSets` applies it
  across a workout. `summarize` carries the result as `recordSetIds`. The live workout, the summary
  and the past-workout page all flag the sets from this one function, so a flag and the records
  listed can never disagree. A record is judged against *earlier workouts* only, so while logging it
  moves to whichever set now holds it — beat your first set's PR in your third and the trophy moves.
- **`ExerciseProgress.kt`** — `ProgressMetric.valueFor(sets)` per workout, `progressSeries` over a
  `ProgressRange` (1M / 3M / 6M / 1Y / all, counted back in whole months), and `summary()` (first,
  latest, best). A workout with nothing for the metric is left out rather than plotted as zero.
- **`WeeklySummaryCalculator.kt`** — `weeklySummary(workouts, muscleSets, today, firstDayOfWeek,
  zone)` (the planned signature took the sets and a clock; the week's workout list already carries
  volume computed the same way as history, so it's reused). `summaryWeeks` gives the span to query.
- **`BodyWeightTrend.kt`** — a 7-calendar-day trailing average at each weigh-in, and its change.
  Calendar days, not a count of entries, so irregular logging isn't averaged across gaps.

---

## 4. Navigation graph

### 4.1 Information architecture

Single activity, Navigation Compose, **type-safe `@Serializable` routes** (Navigation 2.8+) rather
than string routes — compile-time checked arguments, no manual encoding.

Four bottom-bar destinations, with settings as a top-app-bar action rather than a fifth tab:

```
RootNavHost
│
├── MainGraph                          ← Scaffold with the bottom bar
│   ├── Home                           start empty workout · routines · resume banner
│   ├── History                        list ⇄ calendar toggle (FR-4.1, FR-4.4)
│   ├── Progress                       weekly summary · body weight · per-exercise entry points
│   └── ExerciseLibrary                search + muscle/equipment filters (FR-1.4)
│
├── ActiveWorkout                      FULL SCREEN, no bottom bar (FR-3.x)
│   └── ExercisePicker                 bottom sheet, returns a selection (FR-3.2)
│
├── WorkoutSummary(workoutId)          FR-3.8
├── WorkoutDetail(workoutId)           view / edit a past workout (FR-4.2)
├── ExerciseDetail(exerciseId)         history + last-performed (FR-1.5, FR-4.3)
├── ExerciseEditor(exerciseId?, initialName?)  null id = create (FR-1.2, FR-1.3); name prefilled from a failed search
├── ArchivedExercises                  archived exercises, with Restore (FR-1.3)
├── RoutineDetail(routineId)
├── RoutineEditor(routineId?)          null id = create (FR-2.1, FR-2.2)
├── ExerciseProgress(exerciseId)       charts, range selector (FR-5.1)
│
└── SettingsGraph
    ├── SettingsHome                   units, theme, rest, week start (FR-6.1, 6.2)
    ├── ReminderList                   schedule entries + global toggle (FR-7.1, 7.7)
    ├── ReminderEditor(scheduleId?)
    └── DataManagement                 export / import / clear all (FR-6.3–6.5)
```

Routines sit on **Home** rather than getting their own tab: a routine exists to start a workout, so
it belongs next to the start button. That keeps the bar at four targets, which is what one-handed
use wants.

*As built:* `Home` is the start destination, labelled **Workout** in the bar. The bar has the
four tabs — Workout, History, Progress and Exercises (`ui/navigation/TopLevelDestination.kt`);
Progress joined with its slice, since a tab leading to a placeholder is worse than no tab. There
is no `MainGraph` node: it is one flat graph in one `NavHost`, and the shell's `Scaffold` shows the
bar only while the current destination is a tab, sliding it away as a full-screen destination
opens. Tabs switch with a cross-fade and keep their own state (`saveState`/`restoreState`); every
other move uses the shared-axis slide. `ArchivedExercises` is the restore path for FR-1.3's
"delete", which archives. Without it, an archived built-in — §8 Q4 lets those be archived — could
never come back, and recreating it as a custom exercise would split its history.

`ActiveWorkout` is the FR-3 screen: header (time, volume, sets done, workout note), one block per
exercise with its set table, and the rest bar docked at the bottom. Finish is a filled button in
the top bar — used once, at the end, and where a mid-set thumb won't hit it by accident; the set
rows, the done toggles and the rest controls are what sit in the lower part of the screen.
Exercises are added from the shared `ExercisePickerSheet` and reordered in a reorder mode that
collapses each to a compact row with a drag handle (plus Move up / Move down accessibility
actions), since dragging a tall block of set rows is unworkable. Home leads with "Start an empty
workout". `WorkoutSummary(workoutId)` follows Finish (FR-3.8).

*As built, step 5:* **History** is the paged list, newest first, a heading at each month
(`insertSeparators`), each row the day, name, exercises, volume and duration (FR-4.1). A row
with no volume — only reps or timed sets — shows its set count instead of "0 kg". The top bar's
one action swaps to the **calendar** (FR-4.4): the month's totals, then its days, training days
in a disc of the accent's soft tone. Tapping a day opens its workout, or a sheet to choose when
there were two. Arrows or a swipe turn the month, never past the current one. The week starts
on the day FR-6.2's setting says. Which view shows, and the month, live in the `SavedStateHandle`. `WorkoutDetail(workoutId)` reads a past workout back as the summary
does — stats, the records it set *at the time*, every set — with Edit in the top bar and Delete
behind the menu. `WorkoutEditor(workoutId)` edits it (§5.2). The exercise page's history links
each session to its workout, so FR-4.3 leads into FR-4.2.

*As built, FR-5:* **Progress** is the third tab: this week against last as a small table — this
week's numbers strongest, last week's receding — then sets per muscle as ink bars with a tick
where last week reached (FR-5.3); body weight's latest weigh-in and a sparkline of its trend,
with Log beside the heading (FR-5.4); then every exercise with finished work, most recent first.
Each opens **`ExerciseProgress(exerciseId)`** (FR-5.1): metric chips for what the type records,
the headline number and its change, a hand-drawn chart, the range under it within thumb reach,
and the workouts behind the line, each opening its `WorkoutDetail`. The exercise page leads there
too, from a progress glance between "Last time" and its history. **`BodyWeight`** is the full log:
the chart (weigh-ins as quiet dots, the trend as the line), the range, and the weigh-ins, each
opening the log sheet to change or delete it (with Undo); Log weight is its bottom action. The
chart's metric and range live in the `SavedStateHandle`. PRs are flagged during the workout
(FR-5.2): a done set holding a record trades its check for a trophy, and the exercise names its
records ("Heaviest · Est. 1RM") under its title, announced politely to TalkBack. The summary and
the past-workout page flag the same sets with a trophy on their line.

*As built, FR-6:* there is no `SettingsGraph` node, as there's no `MainGraph`: **`Settings`** and
**`DataManagement`** are two full-screen destinations. Settings opens from a gear in the Workout
tab's top bar. It shows every setting at once — the default rest as a row that opens the rest
dialog the workout uses, the week start and the theme as segmented choices — each applied as it's
picked, and a **Backup & data** row that names the last backup. That screen leads with what's on
the phone (workouts, routines, weigh-ins as numbers) and when it was last backed up; **Export
backup** is its bottom action. Import and **Clear all data** are rows beneath, clearing last and
apart, in the error colour with its icon. An empty phone shows an empty state whose action is
Import, and no export. Importing reads the file first and opens a sheet: when it was exported,
what's in it, and Merge or Replace — Merge first, or Replace on an empty phone — with the action
naming which. Replace asks once more. Clearing asks for a typed word (FR-6.5); its button stays
off until the word matches, ignoring case, and the dialog says when the last backup was.
Every outcome is a snackbar in plain words.

### 4.2 Navigation rules

- **Active-workout banner.** While `finishedAt IS NULL`, every `MainGraph` screen shows a persistent
  tappable bar with the workout name and elapsed time. This is what makes FR-3.1 and FR-3.7 legible
  to the user — the workout is never lost, and never more than one tap away.
  *As built:* docked above the tabs in the accent's container tone (`ActiveWorkoutBanner`, fed by
  `ActiveWorkoutBannerViewModel` at activity scope). It shows the elapsed time, or while resting
  the rest left, ticking once a second only while a workout is in progress. **This is FR-3.7's
  "resumes on next launch":** a relaunch after the app was killed opens on Home with the banner.
  Opening the workout automatically on launch was tried and dropped — navigating programmatically
  while the launch window animation was still running intermittently sent the next touch to the
  wrong node (a scroll landed as a tap on Back), and the banner already keeps the workout one tap
  away.
- **Finishing pops the workout.** `ActiveWorkout → WorkoutSummary` with
  `popUpTo(ActiveWorkout) { inclusive = true }`, so Back from the summary goes Home, never back into
  a finished workout. Discard (FR-3.8) is a confirmation dialog, then `popBackStack()`.
- **Start is idempotent.** Any start entry point — Home, a routine, a notification — routes to the
  *existing* active workout if there is one, rather than creating a second (FR-3.1).
  *As built:* only when it's the same routine. Starting a different routine while one is in
  progress shows "“Push” is still in progress" with Resume, instead of silently opening a workout
  other than the one tapped. Starting from a routine's page pops back to Home first, so Back from
  the workout lands where the banner keeps it in reach.
- **Deep links for notifications (FR-7.3).** `liftbook://active` and
  `liftbook://start?routineId={id}` as `navDeepLink` entries, reached by `PendingIntent` into
  `MainActivity`. A custom scheme, so this works with no `INTERNET` permission (NFR-1).
- **ViewModels never hold a `NavController`.** Screens take `onNavigateToX: (Id) -> Unit` lambdas
  wired in the `NavHost`. For navigation that a ViewModel must originate (save completes, workout
  finishes), the ViewModel exposes a `Channel`-backed `Flow<UiEvent>` collected with
  `LaunchedEffect` — one-shot, not replayed on rotation.
- **Editor screens return results through the DB**, not through nav result callbacks. The editor
  writes; the list is already collecting a `Flow` and updates itself.
- **UI messages that outlive a screen go through the previous entry's `SavedStateHandle`.** Archiving
  from the detail screen writes the exercise id to the library entry's handle and pops. The library
  ViewModel receives the same handle through Hilt, reads the id, and shows "archived · Undo". Data
  still flows through the DB; only the one-shot Undo offer travels this way.
- **Route arguments reach ViewModels by Hilt assisted injection**
  (`@HiltViewModel(assistedFactory = …)` + `hiltViewModel(creationCallback = …)`), not by parsing
  `SavedStateHandle`. The id becomes a plain constructor parameter, so tests build the ViewModel
  directly.
- **Navigation callbacks run only while their entry is `RESUMED`**, so a double tap or a tap during
  a transition can't navigate twice.

---

## 5. ViewModel structure

### 5.1 The uniform pattern

Every screen is exactly three files and one shape:

```
XxxScreen.kt       stateless @Composable (state: XxxUiState, onAction: (XxxAction) -> Unit)
XxxUiState.kt      immutable data class + sealed interface XxxAction
XxxViewModel.kt    @HiltViewModel, exposes StateFlow<XxxUiState>, has fun onAction(XxxAction)
```

`(state, onAction)` uniformly means every screen previews with a literal state object and an empty
lambda — which is what makes the light/dark `@Preview` pairs required by `CLAUDE.md` cheap enough
that they actually get written.

State is produced by combining repository `Flow`s:

```
combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), XxxUiState.Initial)
```

`WhileSubscribed(5_000)` is deliberate — it keeps upstream collection alive across a rotation or a
brief background trip, but drops it when the screen is genuinely gone.

**UI state is a data class with `isLoading` / `errorMessage` fields, not a sealed
`Loading | Success | Error` hierarchy.** A sealed hierarchy throws away the content already on
screen during a refresh, which produces exactly the flash-to-spinner that `CLAUDE.md` rules out.

**Text being typed is a `TextFieldState` owned by the ViewModel**, not a `String` in `UiState`.
The field edits it synchronously and the ViewModel observes it with `snapshotFlow`. Round-tripping
keystrokes through an async `StateFlow` is the classic cause of lost characters and jumping
cursors. The screen signature becomes `(state, textFieldState, onAction)`.

**Paged lists sit beside the state, not inside it.** A screen with a paged list also takes
`LazyPagingItems`, from a `Flow<PagingData>` that the ViewModel exposes with `cachedIn`.

### 5.2 The ViewModels

| ViewModel | State it owns | Notes |
| --- | --- | --- |
| `HomeViewModel` | routines + last-performed, one-tap start | FR-2.3, 2.4, 3.1. The active workout is the banner's |
| `ActiveWorkoutBannerViewModel` | the workout in progress, for the banner | activity-scoped; §4.2 |
| **`ActiveWorkoutViewModel`** | the active workout graph, rest timer, elapsed time | the core — §5.3 |
| `WorkoutSummaryViewModel` | duration, volume, completed sets, new PRs | FR-3.8. Records are best-effort: if the history can't be read the summary shows without them |
| `HistoryViewModel` | paged completed workouts, calendar month | FR-4.1, 4.4 |
| `WorkoutDetailViewModel` | one past workout: sets, totals, records at the time; delete | FR-4.2 |
| `WorkoutEditorViewModel` | a past workout's draft: name, times, notes, sets, exercises | FR-4.2 — see below |
| `ExerciseLibraryViewModel` | query + filters → results | FR-1.1, 1.4. **Deviation:** filters in memory, no debounce — see below |
| `ExerciseDetailViewModel` | exercise, last session, paged history, unit | FR-1.5, 4.3 |
| `ExerciseEditorViewModel` | form state + validation | FR-1.2, 1.3 |
| `ArchivedExercisesViewModel` | archived exercises | FR-1.3 restore path |
| `RoutineDetailViewModel` | routine + last performed; start, duplicate, delete | FR-2.2–2.4 |
| `RoutineEditorViewModel` | name, ordered exercises with target text fields, exercise picker | FR-2.1, 2.2 — see below |
| `ProgressViewModel` | weekly summary, trained exercises, body-weight glance, log sheet | FR-5.1, 5.3, 5.4 — see below |
| `ExerciseProgressViewModel` | chart series + metric and range selectors | FR-5.1 |
| `BodyWeightViewModel` | weigh-ins and trend over a range, log / edit / delete | FR-5.4 |
| `SettingsViewModel` | `UserPreferences`, last backup | FR-6.2 — no unit toggle (§2.1) |
| `MainViewModel` | the theme setting, for `MainActivity` | FR-6.2 — see below |
| `ReminderListViewModel` / `ReminderEditorViewModel` | schedules, permission state | FR-7.1, 7.6, 7.7 |
| `DataManagementViewModel` | counts, last backup, the operation running, the import draft, confirmations | FR-6.3–6.5 — see below |

*Library search deviates from the original plan* (a debounced DB query via `flatMapLatest`). The
library is a few hundred rows at most, so `domain/calculator/ExerciseSearch.kt` filters the
observed list in memory on every keystroke, well under a millisecond. That allows matching
`LIKE` can't express: words in any order, "pullup" = "Pull-Up", plurals, accents, gym shorthand
(`db`, `rdl`, `ohp`), and results ranked by relevance. It is a pure function with unit tests, and
a debounce would only add latency. NFR-2's paging concern is history, which is paged. The exercise
picker in the routine editor uses the same search.

*The routine editor writes nothing until Save*, unlike the active workout: a routine is a plan,
and closing the editor should discard the edit as a whole. Each exercise's target is a set of
`TextFieldState`s owned by the ViewModel (`TargetFields`), typed in the user's unit and converted
to kilograms and metres on save. A weight or distance the user didn't touch is saved exactly as
loaded rather than re-parsed from its rounded display text, so 80 kg viewed in pounds doesn't come
back as 79.9999 kg. Reordering is a drag handle (`ui/components/ReorderableList.kt`, hand-rolled
over `LazyListState` — Compose has no drag-to-reorder and it isn't worth a library) plus Move up /
Move down in each exercise's menu, which is also the TalkBack path.

*Progress reads through `ProgressRepository`* (`data/local/dao/ProgressDao.kt`): every working set
of one exercise (a range scan of the `(exerciseId, completedAt)` index), per-workout per-muscle set
counts for a span (grouped in SQL), and the trained exercises. Each query repeats the rules at the
source — completed, not a warm-up, finished workout — and none filters archived exercises. An
exercise's workouts are read whole and filtered by range in memory: an exercise's history is
bounded by how often it's been done, so this is a list rather than pages, and changing the range
or metric is instant. *Logging body weight* is a `BodyWeightLogger` that both the Progress tab and
the body-weight screen own — the sheet's state, the weight's `TextFieldState`, and the writes — so
the rules live once: a new day starts from the last weight, selected; a day with a weigh-in shows
it; an untouched value is saved exactly as stored, never re-read from its rounded text.

*The theme (FR-6.2) is applied above the navigation graph.* `MainViewModel` exposes the setting
as a `StateFlow<ThemeMode?>` that is null until DataStore has been read, and `MainActivity` draws
nothing until then — a few milliseconds, with the launch window's background showing — so the
first frame is already in the chosen theme. The status and navigation bar icons follow the app's
theme rather than the system's (`enableEdgeToEdge` is re-applied when it changes).

*Backups run one at a time* in `DataManagementViewModel`: an export, a read, an import or a
clear sets the operation, and every other start is ignored until it ends. Each runs in
`NonCancellable`, so leaving the screen can't cut one short between the database and settings
writes. A replace or a clear deletes the workout in progress with the rest, so they cancel the
rest alert (`RestTimerScheduler.cancel`). The typed word for clearing is a `TextFieldState` the
ViewModel owns and empties each time the dialog opens; the word itself is a string resource, so
it's matched in the dialog.

*Editing a past workout (FR-4.2) is a draft, like the routine editor, not write-through like the
active workout.* History is edited rarely and on purpose, so Close has to be able to throw an
edit away whole. Save builds a `WorkoutRevision` and `WorkoutRepository.saveRevision` applies it
in one transaction, keeping row ids: kept sets and exercises are updated in place, removed ones
deleted, new ones inserted. Every set in history is done, so a revision's sets are `SetMetrics`,
and one missing what its type records blocks the save and is marked. With no sets left, Save
offers Delete instead. Nothing else needs recomputing — volume and records are derived (§0.4).
The times are a date, a start and an end (`domain/calculator/WorkoutTimes`): the date moves the
whole workout, the start keeps the end, and the end is the first such time after the start, so a
workout left running overnight is fixed by setting its end. Each set's `completedAt` moves with
the start and is kept inside the workout. *Deviation:* the editor lives in `ui/feature/workout/`,
not `history/`, because it is the workout's set table — `ExerciseBlock` and `SetRow` without the
done toggle or rest chip, `SetFields`, `ReorderRow` — and that keeps features from importing each
other. Exercises added in an edit start from last time, as in a workout.

### 5.3 `ActiveWorkoutViewModel` — the one that matters

This screen carries most of the app's risk, so its rules are explicit.

- **It holds no authoritative state.** `uiState` is a projection of
  `workoutRepository.observeActiveWorkout()`. Every action writes to Room and the new state arrives
  back through the `Flow`. Kill the process mid-set and the next launch renders the same screen —
  FR-3.7 with no save/restore code.
- **Write granularity:** discrete edits (set complete, weight ±, add/remove/reorder) commit
  immediately; free-text notes debounce ~400 ms. Multi-row operations (reorder, add an exercise with
  its target sets) go in one `@Transaction`.
- **Pre-fill (FR-3.4)** comes from a `lastPerformed(exerciseId)` query at the moment an exercise is
  added, written into the new rows as real values. It is not a display-time fallback — the user must
  be able to edit it, and a phantom placeholder they cannot change is the wrong behaviour.
- **Elapsed time (FR-3.6)** is a `Flow` ticking once a second that emits `now() − startedAt`. It is
  never a counter, so backgrounding cannot make it drift.
- **Rest timer (FR-3.5, NFR-3): `AlarmManager.setExactAndAllowWhileIdle` + a locally-rendered
  countdown.** Marking a set complete stores `restEndsAt` and schedules one exact alarm; the UI
  renders `restEndsAt − now()`. The alarm is the authority, so the notification and vibration fire
  with the screen off, in Doze, and after a process kill. A foreground service was the alternative
  and is rejected: Android 14's `shortService` type caps at 3 minutes, which a rest timer can exceed,
  and the other FGS types do not honestly apply.
- **Actions are one sealed interface** (`CompleteSet`, `UpdateWeight`, `AddSet`, `RemoveExercise`,
  `ReorderExercise`, `SkipRest`, `AddRestTime`, `Finish`, `Discard`, …). With ~20 interactions,
  individual lambdas would make the composable signature unreadable.

*As built:*
- **Typed values live in `SetFields` / `NoteText`**, owned by the ViewModel and kept in step with
  the workout's sets. Each field remembers the value and text it last agreed with the database, so
  an untouched converted value is never re-parsed (80 kg shown as "176.37" lb stays 80 kg). One
  watcher saves changes 400 ms after typing pauses, in one transaction; completing a set, adding
  one, reordering and finishing save first; the route saves on `ON_STOP`.
- **All writes take turns** behind one `Mutex`, inside `NonCancellable` so leaving the screen can't
  cut a save in half. A failed write shows "Couldn't save that. Try again."
- **Elapsed time and the countdown** read a separate `now: StateFlow<Instant>` (`core/time/ticks`),
  passed to the clocks as a lambda, so the tick redraws only them — `uiState` never ticks.
- **Reorder mode** shows the new order at once and writes each move; it's shown ahead of the
  database until the database catches up, so the list never flashes back.
- **The rest alert** goes through `domain/repository/RestTimerScheduler`, implemented by
  `notification/rest/AlarmRestTimerScheduler`: one exact alarm, replaced by each rest, whose
  receiver posts "Rest over · Next up: <exercise>" on a high-importance channel with vibration.
  Without exact-alarm permission (default from Android 14) it falls back to an inexact alarm, and
  the rest bar says alerts may be late, with a Turn on shortcut; likewise when notifications are
  off. `POST_NOTIFICATIONS` is requested when the first rest starts — the moment it's for.
- **"Rest over" stays until dismissed** or replaced by the next set's rest (it clears itself only
  after 10 minutes, as the notification does). A bar that stepped aside on a timer put the set row
  behind it under a thumb reaching for Done.
- **Live records (FR-5.2)** are `Workout.recordSets` against each exercise's earlier working sets.
  Those are read once — again only when an exercise joins, since history doesn't change mid-workout
  — through the same shared active-workout flow the screen uses, so the hot query doesn't run twice.
  A failed read leaves the workout without flags rather than failing it.

### 5.4 `ui/components/` — the design system

Built once, before the feature screens, because `CLAUDE.md` makes design a completion criterion and
these are what stop each screen being re-invented:

`SetRow` (the most-used control in the app) · `ExerciseBlock` · `RestTimerBar` ·
`ActiveWorkoutBanner` · `LiftBookTopBar` · `PrimaryActionButton` · `StatTile` ·
`EmptyState(icon, title, body, action)` · `SkeletonList` · `ErrorState` ·
`ConfirmDialog` · `WeightField` / `RepsField` (unit-aware, tabular figures) · `FilterChipRow` ·
`MuscleGroupBadge` · `SectionHeader`.

*As built so far:* `ActiveWorkoutBanner` · `BottomActionBar` (the primary action pinned above the
keyboard) · `ChoiceChip` / `DropdownFilterChip` · `ConfirmDialog` · `EmptyState` ·
`ExercisePickerSheet` · `Fact` (a header's labelled value) · `LiftBookTopBar` · `NumberField` ·
`PrimaryActionButton` · `ReorderableList` · `SearchField` · `SectionHeader` · `SetMetricsText` /
`SetTargetText` · `SkeletonContainer` / `SkeletonBlock` · `WorkoutInProgressDialog`. `NumberField`
covers `WeightField` / `RepsField` in one: whole, decimal (either "." or ",") and duration kinds.
A duration is typed as digits that fill from the right, microwave-style, so "130" is 1:30 with no
colon to find on a number pad.

Step 4 added `NumberCell` (a label-less, centred `NumberField` for table cells: selects its value
on focus, "settles" — loses its fill — once the set is done, and scrolls itself and its row back
into view once the keyboard has risen) · `StatTile` · `NoteField` · `WorkoutLabels`, and a
`subtitle` slot on `LiftBookTopBar`. The workout's own parts are in `ui/feature/workout/components/`:
`SetRow` (set-type button · value cells · 48 dp done toggle) · `ExerciseBlock` · `ReorderRow` ·
`RestTimerBar` · `RestDurationDialog`. `ExercisePickerUiState` moved to `ui/components/` beside
the sheet, now that two features use it.

Step 5 moved the summary's read-back of a finished workout into `ui/components/WorkoutRecap.kt`,
which the past-workout page shares: `WorkoutStatsRow`, `PersonalRecordsBlock`,
`LoggedExerciseCard`, `LoggedSetLine` and `RecapExercise`. `SetRow` and `ExerciseBlock` take a
null done toggle and rest chip for the editor. The history's own parts are in
`ui/feature/history/`: `WorkoutHistoryRow`, `CalendarMonthHeader`, `TrainingCalendarGrid`.

The FR-5 slice added `ProgressChart` / `Sparkline` (Canvas, no library: dashed gridlines at round
values from `ChartScale`, days spaced by time, the latest point emphasised; press-and-drag scrubs
and letting go returns to the latest; an optional trend line with the readings as dots) ·
`SegmentedSelector` (a tonal track with a sliding ink indicator, for a chart's range) ·
`ProgressLabels` (metric and range names, stored-to-display conversion, signed changes) ·
`PastDatePickerDialog` (moved out of the workout editor for the log sheet to share) · and
`shortDateText` / `dateRangeText` in `DateLabels`. `RecapExercise` carries `recordSets` and
`LoggedSetLine` an `isRecord` trophy. The progress feature's own parts are in
`ui/feature/progress/`: `ChartHeadline`, `RangeSelector`, `ProgressRow`, `ChartEmptyPanel`,
`WeeklySummaryBlock`, `BodyWeightLogSheet`.

The FR-6 slice moved `RestDurationDialog` from the workout's components to `ui/components/`, now
that settings picks the default rest with it too. The settings feature's own parts are in
`ui/feature/settings/`: `SettingsRow` (a tonal row with its value or a chevron; destructive rows in
the error colour, always with an icon), `SettingChoice` (a title over a `SegmentedSelector`),
`DataCountTiles`, `ImportBackupSheet` and `ClearDataDialog`.

### 5.5 Threading & testing

- Repositories take an injected `@IoDispatcher`. Room and DataStore manage their own threads;
  ViewModels never touch `Dispatchers.IO` directly.
- `java.time.Clock` is injected everywhere a "now" is needed, so PR, weekly-summary and
  "completed earlier today" (FR-7.5) logic is testable without waiting for Tuesday.
- Test layers: `domain/calculator` → plain JUnit · repositories → in-memory Room · ViewModels →
  `kotlinx-coroutines-test` + Turbine + fake repositories · `ActiveWorkoutScreen` → Compose UI test.
- *As built:* the in-memory Room tests run on the JVM under **Robolectric** (test-only dependency),
  so `./gradlew testDebugUnitTest` checks the real SQL — seeding, the history query, foreign keys —
  without a device. Fakes live in `test/.../testing/`. ViewModel tests that type into a
  `TextFieldState` call `Snapshot.sendApplyNotifications()` so `snapshotFlow` sees the edit.
- *As built, step 4:* the `ActiveWorkoutScreen` Compose UI test also runs on the JVM under
  Robolectric, driven through semantics. `src/test/resources/robolectric.properties` pins the SDK
  below `targetSdk` (Robolectric trails the newest release), a phone-sized screen, and the legacy
  SQLite and graphics modes — Robolectric's native runtime fails to load from a path containing a
  space. Tests check SQL and semantics, not pixels, so nothing needs it.

---

## 6. Cross-cutting

### 6.1 Time and timers

Nothing in this app counts. The rest timer, the elapsed workout time and the reminders all derive
from a stored `Instant` plus an `AlarmManager` entry. A process kill loses no time, and Doze cannot
stall a countdown that is really just subtraction.

### 6.2 Reminders (FR-7)

`AlarmManager.setExactAndAllowWhileIdle` per enabled schedule entry, each with its own request code.
`RescheduleRemindersUseCase` is called from `BOOT_COMPLETED`, from any schedule edit, and from the
global toggle (FR-7.6) — one path, three triggers. Permissions: `POST_NOTIFICATIONS` (13+, requested
in context with rationale), `SCHEDULE_EXACT_ALARM` (13+, with a `canScheduleExactAlarms()` check and
a settings deep-link fallback), `RECEIVE_BOOT_COMPLETED`. FR-7.5's suppression check runs at fire
time in the receiver, not at schedule time.

### 6.3 Export / import (FR-6.3, 6.4, NFR-6)

Storage Access Framework (`CreateDocument` / `OpenDocument`) — the system picker, so no storage
permission. `kotlinx.serialization` with a versioned envelope:

```
{ "formatVersion": 1, "exportedAt": ..., "appVersion": ..., "data": { exercises, routines, workouts, sets, schedules, settings } }
```

Import reads `formatVersion` first and migrates forward; merge is a UUID-keyed union, replace is
gated behind a typed confirmation (FR-6.4, 6.5).

*Deviation from the spec's suggested stack:* this runs in an application-scoped coroutine, not
WorkManager. A single-user export is a few MB and completes in well under a second; WorkManager adds
a dependency, a worker and a constraint system for a job that does not outlive the screen. Revisit
if exports ever get large enough to need retry-on-failure.

*As built* (`data/backup/`, `BackupRepositoryImpl`, `BackupDao`):
- **The file.** `BackupFormat.kt` holds the DTOs, apart from the entities so a schema change can't
  silently change the format. The envelope is `format` ("liftbook-backup", so any file name
  works), `formatVersion`, `exportedAt`, `appVersion` and `settings`, then `exercises`, `routines`
  (each nesting its targets), `workouts` (each nesting its exercises, each nesting its sets) and
  `bodyWeight`. Nesting replaces the planned flat `sets` list: position is list order, and a set's
  denormalised `workoutId` / `exerciseId` are taken from its parent on import, never from the
  file, so they can't disagree (§2.6). Values are as stored — kilograms, metres, seconds — with
  instants in ISO-8601 UTC and dates as ISO days. Nulls are left out; unknown keys are skipped.
- **What's in it.** Every exercise, built-ins included, since they carry the user's archiving and
  rest times; every routine; *finished* workouts only — the one in progress isn't history yet;
  every weigh-in; and the three FR-6.2 settings. Not the unit (§2.1), not `lastExportedAt`.
- **Versions (NFR-6).** Reading checks the marker, then the version: newer than this app is refused
  with "update the app", not read wrongly. Older ones go through `BackupMigrations`, one
  `JsonObject` step per version, kept forever. There are none yet: v1 is the only format.
- **Checked before writing.** `BackupFile.isConsistent()` rejects what the database would reject or
  the screens couldn't show — repeated ids, a set or target of an exercise the file doesn't have,
  negative or non-finite numbers, a workout that ends before it starts, two weigh-ins on one day —
  so an import never starts on a file it would have to abandon. A workout's link to a routine the
  file lacks is dropped, as deleting a routine unlinks its workouts. Problems are typed
  (`BackupProblem`: unreadable, not a backup, newer version, damaged) and each has its own message.
- **Replace** deletes every table, the workout in progress included, and inserts the backup's rows
  in one transaction, then puts back any built-in the backup lacks (an older app may have shipped
  fewer), then writes the backup's settings. A missing or unknown setting takes its default.
- **Merge** is `planMerge`, a pure function over the backup's rows and what's on the phone: a union
  by id in which nothing already here changes, settings included. Beyond ids, an exercise new to
  the phone but named like one in its library (ignoring case) *is* that exercise when it records
  the same way, so its sets join the history already here rather than splitting it across two
  lookalikes; named like one of another type, it's added numbered ("Plank Hold 2"). A routine
  whose name clashes is numbered as a copy is. A weigh-in on a day the phone already has is
  skipped. The whole merge is one transaction and reports what it added.
- **Clear** (FR-6.5) deletes every table, puts back the built-in library, and clears the settings
  file, `lastExportedAt` with it: LiftBook is as it was installed.
- **Files** go through `BackupDocuments` — `ContentResolver` streams on the picked URI, read up to
  64 MB so a wrong pick like a video can't fill memory, written with `"wt"` where the provider has
  it. Above the data layer a file is an opaque `DocumentUri`. *Deviation:* the work runs in the
  ViewModel's scope under `NonCancellable` (§5.2) rather than an application scope — the result is
  shown on the screen that asked, and nothing needs to outlive it.

### 6.4 Dependencies to add

Room (+ `ksp`), Hilt (+ `ksp`), Navigation Compose, `kotlinx-serialization` (+ plugin), DataStore
Preferences, Paging 3 + `paging-compose`, `lifecycle-viewmodel-compose`, `compose-material-icons-extended`,
and for tests `kotlinx-coroutines-test`, Turbine, `room-testing`. All via
`gradle/libs.versions.toml`.

*As built:* all of the above except `room-testing`, which arrives with the first migration test,
plus `hilt-lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, Robolectric and
`androidx.test:core` (tests). The versions are pinned to what compiles against AGP 9's built-in
Kotlin 2.2.10: KSP 2.3.x, Hilt 2.59.x (2.60 pulls the Kotlin 2.3 BOM), kotlinx-serialization 1.9.x
(1.10+ needs Kotlin 2.3). The Compose BOM moved to 2026.09.00 because lifecycle-compose 2.11,
androidx.hilt 1.4 and Navigation 2.10 all require Compose ≥ 1.11. Lint's "newer version
available" warnings on these pins are expected until Kotlin itself is upgraded.

The FR-6 slice added `kotlinx-serialization-json` (same 1.9.x line) for the backup file, and turned
on `buildConfig` for the app version written into it. `DispatcherModule` provides `@IoDispatcher`,
used for reading and writing backup files.

---

## 7. Build order

Matches the MVP column of spec §8, each slice ending in something runnable.

1. **Foundation** — `minSdk` 24→26, Hilt, Room + entities + DAOs, seed library, DataStore, mappers,
   domain calculators **with their unit tests**, design tokens + `ui/components`.
2. **Exercise library** — FR-1.1–1.4.
3. **Routines** — FR-2.1–2.3.
4. **Active workout** — FR-3.1–3.9. The largest slice; the rest timer is its own sub-step.
5. **History** — FR-4.1–4.3.
6. **Settings & data** — FR-6.1, 6.3, 6.5.
7. **Reminders** — FR-7.1–7.3, 7.6, 7.7.

Phase 2 afterwards: FR-1.5, 2.4, 3.10, 4.4, 5.1–5.4, 6.2, 6.4, 7.4, 7.5.

**Progress.** Steps 1 and 2 are done, with FR-1.5 pulled forward on request. Step 1 was built to
what the library needs: Hilt, Room with the v1 core schema and the exercise/set DAOs, the seed,
DataStore (the unit setting only), mappers, design tokens, and the `ui/components` the exercise
screens use. The §3 calculators — volume, 1RM, PR detection, weekly summary — and the remaining
DAOs and components are built with the slices that use them, starting with the active workout.
Until the active-workout slice writes sets, the history on an exercise's page is always empty.

Step 3 is done, with FR-2.4 pulled forward on request: Home and the bottom bar, the routine
detail and editor, the exercise picker, start-from-routine, and the active-workout banner. The
routine and workout DAOs arrived with it; the schema is unchanged, since v1 already had the
tables.

Step 4 is done, with FR-3.10 (set types) pulled forward on request: empty and routine starts, the
set row and logging, add / remove / reorder exercises, pre-fill, the rest timer and its alarm,
elapsed time, autosave, notes, Finish and the summary with PRs — which brought the volume, 1RM and
PR calculators forward from §3. Schema v2 added the rest timer columns. Still to come from step 4's
neighbours: rescheduling a rest alarm after a reboot (alarms don't survive one) arrives with the
reminders slice's `BootReceiver`. PRs flagged on the set row *during* the workout (FR-5.2)
arrived with the progress slice.

Step 5 is done, with FR-4.4 (the calendar) pulled forward on request: the History tab's list and
calendar, the past-workout page with delete, and the editor. FR-4.3 already existed as the
exercise page's history (FR-1.5); it now opens each session's workout. The schema is unchanged:
history is queries over the v2 tables.

FR-5.1–5.4 (Phase 2) are done, on request: the Progress tab, per-exercise charts, PRs flagged on
the set row during the workout and on the recap's set lines, the weekly summary, and the
body-weight log with its trend. Schema v3 added `body_weight_entries`; everything else is queries
over existing tables.

Step 6 is done as FR-6.2–6.5, on request, with FR-6.2 and FR-6.4 pulled forward from Phase 2 and
FR-6.1 left out: the app uses kilograms and kilometres only (§2.1). Settings (default rest, week
start, theme), export, import with merge or replace, and clearing all data. The week start
replaced the locale's `WeekFields` that `di/ClockModule` used to provide; the calendar and the
weekly summary read the setting and follow it as it changes. The schema is unchanged.

---

## 8. Open questions

Assumptions are stated so work is not blocked; confirm or correct before the foundation slice.

1. **Do bodyweight sets contribute to volume?** The spec defines volume as `weight × reps`, and a
   bodyweight set has no weight. *Assumed:* they contribute `0` unless `addedWeightKg` is set, in
   which case `addedWeightKg × reps`. The alternative — multiplying by the user's logged body
   weight — couples volume to FR-5.4, which is Phase 2.
2. **Do cardio sets contribute to volume?** *Assumed:* no. They appear in the summary as duration
   and distance, and are excluded from volume and PR figures entirely.
3. **`applicationId` is `com.example.liftbook`.** Fine for a personal sideloaded build, but it
   cannot be changed after a Play Store release. Change it now if that is ever the plan.
4. **Can seeded exercises be archived?** *Assumed:* yes — hidden from the library, edits still
   restricted to custom exercises (FR-1.3). A library the user cannot prune gets unusable fast.
   *Built as assumed*, with Undo on archive and an Archived screen to restore from.
5. **`minSdk` 24 → 26** is required by NFR-4 and assumed throughout (it is what makes `java.time`
   available natively). Confirm no device below API 26 matters. *Done:* `minSdk = 26`.
6. **Timed holds don't fit the three types.** Plank, side plank, wall sit and dead hang record a
   duration, and CARDIO is the only type that logs one, so they're seeded as CARDIO. To keep that
   honest, the UI names types by what a set records — "Weight & reps", "Reps", "Time &
   distance" — rather than "Strength / Bodyweight / Cardio". *Option:* add a `TIMED` type before
   the active-workout slice; since the built-in ids are fixed, it would be a one-line data
   migration per exercise.
7. **Distance units follow the weight unit** — km with kg, miles with lb — since FR-6.1 is a
   single toggle "applied everywhere". Split into two settings if that's wrong.
   *Decided in step 6:* kilograms and kilometres only, so there is no toggle to follow.
8. **Bodyweight sets and added weight.** FR-3.3 says bodyweight logs reps only; §2.11 models
   `Bodyweight(reps, addedWeightKg?)`. History already displays added weight when present
   ("+10 kg × 8"); whether the set row offers it is for the active-workout slice to decide.
   Routine targets for bodyweight exercises are sets and reps only, as the spec says.
   *Decided in step 4:* the set row logs reps only, as FR-3.3 says. Added weight stays readable
   in history and counts in volume if a set has it (from an import, say), but isn't entered.
9. **Routine targets vs. last time (FR-2.3 against FR-3.4).** Starting a routine fills each set
   with the routine's targets; a value the routine leaves blank stays blank. *Assumed for step 4:*
   the last session fills only those blanks, so a routine's planned weight wins over what was
   lifted last time. The alternative — last time always wins — would make the routine's weight
   a one-off default. *Built as assumed* (`SetPrefill.forRoutineExercise`).
10. **Targets are the same for every set of an exercise** — "3 × 10 at 80 kg", as FR-2.1 words it
    and the schema stores. Per-set targets (a pyramid, a top set and back-offs) would need a
    `routine_sets` table; say so if they matter.
11. **Exact alarms need the user's permission from Android 14.** The rest timer declares
    `SCHEDULE_EXACT_ALARM` (§6.2) and falls back to an inexact alarm, saying so in the rest bar.
    `USE_EXACT_ALARM` is granted at install and would remove that step, but Play restricts it to
    alarm-clock and calendar apps. For a sideloaded personal build it's the simpler choice; say
    if that's the plan.
12. **Finishing drops sets never marked done.** Pre-filled but undone sets would otherwise sit in
    history as rows that never happened. The screen says how many before finishing.
13. **What an edit to a past workout can change (FR-4.2).** *Assumed:* everything the workout
    logged — name, date, start and end, notes, set values and types, sets and exercises added or
    removed, their order — but not which exercise a logged set belongs to. Swapping an exercise
    would be a remove and an add. Deleting asks first and has no undo, as for routines.
14. **The calendar's week start (FR-4.4)** follows the locale until FR-6.2 adds the setting. The
    weekly summary (FR-5.3) does the same. *Done:* both follow the setting; until it's picked, it
    defaults to the locale's Monday or Sunday.
15. **What a chart shows for types other than strength (FR-5.1).** The spec names max weight,
    estimated 1RM and volume, which only mean something for weighted sets. *Assumed:* strength
    charts those three (1RM first — it compares sets of different reps); bodyweight charts most
    reps and total reps; cardio charts total time and total distance. A pull-up page with nothing
    to chart would be the wrong answer.
16. **Sets per muscle group (FR-5.3)** count completed working sets by the exercise's primary
    muscle; warm-ups don't count, as for volume. Exercises have no secondary muscles, so a bench
    set counts for chest only.
17. **A record belongs to the set that holds it at the end (FR-5.2).** Records are judged against
    earlier workouts, as the summary judges them, so if a later set beats an earlier set's record
    in the same workout the flag moves to it. The alternative — flag every set that beat
    everything before it, this workout included — would flag sets the summary doesn't list.
18. **Body-weight trend** is a 7-day trailing average (calendar days). Other smoothing
    (exponential, 10-day) is a one-constant change if it reads wrong in use.
19. **A backup holds finished workouts only (FR-6.3).** The workout in progress isn't history
    yet, so it isn't exported; a replace or a clear does delete it. Exporting mid-workout and
    restoring on another phone therefore leaves that session behind.
20. **Merge keeps this phone's version of anything both have (FR-6.4)**, by id, and never changes
    settings. An exercise matched by name joins the one here only when it records the same way;
    say if a same-named exercise should always stay separate instead.
21. **Clearing all data resets settings too (FR-6.5)**, since FR-6.3 counts settings as data. The
    alternative — keeping the theme and week start — is one line in `BackupRepositoryImpl.clearAll`.
