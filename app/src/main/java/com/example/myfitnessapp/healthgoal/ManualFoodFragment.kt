package com.example.myfitnessapp.healthgoal

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.R
import com.example.myfitnessapp.models.FoodItem

class ManualFoodFragment : Fragment() {

    private val viewModel: HealthGoalViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (requireActivity().application as HealthPilot).repository
                return HealthGoalViewModel(repository) as T
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manual_food, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            setupUI(view)

            arguments?.let { bundle ->
                bundle.getString("foodName")?.let { view.findViewById<EditText>(R.id.et_name).setText(it) }
                if (bundle.containsKey("calories")) {
                    view.findViewById<EditText>(R.id.et_calories).setText(bundle.getInt("calories").toString())
                }
                if (bundle.containsKey("protein")) {
                    view.findViewById<EditText>(R.id.et_protein).setText(bundle.getDouble("protein").toString())
                }
                if (bundle.containsKey("carbs")) {
                    view.findViewById<EditText>(R.id.et_carbs).setText(bundle.getDouble("carbs").toString())
                }
                if (bundle.containsKey("fat")) {
                    view.findViewById<EditText>(R.id.et_fat).setText(bundle.getDouble("fat").toString())
                }
            }
        }
    }

    private fun setupUI(view: View) {
        val isLoss = viewModel.uiState.value.isWeightLoss
        val primaryColor = ContextCompat.getColor(requireContext(), if (isLoss) R.color.accent_blue else R.color.accent_green)
        
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save).apply {
            backgroundTintList = ColorStateList.valueOf(primaryColor)
            setOnClickListener { saveFood() }
        }

        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        // Setup Category Spinner
        val categories = viewModel.uiState.value.categories.ifEmpty { listOf("Breakfast", "Lunch", "Dinner", "Snacks") }
        val spinner = view.findViewById<AutoCompleteTextView>(R.id.spinner_category)
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        spinner.setAdapter(spinnerAdapter)
    }

    private fun saveFood() {
        val view = view ?: return
        val name = view.findViewById<EditText>(R.id.et_name).text.toString()
        val category = view.findViewById<AutoCompleteTextView>(R.id.spinner_category).text.toString()
        val calories = view.findViewById<EditText>(R.id.et_calories).text.toString().toIntOrNull() ?: 0
        val protein = view.findViewById<EditText>(R.id.et_protein).text.toString().toDoubleOrNull() ?: 0.0
        val carbs = view.findViewById<EditText>(R.id.et_carbs).text.toString().toDoubleOrNull() ?: 0.0
        val fat = view.findViewById<EditText>(R.id.et_fat).text.toString().toDoubleOrNull() ?: 0.0

        if (name.isNotEmpty() && calories > 0) {
            viewModel.addFoodToDaily(
                FoodItem(name = name, calories = calories, protein = protein, carbs = carbs, fat = fat, category = category),
                1.0
            )
            Toast.makeText(requireContext(), "$name added successfully!", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        } else {
            Toast.makeText(requireContext(), "Please enter name and calories", Toast.LENGTH_SHORT).show()
        }
    }
}
