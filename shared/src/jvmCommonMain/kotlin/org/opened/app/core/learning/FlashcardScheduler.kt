package org.opened.app.core.learning

import org.opened.app.core.database.entity.FlashcardEntity
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class Sm2Schedule(
    val intervalDays: Int,
    val easeFactor: Float,
    val repetitions: Int
)

enum class FsrsRating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4)
}

enum class FsrsState(val value: Int) {
    NEW(0),
    LEARNING(1),
    REVIEW(2),
    RELEARNING(3)
}

data class FsrsSchedule(
    val intervalDays: Int,
    val stability: Float,
    val difficulty: Float,
    val state: FsrsState,
    val retrievability: Float,
    val repetitions: Int
)

object FlashcardScheduler {

    // Default FSRS-v4 parameters calibrated for general knowledge domains
    private val W = doubleArrayOf(
        0.4025, 1.1838, 3.173, 15.691, // initial stability for 1..4
        7.1949, 0.5345,                // initial difficulty
        1.4604, 0.0046,                // difficulty updates
        1.5457, 0.1192, 1.0192,        // stability recall updates
        1.9395, 0.11, 0.296, 2.2698,   // stability lapse updates
        0.2315, 2.9898                 // weights
    )

    private const val FACTOR = 19.0 / 81.0 // forgetting curve factor for 0.9 retention
    private const val DEFAULT_DESIRED_RETENTION = 0.90

    /**
     * Compute current retrievability R(t, S) = (1 + FACTOR * t / S)^(-0.5)
     * where t is elapsed days since last review and S is memory stability in days.
     */
    fun calculateRetrievability(elapsedDays: Int, stability: Float): Float {
        if (stability <= 0.01f) return 0.0f
        val t = max(0, elapsedDays).toDouble()
        val r = (1.0 + FACTOR * (t / stability.toDouble())).pow(-0.5)
        return r.toFloat().coerceIn(0.0f, 1.0f)
    }

    /**
     * FSRS state transition algorithm.
     * @param card Current flashcard state
     * @param rating User rating (AGAIN, HARD, GOOD, EASY)
     * @param elapsedDays Days elapsed since last review (0 if reviewed same day)
     * @param desiredRetention Target probability of recall at scheduled date (default 0.90)
     */
    fun calculateFsrsNextReview(
        card: FlashcardEntity,
        rating: FsrsRating,
        elapsedDays: Int = 0,
        desiredRetention: Double = DEFAULT_DESIRED_RETENTION
    ): FsrsSchedule {
        val grade = rating.value
        val isFirstReview = card.repetitions == 0 || card.state == FsrsState.NEW.value

        var newStability: Double
        var newDifficulty: Double
        val newState: FsrsState

        if (isFirstReview) {
            // Initial stability from W[0..3]
            newStability = W[grade - 1]
            // Initial difficulty from W[4..5]
            newDifficulty = (W[4] - (grade - 3) * W[5]).coerceIn(1.0, 10.0)
            newState = if (rating == FsrsRating.AGAIN) FsrsState.LEARNING else FsrsState.REVIEW
        } else {
            val prevS = max(0.1, card.stability.toDouble())
            val prevD = card.difficulty.toDouble().coerceIn(1.0, 10.0)
            val currentR = calculateRetrievability(elapsedDays, card.stability).toDouble()

            // Update difficulty: mean reversion towards default difficulty W[4]
            val deltaD = -W[6] * (grade - 3)
            val targetD = (prevD + deltaD).coerceIn(1.0, 10.0)
            newDifficulty = (W[7] * W[4] + (1 - W[7]) * targetD).coerceIn(1.0, 10.0)

            if (rating == FsrsRating.AGAIN) {
                // Memory lapse: calculate reduced stability for relearning
                newStability = (W[11] * newDifficulty.pow(-W[12]) *
                        ((prevS + 1.0).pow(W[13]) - 1.0) *
                        exp(W[14] * (1.0 - currentR))).coerceAtLeast(0.1)
                newStability = min(newStability, prevS)
                newState = FsrsState.RELEARNING
            } else {
                // Successful recall: stability increase
                val hardPenalty = if (rating == FsrsRating.HARD) W[15] else 1.0
                val easyBonus = if (rating == FsrsRating.EASY) W[16] else 1.0

                newStability = prevS * (1.0 + exp(W[8]) *
                        (11.0 - newDifficulty) *
                        prevS.pow(-W[9]) *
                        (exp(W[10] * (1.0 - currentR)) - 1.0) *
                        hardPenalty * easyBonus)
                newStability = max(0.1, newStability)
                newState = FsrsState.REVIEW
            }
        }

        // Calculate next interval from stability and desired retention
        val intervalDays = calculateInterval(newStability, desiredRetention)
        val repCount = if (rating == FsrsRating.AGAIN) 0 else card.repetitions + 1

        return FsrsSchedule(
            intervalDays = intervalDays,
            stability = newStability.toFloat(),
            difficulty = newDifficulty.toFloat(),
            state = newState,
            retrievability = calculateRetrievability(0, newStability.toFloat()),
            repetitions = repCount
        )
    }

    /**
     * Compute optimal interval I = (S / FACTOR) * (r^(-2) - 1)
     */
    fun calculateInterval(stability: Double, desiredRetention: Double = DEFAULT_DESIRED_RETENTION): Int {
        val r = desiredRetention.coerceIn(0.70, 0.98)
        val interval = (stability / FACTOR) * (r.pow(-2.0) - 1.0)
        return max(1, interval.roundToInt())
    }

    /**
     * SM-2 / FSRS algorithm transition.
     * @param quality Rating: 0=Again, 1=Hard, 2=Good, 3=Easy
     */
    fun calculateNextReview(
        repetitions: Int,
        intervalDays: Int,
        easeFactor: Float,
        quality: Int
    ): Sm2Schedule {
        val q = (quality * 1.67f).toInt().coerceIn(0, 5)
        return if (q < 3) {
            Sm2Schedule(intervalDays = 1, easeFactor = easeFactor, repetitions = 0)
        } else {
            val newEase = (easeFactor + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))).toFloat().coerceAtLeast(1.3f)
            val newInterval = when (repetitions) {
                0 -> 1
                1 -> 6
                else -> (intervalDays * newEase).toInt().coerceAtLeast(1)
            }
            Sm2Schedule(intervalDays = newInterval, easeFactor = newEase, repetitions = repetitions + 1)
        }
    }

    /**
     * Interleaved Practice Scheduler.
     * Takes candidate due flashcards, computes urgency scores based on forgetting curves
     * and repeated mistakes, and interleaves them across chapters/topics to prevent
     * blocked repetition and maximize deep retention.
     */
    fun scheduleInterleavedPractice(
        cards: List<FlashcardEntity>,
        nowMillis: Long = System.currentTimeMillis(),
        weakChapterIds: Set<String> = emptySet()
    ): List<FlashcardEntity> {
        if (cards.size <= 1) return cards

        // 1. Calculate priority score for each card
        data class ScoredCard(val card: FlashcardEntity, val priority: Float)

        val scored = cards.map { card ->
            val elapsedMillis = max(0L, nowMillis - card.lastReviewTimestamp)
            val elapsedDays = (elapsedMillis / (24L * 60L * 60L * 1000L)).toInt()
            val retrievability = calculateRetrievability(elapsedDays, card.stability)

            // Urgency = 1.0 - R (cards at risk of being forgotten get highest urgency)
            var score = 1.0f - retrievability

            // Extra boost if card belongs to a chapter flagged with repeated mistakes/prerequisite gaps
            if (weakChapterIds.contains(card.chapterId)) {
                score += 0.35f
            }
            // Penalty if already reviewed very recently with high stability
            if (elapsedDays == 0 && retrievability > 0.95f) {
                score -= 0.5f
            }

            ScoredCard(card, score)
        }.sortedByDescending { it.priority }

        // 2. Interleave across chapters: do not place two consecutive cards from the same chapter
        // if an alternative chapter card is available in the top candidate pool
        val remaining = scored.map { it.card }.toMutableList()
        val interleaved = mutableListOf<FlashcardEntity>()
        var lastChapterId: String? = null

        while (remaining.isNotEmpty()) {
            val nextIdx = remaining.indexOfFirst { it.chapterId != lastChapterId }
            val chosen = if (nextIdx != -1) {
                remaining.removeAt(nextIdx)
            } else {
                remaining.removeAt(0)
            }
            interleaved.add(chosen)
            lastChapterId = chosen.chapterId
        }

        return interleaved
    }
}
