package com.example.myfitnessapp

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.example.myfitnessapp.ble.BleManager
import com.example.myfitnessapp.databinding.ActivityMainBinding
import com.example.myfitnessapp.music.MusicViewModel
import com.example.myfitnessapp.utils.BiometricHelper
import com.example.myfitnessapp.utils.NetworkObserver
import com.example.myfitnessapp.utils.PermissionManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    private lateinit var networkObserver: NetworkObserver
    private lateinit var biometricHelper: BiometricHelper
    private val musicViewModel: MusicViewModel by viewModels()
    private var snackbar: Snackbar? = null

    private var floatingMusicView: View? = null
    private var isExpanded = false

    val globalYouTubeContainer: FrameLayout get() = binding.globalYouTubeContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = PermissionManager(this)
        biometricHelper = BiometricHelper(this)
        networkObserver = (application as HealthPilot).networkObserver
        observeNetwork()
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        binding.bottomNavigation.itemIconTintList = null
        binding.bottomNavigation.setupWithNavController(navController)
        binding.navView.setupWithNavController(navController)

        binding.fabChatAssistant.setOnClickListener {
            navController.navigate(R.id.chatFragment)
        }

        setupFloatingMusicController(navController)

        // Handle Logout and Drawer Navigation
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.logout -> {
                    AlertDialog.Builder(this)
                        .setTitle("Logout")
                        .setMessage("Are you sure you want to logout?")
                        .setPositiveButton("Logout") { _, _ ->
                            BiometricHelper.setBiometricEnabled(this, false)
                            FirebaseAuth.getInstance().signOut()
                            // Re-create the NavHostFragment to clear all ViewModels and states
                            val intent = Intent(this, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            startActivity(intent)
                            finish()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
                R.id.startWorkout -> {
                    if (navController.currentDestination?.id == R.id.dashboardFragment) {
                        navController.currentBackStackEntry?.savedStateHandle?.set("show_workout_list", true)
                    } else {
                        navController.navigate(R.id.dashboardFragment)
                        navController.currentBackStackEntry?.savedStateHandle?.set("show_workout_list", true)
                    }
                    true
                }
                else -> {
                    val handled = NavigationUI.onNavDestinationSelected(menuItem, navController)
                    if (!handled) {
                        try {
                            navController.navigate(menuItem.itemId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    true
                }
            }
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.dashboardFragment, true)
                .build()
            navController.navigate(R.id.loginFragment, null, navOptions)
        } else {
            if (BiometricHelper.isBiometricEnabled(this)) {
                // Hide content while authenticating
                binding.root.visibility = View.INVISIBLE
                biometricHelper.showBiometricPrompt(
                    onSuccess = {
                        binding.root.visibility = View.VISIBLE
                        checkUserInFirestore(currentUser, navController)
                    },
                    onError = { _, _ ->
                        finish()
                    },
                    onFailed = {
                    }
                )
            } else {
                checkUserInFirestore(currentUser, navController)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateFloatingMusicVisibility(destination.id)

            if (destination.id == R.id.loginFragment || 
                destination.id == R.id.signupStep1Fragment || 
                destination.id == R.id.signupStep2Fragment ||
                destination.id == R.id.signupStep3Fragment ||
                destination.id == R.id.addFoodFragment ||
                destination.id == R.id.manualFoodFragment ||
                destination.id == R.id.chatFragment ||
                destination.id == R.id.forgotPasswordFragment ||
                destination.id == R.id.workoutIntroFragment ||
                destination.id == R.id.workoutTrackingFragment ||
                destination.id == R.id.workoutHistoryFragment ||
                destination.id == R.id.workoutPlanFragment ||
                destination.id == R.id.workoutDetailFragment ||
                destination.id == R.id.workoutShareFragment ||
                destination.id == R.id.medicalAssessmentFragment ||
                destination.id == R.id.medicalResultFragment) {
                binding.bottomNavigation.visibility = View.GONE
                binding.fabChatAssistant.visibility = View.GONE
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                binding.bottomNavigation.visibility = View.VISIBLE
                binding.fabChatAssistant.visibility = View.VISIBLE
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }
        }

        permissionManager.checkAndRequestPermissions {
        }
    }

    private fun setupFloatingMusicController(navController: NavController) {
        val floatingView = binding.root.findViewById<View>(R.id.floating_music_view) ?: return

        val cardFloatIcon = floatingView.findViewById<View>(R.id.cardFloatIcon)
        val panelExpandedControls = floatingView.findViewById<View>(R.id.panelExpandedControls)
        val btnPrev = floatingView.findViewById<ImageView>(R.id.btnFloatPrevious)
        val btnPlayPause = floatingView.findViewById<ImageView>(R.id.btnFloatPlayPause)
        val btnNext = floatingView.findViewById<ImageView>(R.id.btnFloatNext)

        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f

        cardFloatIcon.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = floatingView.translationX - event.rawX
                    dY = floatingView.translationY - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    cardFloatIcon.alpha = 1.0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    floatingView.translationX = event.rawX + dX
                    floatingView.translationY = event.rawY + dY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val distance = hypot((event.rawX - startX).toDouble(), (event.rawY - startY).toDouble()).toFloat()
                    if (distance < 15f) {
                        // Clicked -> toggle expand / collapse
                        isExpanded = !isExpanded
                        if (isExpanded) {
                            panelExpandedControls.visibility = View.VISIBLE
                            panelExpandedControls.alpha = 0f
                            panelExpandedControls.scaleX = 0.8f
                            panelExpandedControls.scaleY = 0.8f
                            panelExpandedControls.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .start()
                        } else {
                            panelExpandedControls.animate()
                                .alpha(0f)
                                .scaleX(0.8f)
                                .scaleY(0.8f)
                                .setDuration(200)
                                .withEndAction { panelExpandedControls.visibility = View.GONE }
                                .start()
                        }
                    } else {
                        view.performClick()
                    }
                    cardFloatIcon.alpha = 0.5f
                    true
                }
                else -> false
            }
        }

        btnPrev.setOnClickListener { musicViewModel.previousSong() }
        btnNext.setOnClickListener { musicViewModel.nextSong() }
        btnPlayPause.setOnClickListener { musicViewModel.togglePlayPause() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    musicViewModel.isPlaying.collectLatest { isPlaying ->
                        if (isPlaying) {
                            btnPlayPause.setImageResource(R.drawable.ic_pause_circle)
                        } else {
                            btnPlayPause.setImageResource(R.drawable.ic_play)
                        }
                    }
                }
                launch {
                    musicViewModel.currentSong.collectLatest {
                        val currentDest = navController.currentDestination?.id ?: 0
                        updateFloatingMusicVisibility(currentDest)
                    }
                }
            }
        }

        floatingMusicView = floatingView
    }

    private fun updateFloatingMusicVisibility(destinationId: Int) {
        val hasSong = musicViewModel.currentSong.value != null
        val isMusicPage = destinationId == R.id.musicFragment
        val isAuthPage = destinationId == R.id.loginFragment || 
            destinationId == R.id.signupStep1Fragment || 
            destinationId == R.id.signupStep2Fragment ||
            destinationId == R.id.signupStep3Fragment

        if (hasSong && !isMusicPage && !isAuthPage) {
            floatingMusicView?.visibility = View.VISIBLE
            floatingMusicView?.bringToFront()
        } else {
            floatingMusicView?.visibility = View.GONE
            collapseFloatingMusicView()
        }
    }

    private fun collapseFloatingMusicView() {
        if (isExpanded && floatingMusicView != null) {
            val panelExpandedControls = floatingMusicView?.findViewById<View>(R.id.panelExpandedControls)
            panelExpandedControls?.animate()
                ?.alpha(0f)
                ?.scaleX(0.8f)
                ?.scaleY(0.8f)
                ?.setDuration(150)
                ?.withEndAction { panelExpandedControls.visibility = View.GONE }
                ?.start()
            isExpanded = false
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN && isExpanded && floatingMusicView != null) {
            val rect = Rect()
            floatingMusicView?.getGlobalVisibleRect(rect)
            if (!rect.contains(ev.x.toInt(), ev.y.toInt())) {
                collapseFloatingMusicView()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun checkUserInFirestore(currentUser: FirebaseUser, navController: NavController) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    val currentDest = navController.currentDestination?.id
                    if (currentDest != R.id.signupStep2Fragment && currentDest != R.id.signupStep3Fragment) {
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.dashboardFragment, true)
                            .build()
                        navController.navigate(R.id.signupStep2Fragment, null, navOptions)
                    }
                }
            }
    }

    private fun observeNetwork() {
        lifecycleScope.launch {
            networkObserver.observe.collectLatest { status ->
                when (status) {
                    NetworkObserver.Status.Available -> {
                        snackbar?.dismiss()
                        snackbar = null
                    }
                    NetworkObserver.Status.Unavailable, NetworkObserver.Status.Lost -> {
                        showNoInternetSnackbar()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showNoInternetSnackbar() {
        if (snackbar == null) {
            snackbar = Snackbar.make(
                binding.root,
                getString(R.string.no_internet_error),
                Snackbar.LENGTH_INDEFINITE
            ).apply {
                setAction("OK") { dismiss() }
                show()
            }
        }
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun isDeviceConnected(): Boolean {
        return (application as HealthPilot).repository.connectionState.value == 
                BleManager.ConnectionState.CONNECTED
    }
}
