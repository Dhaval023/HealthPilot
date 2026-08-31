package com.example.myfitnessapp.reminders

import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.R
import com.example.myfitnessapp.healthgoal.HealthGoalViewModel
import com.example.myfitnessapp.models.Reminder
import com.example.myfitnessapp.utils.ReminderIconUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class RemindersFragment : Fragment() {

    private val viewModel: HealthGoalViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (requireActivity().application as HealthPilot).repository
                return HealthGoalViewModel(repository) as T
            }
        }
    }

    private var rootView: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootView = inflater.inflate(R.layout.fragment_reminders, container, false)
        return rootView!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initReminderManager(requireContext())
        setupUI(view)
        observeViewModel()
    }

    private fun setupUI(view: View) {
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }
        view.findViewById<View>(R.id.btn_add_reminder).setOnClickListener {
            showAddReminderDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private var lastRenderedReminders: List<Reminder>? = null

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

        // Only show Meal related reminders here
        val mealTypes = listOf("breakfast", "lunch", "dinner", "snack", "snacks")
        val mealReminders = state.reminders.filter { 
            it.type.lowercase() in mealTypes || !isWellnessType(it.type)
        }

        if (lastRenderedReminders != mealReminders) {
            lastRenderedReminders = mealReminders
            renderRemindersList(mealReminders)
            renderRemindersGrid(mealReminders)
        }
    }

    private fun isWellnessType(type: String): Boolean {
        val wellnessTypes = listOf("water", "stand up", "walk", "stretch", "eye exercise", "sit break", "leg stretch", "deep breathing", "rest", "nutrition", "safe break", "posture", "relaxation", "sleep preparation")
        return type.lowercase() in wellnessTypes
    }

    private fun renderRemindersList(reminders: List<Reminder>) {
        val container = rootView?.findViewById<LinearLayout>(R.id.ll_reminders_list) ?: return
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        
        reminders.forEach { reminder ->
            val itemView = inflater.inflate(R.layout.item_meal_reminder, container, false)
            
            itemView.findViewById<TextView>(R.id.tv_meal_type).text = reminder.type
            itemView.findViewById<TextView>(R.id.tv_meal_time).text = formatTo12h(reminder.time)
            itemView.findViewById<ImageView>(R.id.iv_meal).setImageResource(ReminderIconUtils.getIconForType(reminder.type))
            
            val switch = itemView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.switch_reminder)
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = reminder.isEnabled
            switch.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleReminder(reminder.id, isChecked)
            }

            itemView.setOnClickListener {
                showMealOptionsDialog(reminder)
            }

            container.addView(itemView)
        }
    }

    private fun renderRemindersGrid(reminders: List<Reminder>) {
        val rv = rootView?.findViewById<RecyclerView>(R.id.rv_reminders_grid) ?: return
        rv.layoutManager = GridLayoutManager(requireContext(), 2)
        rv.adapter = RemindersAdapter(reminders) { showMealOptionsDialog(it) }
    }

    private fun showMealOptionsDialog(reminder: Reminder) {
        val options = arrayOf(
            if (reminder.isEnabled) "Turn Off Reminder" else "Turn On Reminder",
            "Change Time",
            "Remove Reminder"
        )
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle(reminder.type)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.toggleReminder(reminder.id, !reminder.isEnabled)
                    1 -> showTimePickerDialog(reminder)
                    2 -> viewModel.removeReminder(reminder.id)
                }
            }
            .show()
    }

    private fun showAddReminderDialog() {
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_add_reminder, null)
        
        val etType = dialogView.findViewById<EditText>(R.id.et_reminder_type)
        val btnTime = dialogView.findViewById<MaterialButton>(R.id.btn_pick_time)
        val btnSave = dialogView.findViewById<View>(R.id.btn_save)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        
        var selectedTime = "08:00"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        btnTime.setOnClickListener {
            TimePickerDialog(requireContext(), R.style.CustomPickerTheme, { _, h, m ->
                selectedTime = "%02d:%02d".format(h, m)
                btnTime.text = "Time: " + formatTo12h(selectedTime)
            }, 8, 0, false).show()
        }

        btnSave.setOnClickListener {
            val type = etType.text.toString()
            if (type.isNotEmpty()) {
                viewModel.addReminder(type, selectedTime)
                dialog.dismiss()
                Toast.makeText(requireContext(), "Reminder Added!", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showTimePickerDialog(reminder: Reminder) {
        val parts = reminder.time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        TimePickerDialog(requireContext(), R.style.CustomPickerTheme, { _, h, m ->
            val newTime = "%02d:%02d".format(h, m)
            viewModel.updateReminderTime(reminder.id, newTime)
        }, hour, minute, false).show()
    }

    private fun formatTo12h(time24h: String): String {
        return try {
            val parts = time24h.split(":")
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val suffix = if (h >= 12) "PM" else "AM"
            val h12 = if (h % 12 == 0) 12 else h % 12
            "%02d:%02d %s".format(h12, m, suffix)
        } catch (e: Exception) { time24h }
    }

    private inner class RemindersAdapter(
        private val reminders: List<Reminder>,
        private val onClick: (Reminder) -> Unit
    ) : RecyclerView.Adapter<RemindersAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvType: TextView = view.findViewById(R.id.tv_meal_type)
            val tvTime: TextView = view.findViewById(R.id.tv_meal_time)
            val ivMeal: ImageView = view.findViewById(R.id.iv_meal)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meal_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val reminder = reminders[position]
            holder.tvType.text = reminder.type
            holder.tvTime.text = formatTo12h(reminder.time)
            holder.ivMeal.setImageResource(ReminderIconUtils.getIconForType(reminder.type))
            holder.itemView.alpha = if (reminder.isEnabled) 1.0f else 0.5f
            holder.itemView.setOnClickListener { onClick(reminder) }
        }

        override fun getItemCount() = reminders.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
    }
}
