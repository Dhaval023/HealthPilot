package com.example.myfitnessapp.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.R
import com.example.myfitnessapp.ble.BleManager
import com.example.myfitnessapp.dashboard.DeviceAdapter
import com.example.myfitnessapp.databinding.BottomSheetDeviceListBinding
import com.example.myfitnessapp.databinding.FragmentWorkoutTrackingBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class WorkoutTrackingFragment : Fragment() {

    private var _binding: FragmentWorkoutTrackingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WorkoutViewModel by viewModels()

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            checkWatchConnectionAndStart()
        } else {
            Toast.makeText(requireContext(), "Some permissions denied. Tracking may be limited.", Toast.LENGTH_SHORT).show()
            checkWatchConnectionAndStart()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutTrackingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val workoutName = arguments?.getString("workoutName") ?: "Workout"
        
        val context = requireContext()
        val nameResId = context.resources.getIdentifier(workoutName.lowercase().replace(" ", "_").replace("-", "_"), "string", context.packageName)
        binding.tvToolbarTitle.text = if (nameResId != 0) getString(nameResId) else workoutName

        // Configure stats visibility based on workout type
        configureStatsVisibility(workoutName)

        // Set icon based on workout
        val iconRes = when (workoutName.lowercase()) {
            "walking" -> R.drawable.ic_walk
            "running" -> R.drawable.running
            "cycling" -> R.drawable.cycling
            "yoga" -> R.drawable.yoga
            "boxing" -> R.drawable.boxing
            "swimming" -> R.drawable.swimming
            "badminton" -> R.drawable.badminton
            else -> R.drawable.ic_walk
        }
        binding.ivWorkoutIcon.setImageResource(iconRes)

        checkPermissionsAndStart()

        setupObservers()
        setupListeners()
        setupLottie(workoutName)

        binding.toolbar.setNavigationOnClickListener {
            handleBackNavigation()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            checkWatchConnectionAndStart()
        }
    }

    private fun checkWatchConnectionAndStart() {
        if (viewModel.connectionState.value == BleManager.ConnectionState.CONNECTED) {
            startTracking()
        } else {
            showWatchConnectPrompt()
        }
    }

    private fun showWatchConnectPrompt() {
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Connect Watch")
            .setMessage("For accurate step tracking and health monitoring, please connect your fitness watch.")
            .setPositiveButton("Connect") { _, _ ->
                showDeviceListBottomSheet()
            }
            .setNegativeButton("Skip") { _, _ ->
                startTracking()
            }
            .setCancelable(false)
            .show()
    }

    private fun configureStatsVisibility(workoutName: String) {
        when (workoutName.lowercase()) {
            "yoga", "swimming", "boxing", "wrestling" -> {
                binding.cardSteps.visibility = View.GONE
                binding.cardDistance.visibility = View.GONE
            }
            "cycling" -> {
                binding.cardSteps.visibility = View.GONE
                // Distance is relevant for cycling
            }
            else -> {
                // Default: show everything
                binding.cardSteps.visibility = View.VISIBLE
                binding.cardDistance.visibility = View.VISIBLE
            }
        }
    }

    private fun startTracking() {
        val workoutName = arguments?.getString("workoutName") ?: "Workout"
        if (viewModel.workoutType.isEmpty()) {
            viewModel.startWorkout(workoutName)
        }
    }

    private fun handleBackNavigation() {
        showSaveDialog()
    }

    private fun showSaveDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Save Workout?")
            .setMessage("Do you want to save this workout record before exiting?")
            .setPositiveButton("Save") { _, _ ->
                viewModel.finishWorkout(true) { id ->
                    if (id != null) {
                        val bundle = Bundle().apply { 
                            putString("workoutId", id)
                            putString("workoutType", viewModel.workoutType)
                        }
                        findNavController().navigate(R.id.action_workoutTrackingFragment_to_workoutDetailFragment, bundle)
                    } else {
                        Toast.makeText(requireContext(), "Failed to save workout. Please check your internet connection.", Toast.LENGTH_LONG).show()
                        exitTracking()
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                viewModel.finishWorkout(false) {
                    exitTracking()
                }
            }
            .setCancelable(true)
            .show()
    }

    private fun exitTracking() {
        findNavController().previousBackStackEntry?.savedStateHandle?.set("show_workout_list", true)
        findNavController().popBackStack()
    }

    private fun setupObservers() {
        val durationMinutes = arguments?.getInt("durationMinutes", 0) ?: 0
        val targetSeconds = durationMinutes * 60L

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.connectionState.collectLatest { state ->
                        updateConnectionUI(state)
                    }
                }
                launch {
                    viewModel.connectedDeviceName.collectLatest { name ->
                        binding.tvDeviceName.text = name ?: "No Watch Connected"
                    }
                }
            }
        }
        
        viewModel.secondsElapsed.observe(viewLifecycleOwner) { seconds ->
            if (targetSeconds > 0 && seconds >= targetSeconds) {
                autoFinishWorkout()
            }
        }

        viewModel.timerText.observe(viewLifecycleOwner) { 
            binding.tvTimer.text = it 
            // Update arc progress based on target duration if available
            if (targetSeconds > 0) {
                binding.arcProgress.progress = ((viewModel.secondsElapsed.value ?: 0L) * 100 / targetSeconds).toInt().coerceAtMost(100)
            } else {
                // Original looping behavior for others
                val parts = it.split(":")
                if (parts.size == 3) {
                    val seconds = parts[2].toInt()
                    binding.arcProgress.progress = (seconds % 60) * 100 / 60
                }
            }
        }
        viewModel.steps.observe(viewLifecycleOwner) {
            binding.tvSteps.text = it.toString()
        }
        viewModel.distance.observe(viewLifecycleOwner) { 
            binding.tvDistanceValue.text = String.format(Locale.getDefault(), "%.2f", it) 
        }
        viewModel.calories.observe(viewLifecycleOwner) { 
            binding.tvCaloriesValue.text = String.format(Locale.getDefault(), "%.0f", it) 
        }
        viewModel.heartRate.observe(viewLifecycleOwner) { hr ->
            binding.tvHeartRateValue.text = hr?.toString() ?: "--"
        }
        viewModel.isTracking.observe(viewLifecycleOwner) { isTracking ->
            binding.tvPause.text = if (isTracking) "Pause" else "Resume"
            binding.ivPause.setImageResource(if (isTracking) R.drawable.ic_pause_circle else R.drawable.ic_play)
            if (isTracking) {
                binding.trackingLottie.resumeAnimation()
            } else {
                binding.trackingLottie.pauseAnimation()
            }
        }
    }

    private fun autoFinishWorkout() {
        if (!isAdded) return
        
        // Prevent multiple calls
        if (viewModel.isTracking.value == false) return

        viewModel.pauseWorkout() // Pause the timer first

        val workoutName = arguments?.getString("workoutName") ?: "Workout"
        
        MaterialAlertDialogBuilder(requireContext(), R.style.CustomDialogTheme)
            .setTitle("Workout Complete!")
            .setMessage("Great job! You've successfully completed your $workoutName session. Your progress has been saved.")
            .setCancelable(false)
            .setPositiveButton("Awesome") { _, _ ->
                viewModel.finishWorkout(true) { id ->
                    if (isAdded) {
                        exitTracking()
                    }
                }
            }
            .show()
    }

    private fun updateConnectionUI(state: BleManager.ConnectionState) {
        when (state) {
            BleManager.ConnectionState.CONNECTED -> {
                binding.tvConnectionStatus.text = "Connected"
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_green))
                binding.ivDeviceIcon.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent_green))
                binding.btnConnectDevice.visibility = View.GONE
            }
            BleManager.ConnectionState.CONNECTING -> {
                binding.tvConnectionStatus.text = "Connecting..."
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_orange))
                binding.btnConnectDevice.visibility = View.GONE
            }
            else -> {
                binding.tvConnectionStatus.text = "Disconnected"
                binding.tvConnectionStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray))
                binding.ivDeviceIcon.imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_gray))
                binding.btnConnectDevice.visibility = View.VISIBLE
            }
        }
    }

    private fun showDeviceListBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val bottomSheetBinding = BottomSheetDeviceListBinding.inflate(layoutInflater)
        dialog.setContentView(bottomSheetBinding.root)

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

        fun startSuccessAnimation() {
            startRadarAnimation(bottomSheetBinding.successCircle1, 0)
            startRadarAnimation(bottomSheetBinding.successCircle2, 400)
            startRadarAnimation(bottomSheetBinding.successCircle3, 800)
            bottomSheetBinding.tvMarqueeFooter.isSelected = true
            
            bottomSheetBinding.ivSuccessCheck.scaleX = 0f
            bottomSheetBinding.ivSuccessCheck.scaleY = 0f
            bottomSheetBinding.ivSuccessCheck.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(500)
                .setInterpolator(android.view.animation.OvershootInterpolator())
                .start()
        }

        startRadarAnimation(bottomSheetBinding.radarCircle1, 0)
        startRadarAnimation(bottomSheetBinding.radarCircle2, 600)
        startRadarAnimation(bottomSheetBinding.radarCircle3, 1200)

        val adapter = DeviceAdapter { address ->
            viewModel.connectToDevice(address)
        }
        bottomSheetBinding.rvDevices.adapter = adapter

        bottomSheetBinding.btnRefreshScan.setOnClickListener {
            viewModel.stopScan()
            viewModel.startScan()
        }

        bottomSheetBinding.btnStartWorkout.setOnClickListener { 
            dialog.dismiss()
            if (viewModel.isTracking.value != true) {
                startTracking()
            }
        }

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
                                bottomSheetBinding.viewFlipper.displayedChild = 1
                                bottomSheetBinding.scrollView.scrollTo(0, 0)
                                bottomSheetBinding.tvSuccessDeviceName.text = viewModel.connectedDeviceName.value?.uppercase() ?: "WATCH CONNECTED"
                                bottomSheetBinding.btnStartWorkout.text = "START WORKOUT"
                                startSuccessAnimation()
                            }
                            BleManager.ConnectionState.CONNECTING -> {
                                bottomSheetBinding.scanStatusText.text = "CONNECTING..."
                                bottomSheetBinding.scanProgress.visibility = View.VISIBLE
                            }
                            else -> {
                                bottomSheetBinding.viewFlipper.displayedChild = 0
                                bottomSheetBinding.btnStartWorkout.text = "SKIP AND START"
                            }
                        }
                    }
                }
            }
        }

        viewModel.startScan()
        dialog.setOnDismissListener { viewModel.stopScan() }
        dialog.show()
    }

    private fun setupListeners() {
        binding.btnPause.setOnClickListener {
            if (viewModel.isTracking.value == true) {
                viewModel.pauseWorkout()
            } else {
                viewModel.resumeWorkout()
            }
        }

        binding.btnConnectDevice.setOnClickListener {
            showDeviceListBottomSheet()
        }

        binding.btnFinish.setOnClickListener {
            showSaveDialog()
        }

        binding.ivHistory.setOnClickListener {
            findNavController().navigate(R.id.action_workoutTrackingFragment_to_workoutHistoryFragment)
        }

        binding.ivShare.setOnClickListener {
            val bundle = Bundle().apply {
                putString("type", viewModel.workoutType)
                putInt("steps", viewModel.steps.value ?: 0)
                putFloat("distance", (viewModel.distance.value ?: 0.0).toFloat())
                putFloat("calories", (viewModel.calories.value ?: 0.0).toFloat())
                putLong("duration", (viewModel.secondsElapsed.value ?: 0L) * 1000)
                putInt("heartRate", viewModel.heartRate.value ?: 80)
                putString("workoutId", "") // Indicating live/temp
                putString("workoutType", viewModel.workoutType)
            }
            findNavController().navigate(R.id.action_workoutTrackingFragment_to_workoutShareFragment, bundle)
        }
    }

    private fun setupLottie(workoutName: String) {
        val normalizedName = workoutName.lowercase().replace(" ", "_").replace("-", "_")
        
        var resId = resources.getIdentifier("${normalizedName}_tracking", "raw", requireContext().packageName)
        if (resId == 0) {
            resId = resources.getIdentifier(normalizedName, "raw", requireContext().packageName)
        }
        if (resId == 0) {
            resId = resources.getIdentifier("${normalizedName}_intro", "raw", requireContext().packageName)
        }
        if (resId == 0) {
            resId = resources.getIdentifier("generic_tracking", "raw", requireContext().packageName)
        }

        if (resId != 0) {
            binding.trackingLottie.setAnimation(resId)
            
            if (workoutName.lowercase().contains("aerobics")) {
                val largeSize = (180 * resources.displayMetrics.density).toInt()
                binding.trackingLottie.layoutParams.width = largeSize
                binding.trackingLottie.layoutParams.height = largeSize
                binding.trackingLottie.requestLayout()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
