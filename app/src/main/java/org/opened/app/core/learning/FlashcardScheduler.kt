package org.opened.app.core.learning

data class Sm2Schedule(
    val intervalDays: Int,
    val easeFactor: Float,
    val repetitions: Int
)

object FlashcardScheduler {
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
}
