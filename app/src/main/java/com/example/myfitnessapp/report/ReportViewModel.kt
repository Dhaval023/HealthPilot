package com.example.myfitnessapp.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfitnessapp.models.DailyData
import com.example.myfitnessapp.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class ReportUiState(
    val isLoading: Boolean = false,
    val dailyDataList: List<DailyData> = emptyList(),
    val user: User? = null,
    val rangeType: String = "W",
    val selectedDate: Date = Date(),
    val selectedMetric: String? = null,
    val error: String? = null
)

class ReportViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        fetchInitialData()
    }

    private fun fetchInitialData() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val userSnapshot = db.collection("users").document(uid).get().await()
                val user = userSnapshot.toObject(User::class.java)
                _uiState.update { it.copy(user = user) }
                fetchData("W", Date())
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun setSelectedMetric(metric: String?) {
        _uiState.update { it.copy(selectedMetric = metric) }
    }

    fun setRange(range: String) {
        _uiState.update { it.copy(rangeType = range) }
        fetchData(range, _uiState.value.selectedDate)
    }

    fun setDate(date: Date) {
        _uiState.update { it.copy(selectedDate = date) }
        fetchData(_uiState.value.rangeType, date)
    }

    private fun fetchData(range: String, baseDate: Date) {
        val uid = auth.currentUser?.uid ?: return
        val days = when (range) {
            "D" -> 1
            "W" -> 7
            "M" -> 30
            else -> 7
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val startTime = System.currentTimeMillis()
            try {
                val calendar = Calendar.getInstance()
                calendar.time = baseDate
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val endDate = dateFormat.format(calendar.time)
                
                calendar.add(Calendar.DAY_OF_YEAR, -(days - 1))
                val startDate = dateFormat.format(calendar.time)
                
                val snapshot = db.collection("users").document(uid)
                    .collection("daily_data")
                    .whereGreaterThanOrEqualTo("date", startDate)
                    .whereLessThanOrEqualTo("date", endDate)
                    .get()
                    .await()
                
                val dataList = snapshot.toObjects(DailyData::class.java)
                
                val fullList = mutableListOf<DailyData>()
                val calendarLoop = Calendar.getInstance()
                calendarLoop.time = baseDate
                calendarLoop.add(Calendar.DAY_OF_YEAR, -(days - 1))
                
                for (i in 0 until days) {
                    val d = dateFormat.format(calendarLoop.time)
                    val existing = dataList.find { it.date == d }
                    fullList.add(existing ?: DailyData(date = d))
                    calendarLoop.add(Calendar.DAY_OF_YEAR, 1)
                }
                
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime < 500) {
                    kotlinx.coroutines.delay(500 - elapsedTime)
                }
                _uiState.update { it.copy(isLoading = false, dailyDataList = fullList, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
