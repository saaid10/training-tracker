-- Training Tracker: migration for an ALREADY-LIVE Supabase project
-- --------------------------------------------------------------------
-- schema.sql uses `create table if not exists`, so on a brand-new Supabase
-- project it creates every table directly in the CURRENT (per-set,
-- typed-exercise) shape and this file is not needed.
--
-- On a project that was already live BEFORE the per-set/Bodyweight/Timed
-- logging redesign (requirements.txt's changelog notes push/restore sync
-- was verified against the live project prior to this redesign), the
-- old-shape `exercises`/`workout_logs` tables already exist, so
-- `create table if not exists` is a no-op for them: the new
-- `exercises.type` column and the reshaped `workout_logs.sets` column
-- never get created. Left as-is, SyncRepository's push starts failing
-- against the stale schema, and SyncWorker retries silently forever (no
-- user-facing error — backups just quietly stop working).
--
-- Run the statements below ONCE, manually (e.g. via the Supabase SQL
-- editor), against that existing project to bring it up to the shape
-- app/src/main/java/com/trainingtracker/app/data/remote/dto/Dtos.kt now
-- expects. This file is NOT executed by the app or by any build/CI step —
-- review it and back up the project before running it yourself.
--
-- Old shape being migrated FROM (see git history of this file / Dtos.kt
-- before commit 5988ec7, "Update Supabase DTOs and schema for exercise
-- type and per-set logs"):
--   exercises: (no `type` column)
--   workout_logs: weight_kg double precision not null, reps integer not
--                 null, sets integer not null (a SET COUNT, not the new
--                 JSON column), rpe double precision
--
-- New shape being migrated TO (matches Dtos.kt's ExerciseDto/WorkoutLogDto
-- exactly):
--   exercises: adds `type text not null default 'WEIGHTED'`
--   workout_logs: replaces weight_kg/reps/sets(count)/rpe with a single
--                 `sets text not null` column — JSON-encoded
--                 List<WorkoutSet>, the same encoding Room's
--                 Converters.kt produces locally.

begin;

-- 1. exercises.type — Postgres backfills existing rows with the DEFAULT
--    automatically when adding a NOT NULL column this way (no separate
--    UPDATE needed). WEIGHTED is correct for every pre-redesign row since
--    Bodyweight/Timed exercise types didn't exist before this migration.
alter table exercises
    add column if not exists type text not null default 'WEIGHTED';

-- 2. workout_logs.sets — the OLD column of this name is an integer set
--    COUNT, which collides with the NEW column of the same name (a JSON
--    TEXT blob), so the old one is renamed out of the way first instead
--    of being dropped, to avoid losing data before it's backfilled below.
alter table workout_logs
    rename column sets to sets_count_legacy;

alter table workout_logs
    add column if not exists sets text;

-- Backfill: reconstruct a List<WorkoutSet> JSON array per row from the old
-- flat columns, one array entry per unit of the old set count (preserving
-- total set count; the old shape had no per-set granularity to recover,
-- so every entry in a row's array repeats that row's single weight/reps/
-- rpe). durationSeconds is always null — TIMED exercises didn't exist
-- before this redesign, so no pre-existing row can be a timed hold.
update workout_logs w
set sets = coalesce(sub.sets_json, '[]')
from (
    select
        wl.id,
        '[' || string_agg(
            jsonb_build_object(
                'weightKg', wl.weight_kg,
                'reps', wl.reps,
                'durationSeconds', null,
                'rpe', wl.rpe
            )::text,
            ','
        ) || ']' as sets_json
    from workout_logs wl
    cross join lateral generate_series(1, greatest(wl.sets_count_legacy, 1))
    group by wl.id
) sub
where w.id = sub.id
  and w.sets is null;

alter table workout_logs
    alter column sets set not null;

-- 3. The old flat columns are no longer read or written by the app (see
--    Dtos.kt's WorkoutLogDto, which now only has `sets: String`). They're
--    kept here for safety — NOT NULL relaxed rather than dropped, so no
--    historical data is destroyed by this script — but future pushed rows
--    will never populate them (Postgrest upsert omits fields the DTO
--    doesn't declare), so they need to stop being required:
alter table workout_logs alter column weight_kg drop not null;
alter table workout_logs alter column reps drop not null;
alter table workout_logs alter column sets_count_legacy drop not null;
-- rpe was already nullable in the old shape.

-- Once you've confirmed (via the Supabase table editor or a SELECT) that
-- the backfilled `sets` column looks correct for your data, you may drop
-- the legacy columns entirely — NOT done automatically here:
--   alter table workout_logs
--     drop column weight_kg, drop column reps,
--     drop column rpe, drop column sets_count_legacy;

commit;
