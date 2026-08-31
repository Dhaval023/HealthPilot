package com.example.myfitnessapp.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = requireActivity().getSharedPreferences("MyFitnessPrefs", Context.MODE_PRIVATE)
        val currentGoal = sharedPref.getString("goal_type", "loss")

        // Set initial state
        binding.rbWeightGain.isChecked = currentGoal == "gain"
        binding.rbWeightLoss.isChecked = currentGoal == "loss"

        binding.cardWeightGain.setOnClickListener {
            updateGoal("gain")
        }

        binding.cardWeightLoss.setOnClickListener {
            updateGoal("loss")
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun updateGoal(goal: String) {
        val sharedPref = requireActivity().getSharedPreferences("MyFitnessPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putString("goal_type", goal).apply()
        
        binding.rbWeightGain.isChecked = goal == "gain"
        binding.rbWeightLoss.isChecked = goal == "loss"

        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).update("goalType", goal)
                .addOnSuccessListener {
                    val goalName = "Weight " + goal.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    Toast.makeText(requireContext(), "Goal updated: $goalName", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "Goal updated locally", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
