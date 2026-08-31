package com.example.myfitnessapp.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.models.User
import com.example.myfitnessapp.utils.IdealTimeUtils
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val networkObserver = (application as HealthPilot).networkObserver

    private fun checkNetwork(): Boolean {
        if (!networkObserver.isNetworkAvailable()) {
            _authState.value = AuthState.Error("No internet connection. Please check your settings.")
            return false
        }
        return true
    }

    // Temporary storage for signup steps
    var tempName = ""
    var tempEmail = ""
    var tempPhone = ""
    var tempPassword = ""
    var tempImageBase64 = ""
    
    // Step 2 temp fields
    var tempHeight = 0
    var tempWeight = 0.0
    var tempAge = 0
    var tempDob = ""
    var tempGender = ""

    init {
        auth.currentUser?.let { user ->
            if (tempName.isEmpty()) tempName = user.displayName ?: ""
            if (tempEmail.isEmpty()) tempEmail = user.email ?: ""
            if (tempImageBase64.isEmpty()) tempImageBase64 = user.photoUrl?.toString() ?: ""
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    sealed class AuthState {
        object Idle : AuthState()
        object Loading : AuthState()
        object Success : AuthState()
        object NewSocialUser : AuthState()
        object VerificationEmailSent : AuthState()
        object ResetLinkSent : AuthState()
        object PasswordChanged : AuthState()
        object EmailVerified : AuthState()
        data class Error(val message: String) : AuthState()
    }

    fun sendVerificationEmail() {
        if (!checkNetwork()) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // Check if email already exists in Firebase Auth
                val methods = auth.fetchSignInMethodsForEmail(tempEmail).await().signInMethods
                if (!methods.isNullOrEmpty()) {
                    _authState.value = AuthState.Error("Email already registered. Please login instead.")
                    return@launch
                }

                var currentUser = auth.currentUser
                if (currentUser == null || currentUser.email != tempEmail) {
                    val result = auth.createUserWithEmailAndPassword(tempEmail, tempPassword).await()
                    currentUser = result.user
                }

                currentUser?.sendEmailVerification()?.await()
                _authState.value = AuthState.VerificationEmailSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to send verification email")
            }
        }
    }

    fun checkEmailVerification() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val user = auth.currentUser
                user?.reload()?.await()
                if (user?.isEmailVerified == true) {
                    _authState.value = AuthState.EmailVerified
                } else {
                    _authState.value = AuthState.Error("Email not verified yet. Please check your inbox.")
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to check verification")
            }
        }
    }

    fun signUp(
        wakeUpTime: String,
        sleepTime: String,
        workStyle: String,
        workStartTime: String,
        workEndTime: String,
        screenTime: Int
    ) {
        if (!checkNetwork()) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val uid = auth.currentUser?.uid ?: throw Exception("User not authenticated")

                // Double check verification if needed
                auth.currentUser?.reload()?.await()
                if (auth.currentUser?.isEmailVerified != true) {
                    throw Exception("Email not verified")
                }

                val tempUser = User(
                    uid = uid,
                    name = tempName,
                    email = tempEmail,
                    phone = tempPhone,
                    height = tempHeight,
                    weight = tempWeight,
                    age = tempAge,
                    dob = tempDob,
                    gender = tempGender,
                    profileImageUrl = tempImageBase64,
                    wakeUpTime = wakeUpTime,
                    sleepTime = sleepTime,
                    workStyle = workStyle,
                    workStartTime = workStartTime,
                    workEndTime = workEndTime,
                    dailyScreenTime = screenTime
                )

                // Generate initial wellness schedule based on the data
                val wellnessReminders = IdealTimeUtils.generateIdealSchedule(tempUser)
                val user = tempUser.copy(wellnessReminders = wellnessReminders)

                db.collection("users").document(uid).set(user).await()
                _authState.value = AuthState.Success
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Signup failed")
            }
        }
    }

    fun login(email: String, password: String) {
        if (!checkNetwork()) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _authState.value = AuthState.Success
            } catch (e: FirebaseAuthInvalidUserException) {
                _authState.value = AuthState.Error("No account found with this email. Please sign up.")
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _authState.value = AuthState.Error("Invalid email or password. Please try again.")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun signInWithSocial(credential: AuthCredential) {
        if (!checkNetwork()) return
        viewModelScope.launch {
            Log.d("AuthViewModel", "signInWithSocial started")
            _authState.value = AuthState.Loading
            try {
                val result = auth.signInWithCredential(credential).await()
                val user = result.user
                Log.d("AuthViewModel", "Firebase sign-in result user: ${user?.uid}")
                if (user != null) {
                    val doc = db.collection("users").document(user.uid).get().await()
                    if (doc.exists()) {
                        Log.d("AuthViewModel", "User document exists, navigating to Success")
                        _authState.value = AuthState.Success
                    } else {
                        Log.d("AuthViewModel", "New user document, navigating to NewSocialUser")
                        // New user via social login
                        tempName = user.displayName ?: ""
                        tempEmail = user.email ?: ""
                        tempImageBase64 = user.photoUrl?.toString() ?: ""
                        _authState.value = AuthState.NewSocialUser
                    }
                } else {
                    Log.e("AuthViewModel", "User is null after sign-in")
                    _authState.value = AuthState.Error("Failed to get user information")
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Social login exception", e)
                _authState.value = AuthState.Error(e.message ?: "Social login failed")
            }
        }
    }

    fun resetPassword(email: String) {
        if (!checkNetwork()) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                auth.sendPasswordResetEmail(email).await()
                _authState.value = AuthState.ResetLinkSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to send reset email")
            }
        }
    }

    fun changePassword(currentEmail: String, currentPass: String, newPass: String) {
        if (!checkNetwork()) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // Re-authenticate user first
                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(currentEmail, currentPass)
                auth.currentUser?.reauthenticate(credential)?.await()
                
                // Update password
                auth.currentUser?.updatePassword(newPass)?.await()
                _authState.value = AuthState.PasswordChanged
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to change password. Ensure current password is correct.")
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}
