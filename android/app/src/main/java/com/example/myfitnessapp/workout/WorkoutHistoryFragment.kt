package com.example.myfitnessapp.workout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myfitnessapp.R
import com.example.myfitnessapp.data.workout.WorkoutRecord
import com.example.myfitnessapp.databinding.FragmentWorkoutHistoryBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import java.util.*

class WorkoutHistoryFragment : Fragment() {

    private var _binding: FragmentWorkoutHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: WorkoutHistoryAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupCalendar()
        
        binding.toolbar.setNavigationOnClickListener { 
            findNavController().previousBackStackEntry?.savedStateHandle?.set("show_workout_list", true)
            findNavController().popBackStack() 
        }

        // Initial load for today
        loadWorkoutsForDate(getStartOfDay(System.currentTimeMillis()))
    }

    private fun setupRecyclerView() {
        adapter = WorkoutHistoryAdapter { record ->
            val bundle = Bundle().apply { 
                putString("workoutId", record.id)
                putString("workoutType", record.workoutType)
            }
            findNavController().navigate(R.id.action_workoutHistoryFragment_to_workoutDetailFragment, bundle)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter
    }

    private fun setupCalendar() {
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            val startOfDay = getStartOfDay(calendar.timeInMillis)
            loadWorkoutsForDate(startOfDay)
        }
    }

    private fun loadWorkoutsForDate(date: Long) {
        val userId = auth.currentUser?.uid ?: return
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(date))
        android.util.Log.d("WorkoutHistory", "Loading workouts for date: $dateStr ($date)")
        
        db.collectionGroup("dailyRecords")
            .whereEqualTo("userId", userId)
            .whereEqualTo("date", date)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("WorkoutHistory", "Error loading workouts: ${error.message}", error)
                    if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                        Toast.makeText(requireContext(), "Fetching history requires a database index. Please check Logcat for the link.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to load history: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }
                
                val workouts = snapshot?.toObjects(WorkoutRecord::class.java) ?: emptyList()
                android.util.Log.d("WorkoutHistory", "Found ${workouts.size} workouts")
                adapter.submitList(workouts)
                binding.tvNoWorkouts.visibility = if (workouts.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
