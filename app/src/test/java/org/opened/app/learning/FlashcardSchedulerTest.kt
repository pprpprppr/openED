package org.opened.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.core.learning.FlashcardScheduler

class FlashcardSchedulerTest {

    @Test
    fun testAgainRatingResetsRepetitionsAndSetsIntervalToOneDay() {
        val schedule = FlashcardScheduler.calculateNextReview(
            repetitions = 4,
            intervalDays = 25,
            easeFactor = 2.5f,
            quality = 0 // Again
        )
        assertEquals(0, schedule.repetitions)
        assertEquals(1, schedule.intervalDays)
        assertEquals(2.5f, schedule.easeFactor, 0.001f)
    }

    @Test
    fun testHardRatingResetsRepetitionsAndSetsIntervalToOneDay() {
        val schedule = FlashcardScheduler.calculateNextReview(
            repetitions = 2,
            intervalDays = 6,
            easeFactor = 2.5f,
            quality = 1 // Hard
        )
        assertEquals(0, schedule.repetitions)
        assertEquals(1, schedule.intervalDays)
    }

    @Test
    fun testGoodRatingIncrementsRepetitionsAndFollowsSm2Ladder() {
        // Step 0 -> Step 1: interval 1 day
        val step1 = FlashcardScheduler.calculateNextReview(
            repetitions = 0,
            intervalDays = 1,
            easeFactor = 2.5f,
            quality = 2 // Good
        )
        assertEquals(1, step1.repetitions)
        assertEquals(1, step1.intervalDays)

        // Step 1 -> Step 2: interval 6 days
        val step2 = FlashcardScheduler.calculateNextReview(
            repetitions = 1,
            intervalDays = 1,
            easeFactor = step1.easeFactor,
            quality = 2
        )
        assertEquals(2, step2.repetitions)
        assertEquals(6, step2.intervalDays)

        // Step 2 -> Step 3: interval = 6 * easeFactor
        val step3 = FlashcardScheduler.calculateNextReview(
            repetitions = 2,
            intervalDays = 6,
            easeFactor = step2.easeFactor,
            quality = 2
        )
        assertEquals(3, step3.repetitions)
        assertTrue("Interval should be greater than previous interval", step3.intervalDays > step2.intervalDays)
        assertEquals(12, step3.intervalDays)
    }

    @Test
    fun testEasyRatingIncreasesEaseFactorAndInterval() {
        val initialEase = 2.5f
        val step = FlashcardScheduler.calculateNextReview(
            repetitions = 1,
            intervalDays = 1,
            easeFactor = initialEase,
            quality = 3 // Easy
        )
        assertEquals(2, step.repetitions)
        assertEquals(6, step.intervalDays)
        assertTrue("Ease factor should increase on easy rating", step.easeFactor >= initialEase)
    }

    @Test
    fun testEaseFactorFloorAtOnePointThree() {
        var ease = 1.4f
        // Repeated low ratings should never pull ease below 1.3
        for (i in 1..10) {
            val step = FlashcardScheduler.calculateNextReview(
                repetitions = 0,
                intervalDays = 1,
                easeFactor = ease,
                quality = 2
            )
            ease = step.easeFactor
        }
        assertTrue("Ease factor must never fall below 1.3", ease >= 1.3f)
    }
}
