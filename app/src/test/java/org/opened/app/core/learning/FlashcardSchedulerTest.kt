package org.opened.app.core.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opened.app.core.database.entity.FlashcardEntity

class FlashcardSchedulerTest {

    @Test
    fun testFsrsInitialReviews() {
        val newCard = FlashcardEntity(
            id = "c1",
            chapterId = "chap1",
            pathId = "p1",
            front = "What is DNA?",
            back = "Deoxyribonucleic acid",
            repetitions = 0,
            state = FsrsState.NEW.value
        )

        // Rating GOOD on a new card
        val goodSchedule = FlashcardScheduler.calculateFsrsNextReview(
            card = newCard,
            rating = FsrsRating.GOOD
        )
        assertEquals(FsrsState.REVIEW, goodSchedule.state)
        assertTrue(goodSchedule.stability > 1.0f)
        assertTrue(goodSchedule.intervalDays >= 1)
        assertEquals(1, goodSchedule.repetitions)

        // Rating AGAIN on a new card
        val againSchedule = FlashcardScheduler.calculateFsrsNextReview(
            card = newCard,
            rating = FsrsRating.AGAIN
        )
        assertEquals(FsrsState.LEARNING, againSchedule.state)
        assertTrue(againSchedule.stability < goodSchedule.stability)
        assertEquals(0, againSchedule.repetitions)
    }

    @Test
    fun testRetrievabilityDecay() {
        val stability = 5.0f
        val rDay0 = FlashcardScheduler.calculateRetrievability(0, stability)
        val rDay5 = FlashcardScheduler.calculateRetrievability(5, stability)
        val rDay30 = FlashcardScheduler.calculateRetrievability(30, stability)

        assertEquals(1.0f, rDay0, 0.001f)
        assertTrue(rDay5 < rDay0)
        assertTrue(rDay30 < rDay5)
        assertTrue(rDay30 > 0.0f)
    }

    @Test
    fun testFsrsStabilityGrowthOnRecall() {
        val card = FlashcardEntity(
            id = "c1",
            chapterId = "chap1",
            pathId = "p1",
            front = "Question",
            back = "Answer",
            repetitions = 2,
            stability = 4.0f,
            difficulty = 5.0f,
            state = FsrsState.REVIEW.value,
            lastReviewTimestamp = System.currentTimeMillis() - 4L * 24L * 3600L * 1000L
        )

        val schedule = FlashcardScheduler.calculateFsrsNextReview(
            card = card,
            rating = FsrsRating.GOOD,
            elapsedDays = 4
        )

        assertTrue(schedule.stability > card.stability)
        assertEquals(3, schedule.repetitions)
    }

    @Test
    fun testInterleavedPracticeScheduling() {
        val c1 = FlashcardEntity("1", "chapA", "p1", "A1", "ans")
        val c2 = FlashcardEntity("2", "chapA", "p1", "A2", "ans")
        val c3 = FlashcardEntity("3", "chapB", "p1", "B1", "ans")
        val c4 = FlashcardEntity("4", "chapC", "p1", "C1", "ans")

        val interleaved = FlashcardScheduler.scheduleInterleavedPractice(
            cards = listOf(c1, c2, c3, c4),
            nowMillis = System.currentTimeMillis(),
            weakChapterIds = setOf("chapB")
        )

        assertEquals(4, interleaved.size)
        // Chapter B has weak prerequisite boost so it should appear early
        assertEquals("chapB", interleaved.first().chapterId)

        // Consecutive items should interleave chapters rather than clustering chapA
        for (i in 0 until interleaved.size - 1) {
            val curr = interleaved[i].chapterId
            val next = interleaved[i + 1].chapterId
            // In a 4-card deck with {A, A, B, C}, no two consecutive cards should be identical
            assertNotEquals("Cards should be interleaved", curr, next)
        }
    }
}
