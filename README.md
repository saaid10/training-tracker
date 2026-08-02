# Training Tracker

Native Android (Kotlin + Jetpack Compose) workout tracker. Offline-first: Room is the
local source of truth; Supabase is a backup-only sync target. See `requirements.txt`
for the full decision log.

## Setup

1. Create a Supabase project, then run `supabase/schema.sql` in its SQL editor.
2. Copy `local.properties.example` to `local.properties` and fill in your project's
   URL and anon key (never commit this file — it's gitignored).
3. Open the `training-tracker/` folder in Android Studio (Ladybug or newer) and let it
   sync Gradle.
4. Run on a device/emulator with API 26+.

## Architecture

- `data/local` — Room entities, DAOs, database (offline source of truth)
- `data/remote` — Supabase client, DTOs, backup push/pull sync (`SyncWorker`)
- `data/repository` — repositories tying local data + sync together
- `data/settings` — DataStore-backed app settings (goal default, rolling window, formula)
- `domain/progress` — the 5 progress-calculation metrics + rolling-average comparison
- `reminders` — WorkManager-based fixed-schedule notifications
- `ui` — Compose screens + ViewModels, one package per screen, manual DI via `AppContainer`

## Not yet done

- App icon is a placeholder vector, not final artwork.
- No automated tests yet.
- Chart is a minimal custom Canvas line chart (no external charting library).
