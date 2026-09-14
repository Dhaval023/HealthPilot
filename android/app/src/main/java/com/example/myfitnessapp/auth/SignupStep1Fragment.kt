package com.example.myfitnessapp.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentSignupStep1Binding

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SignupStep1Fragment : Fragment() {

    private var _binding: FragmentSignupStep1Binding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignupStep1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnVerifyEmail.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (name.isNotEmpty() && email.isNotEmpty() && phone.isNotEmpty() && password.isNotEmpty()) {
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                if (phone.length != 10 || !phone.all { it.isDigit() }) {
                    Toast.makeText(context, "Please enter a valid 10-digit mobile number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                viewModel.tempName = name
                viewModel.tempEmail = email
                viewModel.tempPhone = phone
                viewModel.tempPassword = password
                
                viewModel.sendVerificationEmail()
            } else {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNext.setOnClickListener {
            viewModel.checkEmailVerification()
        }

        binding.tvLogin.setOnClickListener {
            findNavController().popBackStack()
        }

        observeViewModel()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Sign Up Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthViewModel.AuthState.Loading -> {
                        binding.btnVerifyEmail.isEnabled = false
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnVerifyEmail.text = ""
                    }
                    is AuthViewModel.AuthState.VerificationEmailSent -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnVerifyEmail.text = "Email Sent! Check Inbox/Spam"
                        binding.btnVerifyEmail.isEnabled = true
                        binding.btnNext.visibility = View.VISIBLE
                        Toast.makeText(context, "Verification email sent! Please check your inbox and SPAM folder.", Toast.LENGTH_LONG).show()
                    }
                    is AuthViewModel.AuthState.EmailVerified -> {
                        binding.progressBar.visibility = View.GONE
                        findNavController().navigate(R.id.action_signupStep1Fragment_to_signupStep2Fragment)
                    }
                    is AuthViewModel.AuthState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnVerifyEmail.isEnabled = true
                        binding.btnVerifyEmail.text = "Verify Email"
                        showErrorDialog(state.message)
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
