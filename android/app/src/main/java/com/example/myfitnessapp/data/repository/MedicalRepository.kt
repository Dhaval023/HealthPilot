package com.example.myfitnessapp.data.repository

import com.example.myfitnessapp.models.medical.MedicalAssessment
import com.example.myfitnessapp.models.medical.MedicalAssessmentResult
import com.example.myfitnessapp.models.medical.MedicalQuestion
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.json.JSONArray

class MedicalRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Reusing the API key and model from the centralized ApiKeyConfig file
    private val apiKey = ApiKeyConfig.GEMINI_API_KEY
    private val modelName = ApiKeyConfig.GEMINI_MODEL_NAME

    private val generativeModel = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey
    )

    suspend fun getNextStep(assessment: MedicalAssessment, language: String = "English"): Any {
        val systemPrompt = getMedicalSystemPrompt(language)
        val userPrompt = buildAssessmentPrompt(assessment)

        val modelWithSystem = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey,
            systemInstruction = content { text(systemPrompt) }
        )

        val response = modelWithSystem.generateContent(userPrompt)
        val responseText = response.text ?: throw Exception("Empty response from Gemini")
        
        return parseGeminiResponse(responseText)
    }

    private fun parseGeminiResponse(jsonString: String): Any {
        var cleaned = jsonString.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substringAfter("```json")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substringBeforeLast("```")
        }
        cleaned = cleaned.trim()

        val json = JSONObject(cleaned)
        val type = json.optString("type")
        
        return if (type == "question") {
            val options = mutableListOf<String>()
            val optionsArray = json.optJSONArray("options")
            if (optionsArray != null) {
                for (i in 0 until optionsArray.length()) {
                    options.add(optionsArray.getString(i))
                }
            }
            
            MedicalQuestion(
                type = "question",
                questionId = json.optString("questionId"),
                question = json.optString("question"),
                options = options,
                allowCustomInput = json.optBoolean("allowCustomInput", false),
                required = json.optBoolean("required", true)
            )
        } else if (type == "assessment") {
            MedicalAssessmentResult(
                type = "assessment",
                urgency = json.optString("urgency"),
                summary = json.optString("summary"),
                possibleExplanations = jsonArrayToList(json.optJSONArray("possibleExplanations")),
                generalGuidance = jsonArrayToList(json.optJSONArray("generalGuidance")),
                medicationInformation = jsonArrayToList(json.optJSONArray("medicationInformation")),
                doctorRecommendation = json.optString("doctorRecommendation"),
                warningSigns = jsonArrayToList(json.optJSONArray("warningSigns"))
            )
        } else {
            throw Exception("Unknown response type from AI")
        }
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        val list = mutableListOf<String>()
        if (array != null) {
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        }
        return list
    }

    suspend fun saveAssessment(assessment: MedicalAssessment) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("medical_assessments")
            .document(uid)
            .collection("assessments")
            .document(assessment.assessmentId)
            .set(assessment)
            .await()
    }

    private fun getMedicalSystemPrompt(language: String): String {
        return """
            You are HealthPilot Medical Assistant.
            Your role is to provide general health information and symptom triage support.
            
            IMPORTANT: ALWAYS respond in $language language.
            Even for structured JSON values like 'question', 'summary', 'possibleExplanations', etc., you MUST translate the content to $language.
            
            You are NOT a doctor and must not claim to diagnose the user.
            You must never present a suspected condition as a confirmed diagnosis.
            You must not independently prescribe prescription medication.
            
            Responsibilities:
            1. Understand the user's complaint.
            2. Identify potentially urgent warning signs.
            3. Ask a MAXIMUM of 3 clear, concise follow-up questions one by one. Do NOT ask repetitive questions or exceed 3 questions total under any circumstance. If 3 questions have been answered, you MUST proceed to the final assessment.
            4. Adapt subsequent questions based on previous answers and user profile. Do not repeat any concept or ask things already answered or self-evident.
            5. Provide general health guidance and recommend professional care when appropriate.
            6. Explain emergency warning signs clearly.
            7. For temporary relief, suggest appropriate over-the-counter (OTC) medications along with clear safety guidelines, age/weight/health-context considerations, and proper dosage warnings based on the user's profile details (age, weight, height, gender, medical restrictions). Make sure this temporary relief guidance is populated in the 'medicationInformation' list.
            
            If symptoms suggest a life-threatening emergency, stop the questionnaire immediately and return an assessment with urgency 'emergency', but still include generic first-aid or safe temporary OTC instructions if appropriate while they await urgent care.
            
            For multiple-choice questions, always include "Other" as the last option in the "options" array, and set "allowCustomInput" to true so that users can type their own text if they choose "Other".
            
            ALWAYS return structured JSON.
            
            For a question:
            {
              "type": "question",
              "questionId": "unique_id",
              "question": "The question text",
              "options": ["Option 1", "Option 2", ...],
              "allowCustomInput": true/false,
              "required": true
            }
            
            For a final assessment:
            {
              "type": "assessment",
              "urgency": "self_care|doctor_soon|urgent|emergency",
              "summary": "Concise summary of findings",
              "possibleExplanations": ["Reason 1", "Reason 2"],
              "generalGuidance": ["Tip 1", "Tip 2"],
              "medicationInformation": ["General info about OTC if relevant"],
              "doctorRecommendation": "When and who to see",
              "warningSigns": ["Sign 1", "Sign 2"]
            }
        """.trimIndent()
    }

    private fun buildAssessmentPrompt(assessment: MedicalAssessment): String {
        val sb = StringBuilder()
        sb.append("User Complaint: ${assessment.initialComplaint}\n")
        sb.append("Profile: ${assessment.profileContext}\n")
        sb.append("Previous Answers: ${assessment.answers}\n")
        sb.append("Number of questions answered so far: ${assessment.answers.size}\n")
        sb.append("Determine the next concise question, or if you have enough info or have reached the 3-question limit, you MUST provide a final assessment now.")
        return sb.toString()
    }
}
