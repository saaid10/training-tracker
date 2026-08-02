package com.trainingtracker.app.domain.bodymetrics

import com.trainingtracker.app.data.local.entity.BodyMetricLog
import com.trainingtracker.app.domain.progress.ProgressTrend
import kotlin.math.abs

/** A tolerance band below which a week-over-week change is considered noise. */
private const val NEUTRAL_BAND_PCT = 0.005 // 0.5% — smaller than exercise metrics since this is
// already a 7-day average, so most day-to-day (e.g. water weight) noise is already smoothed out.

private enum class GoodDirection { UP, DOWN, NEUTRAL }

data class MetricTrend(val currentAvg: Double?, val previousAvg: Double?, val trend: ProgressTrend)

/** requirements.txt 3m: weight stays neutral, body fat % down = progress, muscle mass % up = progress. */
data class BodyMetricsWeeklyTrend(val weight: MetricTrend, val bodyFatPercent: MetricTrend, val muscleMassPercent: MetricTrend)

object BodyMetricsCalculator {
    private val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000

    fun evaluate(entries: List<BodyMetricLog>, now: Long = System.currentTimeMillis()): BodyMetricsWeeklyTrend {
        val currentWindowStart = now - SEVEN_DAYS_MS
        val previousWindowStart = now - 2 * SEVEN_DAYS_MS

        val currentEntries = entries.filter { it.loggedAt in currentWindowStart..now }
        val previousEntries = entries.filter { it.loggedAt in previousWindowStart until currentWindowStart }

        return BodyMetricsWeeklyTrend(
            weight = trendFor(currentEntries, previousEntries, GoodDirection.NEUTRAL) { it.weightKg },
            bodyFatPercent = trendFor(currentEntries, previousEntries, GoodDirection.DOWN) { it.bodyFatPercent },
            muscleMassPercent = trendFor(currentEntries, previousEntries, GoodDirection.UP) { it.muscleMassPercent },
        )
    }

    private fun trendFor(
        currentEntries: List<BodyMetricLog>,
        previousEntries: List<BodyMetricLog>,
        goodDirection: GoodDirection,
        selector: (BodyMetricLog) -> Double?,
    ): MetricTrend {
        val currentVals = currentEntries.mapNotNull(selector)
        val previousVals = previousEntries.mapNotNull(selector)
        val currentAvg = currentVals.takeIf { it.isNotEmpty() }?.average()
        val previousAvg = previousVals.takeIf { it.isNotEmpty() }?.average()

        if (currentAvg == null || previousAvg == null) {
            return MetricTrend(currentAvg, previousAvg, ProgressTrend.INSUFFICIENT_DATA)
        }

        val relativeDiff = (currentAvg - previousAvg) / if (previousAvg == 0.0) 1.0 else abs(previousAvg)
        val trend = when {
            goodDirection == GoodDirection.NEUTRAL -> ProgressTrend.NEUTRAL
            abs(relativeDiff) < NEUTRAL_BAND_PCT -> ProgressTrend.NEUTRAL
            (relativeDiff > 0) == (goodDirection == GoodDirection.UP) -> ProgressTrend.PROGRESSED
            else -> ProgressTrend.REGRESSED
        }
        return MetricTrend(currentAvg, previousAvg, trend)
    }
}
