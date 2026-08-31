package com.example.myfitnessapp.chat

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.models.ChatMessage
import com.example.myfitnessapp.models.DailyData
import com.example.myfitnessapp.models.User
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val networkObserver =
        (application as HealthPilot).networkObserver

    private fun checkNetwork(): Boolean {
        return networkObserver.isNetworkAvailable()
    }


    private val _messages = MutableLiveData<List<ChatMessage>>(
        listOf(
            ChatMessage(
                "Hello! I'm Ved, your AI health assistant. How can I help you today?",
                false
            )
        )
    )

    val messages: LiveData<List<ChatMessage>> = _messages

    private val _isLoading = MutableLiveData(false)

    val isLoading: LiveData<Boolean> = _isLoading

    // SPEECH RECOGNITION
    private val _isListening = MutableLiveData(false)

    val isListening: LiveData<Boolean> = _isListening

    // USER DATA
    private val _userName = MutableLiveData("User")

    val userName: LiveData<String> = _userName

    private val _userProfileImage = MutableLiveData<String?>(null)

    val userProfileImage: LiveData<String?> = _userProfileImage

    // FIREBASE DATA
    private var currentUserData: User? = null

    private var todayHealthData: DailyData? = null

    // GEMINI CONFIGURATION
    private val apiKey = ""

    private val geminiModelName = "gemini-3.5-flash-lite"

    // SPEECH RECOGNIZER
    private val speechRecognizer: SpeechRecognizer by lazy {
        SpeechRecognizer.createSpeechRecognizer(getApplication())
    }

    // TEXT TO SPEECH
    private var tts: TextToSpeech? = null

    private val _isSpeaking = MutableLiveData(false)

    val isSpeaking: LiveData<Boolean> = _isSpeaking

    // INITIALIZATION
    init {
        loadUserData()
        setupSpeechRecognizer()
        setupTTS()
    }

    // TEXT TO SPEECH
    private fun setupTTS() {

        tts = TextToSpeech(getApplication()) { status ->

            if (status != TextToSpeech.ERROR) {

                tts?.language = Locale.getDefault()

                tts?.setOnUtteranceProgressListener(
                    object : android.speech.tts.UtteranceProgressListener() {

                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.postValue(true)
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.postValue(false)
                        }

                        override fun onError(utteranceId: String?) {
                            _isSpeaking.postValue(false)
                        }
                    }
                )
            }
        }
    }


    private fun speak(text: String) {

        if (text.isBlank()) {
            return
        }

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "ChatResponse"
        )
    }


    fun stopSpeaking() {

        tts?.stop()

        _isSpeaking.value = false
    }

    // SPEECH RECOGNITION
    private fun setupSpeechRecognizer() {

        speechRecognizer.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                }

                override fun onBeginningOfSpeech() {
                }

                override fun onRmsChanged(rmsdB: Float) {
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                }

                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {

                    _isListening.value = false

                    val errorMessage = when (error) {

                        SpeechRecognizer.ERROR_AUDIO ->
                            "Audio recording error"

                        SpeechRecognizer.ERROR_CLIENT ->
                            "Client side error"

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Insufficient permissions"

                        SpeechRecognizer.ERROR_NETWORK ->
                            "Network error"

                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Network timeout"

                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "No match found"

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Recognition service busy"

                        SpeechRecognizer.ERROR_SERVER ->
                            "Server error"

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No speech input"

                        else ->
                            "Unknown speech error"
                    }

                    if (
                        error != SpeechRecognizer.ERROR_NO_MATCH &&
                        error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {

                        val updatedList =
                            _messages.value.orEmpty().toMutableList()

                        updatedList.add(
                            ChatMessage(
                                "Speech Error: $errorMessage",
                                false
                            )
                        )

                        _messages.postValue(updatedList)
                    }
                }


                override fun onResults(results: Bundle?) {

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    if (!matches.isNullOrEmpty()) {

                        val recognizedText = matches[0]

                        sendMessage(
                            recognizedText,
                            isVoiceInput = true
                        )
                    }
                }


                override fun onPartialResults(
                    partialResults: Bundle?
                ) {
                }


                override fun onEvent(
                    eventType: Int,
                    params: Bundle?
                ) {
                }
            }
        )
    }


    fun startListening() {

        if (!SpeechRecognizer.isRecognitionAvailable(getApplication())) {

            val updatedList =
                _messages.value.orEmpty().toMutableList()

            updatedList.add(
                ChatMessage(
                    "Speech recognition is not available on this device.",
                    false
                )
            )

            _messages.value = updatedList

            return
        }


        if (_isListening.value == true) {
            return
        }


        //Stop Ved from speaking while
        stopSpeaking()

        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault()
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    1
                )
            }

        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            val updatedList =
                _messages.value.orEmpty().toMutableList()

            updatedList.add(
                ChatMessage(
                    "Unable to start speech recognition.",
                    false
                )
            )
            _messages.value = updatedList
        }
    }

    fun stopListening() {

        if (_isListening.value == true) {

            try {

                speechRecognizer.stopListening()

            } catch (e: Exception) {

                e.printStackTrace()
            }

            _isListening.value = false
        }
    }

    // LOAD FIREBASE USER DATA
    private fun loadUserData() {

        if (!checkNetwork()) {
            return
        }

        val userId =
            FirebaseAuth.getInstance()
                .currentUser
                ?.uid
                ?: return

        viewModelScope.launch {
            try {

                val db =
                    FirebaseFirestore.getInstance()

                // USER PROFILE
                val userDoc =
                    db.collection("users")
                        .document(userId)
                        .get()
                        .await()

                currentUserData =
                    userDoc.toObject(User::class.java)

                _userName.value =
                    currentUserData?.name ?: "User"

                _userProfileImage.value =
                    currentUserData?.profileImageUrl

                // TODAY'S HEALTH DATA
                val today =
                    SimpleDateFormat(
                        "yyyy-MM-dd",
                        Locale.US
                    ).format(Date())

                val dailyDoc =
                    db.collection("users")
                        .document(userId)
                        .collection("daily_data")
                        .document(today)
                        .get()
                        .await()

                todayHealthData =
                    dailyDoc.toObject(DailyData::class.java)

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }

    // SEND MESSAGE
    fun sendMessage(
        text: String,
        isVoiceInput: Boolean = false
    ) {

        if (text.isBlank()) {
            return
        }

        // NETWORK CHECK
        if (!checkNetwork()) {

            val updatedList =
                _messages.value.orEmpty().toMutableList()

            updatedList.add(
                ChatMessage(
                    "No internet connection. Please check your settings.",
                    false
                )
            )

            _messages.value = updatedList

            return
        }

        // ADD USER MESSAGE
        val userMessage =
            ChatMessage(
                text.trim(),
                true
            )

        val currentList =
            _messages.value.orEmpty().toMutableList()

        currentList.add(userMessage)

        _messages.value = currentList

        _isLoading.value = true


        // CALL GEMINI
        viewModelScope.launch {

            try {

                val responseText =
                    callGemini(text.trim())

                val aiMessage =
                    ChatMessage(
                        responseText,
                        false
                    )

                val updatedList =
                    _messages.value.orEmpty().toMutableList()

                updatedList.add(aiMessage)

                _messages.value = updatedList

                // VOICE RESPONSE
                if (isVoiceInput) {
                    speak(responseText)
                }

            } catch (e: Exception) {
                e.printStackTrace()

                val errorText =
                    getFriendlyApiError(e)

//                val errorText =
//                    "Gemini Error:\n${e.message ?: e.localizedMessage ?: "Unknown error"}"

                val updatedList =
                    _messages.value.orEmpty().toMutableList()

                updatedList.add(
                    ChatMessage(
                        errorText,
                        false
                    )
                )

                _messages.value = updatedList

            } finally {
                _isLoading.value = false
            }
        }
    }

    // SYSTEM PROMPT
    private fun getSystemPrompt(): String {

        val user = currentUserData
        val daily = todayHealthData
        val context = StringBuilder()

        context.append(
            """
            You are Ved, a helpful AI health and fitness assistant
            inside the HealthPilot Android app.

            Your job is to provide simple, useful and personalized
            health and fitness guidance.

            IMPORTANT RESPONSE RULES:
            - Keep responses short.
            - Normally respond in 1-5 short sentences.
            - Be direct and easy to understand.
            - Do not use unnecessary markdown.
            - Do not repeat the user's question.
            - Use the user's HealthPilot data when relevant.
            - Never invent health data.
            - If required data is missing, say so briefly.
            - Do not give dangerous or extreme health advice.
            - For serious medical symptoms, recommend speaking with
              a qualified healthcare professional.
            """.trimIndent()
        )

        // USER PROFILE

        if (user != null) {
            context.append(
                """
                User Profile:
                Name: ${user.name}
                Age: ${user.age}
                Gender: ${user.gender}
                Height: ${user.height} cm
                Weight: ${user.weight} kg
                Goal: ${user.goalType}
                Target Calories: ${user.targetCalories} kcal
                Target Steps: ${user.targetSteps}
                """.trimIndent()
            )
        }

        // TODAY DATA
        if (daily != null) {
            context.append(
                """
                Today's Health Data:
                Steps: ${daily.steps}
                Calories Consumed: ${daily.caloriesConsumed} kcal
                Water: ${daily.waterIntakeL} L
                Sleep: ${daily.sleepHours} hours
                """.trimIndent()
            )
        }

        context.append(
            """
            Give personalized advice based on this information
            whenever it is relevant.
            """.trimIndent()
        )
        return context.toString()
    }

    // GEMINI API
    private suspend fun callGemini(
        userText: String
    ): String {

        if (
            apiKey.isBlank() ||
            apiKey == "GEMINI_API_KEY"
        ) {

            throw Exception(
                "Gemini API key is not configured."
            )
        }

        val generativeModel =
            GenerativeModel(

                modelName = geminiModelName,
                apiKey = apiKey,
                systemInstruction = content {
                    text(
                        getSystemPrompt()
                    )
                }
            )

        /*
         * Send only recent conversation history.
         * This prevents the request from becoming unnecessarily
         * large after a long conversation.
         */

        val history =
            _messages.value.orEmpty()
                .takeLast(10)

        /*
         * Build a conversation prompt.
         * This keeps the existing ChatMessage model unchanged.
         */

        val conversation = StringBuilder()

        for (message in history) {
            if (message.isUser) {
                conversation.append(
                    "User: ${message.text}\n"
                )

            } else {
                conversation.append(
                    "Ved: ${message.text}\n"
                )
            }
        }

        conversation.append(
            "User: $userText"
        )

        val response = generativeModel.generateContent(
                conversation.toString()
            )

        return response.text?.trim()
            ?: "Sorry, I couldn't generate a response right now."
    }

    // FRIENDLY API ERROR

    private fun getFriendlyApiError(
        exception: Exception
    ): String {

        val message =
            exception.localizedMessage
                ?: exception.message
                ?: ""

        val lowerMessage =
            message.lowercase()

        return when {
            lowerMessage.contains("403") ->
                "Gemini API access is not available for this API key or project."

            lowerMessage.contains("429") ->
                "Ved is temporarily busy. Please try again in a moment."

            lowerMessage.contains("401") ->
                "Gemini API key is invalid. Please check your API key."

            lowerMessage.contains("400") ->
                "Ved couldn't process that request. Please try again."

            lowerMessage.contains("network") ->
                "Network error. Please check your internet connection."

            lowerMessage.contains("timeout") ->
                "The request took too long. Please try again."

            lowerMessage.contains("api key") ->
                "Gemini API key is not configured correctly."

            else ->
                "Sorry, I couldn't connect to Ved right now. Please try again."
        }
    }

    // CLEANUP
    override fun onCleared() {
        super.onCleared()
        try {
            speechRecognizer.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            tts?.stop()
            tts?.shutdown()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}