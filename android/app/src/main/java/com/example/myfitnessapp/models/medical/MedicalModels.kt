package com.example.myfitnessapp.models.medical

import java.util.UUID

data class MedicalAssessment(
    val assessmentId: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val initialComplaint: String = "",
    val category: String = "",
    val profileContext: Map<String, Any> = emptyMap(),
    val questions: List<MedicalQuestion> = emptyList(),
    val answers: Map<String, String> = emptyMap(),
    val currentQuestion: MedicalQuestion? = null,
    val urgency: String = "routine", // routine, self_care, doctor_soon, urgent, emergency
    val finalAssessment: MedicalAssessmentResult? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

data class MedicalQuestion(
    val type: String = "question",
    val questionId: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val allowCustomInput: Boolean = false,
    val required: Boolean = true
)

data class MedicalAnswer(
    val questionId: String,
    val answer: String,
    val customInput: String? = null
)

data class MedicalAssessmentResult(
    val type: String = "assessment",
    val urgency: String = "routine",
    val summary: String = "",
    val possibleExplanations: List<String> = emptyList(),
    val generalGuidance: List<String> = emptyList(),
    val medicationInformation: List<String> = emptyList(),
    val doctorRecommendation: String = "",
    val warningSigns: List<String> = emptyList()
)
