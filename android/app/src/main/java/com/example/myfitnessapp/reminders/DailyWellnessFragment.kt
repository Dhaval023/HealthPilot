package com.example.myfitnessapp.reminders

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentDailyWellnessBinding
import com.example.myfitnessapp.healthgoal.HealthGoalViewModel
import com.example.myfitnessapp.models.Reminder
import com.example.myfitnessapp.models.User
import com.example.myfitnessapp.utils.ReminderIconUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DailyWellnessFragment : Fragment() {

    private var _binding: FragmentDailyWellnessBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthGoalViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (requireActivity().application as HealthPilot).repository
                @Suppress("UNCHECKED_CAST")
                return HealthGoalViewModel(requireActivity().application, repository) as T
            }
        }
    }

    private var adapter: TimelineAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDailyWellnessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initReminderManager(requireContext())
        
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        loadUserSummary()
    }

    private fun setupRecyclerView() {
        binding.rvWellnessTimeline.layoutManager = LinearLayoutManager(requireContext())
        adapter = TimelineAdapter(emptyList()) { showReminderOptions(it) }
        binding.rvWellnessTimeline.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnRefreshSchedule.setOnClickListener {
            viewModel.generateIdealSchedule()
            android.widget.Toast.makeText(context, getString(R.string.schedule_refreshed), android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.btnEditWellness.setOnClickListener {
            showEditMode()
        }

        binding.btnCancelWellness.setOnClickListener {
            binding.wellnessViewSwitcher.showPrevious()
        }

        binding.btnSaveWellness.setOnClickListener {
            saveWellnessSettings()
        }

        setupTimePicker(binding.etWellnessWakeUp)
        setupTimePicker(binding.etWellnessSleep)
        setupTimePicker(binding.etWellnessWorkStart)
        setupTimePicker(binding.etWellnessWorkEnd)

        binding.etWellnessWorkStyle.isFocusable = false
        binding.etWellnessWorkStyle.setOnClickListener {
            val styles = arrayOf(
                getString(R.string.mostly_sitting),
                getString(R.string.mostly_standing),
                getString(R.string.mixed_activity),
                getString(R.string.heavy_physical_work),
                getString(R.string.driving),
                getString(R.string.home_and_care),
            )
            MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
                .setTitle(getString(R.string.select_work_style_dialog))
                .setItems(styles) { _, which ->
                    binding.etWellnessWorkStyle.setText(styles[which])
                }
                .show()
        }
    }

    private fun setupTimePicker(editText: android.widget.EditText) {
        editText.isFocusable = false
        editText.isClickable = true
        editText.setOnClickListener {
            val current24h = editText.tag?.toString() ?: "08:00"
            val parts = current24h.split(":")
            val hour = if (parts.size == 2) parts[0].toInt() else 8
            val minute = if (parts.size == 2) parts[1].toInt() else 0

            TimePickerDialog(requireContext(), R.style.CustomPickerTheme, { _, h, m ->
                val time24h = "%02d:%02d".format(h, m)
                editText.tag = time24h
                editText.setText(formatTo12h(time24h))
            }, hour, minute, false).show()
        }
    }

    private fun showEditMode() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snapshot = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                val user = snapshot.toObject(User::class.java)
                user?.let {
                    setFieldTime(binding.etWellnessWakeUp, it.wakeUpTime)
                    setFieldTime(binding.etWellnessSleep, it.sleepTime)
                    binding.etWellnessWorkStyle.setText(it.workStyle)
                    setFieldTime(binding.etWellnessWorkStart, it.workStartTime)
                    setFieldTime(binding.etWellnessWorkEnd, it.workEndTime)
                    binding.etWellnessScreenTime.setText(it.dailyScreenTime.toString())
                    
                    binding.wellnessViewSwitcher.showNext()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setFieldTime(editText: android.widget.EditText, time24h: String) {
        editText.tag = time24h
        editText.setText(formatTo12h(time24h))
    }

    private fun saveWellnessSettings() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updates = mapOf(
                    "wakeUpTime" to (binding.etWellnessWakeUp.tag?.toString() ?: "07:00"),
                    "sleepTime" to (binding.etWellnessSleep.tag?.toString() ?: "22:00"),
                    "workStyle" to binding.etWellnessWorkStyle.text.toString(),
                    "workStartTime" to (binding.etWellnessWorkStart.tag?.toString() ?: "09:00"),
                    "workEndTime" to (binding.etWellnessWorkEnd.tag?.toString() ?: "17:00"),
                    "dailyScreenTime" to (binding.etWellnessScreenTime.text.toString().toIntOrNull() ?: 0)
                )
                FirebaseFirestore.getInstance().collection("users").document(uid).update(updates).await()
                
                viewModel.generateIdealSchedule()
                binding.wellnessViewSwitcher.showPrevious()
                android.widget.Toast.makeText(context, getString(R.string.settings_updated_refreshed), android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Failed to update", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Combine Meals and Wellness Reminders for a complete timeline
                    val combined = (state.reminders + state.wellnessReminders)
                    
                    // Filter out duplicates (Same type and time)
                    val fullTimeline = combined
                        .groupBy { "${it.type.lowercase()}_${it.time}" }
                        .map { it.value.first() }
                        .sortedBy { it.time }
                    
                    adapter?.updateList(fullTimeline)
                }
            }
        }
    }

    private fun loadUserSummary() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val snapshot = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                val user = snapshot.toObject(User::class.java)
                user?.let {
                    binding.tvWellnessScheduleRange.text = "${formatTo12h(it.wakeUpTime)} - ${formatTo12h(it.sleepTime)}"
                    
                    val workStyleResId = resources.getIdentifier(
                        it.workStyle.lowercase().replace(" ", "_").replace("&", "and"),
                        "string",
                        requireContext().packageName
                    )
                    binding.tvWellnessWorkStyle.text = if (workStyleResId != 0) getString(workStyleResId) else it.workStyle
                    
                    val hrsScreenText = getString(R.string.hrs_screen, it.dailyScreenTime)
                    binding.tvWellnessWorkMeta.text = "${formatTo12h(it.workStartTime)} - ${formatTo12h(it.workEndTime)} | $hrsScreenText"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showReminderOptions(reminder: Reminder) {
        val options = arrayOf(
            if (reminder.isEnabled) getString(R.string.turn_off) else getString(R.string.turn_on),
            getString(R.string.change_time),
            getString(R.string.remove)
        )
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle(reminder.type)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.toggleWellnessReminder(reminder.id, !reminder.isEnabled)
                    1 -> showTimePickerDialog(reminder)
                    2 -> viewModel.removeWellnessReminder(reminder.id)
                }
            }
            .show()
    }

    private fun showTimePickerDialog(reminder: Reminder) {
        val parts = reminder.time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        TimePickerDialog(requireContext(), R.style.CustomPickerTheme, { _, h, m ->
            val newTime = "%02d:%02d".format(h, m)
            viewModel.updateWellnessReminderTime(reminder.id, newTime)
        }, hour, minute, false).show()
    }

    private fun formatTo12h(time24h: String): String {
        return try {
            val parts = time24h.split(":")
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val suffix = if (h >= 12) "PM" else "AM"
            val h12 = if ((h % 12) == 0) 12 else h % 12
            "%02d:%02d %s".format(h12, m, suffix)
        } catch (e: Exception) { time24h }
    }

    private inner class TimelineAdapter(
        private var list: List<Reminder>,
        private val onClick: (Reminder) -> Unit
    ) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvType: TextView = view.findViewById(R.id.tv_meal_type)
            val tvTime: TextView = view.findViewById(R.id.tv_meal_time)
            val ivIcon: ImageView = view.findViewById(R.id.iv_meal)
            val switch: com.google.android.material.materialswitch.MaterialSwitch = view.findViewById(R.id.switch_reminder)
        }

        fun updateList(newList: List<Reminder>) {
            if (list == newList) return
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meal_reminder, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val reminder = list[position]
            holder.tvType.text = reminder.type
            holder.tvTime.text = formatTo12h(reminder.time)
            holder.ivIcon.setImageResource(ReminderIconUtils.getIconForType(reminder.type))
            
            holder.switch.setOnCheckedChangeListener(null)
            holder.switch.isChecked = reminder.isEnabled
            holder.switch.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleWellnessReminder(reminder.id, isChecked)
            }

            holder.itemView.setOnClickListener { onClick(reminder) }
        }

        override fun getItemCount() = list.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
