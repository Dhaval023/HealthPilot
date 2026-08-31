package com.example.myfitnessapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.myfitnessapp.databinding.ActivityMainBinding
import com.example.myfitnessapp.utils.NetworkObserver
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.myfitnessapp.utils.PermissionManager
import com.example.myfitnessapp.utils.BiometricHelper
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    private lateinit var networkObserver: NetworkObserver
    private lateinit var biometricHelper: BiometricHelper
    private var snackbar: Snackbar? = null

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
        
        binding.bottomNavigation.setupWithNavController(navController)
        binding.navView.setupWithNavController(navController)

        binding.fabChatAssistant.setOnClickListener {
            navController.navigate(R.id.chatFragment)
        }

        // Handle Logout
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.logout -> {
                    androidx.appcompat.app.AlertDialog.Builder(this)
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
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> {
                    val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(menuItem, navController)
                    if (handled) {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                    handled
                }
            }
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            val navOptions = androidx.navigation.NavOptions.Builder()
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
                        // On error (like cancel), we can either exit or sign out. 
                        // Let's sign out to be safe or just finish the activity.
                        finish()
                    },
                    onFailed = {
                        // Failed attempt, usually can try again or it shows error eventually
                    }
                )
            } else {
                checkUserInFirestore(currentUser, navController)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.loginFragment || 
                destination.id == R.id.signupStep1Fragment || 
                destination.id == R.id.signupStep2Fragment ||
                destination.id == R.id.signupStep3Fragment ||
                destination.id == R.id.addFoodFragment ||
                destination.id == R.id.manualFoodFragment ||
                destination.id == R.id.chatFragment ||
                destination.id == R.id.remindersFragment ||
                destination.id == R.id.forgotPasswordFragment ||
                destination.id == R.id.dailyWellnessFragment ||
                destination.id == R.id.workoutIntroFragment ||
                destination.id == R.id.workoutTrackingFragment ||
                destination.id == R.id.workoutHistoryFragment ||
                destination.id == R.id.workoutPlanFragment ||
                destination.id == R.id.workoutDetailFragment ||
                destination.id == R.id.workoutShareFragment) {
                binding.bottomNavigation.visibility = View.GONE
                binding.fabChatAssistant.visibility = View.GONE
                binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                binding.bottomNavigation.visibility = View.VISIBLE
                binding.fabChatAssistant.visibility = View.VISIBLE
                binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
            }
        }

        permissionManager.checkAndRequestPermissions {
        }
    }

    private fun checkUserInFirestore(currentUser: com.google.firebase.auth.FirebaseUser, navController: androidx.navigation.NavController) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    // User exists in Auth but not in Firestore, meaning signup is incomplete
                    val currentDest = navController.currentDestination?.id
                    if (currentDest != R.id.signupStep2Fragment && currentDest != R.id.signupStep3Fragment) {
                        val navOptions = androidx.navigation.NavOptions.Builder()
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
                "No internet connection. Please check your settings.",
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
                com.example.myfitnessapp.ble.BleManager.ConnectionState.CONNECTED
    }
}
