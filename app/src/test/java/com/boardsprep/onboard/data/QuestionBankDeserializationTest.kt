package com.boardsprep.onboard.data

import com.boardsprep.onboard.data.models.SubjectQuestionBank
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuestionBankDeserializationTest {

    @Test
    fun testPhysicsQuestionBankDeserializationWithUnderscoresGson() {
        val jsonFile = File("src/main/assets/question_banks/physics.json")
        assertTrue("physics.json must exist in assets", jsonFile.exists())

        // Test with LOWER_CASE_WITH_UNDERSCORES (the policy used by BoardsRepository)
        val gsonUnderscore = GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()

        val bankUnderscore = gsonUnderscore.fromJson(jsonFile.readText(), SubjectQuestionBank::class.java)
        assertNotNull(bankUnderscore)
        assertTrue(bankUnderscore.questions.isNotEmpty())

        val firstQ = bankUnderscore.questions.first()
        assertTrue("id must not be blank", firstQ.id.isNotBlank())
        assertTrue("chapterName must not be blank: ${firstQ.chapterName}", firstQ.chapterName.isNotBlank())
        assertTrue("questionText must not be blank: ${firstQ.questionText}", firstQ.questionText.isNotBlank())
        assertTrue("modelAnswer must not be blank: ${firstQ.modelAnswer}", firstQ.modelAnswer.isNotBlank())
        assertTrue("stepMarking must not be empty", firstQ.stepMarking.isNotEmpty())
        assertTrue("keyTerms must not be empty", firstQ.keyTerms.isNotEmpty())
    }

    @Test
    fun testPhysicsQuestionBankDeserializationWithStandardGson() {
        val jsonFile = File("src/main/assets/question_banks/physics.json")
        assertTrue("physics.json must exist in assets", jsonFile.exists())

        // Test with standard identity Gson
        val gsonIdentity = GsonBuilder().create()

        val bankIdentity = gsonIdentity.fromJson(jsonFile.readText(), SubjectQuestionBank::class.java)
        assertNotNull(bankIdentity)
        assertTrue(bankIdentity.questions.isNotEmpty())

        val firstQ = bankIdentity.questions.first()
        assertTrue("id must not be blank", firstQ.id.isNotBlank())
        assertTrue("chapterName must not be blank: ${firstQ.chapterName}", firstQ.chapterName.isNotBlank())
        assertTrue("questionText must not be blank: ${firstQ.questionText}", firstQ.questionText.isNotBlank())
        assertTrue("modelAnswer must not be blank: ${firstQ.modelAnswer}", firstQ.modelAnswer.isNotBlank())
        assertTrue("stepMarking must not be empty", firstQ.stepMarking.isNotEmpty())
        assertTrue("keyTerms must not be empty", firstQ.keyTerms.isNotEmpty())
    }

    @Test
    fun testEnglishQuestionBankDeserialization() {
        val jsonFile = File("src/main/assets/question_banks/english.json")
        assertTrue("english.json must exist in assets", jsonFile.exists())

        val gson = GsonBuilder().create()
        val bank = gson.fromJson(jsonFile.readText(), SubjectQuestionBank::class.java)
        assertNotNull(bank)
        assertTrue(bank.questions.isNotEmpty())

        val firstQ = bank.questions.first()
        assertTrue("id must not be blank", firstQ.id.isNotBlank())
        assertTrue("chapterName must not be blank", firstQ.chapterName.isNotBlank())
        assertTrue("questionText must not be blank", firstQ.questionText.isNotBlank())
        assertTrue("modelAnswer must not be blank", firstQ.modelAnswer.isNotBlank())
    }
}
