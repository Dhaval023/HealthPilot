package com.example.myfitnessapp.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.ItemWorkoutBinding

data class WorkoutItem(val name: String, val iconRes: Int, val colorRes: Int)

class WorkoutAdapter(private val onWorkoutClick: (String) -> Unit) : RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder>() {

    private val workouts = listOf(
        WorkoutItem("Walking", R.drawable.ic_walk, R.color.accent_green),
        WorkoutItem("Running", R.drawable.running, R.color.accent_red),
        WorkoutItem("Cycling", R.drawable.cycling, R.color.accent_blue),
        WorkoutItem("Badminton", R.drawable.badminton, R.color.accent_yellow),
        WorkoutItem("Swimming", R.drawable.swimming, R.color.accent_teal),
        WorkoutItem("Yoga", R.drawable.yoga, R.color.accent_purple),
        WorkoutItem("Dancing", R.drawable.dancing, R.color.accent_pink),
        WorkoutItem("Aerobics", R.drawable.aerobics, R.color.accent_orange),
        WorkoutItem("Football", R.drawable.football, R.color.accent_green),
        WorkoutItem("Hockey", R.drawable.hockey, R.color.accent_indigo),
        WorkoutItem("Cricket", R.drawable.cricket, R.color.accent_lime),
        WorkoutItem("Kabaddi", R.drawable.kabaddi, R.color.accent_brown),
        WorkoutItem("Indoor Running", R.drawable.indoor_running, R.color.accent_orange),
        WorkoutItem("Zumba", R.drawable.zumba, R.color.accent_pink),
        WorkoutItem("Sit-Ups", R.drawable.situp, R.color.accent_blue),
        WorkoutItem("Weightlifting", R.drawable.weightlifting, R.color.accent_red),
        WorkoutItem("Boxing", R.drawable.boxing, R.color.accent_red),
        WorkoutItem("Wrestling", R.drawable.wrestling, R.color.accent_brown),
        WorkoutItem("Basketball", R.drawable.basketball, R.color.accent_orange),
        WorkoutItem("Tennis", R.drawable.tennis, R.color.accent_lime),
        WorkoutItem("Volleyball", R.drawable.volleyball, R.color.accent_teal)
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val binding = ItemWorkoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WorkoutViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        holder.bind(workouts[position])
    }

    override fun getItemCount() = workouts.size

    inner class WorkoutViewHolder(private val binding: ItemWorkoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(workout: WorkoutItem) {
            val context = itemView.context
            val resId = context.resources.getIdentifier(workout.name.lowercase().replace(" ", "_").replace("-", "_"), "string", context.packageName)
            binding.tvWorkoutName.text = if (resId != 0) context.getString(resId) else workout.name
            binding.ivWorkoutIcon.setImageResource(workout.iconRes)
            binding.ivWorkoutIcon.setColorFilter(ContextCompat.getColor(itemView.context, workout.colorRes))
            binding.root.setOnClickListener { onWorkoutClick(workout.name) }
        }
    }
}
