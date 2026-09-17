package com.example.myfitnessapp.report

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentReportBinding
import com.example.myfitnessapp.databinding.ItemReportGraphSectionBinding
import com.example.myfitnessapp.models.DailyData
import com.example.myfitnessapp.models.User
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.*

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ReportViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        arguments?.getString("metric")?.let {
            viewModel.setSelectedMetric(it)
        }

        view.post {
            setupListeners()
            observeViewModel()
        }
    }

    private fun setupListeners() {
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val range = when(checkedId) {
                    R.id.btn_day -> "D"
                    R.id.btn_weekly -> "W"
                    R.id.btn_monthly -> "M"
                    else -> "W"
                }
                viewModel.setRange(range)
            }
        }

        binding.btnCalendar.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTheme(R.style.MaterialCalendarTheme)
            .setTitleText("Select Date")
            .setSelection(viewModel.uiState.value.selectedDate.time)
            .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            viewModel.setDate(Date(selection))
        }

        datePicker.show(childFragmentManager, "DATE_PICKER")
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                val skeleton = binding.root.findViewById<View>(R.id.layout_skeleton)
                val mainContent = binding.root.findViewById<View>(R.id.main_content)
                val shimmerContainer = binding.root.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_view_container)

                if (state.isLoading) {
                    skeleton?.visibility = View.VISIBLE
                    mainContent?.visibility = View.GONE
                    shimmerContainer?.startShimmer()
                    return@collectLatest
                } else {
                    skeleton?.visibility = View.GONE
                    mainContent?.visibility = View.VISIBLE
                    shimmerContainer?.stopShimmer()
                }

                updateDateRangeText(state)
                updateReportSections(state)
            }
        }
    }

    private fun updateDateRangeText(state: ReportUiState) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val selectedDateStr = sdf.format(state.selectedDate)
        val today = sdf.format(Date())

        binding.tvDateRange.text = when(state.rangeType) {
            "D" -> if (selectedDateStr == today) getString(R.string.today) else selectedDateStr
            "W" -> {
                val calendar = Calendar.getInstance()
                calendar.time = state.selectedDate
                val end = sdf.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                val start = sdf.format(calendar.time)
                "$start - $end"
            }
            "M" -> {
                val calendar = Calendar.getInstance()
                calendar.time = state.selectedDate
                val end = sdf.format(calendar.time)
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                val start = sdf.format(calendar.time)
                "$start - $end"
            }
            else -> ""
        }
    }

    private fun updateReportSections(state: ReportUiState) {
        // Steps
        setupSection(binding.sectionSteps, getString(R.string.steps_history), "steps", state.dailyDataList, state.user)
        // Distance
        setupSection(binding.sectionDistance, getString(R.string.distance_history), "km", state.dailyDataList, state.user)
        // Heart Rate
        setupSection(binding.sectionHeart, getString(R.string.heart_rate_history), "bpm", state.dailyDataList, state.user)
        // Calories
        setupSection(binding.sectionCalories, getString(R.string.calories_history), "kcal", state.dailyDataList, state.user)
        // Sleep
        setupSection(binding.sectionSleep, getString(R.string.sleep_history), "hours", state.dailyDataList, state.user)
        // Weight
        setupSection(binding.sectionWeight, getString(R.string.weight_progress), "kg", state.dailyDataList, state.user)
        // Health Goals
        setupSection(binding.sectionGoals, getString(R.string.health_goal_progress), "nutrition", state.dailyDataList, state.user)
        
        // Auto-scroll logic
        state.selectedMetric?.let { metric ->
            val targetView: View? = when(metric) {
                "steps" -> binding.sectionSteps.root
                "distance" -> binding.sectionDistance.root
                "heart" -> binding.sectionHeart.root
                "calories" -> binding.sectionCalories.root
                "sleep" -> binding.sectionSleep.root
                "weight" -> binding.sectionWeight.root
                "goals" -> binding.sectionGoals.root
                else -> null
            }
            targetView?.post {
                binding.scrollView.smoothScrollTo(0, targetView.top)
                viewModel.setSelectedMetric(null)
            }
        }
    }

    private fun setupSection(
        sectionBinding: ItemReportGraphSectionBinding,
        title: String,
        unit: String,
        dataList: List<DailyData>,
        user: User?
    ) {
        sectionBinding.tvGraphTitle.text = title
        sectionBinding.tvOverviewLabel.text = when(viewModel.uiState.value.rangeType) {
            "D" -> getString(R.string.todays_overview)
            "W" -> getString(R.string.seven_day_overview)
            "M" -> getString(R.string.thirty_day_overview)
            else -> getString(R.string.overview)
        }

        val values = dataList.map { 
            when(unit) {
                "steps" -> it.steps.toDouble()
                "km" -> calculateDistance(it.steps, user)
                "bpm" -> it.heartRate.toDouble()
                "kcal", "nutrition" -> it.caloriesConsumed.toDouble()
                "hours" -> it.sleepHours
                "kg" -> it.weight
                else -> 0.0
            }
        }

        val total = values.sum()
        val most = values.maxOrNull() ?: 0.0
        val least = values.filter { it > 0 }.minOrNull() ?: 0.0

        val displayUnit = if (unit == "nutrition") "kcal" else unit
        sectionBinding.tvStatTotal.text = formatValue(total, displayUnit)
        sectionBinding.tvStatMost.text = formatValue(most, displayUnit)
        sectionBinding.tvStatLeast.text = formatValue(least, displayUnit)

        renderChart(sectionBinding, unit, dataList, user)
    }

    private fun renderChart(
        sectionBinding: ItemReportGraphSectionBinding,
        unit: String,
        dataList: List<DailyData>,
        user: User?
    ) {
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_gray)
        val gridColor = Color.parseColor("#1AFFFFFF")

        when (unit) {
            "steps", "kcal", "hours" -> {
                sectionBinding.barChart.visibility = View.VISIBLE
                sectionBinding.lineChart.visibility = View.GONE
                sectionBinding.pieChart.visibility = View.GONE
                
                val entries = dataList.mapIndexed { index, data ->
                    val valY = when(unit) {
                        "steps" -> data.steps.toFloat()
                        "kcal" -> data.caloriesConsumed.toFloat()
                        else -> data.sleepHours.toFloat()
                    }
                    BarEntry(index.toFloat(), valY)
                }
                val dataSet = BarDataSet(entries, unit)
                dataSet.color = ContextCompat.getColor(requireContext(), when(unit) {
                    "steps" -> R.color.accent_blue
                    "kcal" -> R.color.accent_orange
                    else -> R.color.accent_teal
                })
                dataSet.setDrawValues(false)
                
                sectionBinding.barChart.apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.textColor = textColor
                    xAxis.setDrawGridLines(false)
                    xAxis.valueFormatter = IndexAxisValueFormatter(dataList.map { formatDate(it.date) })
                    axisLeft.textColor = textColor
                    axisLeft.gridColor = gridColor
                    axisRight.isEnabled = false
                    data = BarData(dataSet)
                    animateY(1000)
                    invalidate()
                }
            }
            "nutrition" -> {
                sectionBinding.pieChart.visibility = View.VISIBLE
                sectionBinding.barChart.visibility = View.GONE
                sectionBinding.lineChart.visibility = View.GONE

                val avgProt = dataList.map { it.proteinConsumed }.average().coerceAtLeast(0.0)
                val avgCal = dataList.map { it.caloriesConsumed }.average().coerceAtLeast(0.0)
                val avgCarb = dataList.map { it.carbsConsumed }.average().coerceAtLeast(0.0)
                val avgFat = dataList.map { it.fatConsumed }.average().coerceAtLeast(0.0)
                val avgWater = dataList.map { it.waterIntakeL }.average().coerceAtLeast(0.0)

                val entries = ArrayList<PieEntry>()
                if (avgProt > 0) entries.add(PieEntry(avgProt.toFloat(), getString(R.string.protein)))
                if (avgCal > 0) entries.add(PieEntry(avgCal.toFloat(), getString(R.string.kcal)))
                if (avgCarb > 0) entries.add(PieEntry(avgCarb.toFloat(), getString(R.string.carbs)))
                if (avgFat > 0) entries.add(PieEntry(avgFat.toFloat(), getString(R.string.fats)))
                if (avgWater > 0) entries.add(PieEntry(avgWater.toFloat() * 100, getString(R.string.water)))

                val dataSet = PieDataSet(entries, "")
                dataSet.colors = listOf(
                    ContextCompat.getColor(requireContext(), R.color.accent_pink),
                    ContextCompat.getColor(requireContext(), R.color.accent_orange),
                    ContextCompat.getColor(requireContext(), R.color.accent_teal),
                    ContextCompat.getColor(requireContext(), R.color.accent_blue),
                    ContextCompat.getColor(requireContext(), R.color.accent_green)
                )
                dataSet.setDrawValues(true)
                dataSet.valueTextColor = Color.WHITE
                dataSet.valueTextSize = 10f
                dataSet.sliceSpace = 3f

                sectionBinding.pieChart.apply {
                    description.isEnabled = false
                    setHoleColor(Color.TRANSPARENT)
                    centerText = getString(R.string.health_goals)
                    setCenterTextColor(Color.WHITE)
                    setCenterTextSize(14f)
                    setEntryLabelColor(Color.WHITE)
                    setEntryLabelTextSize(10f)
                    legend.isEnabled = true
                    legend.textColor = textColor
                    legend.horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER
                    data = PieData(dataSet)
                    animateXY(1000, 1000)
                    invalidate()
                }
            }
            else -> {
                sectionBinding.lineChart.visibility = View.VISIBLE
                sectionBinding.barChart.visibility = View.GONE
                sectionBinding.pieChart.visibility = View.GONE
                
                val entries = dataList.mapIndexed { index, data ->
                    val valY = when(unit) {
                        "bpm" -> data.heartRate.toFloat()
                        "km" -> calculateDistance(data.steps, user).toFloat()
                        else -> data.weight.toFloat()
                    }
                    Entry(index.toFloat(), valY)
                }
                val dataSet = LineDataSet(entries, unit)
                dataSet.color = ContextCompat.getColor(requireContext(), when(unit) {
                    "bpm" -> R.color.accent_pink
                    "km" -> R.color.accent_green
                    else -> R.color.accent_blue
                })
                dataSet.setCircleColor(Color.WHITE)
                dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
                dataSet.setDrawFilled(true)
                dataSet.fillAlpha = 40
                dataSet.setDrawValues(false)
                
                sectionBinding.lineChart.apply {
                    description.isEnabled = false
                    legend.isEnabled = false
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.textColor = textColor
                    xAxis.setDrawGridLines(false)
                    xAxis.valueFormatter = IndexAxisValueFormatter(dataList.map { formatDate(it.date) })
                    axisLeft.textColor = textColor
                    axisLeft.gridColor = gridColor
                    axisRight.isEnabled = false
                    data = LineData(dataSet)
                    animateX(1000)
                    invalidate()
                }
            }
        }
    }

    private fun formatValue(value: Double, unit: String): String {
        val unitStr = when(unit) {
            "steps" -> getString(R.string.steps)
            "km" -> getString(R.string.km)
            "bpm" -> getString(R.string.bpm)
            "kcal" -> getString(R.string.kcal)
            "hours" -> getString(R.string.hrs)
            "kg" -> "kg"
            else -> unit
        }
        return if (value >= 1000 && unit == "steps") {
            getString(R.string.k_steps_format, value / 1000.0, unitStr)
        } else if (value % 1.0 == 0.0) {
            getString(R.string.value_unit_format, value.toInt().toString(), unitStr)
        } else {
            getString(R.string.value_unit_format, "%.1f".format(value), unitStr)
        }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("MM/dd", Locale.US)
            formatter.format(parser.parse(dateStr)!!)
        } catch (e: Exception) { "" }
    }

    private fun calculateDistance(steps: Int, user: User?): Double {
        val height = user?.height ?: 175
        val factor = if (user?.gender?.lowercase() == "male") 0.415 else 0.413
        val stride = (height * factor) / 100.0
        return (steps * stride) / 1000.0
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
