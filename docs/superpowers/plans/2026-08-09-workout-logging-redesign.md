# Per-Set / Bodyweight / Timed Workout Logging — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single weight/reps/sets-count logging model with per-set entries (independent weight/reps per set), add Bodyweight and Timed exercise types, and fix the silent logging-failure bug as part of the same change.

**Architecture:** `WorkoutLog` keeps one row per session but its `weightKg/reps/sets/rpe` fields are replaced by `sets: List<WorkoutSet>`, JSON-encoded through a Room `TypeConverter` (mirrors how `Routine.daysOfWeek` already round-trips through a converter in this codebase). `Exercise` gains a `type: ExerciseType` (WEIGHTED/BODYWEIGHT/TIMED) that decides which fields a set requires. Progress metrics move from reading raw fields to shared, unit-tested aggregate functions (`WorkoutSetAggregates`) that are type-aware. A new shared Compose component (`SetListEditor`) replaces the duplicated weight/reps/rpe form fields in three screens.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room (KSP), kotlinx.serialization (already a dependency), JUnit4 (already declared via `testImplementation`, currently unused — no `app/src/test` directory exists yet).

**Design spec:** `docs/superpowers/specs/2026-08-09-workout-logging-redesign-design.md` (approved).

## Global Constraints

- kg only, no unit conversion (requirements.txt 3h).
- Offline-first: Room is the source of truth; Supabase is backup-only, single-device (requirements.txt 3i).
- `AppContainer.kt:21` already uses `.fallbackToDestructiveMigration()` intentionally (pre-release, single-user app, comment explains no shipped installs to migrate) — bump `AppDatabase.version` only, no `Migration` object.
- Every form must show an inline error message, never silently no-op on invalid/missing input (requirements.txt 3n). This is also the direct root cause of the reported bug (`LogWorkoutViewModel.kt:28`'s silent `reps <= 0 || sets <= 0` guard) — it is removed, not patched, because per-set logging removes the concept it was checking.
- No automated UI/DB test framework is being introduced (project has none — README: "No automated tests yet"). Pure-Kotlin domain logic (JSON converters, aggregate math, delta application) gets real JUnit4 unit tests since the dependency is already declared and these pieces need no Android runtime. Room/Compose-touching changes are verified by compiling (`./gradlew compileDebugKotlin`) and a manual smoke-test pass (Task 12).
- Follow existing project conventions: enums colocated with their primary entity file (e.g. `LogStatus` lives in `WorkoutLog.kt`), manual DI via `AppContainer`, one ViewModel/screen pair per feature package.

---

### Task 1: `ExerciseType`, `WorkoutSet`, and their Room converters

**Files:**
- Modify: `app/src/main/java/com/trainingtracker/app/data/local/entity/Exercise.kt`
- Create: `app/src/main/java/com/trainingtracker/app/data/local/entity/WorkoutSet.kt`
- Modify: `app/src/main/java/com/trainingtracker/app/data/local/Converters.kt`
- Test: `app/src/test/java/com/trainingtracker/app/data/local/ConvertersTest.kt`

**Interfaces:**
- Produces: `enum class ExerciseType { WEIGHTED, BODYWEIGHT, TIMED }` (in `entity/Exercise.kt`), `data class WorkoutSet(weightKg: Double?, reps: Int?, durationSeconds: Int?, rpe: Double?)` (in `entity/WorkoutSet.kt`), and four new `Converters` methods: `exerciseTypeToString`, `stringToExerciseType`, `workoutSetListToString`, `stringToWorkoutSetList`. Every later task depends on these exact names/types.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/trainingtracker/app/data/local/ConvertersTest.kt`:

```kotlin
package com.trainingtracker.app.data.local

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `round-trips an empty set list`() {
        val encoded = converters.workoutSetListToString(emptyList())
        assertEquals(emptyList<WorkoutSet>(), converters.stringToWorkoutSetList(encoded))
    }

    @Test
    fun `round-trips a list of sets with mixed weight, reps, duration, rpe`() {
        val sets = listOf(
            WorkoutSet(weightKg = 70.0, reps = 8, durationSeconds = null, rpe = 7.5),
            WorkoutSet(weightKg = 80.0, reps = 6, durationSeconds = null, rpe = null),
            WorkoutSet(weightKg = null, reps = null, durationSeconds = 45, rpe = 8.0),
        )
        val encoded = converters.workoutSetListToString(sets)
        assertEquals(sets, converters.stringToWorkoutSetList(encoded))
    }

    @Test
    fun `blank string decodes to an empty set list`() {
        assertEquals(emptyList<WorkoutSet>(), converters.stringToWorkoutSetList(""))
    }

    @Test
    fun `round-trips every exercise type`() {
        ExerciseType.entries.forEach { type ->
            assertEquals(type, converters.stringToExerciseType(converters.exerciseTypeToString(type)))
        }
    }
}
```

This won't compile yet — `ExerciseType`, `WorkoutSet`, and the four converter methods don't exist.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.trainingtracker.app.data.local.ConvertersTest"`
Expected: FAIL (compilation error — unresolved references `ExerciseType`, `WorkoutSet`, `workoutSetListToString`, etc.)

- [ ] **Step 3: Add `ExerciseType` to `Exercise.kt`**

In `app/src/main/java/com/trainingtracker/app/data/local/entity/Exercise.kt`, add the enum above the `Exercise` data class (same pattern as `LogStatus` living in `WorkoutLog.kt`) and add a `type` field with a default so existing call sites without it still compile temporarily during this task:

```kotlin
package com.trainingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trainingtracker.app.domain.model.Goal

enum class ExerciseType { WEIGHTED, BODYWEIGHT, TIMED }

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String,
    /** Null = inherit the app-wide default goal (see AppSettings.globalDefaultGoal). */
    val goalOverride: Goal?,
    /** Determines which fields a logged set exposes/requires — see WorkoutSet. */
    val type: ExerciseType = ExerciseType.WEIGHTED,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
```

- [ ] **Step 4: Create `WorkoutSet.kt`**

```kotlin
package com.trainingtracker.app.data.local.entity

import kotlinx.serialization.Serializable

/**
 * One set within a logged session. Which fields are populated depends on the exercise's
 * ExerciseType: WEIGHTED requires weightKg+reps, BODYWEIGHT requires reps (weightKg optional),
 * TIMED requires durationSeconds (weightKg optional, reps unused). rpe is always optional.
 */
@Serializable
data class WorkoutSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val rpe: Double? = null,
)
```

- [ ] **Step 5: Add the four converters**

Replace the full contents of `app/src/main/java/com/trainingtracker/app/data/local/Converters.kt`:

```kotlin
package com.trainingtracker.app.data.local

import androidx.room.TypeConverter
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.model.Goal
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun goalToString(goal: Goal?): String? = goal?.name

    @TypeConverter
    fun stringToGoal(value: String?): Goal? = value?.let { Goal.valueOf(it) }

    @TypeConverter
    fun logStatusToString(status: LogStatus): String = status.name

    @TypeConverter
    fun stringToLogStatus(value: String): LogStatus = LogStatus.valueOf(value)

    @TypeConverter
    fun intListToString(list: List<Int>): String = list.joinToString(",")

    @TypeConverter
    fun stringToIntList(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim().toInt() }

    @TypeConverter
    fun stringListToString(list: List<String>): String = list.joinToString(",")

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }

    @TypeConverter
    fun exerciseTypeToString(type: ExerciseType): String = type.name

    @TypeConverter
    fun stringToExerciseType(value: String): ExerciseType = ExerciseType.valueOf(value)

    @TypeConverter
    fun workoutSetListToString(list: List<WorkoutSet>): String = Json.encodeToString(list)

    @TypeConverter
    fun stringToWorkoutSetList(value: String): List<WorkoutSet> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(value)
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.trainingtracker.app.data.local.ConvertersTest"`
Expected: PASS (4 tests)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/data/local/entity/Exercise.kt app/src/main/java/com/trainingtracker/app/data/local/entity/WorkoutSet.kt app/src/main/java/com/trainingtracker/app/data/local/Converters.kt app/src/test/java/com/trainingtracker/app/data/local/ConvertersTest.kt
git commit -m "Add ExerciseType and WorkoutSet with Room JSON converters"
```

---

### Task 2: `WorkoutSetAggregates` — shared per-set aggregate math

**Files:**
- Create: `app/src/main/java/com/trainingtracker/app/domain/progress/WorkoutSetAggregates.kt`
- Test: `app/src/test/java/com/trainingtracker/app/domain/progress/WorkoutSetAggregatesTest.kt`

**Interfaces:**
- Consumes: `ExerciseType`, `WorkoutSet` (Task 1); `OneRepMax.estimate(formula, weightKg, reps): Double` and `OneRepMaxFormula` (existing, `domain/progress/OneRepMax.kt`, `domain/model/Goal.kt`).
- Produces: `object WorkoutSetAggregates` with `bestSet(sets, type, formula): WorkoutSet`, `bestSetScore(sets, type, formula): Double`, `topSetByWeight(sets, type): WorkoutSet`, `totalVolume(sets, type): Double`, `totalEndurance(sets, type): Double`, `repOrDuration(set, type): Int?`. Task 5 and Task 6 depend on these exact names.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/trainingtracker/app/domain/progress/WorkoutSetAggregatesTest.kt`:

```kotlin
package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.model.OneRepMaxFormula
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSetAggregatesTest {

    @Test
    fun `bestSet picks the set with the highest estimated 1RM, not just the heaviest weight`() {
        // Epley: 100kg x 1 = 103.33 est 1RM; 90kg x 5 = 105.0 est 1RM — the lighter set wins.
        val sets = listOf(
            WorkoutSet(weightKg = 100.0, reps = 1),
            WorkoutSet(weightKg = 90.0, reps = 5),
        )
        val best = WorkoutSetAggregates.bestSet(sets, ExerciseType.WEIGHTED, OneRepMaxFormula.EPLEY)
        assertEquals(90.0, best.weightKg)
        assertEquals(5, best.reps)
    }

    @Test
    fun `bestSet for timed exercises picks the longest duration`() {
        val sets = listOf(
            WorkoutSet(weightKg = 20.0, durationSeconds = 30),
            WorkoutSet(weightKg = 20.0, durationSeconds = 45),
        )
        val best = WorkoutSetAggregates.bestSet(sets, ExerciseType.TIMED, OneRepMaxFormula.EPLEY)
        assertEquals(45, best.durationSeconds)
    }

    @Test
    fun `bestSetScore for timed exercises is weight times duration`() {
        val sets = listOf(WorkoutSet(weightKg = 24.0, durationSeconds = 40))
        val score = WorkoutSetAggregates.bestSetScore(sets, ExerciseType.TIMED, OneRepMaxFormula.EPLEY)
        assertEquals(960.0, score, 0.001)
    }

    @Test
    fun `topSetByWeight picks the heaviest weight regardless of estimated 1RM`() {
        val sets = listOf(
            WorkoutSet(weightKg = 100.0, reps = 1),
            WorkoutSet(weightKg = 90.0, reps = 5),
        )
        val top = WorkoutSetAggregates.topSetByWeight(sets, ExerciseType.WEIGHTED)
        assertEquals(100.0, top.weightKg)
    }

    @Test
    fun `totalVolume sums weight times reps across all sets`() {
        val sets = listOf(
            WorkoutSet(weightKg = 70.0, reps = 8),
            WorkoutSet(weightKg = 80.0, reps = 6),
            WorkoutSet(weightKg = 80.0, reps = 6),
        )
        val volume = WorkoutSetAggregates.totalVolume(sets, ExerciseType.WEIGHTED)
        assertEquals(70.0 * 8 + 80.0 * 6 + 80.0 * 6, volume, 0.001)
    }

    @Test
    fun `totalVolume for timed exercises sums weight times duration`() {
        val sets = listOf(WorkoutSet(weightKg = 24.0, durationSeconds = 40), WorkoutSet(weightKg = 24.0, durationSeconds = 35))
        val volume = WorkoutSetAggregates.totalVolume(sets, ExerciseType.TIMED)
        assertEquals(24.0 * 40 + 24.0 * 35, volume, 0.001)
    }

    @Test
    fun `totalEndurance sums reps for weighted and duration for timed`() {
        val weighted = listOf(WorkoutSet(reps = 8), WorkoutSet(reps = 6), WorkoutSet(reps = 6))
        assertEquals(20.0, WorkoutSetAggregates.totalEndurance(weighted, ExerciseType.WEIGHTED), 0.001)

        val timed = listOf(WorkoutSet(durationSeconds = 40), WorkoutSet(durationSeconds = 35))
        assertEquals(75.0, WorkoutSetAggregates.totalEndurance(timed, ExerciseType.TIMED), 0.001)
    }

    @Test
    fun `bodyweight sets with null weight are treated as zero, not excluded`() {
        val sets = listOf(WorkoutSet(weightKg = null, reps = 12))
        assertEquals(0.0, WorkoutSetAggregates.totalVolume(sets, ExerciseType.BODYWEIGHT), 0.001)
        assertEquals(12.0, WorkoutSetAggregates.totalEndurance(sets, ExerciseType.BODYWEIGHT), 0.001)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.trainingtracker.app.domain.progress.WorkoutSetAggregatesTest"`
Expected: FAIL (compilation error — `WorkoutSetAggregates` doesn't exist)

- [ ] **Step 3: Implement `WorkoutSetAggregates`**

Create `app/src/main/java/com/trainingtracker/app/domain/progress/WorkoutSetAggregates.kt`:

```kotlin
package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.model.OneRepMaxFormula

/**
 * Pure aggregate helpers over a session's sets, used by every ProgressMetric so per-set logging
 * (independent weight/reps per set, optional weight for bodyweight/timed exercises) has one
 * shared, tested definition of "the session's number" instead of each metric reimplementing it.
 */
object WorkoutSetAggregates {

    /**
     * The set with the highest estimated 1RM (WEIGHTED/BODYWEIGHT) or the longest duration, tie
     * broken by heavier weight (TIMED). Used by the Strength and Autoregulated metrics, which are
     * explicitly formula-based.
     */
    fun bestSet(sets: List<WorkoutSet>, type: ExerciseType, formula: OneRepMaxFormula): WorkoutSet {
        require(sets.isNotEmpty()) { "A session must have at least one set" }
        return if (type == ExerciseType.TIMED) {
            sets.maxWithOrNull(compareBy({ it.durationSeconds ?: 0 }, { it.weightKg ?: 0.0 }))!!
        } else {
            sets.maxByOrNull { OneRepMax.estimate(formula, it.weightKg ?: 0.0, it.reps ?: 0) }!!
        }
    }

    /** [bestSet]'s score: est. 1RM (WEIGHTED/BODYWEIGHT) or weight x duration, i.e. load x time (TIMED). */
    fun bestSetScore(sets: List<WorkoutSet>, type: ExerciseType, formula: OneRepMaxFormula): Double {
        val best = bestSet(sets, type, formula)
        return if (type == ExerciseType.TIMED) {
            (best.weightKg ?: 0.0) * (best.durationSeconds ?: 0)
        } else {
            OneRepMax.estimate(formula, best.weightKg ?: 0.0, best.reps ?: 0)
        }
    }

    /**
     * The heaviest-weight set (WEIGHTED/BODYWEIGHT, ties broken by more reps) or longest-duration
     * set (TIMED, ties broken by heavier weight) — no formula involved. Used only by the Simple
     * Comparison metric, which is explicitly "no formula" per its own tooltip.
     */
    fun topSetByWeight(sets: List<WorkoutSet>, type: ExerciseType): WorkoutSet {
        require(sets.isNotEmpty()) { "A session must have at least one set" }
        return if (type == ExerciseType.TIMED) {
            sets.maxWithOrNull(compareBy({ it.durationSeconds ?: 0 }, { it.weightKg ?: 0.0 }))!!
        } else {
            sets.maxWithOrNull(compareBy({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }))!!
        }
    }

    /** Total volume: sum(weight x reps) for WEIGHTED/BODYWEIGHT; sum(weight x duration) for TIMED. */
    fun totalVolume(sets: List<WorkoutSet>, type: ExerciseType): Double = sets.sumOf { set ->
        val weight = set.weightKg ?: 0.0
        if (type == ExerciseType.TIMED) weight * (set.durationSeconds ?: 0) else weight * (set.reps ?: 0)
    }

    /** Total endurance quantity: sum(reps) for WEIGHTED/BODYWEIGHT; sum(duration seconds) for TIMED. */
    fun totalEndurance(sets: List<WorkoutSet>, type: ExerciseType): Double = sets.sumOf { set ->
        if (type == ExerciseType.TIMED) (set.durationSeconds ?: 0).toDouble() else (set.reps ?: 0).toDouble()
    }

    /** The reps-or-duration value of one set, matching the exercise's type — for matched-load comparisons. */
    fun repOrDuration(set: WorkoutSet, type: ExerciseType): Int? =
        if (type == ExerciseType.TIMED) set.durationSeconds else set.reps
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.trainingtracker.app.domain.progress.WorkoutSetAggregatesTest"`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/domain/progress/WorkoutSetAggregates.kt app/src/test/java/com/trainingtracker/app/domain/progress/WorkoutSetAggregatesTest.kt
git commit -m "Add WorkoutSetAggregates for type-aware per-set progress math"
```

---

### Task 3: `WorkoutLog.sets`, `AppDatabase` version bump

**Files:**
- Modify: `app/src/main/java/com/trainingtracker/app/data/local/entity/WorkoutLog.kt`
- Modify: `app/src/main/java/com/trainingtracker/app/data/local/AppDatabase.kt:19`

**Interfaces:**
- Consumes: `WorkoutSet` (Task 1).
- Produces: `WorkoutLog(id, exerciseId, loggedAt, sets: List<WorkoutSet>, status, sourceLogId, notes, updatedAt, deleted)` — this exact field order/set is what every later task's `WorkoutLog(...)` constructor calls and `.copy(...)` calls must match.

No DAO changes needed: `WorkoutLogDao` and `ExerciseDao` (`data/local/dao/`) both use `SELECT *` and Room maps the new/changed columns automatically via the Task 1 converters.

- [ ] **Step 1: Replace `WorkoutLog.kt`**

```kotlin
package com.trainingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogStatus { COMPLETED, TBD }

@Entity(tableName = "workout_logs")
data class WorkoutLog(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val loggedAt: Long,
    /** One or more sets, each with independent weight/reps/duration/RPE. Stored as JSON — see Converters. */
    val sets: List<WorkoutSet>,
    val status: LogStatus,
    /** For a TBD entry: the completed log it was suggested from. Null for completed logs. */
    val sourceLogId: String?,
    val notes: String?,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
```

- [ ] **Step 2: Bump the database version**

In `app/src/main/java/com/trainingtracker/app/data/local/AppDatabase.kt:19`, change:

```kotlin
    version = 2,
```

to:

```kotlin
    version = 3,
```

- [ ] **Step 3: Verify it compiles (expect downstream errors — that's fine at this point)**

Run: `./gradlew compileDebugKotlin`
Expected: FAIL — `LogRepository.kt`, `ProgressMetric.kt`, `Dtos.kt`, and the UI screens still reference the old `weightKg/reps/sets/rpe` fields. This confirms Task 3's change took effect; those call sites get fixed in Tasks 5–11. Do not attempt to fix them here.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/data/local/entity/WorkoutLog.kt app/src/main/java/com/trainingtracker/app/data/local/AppDatabase.kt
git commit -m "Replace WorkoutLog's flat weight/reps/sets/rpe with a per-set list"
```

---

### Task 4: Exercise type in repository, Exercise Library UI

**Files:**
- Modify: `app/src/main/java/com/trainingtracker/app/data/repository/ExerciseRepository.kt`
- Modify: `app/src/main/java/com/trainingtracker/app/ui/library/ExerciseLibraryViewModel.kt`
- Modify: `app/src/main/java/com/trainingtracker/app/ui/library/ExerciseLibraryScreen.kt`

**Interfaces:**
- Consumes: `ExerciseType` (Task 1).
- Produces: `ExerciseRepository.create(name, categoryId, goalOverride, type: ExerciseType): Exercise`; `ExerciseLibraryViewModel.createExercise(name, categoryId, goalOverride, type: ExerciseType)` and `.updateExercise(exercise, name, categoryId, goalOverride, type: ExerciseType)`.

- [ ] **Step 1: Update `ExerciseRepository.kt`**

```kotlin
package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.dao.ExerciseDao
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.domain.model.Goal
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Exercises are created once and reused from a searchable list when logging (requirements.txt 3a). */
class ExerciseRepository(private val dao: ExerciseDao) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()

    fun observeById(id: String): Flow<Exercise?> = dao.observeById(id)

    suspend fun getById(id: String): Exercise? = dao.getById(id)

    suspend fun create(name: String, categoryId: String, goalOverride: Goal?, type: ExerciseType): Exercise {
        val now = System.currentTimeMillis()
        val exercise = Exercise(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            categoryId = categoryId,
            goalOverride = goalOverride,
            type = type,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(exercise)
        return exercise
    }

    suspend fun update(exercise: Exercise) {
        dao.update(exercise.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }
}
```

- [ ] **Step 2: Update `ExerciseLibraryViewModel.kt`**

```kotlin
package com.trainingtracker.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Category
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.domain.model.Goal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseLibraryState(val exercises: List<Exercise> = emptyList(), val categories: List<Category> = emptyList())

class ExerciseLibraryViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<ExerciseLibraryState> = combine(
        container.exerciseRepository.observeAll(),
        container.categoryRepository.observeAll(),
    ) { exercises, categories -> ExerciseLibraryState(exercises, categories) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseLibraryState())

    fun createExercise(name: String, categoryId: String, goalOverride: Goal?, type: ExerciseType) {
        if (name.isBlank() || categoryId.isBlank()) return
        viewModelScope.launch { container.exerciseRepository.create(name, categoryId, goalOverride, type) }
    }

    fun updateExercise(exercise: Exercise, name: String, categoryId: String, goalOverride: Goal?, type: ExerciseType) {
        if (name.isBlank() || categoryId.isBlank()) return
        viewModelScope.launch {
            container.exerciseRepository.update(
                exercise.copy(name = name.trim(), categoryId = categoryId, goalOverride = goalOverride, type = type)
            )
        }
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { container.categoryRepository.addCustom(name) }
    }

    fun deleteExercise(id: String) {
        viewModelScope.launch { container.exerciseRepository.delete(id) }
    }
}
```

- [ ] **Step 3: Replace `ExerciseLibraryScreen.kt`**

```kotlin
package com.trainingtracker.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainingtracker.app.data.local.entity.Category
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.domain.model.Goal
import com.trainingtracker.app.ui.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseLibraryScreen(factory: ViewModelFactory, onBack: () -> Unit) {
    val viewModel: ExerciseLibraryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var deletingExercise by remember { mutableStateOf<Exercise?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New exercise")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Exercise Library",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp),
            )
            Text(
                "Tap an exercise to rename it or change its category/goal/type.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn {
                items(state.exercises, key = { it.id }) { exercise ->
                    val categoryName = state.categories.firstOrNull { it.id == exercise.categoryId }?.name ?: "—"
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            Text(
                                "$categoryName · ${exercise.type.name}" +
                                    (exercise.goalOverride?.let { " · goal: ${it.name}" } ?: "")
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { deletingExercise = exercise }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        },
                        modifier = Modifier.clickable { editingExercise = exercise },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ExerciseFormDialog(
            title = "New Exercise",
            initial = null,
            categories = state.categories,
            onDismiss = { showCreateDialog = false },
            onAddCategory = viewModel::addCategory,
            onSubmit = { name, categoryId, goal, type ->
                viewModel.createExercise(name, categoryId, goal, type)
                showCreateDialog = false
            },
        )
    }

    editingExercise?.let { exercise ->
        ExerciseFormDialog(
            title = "Edit Exercise",
            initial = exercise,
            categories = state.categories,
            onDismiss = { editingExercise = null },
            onAddCategory = viewModel::addCategory,
            onSubmit = { name, categoryId, goal, type ->
                viewModel.updateExercise(exercise, name, categoryId, goal, type)
                editingExercise = null
            },
        )
    }

    deletingExercise?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deletingExercise = null },
            title = { Text("Delete ${exercise.name}?") },
            text = { Text("Its logged history stays in History & Graphs, but it will no longer show up here or when logging a new session.") },
            confirmButton = {
                Button(onClick = { viewModel.deleteExercise(exercise.id); deletingExercise = null }) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deletingExercise = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExerciseFormDialog(
    title: String,
    initial: Exercise?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit,
    onSubmit: (String, String, Goal?, ExerciseType) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedCategoryId by remember { mutableStateOf(initial?.categoryId ?: categories.firstOrNull()?.id ?: "") }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedGoal by remember { mutableStateOf(initial?.goalOverride) }
    var selectedType by remember { mutableStateOf(initial?.type ?: ExerciseType.WEIGHTED) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Bench Press)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Select category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    categoryMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Add custom category") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAddCategory(newCategoryName); newCategoryName = "" }) {
                        Text("Add")
                    }
                }

                Text("Exercise type", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExerciseType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.name) },
                        )
                    }
                }

                Text("Goal override (optional)", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedGoal == null,
                        onClick = { selectedGoal = null },
                        label = { Text("Use default") },
                    )
                    Goal.entries.forEach { goal ->
                        FilterChip(
                            selected = selectedGoal == goal,
                            onClick = { selectedGoal = goal },
                            label = { Text(goal.name) },
                        )
                    }
                }

                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                errorMessage = when {
                    name.isBlank() -> "Enter a name"
                    selectedCategoryId.isBlank() -> "Select a category"
                    else -> null
                }
                if (errorMessage != null) return@Button
                onSubmit(name, selectedCategoryId, selectedGoal, selectedType)
            }) { Text(if (initial == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

- [ ] **Step 4: Verify it compiles in isolation**

Run: `./gradlew compileDebugKotlin`
Expected: still FAIL on `LogRepository.kt`/`ProgressMetric.kt`/`Dtos.kt`/other screens (unchanged until their tasks) — but no NEW errors should appear in `ExerciseRepository.kt`, `ExerciseLibraryViewModel.kt`, or `ExerciseLibraryScreen.kt`. Skim the compiler output to confirm those three files aren't listed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/data/repository/ExerciseRepository.kt app/src/main/java/com/trainingtracker/app/ui/library/ExerciseLibraryViewModel.kt app/src/main/java/com/trainingtracker/app/ui/library/ExerciseLibraryScreen.kt
git commit -m "Add exercise type picker to Exercise Library"
```

---

### Task 5: `LogRepository` — per-set CRUD and adjustment logic

**Files:**
- Modify: `app/src/main/java/com/trainingtracker/app/data/repository/LogRepository.kt`
- Test: `app/src/test/java/com/trainingtracker/app/data/repository/LogRepositoryAdjustmentTest.kt`

**Interfaces:**
- Consumes: `WorkoutSet`, `ExerciseType` (Task 1); `WorkoutLog` (Task 3).
- Produces: `NextSessionAdjustment(weightDeltaKg, repsDelta, setsDelta, rpeDelta)` (renamed semantics: `repsDelta` also serves as the duration-delta for TIMED exercises); `internal fun applyAdjustment(sets, exerciseType, adjustment): List<WorkoutSet>`; `LogRepository.logCompleted(exerciseId, exerciseType: ExerciseType, sets: List<WorkoutSet>, notes, nextSession, loggedAt): WorkoutLog`; `.confirmPending(id, sets: List<WorkoutSet>, notes)`; `.updateCompleted(id, sets: List<WorkoutSet>, notes, loggedAt)`. Tasks 9, 10, 11 call these exact signatures.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/trainingtracker/app/data/repository/LogRepositoryAdjustmentTest.kt`:

```kotlin
package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

class LogRepositoryAdjustmentTest {

    @Test
    fun `applies weight and reps deltas to every set uniformly`() {
        val sets = listOf(WorkoutSet(weightKg = 70.0, reps = 8), WorkoutSet(weightKg = 80.0, reps = 6))
        val result = applyAdjustment(sets, ExerciseType.WEIGHTED, NextSessionAdjustment(weightDeltaKg = 2.5, repsDelta = 1))
        assertEquals(listOf(WorkoutSet(weightKg = 72.5, reps = 9), WorkoutSet(weightKg = 82.5, reps = 7)), result)
    }

    @Test
    fun `applies reps delta to duration instead, for timed exercises`() {
        val sets = listOf(WorkoutSet(weightKg = 20.0, durationSeconds = 30))
        val result = applyAdjustment(sets, ExerciseType.TIMED, NextSessionAdjustment(repsDelta = 5))
        assertEquals(35, result.single().durationSeconds)
    }

    @Test
    fun `positive sets delta duplicates the last set`() {
        val sets = listOf(WorkoutSet(weightKg = 70.0, reps = 8))
        val result = applyAdjustment(sets, ExerciseType.WEIGHTED, NextSessionAdjustment(setsDelta = 2))
        assertEquals(3, result.size)
        assertEquals(WorkoutSet(weightKg = 70.0, reps = 8), result.last())
    }

    @Test
    fun `negative sets delta trims trailing sets but always keeps at least one`() {
        val sets = listOf(WorkoutSet(reps = 8), WorkoutSet(reps = 8), WorkoutSet(reps = 8))
        val result = applyAdjustment(sets, ExerciseType.WEIGHTED, NextSessionAdjustment(setsDelta = -5))
        assertEquals(1, result.size)
    }

    @Test
    fun `null deltas leave sets unchanged`() {
        val sets = listOf(WorkoutSet(weightKg = 70.0, reps = 8, rpe = 7.0))
        val result = applyAdjustment(sets, ExerciseType.WEIGHTED, NextSessionAdjustment())
        assertEquals(sets, result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.trainingtracker.app.data.repository.LogRepositoryAdjustmentTest"`
Expected: FAIL (compilation error — `applyAdjustment` and the new `NextSessionAdjustment` fields don't exist yet)

- [ ] **Step 3: Replace `LogRepository.kt`**

```kotlin
package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.dao.ExerciseDao
import com.trainingtracker.app.data.local.dao.WorkoutLogDao
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.data.settings.SettingsRepository
import com.trainingtracker.app.domain.progress.ProgressCalculator
import com.trainingtracker.app.domain.progress.ProgressResult
import com.trainingtracker.app.domain.progress.ProgressTrend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/**
 * Increments requested when confirming a completed log, used to auto-generate the next TBD
 * session (requirements.txt 3b). Any field left null means "keep the same as this session".
 * Applied uniformly to every set in the generated TBD entry.
 */
data class NextSessionAdjustment(
    val weightDeltaKg: Double? = null,
    /** Also serves as the duration-delta (seconds) for TIMED exercises' sets. */
    val repsDelta: Int? = null,
    val setsDelta: Int? = null,
    val rpeDelta: Double? = null,
)

/**
 * Applies [adjustment] to every set, then adds (duplicating the last set) or trims trailing sets
 * per [NextSessionAdjustment.setsDelta]. Always leaves at least one set. Pure and unit-tested
 * independently of the DAOs this repository otherwise depends on.
 */
internal fun applyAdjustment(
    sets: List<WorkoutSet>,
    exerciseType: ExerciseType,
    adjustment: NextSessionAdjustment,
): List<WorkoutSet> {
    val bumped = sets.map { set ->
        set.copy(
            weightKg = set.weightKg?.let { it + (adjustment.weightDeltaKg ?: 0.0) },
            reps = if (exerciseType == ExerciseType.TIMED) set.reps else set.reps?.let { it + (adjustment.repsDelta ?: 0) },
            durationSeconds = if (exerciseType == ExerciseType.TIMED) {
                set.durationSeconds?.let { it + (adjustment.repsDelta ?: 0) }
            } else {
                set.durationSeconds
            },
            rpe = set.rpe?.let { it + (adjustment.rpeDelta ?: 0.0) },
        )
    }
    val delta = adjustment.setsDelta ?: 0
    return when {
        delta > 0 -> bumped + List(delta) { bumped.last() }
        delta < 0 -> bumped.dropLast(minOf(-delta, bumped.size - 1))
        else -> bumped
    }
}

class LogRepository(
    private val logDao: WorkoutLogDao,
    private val exerciseDao: ExerciseDao,
    private val settingsRepository: SettingsRepository,
) {
    fun observeCompletedForExercise(exerciseId: String): Flow<List<WorkoutLog>> =
        logDao.observeCompletedForExercise(exerciseId)

    fun observePending(): Flow<List<WorkoutLog>> = logDao.observePending()

    suspend fun getById(id: String): WorkoutLog? = logDao.getById(id)

    /**
     * Logs a completed session. If [nextSession] is non-null, also creates a TBD entry for the
     * next workout with the requested increments already applied — the user just has to confirm
     * it later from the Pending screen instead of re-entering everything (requirements.txt 3b).
     */
    suspend fun logCompleted(
        exerciseId: String,
        exerciseType: ExerciseType,
        sets: List<WorkoutSet>,
        notes: String?,
        nextSession: NextSessionAdjustment? = null,
        /** Defaults to right now, but can be backdated (e.g. logging yesterday's forgotten session). */
        loggedAt: Long = System.currentTimeMillis(),
    ): WorkoutLog {
        val now = System.currentTimeMillis()
        val completed = WorkoutLog(
            id = UUID.randomUUID().toString(),
            exerciseId = exerciseId,
            loggedAt = loggedAt,
            sets = sets,
            status = LogStatus.COMPLETED,
            sourceLogId = null,
            notes = notes,
            updatedAt = now,
        )
        logDao.upsert(completed)

        if (nextSession != null) {
            val tbd = WorkoutLog(
                id = UUID.randomUUID().toString(),
                exerciseId = exerciseId,
                loggedAt = loggedAt, // placeholder until the routine's actual scheduled date/confirmation
                sets = applyAdjustment(sets, exerciseType, nextSession),
                status = LogStatus.TBD,
                sourceLogId = completed.id,
                notes = null,
                updatedAt = now,
            )
            logDao.upsert(tbd)
        }
        return completed
    }

    /**
     * Confirms a TBD entry from the Pending screen (requirements.txt 3g): the user reviews/edits
     * the auto-suggested numbers, then it becomes a real COMPLETED log for today.
     */
    suspend fun confirmPending(id: String, sets: List<WorkoutSet>, notes: String?) {
        val existing = logDao.getById(id) ?: return
        logDao.update(
            existing.copy(
                loggedAt = System.currentTimeMillis(),
                sets = sets,
                status = LogStatus.COMPLETED,
                notes = notes,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Discards a suggested TBD session the user doesn't want to keep. */
    suspend fun discardPending(id: String) {
        logDao.softDelete(id, System.currentTimeMillis())
    }

    /** Corrects a mistake in an already-completed log (e.g. wrong weight/reps entered). */
    suspend fun updateCompleted(id: String, sets: List<WorkoutSet>, notes: String?, loggedAt: Long) {
        val existing = logDao.getById(id) ?: return
        logDao.update(
            existing.copy(
                loggedAt = loggedAt,
                sets = sets,
                notes = notes,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Progress result for one exercise, per requirements.txt 3c (goal-based, 5-session rolling average). */
    fun observeProgress(exerciseId: String): Flow<ProgressResult> {
        val exerciseFlow = exerciseDao.observeById(exerciseId)
        val logsFlow = logDao.observeCompletedForExercise(exerciseId)
        return combine(
            exerciseFlow,
            logsFlow,
            settingsRepository.globalDefaultGoal,
            settingsRepository.rollingAverageWindow,
            settingsRepository.oneRepMaxFormula,
        ) { exercise, logs, globalGoal, window, formula ->
            if (exercise == null) {
                ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "Exercise not found.")
            } else {
                ProgressCalculator.evaluate(exercise, logs, globalGoal, window, formula)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.trainingtracker.app.data.repository.LogRepositoryAdjustmentTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/data/repository/LogRepository.kt app/src/test/java/com/trainingtracker/app/data/repository/LogRepositoryAdjustmentTest.kt
git commit -m "Move LogRepository to per-set CRUD and type-aware next-session deltas"
```

---

### Task 6: `ProgressMetric` and `ProgressCalculator` — type-aware metrics

**Files:**
- Modify: `app/src/main/java/com/trainingtracker/app/domain/progress/ProgressMetric.kt`
- Modify: `app/src/main/java/com/trainingtracker/app/domain/progress/ProgressCalculator.kt`
- Test: `app/src/test/java/com/trainingtracker/app/domain/progress/ProgressMetricTest.kt`

**Interfaces:**
- Consumes: `WorkoutSetAggregates` (Task 2); `WorkoutLog`, `WorkoutSet`, `ExerciseType` (Tasks 1/3); `Exercise.type` (Task 1).
- Produces: `ProgressMetrics.forGoal(goal: Goal, formula: OneRepMaxFormula, exerciseType: ExerciseType): ProgressMetric` and `.all(formula, exerciseType): List<ProgressMetric>` — the `ProgressMetric` interface itself (`evaluate`, `chartScore`, `displayName`, `tooltip`, `goal`) is unchanged. Tasks 10 (`HistoryViewModel`) call `forGoal` with this exact 3-arg signature.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/trainingtracker/app/domain/progress/ProgressMetricTest.kt`:

```kotlin
package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.model.OneRepMaxFormula
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun log(sets: List<WorkoutSet>, loggedAt: Long = 0L) = WorkoutLog(
    id = "id-$loggedAt", exerciseId = "ex", loggedAt = loggedAt, sets = sets,
    status = LogStatus.COMPLETED, sourceLogId = null, notes = null, updatedAt = loggedAt,
)

class ProgressMetricTest {

    @Test
    fun `OneRepMaxMetric reports progress when best-set score rises above baseline`() {
        val metric = OneRepMaxMetric(OneRepMaxFormula.EPLEY, ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 100.0, reps = 5)))
        val prior = listOf(log(listOf(WorkoutSet(weightKg = 90.0, reps = 5))))
        val result = metric.evaluate(current, prior)
        assertEquals(ProgressTrend.PROGRESSED, result.trend)
    }

    @Test
    fun `OneRepMaxMetric for timed exercises scores best set as weight times duration`() {
        val metric = OneRepMaxMetric(OneRepMaxFormula.EPLEY, ExerciseType.TIMED)
        val current = log(listOf(WorkoutSet(weightKg = 24.0, durationSeconds = 40)))
        assertEquals(960.0, metric.chartScore(current), 0.001)
    }

    @Test
    fun `VolumeMetric sums weight times reps across all sets`() {
        val metric = VolumeMetric(ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 70.0, reps = 8), WorkoutSet(weightKg = 80.0, reps = 6)))
        assertEquals(70.0 * 8 + 80.0 * 6, metric.chartScore(current), 0.001)
    }

    @Test
    fun `EnduranceMetric for bodyweight exercises sums reps even with no weight`() {
        val metric = EnduranceMetric(ExerciseType.BODYWEIGHT)
        val current = log(listOf(WorkoutSet(reps = 12), WorkoutSet(reps = 10)))
        assertEquals(22.0, metric.chartScore(current), 0.001)
    }

    @Test
    fun `AutoregulatedMetric finds a lower RPE at the same weight and reps as progress`() {
        val metric = AutoregulatedMetric(OneRepMaxFormula.EPLEY, ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 100.0, reps = 5, rpe = 7.0)))
        val prior = listOf(log(listOf(WorkoutSet(weightKg = 100.0, reps = 5, rpe = 8.5))))
        val result = metric.evaluate(current, prior)
        assertEquals(ProgressTrend.PROGRESSED, result.trend)
    }

    @Test
    fun `AutoregulatedMetric falls back to best-set score when no matching load exists`() {
        val metric = AutoregulatedMetric(OneRepMaxFormula.EPLEY, ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 100.0, reps = 5, rpe = 7.0)))
        val prior = listOf(log(listOf(WorkoutSet(weightKg = 90.0, reps = 5, rpe = 8.0))))
        val result = metric.evaluate(current, prior)
        assertTrue(result.explanation.contains("No matching load history"))
    }

    @Test
    fun `SimpleComparisonMetric flags progress when 2 of 3 fields improve`() {
        val metric = SimpleComparisonMetric(ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 100.0, reps = 5), WorkoutSet(weightKg = 90.0, reps = 5)))
        val prior = listOf(
            log(listOf(WorkoutSet(weightKg = 90.0, reps = 5))),
            log(listOf(WorkoutSet(weightKg = 90.0, reps = 5))),
        )
        // current top set (100kg) beats avg top-set weight (90kg), and set count (2) beats avg (1) -> 2/3 improved
        val result = metric.evaluate(current, prior)
        assertEquals(ProgressTrend.PROGRESSED, result.trend)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.trainingtracker.app.domain.progress.ProgressMetricTest"`
Expected: FAIL (compilation error — the metric classes still take a `WorkoutLog` with `weightKg/reps/sets/rpe`, not `sets: List<WorkoutSet>`, and don't accept an `exerciseType` constructor param yet)

- [ ] **Step 3: Replace `ProgressMetric.kt`**

```kotlin
package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.domain.model.Goal
import com.trainingtracker.app.domain.model.OneRepMaxFormula
import kotlin.math.abs

/** A tolerance band below which a change is considered noise, not real progress/regress. */
private const val NEUTRAL_BAND_PCT = 0.01 // 1%

interface ProgressMetric {
    val goal: Goal
    val displayName: String

    /** Shown in the History & Graphs screen's info tooltip. */
    val tooltip: String

    /**
     * @param current the just-logged (most recent) completed session for this exercise
     * @param priorSessions the previous sessions of the SAME exercise, newest first, already
     *   limited to the configured rolling-average window (see requirements.txt 3c)
     */
    fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult

    /** A single plottable number per session, for the History & Graphs chart. */
    fun chartScore(log: WorkoutLog): Double
}

private fun trendFromRelativeDiff(current: Double, baseline: Double): ProgressTrend {
    if (baseline == 0.0) return if (current > 0) ProgressTrend.PROGRESSED else ProgressTrend.NEUTRAL
    val relativeDiff = (current - baseline) / abs(baseline)
    return when {
        relativeDiff > NEUTRAL_BAND_PCT -> ProgressTrend.PROGRESSED
        relativeDiff < -NEUTRAL_BAND_PCT -> ProgressTrend.REGRESSED
        else -> ProgressTrend.NEUTRAL
    }
}

/**
 * Goal: Strength — the session's best set's estimated 1RM, compared to the rolling-average
 * baseline. For TIMED exercises (no rep count to plug into a 1RM formula), substitutes a
 * load x time (weight x duration) score for the best set instead.
 */
class OneRepMaxMetric(private val formula: OneRepMaxFormula, private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.STRENGTH
    override val displayName =
        if (exerciseType == ExerciseType.TIMED) "Best Set Load x Time"
        else "Estimated 1RM (${formula.name.lowercase().replaceFirstChar { it.uppercase() }})"
    override val tooltip: String
        get() = if (exerciseType == ExerciseType.TIMED) {
            "Estimated-1RM formulas need a rep count, which timed exercises don't have. Instead this " +
                "tracks your best set's load x time (weight x seconds held) — the same 'best single " +
                "set this session' idea, applied to a timed hold/carry instead of a rep max."
        } else {
            OneRepMax.description(formula)
        }

    override fun chartScore(log: WorkoutLog) = WorkoutSetAggregates.bestSetScore(log.sets, exerciseType, formula)

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentScore = chartScore(current)
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, currentScore, null, "No prior sessions yet.")
        }
        val baseline = priorSessions.map { chartScore(it) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        val label = if (exerciseType == ExerciseType.TIMED) "Best set load x time" else "Est. 1RM"
        return ProgressResult(
            trend, currentScore, baseline,
            "$label %.1f vs avg %.1f over last %d session(s)".format(currentScore, baseline, priorSessions.size),
        )
    }
}

/** Goal: Hypertrophy — total volume load per session (sum of weight x reps, or weight x duration for TIMED). */
class VolumeMetric(private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.HYPERTROPHY
    override val displayName = "Volume Load"
    override val tooltip =
        if (exerciseType == ExerciseType.TIMED) {
            "Volume load = sum of (weight x seconds held) across all sets. Tracks total load-time " +
                "under tension — a good fit for muscle-growth (hypertrophy) goals on timed holds/carries."
        } else {
            "Volume load = sets x reps x weight. The total amount of weight moved in the session. " +
                "Tracks work capacity — a good fit for muscle-growth (hypertrophy) goals."
        }

    override fun chartScore(log: WorkoutLog) = WorkoutSetAggregates.totalVolume(log.sets, exerciseType)

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentScore = chartScore(current)
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, currentScore, null, "No prior sessions yet.")
        }
        val baseline = priorSessions.map { chartScore(it) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "Volume %.0f vs avg %.0f over last %d session(s)".format(currentScore, baseline, priorSessions.size),
        )
    }
}

/** Goal: Muscular Endurance — total reps (or total seconds held, for TIMED) per session. */
class EnduranceMetric(private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.ENDURANCE
    override val displayName = if (exerciseType == ExerciseType.TIMED) "Total Time Held (Endurance)" else "Total Reps (Endurance)"
    override val tooltip =
        if (exerciseType == ExerciseType.TIMED) {
            "Total time held = sum of every set's duration. Tracks how long you can sustain the " +
                "hold/carry in total across the session."
        } else {
            "Total reps = sum of every set's reps. Tracks how many total repetitions you can perform. " +
                "Best for muscular-endurance goals, including bodyweight exercises at a fixed load, " +
                "where doing more reps matters more than lifting heavier."
        }

    override fun chartScore(log: WorkoutLog) = WorkoutSetAggregates.totalEndurance(log.sets, exerciseType)

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentScore = chartScore(current)
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, currentScore, null, "No prior sessions yet.")
        }
        val baseline = priorSessions.map { chartScore(it) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "%.0f vs avg %.1f over last %d session(s)".format(currentScore, baseline, priorSessions.size),
        )
    }
}

/**
 * Goal: Autoregulated / Powerlifting — RPE trend at matched load. If you hit the SAME weight and
 * reps (or weight and duration, for TIMED) for a lower RPE than before, that's progress. Falls
 * back to a best-set-score trend when no matching prior load exists.
 */
class AutoregulatedMetric(private val formula: OneRepMaxFormula, private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.AUTOREGULATED
    override val displayName = "RPE Trend"
    override val tooltip =
        "Compares the RPE (perceived effort, 1-10) you logged on your best set against previous " +
            "sessions' best sets at the SAME weight and reps (or weight and duration, for timed " +
            "exercises). A lower RPE at the same load means it felt easier — that's progress, even if " +
            "the number on the bar/clock didn't change. If no matching prior session exists, falls " +
            "back to comparing best-set score instead."

    override fun chartScore(log: WorkoutLog): Double {
        val best = WorkoutSetAggregates.bestSet(log.sets, exerciseType, formula)
        return best.rpe ?: WorkoutSetAggregates.bestSetScore(log.sets, exerciseType, formula)
    }

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentBest = WorkoutSetAggregates.bestSet(current.sets, exerciseType, formula)
        val currentRpe = currentBest.rpe
        val currentLoad = currentBest.weightKg
        val currentRepOrDuration = WorkoutSetAggregates.repOrDuration(currentBest, exerciseType)

        val matchedRpes = priorSessions.mapNotNull { log ->
            val best = WorkoutSetAggregates.bestSet(log.sets, exerciseType, formula)
            val sameLoad = best.weightKg == currentLoad &&
                WorkoutSetAggregates.repOrDuration(best, exerciseType) == currentRepOrDuration
            if (sameLoad) best.rpe else null
        }

        if (currentRpe != null && matchedRpes.isNotEmpty()) {
            val baselineRpe = matchedRpes.average()
            val trend = trendFromRelativeDiff(baselineRpe, currentRpe)
            return ProgressResult(
                trend, currentRpe, baselineRpe,
                "RPE %.1f vs avg %.1f at the same load over %d matched session(s)"
                    .format(currentRpe, baselineRpe, matchedRpes.size),
            )
        }

        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "No prior sessions yet.")
        }
        val currentScore = WorkoutSetAggregates.bestSetScore(current.sets, exerciseType, formula)
        val baseline = priorSessions.map { WorkoutSetAggregates.bestSetScore(it.sets, exerciseType, formula) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "No matching load history — compared best-set score %.1f vs avg %.1f instead"
                .format(currentScore, baseline),
        )
    }
}

/** Goal: Simple/beginner — direct field-by-field comparison of the session's top set, no formula. */
class SimpleComparisonMetric(private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.SIMPLE
    override val displayName = "Simple Comparison"
    override val tooltip =
        "Compares your top set's weight and reps (or weight and duration, for timed exercises), plus " +
            "your total set count, directly against the average of your recent sessions — no formula " +
            "involved. If 2 or more of those 3 fields improved, it's progress. If 2 or more got worse, " +
            "it's a regress. Otherwise it's a mixed/no-change result."

    override fun chartScore(log: WorkoutLog) = WorkoutSetAggregates.topSetByWeight(log.sets, exerciseType).weightKg ?: 0.0

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "No prior sessions yet.")
        }
        val currentTop = WorkoutSetAggregates.topSetByWeight(current.sets, exerciseType)
        val currentWeight = currentTop.weightKg ?: 0.0
        val currentRepOrDuration = (WorkoutSetAggregates.repOrDuration(currentTop, exerciseType) ?: 0).toDouble()
        val currentSetCount = current.sets.size.toDouble()

        val priorTops = priorSessions.map { WorkoutSetAggregates.topSetByWeight(it.sets, exerciseType) }
        val avgWeight = priorTops.map { it.weightKg ?: 0.0 }.average()
        val avgRepOrDuration = priorTops.map { (WorkoutSetAggregates.repOrDuration(it, exerciseType) ?: 0).toDouble() }.average()
        val avgSetCount = priorSessions.map { it.sets.size.toDouble() }.average()

        var improved = 0
        var worsened = 0
        fun compare(cur: Double, base: Double) {
            val diff = (cur - base) / if (base == 0.0) 1.0 else abs(base)
            if (diff > NEUTRAL_BAND_PCT) improved++ else if (diff < -NEUTRAL_BAND_PCT) worsened++
        }
        compare(currentWeight, avgWeight)
        compare(currentRepOrDuration, avgRepOrDuration)
        compare(currentSetCount, avgSetCount)

        val trend = when {
            improved >= 2 -> ProgressTrend.PROGRESSED
            worsened >= 2 -> ProgressTrend.REGRESSED
            else -> ProgressTrend.NEUTRAL
        }
        val unit = if (exerciseType == ExerciseType.TIMED) "s" else "reps"
        return ProgressResult(
            trend, null, null,
            "%.1fkg x %.0f%s x %.0fsets vs avg %.1fkg x %.1f%s x %.1fsets over last %d session(s)".format(
                currentWeight, currentRepOrDuration, unit, currentSetCount,
                avgWeight, avgRepOrDuration, unit, avgSetCount, priorSessions.size,
            ),
        )
    }
}

object ProgressMetrics {
    fun forGoal(goal: Goal, formula: OneRepMaxFormula, exerciseType: ExerciseType): ProgressMetric = when (goal) {
        Goal.STRENGTH -> OneRepMaxMetric(formula, exerciseType)
        Goal.HYPERTROPHY -> VolumeMetric(exerciseType)
        Goal.ENDURANCE -> EnduranceMetric(exerciseType)
        Goal.AUTOREGULATED -> AutoregulatedMetric(formula, exerciseType)
        Goal.SIMPLE -> SimpleComparisonMetric(exerciseType)
    }

    fun all(formula: OneRepMaxFormula, exerciseType: ExerciseType): List<ProgressMetric> =
        Goal.entries.map { forGoal(it, formula, exerciseType) }
}
```

- [ ] **Step 4: Update `ProgressCalculator.kt`**

```kotlin
package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.domain.model.Goal
import com.trainingtracker.app.domain.model.OneRepMaxFormula

/**
 * Computes the progress color/result for one exercise, per requirements.txt 3c:
 * - goal is exercise.goalOverride, falling back to the app-wide default goal
 * - compared against a rolling average of that SAME exercise's last N sessions
 *   (N = rollingWindow, from Settings; defaults to 5)
 */
object ProgressCalculator {
    fun effectiveGoal(exercise: Exercise, globalDefaultGoal: Goal): Goal = exercise.goalOverride ?: globalDefaultGoal

    /**
     * @param completedLogsNewestFirst all COMPLETED logs for this exercise, sorted newest first.
     *   The first element is treated as the "current" session; the next [rollingWindow] entries
     *   form the comparison baseline.
     */
    fun evaluate(
        exercise: Exercise,
        completedLogsNewestFirst: List<WorkoutLog>,
        globalDefaultGoal: Goal,
        rollingWindow: Int,
        oneRepMaxFormula: OneRepMaxFormula,
    ): ProgressResult {
        if (completedLogsNewestFirst.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "No sessions logged yet.")
        }
        val current = completedLogsNewestFirst.first()
        val priorSessions = completedLogsNewestFirst.drop(1).take(rollingWindow)
        val metric = ProgressMetrics.forGoal(effectiveGoal(exercise, globalDefaultGoal), oneRepMaxFormula, exercise.type)
        return metric.evaluate(current, priorSessions)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.trainingtracker.app.domain.progress.ProgressMetricTest"`
Expected: PASS (7 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/domain/progress/ProgressMetric.kt app/src/main/java/com/trainingtracker/app/domain/progress/ProgressCalculator.kt app/src/test/java/com/trainingtracker/app/domain/progress/ProgressMetricTest.kt
git commit -m "Make progress metrics type-aware using per-set aggregates"
```

---

### Task 7: Supabase DTOs and schema

**Files:**
- Modify: `app/src/main/java/com/trainingtracker/app/data/remote/dto/Dtos.kt`
- Modify: `supabase/schema.sql`

**Interfaces:**
- Consumes: `ExerciseType`, `WorkoutSet` (Task 1); `Exercise` (Task 1), `WorkoutLog` (Task 3).
- Produces: `ExerciseDto` gains `type: String`; `WorkoutLogDto` drops `weight_kg/reps/sets/rpe`, gains `sets: String` (JSON-encoded, same encoding as the local Room converter from Task 1). `SyncRepository.kt` and `SyncWorker.kt` call `.toDto()`/`.toEntity()` only — no direct field access — so they need no changes.

- [ ] **Step 1: Replace `Dtos.kt`**

```kotlin
package com.trainingtracker.app.data.remote.dto

import com.trainingtracker.app.data.local.entity.BodyMetricLog
import com.trainingtracker.app.data.local.entity.Category
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.Routine
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.domain.model.Goal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Postgrest row DTOs — mirror supabase/schema.sql (snake_case columns, epoch-millis timestamps). */

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    @SerialName("is_custom") val isCustom: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

fun Category.toDto() = CategoryDto(id, name, isCustom, updatedAt, deleted)
fun CategoryDto.toEntity() = Category(id, name, isCustom, updatedAt, deleted)

@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("goal_override") val goalOverride: String?,
    val type: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

fun Exercise.toDto() = ExerciseDto(id, name, categoryId, goalOverride?.name, type.name, createdAt, updatedAt, deleted)
fun ExerciseDto.toEntity() = Exercise(
    id, name, categoryId,
    goalOverride?.let { runCatching { Goal.valueOf(it) }.getOrNull() },
    runCatching { ExerciseType.valueOf(type) }.getOrDefault(ExerciseType.WEIGHTED),
    createdAt, updatedAt, deleted,
)

@Serializable
data class WorkoutLogDto(
    val id: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("logged_at") val loggedAt: Long,
    /** JSON-encoded List<WorkoutSet> — same encoding the local Room TypeConverter produces. */
    val sets: String,
    val status: String,
    @SerialName("source_log_id") val sourceLogId: String?,
    val notes: String?,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

fun WorkoutLog.toDto() = WorkoutLogDto(
    id, exerciseId, loggedAt, Json.encodeToString(sets), status.name, sourceLogId, notes, updatedAt, deleted,
)
fun WorkoutLogDto.toEntity() = WorkoutLog(
    id, exerciseId, loggedAt,
    if (sets.isBlank()) emptyList() else Json.decodeFromString(sets),
    runCatching { LogStatus.valueOf(status) }.getOrDefault(LogStatus.COMPLETED),
    sourceLogId, notes, updatedAt, deleted,
)

@Serializable
data class RoutineDto(
    val id: String,
    val name: String,
    @SerialName("days_of_week") val daysOfWeek: String,
    @SerialName("reminder_hour") val reminderHour: Int,
    @SerialName("reminder_minute") val reminderMinute: Int,
    @SerialName("exercise_ids") val exerciseIds: String,
    val enabled: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

private fun List<Int>.toCsv() = joinToString(",")
private fun List<String>.toCsvStr() = joinToString(",")
private fun String.toIntListCsv() = if (isBlank()) emptyList() else split(",").map { it.trim().toInt() }
private fun String.toStringListCsv() = if (isBlank()) emptyList() else split(",").map { it.trim() }

fun Routine.toDto() = RoutineDto(
    id, name, daysOfWeek.toCsv(), reminderHour, reminderMinute, exerciseIds.toCsvStr(), enabled, updatedAt, deleted,
)
fun RoutineDto.toEntity() = Routine(
    id, name, daysOfWeek.toIntListCsv(), reminderHour, reminderMinute, exerciseIds.toStringListCsv(),
    enabled, updatedAt, deleted,
)

@Serializable
data class BodyMetricLogDto(
    val id: String,
    @SerialName("logged_at") val loggedAt: Long,
    @SerialName("weight_kg") val weightKg: Double?,
    @SerialName("body_fat_percent") val bodyFatPercent: Double?,
    @SerialName("muscle_mass_percent") val muscleMassPercent: Double?,
    val notes: String?,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

fun BodyMetricLog.toDto() = BodyMetricLogDto(
    id, loggedAt, weightKg, bodyFatPercent, muscleMassPercent, notes, updatedAt, deleted,
)
fun BodyMetricLogDto.toEntity() = BodyMetricLog(
    id, loggedAt, weightKg, bodyFatPercent, muscleMassPercent, notes, updatedAt, deleted,
)

@Serializable
data class AppSettingsDto(
    val id: String = "singleton",
    @SerialName("global_default_goal") val globalDefaultGoal: String,
    @SerialName("rolling_average_window") val rollingAverageWindow: Int,
    @SerialName("one_rep_max_formula") val oneRepMaxFormula: String,
    @SerialName("updated_at") val updatedAt: Long,
)
```

- [ ] **Step 2: Update `supabase/schema.sql`**

Replace the `exercises` table definition:

```sql
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
```

Replace the `workout_logs` table definition:

```sql
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
```

(`sets` is `text`, not `jsonb`, because the DTO already encodes it to a JSON string — a `jsonb` column would double-encode that string. This is the same pattern already used for `routines.days_of_week`/`exercise_ids`: structured data stored as plain text, decoded client-side.)

This schema.sql only takes effect on a fresh Supabase project or if the user manually re-runs it — note that in your final report but don't attempt to run it against a live database as part of this task.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: no NEW errors in `Dtos.kt`. Remaining failures should now only be in the UI screens (Tasks 8–11) — confirm by skimming the file list in the compiler output.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/data/remote/dto/Dtos.kt supabase/schema.sql
git commit -m "Update Supabase DTOs and schema for exercise type and per-set logs"
```

---

### Task 8: `SetListEditor` — shared per-set editing component

**Files:**
- Create: `app/src/main/java/com/trainingtracker/app/ui/components/SetListEditor.kt`

**Interfaces:**
- Consumes: `ExerciseType`, `WorkoutSet` (Task 1).
- Produces: `data class SetRowState(weight, reps, durationSeconds, rpe: String)`; extension functions `WorkoutSet.toRowState()`, `List<WorkoutSet>.toRowStates()`, `SetRowState.validationError(type): String?`, `List<SetRowState>.hasErrors(type): Boolean`, `SetRowState.toWorkoutSet(type): WorkoutSet`, `List<SetRowState>.toWorkoutSets(type): List<WorkoutSet>`, `List<WorkoutSet>.summaryText(type): String`; and `@Composable fun SetListEditor(exerciseType, rows, onRowsChange, modifier)`. Tasks 9, 10, 11 import all of these by exact name.

This is a pure-Compose UI component with no DAO/DB dependency, so it's verified by compiling + the manual smoke test in Task 12, not a JVM unit test (Compose composables require the Android/Compose runtime).

- [ ] **Step 1: Create `SetListEditor.kt`**

```kotlin
package com.trainingtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet

/** One set row's raw text-field state, converted to/from [WorkoutSet] on save. */
data class SetRowState(
    val weight: String = "",
    val reps: String = "",
    val durationSeconds: String = "",
    val rpe: String = "",
)

fun WorkoutSet.toRowState() = SetRowState(
    weight = weightKg?.toString() ?: "",
    reps = reps?.toString() ?: "",
    durationSeconds = durationSeconds?.toString() ?: "",
    rpe = rpe?.toString() ?: "",
)

fun List<WorkoutSet>.toRowStates(): List<SetRowState> = map { it.toRowState() }

/**
 * Inline validation error for one row, or null if it's valid for [type] — requirements.txt 3n:
 * never a silent no-op on bad input.
 */
fun SetRowState.validationError(type: ExerciseType): String? {
    if (weight.isNotBlank() && weight.toDoubleOrNull() == null) return "Enter a valid weight or leave it blank"
    return when (type) {
        ExerciseType.WEIGHTED -> when {
            weight.isBlank() || weight.toDoubleOrNull() == null -> "Enter a valid weight"
            reps.toIntOrNull() == null -> "Enter a valid rep count"
            else -> null
        }
        ExerciseType.BODYWEIGHT -> if (reps.toIntOrNull() == null) "Enter a valid rep count" else null
        ExerciseType.TIMED -> if (durationSeconds.toIntOrNull() == null) "Enter a valid duration (seconds)" else null
    }
}

fun List<SetRowState>.hasErrors(type: ExerciseType): Boolean = any { it.validationError(type) != null }

fun SetRowState.toWorkoutSet(type: ExerciseType): WorkoutSet = WorkoutSet(
    weightKg = weight.toDoubleOrNull(),
    reps = if (type == ExerciseType.TIMED) null else reps.toIntOrNull(),
    durationSeconds = if (type == ExerciseType.TIMED) durationSeconds.toIntOrNull() else null,
    rpe = rpe.toDoubleOrNull(),
)

fun List<SetRowState>.toWorkoutSets(type: ExerciseType): List<WorkoutSet> = map { it.toWorkoutSet(type) }

/** Session-log/Pending list row summary, e.g. "70.0kg x 8 reps · 80.0kg x 6 reps" or "20.0kg x 45s". */
fun List<WorkoutSet>.summaryText(type: ExerciseType): String = joinToString(" · ") { set ->
    val weightPart = set.weightKg?.let { "${it}kg" } ?: if (type == ExerciseType.WEIGHTED) "0kg" else "BW"
    val loadPart = when (type) {
        ExerciseType.TIMED -> "${set.durationSeconds ?: 0}s"
        else -> "${set.reps ?: 0} reps"
    }
    val rpePart = set.rpe?.let { " (RPE $it)" } ?: ""
    "$weightPart x $loadPart$rpePart"
}

/**
 * A dynamic list of set-row editors, showing/requiring fields depending on [exerciseType]
 * (weighted exercises require weight+reps, bodyweight exercises make weight optional, timed
 * exercises swap reps for a duration field). Shared by Log Workout, the History edit dialog, and
 * the Pending confirm dialog so the per-set editing logic lives in one place.
 */
@Composable
fun SetListEditor(
    exerciseType: ExerciseType,
    rows: List<SetRowState>,
    onRowsChange: (List<SetRowState>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { index, row ->
            fun updateRow(updated: SetRowState) {
                onRowsChange(rows.toMutableList().apply { this[index] = updated })
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set ${index + 1}", modifier = Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = row.weight,
                        onValueChange = { updateRow(row.copy(weight = it)) },
                        label = { Text(if (exerciseType == ExerciseType.WEIGHTED) "Weight (kg)" else "Weight (kg, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    if (exerciseType == ExerciseType.TIMED) {
                        OutlinedTextField(
                            value = row.durationSeconds,
                            onValueChange = { updateRow(row.copy(durationSeconds = it)) },
                            label = { Text("Duration (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        OutlinedTextField(
                            value = row.reps,
                            onValueChange = { updateRow(row.copy(reps = it)) },
                            label = { Text("Reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = row.rpe,
                        onValueChange = { updateRow(row.copy(rpe = it)) },
                        label = { Text("RPE") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRowsChange(rows.toMutableList().apply { removeAt(index) }) }, enabled = rows.size > 1) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove set ${index + 1}")
                    }
                }
                row.validationError(exerciseType)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onRowsChange(rows + SetRowState()) }) { Text("+ Add set") }
            TextButton(onClick = { onRowsChange(rows + rows.last()) }) { Text("Duplicate last set") }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: no errors in `SetListEditor.kt` itself (it isn't consumed anywhere yet, so no new errors should appear from this file).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/ui/components/SetListEditor.kt
git commit -m "Add shared SetListEditor component for per-set logging UI"
```

---

### Task 9: Log Workout screen — bug fix lands here

**Files:**
- Modify: `app/src/main/java/com/trainingtracker/app/ui/log/LogWorkoutViewModel.kt`
- Modify: `app/src/main/java/com/trainingtracker/app/ui/log/LogWorkoutScreen.kt`

**Interfaces:**
- Consumes: `SetListEditor`, `SetRowState`, `hasErrors`, `toWorkoutSets` (Task 8); `LogRepository.logCompleted` (Task 5); `ExerciseType` (Task 1).
- Produces: `LogWorkoutViewModel.logSession(exerciseId, exerciseType, sets, notes, nextSession, loggedAt, onDone)`.

This task removes the bug's root cause: `LogWorkoutViewModel.kt`'s old silent guard `if (exerciseId.isBlank() || weightKg < 0 || reps <= 0 || sets <= 0) return` is gone. There is no `reps`/`sets` count to silently reject anymore — every set row's own required fields are validated inline by `SetListEditor` (Task 8), with a visible error message, before the button handler even calls `logSession`.

- [ ] **Step 1: Replace `LogWorkoutViewModel.kt`**

```kotlin
package com.trainingtracker.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.data.repository.NextSessionAdjustment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogWorkoutViewModel(private val container: AppContainer) : ViewModel() {
    val exercises: StateFlow<List<Exercise>> = container.exerciseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logSession(
        exerciseId: String,
        exerciseType: ExerciseType,
        sets: List<WorkoutSet>,
        notes: String?,
        nextSession: NextSessionAdjustment?,
        loggedAt: Long,
        onDone: () -> Unit,
    ) {
        if (exerciseId.isBlank() || sets.isEmpty()) return
        viewModelScope.launch {
            container.logRepository.logCompleted(exerciseId, exerciseType, sets, notes, nextSession, loggedAt)
            onDone()
        }
    }
}
```

- [ ] **Step 2: Replace `LogWorkoutScreen.kt`**

```kotlin
package com.trainingtracker.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.repository.NextSessionAdjustment
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.components.SearchableExercisePicker
import com.trainingtracker.app.ui.components.SetListEditor
import com.trainingtracker.app.ui.components.SetRowState
import com.trainingtracker.app.ui.components.hasErrors
import com.trainingtracker.app.ui.components.toWorkoutSets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(factory: ViewModelFactory) {
    val viewModel: LogWorkoutViewModel = viewModel(factory = factory)
    val exercises by viewModel.exercises.collectAsState()

    var selectedExerciseId by remember { mutableStateOf("") }
    val exerciseType = exercises.firstOrNull { it.id == selectedExerciseId }?.type ?: ExerciseType.WEIGHTED

    var setRows by remember { mutableStateOf(listOf(SetRowState())) }
    var notes by remember { mutableStateOf("") }

    var planNext by remember { mutableStateOf(false) }
    var weightDelta by remember { mutableStateOf("") }
    var repsDelta by remember { mutableStateOf("") }
    var setsDelta by remember { mutableStateOf("") }
    var rpeDelta by remember { mutableStateOf("") }

    var confirmationMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Defaults to today; the user can back-date a forgotten/late log via the date picker below.
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Set rows depend on which fields the exercise's type exposes, so reset them on exercise change.
    LaunchedEffect(selectedExerciseId) {
        setRows = listOf(SetRowState())
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Log Workout", style = MaterialTheme.typography.headlineSmall)

        SearchableExercisePicker(
            exercises = exercises,
            selectedExerciseId = selectedExerciseId,
            onSelect = { selectedExerciseId = it.id },
        )

        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Date: " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(selectedDateMillis)))
        }

        SetListEditor(exerciseType = exerciseType, rows = setRows, onRowsChange = { setRows = it })

        OutlinedTextField(
            value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = planNext, onCheckedChange = { planNext = it })
            Text("Plan next session from this log")
        }

        if (planNext) {
            Text(
                "Leave a field blank to keep it the same next time. This creates a Pending entry " +
                    "you'll confirm before/at your next workout.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightDelta, onValueChange = { weightDelta = it }, label = { Text("+kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = repsDelta, onValueChange = { repsDelta = it },
                    label = { Text(if (exerciseType == ExerciseType.TIMED) "+seconds" else "+reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = setsDelta, onValueChange = { setsDelta = it }, label = { Text("+sets") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = rpeDelta, onValueChange = { rpeDelta = it }, label = { Text("+RPE") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        confirmationMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                confirmationMessage = null
                errorMessage = when {
                    selectedExerciseId.isBlank() -> "Select an exercise"
                    setRows.hasErrors(exerciseType) -> "Fix the highlighted set(s)"
                    else -> null
                }
                if (errorMessage != null) return@Button
                val adjustment = if (planNext) {
                    NextSessionAdjustment(
                        weightDeltaKg = weightDelta.toDoubleOrNull(),
                        repsDelta = repsDelta.toIntOrNull(),
                        setsDelta = setsDelta.toIntOrNull(),
                        rpeDelta = rpeDelta.toDoubleOrNull(),
                    )
                } else null

                viewModel.logSession(
                    selectedExerciseId, exerciseType, setRows.toWorkoutSets(exerciseType),
                    notes.ifBlank { null }, adjustment, selectedDateMillis,
                ) {
                    confirmationMessage = "Session logged" + if (planNext) " — next session added to Pending" else ""
                    selectedExerciseId = ""
                    setRows = listOf(SetRowState())
                    notes = ""
                    weightDelta = ""; repsDelta = ""; setsDelta = ""; rpeDelta = ""; planNext = false
                    selectedDateMillis = System.currentTimeMillis()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log Session")
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: no NEW errors in `LogWorkoutViewModel.kt`/`LogWorkoutScreen.kt`. Remaining failures should now only be `HistoryScreen.kt`, `HistoryViewModel.kt`, `PendingScreen.kt`, `PendingViewModel.kt` (Tasks 10–11).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/ui/log/LogWorkoutViewModel.kt app/src/main/java/com/trainingtracker/app/ui/log/LogWorkoutScreen.kt
git commit -m "Rework Log Workout for per-set entry; remove silent logging-failure guard"
```

---

### Task 10: History screen — per-set list, edit dialog, chart

**Files:**
- Modify: `app/src/main/java/com/trainingtracker/app/ui/history/HistoryViewModel.kt`
- Modify: `app/src/main/java/com/trainingtracker/app/ui/history/HistoryScreen.kt`

**Interfaces:**
- Consumes: `SetListEditor`, `toRowStates`, `hasErrors`, `toWorkoutSets`, `summaryText` (Task 8); `LogRepository.updateCompleted` (Task 5); `ProgressMetrics.forGoal` 3-arg (Task 6); `ExerciseType` (Task 1).
- Produces: `HistoryState.selectedExerciseType: ExerciseType?`; `HistoryViewModel.updateLog(id, sets: List<WorkoutSet>, notes, loggedAt)`.

- [ ] **Step 1: Replace `HistoryViewModel.kt`**

```kotlin
package com.trainingtracker.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.progress.ProgressCalculator
import com.trainingtracker.app.domain.progress.ProgressMetrics
import com.trainingtracker.app.domain.progress.ProgressResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryState(
    val exercises: List<Exercise> = emptyList(),
    val selectedExerciseId: String? = null,
    val selectedExerciseType: ExerciseType? = null,
    val logsNewestFirst: List<WorkoutLog> = emptyList(),
    val progress: ProgressResult? = null,
    val metricName: String = "",
    val metricTooltip: String = "",
    val chartPointsOldestFirst: List<ChartPoint> = emptyList(),
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedExerciseId = MutableStateFlow<String?>(null)

    private val settingsFlow = combine(
        container.settingsRepository.globalDefaultGoal,
        container.settingsRepository.rollingAverageWindow,
        container.settingsRepository.oneRepMaxFormula,
    ) { goal, window, formula -> Triple(goal, window, formula) }

    val state: StateFlow<HistoryState> = combine(
        container.exerciseRepository.observeAll(),
        selectedExerciseId,
        settingsFlow,
    ) { exercises, selectedId, settings -> Triple(exercises, selectedId ?: exercises.firstOrNull()?.id, settings) }
        .flatMapLatest { (exercises, effectiveId, settings) ->
            val (globalGoal, window, formula) = settings
            if (effectiveId == null) {
                flowOf(HistoryState(exercises = exercises))
            } else {
                container.logRepository.observeCompletedForExercise(effectiveId).map { logs ->
                    val exercise = exercises.firstOrNull { it.id == effectiveId }
                    val progress = exercise?.let { ProgressCalculator.evaluate(it, logs, globalGoal, window, formula) }
                    val goal = exercise?.let { ProgressCalculator.effectiveGoal(it, globalGoal) } ?: globalGoal
                    val exerciseType = exercise?.type ?: ExerciseType.WEIGHTED
                    val metric = ProgressMetrics.forGoal(goal, formula, exerciseType)
                    val chartPoints = logs.reversed().map { ChartPoint(it.loggedAt, metric.chartScore(it)) }
                    HistoryState(exercises, effectiveId, exerciseType, logs, progress, metric.displayName, metric.tooltip, chartPoints)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())

    fun selectExercise(exerciseId: String) {
        selectedExerciseId.value = exerciseId
    }

    /** Corrects a mistake in an already-logged session (requirements: logs must be editable). */
    fun updateLog(id: String, sets: List<WorkoutSet>, notes: String?, loggedAt: Long) {
        viewModelScope.launch {
            container.logRepository.updateCompleted(id, sets, notes, loggedAt)
        }
    }
}
```

- [ ] **Step 2: Replace `HistoryScreen.kt`**

```kotlin
package com.trainingtracker.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.progress.ProgressTrend
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.components.SearchableExercisePicker
import com.trainingtracker.app.ui.components.SetListEditor
import com.trainingtracker.app.ui.components.hasErrors
import com.trainingtracker.app.ui.components.summaryText
import com.trainingtracker.app.ui.components.toRowStates
import com.trainingtracker.app.ui.components.toWorkoutSets
import com.trainingtracker.app.ui.theme.ProgressGreen
import com.trainingtracker.app.ui.theme.ProgressRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(factory: ViewModelFactory) {
    val viewModel: HistoryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    var showTooltip by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<WorkoutLog?>(null) }
    val exerciseType = state.selectedExerciseType ?: ExerciseType.WEIGHTED

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("History & Graphs", style = MaterialTheme.typography.headlineSmall)

        SearchableExercisePicker(
            exercises = state.exercises,
            selectedExerciseId = state.selectedExerciseId,
            onSelect = { viewModel.selectExercise(it.id) },
        )

        if (state.selectedExerciseId != null) {
            val trend = state.progress?.trend
            val trendColor = when (trend) {
                ProgressTrend.PROGRESSED -> ProgressGreen
                ProgressTrend.REGRESSED -> ProgressRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(colors = CardDefaults.cardColors(contentColor = trendColor)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(state.metricName, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { showTooltip = true }) {
                            Icon(Icons.Filled.Info, contentDescription = "What is this metric?")
                        }
                    }
                    Text(
                        trend?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "No data",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    state.progress?.explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            if (state.chartPointsOldestFirst.size >= 2) {
                SimpleLineChart(pointsOldestFirst = state.chartPointsOldestFirst)
            }

            Text("Session log", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a session to fix a mistake.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn {
                items(state.logsNewestFirst, key = { it.id }) { log ->
                    ListItem(
                        headlineContent = { Text(log.sets.summaryText(exerciseType)) },
                        supportingContent = {
                            Text(SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(log.loggedAt)))
                        },
                        modifier = Modifier.clickable { editingLog = log },
                    )
                }
            }
        } else {
            Text("Create an exercise and log a session to see history here.")
        }
    }

    if (showTooltip) {
        AlertDialog(
            onDismissRequest = { showTooltip = false },
            title = { Text(state.metricName) },
            text = { Text(state.metricTooltip) },
            confirmButton = { TextButton(onClick = { showTooltip = false }) { Text("Got it") } },
        )
    }

    editingLog?.let { log ->
        EditLogDialog(
            log = log,
            exerciseType = exerciseType,
            onDismiss = { editingLog = null },
            onSave = { sets, notes, loggedAt ->
                viewModel.updateLog(log.id, sets, notes, loggedAt)
                editingLog = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLogDialog(
    log: WorkoutLog,
    exerciseType: ExerciseType,
    onDismiss: () -> Unit,
    onSave: (List<WorkoutSet>, String?, Long) -> Unit,
) {
    var rows by remember { mutableStateOf(log.sets.toRowStates()) }
    var notes by remember { mutableStateOf(log.notes ?: "") }
    var loggedAt by remember { mutableStateOf(log.loggedAt) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(loggedAt)))
                }
                SetListEditor(exerciseType = exerciseType, rows = rows, onRowsChange = { rows = it })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                errorMessage = if (rows.hasErrors(exerciseType)) "Fix the highlighted set(s)" else null
                if (errorMessage != null) return@Button
                onSave(rows.toWorkoutSets(exerciseType), notes.ifBlank { null }, loggedAt)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = loggedAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { loggedAt = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew compileDebugKotlin`
Expected: no NEW errors in `HistoryViewModel.kt`/`HistoryScreen.kt`. Remaining failures should now only be `PendingScreen.kt`/`PendingViewModel.kt` (Task 11).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/ui/history/HistoryViewModel.kt app/src/main/java/com/trainingtracker/app/ui/history/HistoryScreen.kt
git commit -m "Rework History screen for per-set session log and edit dialog"
```

---

### Task 11: Pending screen — per-set confirm dialog

**Files:**
- Modify: `app/src/main/java/com/trainingtracker/app/ui/pending/PendingViewModel.kt`
- Modify: `app/src/main/java/com/trainingtracker/app/ui/pending/PendingScreen.kt`

**Interfaces:**
- Consumes: `SetListEditor`, `toRowStates`, `hasErrors`, `toWorkoutSets`, `summaryText` (Task 8); `LogRepository.confirmPending` (Task 5); `ExerciseType` (Task 1).
- Produces: `PendingItem(log, exerciseName, exerciseType: ExerciseType)`; `PendingViewModel.confirm(id, sets: List<WorkoutSet>, notes)`.

- [ ] **Step 1: Replace `PendingViewModel.kt`**

```kotlin
package com.trainingtracker.app.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.local.entity.WorkoutSet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingItem(val log: WorkoutLog, val exerciseName: String, val exerciseType: ExerciseType)

class PendingViewModel(private val container: AppContainer) : ViewModel() {
    val pendingItems: StateFlow<List<PendingItem>> = combine(
        container.logRepository.observePending(),
        container.exerciseRepository.observeAll(),
    ) { logs, exercises ->
        val byId = exercises.associateBy(Exercise::id)
        logs.map { log ->
            val exercise = byId[log.exerciseId]
            PendingItem(log, exercise?.name ?: "Unknown exercise", exercise?.type ?: ExerciseType.WEIGHTED)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun confirm(id: String, sets: List<WorkoutSet>, notes: String?) {
        viewModelScope.launch { container.logRepository.confirmPending(id, sets, notes) }
    }

    fun discard(id: String) {
        viewModelScope.launch { container.logRepository.discardPending(id) }
    }
}
```

- [ ] **Step 2: Replace `PendingScreen.kt`**

```kotlin
package com.trainingtracker.app.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.components.SetListEditor
import com.trainingtracker.app.ui.components.hasErrors
import com.trainingtracker.app.ui.components.summaryText
import com.trainingtracker.app.ui.components.toRowStates
import com.trainingtracker.app.ui.components.toWorkoutSets

@Composable
fun PendingScreen(factory: ViewModelFactory) {
    val viewModel: PendingViewModel = viewModel(factory = factory)
    val items by viewModel.pendingItems.collectAsState()
    var editing by remember { mutableStateOf<PendingItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Pending", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        if (items.isEmpty()) {
            Text(
                "No pending sessions. These appear when you plan a next session from a Log Workout entry.",
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(items, key = { it.log.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.exerciseName) },
                    supportingContent = { Text(item.log.sets.summaryText(item.exerciseType)) },
                    trailingContent = {
                        Button(onClick = { editing = item }) { Text("Review") }
                    },
                )
            }
        }
    }

    editing?.let { item ->
        ConfirmPendingDialog(
            item = item,
            onDismiss = { editing = null },
            onDiscard = { viewModel.discard(item.log.id); editing = null },
            onConfirm = { sets, notes ->
                viewModel.confirm(item.log.id, sets, notes)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmPendingDialog(
    item: PendingItem,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onConfirm: (List<WorkoutSet>, String?) -> Unit,
) {
    var rows by remember { mutableStateOf(item.log.sets.toRowStates()) }
    var notes by remember { mutableStateOf(item.log.notes ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm: ${item.exerciseName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetListEditor(exerciseType = item.exerciseType, rows = rows, onRowsChange = { rows = it })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Discard this planned session") }
            }
        },
        confirmButton = {
            Button(onClick = {
                errorMessage = if (rows.hasErrors(item.exerciseType)) "Fix the highlighted set(s)" else null
                if (errorMessage != null) return@Button
                onConfirm(rows.toWorkoutSets(item.exerciseType), notes.ifBlank { null })
            }) { Text("Confirm as done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

- [ ] **Step 3: Verify the FULL project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: PASS with zero errors. Every file touched by this plan is now consistent.

- [ ] **Step 4: Run the full unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: PASS (all tests from Tasks 1, 2, 5, 6 — 24 tests total).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/trainingtracker/app/ui/pending/PendingViewModel.kt app/src/main/java/com/trainingtracker/app/ui/pending/PendingScreen.kt
git commit -m "Rework Pending screen for per-set confirm dialog"
```

---

### Task 12: Full build and manual smoke test

**Files:** none (verification only)

**Interfaces:** none — this task exercises the whole app end to end.

- [ ] **Step 1: Assemble the debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install and launch on a device/emulator**

Run: `./gradlew installDebug`, then launch the app (API 26+ per `README.md`).

- [ ] **Step 3: Create one exercise of each type**

In Exercise Library, create:
- "Bench Press" — category any, type WEIGHTED
- "Pull-ups" — category any, type BODYWEIGHT
- "Plank" — category any, type TIMED

Confirm the list row shows the type next to the category (e.g. "Chest · WEIGHTED").

- [ ] **Step 4: Log a multi-set WEIGHTED session**

In Log Workout, select Bench Press, add 3 set rows: 70kg×8, 80kg×6, 80kg×6 (matching the originally reported bug scenario), leave RPE blank on some, submit. Confirm "Session logged" appears and the form resets.

- [ ] **Step 5: Verify the bug is fixed — check History → Session Log**

Go to History & Graphs, select Bench Press. Confirm the just-logged session appears under "Session log" with a per-set summary line (e.g. "70.0kg x 8 reps · 80.0kg x 6 reps · 80.0kg x 6 reps"), and the chart/trend card shows a value (not "No data" from an empty log list). This is the direct verification that the original "nothing shows in history" bug is resolved.

- [ ] **Step 6: Log a BODYWEIGHT session with no weight typed**

Log Workout → Pull-ups → one set with reps=12, weight left blank. Submit — confirm no "invalid weight" error blocks it (weight is optional for BODYWEIGHT). Check it appears in History with a "BW x 12 reps" style summary.

- [ ] **Step 7: Log a TIMED session**

Log Workout → Plank → one set with weight=0 or blank, duration=45. Submit, then check the "Duration (s)" field (not "Reps") was shown for this exercise, and it appears in History.

- [ ] **Step 8: Verify Pending / "Plan next session" round-trip**

Log another Bench Press session with "Plan next session" checked, +2.5kg, +1 rep. Go to Pending, confirm one entry appears with weights/reps bumped by those deltas on every set, tap Review, confirm the per-set editor shows correctly, confirm it.

- [ ] **Step 9: Verify editing an existing log**

In History, tap an existing session, change one set's weight, save, confirm the list updates.

- [ ] **Step 10: Report results**

If any step fails, stop and treat it as a new bug — return to Phase 1 of systematic-debugging rather than patching ad hoc. If all steps pass, this task is done; no commit needed (verification only).

---

### Task 13: Update `requirements.txt`

**Files:**
- Modify: `requirements.txt`

**Interfaces:** none — documentation only.

- [ ] **Step 1: Replace section 3a's field list**

Find (lines 27–32):

```
3a. Exercise creation
    - Fields: name (e.g. "Bench Press", "Squat", "Deadlift", "Bicep Curl"),
      RPE, reps, weight, sets, category (e.g. leg, chest, etc.)
    - Exercise is created ONCE (a reusable template/entry). When logging a
      workout later, the user picks the exercise from a searchable list
      instead of retyping the name each time.
```

Replace with:

```
3a. Exercise creation
    - Fields: name (e.g. "Bench Press", "Squat", "Deadlift", "Bicep Curl"),
      category (e.g. leg, chest, etc.), and a type: Weighted (requires
      weight+reps per set), Bodyweight (reps required, weight optional —
      e.g. dips, pull-ups, with or without added load), or Timed (duration
      required per set, weight optional — e.g. planks, farmer's carries).
      RPE, reps/duration, and weight are entered per SET when logging a
      session (see 3b), not stored on the exercise itself.
    - Exercise is created ONCE (a reusable template/entry). When logging a
      workout later, the user picks the exercise from a searchable list
      instead of retyping the name each time.
```

- [ ] **Step 2: Replace section 3b's opening**

Find (lines 34–44):

```
3b. Logging a session + "future log" workflow
    - When logging a completed exercise, user has the option to increase
      weight, reps, sets, and/or RPE for the NEXT session.
    - If increased, the app creates a new "TBD" log entry (a planned future
      session) that the user must confirm/verify before/at that next
      workout, rather than the user manually re-entering the same data
      each time.
    - DECISION MADE: logging a session lets the user pick the date via a
      date picker, defaulting to today. Covers forgetting to log on the
      day, or not having time until later - the session gets recorded
      under the day it actually happened, not necessarily "now".
```

Replace with:

```
3b. Logging a session + "future log" workflow
    - A logged session is one or more individual SETS, each with its own
      weight/reps (or weight/duration for Timed exercises) and optional
      RPE — e.g. set 1 at 70kg x 8, sets 2-3 at 80kg x 6, all in the same
      session. Sets can be added/removed freely; at least one set is
      required to log a session.
    - When logging a completed exercise, user has the option to increase
      weight, reps (or duration, for Timed exercises), and/or RPE for the
      NEXT session, applied uniformly to every set, plus add/remove a
      number of sets.
    - If increased, the app creates a new "TBD" log entry (a planned future
      session) that the user must confirm/verify before/at that next
      workout, rather than the user manually re-entering the same data
      each time.
    - DECISION MADE: logging a session lets the user pick the date via a
      date picker, defaulting to today. Covers forgetting to log on the
      day, or not having time until later - the session gets recorded
      under the day it actually happened, not necessarily "now".
```

- [ ] **Step 3: Add a decision bullet after the 5-metrics list in section 3c**

Find the line (originally line 70):

```
          worse >= 2 of 3 = red, mixed/unchanged = default color
```

Add immediately after it (before the "Goal scope" bullet that currently follows):

```
    - DECISION MADE: since sessions can now have multiple sets with
      independent weight/reps, and exercises can be Bodyweight or Timed,
      each metric uses a consistent "which set/number represents this
      session" rule: Strength and Autoregulated use the session's best
      set (by estimated 1RM, or by weight x duration for Timed); Volume
      and Endurance sum across ALL sets in the session; Simple Comparison
      uses the session's heaviest/longest top set (no formula). For Timed
      exercises, the Strength metric substitutes a load x time (weight x
      duration) score for the estimated-1RM formulas, since there's no
      rep count to plug in.
```

- [ ] **Step 4: Append two changelog entries at the end of the "5. CHANGE LOG" section**

After the final existing line ("Verified: Supabase push sync and restore-from-backup both confirmed working end-to-end against the live project (not just code-reviewed)."), add:

```
- Added: exercises now have a type (Weighted/Bodyweight/Timed) determining
  which fields a logged set requires; a logged session is one or more
  individual sets with independent weight/reps/duration/RPE, not a single
  weight/reps/sets-count triple.
- Fixed: a silent-failure bug where logging a session with a zero rep or
  set count was dropped with no confirmation and no error message,
  violating the inline-error requirement (3n). Per-set logging removes
  the top-level sets-count field the old silent guard was checking.
```

- [ ] **Step 5: Commit**

```bash
git add requirements.txt
git commit -m "Update requirements.txt for per-set/bodyweight/timed logging"
```
