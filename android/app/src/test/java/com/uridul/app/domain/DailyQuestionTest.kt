package com.uridul.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyQuestionTest {

    private val n = DailyQuestion.QUESTIONS.size

    @Test
    fun noRepeatUntilAllQuestionsUsed() {
        val start = LocalDate.of(2026, 11, 20)
        val used = (0 until n).map { DailyQuestion.indexFor("coupleA", start.plusDays(it.toLong())) }
        assertEquals(n, used.toSet().size)
    }

    @Test
    fun sameDaySameQuestionForBothPeople() {
        val d = LocalDate.of(2027, 2, 14)
        assertEquals(DailyQuestion.indexFor("XZl4", d), DailyQuestion.indexFor("XZl4", d))
    }

    @Test
    fun couplesGetDifferentOrders() {
        val start = LocalDate.of(2026, 1, 1)
        val a = (0 until 10).map { DailyQuestion.indexFor("coupleA", start.plusDays(it.toLong())) }
        val b = (0 until 10).map { DailyQuestion.indexFor("coupleB", start.plusDays(it.toLong())) }
        assertNotEquals(a, b)
    }

    @Test
    fun questionsAreUniqueAndShort() {
        assertEquals(n, DailyQuestion.QUESTIONS.toSet().size)
        assertTrue(n >= 100)
        assertTrue(DailyQuestion.QUESTIONS.all { it.isNotBlank() && it.length <= 60 })
    }
}
