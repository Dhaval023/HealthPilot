package com.example.myfitnessapp.workout

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.databinding.ItemYogaExerciseBinding

class WorkoutExerciseAdapter(
    private val exercises: List<WorkoutExercise>,
    private val onExerciseClick: (WorkoutExercise) -> Unit
) : RecyclerView.Adapter<WorkoutExerciseAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemYogaExerciseBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemYogaExerciseBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val exercise = exercises[position]
        val context = holder.itemView.context
        
        val nameResId = context.resources.getIdentifier("ex_${exercise.name.lowercase().replace(" ", "_").replace("-", "_")}", "string", context.packageName)
        holder.binding.tvExerciseName.text = if (nameResId != 0) context.getString(nameResId) else exercise.name
        
        val descResId = context.resources.getIdentifier("ex_${exercise.desc.lowercase().replace(" ", "_").replace("-", "_")}", "string", context.packageName)
        holder.binding.tvExerciseDesc.text = if (descResId != 0) context.getString(descResId) else exercise.desc
        
        holder.binding.tvExerciseTime.text = exercise.time
        holder.binding.ivExerciseIcon.setImageResource(exercise.imageRes)
        
        holder.itemView.setOnClickListener {
            onExerciseClick(exercise)
        }
    }

    override fun getItemCount() = exercises.size
}

data class WorkoutExercise(val name: String, val desc: String, val time: String, val imageRes: Int)
