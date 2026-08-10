-- Training Tracker: Supabase backup schema
-- Mirrors the Room local database. Sync is single-device, backup-only
-- (see requirements.txt section 3i) so this schema has no multi-device
-- conflict resolution columns beyond updated_at/deleted for last-write-wins.
--
-- Timestamps are stored as bigint epoch-millis (not timestamptz) to match
-- Room's Long timestamps exactly, avoiding ISO8601/timezone conversion on
-- every sync.
--
-- `create table if not exists` below only creates tables in the CURRENT
-- shape on a brand-new project. If you already have a live project from
-- BEFORE the per-set/Bodyweight/Timed logging redesign, its old-shape
-- tables already exist and none of these `if not exists` clauses will
-- update them — see schema-migrations.sql (sibling file) for the manual
-- migration path.

create table if not exists categories (
    id text primary key,
    name text not null,
    is_custom boolean not null default false,
    updated_at bigint not null,
    deleted boolean not null default false
);

create table if not exists exercises (
    id text primary key,
    name text not null,
    category_id text not null references categories(id),
    goal_override text, -- null = inherit global default. One of: STRENGTH, HYPERTROPHY, ENDURANCE, AUTOREGULATED, SIMPLE
    type text not null default 'WEIGHTED', -- WEIGHTED, BODYWEIGHT, or TIMED — see WorkoutSet
    created_at bigint not null,
    updated_at bigint not null,
    deleted boolean not null default false
);

create table if not exists workout_logs (
    id text primary key,
    exercise_id text not null references exercises(id),
    logged_at bigint not null,
    sets text not null, -- JSON-encoded list of {weightKg, reps, durationSeconds, rpe}, one per set
    status text not null default 'COMPLETED', -- COMPLETED or TBD
    source_log_id text references workout_logs(id), -- the completed log a TBD entry was generated from
    notes text,
    updated_at bigint not null,
    deleted boolean not null default false
);

create table if not exists routines (
    id text primary key,
    name text not null,
    days_of_week text not null, -- comma-separated ints, 1=Mon..7=Sun
    reminder_hour integer not null,
    reminder_minute integer not null,
    exercise_ids text not null default '', -- comma-separated exercise ids covered by this routine
    enabled boolean not null default true,
    updated_at bigint not null,
    deleted boolean not null default false
);

create table if not exists app_settings (
    id text primary key default 'singleton',
    global_default_goal text not null default 'STRENGTH',
    rolling_average_window integer not null default 5,
    one_rep_max_formula text not null default 'EPLEY',
    updated_at bigint not null
);

-- requirements.txt 3m: body weight/body fat %/muscle mass % logging, independent of exercises.
create table if not exists body_metric_logs (
    id text primary key,
    logged_at bigint not null,
    weight_kg double precision,
    body_fat_percent double precision,
    muscle_mass_percent double precision,
    notes text,
    updated_at bigint not null,
    deleted boolean not null default false
);

-- Row Level Security: this app has exactly one user per Supabase project
-- (backup-only, single device per requirements.txt 3f/3i), so policies
-- simply require an authenticated (or matching anon) request rather than
-- per-row ownership filtering.
alter table categories enable row level security;
alter table exercises enable row level security;
alter table workout_logs enable row level security;
alter table routines enable row level security;
alter table app_settings enable row level security;
alter table body_metric_logs enable row level security;

create policy "allow all to authenticated" on categories for all using (true) with check (true);
create policy "allow all to authenticated" on exercises for all using (true) with check (true);
create policy "allow all to authenticated" on workout_logs for all using (true) with check (true);
create policy "allow all to authenticated" on routines for all using (true) with check (true);
create policy "allow all to authenticated" on app_settings for all using (true) with check (true);
create policy "allow all to authenticated" on body_metric_logs for all using (true) with check (true);

create index if not exists idx_workout_logs_exercise on workout_logs(exercise_id, logged_at desc);
create index if not exists idx_exercises_category on exercises(category_id);
create index if not exists idx_body_metric_logs_logged_at on body_metric_logs(logged_at desc);
