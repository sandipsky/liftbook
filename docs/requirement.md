
Sep 25, 2026 · @Sandip

## Overview
App Name: LiftBook
A single-user Android workout tracker in Kotlin that works fully offline, with all data stored on-device and no accounts, sync or backend.

**Assumptions**

- One user per install; no login.
- No network permission at all in v1.
- Data must persist between launches. Room (SQLite) is local and the idiomatic Kotlin choice; if "no database" means no SQLite either, Proto DataStore or a JSON file in app storage is enough for the MVP.
- Requirement IDs (FR-x.y) are stable so they can be referenced in issues and commits.

## 1. Exercise library

The app ships with a ready-to-use exercise list and lets the user extend it.

| ID | Requirement |
| --- | --- |
| FR-1.1 | Ship with a preloaded list of exercises, each with name, primary muscle group, equipment, and type (strength / cardio / bodyweight). |
| FR-1.2 | User can create a custom exercise with the same fields. |
| FR-1.3 | User can edit or delete custom exercises. Deleting archives the exercise; past logs that reference it stay intact. |
| FR-1.4 | Search exercises by name; filter by muscle group and by equipment. |
| FR-1.5 | Exercise detail screen shows its history and last-performed values (see FR-4.3). |

## 2. Routines (templates)

Routines let the user start a workout with exercises and targets already in place.

| ID | Requirement |
| --- | --- |
| FR-2.1 | Create a routine: name plus an ordered list of exercises, each with default sets, reps and weight. |
| FR-2.2 | Edit, reorder exercises within, duplicate, and delete routines. |
| FR-2.3 | Start a workout from a routine, pre-populated with its exercises and targets. |
| FR-2.4 | Routines list shows last-performed date per routine. |

## 3. Active workout logging

This is the core screen; everything else exists to feed it or read from it.

| ID | Requirement |
| --- | --- |
| FR-3.1 | Start an empty workout or one from a routine. Only one workout can be active at a time. |
| FR-3.2 | Add, remove and reorder exercises mid-workout. |
| FR-3.3 | Log sets per exercise: weight × reps for strength; duration and distance for cardio; reps only for bodyweight. Each set can be marked complete. |
| FR-3.4 | Pre-fill each set with the values from the last time that exercise was performed. |
| FR-3.5 | Rest timer starts automatically when a set is marked complete; duration is configurable per exercise and globally. It fires a notification and vibration when done, including with the screen off. |
| FR-3.6 | Show total elapsed workout time. |
| FR-3.7 | Autosave the in-progress workout on every change. If the app is killed, it resumes on next launch. |
| FR-3.8 | Finish workout → summary screen: duration, total volume, completed sets, any new PRs. Discard requires confirmation. |
| FR-3.9 | Optional free-text note per workout and per exercise. |
| FR-3.10 | Set types: normal, warm-up, drop set, failure. Warm-up sets are excluded from volume and PR calculations. |

## 4. History

Every completed workout is browsable and editable after the fact.

| ID | Requirement |
| --- | --- |
| FR-4.1 | Reverse-chronological list of completed workouts showing date, name, duration and total volume. |
| FR-4.2 | Open any past workout to view all sets; edit or delete it. Edits recompute volume and PRs. |
| FR-4.3 | Per-exercise history: every set ever logged for that exercise, grouped by workout date. |
| FR-4.4 | Calendar view highlighting training days; tapping a day opens that workout. |

## 5. Progress and stats

Progress views are computed from logged sets; nothing is stored separately.

| ID | Requirement |
| --- | --- |
| FR-5.1 | Per-exercise charts over time: max weight, estimated 1RM (Epley: weight × (1 + reps / 30)), and total volume. Range selectable: 1M / 3M / 6M / 1Y / all. |
| FR-5.2 | Automatic personal record detection: heaviest weight, most reps at a given weight, best estimated 1RM. PRs are flagged during the workout and shown in history. |
| FR-5.3 | Weekly summary: number of workouts, total volume, and sets per muscle group for the current and previous week. |
| FR-5.4 | Optional body-weight log with a trend chart. |

## 6. Settings and data

With no cloud, JSON export is the only backup path, so it belongs in the MVP.

| ID | Requirement |
| --- | --- |
| FR-6.1 | Unit toggle (kg / lb), applied everywhere. Values are stored in one canonical unit and converted on display. |
| FR-6.2 | Default rest duration, first day of week (Mon / Sun), theme (light / dark / system). |
| FR-6.3 | Export all data (exercises, routines, workouts, settings) to a single JSON file via the system file picker. |
| FR-6.4 | Import from a JSON export. The user chooses merge or replace, with a confirmation before replace. |
| FR-6.5 | Clear all data, with a typed or two-step confirmation. |

## 7. Workout reminders

A scheduled workout triggers a notification before it starts so the user remembers to go.

| ID | Requirement |
| --- | --- |
| FR-7.1 | User can set a weekly workout schedule: one or more days of the week with a start time, each optionally linked to a routine. |
| FR-7.2 | Send a notification before each scheduled workout. Lead time defaults to 10 minutes and is configurable globally in Settings and per schedule entry. |
| FR-7.3 | Tapping the notification opens the app; if a routine is linked, it offers a one-tap "Start workout". |
| FR-7.4 | Notification actions: Start now, Snooze (default 10 min), Skip today. |
| FR-7.5 | No reminder is sent if a workout is already active or was completed earlier that day. |
| FR-7.6 | Reminders are rescheduled after device reboot and after any schedule change; they use exact alarms and request the notification permission on Android 13+. |
| FR-7.7 | Global on/off toggle for reminders in Settings, plus enable/disable per schedule entry. |

## 8. Scope: MVP vs Phase 2

Build logging end to end first; add analytics once logging feels solid.

| Area | MVP | Phase 2 |
| --- | --- | --- |
| Exercise library | FR-1.1 – 1.4 | FR-1.5 |
| Routines | FR-2.1 – 2.3 | FR-2.4 |
| Active workout | FR-3.1 – 3.9 | FR-3.10 |
| History | FR-4.1 – 4.3 | FR-4.4 |
| Progress | — | FR-5.1 – 5.4 |
| Settings & data | FR-6.1, 6.3, 6.5 | FR-6.2, 6.4 |
| Reminders | FR-7.1 – 7.3, 7.6, 7.7 | FR-7.4, 7.5 |

## 9. Non-functional requirements and stack

The app must be fully usable in airplane mode and stay responsive as history grows.

| ID | Requirement |
| --- | --- |
| NFR-1 | No network permission declared in the manifest. |
| NFR-2 | Workout and history screens stay responsive with 1,000+ logged workouts; lists are paged or lazy. |
| NFR-3 | Rest timer survives backgrounding and Doze (AlarmManager exact alarm or foreground service). |
| NFR-4 | Minimum Android 8.0 (API 26); target latest stable API. |
| NFR-5 | All screens support light and dark theme and TalkBack labels on interactive controls. |
| NFR-6 | Export file format is versioned so future imports can migrate older backups. |

**Suggested stack**

- Kotlin, Jetpack Compose, Material 3
- ViewModel + StateFlow, single-activity navigation (Navigation Compose)
- Persistence: Room, or Proto DataStore / JSON file if avoiding SQLite
- Timer: AlarmManager + NotificationManager; WorkManager for export
- Charts: Vico or a hand-rolled Compose Canvas chart
