package com.example.myfitnessapp.utils;

import android.widget.ImageView;
import androidx.databinding.BindingAdapter;
import com.example.myfitnessapp.R;

public class BindingAdapters {
    @BindingAdapter("mealIcon")
    public static void setMealIcon(ImageView view, String mealType) {
        int icon;
        if (mealType == null) {
            icon = R.drawable.calories;
        } else {
            switch (mealType.toLowerCase()) {
                case "breakfast":
                    icon = R.drawable.ic_breakfast;
                    break;
                case "lunch":
                    icon = R.drawable.ic_lunch;
                    break;
                case "snack":
                case "evening snack":
                case "evening snacks":
                    icon = R.drawable.ic_evening_snacks;
                    break;
                case "dinner":
                    icon = R.drawable.ic_dinner;
                    break;
                default:
                    icon = R.drawable.calories;
                    break;
            }
        }
        view.setImageResource(icon);
    }
}