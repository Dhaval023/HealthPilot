package com.example.myfitnessapp.auth

import android.animation.ObjectAnimator
import android.view.animation.LinearInterpolator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.example.myfitnessapp.utils.BiometricHelper
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        setupGoogleSignIn()
        setupFacebookSignIn()
        return binding.root
    }

    private fun setupFacebookSignIn() {
        callbackManager = CallbackManager.Factory.create()
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                val credential = FacebookAuthProvider.getCredential(result.accessToken.token)
                viewModel.signInWithSocial(credential)
            }

            override fun onCancel() {
                Log.d("LoginFragment", "Facebook sign in cancelled")
            }

            override fun onError(error: FacebookException) {
                Log.e("LoginFragment", "Facebook sign in error", error)
                showErrorDialog("Facebook sign in failed: ${error.message}")
            }
        })
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
    }

    private val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("LoginFragment", "Google Sign-In result code: ${result.resultCode}")
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                Log.d("LoginFragment", "Google Sign-In success: ${account.email}")
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                viewModel.signInWithSocial(credential)
            } catch (e: ApiException) {
                Log.e("LoginFragment", "Google sign in failed", e)
                showErrorDialog("Google sign in failed: ${e.message}")
            }
        } else {
            Log.w("LoginFragment", "Google Sign-In cancelled or failed with result code: ${result.resultCode}")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Reset state when entering login screen to clear previous results/errors
        viewModel.resetState()

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                viewModel.login(email, password)
            } else {
                Toast.makeText(context, getString(R.string.please_fill_fields), Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvSignup.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_signupStep1Fragment)
        }

        startSignUpAnimation()

        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_forgotPasswordFragment)
        }

        binding.btnGoogle.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.btnFacebook.setOnClickListener {
            showDevelopmentModeDialog("Facebook")
        }

        binding.btnLinkedin.setOnClickListener {
            showDevelopmentModeDialog("LinkedIn")
        }

        binding.btnX.setOnClickListener {
            startTwitterSignIn()
        }

        observeViewModel()
    }

    private fun startSignUpAnimation() {
        ObjectAnimator.ofFloat(binding.signupBorderAnim, "rotation", 0f, 360f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun showDevelopmentModeDialog(provider: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Development Mode")
            .setMessage("$provider login is currently in Development mode. Please try another login method.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun startFacebookSignIn() {
        LoginManager.getInstance().logInWithReadPermissions(this, callbackManager, listOf("email", "public_profile"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }

    private fun startTwitterSignIn() {
        val provider = OAuthProvider.newBuilder("twitter.com")
        val auth = FirebaseAuth.getInstance()

        // Check if there's a pending result (e.g. if the activity was destroyed during flow)
        val pendingResultTask = auth.pendingAuthResult
        if (pendingResultTask != null) {
            pendingResultTask
                .addOnSuccessListener { authResult ->
                    authResult.credential?.let { viewModel.signInWithSocial(it) }
                }
                .addOnFailureListener { e ->
                    showErrorDialog("Twitter sign in failed: ${e.message}")
                }
        } else {
            auth.startActivityForSignInWithProvider(requireActivity(), provider.build())
                .addOnSuccessListener { authResult ->
                    authResult.credential?.let { viewModel.signInWithSocial(it) }
                }
                .addOnFailureListener { e ->
                    Log.e("LoginFragment", "Twitter sign in failed", e)
                    showErrorDialog("Twitter sign in failed: ${e.message}")
                }
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.login_error_title))
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthViewModel.AuthState.Loading -> {
                        setLoadingState(true)
                    }
                    is AuthViewModel.AuthState.Success -> {
                        setLoadingState(false)
                        checkBiometricAndNavigate()
                    }
                    is AuthViewModel.AuthState.NewSocialUser -> {
                        setLoadingState(false)
                        findNavController().navigate(R.id.action_loginFragment_to_signupStep2Fragment)
                    }
                    is AuthViewModel.AuthState.Error -> {
                        setLoadingState(false)
                        showErrorDialog(state.message)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun checkBiometricAndNavigate() {
        val biometricHelper = BiometricHelper(requireActivity())
        if (biometricHelper.isBiometricAvailable() && !BiometricHelper.isBiometricEnabled(requireContext())) {
            AlertDialog.Builder(requireContext())
                .setTitle("Enable Biometric Login")
                .setMessage("Would you like to use biometric authentication for future logins?")
                .setPositiveButton("Yes") { _, _ ->
                    BiometricHelper.setBiometricEnabled(requireContext(), true)
                    navigateToDashboard()
                }
                .setNegativeButton("No") { _, _ ->
                    navigateToDashboard()
                }
                .setCancelable(false)
                .show()
        } else {
            navigateToDashboard()
        }
    }

    private fun navigateToDashboard() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.loginFragment, true)
            .build()
        findNavController().navigate(R.id.dashboardFragment, null, navOptions)
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnLogin.isEnabled = !isLoading
        binding.btnGoogle.isEnabled = !isLoading
        binding.btnFacebook.isEnabled = !isLoading
        binding.btnLinkedin.isEnabled = !isLoading
        binding.btnX.isEnabled = !isLoading
        
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.text = if (isLoading) "" else "Login"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
