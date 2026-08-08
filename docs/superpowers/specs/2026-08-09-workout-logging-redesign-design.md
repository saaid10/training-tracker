# Workout Logging Redesign — Design Spec

Date: 2026-08-09
Status: Approved (pending user review of this written spec)

## Problem

The current logging model (`WorkoutLog`: one `weightKg`/`reps`/`sets` triple per session)
cannot represent:

1. **Bodyweight exercises** (dips, pull-ups) where weight is optional.
2. **Timed exercises** (planks, farmer's carries) measured by duration, not reps.
3. **Individual sets with different weights/reps** in the same session (e.g. 70kg×8,
   then 80kg×6, 80kg×6) — today's single `sets: Int` field is just a multiplier assuming
   every set is identical.

Trying to force per-set data into the current single-weight form is also the direct cause
of the reported bug: nothing appears under History → Session Log after logging. The actual
root cause (confirmed by reading the code) is unrelated to exercise selection — it's a
silent failure in `LogWorkoutViewModel.logSession` (`ui/log/LogWorkoutViewModel.kt:28`):

```kotlin
if (exerciseId.isBlank() || weightKg < 0 || reps <= 0 || sets <= 0) return
```

This guard returns silently (no error message, no `onDone()`) whenever `reps` or `sets`
parses to `0` or less. The screen's own validation (`LogWorkoutScreen.kt:157-163`) only
rejects unparseable input (`null`), not zero values, so a `0` typed into Reps or Sets
passes screen validation, reaches the ViewModel, and is dropped with zero feedback — the
user sees no confirmation and no error, and naturally nothing shows up in History. This
directly violates requirements.txt 3n ("forms show an inline error message instead of
silently doing nothing on invalid/missing input"). The redesign below removes this guard
entirely rather than patching it, since per-set logging removes the concept of a
top-level `sets: Int` count to validate.

## Goals

- Support three exercise types: **Weighted** (current behavior), **Bodyweight**, **Timed**.
- Support per-set entries with independent weight, reps, duration, and RPE.
- Fix the silent-failure logging bug as part of the redesign (not as a separate patch).
- Keep the change additive to existing architecture (Room + Supabase backup sync,
  offline-first, manual DI via `AppContainer`) — no new architectural patterns introduced.

## Non-goals

- No SQL-level querying of individual sets (nothing in the app's requirements needs it).
- No change to the offline-first / backup-only sync model (requirements.txt 3i).
- No automated test suite added (project currently has none — see README "Not yet done").

## Data model

### `ExerciseType`

New enum on `Exercise`:

```kotlin
enum class ExerciseType { WEIGHTED, BODYWEIGHT, TIMED }
```

`Exercise.type: ExerciseType = ExerciseType.WEIGHTED` (default preserves current behavior
for existing exercises). Editable after creation, same as category/goal override, via a
3-way chip picker in the Exercise Library form (same visual pattern as the existing
Goal-override `FilterChip` row).

Type determines which fields a set exposes and which are required:

| Type | Weight | Reps | Duration |
|---|---|---|---|
| WEIGHTED | required | required | — |
| BODYWEIGHT | optional | required | — |
| TIMED | optional | — | required |

RPE is optional on every set regardless of type. For BODYWEIGHT, the weight field means
whatever number the user chooses to type (their bodyweight, bodyweight + added load, or
just the added load) — the app does not compute or track bodyweight itself.

### `WorkoutSet`

New plain value class (not a Room entity):

```kotlin
data class WorkoutSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val rpe: Double? = null,
)
```

### `WorkoutLog`

Drops `weightKg`, `reps`, `sets` (Int), `rpe`. Gains:

```kotlin
val sets: List<WorkoutSet>
```

Stored as a JSON string column via a Room `TypeConverter` (kotlinx.serialization, already
a project dependency for Supabase DTOs) — the same pattern this codebase already uses for
`Routine.daysOfWeek` (a converted list stored in one column). No new table, no new DAO, no
cascade-delete bookkeeping: editing a session replaces the whole `sets` list at once,
matching how the History edit dialog already treats a log as one editable unit.

### Migration

`AppContainer.kt` already uses `.fallbackToDestructiveMigration()` with an explicit
comment that this is intentional for a pre-release, single-user app — no shipped installs
to migrate. Bump `AppDatabase.version` from 2 to 3; no manual `Migration` object needed.

### Supabase schema (`supabase/schema.sql`)

`workout_logs` table: drop `weight_kg`, `reps`, `sets`, `rpe` columns; add `sets jsonb not
null`. `WorkoutLogDto` mirrors this with a `sets: String` field (the same JSON-encoded
string produced by the local Room `TypeConverter`) replacing the four dropped fields —
consistent encode/decode logic on both sides, no separate serialization path to maintain.

## Progress metric adaptation

`ProgressMetric` implementations (`domain/progress/ProgressMetric.kt`) currently read
`log.weightKg` / `log.reps` / `log.sets` directly. They're rewritten to use per-session
aggregate helpers computed from `log.sets: List<WorkoutSet>`:

- **Best set**: the set representing peak performance for the session.
  - WEIGHTED/BODYWEIGHT: highest estimated 1RM (null weight treated as 0).
  - TIMED: longest duration (ties broken by heavier weight).
  - Feeds the **Strength (1RM)** and **Autoregulated (RPE trend)** metrics — matching by
    weight+reps (WEIGHTED/BODYWEIGHT) or weight+duration (TIMED) across sessions.
- **Total volume** (Hypertrophy): Σ `weight × reps` per set for WEIGHTED/BODYWEIGHT; Σ
  `weight × duration` per set for TIMED (load × time-under-tension — matches the
  farmer's-carry case explicitly called out during design).
- **Total reps/duration** (Endurance): Σ reps for WEIGHTED/BODYWEIGHT; Σ duration for
  TIMED. This lines up with requirements.txt 3c's original Endurance definition ("max
  reps at a fixed/bodyweight load") for bodyweight exercises with no special-casing
  needed.
- **Simple comparison**: 3-field direct compare of (weight, reps, set-count) for
  WEIGHTED/BODYWEIGHT; (weight, duration, set-count) for TIMED.

For TIMED exercises, the Strength slot's estimated-1RM formulas (Epley/Brzycki/Lombardi/
O'Conner) don't apply — there's no reps value to plug in. They're replaced by a
**load × time score** (best set's `weight × duration`) with its own tooltip explaining
the substitution, rather than silently producing a meaningless number by misusing an
existing formula.

`ProgressMetrics.forGoal` gains an `exerciseType: ExerciseType` parameter so it can select
the TIMED-aware variant of the Strength metric.

## UI changes

- **Log Workout** (`LogWorkoutScreen.kt`): the single weight/reps/rpe row becomes a
  dynamic list of set rows. Each row renders only the fields valid for the selected
  exercise's type (per the table above). Actions: "+ Add set", per-row remove, and
  "duplicate last set" (prefills a new row from the previous one — convenience for
  same-weight sets). "Plan next session" deltas (`weightDelta`/`repsDelta`/`rpeDelta`)
  apply uniformly to every set in the generated TBD entry; `setsDelta` adds rows
  (duplicating the last set) or trims trailing rows for a negative delta.
- **History** (`HistoryScreen.kt`): the session-log list row and the edit dialog both
  render/edit the per-set list instead of single weight/reps/sets fields. The chart score
  (`chartScore`) reuses the same best-set/volume/endurance aggregate the active metric
  uses, so the graph and the color-coded trend stay consistent.
- **Pending** (`PendingScreen.kt`): the confirm dialog gets the same per-set list editor
  component as History's edit dialog (shared composable to avoid duplicating the
  dynamic-row logic).
- **Exercise Library** (`ExerciseLibraryScreen.kt`): create/edit form gains a 3-way
  `ExerciseType` chip picker (mirrors the existing Goal-override chip row), defaulting to
  WEIGHTED.

## Error handling

Each set row validates inline (per requirements.txt 3n: no silent no-ops):
- WEIGHTED: missing/invalid weight or reps → inline error on that field.
- BODYWEIGHT: missing/invalid reps → inline error (weight has no error state, it's
  optional).
- TIMED: missing/invalid duration → inline error (weight optional).

A session must have at least one set to submit; the "+ Add set" UI starts with one blank
row by default so there's always something to fill in or explicitly remove-then-readd.

## Testing

No automated test suite exists in this project yet (README: "No automated tests yet").
Consistent with existing project conventions, verification for this change is manual:
build the app, log a multi-set session for one exercise of each type (weighted,
bodyweight, timed), confirm it appears in History → Session Log with correct per-set
display, confirm the progress trend/chart reflect the new aggregate rules, and confirm
Pending confirm/edit round-trips a per-set entry correctly.

## Open follow-ups (out of scope for this spec)

- `requirements.txt` will be updated after implementation to reflect the new logging
  model as current-state (sections 3a/3b/3c touch exercise fields and log structure),
  with a changelog entry appended per the file's existing convention — not written as a
  before/after narrative.
