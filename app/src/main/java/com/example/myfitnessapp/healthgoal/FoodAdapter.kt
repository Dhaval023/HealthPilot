package com.example.myfitnessapp.healthgoal

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.R
import com.example.myfitnessapp.models.FoodItem
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton

class FoodAdapter(
    private var foods: List<FoodItem>,
    private val onAddClick: (FoodItem) -> Unit,
    private val onItemClick: (FoodItem) -> Unit
) : RecyclerView.Adapter<FoodAdapter.FoodViewHolder>() {

    private var primaryColor: Int = 0

    class FoodViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFood: ImageView = view.findViewById(R.id.iv_food)
        val tvName: TextView = view.findViewById(R.id.tv_food_name)
        val tvMacros: TextView = view.findViewById(R.id.tv_food_macros)
        val btnAdd: MaterialButton = view.findViewById(R.id.btn_add_food)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_food_result, parent, false)
        if (primaryColor == 0) {
            primaryColor = ContextCompat.getColor(parent.context, R.color.accent_blue)
        }
        return FoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        val food = foods[position]
        holder.tvName.text = food.name
        holder.tvMacros.text = "${food.calories} kcal | P: ${food.protein}g | C: ${food.carbs}g | F: ${food.fat}g"
        
        val csl = ColorStateList.valueOf(primaryColor)
        holder.btnAdd.iconTint = csl
        
        holder.btnAdd.setOnClickListener { onAddClick(food) }
        holder.itemView.setOnClickListener { onItemClick(food) }
        
        if (food.imageUrl.isNotEmpty()) {
            holder.ivFood.imageTintList = null
            Glide.with(holder.itemView.context)
                .load(food.imageUrl)
                .placeholder(R.drawable.calories)
                .error(R.drawable.calories)
                .into(holder.ivFood)
        } else {
            holder.ivFood.imageTintList = csl
            holder.ivFood.setImageResource(R.drawable.calories)
        }
    }

    override fun getItemCount() = foods.size

    fun updateData(newFoods: List<FoodItem>) {
        foods = newFoods
        notifyDataSetChanged()
    }

    fun setThemeColor(color: Int) {
        primaryColor = color
        notifyDataSetChanged()
    }
}
