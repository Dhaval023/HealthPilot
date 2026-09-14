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

        binding.toolbar.title = "$workoutName Plan"
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
                WorkoutExercise("Warm-up Walk", "Slow walking to loosen muscles", "${(3 * factor).toInt()} min", R.drawable.ic_walk),
                WorkoutExercise("Brisk Walking", "Increase pace for cardio", "${(12 * factor).toInt()} min", R.drawable.ic_walk),
                WorkoutExercise("Incline Walk", "Boost endurance", "${(3 * factor).toInt()} min", R.drawable.ic_walk),
                WorkoutExercise("Cool-down Walk", "Reduce heart rate", "${(2 * factor).toInt()} min", R.drawable.ic_walk)
            )

            normalized.contains("running") -> listOf(
                WorkoutExercise("Dynamic Warm-up", "Prepare muscles", "${(3 * factor).toInt()} min", R.drawable.running),
                WorkoutExercise("Easy Run", "Steady jogging pace", "${(8 * factor).toInt()} min", R.drawable.running),
                WorkoutExercise("Sprint Intervals", "Improve speed", "${(6 * factor).toInt()} min", R.drawable.running),
                WorkoutExercise("Cool-down Jog", "Recover gradually", "${(3 * factor).toInt()} min", R.drawable.running)
            )

            normalized == "cycling" ->listOf(
                WorkoutExercise("Easy Pedaling", "Warm up legs", "${(4 * factor).toInt()} min", R.drawable.cycling),
                WorkoutExercise("High Cadence Ride", "Improve endurance", "${(8 * factor).toInt()} min", R.drawable.cycling),
                WorkoutExercise("Hill Climb", "Build leg strength", "${(5 * factor).toInt()} min", R.drawable.cycling),
                WorkoutExercise("Recovery Ride", "Cool down", "${(3 * factor).toInt()} min", R.drawable.cycling)
            )

            normalized == "boxing" -> listOf(
                WorkoutExercise("Jump Rope", "Warm up body", "${(4 * factor).toInt()} min", R.drawable.boxing),
                WorkoutExercise("Shadow Boxing", "Technique practice", "${(5 * factor).toInt()} min", R.drawable.boxing),
                WorkoutExercise("Heavy Bag", "Power punches", "${(8 * factor).toInt()} min", R.drawable.boxing),
                WorkoutExercise("Speed Bag", "Improve coordination", "${(3 * factor).toInt()} min", R.drawable.boxing)
            )

            normalized == "swimming" -> listOf(
                WorkoutExercise("Warm-up Laps", "Easy freestyle", "${(5 * factor).toInt()} min", R.drawable.swimming),
                WorkoutExercise("Freestyle", "Main cardio", "${(8 * factor).toInt()} min", R.drawable.swimming),
                WorkoutExercise("Breaststroke", "Technique practice", "${(4 * factor).toInt()} min", R.drawable.swimming),
                WorkoutExercise("Cool-down", "Slow swimming", "${(3 * factor).toInt()} min", R.drawable.swimming)
            )

            normalized == "badminton" -> listOf(
                WorkoutExercise("Footwork Drill", "Quick court movement", "${(5 * factor).toInt()} min", R.drawable.badminton),
                WorkoutExercise("Smash Practice", "Power shots", "${(5 * factor).toInt()} min", R.drawable.badminton),
                WorkoutExercise("Net Play", "Control & reflex", "${(5 * factor).toInt()} min", R.drawable.badminton),
                WorkoutExercise("Match Rally", "Continuous play", "${(5 * factor).toInt()} min", R.drawable.badminton)
            )

            normalized == "tennis" -> listOf(
                WorkoutExercise("Footwork", "Court movement", "${(4 * factor).toInt()} min", R.drawable.tennis),
                WorkoutExercise("Forehand Drill", "Consistency", "${(5 * factor).toInt()} min", R.drawable.tennis),
                WorkoutExercise("Backhand Drill", "Shot control", "${(5 * factor).toInt()} min", R.drawable.tennis),
                WorkoutExercise("Serve Practice", "Accuracy", "${(6 * factor).toInt()} min", R.drawable.tennis)
            )

            normalized == "football"  -> listOf(
                WorkoutExercise("Warm-up Jog", "Prepare body", "${(4 * factor).toInt()} min", R.drawable.football),
                WorkoutExercise("Dribbling Drill", "Ball control", "${(5 * factor).toInt()} min", R.drawable.football),
                WorkoutExercise("Passing Drill", "Passing accuracy", "${(5 * factor).toInt()} min", R.drawable.football),
                WorkoutExercise("Shooting Practice", "Finishing skills", "${(6 * factor).toInt()} min", R.drawable.football)
            )

            normalized == "hockey"-> listOf(
                WorkoutExercise("Warm-up Run", "Increase mobility", "${(4 * factor).toInt()} min", R.drawable.hockey),
                WorkoutExercise("Stick Handling", "Ball control", "${(6 * factor).toInt()} min", R.drawable.hockey),
                WorkoutExercise("Passing Drill", "Team coordination", "${(5 * factor).toInt()} min", R.drawable.hockey),
                WorkoutExercise("Goal Shooting", "Accuracy", "${(5 * factor).toInt()} min", R.drawable.hockey)
            )

            normalized == "basketball" -> listOf(
                WorkoutExercise("Dynamic Warm-up", "Prepare muscles", "${(4 * factor).toInt()} min", R.drawable.basketball),
                WorkoutExercise("Dribbling Drill", "Ball handling", "${(5 * factor).toInt()} min", R.drawable.basketball),
                WorkoutExercise("Shooting Practice", "Improve accuracy", "${(6 * factor).toInt()} min", R.drawable.basketball),
                WorkoutExercise("Layup Drill", "Finishing practice", "${(5 * factor).toInt()} min", R.drawable.basketball)
            )

            normalized == "zumba"  -> listOf(
                WorkoutExercise("Warm-up Dance", "Light rhythm", "${(4 * factor).toInt()} min", R.drawable.zumba),
                WorkoutExercise("Cardio Routine", "Burn calories", "${(8 * factor).toInt()} min", R.drawable.zumba),
                WorkoutExercise("Dance Combo", "Full body movement", "${(5 * factor).toInt()} min", R.drawable.zumba),
                WorkoutExercise("Stretch & Relax", "Recovery", "${(3 * factor).toInt()} min", R.drawable.zumba)
            )

            normalized == "dancing" -> listOf(
                WorkoutExercise("Warm-up Dance", "Light rhythm", "${(4 * factor).toInt()} min", R.drawable.zumba),
                WorkoutExercise("Cardio Routine", "Burn calories", "${(8 * factor).toInt()} min", R.drawable.zumba),
                WorkoutExercise("Dance Combo", "Full body movement", "${(5 * factor).toInt()} min", R.drawable.zumba),
                WorkoutExercise("Stretch & Relax", "Recovery", "${(3 * factor).toInt()} min", R.drawable.zumba)
            )

            normalized == "aerobics" -> listOf(
                WorkoutExercise("Warm-up", "Light movement", "${(4 * factor).toInt()} min", R.drawable.aerobics),
                WorkoutExercise("High Knees", "Cardio exercise", "${(5 * factor).toInt()} min", R.drawable.aerobics),
                WorkoutExercise("Jumping Jacks", "Full body workout", "${(5 * factor).toInt()} min", R.drawable.aerobics),
                WorkoutExercise("Cool-down Stretch", "Relax muscles", "${(6 * factor).toInt()} min", R.drawable.aerobics)
            )

            normalized == "weightlifting" -> listOf(
                WorkoutExercise("Warm-up Set", "Light weights", "${(4 * factor).toInt()} min", R.drawable.weightlifting),
                WorkoutExercise("Squats", "Lower body strength", "${(5 * factor).toInt()} min", R.drawable.weightlifting),
                WorkoutExercise("Bench Press", "Chest strength", "${(5 * factor).toInt()} min", R.drawable.weightlifting),
                WorkoutExercise("Deadlift", "Full body strength", "${(6 * factor).toInt()} min", R.drawable.weightlifting)
            )

            normalized == "sit-ups" -> listOf(
                WorkoutExercise("Plank", "Core stability", "${(4 * factor).toInt()} min", R.drawable.situp),
                WorkoutExercise("Sit-ups", "Abdominal strength", "${(6 * factor).toInt()} min", R.drawable.situp),
                WorkoutExercise("Russian Twists", "Oblique muscles", "${(5 * factor).toInt()} min", R.drawable.situp),
                WorkoutExercise("Leg Raises", "Lower abs", "${(5 * factor).toInt()} min", R.drawable.situp)
            )

            normalized == "volleyball" -> listOf(
                WorkoutExercise("Jump Rope", "Warm up & footwork", "${(4 * factor).toInt()} min", R.drawable.volleyball),
                WorkoutExercise("Serve Practice", "Serving accuracy", "${(4 * factor).toInt()} min", R.drawable.volleyball),
                WorkoutExercise("Passing Drill", "Ball control", "${(4 * factor).toInt()} min", R.drawable.volleyball),
                WorkoutExercise("Setting Drill", "Accurate sets", "${(4 * factor).toInt()} min", R.drawable.volleyball),
                WorkoutExercise("Spike Practice", "Attack power", "${(4 * factor).toInt()} min", R.drawable.volleyball)
            )
            normalized == "cricket" -> listOf(
                WorkoutExercise("Warm-up Jog", "Light jogging and dynamic stretching", "${(4 * factor).toInt()} min", R.drawable.cricket),
                WorkoutExercise("Batting Practice", "Improve shot timing and control", "${(5 * factor).toInt()} min", R.drawable.cricket),
                WorkoutExercise("Bowling Drills", "Work on accuracy and consistency", "${(5 * factor).toInt()} min", R.drawable.cricket),
                WorkoutExercise("Fielding Practice", "Catching, throwing & reflex training", "${(4 * factor).toInt()} min", R.drawable.cricket),
                WorkoutExercise("Sprint Runs", "Improve speed between wickets", "${(2 * factor).toInt()} min", R.drawable.cricket)
            )

            normalized == "kabaddi" -> listOf(
                WorkoutExercise("Warm-up Jog", "Light jogging and dynamic stretching", "${(4 * factor).toInt()} min", R.drawable.kabaddi),
                WorkoutExercise("Raid Practice", "Improve agility and raiding techniques", "${(5 * factor).toInt()} min", R.drawable.kabaddi),
                WorkoutExercise("Defensive Holds", "Practice ankle holds and tackles", "${(5 * factor).toInt()} min", R.drawable.kabaddi),
                WorkoutExercise("Agility Drills", "Enhance speed, balance, and quick direction changes", "${(4 * factor).toInt()} min", R.drawable.kabaddi),
                WorkoutExercise("Sprint Runs", "Build explosive speed and endurance", "${(2 * factor).toInt()} min", R.drawable.kabaddi)
            )

            else -> listOf( // Default/Yoga-style
                WorkoutExercise("Breath Work", "Focus and relax", "${(3 * factor).toInt()} min", R.drawable.yoga),
                WorkoutExercise("Warm-up Stretches", "Loosen up", "${(4 * factor).toInt()} min", R.drawable.yoga),
                WorkoutExercise("Main Flow", "Core activity", "${(10 * factor).toInt()} min", R.drawable.yoga),
                WorkoutExercise("Cool-down", "Relax and recover", "${(3 * factor).toInt()} min", R.drawable.yoga)
            )
        }

        val tips = when {
            normalized == "walking" ->
                "• Wear comfortable walking shoes\n• Keep your posture upright\n• Swing your arms naturally\n• Stay hydrated"
            normalized.contains("running") ->
                "• Wear proper running shoes\n• Land softly on your feet\n• Keep a steady breathing rhythm\n• Warm up before running"
            normalized == "cycling" ->
                "• Adjust your bike seat properly\n• Wear a helmet\n• Maintain a steady cadence\n• Keep both hands on the handlebars"
            normalized == "boxing" ->
                "• Keep your hands up\n• Stay light on your feet\n• Wrap your wrists properly\n• Exhale with every punch"
            normalized == "swimming" ->
                "• Wear goggles for better vision\n• Practice controlled breathing\n• Stretch before entering the pool\n• Stay relaxed in the water"
            normalized == "badminton" ->
                "• Stay on your toes\n• Keep your racket ready\n• Focus on quick footwork\n• Maintain good balance"
            normalized == "tennis" ->
                "• Bend your knees slightly\n• Watch the ball closely\n• Grip the racket correctly\n• Recover to the center after every shot"
            normalized == "football" ->
                "• Communicate with teammates\n• Keep your head up\n• Stay hydrated\n• Warm up your legs before playing"
            normalized == "hockey" ->
                "• Wear protective gear\n• Keep your stick under control\n• Stay low for better balance\n• Watch your surroundings"
            normalized == "basketball" ->
                "• Bend your knees while defending\n• Keep your eyes on the court\n• Control your dribble\n• Stay light on your feet"
            normalized == "volleyball" ->
                "• Bend your knees before jumping\n• Call for the ball\n• Keep your hands ready\n• Focus on teamwork"
            normalized == "zumba" ->
                "• Wear supportive shoes\n• Follow the rhythm\n• Keep moving continuously\n• Drink water during breaks"
            normalized == "dancing" ->
                "• Warm up before dancing\n• Maintain good posture\n• Stay relaxed\n• Keep yourself hydrated"
            normalized == "aerobics" ->
                "• Start with a proper warm-up\n• Keep movements controlled\n• Breathe continuously\n• Wear supportive shoes"
            normalized == "weightlifting" ->
                "• Focus on proper form\n• Never hold your breath\n• Lift with controlled movements\n• Rest between sets"
            normalized == "sit-ups" ->
                "• Engage your core muscles\n• Avoid pulling your neck\n• Move slowly and steadily\n• Breathe out while lifting"
            normalized == "cricket" ->
                "• Warm up your shoulders and legs\n• Keep your eyes on the ball\n• Wear proper protective gear\n• Stay hydrated throughout the session"
            normalized == "yoga" ->
                "• Focus on your breathing\n• Don't force any pose\n• Move slowly and mindfully\n• Use a yoga mat for comfort"
            normalized == "kabaddi" ->
                "• Warm up your legs and shoulders\n• Maintain a low center of gravity\n• Focus on quick footwork and balance\n• Stay hydrated and breathe steadily"
            else ->
                "• Warm up before exercising\n• Wear comfortable clothing\n• Stay hydrated\n• Cool down after your workout"
        }
        binding.tvTips.text = tips

        binding.rvExercises.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExercises.adapter = WorkoutExerciseAdapter(exercises) { exercise ->
            showExerciseDetail(exercise, workoutName)
        }
    }

    private fun showExerciseDetail(exercise: WorkoutExercise, workoutName: String) {
        val dialog = BottomSheetDialog(requireContext(), R.style.CustomDialogTheme)
        val dBinding = BottomSheetYogaDetailBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        dBinding.tvDetailName.text = exercise.name
        dBinding.tvDetailDuration.text = "Duration: ${exercise.time}"
        dBinding.tvDetailDesc.text = exercise.desc

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
