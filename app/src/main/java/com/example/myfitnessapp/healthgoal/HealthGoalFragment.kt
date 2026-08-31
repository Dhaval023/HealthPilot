package com.example.myfitnessapp.healthgoal

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.R
import com.example.myfitnessapp.models.LoggedFood
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class HealthGoalFragment : Fragment() {

    private val viewModel: HealthGoalViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (requireActivity().application as HealthPilot).repository
                @Suppress("UNCHECKED_CAST")
                return HealthGoalViewModel(repository) as T
            }
        }
    }

    private var rootView: View? = null
    private var isShowingAllFoods = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootView = inflater.inflate(R.layout.fragment_health_goal, container, false)
        return rootView!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initReminderManager(requireContext())
        
        view.post {
            setupUI(view)
            observeViewModel()
        }
    }

    private fun setupUI(view: View) {
        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        
        view.findViewById<View>(R.id.btn_menu).setOnClickListener {
            (activity as? com.example.myfitnessapp.MainActivity)?.openDrawer()
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tabLayout.tag == "loading") return
                val isLoss = tab?.position == 0
                if (viewModel.uiState.value.isWeightLoss != isLoss) {
                    viewModel.setGoalType(isLoss)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        setupDailyTargetIcons(view)
        setupProgressRowIcons(view)
        setupInteractiveCards(view)

        view.findViewById<View>(R.id.btn_nav_add_food).setOnClickListener {
            findNavController().navigate(R.id.action_healthGoalFragment_to_addFoodFragment)
        }

        view.findViewById<View>(R.id.btn_save_health_goals).setOnClickListener {
            val targetWeight = view.findViewById<EditText>(R.id.et_target_weight).text.toString().toDoubleOrNull() ?: 0.0
            viewModel.saveGoals(targetWeight)
            Toast.makeText(requireContext(), "Goals Saved!", Toast.LENGTH_SHORT).show()
        }

        setupCalculationTriggers(view)

        view.findViewById<View>(R.id.btn_edit_target_weight).setOnClickListener {
            showSetTargetWeightDialog()
        }

        view.findViewById<View>(R.id.tv_weight_current).setOnClickListener {
            showUpdateCurrentWeightDialog()
        }

        view.findViewById<View>(R.id.btn_add_water_quick)?.setOnClickListener {
            viewModel.addWater(0.25)
            Toast.makeText(requireContext(), "Added 250ml water", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btn_remove_water_quick)?.setOnClickListener {
            viewModel.addWater(-0.25)
            Toast.makeText(requireContext(), "Removed 250ml water", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<TextView>(R.id.tv_view_more_foods)?.setOnClickListener {
            isShowingAllFoods = !isShowingAllFoods
            (it as TextView).text = if (isShowingAllFoods) "Show Less" else "View More"
            renderLoggedFoods(viewModel.uiState.value.loggedFoods)
        }
    }

    private fun showUpdateCurrentWeightDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_set_target_weight, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_title)
        tvTitle.text = "Weight Entry"
        
        val etInput = dialogView.findViewById<EditText>(R.id.et_target_weight_input)
        etInput.hint = "Current Weight (kg)"
        etInput.setText(viewModel.uiState.value.currentWeight.toString())

        val tilStartWeight = dialogView.findViewById<View>(R.id.til_start_weight)
        tilStartWeight.visibility = View.VISIBLE
        val etStartWeightInput = dialogView.findViewById<EditText>(R.id.et_start_weight_input)
        etStartWeightInput.setText(viewModel.uiState.value.startWeight.toString())

        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val currentW = etInput.text.toString().toDoubleOrNull()
            val startW = etStartWeightInput.text.toString().toDoubleOrNull()
            
            if (currentW != null) viewModel.updateCurrentWeight(currentW)
            if (startW != null) viewModel.updateStartWeight(startW)
            
            dialog.dismiss()
            Toast.makeText(requireContext(), "Weights Updated!", Toast.LENGTH_SHORT).show()
        }

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun setupCalculationTriggers(view: View) {
        val etTargetWeight = view.findViewById<EditText>(R.id.et_target_weight)
        val etDuration = view.findViewById<EditText>(R.id.et_duration_weeks)

        etTargetWeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val weight = s.toString().toDoubleOrNull()
                if (weight != null && weight != viewModel.uiState.value.targetWeight) {
                    viewModel.updateTargetWeight(weight)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etDuration.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val weeks = s.toString().toIntOrNull()
                if (weeks != null && weeks != viewModel.uiState.value.durationWeeks) {
                    viewModel.updateDuration(weeks)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showSetTargetWeightDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_set_target_weight, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val etInput = dialogView.findViewById<EditText>(R.id.et_target_weight_input)
        val currentTarget = viewModel.uiState.value.targetWeight
        if (currentTarget > 0) {
            etInput.setText(currentTarget.toString())
        }

        dialogView.findViewById<View>(R.id.btn_save).setOnClickListener {
            val weight = etInput.text.toString().toDoubleOrNull()
            if (weight != null) {
                viewModel.saveGoals(weight)
                dialog.dismiss()
                Toast.makeText(requireContext(), "Goal Updated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Please enter a valid weight", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupInteractiveCards(view: View) {
        view.findViewById<View>(R.id.row_water).setOnClickListener { showWaterDialog() }
        view.findViewById<View>(R.id.card_water_intake).setOnClickListener { showWaterDialog() }
    }

    private fun showWaterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_water, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme).setView(dialogView).create()
        
        dialogView.findViewById<View>(R.id.btn_250ml).setOnClickListener { viewModel.addWater(0.25); dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_500ml).setOnClickListener { viewModel.addWater(0.5); dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_750ml).setOnClickListener { viewModel.addWater(0.75); dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_1l).setOnClickListener { viewModel.addWater(1.0); dialog.dismiss() }
        dialogView.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }

    private fun renderLoggedFoods(foods: List<LoggedFood>) {
        val container = rootView?.findViewById<LinearLayout>(R.id.ll_logged_foods) ?: return
        container.removeAllViews()
        
        val tvViewMore = rootView?.findViewById<TextView>(R.id.tv_view_more_foods)
        if (foods.size > 4) {
            tvViewMore?.visibility = View.VISIBLE
        } else {
            tvViewMore?.visibility = View.GONE
        }

        val displayFoods = if (isShowingAllFoods) foods else foods.take(4)

        displayFoods.forEach { food ->
            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_logged_food_v2, container, false)
            
            itemView.findViewById<TextView>(R.id.tv_food_name).text = "${food.name} x${food.quantity}"
            itemView.findViewById<TextView>(R.id.tv_food_calories).text = "${food.calories} kcal"
            
            itemView.findViewById<View>(R.id.btn_remove).setOnClickListener {
                val confirmView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_remove, null)
                val confirmDialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
                    .setView(confirmView)
                    .create()

                confirmView.findViewById<TextView>(R.id.tv_title).text = "Remove Food?"
                confirmView.findViewById<TextView>(R.id.tv_message).text = "Do you want to remove ${food.name} from today's log?"

                confirmView.findViewById<View>(R.id.btn_confirm).setOnClickListener {
                    viewModel.removeFood(food)
                    confirmDialog.dismiss()
                }
                confirmView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
                    confirmDialog.dismiss()
                }
                confirmDialog.show()
            }
            container.addView(itemView)
        }
    }

    private fun setupDailyTargetIcons(view: View) {
        setTargetInfo(view.findViewById(R.id.target_cal), R.drawable.ic_fire, "kcal")
        setTargetInfo(view.findViewById(R.id.target_steps), R.drawable.ic_walk, "steps")
        setTargetInfo(view.findViewById(R.id.target_dist), R.drawable.ic_location, "km")
        setTargetInfo(view.findViewById(R.id.target_water), R.drawable.ic_water, "L")
        setTargetInfo(view.findViewById(R.id.target_sleep), R.drawable.ic_sleep, "hrs")
    }

    private fun setTargetInfo(v: View, iconRes: Int, unit: String) {
        v.findViewById<ImageView>(R.id.iv_icon).setImageResource(iconRes)
        v.findViewById<TextView>(R.id.tv_unit).text = unit
    }

    private fun setupProgressRowIcons(view: View) {
        setProgressRowInfo(view.findViewById(R.id.row_steps), R.drawable.ic_walk, "Steps")
        setProgressRowInfo(view.findViewById(R.id.row_dist), R.drawable.ic_location, "Distance")
        setProgressRowInfo(view.findViewById(R.id.row_calories), R.drawable.ic_fire, "Calories")
        setProgressRowInfo(view.findViewById(R.id.row_protein), R.drawable.calories, "Protein")
        setProgressRowInfo(view.findViewById(R.id.row_carbs), R.drawable.calories, "Carbs")
        setProgressRowInfo(view.findViewById(R.id.row_fat), R.drawable.calories, "Fat")
        setProgressRowInfo(view.findViewById(R.id.row_water), R.drawable.ic_water, "Water")
        setProgressRowInfo(view.findViewById(R.id.row_sleep), R.drawable.ic_sleep, "Sleep")
    }

    private fun setProgressRowInfo(v: View, iconRes: Int, label: String) {
        v.findViewById<ImageView>(R.id.iv_icon).setImageResource(iconRes)
        v.findViewById<TextView>(R.id.tv_label).text = label
    }

    private fun updateTheme(isLoss: Boolean) {
        val view = rootView ?: return
        val primaryColor = ContextCompat.getColor(requireContext(), if (isLoss) R.color.accent_blue else R.color.accent_green)
        val colorStateList = ColorStateList.valueOf(primaryColor)

        view.findViewById<TextView>(R.id.tv_goal_title).setTextColor(primaryColor)
        view.findViewById<TextView>(R.id.tv_weight_left).setTextColor(primaryColor)
        view.findViewById<EditText>(R.id.et_duration_weeks).setTextColor(primaryColor)
        view.findViewById<TextView>(R.id.tv_duration_unit).setTextColor(primaryColor)
        
        val pbCircular = view.findViewById<ProgressBar>(R.id.pb_circular)
        val progressDrawable = pbCircular.progressDrawable as? android.graphics.drawable.LayerDrawable
        progressDrawable?.findDrawableByLayerId(android.R.id.progress)?.setTint(primaryColor)
        
        view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save_health_goals).backgroundTintList = colorStateList
        
        applyDashboardMetricsStyle(view)
    }

    private fun applyDashboardMetricsStyle(view: View) {
        val metricColors = mapOf(
            R.id.target_cal to R.color.accent_pink, 
            R.id.target_steps to R.color.accent_orange,
            R.id.target_dist to R.color.accent_green, 
            R.id.target_water to R.color.accent_teal,
            R.id.target_sleep to R.color.accent_blue, 
            R.id.row_steps to R.color.accent_orange,
            R.id.row_dist to R.color.accent_green,
            R.id.row_calories to R.color.accent_pink,
            R.id.row_protein to R.color.accent_green,
            R.id.row_carbs to R.color.accent_orange,
            R.id.row_water to R.color.accent_teal, 
            R.id.row_sleep to R.color.accent_blue,
            R.id.btn_nav_add_food to if (viewModel.uiState.value.isWeightLoss) R.color.accent_blue else R.color.accent_green
        )

        metricColors.forEach { (id, colorRes) ->
            val color = ContextCompat.getColor(requireContext(), colorRes)
            val csl = ColorStateList.valueOf(color)
            val container = view.findViewById<View>(id) ?: return@forEach
            container.findViewById<ImageView>(R.id.iv_icon)?.imageTintList = csl
            if (container is com.google.android.material.card.MaterialCardView) {
                container.strokeColor = color
            }
            container.findViewById<ProgressBar>(R.id.pb_linear)?.progressTintList = csl
            container.findViewById<TextView>(R.id.tv_percent)?.setTextColor(color)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> updateUI(state) }
                }
                launch {
                    viewModel.validationError.collect { error ->
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateUI(state: HealthGoalViewModel.HealthGoalUiState) {
        val view = rootView ?: return
        
        val skeleton = view.findViewById<View>(R.id.layout_skeleton)
        val mainContent = view.findViewById<View>(R.id.main_content)
        val shimmerContainer = view.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_view_container)

        if (state.isLoading) {
            skeleton.visibility = View.VISIBLE
            mainContent.visibility = View.GONE
            shimmerContainer?.startShimmer()
            return
        } else {
            skeleton.visibility = View.GONE
            mainContent.visibility = View.VISIBLE
            shimmerContainer?.stopShimmer()
        }

        view.findViewById<View>(R.id.ll_calculating_loader).visibility = if (state.isCalculating) View.VISIBLE else View.GONE

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        
        if (state.isGoalSet) {
            tabLayout.visibility = View.GONE
            // Ensure ViewModel is in sync with the set goal type if it was just loaded
            // The ViewModel already handles this in fetchUserData and setGoalType
        } else {
            tabLayout.visibility = View.VISIBLE
            if (tabLayout.tabCount < 2) {
                tabLayout.removeAllTabs()
                tabLayout.addTab(tabLayout.newTab().setText("Weight Loss 🏃"))
                tabLayout.addTab(tabLayout.newTab().setText("Weight Gain 💪"))
            }
            val expectedTabIndex = if (state.isWeightLoss) 0 else 1
            if (tabLayout.selectedTabPosition != expectedTabIndex) {
                tabLayout.tag = "loading"
                tabLayout.getTabAt(expectedTabIndex)?.select()
                tabLayout.tag = null
            }
        }
        
        updateTheme(state.isWeightLoss)

        view.findViewById<TextView>(R.id.tv_weight_current).text = "%.1f kg".format(state.currentWeight)

        val etTargetWeight = view.findViewById<EditText>(R.id.et_target_weight)
        if (!etTargetWeight.hasFocus()) {
            if (state.targetWeight <= 0) {
                etTargetWeight.setText("")
                etTargetWeight.hint = "Set Target"
            } else {
                etTargetWeight.setText("%.1f".format(state.targetWeight))
            }
        }

        val etDuration = view.findViewById<EditText>(R.id.et_duration_weeks)
        if (!etDuration.hasFocus()) etDuration.setText(state.durationWeeks.toString())
        
        val weightDiff = Math.abs(state.currentWeight - state.targetWeight)
        if (state.targetWeight <= 0) {
            view.findViewById<TextView>(R.id.tv_weight_left).text = "Target not set"
        } else {
            view.findViewById<TextView>(R.id.tv_weight_left).text = "%.1f kg".format(weightDiff)
        }
        
        val progressPercent = if (state.targetWeight <= 0) 0.0 else state.weightProgress
        val pbCircular = view.findViewById<ProgressBar>(R.id.pb_circular)
        pbCircular?.let {
            it.max = 1000
            it.progress = (progressPercent * 10).toInt()
        }
        
        view.findViewById<TextView>(R.id.tv_progress_percent)?.text = if (state.targetWeight <= 0) "0%" else "%.1f%%".format(progressPercent)

        view.findViewById<TextView>(R.id.tv_goal_title)?.text = if (state.targetWeight <= 0) "Set your target weight" else "${if (state.isWeightLoss) "Weight Loss" else "Weight Gain"} Goal"
        
        val tvStatus = view.findViewById<TextView>(R.id.tv_goal_status)
        tvStatus?.let {
            if (state.targetWeight <= 0) {
                it.visibility = View.GONE
            } else {
                it.visibility = View.VISIBLE
                it.text = state.status
                val statusColor = if (state.targetWeight <= 0) Color.GRAY else Color.parseColor(state.statusColor)
                it.setTextColor(statusColor)
            }
        }

        if (state.targetWeight <= 0) {
            setTargetValue(view.findViewById(R.id.target_cal), "--")
            setTargetValue(view.findViewById(R.id.target_steps), "--")
            setTargetValue(view.findViewById(R.id.target_dist), "--")
            setTargetValue(view.findViewById(R.id.target_water), "--")
            setTargetValue(view.findViewById(R.id.target_sleep), "--")
        } else {
            setTargetValue(view.findViewById(R.id.target_cal), state.targetCalories.toString())
            setTargetValue(view.findViewById(R.id.target_steps), state.targetSteps.toString())
            setTargetValue(view.findViewById(R.id.target_dist), "%.2f".format(state.targetDistanceKm))
            setTargetValue(view.findViewById(R.id.target_water), "%.1f".format(state.targetWaterL))
            setTargetValue(view.findViewById(R.id.target_sleep), "%.1f".format(state.targetSleepHours.toDouble()))
        }

        val illustration = view.findViewById<ImageView>(R.id.iv_goal_illustration)
        val isFemale = state.gender.equals("Female", ignoreCase = true)
        if (state.isWeightLoss) illustration.setImageResource(if (isFemale) R.drawable.weightloss_women else R.drawable.ic_steps_styled)
        else illustration.setImageResource(if (isFemale) R.drawable.weightgain_female else R.drawable.weightgain_male)
        illustration.alpha = 0.5f

        updateProgressRow(view.findViewById(R.id.row_steps), state.steps, state.targetSteps, "steps")
        updateProgressRow(view.findViewById(R.id.row_dist), state.currentDistanceKm, state.targetDistanceKm, "km")
        updateProgressRow(view.findViewById(R.id.row_calories), state.caloriesConsumed, state.targetCalories, "kcal")
        updateProgressRow(view.findViewById(R.id.row_protein), state.proteinConsumed, state.targetProtein, "g")
        updateProgressRow(view.findViewById(R.id.row_carbs), state.carbsConsumed, state.targetCarbs, "g")
        updateProgressRow(view.findViewById(R.id.row_fat), state.fatConsumed, state.targetFat, "g")
        updateProgressRow(view.findViewById(R.id.row_water), state.currentWater, state.targetWaterL, "L")
        updateProgressRow(view.findViewById(R.id.row_sleep), 6.5, state.targetSleepHours, "hrs")

        view.findViewById<TextView>(R.id.tv_water_value_mini).text = "%.1fL".format(state.currentWater)
        val waterProgress = if (state.targetWaterL > 0) (state.currentWater * 100 / state.targetWaterL).toInt() else 0
        view.findViewById<ProgressBar>(R.id.pb_water_mini).progress = waterProgress

        renderLoggedFoods(state.loggedFoods)
        updateBottomCards(state)
    }

    private fun updateBottomCards(state: HealthGoalViewModel.HealthGoalUiState) {
        val view = rootView ?: return
        
        // Update Water Card
        view.findViewById<TextView>(R.id.tv_water_value_mini).text = "%.1f / %.1f L".format(state.currentWater, state.targetWaterL)
        val waterPercent = if (state.targetWaterL > 0) (state.currentWater * 100 / state.targetWaterL).toInt() else 0
        view.findViewById<TextView>(R.id.tv_water_percent_label).text = "$waterPercent% of goal"
        
        // Update Watch Card
        view.findViewById<TextView>(R.id.tv_watch_goal).text = "Goal: 8,000 steps"
        view.findViewById<TextView>(R.id.tv_watch_steps).text = "%,d steps".format(state.steps)
        val watchIcon = view.findViewById<ImageView>(R.id.iv_watch_icon)
        val isConnected = (activity as? com.example.myfitnessapp.MainActivity)?.isDeviceConnected() ?: false
        watchIcon?.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), if (isConnected) R.color.accent_green else R.color.accent_blue)
        )

        // Update Streaks Card
        view.findViewById<TextView>(R.id.tv_streak_value).text = "12 Days"
        
        // Update Logged Food Summary
        view.findViewById<TextView>(R.id.tv_total_logged_calories).text = "${state.caloriesConsumed} kcal"
        view.findViewById<TextView>(R.id.tv_total_logged_items).text = "${state.loggedFoods.size} Items"
    }

    private fun setTargetValue(v: View, value: String) {
        v.findViewById<TextView>(R.id.tv_value).text = value
    }

    private fun updateProgressRow(v: View, current: Number, target: Number, unit: String) {
        val currentVal = current.toDouble()
        val targetVal = target.toDouble()
        val displayValue = if (currentVal % 1 == 0.0 && targetVal % 1 == 0.0) "${currentVal.toInt()} / ${targetVal.toInt()}" else "%.1f / %.1f".format(currentVal, targetVal)
        v.findViewById<TextView>(R.id.tv_values).text = "$displayValue $unit"
        val progress = if (targetVal > 0) (currentVal * 100 / targetVal).toInt() else 0
        v.findViewById<ProgressBar>(R.id.pb_linear).progress = progress
        v.findViewById<TextView>(R.id.tv_percent).text = "$progress%"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
    }
}
