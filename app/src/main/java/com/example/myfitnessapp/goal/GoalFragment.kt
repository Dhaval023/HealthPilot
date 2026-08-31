package com.example.myfitnessapp.goal

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentGoalBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.NumberFormat
import java.util.Locale
import java.util.ArrayList

class GoalFragment : Fragment() {

    private var _binding: FragmentGoalBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GoalViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (requireActivity().application as HealthPilot).repository
                return GoalViewModel(repository) as T
            }
        }
    }

    private var isUpdatingUI = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGoalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.post {
            setupUI()
            setupObservers()
            setupTextWatchers()
            setupClickListeners()
            setupBMIGraph()
        }
    }

    private fun setupUI() {
        // Initially disable save button until changes are made
        binding.btnSaveGoal.isEnabled = false
        binding.btnSaveGoal.alpha = 0.5f

        binding.btnMenu.setOnClickListener {
            (activity as? com.example.myfitnessapp.MainActivity)?.openDrawer()
        }
    }

    private fun setupObservers() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            state ?: return@observe

            updateUI(state)

            // Show skeleton/loading
            val skeleton = binding.root.findViewById<View>(R.id.layout_skeleton)
            val mainContent = binding.root.findViewById<View>(R.id.main_content)
            val shimmerContainer = binding.root.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_view_container)

            if (state.isLoading) {
                skeleton?.visibility = View.VISIBLE
                mainContent?.visibility = View.GONE
                shimmerContainer?.startShimmer()
                binding.progressBarLoading.visibility = View.GONE // Hide old loader if it exists
            } else {
                skeleton?.visibility = View.GONE
                mainContent?.visibility = View.VISIBLE
                shimmerContainer?.stopShimmer()
                binding.progressBarLoading.visibility = View.GONE
            }

            // Show error
            if (state.error != null) {
                Toast.makeText(requireContext(), state.error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUI(state: GoalState) {
        isUpdatingUI = true
        // Update BMI
        binding.tvBmiValue.text = String.format(Locale.US, "%.2f", state.bmi)
        binding.tvBmiCategory.text = state.bmiCategory

        // Update BMI status color
        val color = when (state.bmiCategory) {
            "Severe Thinness", "Moderate Thinness", "Mild Thinness" ->
                ContextCompat.getColor(requireContext(), R.color.bmi_underweight)
            "Normal" ->
                ContextCompat.getColor(requireContext(), R.color.bmi_normal)
            "Overweight" ->
                ContextCompat.getColor(requireContext(), R.color.bmi_overweight)
            else ->
                ContextCompat.getColor(requireContext(), R.color.bmi_obese)
        }
        binding.tvBmiCategory.setTextColor(color)
        binding.bmiIndicator.setBackgroundColor(color)

        // Update Goals - Only if not focused to avoid typing issues
        if (!binding.etTargetCalories.isFocused) {
            val caloriesStr = state.targetCalories.toString()
            if (binding.etTargetCalories.text.toString() != caloriesStr) {
                binding.etTargetCalories.setText(caloriesStr)
            }
        }
        
        if (!binding.etTargetSteps.isFocused) {
            val stepsStr = state.targetSteps.toString()
            if (binding.etTargetSteps.text.toString() != stepsStr) {
                binding.etTargetSteps.setText(stepsStr)
            }
        }
        
        if (!binding.etTargetDistance.isFocused) {
            val distStr = String.format(Locale.US, "%.2f", state.targetDistanceKm)
            if (binding.etTargetDistance.text.toString() != distStr) {
                binding.etTargetDistance.setText(distStr)
            }
        }

        // Update Maintenance Calories
        binding.tvMaintenanceCalories.text = String.format("%s kcal", formatNumber(state.maintenanceCalories))

        // Update Daily Burn
        binding.tvDailyBurn.text = String.format("%s kcal", formatNumber(state.dailyCalorieBurnEstimate))

        // Update Progress
        val progressPercent = (state.goalProgress * 100).toInt()
        binding.progressBarGoal.progress = progressPercent
        binding.tvProgressPercent.text = String.format("%d%%", progressPercent)

        val remaining = maxOf(0, state.targetSteps - state.currentSteps)
        binding.tvStepsRemaining.text = String.format("%s steps remaining", formatNumber(remaining))

        // Update Weekly Goals (Fancy List)
        updateWeeklyGoalsList(state.weeklyGoals)

        // Update BMI Graph
        updateBMIGraph(state.bmi, state.bmiCategory)

        isUpdatingUI = false
    }

    private fun updateWeeklyGoalsList(weeklyGoals: List<DailyGoal>) {
        binding.llWeeklyGoals.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        val currentDay = java.util.Calendar.getInstance().getDisplayName(
            java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SHORT, java.util.Locale.US
        )

        weeklyGoals.forEach { goal ->
            val itemView = inflater.inflate(R.layout.item_weekly_goal, binding.llWeeklyGoals, false)
            
            val tvDay = itemView.findViewById<TextView>(R.id.tv_day)
            val tvSteps = itemView.findViewById<TextView>(R.id.tv_steps)
            val tvVariance = itemView.findViewById<TextView>(R.id.tv_variance)
            val tvDistance = itemView.findViewById<TextView>(R.id.tv_distance)
            val tvCalories = itemView.findViewById<TextView>(R.id.tv_calories)
            val container = itemView.findViewById<View>(R.id.container_weekly)
            val restIndicator = itemView.findViewById<View>(R.id.rest_day_indicator)

            tvDay.text = goal.day
            tvSteps.text = String.format("%s steps", formatNumber(goal.steps))
            tvVariance.text = goal.variance
            tvDistance.text = String.format(Locale.US, "%.2f km", goal.distance)
            tvCalories.text = String.format(Locale.US, "%d kcal", goal.calories)

            if (goal.day == currentDay) {
                container.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_dark_highlight))
                tvDay.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_blue))
            }

            if (goal.isRestDay) {
                restIndicator.visibility = View.VISIBLE
                tvVariance.text = "Rest Day"
                tvVariance.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_pink))
            } else {
                restIndicator.visibility = View.GONE
                tvVariance.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray))
            }

            binding.llWeeklyGoals.addView(itemView)
        }
    }

    private fun setupTextWatchers() {
        binding.etTargetCalories.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingUI || !binding.etTargetCalories.isFocused) return
                val calories = s.toString().toIntOrNull() ?: return
                viewModel.updateGoalsByCalories(calories)
                enableSaveButton()
            }
        })

        binding.etTargetSteps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingUI || !binding.etTargetSteps.isFocused) return
                val steps = s.toString().toIntOrNull() ?: return
                viewModel.updateGoalsBySteps(steps)
                enableSaveButton()
            }
        })

        binding.etTargetDistance.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingUI || !binding.etTargetDistance.isFocused) return
                val distance = s.toString().toDoubleOrNull() ?: return
                viewModel.updateGoalsByDistance(distance)
                enableSaveButton()
            }
        })
    }

    private fun enableSaveButton() {
        binding.btnSaveGoal.isEnabled = true
        binding.btnSaveGoal.alpha = 1.0f
    }

    private fun setupClickListeners() {
        binding.btnSaveGoal.setOnClickListener {
            saveGoals()
        }

        binding.btnResetToRecommended.setOnClickListener {
            viewModel.resetToRecommended()
            Toast.makeText(requireContext(), "Reset to recommended goals", Toast.LENGTH_SHORT).show()
        }

        binding.btnCalculateBmi.setOnClickListener {
            Toast.makeText(requireContext(), "Update your profile to recalculate BMI", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveGoals() {
        val calories = binding.etTargetCalories.text.toString().toIntOrNull() ?: return
        
        viewModel.updateGoalsByCalories(calories)
        viewModel.saveGoals()

        binding.btnSaveGoal.isEnabled = false
        binding.btnSaveGoal.alpha = 0.5f

        Toast.makeText(requireContext(), "Goals saved successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun setupBMIGraph() {
        binding.bmiChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setPinchZoom(false)
            setScaleEnabled(false)
            setTouchEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                valueFormatter = IndexAxisValueFormatter(
                    arrayOf("Underweight", "Normal", "Overweight", "Obese")
                )
                textSize = 10f
                textColor = ContextCompat.getColor(context, R.color.text_gray)
                labelRotationAngle = -45f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                axisMaximum = 40f
                textSize = 10f
                textColor = ContextCompat.getColor(context, R.color.text_gray)
            }

            axisRight.isEnabled = false
        }
    }

    private fun updateBMIGraph(bmi: Double, category: String) {
        val entries = ArrayList<BarEntry>()

        // Represent BMI across the 4 standard categories
        val underweight = minOf(18.5f, bmi.toFloat())
        val normal = if (bmi > 18.5) minOf((bmi - 18.5).toFloat(), 6.5f) else 0f
        val overweight = if (bmi > 25) minOf((bmi - 25).toFloat(), 5.0f) else 0f
        val obese = if (bmi > 30) (bmi - 30).toFloat() else 0f

        entries.add(BarEntry(0f, underweight))
        entries.add(BarEntry(1f, normal))
        entries.add(BarEntry(2f, overweight))
        entries.add(BarEntry(3f, obese))

        val dataSet = BarDataSet(entries, "BMI Distribution")
        dataSet.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.bmi_underweight),
            ContextCompat.getColor(requireContext(), R.color.bmi_normal),
            ContextCompat.getColor(requireContext(), R.color.bmi_overweight),
            ContextCompat.getColor(requireContext(), R.color.bmi_obese)
        )
        dataSet.setDrawValues(true)
        dataSet.valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_white)
        dataSet.valueTextSize = 10f

        val data = BarData(dataSet)
        data.barWidth = 0.5f

        binding.bmiChart.data = data
        binding.bmiChart.animateY(1000)
        
        // Show marker/indicator if needed, but the bars themselves now show the value
        binding.bmiChart.invalidate()
    }

    private fun formatNumber(number: Int): String {
        return NumberFormat.getNumberInstance(Locale.US).format(number)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
