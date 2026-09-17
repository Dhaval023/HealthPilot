package com.example.myfitnessapp.workout

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.R
import com.example.myfitnessapp.data.workout.WorkoutRecord
import com.example.myfitnessapp.databinding.ItemWorkoutHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class WorkoutHistoryAdapter(private val onItemClick: (WorkoutRecord) -> Unit) :
    ListAdapter<WorkoutRecord, WorkoutHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWorkoutHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemWorkoutHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(record: WorkoutRecord) {
            val context = itemView.context
            val nameResId = context.resources.getIdentifier(record.workoutType.lowercase().replace(" ", "_"), "string", context.packageName)
            binding.tvWorkoutName.text = if (nameResId != 0) context.getString(nameResId) else record.workoutType
            
            binding.tvDetails.text = context.getString(
                R.string.history_details_format,
                record.duration / 60000,
                context.getString(R.string.mins),
                record.totalDistance,
                context.getString(R.string.km)
            )
            binding.tvCalories.text = context.getString(R.string.unit_kcal, record.caloriesBurned)
            binding.tvTime.text = timeFormat.format(Date(record.startTime))
            
            // Set icon based on type (simplified)
            val iconRes = when (record.workoutType.lowercase()) {
                "walking" -> R.drawable.ic_walk
                "running" -> R.drawable.running
                "cycling" -> R.drawable.cycling
                "yoga" -> R.drawable.yoga
                "boxing" -> R.drawable.boxing
                else -> R.drawable.ic_steps_styled
            }
            binding.ivIcon.setImageResource(iconRes)
            
            binding.root.setOnClickListener { onItemClick(record) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<WorkoutRecord>() {
        override fun areItemsTheSame(oldItem: WorkoutRecord, newItem: WorkoutRecord) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: WorkoutRecord, newItem: WorkoutRecord) = oldItem == newItem
    }
}
