package com.example.myfitnessapp.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Color
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.MainActivity
import com.example.myfitnessapp.R
import com.example.myfitnessapp.ble.BleManager
import com.example.myfitnessapp.databinding.BottomSheetDeviceListBinding
import com.example.myfitnessapp.databinding.FragmentDashboardBinding
import com.example.myfitnessapp.databinding.DialogWorkoutListBinding
import com.example.myfitnessapp.onboarding.OnboardingManager
import com.example.myfitnessapp.onboarding.OnboardingOverlayView
import com.example.myfitnessapp.onboarding.OnboardingStep
import com.example.myfitnessapp.viewmodel.DashboardViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = requireActivity().application as HealthPilot
                val repository = app.repository
                return DashboardViewModel(app, repository) as T
            }
        }
    }

    private var isOnboardingStarted = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("show_workout_list")
            ?.observe(viewLifecycleOwner) { show ->
                if (show) {
                    showWorkoutListBottomSheet()
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>("show_workout_list")
                }
            }

        setupUI()
        observeViewModel()

        binding.btnDrawer.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        // Show back button if this is the "Schedule" screen
        if (findNavController().currentDestination?.id == R.id.scheduleFragment) {
            binding.btnBack.visibility = View.VISIBLE
            binding.btnDrawer.visibility = View.GONE
            binding.tvGreeting.text = "My Schedule"
            binding.btnBack.setOnClickListener {
                findNavController().navigateUp()
            }
        }
    }

    private fun setupUI() {
        binding.dateText.text = SimpleDateFormat("MMM, d", Locale.getDefault()).format(Date())

        binding.connectButton.setOnClickListener {
            showDeviceListBottomSheet()
        }

        binding.cardHeart.root.setOnClickListener { navigateToReport("heart") }
        binding.cardSteps.root.setOnClickListener { navigateToReport("steps") }
        binding.cardCalories.root.setOnClickListener { navigateToReport("calories") }
        binding.cardDistance.root.setOnClickListener { navigateToReport("distance") }

        binding.cardStartWorkout.setOnClickListener {
            showWorkoutListBottomSheet()
        }

        binding.macroProtein.apply {
            macroTitle.text = "Protein"
            macroTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_lime))
            macroProgress.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.accent_lime))
        }
        binding.macroCarbs.apply {
            macroTitle.text = "Carbs"
            macroTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_orange))
            macroProgress.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.accent_orange))
        }
        binding.macroFats.apply {
            macroTitle.text = "Fats"
            macroTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_pink))
            macroProgress.setIndicatorColor(ContextCompat.getColor(requireContext(), R.color.accent_pink))
        }

        binding.cardHeart.apply {
            cardTitle.text = "HEART RATE"
            cardUnit.text = "BPM"
            cardIcon.setImageResource(R.drawable.ic_heart)
            cardIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.accent_red))
        }
        binding.cardSteps.apply {
            cardTitle.text = "STEPS"
            cardIcon.setImageResource(R.drawable.ic_walk)
            cardIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.accent_lime))
        }
        binding.cardCalories.apply {
            cardTitle.text = "BURNED"
            cardUnit.text = "KCAL"
            cardIcon.setImageResource(R.drawable.calories)
            cardIcon.imageTintList = null // Use icon's own colors
        }
        binding.cardDistance.apply {
            cardTitle.text = "DISTANCE"
            cardUnit.text = "KM"
            cardIcon.setImageResource(R.drawable.ic_location)
            cardIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.accent_red))
        }
        binding.cardSpo2.apply {
            cardTitle.text = "SPO2"
            cardIcon.setImageResource(R.drawable.ic_spo2_styled)
            cardIcon.imageTintList = null // Use icon's own colors
        }
        binding.cardWater.apply {
            cardTitle.text = "INTAKE"
            cardUnit.text = "WATER"
            cardIcon.setImageResource(R.drawable.ic_water)
            cardIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.accent_teal))
        }

        // Apply pulse animation to icons
        val pulseAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.pulse)
        binding.cardHeart.cardIcon.startAnimation(pulseAnimation)
        binding.cardSteps.cardIcon.startAnimation(pulseAnimation)
        binding.cardCalories.cardIcon.startAnimation(pulseAnimation)
        binding.cardDistance.cardIcon.startAnimation(pulseAnimation)
        binding.cardSpo2.cardIcon.startAnimation(pulseAnimation)
        binding.cardWater.cardIcon.startAnimation(pulseAnimation)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.healthData.collect { updateUI() }
                }
                launch {
                    viewModel.dailyData.collect { updateUI() }
                }
                launch {
                    viewModel.user.collect { user ->
                        user?.let {
                            binding.tvGreeting.text = "Hello ${it.name} !"
                            startOnboarding()
                        }
                        updateUI()
                    }
                }
                launch {
                    viewModel.todayTargetSteps.collect { updateUI() }
                }
                launch {
                    viewModel.todayTargetCalories.collect { updateUI() }
                }
                launch {
                    viewModel.todayTargetBurnCalories.collect { updateUI() }
                }
                launch {
                    viewModel.todayTargetDistance.collect { updateUI() }
                }
                launch {
                    viewModel.targetProtein.collect { updateUI() }
                }
                launch {
                    viewModel.targetCarbs.collect { updateUI() }
                }
                launch {
                    viewModel.targetFat.collect { updateUI() }
                }
                launch {
                    viewModel.connectionState.collect { state ->
                        updateConnectionUI(state)
                    }
                }
                launch {
                    viewModel.connectedDeviceName.collect { name ->
                        if (viewModel.connectionState.value == BleManager.ConnectionState.CONNECTED) {
                            binding.statusText.text = name?.uppercase() ?: "CONNECTED"
                        }
                    }
                }
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        val skeleton = binding.root.findViewById<View>(R.id.layout_skeleton)
                        val mainContent = binding.root.findViewById<View>(R.id.main_content)
                        val shimmerContainer = binding.root.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_view_container)

                        skeleton?.visibility = if (isLoading) View.VISIBLE else View.GONE
                        mainContent?.visibility = if (isLoading) View.GONE else View.VISIBLE

                        if (isLoading) shimmerContainer?.startShimmer()
                        else shimmerContainer?.stopShimmer()
                    }
                }
            }
        }
    }

    private fun startOnboarding() {
        if (isOnboardingStarted) return
        
        val user = viewModel.user.value
        if (user == null || user.uid.isEmpty()) return

        val onboardingManager = OnboardingManager(requireContext())
        if (!onboardingManager.shouldShowOnboarding()) return

        // Final safety check: ensure we are currently viewing the Dashboard
        val currentDestId = try { findNavController().currentDestination?.id } catch (e: Exception) { null }
        if (currentDestId != R.id.dashboardFragment) return

        isOnboardingStarted = true

        // Add a small delay to ensure everything is laid out for the spotlight rects
        view?.postDelayed({
            if (!isAdded) {
                isOnboardingStarted = false
                return@postDelayed
            }
            
            // Re-check destination inside delay
            val currentDestIdInside = try { findNavController().currentDestination?.id } catch (e: Exception) { null }
            if (currentDestIdInside != R.id.dashboardFragment) {
                isOnboardingStarted = false
                return@postDelayed
            }

            val root = requireActivity().window.decorView as ViewGroup
            val overlay = OnboardingOverlayView(requireContext())
            root.addView(overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
            
            val steps = listOf(
                OnboardingStep(
                    "Welcome to HealthPilot",
                    "Your comprehensive fitness journey starts here. Track your daily summary and goals at a glance.",
                    binding.tvGreeting,
                    R.drawable.healthpilot_logo,
                    paddingDp = 20f
                ),
                OnboardingStep(
                    "Connect Smart Watch",
                    "Sync your steps, heart rate, SpO2, and more automatically by connecting your watch and after connect try to move for accurate data.",
                    binding.connectionStatusCard,
                    R.drawable.ic_heart,
                    paddingDp = 12f
                ),
                OnboardingStep(
                    "Workouts",
                    "Choose from various workouts and track your real-time statistics with your watch or phone pedometer.",
                    binding.cardStartWorkout,
                    R.drawable.workout,
                    paddingDp = 12f
                ),
                OnboardingStep(
                    "Health & Nutrition",
                    "Set your weight goals (gain/loss), log your meals, and track macros like Protein, Carbs, and Fats to reach your target.",
                    bottomNav?.findViewById(R.id.healthGoalFragment),
                    R.drawable.health_goal,
                    paddingDp = 4f
                ),
                OnboardingStep(
                    "Meal Reminder",
                    "Stay on track by scheduling meal reminders and viewing your daily wellness goals.",
                    binding.btnDrawer,
                    R.drawable.ic_calendar,
                    paddingDp = 8f
                ),
                OnboardingStep(
                    "Daily Wellness",
                    "Monitor your mood, water intake, and daily habits to maintain a healthy lifestyle.",
                    binding.btnDrawer,
                    R.drawable.ic_water,
                    paddingDp = 12f
                ),
                OnboardingStep(
                    "Progress & Reports",
                    "View detailed reports of your activity, workout history, and health achievements over time.",
                    bottomNav?.findViewById(R.id.reportFragment),
                    R.drawable.report,
                    paddingDp = 4f
                ),
                OnboardingStep(
                    "Target & BMI",
                    "Monitor your BMI, set your targeted daily steps and calories, and track your weekly fitness achievements.",
                    bottomNav?.findViewById(R.id.goalFragment),
                    R.drawable.goal,
                    paddingDp = 4f
                ),
                OnboardingStep(
                    "Meet Ved - Your AI Assistant",
                    "Hi! I'm Ved, your personal AI fitness companion. Ask me anything about workouts, nutrition, or health tips anytime!",
                    requireActivity().findViewById(R.id.fab_chat_assistant),
                    R.drawable.ic_chatbot,
                    paddingDp = 8f
                ),
                OnboardingStep(
                    "Personal Profile",
                    "Customize your profile, adjust settings, and manage your connected devices.",
                    bottomNav?.findViewById(R.id.profileFragment),
                    R.drawable.profile,
                    paddingDp = 4f
                ),
                OnboardingStep(
                    "All Set!",
                    "🎉 You're all set! Start your fitness journey and achieve your goals with HealthPilot.",
                    null,
                    R.drawable.home,
                    isFinalStep = true
                )
            )

            overlay.setSteps(steps)
            overlay.setOnFinishListener {
                onboardingManager.setOnboardingShown()
                isOnboardingStarted = false
            }
        }, 500)
    }

    private fun updateUI() {
        val healthData = viewModel.healthData.value
        val dailyData = viewModel.dailyData.value
        val targetCal = viewModel.todayTargetCalories.value
        val targetBurnCal = viewModel.todayTargetBurnCalories.value
        val targetProtein = viewModel.targetProtein.value
        val targetCarbs = viewModel.targetCarbs.value
        val targetFat = viewModel.targetFat.value

        binding.calText.text = "${dailyData.caloriesConsumed}/$targetCal"

        val gainProgress = if (targetCal > 0) (dailyData.caloriesConsumed * 100 / targetCal) else 0
        binding.progressCalories.progress = gainProgress
        binding.progressGain.progress = gainProgress
        binding.tvGainPercent.text = "$gainProgress%"

        val burnProgress = if (targetBurnCal > 0) (healthData.calories * 100 / targetBurnCal) else 0
        binding.progressBurn.progress = burnProgress
        binding.tvBurnPercent.text = "$burnProgress%"

        binding.macroProtein.macroValue.text = "${dailyData.proteinConsumed.toInt()}/$targetProtein g"
        binding.macroCarbs.macroValue.text = "${dailyData.carbsConsumed.toInt()}/$targetCarbs g"
        binding.macroFats.macroValue.text = "${dailyData.fatConsumed.toInt()}/$targetFat g"

        binding.macroProtein.macroProgress.progress = if (targetProtein > 0) (dailyData.proteinConsumed * 100 / targetProtein).toInt() else 0
        binding.macroCarbs.macroProgress.progress = if (targetCarbs > 0) (dailyData.carbsConsumed * 100 / targetCarbs).toInt() else 0
        binding.macroFats.macroProgress.progress = if (targetFat > 0) (dailyData.fatConsumed * 100 / targetFat).toInt() else 0

        binding.cardHeart.cardValue.text = if (healthData.heartRate > 0) healthData.heartRate.toString() else dailyData.heartRate.toString()

        val displaySteps = if (healthData.steps > 0) healthData.steps else dailyData.steps
        binding.cardSteps.cardValue.text = displaySteps.toString()

        // Show Burned Calories from Watch in the grid card
        binding.cardCalories.cardValue.text = healthData.calories.toString()

        binding.cardDistance.cardValue.text = "%.2f".format(healthData.distanceKm)

        binding.cardSpo2.cardValue.text = "${healthData.spo2}%"

        binding.cardWater.cardValue.text = "%.1f".format(dailyData.waterIntakeL)
    }

    private fun updateConnectionUI(state: BleManager.ConnectionState) {
        binding.statusText.text = if (state == BleManager.ConnectionState.CONNECTED) {
            viewModel.connectedDeviceName.value?.uppercase() ?: "CONNECTED"
        } else {
            state.name
        }
        val color = when (state) {
            BleManager.ConnectionState.CONNECTED -> ContextCompat.getColor(requireContext(), R.color.accent_lime)
            BleManager.ConnectionState.CONNECTING -> ContextCompat.getColor(requireContext(), R.color.accent_orange)
            else -> ContextCompat.getColor(requireContext(), R.color.accent_red)
        }
        binding.statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)

        if (state == BleManager.ConnectionState.CONNECTED) {
            binding.connectButton.text = "Disconnect"
            binding.connectButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_red))
            val disconnectAction = View.OnClickListener { viewModel.disconnectDevice() }
            binding.connectButton.setOnClickListener(disconnectAction)
            binding.connectionStatusCard.setOnClickListener(disconnectAction)
        } else {
            binding.connectButton.text = "Connect"
            binding.connectButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_lime))
            val connectAction = View.OnClickListener { showDeviceListBottomSheet() }
            binding.connectButton.setOnClickListener(connectAction)
            binding.connectionStatusCard.setOnClickListener(connectAction)
        }
    }

    private fun showConnectionHelpDialog() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val isBluetoothEnabled = bluetoothManager.adapter?.isEnabled == true
        
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }

        val message = StringBuilder()
        message.append("HARDWARE STATUS:\n")
        message.append("• Bluetooth: ${if (isBluetoothEnabled) "✅ ENABLED" else "❌ DISABLED"}\n")
        message.append("• Location: ${if (isLocationEnabled) "✅ ENABLED" else "❌ DISABLED"}\n\n")
        
        if (!isBluetoothEnabled || !isLocationEnabled) {
            message.append("⚠️ ACTION REQUIRED:\n")
            if (!isBluetoothEnabled) message.append("- Please turn ON Bluetooth in System Settings.\n")
            if (!isLocationEnabled) message.append("- Please turn ON Location/GPS in System Settings.\n")
            message.append("\n")
        }
        
        message.append("TROUBLESHOOTING TIPS:\n")
        message.append("• Ensure your watch is not currently paired with another smartphone.\n")
        message.append("• Keep the watch within 1 meter of your phone while scanning.\n")
        message.append("• Check if the watch is in 'Pairing Mode'.\n")
        message.append("• Try toggling Bluetooth OFF and ON again on your phone.")

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Connection Support")
            .setMessage(message.toString())
            .setPositiveButton("GOT IT", null)
            .show()
    }

    private fun navigateToReport(metric: String) {
        val bundle = Bundle().apply {
            putString("metric", metric)
        }
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(R.id.dashboardFragment, inclusive = false, saveState = true)
            .build()

        findNavController().navigate(R.id.reportFragment, bundle, navOptions)
    }

    private fun showWorkoutListBottomSheet() {
        val dialog = BottomSheetDialog(requireContext(), R.style.CustomDialogTheme)
        val bottomSheetBinding = DialogWorkoutListBinding.inflate(layoutInflater)
        dialog.setContentView(bottomSheetBinding.root)

        val adapter = WorkoutAdapter { workoutName ->
            dialog.dismiss()
            val bundle = Bundle().apply { putString("workoutName", workoutName) }
            findNavController().navigate(R.id.action_dashboardFragment_to_workoutIntroFragment, bundle)
        }
        bottomSheetBinding.rvWorkouts.adapter = adapter

        bottomSheetBinding.ivHistory.setOnClickListener {
            dialog.dismiss()
            findNavController().navigate(R.id.workoutHistoryFragment)
        }

        dialog.show()
    }

    private fun showDeviceListBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val bottomSheetBinding = BottomSheetDeviceListBinding.inflate(layoutInflater)
        dialog.setContentView(bottomSheetBinding.root)

        // Radar animations
        fun startRadarAnimation(view: View, delay: Long) {
            val scaleX = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.5f)
            val scaleY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.5f)
            val alpha = android.animation.ObjectAnimator.ofFloat(view, "alpha", 0.8f, 0f)

            scaleX.repeatCount = android.animation.ValueAnimator.INFINITE
            scaleY.repeatCount = android.animation.ValueAnimator.INFINITE
            alpha.repeatCount = android.animation.ValueAnimator.INFINITE

            android.animation.AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 2000
                startDelay = delay
                start()
            }
        }

        startRadarAnimation(bottomSheetBinding.radarCircle1, 0)
        startRadarAnimation(bottomSheetBinding.radarCircle2, 600)
        startRadarAnimation(bottomSheetBinding.radarCircle3, 1200)

        // Success animations setup
        fun startSuccessAnimation() {
            startRadarAnimation(bottomSheetBinding.successCircle1, 0)
            startRadarAnimation(bottomSheetBinding.successCircle2, 400)
            startRadarAnimation(bottomSheetBinding.successCircle3, 800)
            bottomSheetBinding.tvMarqueeFooter.isSelected = true // Start marquee
            
            // Pop in checkmark
            bottomSheetBinding.ivSuccessCheck.scaleX = 0f
            bottomSheetBinding.ivSuccessCheck.scaleY = 0f
            bottomSheetBinding.ivSuccessCheck.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(500)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
        }

        val adapter = DeviceAdapter { address ->
            viewModel.connectDevice(address)
            // We don't dismiss immediately, wait for connected state to show success UI
        }
        bottomSheetBinding.rvDevices.adapter = adapter

        bottomSheetBinding.btnRefreshScan.setOnClickListener {
            viewModel.stopScan()
            viewModel.startScan()
        }

        bottomSheetBinding.btnHelp.setOnClickListener {
            showConnectionHelpDialog()
        }

        bottomSheetBinding.btnStartWorkout.setOnClickListener { dialog.dismiss() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.foundDevices.collect { devices ->
                        adapter.submitList(devices)
                        if (viewModel.connectionState.value != BleManager.ConnectionState.CONNECTING && 
                            viewModel.connectionState.value != BleManager.ConnectionState.CONNECTED) {
                            bottomSheetBinding.scanProgress.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                            bottomSheetBinding.scanStatusText.text = if (devices.isEmpty()) "SEARCHING FOR DEVICES..." else "DISCOVERED ${devices.size} DEVICES"
                        }
                    }
                }
                launch {
                    viewModel.connectionState.collect { state ->
                        when (state) {
                            BleManager.ConnectionState.CONNECTED -> {
                                bottomSheetBinding.viewFlipper.displayedChild = 1 // Show success layout
                                bottomSheetBinding.scrollView.scrollTo(0, 0) // Scroll to top
                                bottomSheetBinding.tvSuccessDeviceName.text = viewModel.connectedDeviceName.value?.uppercase() ?: "FORGE PULSE WATCH"
                                startSuccessAnimation()
                                
                                // Auto dismiss after 10 seconds
                                launch {
                                    kotlinx.coroutines.delay(10000)
                                    if (dialog.isShowing) dialog.dismiss()
                                }
                            }
                            BleManager.ConnectionState.CONNECTING -> {
                                bottomSheetBinding.scanStatusText.text = "CONNECTING..."
                                bottomSheetBinding.scanProgress.visibility = View.VISIBLE
                            }
                            else -> {
                                bottomSheetBinding.viewFlipper.displayedChild = 0 // Show scan layout
                                val devices = viewModel.foundDevices.value
                                bottomSheetBinding.scanStatusText.text = if (devices.isEmpty()) "SEARCHING FOR DEVICES..." else "DISCOVERED ${devices.size} DEVICES"
                                bottomSheetBinding.scanProgress.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                            }
                        }
                    }
                }
                launch {
                    viewModel.healthData.collect { data ->
                        if (bottomSheetBinding.viewFlipper.displayedChild == 1) {
                            // Update Battery
                            val battery = data.batteryLevel
                            if (battery > 0) {
                                bottomSheetBinding.tvSuccessBattery.text = "$battery%"
                                bottomSheetBinding.tvBatteryStatus.text = when {
                                    battery >= 80 -> "EXC"
                                    battery >= 50 -> "GOOD"
                                    battery >= 20 -> "FAIR"
                                    else -> "LOW"
                                }
                                bottomSheetBinding.tvBatteryStatus.setTextColor(ContextCompat.getColor(requireContext(), 
                                    if (battery >= 20) R.color.accent_lime else R.color.accent_red))
                            } else {
                                // Default placeholders while waiting for first data packet
                                bottomSheetBinding.tvSuccessBattery.text = "--%"
                                bottomSheetBinding.tvBatteryStatus.text = "WAIT"
                                bottomSheetBinding.tvBatteryStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray))
                            }

                            // Update Signal Strength (RSSI)
                            val rssi = data.rssi
                            val bars = when {
                                rssi >= -60 -> 4 // High
                                rssi >= -75 -> 3 // Mid-High
                                rssi >= -90 -> 2 // Mid-Low
                                else -> 1 // Low
                            }
                            
                            val activeColor = ContextCompat.getColor(requireContext(), R.color.accent_lime)
                            val inactiveColor = Color.parseColor("#33FFFFFF")
                            
                            bottomSheetBinding.signalBar1.setBackgroundColor(if (bars >= 1) activeColor else inactiveColor)
                            bottomSheetBinding.signalBar2.setBackgroundColor(if (bars >= 2) activeColor else inactiveColor)
                            bottomSheetBinding.signalBar3.setBackgroundColor(if (bars >= 3) activeColor else inactiveColor)
                            bottomSheetBinding.signalBar4.setBackgroundColor(if (bars >= 4) activeColor else inactiveColor)
                            
                            bottomSheetBinding.tvSignalText.text = when (bars) {
                                4 -> "HIGH"
                                3 -> "MID"
                                2 -> "FAIR"
                                else -> "LOW"
                            }
                            bottomSheetBinding.tvSignalText.setTextColor(if (bars >= 2) activeColor else ContextCompat.getColor(requireContext(), R.color.accent_red))
                        }
                    }
                }
            }
        }

        viewModel.startScan()
        dialog.setOnDismissListener { viewModel.stopScan() }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
