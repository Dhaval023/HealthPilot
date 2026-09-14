package com.example.myfitnessapp.workout

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentWorkoutIntroBinding
import com.example.myfitnessapp.databinding.ItemYogaBenefitBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WorkoutIntroFragment : Fragment() {

    private var _binding: FragmentWorkoutIntroBinding? = null
    private val binding get() = _binding!!
    private var workoutTimeMinutes = 20

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutIntroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val workoutName = arguments?.getString("workoutName") ?: "Workout"
        setupUI(workoutName)

        binding.btnBack.setOnClickListener {
            findNavController().previousBackStackEntry?.savedStateHandle?.set("show_workout_list", true)
            findNavController().popBackStack()
        }

        binding.btnViewPlan.setOnClickListener {
            val bundle = Bundle().apply {
                putString("workoutName", workoutName)
                putInt("totalDuration", workoutTimeMinutes)
            }
            findNavController().navigate(R.id.action_workoutIntroFragment_to_workoutPlanFragment, bundle)
        }

        binding.btnEditTime.setOnClickListener {
            showEditTimeDialog()
        }

        binding.btnStartWorkout.setOnClickListener {
            startWorkout(workoutName)
        }
    }

    private fun startWorkout(workoutName: String) {
        val bundle = Bundle().apply {
            putString("workoutName", workoutName)
            putInt("durationMinutes", workoutTimeMinutes)
        }
        findNavController().navigate(R.id.action_workoutIntroFragment_to_workoutTrackingFragment, bundle)
    }

    private fun setupUI(workoutName: String) {
        binding.tvWorkoutName.text = workoutName.uppercase()
        
        val theme = getWorkoutTheme(workoutName)
        workoutTimeMinutes = theme.defaultTime
        binding.tvTotalTime.text = "${theme.defaultTime} Min"
        binding.tvSubtitle.text = theme.subtitle
        binding.tvSessionTitle.text = theme.sessionTitle
        binding.tvSessionDesc.text = theme.sessionDesc
        binding.btnStartWorkout.text = "Start $workoutName"
        
        // Lottie
        binding.lottieAnimation.setAnimation(theme.lottieRes)
        
        // Aerobics lottie often has a lot of padding, let's make it larger
        if (workoutName.lowercase().contains("aerobics")) {
            val largeSize = (360 * resources.displayMetrics.density).toInt()
            binding.lottieAnimation.layoutParams.width = largeSize
            binding.lottieAnimation.layoutParams.height = largeSize
        } else {
            val normalSize = (280 * resources.displayMetrics.density).toInt()
            binding.lottieAnimation.layoutParams.width = normalSize
            binding.lottieAnimation.layoutParams.height = normalSize
        }
        binding.lottieAnimation.requestLayout()
        
        // Colors
        val themeColor = ContextCompat.getColor(requireContext(), theme.colorRes)
        binding.tvWorkoutName.setTextColor(themeColor)
        binding.btnStartWorkout.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.ivSparkle.imageTintList = android.content.res.ColorStateList.valueOf(themeColor)
        binding.tvTodaySessionLabel.setTextColor(themeColor)
        binding.tvTotalTime.setTextColor(themeColor)
        binding.btnEditTime.backgroundTintList = android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(themeColor, 25))
        binding.ivProgressIcon.imageTintList = android.content.res.ColorStateList.valueOf(themeColor)
        
        // Benefits
        setupBenefit(binding.benefit1, theme.benefits[0], theme.colorRes)
        setupBenefit(binding.benefit2, theme.benefits[1], theme.colorRes)
        setupBenefit(binding.benefit3, theme.benefits[2], theme.colorRes)
        setupBenefit(binding.benefit4, theme.benefits[3], theme.colorRes)
    }

    private fun setupBenefit(itemBinding: ItemYogaBenefitBinding, benefit: Benefit, themeColorRes: Int) {
        val colorRes = benefit.benefitColorRes ?: themeColorRes
        val color = ContextCompat.getColor(requireContext(), colorRes)
        itemBinding.ivBenefitIcon.setImageResource(benefit.iconRes)
        itemBinding.tvBenefitTitle.text = benefit.title
        itemBinding.tvBenefitSub.text = benefit.subtitle
        itemBinding.ivBenefitIcon.imageTintList = android.content.res.ColorStateList.valueOf(color)
        itemBinding.flIconContainer.backgroundTintList = android.content.res.ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 25))
    }

    private fun showEditTimeDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setText(workoutTimeMinutes.toString())
        input.setSelection(input.text.length)

        val container = FrameLayout(requireContext())
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val margin = (24 * resources.displayMetrics.density).toInt()
        params.leftMargin = margin
        params.rightMargin = margin
        input.layoutParams = params
        container.addView(input)

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Set Duration")
            .setMessage("Enter session time in minutes:")
            .setView(container)
            .setPositiveButton("Set") { _, _ ->
                val newTime = input.text.toString()
                if (newTime.isNotEmpty()) {
                    workoutTimeMinutes = newTime.toInt()
                    binding.tvTotalTime.text = "$workoutTimeMinutes Min"
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getWorkoutTheme(name: String): WorkoutTheme {
        val normalized = name.lowercase()
        return when {
            normalized.contains("running") -> WorkoutTheme(
                colorRes = R.color.accent_red,
                lottieRes = if (normalized.contains("indoor")) R.raw.indoor_running else R.raw.running,
                subtitle = if (normalized.contains("indoor")) "Run anytime, rain or shine." else "Get Ready...",
                sessionTitle = if (normalized.contains("indoor")) "Treadmill Session" else "Morning Run",
                sessionDesc = if (normalized.contains("indoor")) "Focus on your pace and breathing." else "Enjoy the fresh air and keep a steady pace.",
                defaultTime = 30,
                benefits = if (normalized.contains("indoor")) {
                    listOf(
                        Benefit(R.drawable.calories, "Burn Calories", "Weight Control", R.color.accent_yellow),
                        Benefit(R.drawable.ic_heart, "Heart Health", "Strong Cardio", R.color.accent_red),
                        Benefit(R.drawable.running, "Build Endurance", "Run Longer", R.color.accent_teal),
                        Benefit(R.drawable.ic_lightning, "Boost Energy", "Stay Active", R.color.accent_orange)
                    )
                } else {
                    listOf(
                        Benefit(R.drawable.running, "Improve", "Endurance", R.color.accent_lime),
                        Benefit(R.drawable.calories, "Burn", "Fat", R.color.accent_yellow),
                        Benefit(R.drawable.ic_heart, "Strong", "Heart", R.color.accent_red),
                        Benefit(R.drawable.ic_lightning, "Boost", "Stamina", R.color.accent_teal)
                    )
                }
            )
            normalized == "walking" -> WorkoutTheme(
                colorRes = R.color.accent_green,
                lottieRes = R.raw.walking,
                subtitle = "Get Ready...",
                sessionTitle = "Evening Walk",
                sessionDesc = "Refresh your mind with a peaceful walk.",
                defaultTime = 25,
                benefits = listOf(
                    Benefit(R.drawable.ic_walk, "Daily", "Activity", R.color.white),
                    Benefit(R.drawable.stay_healthy, "Weight", "Control", R.color.accent_lime),
                    Benefit(R.drawable.ic_heart, "Heart", "Health", R.color.accent_red),
                    Benefit(R.drawable.smile, "Better", "Mood", R.color.accent_yellow)
                )
            )
            normalized == "cycling" -> WorkoutTheme(
                colorRes = R.color.accent_blue,
                lottieRes = R.raw.cycling,
                subtitle = "Get Ready...",
                sessionTitle = "Outdoor Ride",
                sessionDesc = "Challenge yourself and improve your fitness.",
                defaultTime = 40,
                benefits = listOf(
                    Benefit(R.drawable.cycling, "Leg", "Strength", R.color.accent_lime),
                    Benefit(R.drawable.ic_heart, "Cardio", "Fitness", R.color.accent_red),
                    Benefit(R.drawable.calories, "Burn", "Calories", R.color.accent_yellow),
                    Benefit(R.drawable.ic_lightning, "Endurance", "Stamina", R.color.accent_teal)
                )
            )
            normalized == "boxing" -> WorkoutTheme(
                colorRes = R.color.accent_red,
                lottieRes = R.raw.boxing_intro,
                subtitle = "Train with power and precision.",
                sessionTitle = "Boxing Training",
                sessionDesc = "Build power, speed and mental toughness.",
                defaultTime = 35,
                benefits = listOf(
                    Benefit(R.drawable.ic_refresh, "Fast Reflexes", "Quick Response", R.color.accent_yellow),
                    Benefit(R.drawable.boxing, "Power Punch", "Upper Strength", R.color.accent_red),
                    Benefit(R.drawable.ic_heart, "Heart Health", "Cardio Fitness", R.color.accent_pink),
                    Benefit(R.drawable.improve_focus, "Mental Focus", "Stay Sharp", R.color.accent_purple)
                )
            )
            normalized == "yoga" -> WorkoutTheme(
                colorRes = R.color.accent_purple,
                lottieRes = R.raw.yoga,
                subtitle = "Find your inner peace",
                sessionTitle = "Morning Yoga Flow",
                sessionDesc = "A perfect blend of stretching, breathing and mindfulness.",
                defaultTime = 20,
                benefits = listOf(
                    Benefit(R.drawable.yoga, "Relax Mind", "Reduce Stress", R.color.accent_purple),
                    Benefit(R.drawable.stay_healthy, "Stay Healthy", "Better Lifestyle", R.color.accent_green),
                    Benefit(R.drawable.improve_focus, "Improve Focus", "Mental Clarity", R.color.accent_purple),
                    Benefit(R.drawable.ic_heart, "Inner Peace", "Feel Balanced", R.color.accent_pink)
                )
            )
            normalized == "swimming" -> WorkoutTheme(
                colorRes = R.color.accent_teal,
                lottieRes = R.raw.swimming,
                subtitle = "Strengthen your entire body",
                sessionTitle = "Dive In..",
                sessionDesc = "Full body workout with low impact on joints.",
                defaultTime = 45,
                benefits = listOf(
                    Benefit(R.drawable.swimming, "Full Body", "Muscle Strength", R.color.accent_yellow),
                    Benefit(R.drawable.ic_heart, "Heart Health", "Better Cardio", R.color.accent_red),
                    Benefit(R.drawable.lungs, "Lung Capacity", "Better Breathing", R.color.live_red),
                    Benefit(R.drawable.ic_lightning, "Calories", "Weight Control", R.color.accent_teal)
                )
            )
            normalized == "badminton" -> WorkoutTheme(
                colorRes = R.color.accent_yellow,
                lottieRes = R.raw.badminton,
                subtitle = "Boost your speed and reflexes",
                sessionTitle = "Match Play",
                sessionDesc = "Quick reflexes and high intensity cardio.",
                defaultTime = 60,
                benefits = listOf(
                    Benefit(R.drawable.badminton, " Movement", "Better Footwork", R.color.accent_lime),
                    Benefit(R.drawable.ic_lightning, "Reflexes", "Faster Reaction", R.color.accent_yellow),
                    Benefit(R.drawable.weightgain_male, "Fitness", "Endurance", R.color.accent_red),
                    Benefit(R.drawable.ic_droid, "Focus", " Coordination", R.color.arc_blue)
                )
            )
            normalized == "football" -> WorkoutTheme(
                colorRes = R.color.accent_green,
                lottieRes = R.raw.football,
                subtitle = "Train your speed and teamwork.",
                sessionTitle = "Team Practice",
                sessionDesc = "Endurance and strategic movement.",
                defaultTime = 90,
                benefits = listOf(
                    Benefit(R.drawable.running, "Quick Speed", "Fast Sprint", R.color.accent_red),
                    Benefit(R.drawable.football, "Teamwork", "Better Coordination", R.color.accent_blue),
                    Benefit(R.drawable.ic_lightning, "Strong Endurance", "Play Longer", R.color.accent_yellow),
                    Benefit(R.drawable.ic_droid, "Ball Control", "Improve Skills", R.color.accent_green)
                )
            )
            normalized == "dancing" -> WorkoutTheme(
                colorRes = R.color.accent_dance,
                lottieRes = R.raw.dancing,
                subtitle = "Dance like nobody's watching.",
                sessionTitle = "Get Ready...",
                sessionDesc = "Move with rhythm and energy",
                defaultTime = 60,
                benefits = listOf(
                    Benefit(R.drawable.ic_heart, "Health", "Cardio Fitness", R.color.accent_red),
                    Benefit(R.drawable.smile, "Happy Mood", "Stress Relief", R.color.accent_yellow),
                    Benefit(R.drawable.ic_refresh, "Balance", "Better Coordination", R.color.accent_dance),
                    Benefit(R.drawable.ic_heart, "Calories", "Stay Active", R.color.accent_yellow)
                )
            )
            normalized == "cricket" -> WorkoutTheme(
                colorRes = R.color.accent_lime,
                lottieRes = R.raw.cricket,
                subtitle = "Build focus, power and precision.",
                sessionTitle = "Net Session",
                sessionDesc = "Focus on coordination and skill.",
                defaultTime = 60,
                benefits = listOf(
                    Benefit(R.drawable.improve_focus, "Sharp Focus", "Better Timing", R.color.accent_purple),
                    Benefit(R.drawable.cricket, "Power Hitting", "Build Strength", R.color.accent_red),
                    Benefit(R.drawable.ic_refresh, "Quick Reflexes", "Fast Reaction", R.color.accent_yellow),
                    Benefit(R.drawable.football, "Team Spirit", "Play Together", R.color.accent_blue)
                )
            )
            normalized == "zumba" -> WorkoutTheme(
                colorRes = R.color.accent_pink,
                lottieRes = R.raw.zumba,
                subtitle = "Dance, sweat and have fun.",
                sessionTitle = "Dance Flow",
                sessionDesc = "Fun and energetic cardio workout.",
                defaultTime = 45,
                benefits = listOf(
                    Benefit(R.drawable.zumba, "Burn Calories", "Weight Loss", R.color.accent_orange),
                    Benefit(R.drawable.smile, "Happy Mood", "Stress Relief", R.color.accent_yellow),
                    Benefit(R.drawable.ic_heart, "Heart Health", "Cardio Fitness", R.color.accent_red),
                    Benefit(R.drawable.dancing, "Better Rhythm", "Body Coordination", R.color.accent_dance)
                )
            )
            normalized == "aerobics" -> WorkoutTheme(
                colorRes = R.color.accent_orange,
                lottieRes = R.raw.aerobics,
                subtitle = "Stay fit with every move.",
                sessionTitle = "Aerobics Session",
                sessionDesc = "Dynamic and rhythmic exercise to improve fitness.",
                defaultTime = 30,
                benefits = listOf(
                    Benefit(R.drawable.ic_heart, "Heart Health", "Strong Cardio", R.color.accent_red),
                    Benefit(R.drawable.ic_fire, "Fat Burn", "Weight Loss", R.color.accent_orange),
                    Benefit(R.drawable.ic_lightning, "Energy Boost", "Stay Active", R.color.accent_yellow),
                    Benefit(R.drawable.weightgain_male, "Better Stamina", "Build Endurance", R.color.accent_teal)
                )
            )
            normalized == "hockey" -> WorkoutTheme(
                colorRes = R.color.accent_indigo,
                lottieRes = R.raw.hockey,
                subtitle = "Master speed and precision.",
                sessionTitle = "Hockey Training",
                sessionDesc = "Fast-paced game requiring agility and focus.",
                defaultTime = 70,
                benefits = listOf(
                    Benefit(R.drawable.ic_refresh, "Quick Reflexes", "Fast Response", R.color.accent_yellow),
                    Benefit(R.drawable.hockey, "Power Shots", "Build Strength", R.color.accent_red),
                    Benefit(R.drawable.ic_droid, "Sharp Control", "Stick Skills", R.color.accent_blue),
                    Benefit(R.drawable.ic_lightning, "High Endurance", "Play Longer", R.color.accent_teal)
                )
            )
            normalized == "kabaddi" -> WorkoutTheme(
                colorRes = R.color.accent_brown,
                lottieRes = R.raw.kabaddi,
                subtitle = "Unleash your power and agility.",
                sessionTitle = "Kabaddi Match",
                sessionDesc = "High-intensity contact sport for strength and stamina.",
                defaultTime = 40,
                benefits = listOf(
                    Benefit(R.drawable.kabaddi, "Body Strength", "Power Moves", R.color.accent_red),
                    Benefit(R.drawable.ic_walk, "Quick Agility", "Fast Footwork", R.color.accent_lime),
                    Benefit(R.drawable.ic_lightning, "Strong Stamina", "Long Performance", R.color.accent_yellow),
                    Benefit(R.drawable.improve_focus, "Mental Focus", "Smart Decisions", R.color.accent_purple)
                )
            )
            normalized == "sit-ups" -> WorkoutTheme(
                colorRes = R.color.accent_blue,
                lottieRes = R.raw.sit_ups,
                subtitle = "Strengthen your core every day.",
                sessionTitle = "Core Session",
                sessionDesc = "Focus on abdominal strength and stability.",
                defaultTime = 15,
                benefits = listOf(
                    Benefit(R.drawable.situp, "Strong Core", "Better Stability", R.color.accent_blue),
                    Benefit(R.drawable.ic_fire, "Flat Abs", "Core Toning", R.color.accent_orange),
                    Benefit(R.drawable.stay_healthy, "Better Posture", "Body Balance", R.color.accent_green),
                    Benefit(R.drawable.ic_lightning, "Build Strength", "Daily Fitness", R.color.accent_red)
                )
            )
            normalized == "weightlifting" -> WorkoutTheme(
                colorRes = R.color.accent_red,
                lottieRes = R.raw.weightlifting,
                subtitle = "Lift stronger, grow stronger.",
                sessionTitle = "Power Session",
                sessionDesc = "Build muscle and increase overall strength.",
                defaultTime = 45,
                benefits = listOf(
                    Benefit(R.drawable.weightlifting, "Muscle Growth", "Build Strength", R.color.accent_red),
                    Benefit(R.drawable.ic_fire, "Burn Fat", "Lean Body", R.color.accent_orange),
                    Benefit(R.drawable.stay_healthy, "Bone Strength", "Healthy Bones", R.color.accent_blue),
                    Benefit(R.drawable.ic_lightning, "Power Boost", "Improve Performance", R.color.accent_yellow)
                )
            )
            normalized == "wrestling" -> WorkoutTheme(
                colorRes = R.color.accent_brown,
                lottieRes = R.raw.wrestling,
                subtitle = "Master strength and discipline.",
                sessionTitle = "Grappling Session",
                sessionDesc = "Full-body workout focusing on control and endurance.",
                defaultTime = 45,
                benefits = listOf(
                    Benefit(R.drawable.wrestling, "Full Strength", "Power Training", R.color.accent_red),
                    Benefit(R.drawable.ic_refresh, "Body Control", "Better Balance", R.color.accent_blue),
                    Benefit(R.drawable.ic_lightning, "High Endurance", "Last Longer", R.color.accent_yellow),
                    Benefit(R.drawable.improve_focus, "Mental Toughness", "Stay Focused", R.color.accent_purple)
                )
            )
            normalized == "basketball" -> WorkoutTheme(
                colorRes = R.color.accent_orange,
                lottieRes = R.raw.basketball,
                subtitle = "Jump higher, play smarter.",
                sessionTitle = "Hoops Session",
                sessionDesc = "Improve coordination, speed, and cardiovascular health.",
                defaultTime = 60,
                benefits = listOf(
                    Benefit(R.drawable.running, "Quick Speed", "Fast Movement", R.color.accent_red),
                    Benefit(R.drawable.basketball, "Vertical Jump", "Explosive Power", R.color.accent_orange),
                    Benefit(R.drawable.football, "Teamwork", "Better Passing", R.color.accent_blue),
                    Benefit(R.drawable.ic_heart, "Heart Health", "Strong Cardio", R.color.accent_pink)
                )
            )
            normalized == "tennis" -> WorkoutTheme(
                colorRes = R.color.accent_lime,
                lottieRes = R.raw.tennis,
                subtitle = "Play with speed and precision.",
                sessionTitle = "Court Session",
                sessionDesc = "High-energy game for agility and mental alertness.",
                defaultTime = 60,
                benefits = listOf(
                    Benefit(R.drawable.ic_refresh, "Quick Reflexes", "Fast Response", R.color.accent_yellow),
                    Benefit(R.drawable.improve_focus, "Sharp Focus", "Mental Alertness", R.color.accent_purple),
                    Benefit(R.drawable.tennis, "Body Agility", "Quick Footwork", R.color.accent_lime),
                    Benefit(R.drawable.ic_heart, "Heart Health", "Cardio Fitness", R.color.accent_red)
                )
            )
            normalized == "volleyball" -> WorkoutTheme(
                colorRes = R.color.accent_teal,
                lottieRes = R.raw.volleyball,
                subtitle = "Rise higher with every jump.",
                sessionTitle = "Volley Session",
                sessionDesc = "Team-based sport for explosive power and coordination.",
                defaultTime = 60,
                benefits = listOf(
                    Benefit(R.drawable.volleyball, "Vertical Jump", "Explosive Power", R.color.accent_teal),
                    Benefit(R.drawable.football, "Teamwork", "Better Coordination", R.color.accent_blue),
                    Benefit(R.drawable.ic_refresh, "Quick Reflexes", "Fast Reaction", R.color.accent_yellow),
                    Benefit(R.drawable.ic_lightning, "Body Strength", "Powerful Performance", R.color.accent_red)
                )
            )
            else -> {
                val colorRes = when {
                    normalized.contains("wrestling") || normalized.contains("kabaddi") -> R.color.accent_brown
                    normalized.contains("basketball") || normalized.contains("aerobics") -> R.color.accent_orange
                    normalized.contains("tennis") -> R.color.accent_lime
                    normalized.contains("volleyball") -> R.color.accent_teal
                    normalized.contains("hockey") -> R.color.accent_indigo
                    normalized.contains("weightlifting") -> R.color.accent_red
                    normalized.contains("sit-ups") -> R.color.accent_blue
                    else -> R.color.accent_purple
                }
                
                val lottieRes = resources.getIdentifier(normalized.replace("-", "_").replace(" ", "_"), "raw", requireContext().packageName).let {
                    if (it != 0) it else R.raw.yoga
                }

                WorkoutTheme(
                    colorRes = colorRes,
                    lottieRes = lottieRes,
                    subtitle = "Get Ready...",
                    sessionTitle = "${name} Session",
                    sessionDesc = "Stay active and push your limits.",
                    defaultTime = 30,
                    benefits = listOf(
                        Benefit(R.drawable.ic_fire, "Burn", "Fat", R.color.accent_orange),
                        Benefit(R.drawable.ic_heart, "Strong", "Heart", R.color.accent_red),
                        Benefit(R.drawable.ic_walk, "Stay", "Active", R.color.accent_green),
                        Benefit(R.drawable.ic_refresh, "Refresh", "", R.color.accent_blue)
                    )
                )
            }
        }
    }

    private data class WorkoutTheme(
        val colorRes: Int,
        val lottieRes: Int,
        val subtitle: String,
        val sessionTitle: String,
        val sessionDesc: String,
        val defaultTime: Int,
        val benefits: List<Benefit>
    )

    private data class Benefit(
        val iconRes: Int, 
        val title: String, 
        val subtitle: String,
        val benefitColorRes: Int? = null
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
