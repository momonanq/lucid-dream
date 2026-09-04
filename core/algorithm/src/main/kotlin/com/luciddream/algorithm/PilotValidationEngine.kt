package com.luciddream.algorithm

import com.luciddream.model.NightSession
import com.luciddream.model.SensorWindow
import com.luciddream.model.SleepImport
import com.luciddream.model.SleepStage
import java.util.Locale

/**
 * Validates real-time nocturnal heuristic predictions against post-hoc ground-truth
 * hypnogram data (e.g. from Samsung Health / Health Connect).
 * Computes hit rates (recall), precision, specificity, false positive rates,
 * performs offline weight optimization, and exports pilot datasets.
 */
class PilotValidationEngine(
    private val defaultThreshold: Double = 0.65
) {

    data class WindowMatch(
        val windowIndex: Int,
        val window: SensorWindow,
        val minutesFromOnset: Long,
        val groundTruthStage: SleepStage?,
        val isRemGroundTruth: Boolean,
        val isPredictedRem: Boolean,
        val isHit: Boolean,
        val isFalseAlarm: Boolean,
        val isMiss: Boolean,
        val isCorrectRejection: Boolean
    )

    data class ValidationMetrics(
        val totalWindows: Int,
        val remWindows: Int,
        val nonRemWindows: Int,
        val truePositives: Int,
        val falsePositives: Int,
        val trueNegatives: Int,
        val falseNegatives: Int,
        val hitRate: Double,       // Recall = TP / (TP + FN)
        val precision: Double,     // TP / (TP + FP)
        val specificity: Double,   // TN / (TN + FP)
        val f1Score: Double,
        val thresholdUsed: Double
    )

    data class OptimalWeightsRecommendation(
        val timeWeight: Double,
        val motionWeight: Double,
        val hrvWeight: Double,
        val consistencyWeight: Double,
        val optimalThreshold: Double,
        val projectedF1Score: Double,
        val projectedHitRate: Double,
        val projectedPrecision: Double
    )

    /**
     * Matches every 60-second sensor window against the ground-truth sleep stage intervals.
     */
    fun evaluateSession(
        session: NightSession,
        sleepImport: SleepImport?,
        threshold: Double = defaultThreshold
    ): Pair<ValidationMetrics, List<WindowMatch>> {
        val windows = session.sensorWindows
        if (windows.isEmpty()) {
            return ValidationMetrics(
                totalWindows = 0,
                remWindows = 0,
                nonRemWindows = 0,
                truePositives = 0,
                falsePositives = 0,
                trueNegatives = 0,
                falseNegatives = 0,
                hitRate = 0.0,
                precision = 0.0,
                specificity = 0.0,
                f1Score = 0.0,
                thresholdUsed = threshold
            ) to emptyList()
        }

        val matches = mutableListOf<WindowMatch>()
        var tp = 0
        var fp = 0
        var tn = 0
        var fn = 0
        var remCount = 0
        var nonRemCount = 0

        val stages = sleepImport?.stages ?: emptyList()

        for ((index, window) in windows.withIndex()) {
            val midPointMs = (window.startTimestampMs + window.endTimestampMs) / 2
            val minutesFromOnset = (midPointMs - session.startTimeMs) / 60000

            val matchingStage = stages.find { stage ->
                midPointMs in stage.startTimestampMs..stage.endTimestampMs
            }?.stage

            val isRem = matchingStage == SleepStage.REM
            val isPredicted = window.confidence >= threshold

            val isHit = isRem && isPredicted
            val isFalseAlarm = !isRem && isPredicted
            val isMiss = isRem && !isPredicted
            val isCorrectRejection = !isRem && !isPredicted

            if (isRem) remCount++ else nonRemCount++

            if (isHit) tp++
            if (isFalseAlarm) fp++
            if (isCorrectRejection) tn++
            if (isMiss) fn++

            matches.add(
                WindowMatch(
                    windowIndex = index,
                    window = window,
                    minutesFromOnset = minutesFromOnset,
                    groundTruthStage = matchingStage,
                    isRemGroundTruth = isRem,
                    isPredictedRem = isPredicted,
                    isHit = isHit,
                    isFalseAlarm = isFalseAlarm,
                    isMiss = isMiss,
                    isCorrectRejection = isCorrectRejection
                )
            )
        }

        val hitRate = if (tp + fn > 0) tp.toDouble() / (tp + fn).toDouble() else 0.0
        val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp).toDouble() else 0.0
        val specificity = if (tn + fp > 0) tn.toDouble() / (tn + fp).toDouble() else 0.0
        val f1 = if (precision + hitRate > 0) (2 * precision * hitRate) / (precision + hitRate) else 0.0

        val metrics = ValidationMetrics(
            totalWindows = windows.size,
            remWindows = remCount,
            nonRemWindows = nonRemCount,
            truePositives = tp,
            falsePositives = fp,
            trueNegatives = tn,
            falseNegatives = fn,
            hitRate = hitRate,
            precision = precision,
            specificity = specificity,
            f1Score = f1,
            thresholdUsed = threshold
        )

        return metrics to matches
    }

    /**
     * Performs a grid search over candidate heuristic weight configurations
     * to find weights that maximize F1 score on real session data.
     */
    fun optimizeWeights(
        session: NightSession,
        sleepImport: SleepImport
    ): OptimalWeightsRecommendation {
        val windows = session.sensorWindows
        if (windows.isEmpty() || sleepImport.stages.isEmpty()) {
            return OptimalWeightsRecommendation(
                timeWeight = 0.35,
                motionWeight = 0.30,
                hrvWeight = 0.20,
                consistencyWeight = 0.15,
                optimalThreshold = defaultThreshold,
                projectedF1Score = 0.0,
                projectedHitRate = 0.0,
                projectedPrecision = 0.0
            )
        }

        // Candidate weight distributions
        val candidateWeights = listOf(
            Triple(0.35, 0.30, 0.20) to 0.15, // Default baseline
            Triple(0.40, 0.25, 0.20) to 0.15, // Circadian-heavy
            Triple(0.30, 0.35, 0.25) to 0.10, // Motion-heavy
            Triple(0.25, 0.30, 0.35) to 0.10, // HRV-heavy
            Triple(0.40, 0.35, 0.15) to 0.10  // Atonia & Circadian
        )

        val candidateThresholds = listOf(0.55, 0.60, 0.65, 0.70, 0.75)

        var bestF1 = -1.0
        var bestConfig = OptimalWeightsRecommendation(0.35, 0.30, 0.20, 0.15, 0.65, 0.0, 0.0, 0.0)

        for ((triple, consistency) in candidateWeights) {
            val (timeW, motionW, hrvW) = triple
            val engine = RemConfidenceEngine(
                timeWeight = timeW,
                motionWeight = motionW,
                hrvWeight = hrvW,
                consistencyWeight = consistency
            )

            // Re-evaluate confidence on all windows
            val recomputedWindows = mutableListOf<SensorWindow>()
            val recentWindows = mutableListOf<SensorWindow>()
            for (w in windows) {
                val midMs = (w.startTimestampMs + w.endTimestampMs) / 2
                val elapsedMin = (midMs - session.startTimeMs) / 60000
                val breakdown = engine.evaluateWindow(w, elapsedMin, recentWindows)
                val updated = w.copy(confidence = breakdown.compositeScore)
                recomputedWindows.add(updated)
                recentWindows.add(updated)
                if (recentWindows.size > 10) recentWindows.removeAt(0)
            }

            val testSession = session.copy(sensorWindows = recomputedWindows)

            for (thresh in candidateThresholds) {
                val (metrics, _) = evaluateSession(testSession, sleepImport, thresh)
                if (metrics.f1Score > bestF1) {
                    bestF1 = metrics.f1Score
                    bestConfig = OptimalWeightsRecommendation(
                        timeWeight = timeW,
                        motionWeight = motionW,
                        hrvWeight = hrvW,
                        consistencyWeight = consistency,
                        optimalThreshold = thresh,
                        projectedF1Score = metrics.f1Score,
                        projectedHitRate = metrics.hitRate,
                        projectedPrecision = metrics.precision
                    )
                }
            }
        }

        return bestConfig
    }

    /**
     * Generates a CSV dataset string suitable for pilot study data analysis in Python / Pandas / R.
     */
    fun generatePilotCsv(
        session: NightSession,
        sleepImport: SleepImport?,
        threshold: Double = defaultThreshold
    ): String {
        val (_, matches) = evaluateSession(session, sleepImport, threshold)
        val sb = java.lang.StringBuilder()

        sb.append("window_index,start_ms,end_ms,minutes_from_onset,mean_hr,rmssd,movement_index,is_data_sufficient,rem_confidence,ground_truth_stage,is_rem_ground_truth,is_predicted_rem,is_hit,is_false_alarm\n")

        for (m in matches) {
            sb.append(
                String.format(
                    Locale.US,
                    "%d,%d,%d,%d,%.2f,%.2f,%.4f,%b,%.4f,%s,%b,%b,%b,%b\n",
                    m.windowIndex,
                    m.window.startTimestampMs,
                    m.window.endTimestampMs,
                    m.minutesFromOnset,
                    m.window.meanHr,
                    m.window.rmssd,
                    m.window.movementIndex,
                    m.window.isDataSufficient,
                    m.window.confidence,
                    m.groundTruthStage?.name ?: "UNKNOWN",
                    m.isRemGroundTruth,
                    m.isPredictedRem,
                    m.isHit,
                    m.isFalseAlarm
                )
            )
        }

        return sb.toString()
    }
}
