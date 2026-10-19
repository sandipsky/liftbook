
Sep 25, 2026 · @Sandip

## Overview
App Name: LiftBook
A single-user Android workout tracker in Kotlin. All data lives on-device and every feature works offline; an optional account adds a per-user cloud backup of that data (§8), and nothing else goes online.

**Assumptions**

- One user per install. An account is optional (FR-8): without one, nothing leaves the phone.
- Network access is used only by the optional account and its cloud backup (NFR-1).
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
| FR-3.11 | Record a workout after the fact: offered beside Start wherever a workout starts, it is the same logging screen without the elapsed clock, the rest timer or per-set completion. Every set that is filled in is saved when the workout is; a recorded workout shows no duration. |

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

JSON export is the backup path that needs no account, so it belongs in the MVP. The cloud backup (FR-8.5) carries the same document.

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

## 8. Accounts and cloud backup

Optional: the app is complete without an account. Signing in adds one thing, a copy of the phone's data online under that account alone, so it survives the phone and moves to the next one.

| ID | Requirement |
| --- | --- |
| FR-8.1 | Create an account and sign in with an email and password. No email verification; the account is usable as soon as it's created. The app stays fully usable signed out. |
| FR-8.2 | Sign in with a Google account, through the phone's credential manager. |
| FR-8.3 | Reset a forgotten password by emailed link. |
| FR-8.4 | Change the password from inside the app; the current password is required. |
| FR-8.5 | Cloud backup: the signed-in account's own copy of the phone's data, as the FR-6.3 document, kept under that account's id where no other account can read or write it. Updated automatically once changes settle and no workout is in progress, and on demand; restored on demand. |
| FR-8.6 | The phone's data belongs to one account at a time. Signing in settles whose: an account with a backup is offered a restore, and another account's data is replaced only after the user confirms. Signing out keeps the data on the phone. |
| FR-8.7 | Automatic backup can be switched off on the Account page; it is on by default, and Back up now still works when it is off. |
| FR-8.8 | The first launch opens on the sign-in page, with Create account, Google, and Continue as guest. The page doesn't show again once passed; signing in stays available from Settings. |

## 9. Scope: MVP vs Phase 2

Build logging end to end first; add analytics once logging feels solid.

| Area | MVP | Phase 2 |
| --- | --- | --- |
| Exercise library | FR-1.1 – 1.4 | FR-1.5 |
| Routines | FR-2.1 – 2.3 | FR-2.4 |
| Active workout | FR-3.1 – 3.9, 3.11 | FR-3.10 |
| History | FR-4.1 – 4.3 | FR-4.4 |
| Progress | — | FR-5.1 – 5.4 |
| Settings & data | FR-6.1, 6.3, 6.5 | FR-6.2, 6.4 |
| Reminders | FR-7.1 – 7.3, 7.6, 7.7 | FR-7.4, 7.5 |
| Accounts & cloud backup | FR-8.1 – 8.8 | — |

## 10. Non-functional requirements and stack

The app must be fully usable in airplane mode, signed in or not, and stay responsive as history grows.

| ID | Requirement |
| --- | --- |
| NFR-1 | The network is used only for the optional account and its cloud backup (FR-8): no analytics, crash reporting, remote images or fonts. Every other feature works with no connection. |
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
