package com.example.myfitnessapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myfitnessapp.data.repository.MedicalRepository
import com.example.myfitnessapp.models.medical.*
import com.example.myfitnessapp.models.User
import com.example.myfitnessapp.utils.MedicalSafetyChecker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MedicalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MedicalRepository()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _assessment = MutableLiveData<MedicalAssessment?>()
    val assessment: LiveData<MedicalAssessment?> = _assessment

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isEmergency = MutableLiveData<Boolean>()
    val isEmergency: LiveData<Boolean> = _isEmergency

    fun startAssessment(complaint: String, category: String) {
        _isEmergency.value = false
        _error.value = null
        viewModelScope.launch {
            _isLoading.value = true
            
            // Check local safety first
            if (MedicalSafetyChecker.isPotentialEmergency(complaint)) {
                _isEmergency.value = true
                _isLoading.value = false
                return@launch
            }

            val uid = auth.currentUser?.uid ?: return@launch
            val userSnapshot = db.collection("users").document(uid).get().addOnSuccessListener { snapshot ->
                val user = snapshot.toObject(User::class.java)
                val profileContext = mutableMapOf<String, Any>()
                user?.let {
                    profileContext["age"] = it.age
                    profileContext["height"] = it.height
                    profileContext["weight"] = it.weight
                    profileContext["sex"] = it.gender
                    if (it.medicalRestrictions.isNotBlank()) {
                        profileContext["medicalRestrictions"] = it.medicalRestrictions
                    }
                }

                val newAssessment = MedicalAssessment(
                    userId = uid,
                    initialComplaint = complaint,
                    category = category,
                    profileContext = profileContext
                )
                
                _assessment.value = newAssessment
                fetchNextStep(newAssessment)
            }
        }
    }

    fun submitAnswer(answer: String) {
        val current = _assessment.value ?: return
        val currentQuestion = current.currentQuestion ?: return
        
        val updatedAnswers = current.answers.toMutableMap()
        updatedAnswers[currentQuestion.questionId] = answer
        
        val updatedAssessment = current.copy(
            answers = updatedAnswers,
            currentQuestion = null
        )
        
        _assessment.value = updatedAssessment
        fetchNextStep(updatedAssessment)
    }

    private fun fetchNextStep(assessment: MedicalAssessment) {
        val appLocale = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().get(0)
        val currentLanguage = appLocale?.displayLanguage ?: "English"

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nextStep = repository.getNextStep(assessment, currentLanguage)
                if (nextStep is MedicalQuestion) {
                    _assessment.value = assessment.copy(currentQuestion = nextStep)
                } else if (nextStep is MedicalAssessmentResult) {
                    val finalAssessment = assessment.copy(
                        finalAssessment = nextStep,
                        completedAt = System.currentTimeMillis()
                    )
                    _assessment.value = finalAssessment
                    repository.saveAssessment(finalAssessment)
                    
                    if (nextStep.urgency == "emergency") {
                        _isEmergency.value = true
                    }
                }
            } catch (e: Exception) {
                _error.value = "Unable to process assessment: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun resetAssessment() {
        _assessment.value = null
        _isEmergency.value = false
        _error.value = null
        _isLoading.value = false
    }
}
