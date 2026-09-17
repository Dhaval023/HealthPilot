package com.example.myfitnessapp.workout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentWorkoutPlanBinding
import com.example.myfitnessapp.databinding.BottomSheetYogaDetailBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class WorkoutPlanFragment : Fragment() {

    private var _binding: FragmentWorkoutPlanBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val workoutName = arguments?.getString("workoutName") ?: "Workout"
        val totalDuration = arguments?.getInt("totalDuration") ?: 20

        val context = requireContext()
        val nameResId = context.resources.getIdentifier(workoutName.lowercase().replace(" ", "_").replace("-", "_"), "string", context.packageName)
        val localizedName = if (nameResId != 0) getString(nameResId) else workoutName
        
        binding.toolbar.title = getString(R.string.plan_title_format, localizedName)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // Set button color based on workout
        val themeColor = when (workoutName.lowercase()) {
            "walking" -> R.color.accent_green
            "running" -> R.color.accent_red
            "cycling" -> R.color.accent_blue
            "boxing" -> R.color.accent_red
            "yoga" -> R.color.accent_purple
            else -> R.color.accent_purple
        }
        binding.btnStartWorkout.backgroundTintList = android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(requireContext(), themeColor)
        )

        setupRecyclerView(workoutName, totalDuration)

        binding.btnStartWorkout.setOnClickListener {
            val bundle = Bundle().apply {
                putString("workoutName", workoutName)
                putInt("durationMinutes", totalDuration)
            }
            findNavController().navigate(R.id.action_workoutPlanFragment_to_workoutTrackingFragment, bundle)
        }
    }

    private fun setupRecyclerView(workoutName: String, totalMinutes: Int) {
        val factor = totalMinutes.toFloat() / 20f
        val normalized = workoutName.lowercase()
        
        val exercises = when {
            normalized == "walking" -> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up_walk), getString(R.string.ex_desc_slow_walking), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.ic_walk),
                WorkoutExercise(getString(R.string.ex_brisk_walking), getString(R.string.ex_desc_increase_pace), "${(12 * factor).toInt()} ${getString(R.string.min)}", R.drawable.ic_walk),
                WorkoutExercise(getString(R.string.ex_incline_walk), getString(R.string.ex_desc_boost_endurance), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.ic_walk),
                WorkoutExercise(getString(R.string.ex_cool_down_walk), getString(R.string.ex_desc_reduce_hr), "${(2 * factor).toInt()} ${getString(R.string.min)}", R.drawable.ic_walk)
            )

            normalized.contains("running") -> listOf(
                WorkoutExercise(getString(R.string.ex_dynamic_warmup), getString(R.string.ex_prepare_muscles), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.running),
                WorkoutExercise(getString(R.string.ex_easy_run), getString(R.string.ex_steady_jogging), "${(8 * factor).toInt()} ${getString(R.string.min)}", R.drawable.running),
                WorkoutExercise(getString(R.string.ex_sprint_intervals), getString(R.string.ex_improve_speed), "${(6 * factor).toInt()} ${getString(R.string.min)}", R.drawable.running),
                WorkoutExercise(getString(R.string.ex_cooldown_jog), getString(R.string.ex_recover_gradually), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.running)
            )

            normalized == "cycling" ->listOf(
                WorkoutExercise(getString(R.string.ex_easy_pedaling), getString(R.string.ex_warm_up_legs), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.cycling),
                WorkoutExercise(getString(R.string.ex_high_cadence_ride), getString(R.string.ex_improve_endurance), "${(8 * factor).toInt()} ${getString(R.string.min)}", R.drawable.cycling),
                WorkoutExercise(getString(R.string.ex_hill_climb), getString(R.string.ex_build_leg_strength), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.cycling),
                WorkoutExercise(getString(R.string.ex_recovery_ride), getString(R.string.ex_recover_gradually), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.cycling)
            )

            normalized == "boxing" -> listOf(
                WorkoutExercise(getString(R.string.ex_jump_rope), getString(R.string.ex_warm_up_body), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.boxing),
                WorkoutExercise(getString(R.string.ex_shadow_boxing), getString(R.string.ex_technique_practice), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.boxing),
                WorkoutExercise(getString(R.string.ex_heavy_bag), getString(R.string.ex_power_punches), "${(8 * factor).toInt()} ${getString(R.string.min)}", R.drawable.boxing),
                WorkoutExercise(getString(R.string.ex_speed_bag), getString(R.string.ex_improve_coordination), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.boxing)
            )

            normalized == "swimming" -> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up_laps), getString(R.string.ex_desc_easy_freestyle), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.swimming),
                WorkoutExercise(getString(R.string.ex_freestyle), getString(R.string.ex_desc_main_cardio), "${(8 * factor).toInt()} ${getString(R.string.min)}", R.drawable.swimming),
                WorkoutExercise(getString(R.string.ex_breaststroke), getString(R.string.ex_desc_technique_practice), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.swimming),
                WorkoutExercise(getString(R.string.ex_cool_down), getString(R.string.ex_desc_slow_swimming), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.swimming)
            )

            normalized == "badminton" -> listOf(
                WorkoutExercise(getString(R.string.ex_footwork_drill), getString(R.string.ex_desc_quick_court_movement), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.badminton),
                WorkoutExercise(getString(R.string.ex_smash_practice), getString(R.string.ex_desc_power_shots), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.badminton),
                WorkoutExercise(getString(R.string.ex_net_play), getString(R.string.ex_desc_control_reflex), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.badminton),
                WorkoutExercise(getString(R.string.ex_match_rally), getString(R.string.ex_desc_continuous_play), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.badminton)
            )

            normalized == "tennis" -> listOf(
                WorkoutExercise(getString(R.string.ex_footwork_drill), getString(R.string.ex_desc_court_movement), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.tennis),
                WorkoutExercise(getString(R.string.ex_forehand_drill), getString(R.string.ex_desc_consistency), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.tennis),
                WorkoutExercise(getString(R.string.ex_backhand_drill), getString(R.string.ex_desc_shot_control), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.tennis),
                WorkoutExercise(getString(R.string.ex_serve_practice), getString(R.string.ex_desc_accuracy), "${(6 * factor).toInt()} ${getString(R.string.min)}", R.drawable.tennis)
            )

            normalized == "football"  -> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up_jog), getString(R.string.ex_desc_prepare_body), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.football),
                WorkoutExercise(getString(R.string.ex_dribbling_drill), getString(R.string.ex_desc_ball_control), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.football),
                WorkoutExercise(getString(R.string.ex_passing_drill), getString(R.string.ex_desc_passing_accuracy), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.football),
                WorkoutExercise(getString(R.string.ex_shooting_practice), getString(R.string.ex_desc_finishing_skills), "${(6 * factor).toInt()} ${getString(R.string.min)}", R.drawable.football)
            )

            normalized == "hockey"-> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up_run), getString(R.string.ex_desc_increase_mobility), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.hockey),
                WorkoutExercise(getString(R.string.ex_stick_handling), getString(R.string.ex_desc_ball_control), "${(6 * factor).toInt()} ${getString(R.string.min)}", R.drawable.hockey),
                WorkoutExercise(getString(R.string.ex_passing_drill), getString(R.string.ex_desc_team_coordination), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.hockey),
                WorkoutExercise(getString(R.string.ex_goal_shooting), getString(R.string.ex_desc_accuracy), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.hockey)
            )

            normalized == "basketball" -> listOf(
                WorkoutExercise(getString(R.string.ex_dynamic_warmup), getString(R.string.ex_desc_prepare_muscles), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.basketball),
                WorkoutExercise(getString(R.string.ex_dribbling_drill), getString(R.string.ex_desc_ball_handling), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.basketball),
                WorkoutExercise(getString(R.string.ex_shooting_practice), getString(R.string.ex_desc_improve_accuracy), "${(6 * factor).toInt()} ${getString(R.string.min)}", R.drawable.basketball),
                WorkoutExercise(getString(R.string.ex_layup_drill), getString(R.string.ex_desc_finishing_practice), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.basketball)
            )

            normalized == "zumba"  -> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up_dance), getString(R.string.ex_desc_light_rhythm), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.zumba),
                WorkoutExercise(getString(R.string.ex_cardio_routine), getString(R.string.ex_desc_burn_calories), "${(8 * factor).toInt()} ${getString(R.string.min)}", R.drawable.zumba),
                WorkoutExercise(getString(R.string.ex_dance_combo), getString(R.string.ex_desc_full_body_movement), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.zumba),
                WorkoutExercise(getString(R.string.ex_stretch_relax), getString(R.string.ex_desc_recovery), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.zumba)
            )

            normalized == "dancing" -> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up_dance), getString(R.string.ex_desc_light_rhythm), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.zumba),
                WorkoutExercise(getString(R.string.ex_cardio_routine), getString(R.string.ex_desc_burn_calories), "${(8 * factor).toInt()} ${getString(R.string.min)}", R.drawable.zumba),
                WorkoutExercise(getString(R.string.ex_dance_combo), getString(R.string.ex_desc_full_body_movement), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.zumba),
                WorkoutExercise(getString(R.string.ex_stretch_relax), getString(R.string.ex_desc_recovery), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.zumba)
            )

            normalized == "aerobics" -> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up), getString(R.string.ex_desc_light_movement), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.aerobics),
                WorkoutExercise(getString(R.string.ex_high_knees), getString(R.string.ex_desc_cardio_exercise), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.aerobics),
                WorkoutExercise(getString(R.string.ex_jumping_jacks), getString(R.string.ex_desc_full_body_workout), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.aerobics),
                WorkoutExercise(getString(R.string.ex_cool_down_stretch), getString(R.string.ex_desc_relax_muscles), "${(6 * factor).toInt()} ${getString(R.string.min)}", R.drawable.aerobics)
            )

            normalized == "weightlifting" -> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up_set), getString(R.string.ex_desc_light_weights), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.weightlifting),
                WorkoutExercise(getString(R.string.ex_squats), getString(R.string.ex_desc_lower_body_strength), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.weightlifting),
                WorkoutExercise(getString(R.string.ex_bench_press), getString(R.string.ex_desc_chest_strength), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.weightlifting),
                WorkoutExercise(getString(R.string.ex_deadlift), getString(R.string.ex_desc_full_body_strength), "${(6 * factor).toInt()} ${getString(R.string.min)}", R.drawable.weightlifting)
            )

            normalized == "sit-ups" -> listOf(
                WorkoutExercise(getString(R.string.ex_plank), getString(R.string.ex_desc_core_stability), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.situp),
                WorkoutExercise(getString(R.string.sit_ups), getString(R.string.ex_desc_abdominal_strength), "${(6 * factor).toInt()} ${getString(R.string.min)}", R.drawable.situp),
                WorkoutExercise(getString(R.string.ex_russian_twists), getString(R.string.ex_desc_oblique_muscles), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.situp),
                WorkoutExercise(getString(R.string.ex_leg_raises), getString(R.string.ex_desc_lower_abs), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.situp)
            )

            normalized == "volleyball" -> listOf(
                WorkoutExercise(getString(R.string.ex_jump_rope), getString(R.string.ex_desc_jump_rope_footwork), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.volleyball),
                WorkoutExercise(getString(R.string.ex_serve_practice), getString(R.string.ex_desc_serving_accuracy), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.volleyball),
                WorkoutExercise(getString(R.string.ex_passing_drill), getString(R.string.ex_desc_ball_control), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.volleyball),
                WorkoutExercise(getString(R.string.ex_setting_drill), getString(R.string.ex_desc_accurate_sets), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.volleyball),
                WorkoutExercise(getString(R.string.ex_spike_practice), getString(R.string.ex_desc_attack_power), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.volleyball)
            )
            normalized == "cricket" -> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up_jog), getString(R.string.ex_desc_cricket_warmup), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.cricket),
                WorkoutExercise(getString(R.string.ex_batting_practice), getString(R.string.ex_desc_batting_control), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.cricket),
                WorkoutExercise(getString(R.string.ex_bowling_drills), getString(R.string.ex_desc_bowling_consistency), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.cricket),
                WorkoutExercise(getString(R.string.ex_fielding_practice), getString(R.string.ex_desc_fielding_reflex), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.cricket),
                WorkoutExercise(getString(R.string.ex_sprint_runs), getString(R.string.ex_desc_speed_wickets), "${(2 * factor).toInt()} ${getString(R.string.min)}", R.drawable.cricket)
            )

            normalized == "kabaddi" -> listOf(
                WorkoutExercise(getString(R.string.ex_warm_up_jog), getString(R.string.ex_desc_cricket_warmup), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.kabaddi),
                WorkoutExercise(getString(R.string.ex_raid_practice), getString(R.string.ex_desc_raid_agility), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.kabaddi),
                WorkoutExercise(getString(R.string.ex_defensive_holds), getString(R.string.ex_desc_defensive_tackles), "${(5 * factor).toInt()} ${getString(R.string.min)}", R.drawable.kabaddi),
                WorkoutExercise(getString(R.string.ex_agility_drills), getString(R.string.ex_desc_agility_drills), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.kabaddi),
                WorkoutExercise(getString(R.string.ex_sprint_runs), getString(R.string.ex_desc_explosive_speed), "${(2 * factor).toInt()} ${getString(R.string.min)}", R.drawable.kabaddi)
            )

            else -> listOf( // Default/Yoga-style
                WorkoutExercise(getString(R.string.ex_breath_work), getString(R.string.ex_desc_focus_relax), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.yoga),
                WorkoutExercise(getString(R.string.ex_warm_up_stretches), getString(R.string.ex_desc_loosen_up), "${(4 * factor).toInt()} ${getString(R.string.min)}", R.drawable.yoga),
                WorkoutExercise(getString(R.string.ex_main_flow), getString(R.string.ex_desc_core_activity), "${(10 * factor).toInt()} ${getString(R.string.min)}", R.drawable.yoga),
                WorkoutExercise(getString(R.string.ex_cool_down), getString(R.string.ex_desc_relax_recover), "${(3 * factor).toInt()} ${getString(R.string.min)}", R.drawable.yoga)
            )
        }

        val tips = when {
            normalized == "walking" ->
                getString(R.string.tips_walking)
            normalized.contains("running") ->
                getString(R.string.tips_running)
            normalized == "cycling" ->
                getString(R.string.tips_cycling)
            else ->
                getString(R.string.tips_general)
        }
        binding.tvTips.text = tips

        binding.rvExercises.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExercises.adapter = WorkoutExerciseAdapter(exercises) { exercise ->
            showExerciseDetail(exercise, workoutName)
        }
    }

    private fun showExerciseDetail(exercise: WorkoutExercise, workoutName: String) {
        val context = requireContext()
        val dialog = BottomSheetDialog(context, R.style.CustomDialogTheme)
        val dBinding = BottomSheetYogaDetailBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        val nameResId = context.resources.getIdentifier("ex_${exercise.name.lowercase().replace(" ", "_").replace("-", "_")}", "string", context.packageName)
        dBinding.tvDetailName.text = if (nameResId != 0) context.getString(nameResId) else exercise.name
        
        dBinding.tvDetailDuration.text = getString(R.string.duration_label, exercise.time)
        
        val descResId = context.resources.getIdentifier("ex_${exercise.desc.lowercase().replace(" ", "_").replace("-", "_")}", "string", context.packageName)
        dBinding.tvDetailDesc.text = if (descResId != 0) context.getString(descResId) else exercise.desc

        val lottieRes = when (workoutName.lowercase()) {
            "walking" -> R.raw.walking
            "running" -> R.raw.running
            "cycling" -> R.raw.cycling
            "boxing" -> R.raw.boxing_intro
            "yoga" -> R.raw.yoga
            else -> {
                val normalized = workoutName.lowercase().replace("-", "_").replace(" ", "_")
                val resId = resources.getIdentifier(normalized, "raw", requireContext().packageName)
                if (resId != 0) resId else R.raw.yoga
            }
        }
        dBinding.lottieExercise.setAnimation(lottieRes)

        dBinding.btnCloseDetail.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
