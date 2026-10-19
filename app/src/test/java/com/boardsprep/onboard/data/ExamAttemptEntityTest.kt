package com.boardsprep.onboard.data

import com.boardsprep.onboard.data.local.entities.ExamAttemptEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamAttemptEntityTest {

    @Test
    fun defaultValues_areValidForCbseBoardExam() {
        val attempt = ExamAttemptEntity(
            paperTitle = "CBSE Class 12 Physics Sample Question Paper 2027",
            subjectId = "physics",
            timeSpentSeconds = 10450L
        )

        assertEquals(10800, attempt.durationSeconds) // 3 hours = 10800s
        assertEquals(900L, attempt.readingTimeSeconds) // 15 mins reading = 900s
        assertEquals(0, attempt.appSwitchCount)
        assertEquals("[]", attempt.completedSectionsJson)
        assertEquals(-1, attempt.selfScore)
        assertEquals(70, attempt.maxMarks)
        assertTrue(attempt.completedAt > 0L)
    }

    @Test
    fun completedSectionsJson_serializesAndDeserializesCorrectly() {
        val sections = listOf("A", "B", "C", "D", "E")
        val gson = Gson()
        val json = gson.toJson(sections)

        val attempt = ExamAttemptEntity(
            paperTitle = "CBSE Class 12 Mathematics SQP",
            subjectId = "maths",
            timeSpentSeconds = 10700L,
            completedSectionsJson = json,
            selfScore = 74,
            maxMarks = 80
        )

        val type = object : TypeToken<List<String>>() {}.type
        val parsed: List<String> = gson.fromJson(attempt.completedSectionsJson, type)

        assertEquals(5, parsed.size)
        assertTrue(parsed.contains("A"))
        assertTrue(parsed.contains("E"))
        assertEquals(74, attempt.selfScore)
        assertEquals(80, attempt.maxMarks)
    }

    @Test
    fun percentageCalculation_isAccurate() {
        val attempt70 = ExamAttemptEntity(
            paperTitle = "Physics Set 1",
            subjectId = "physics",
            timeSpentSeconds = 10200L,
            selfScore = 63,
            maxMarks = 70
        )
        val pct70 = (attempt70.selfScore.toDouble() / attempt70.maxMarks) * 100.0
        assertEquals(90.0, pct70, 0.001)

        val attempt80 = ExamAttemptEntity(
            paperTitle = "Maths Set 1",
            subjectId = "maths",
            timeSpentSeconds = 10500L,
            selfScore = 76,
            maxMarks = 80
        )
        val pct80 = (attempt80.selfScore.toDouble() / attempt80.maxMarks) * 100.0
        assertEquals(95.0, pct80, 0.001)
    }

    @Test
    fun focusIntegrity_evaluatesCorrectly() {
        val cleanAttempt = ExamAttemptEntity(
            paperTitle = "Chemistry SQP",
            subjectId = "chemistry",
            timeSpentSeconds = 9800L,
            appSwitchCount = 0
        )
        assertEquals(0, cleanAttempt.appSwitchCount)

        val interruptedAttempt = ExamAttemptEntity(
            paperTitle = "Chemistry SQP",
            subjectId = "chemistry",
            timeSpentSeconds = 10200L,
            appSwitchCount = 2
        )
        assertTrue(interruptedAttempt.appSwitchCount > 0)
    }
}
