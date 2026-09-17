package com.example.myfitnessapp.workout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.R
import com.example.myfitnessapp.data.workout.WorkoutRecord
import com.example.myfitnessapp.databinding.FragmentWorkoutDetailBinding
import com.example.myfitnessapp.databinding.ItemSummaryStatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class WorkoutDetailFragment : Fragment() {

    private var _binding: FragmentWorkoutDetailBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val workoutId = arguments?.getString("workoutId") ?: ""
        val workoutType = arguments?.getString("workoutType") ?: ""
        
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        
        binding.btnShare.setOnClickListener {
            val bundle = Bundle().apply {
                putString("workoutId", workoutId)
                putString("workoutType", workoutType)
            }
            findNavController().navigate(R.id.action_workoutDetailFragment_to_workoutShareFragment, bundle)
        }

        if (workoutId.isNotEmpty() && workoutType.isNotEmpty()) {
            loadWorkoutDetails(workoutId, workoutType)
        }
    }

    private fun loadWorkoutDetails(id: String, type: String) {
        val userId = auth.currentUser?.uid ?: return
        
        db.collection("users").document(userId)
            .collection("workouts").document(type)
            .collection("dailyRecords").document(id)
            .get()
            .addOnSuccessListener { document ->
                val workout = document.toObject(WorkoutRecord::class.java)
                workout?.let { displayWorkout(it) }
            }
    }

    private fun displayWorkout(it: WorkoutRecord) {
        val context = requireContext()
        val nameResId = context.resources.getIdentifier(it.workoutType.lowercase().replace(" ", "_").replace("-", "_"), "string", context.packageName)
        binding.tvWorkoutName.text = if (nameResId != 0) context.getString(nameResId) else it.workoutType
        
        val dateSdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        binding.tvDate.text = dateSdf.format(Date(it.startTime))
        binding.tvTime.text = timeSdf.format(Date(it.startTime))

        val iconRes = when (it.workoutType.lowercase()) {
            "walking" -> R.drawable.ic_walk
            "running" -> R.drawable.running
            "cycling" -> R.drawable.cycling
            "yoga" -> R.drawable.yoga
            "boxing" -> R.drawable.boxing
            "swimming" -> R.drawable.swimming
            "badminton" -> R.drawable.badminton
            else -> R.drawable.ic_walk
        }
        binding.ivWorkoutIcon.setImageResource(iconRes)

        // Fill stats
        val durationBinding = ItemSummaryStatBinding.bind(binding.cardDuration.root)
        durationBinding.ivStatIcon.setImageResource(R.drawable.ic_refresh)
        durationBinding.tvStatLabel.text = getString(R.string.duration)
        val minutes = it.duration / 60000
        val seconds = (it.duration % 60000) / 1000
        durationBinding.tvStatValue.text = if (minutes > 0) {
            getString(R.string.unit_min, minutes)
        } else {
            getString(R.string.unit_sec, seconds)
        }

        val stepsBinding = ItemSummaryStatBinding.bind(binding.cardSteps.root)
        stepsBinding.ivStatIcon.setImageResource(R.drawable.ic_walk)
        stepsBinding.tvStatLabel.text = getString(R.string.steps)
        stepsBinding.tvStatValue.text = it.totalSteps.toString()

        val distanceBinding = ItemSummaryStatBinding.bind(binding.cardDistance.root)
        distanceBinding.ivStatIcon.setImageResource(R.drawable.ic_location)
        distanceBinding.tvStatLabel.text = getString(R.string.distance)
        distanceBinding.tvStatValue.text = getString(R.string.unit_km, it.totalDistance)

        val caloriesBinding = ItemSummaryStatBinding.bind(binding.cardCalories.root)
        caloriesBinding.ivStatIcon.setImageResource(R.drawable.ic_fire)
        caloriesBinding.tvStatLabel.text = getString(R.string.calories_caps)
        caloriesBinding.tvStatValue.text = getString(R.string.unit_kcal, it.caloriesBurned)

        val heartBinding = ItemSummaryStatBinding.bind(binding.cardHeartRate.root)
        heartBinding.ivStatIcon.setImageResource(R.drawable.ic_heart)
        heartBinding.ivStatIcon.imageTintList = android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.accent_pink)
        )
        heartBinding.tvStatLabel.text = getString(R.string.heart_rate)
        heartBinding.tvStatValue.text = it.averageHeartRate?.toString() ?: "--"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
