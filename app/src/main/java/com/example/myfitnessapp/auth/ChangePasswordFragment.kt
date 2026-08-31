package com.example.myfitnessapp.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentChangePasswordBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChangePasswordFragment : Fragment() {

    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate<FragmentChangePasswordBinding>(
            inflater, R.layout.fragment_change_password, container, false
        )
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnUpdate.setOnClickListener {
            val currentPass = binding.etCurrentPassword.text.toString().trim()
            val newPass = binding.etNewPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()
            val email = FirebaseAuth.getInstance().currentUser?.email ?: ""

            if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass != confirmPass) {
                binding.etConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }
            
            if (newPass.length < 6) {
                binding.etNewPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            viewModel.changePassword(email, currentPass, newPass)
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = FirebaseAuth.getInstance().currentUser?.email
            if (email != null) {
                viewModel.resetPassword(email)
            } else {
                Toast.makeText(requireContext(), "User email not found", Toast.LENGTH_SHORT).show()
            }
        }

        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.authState.collectLatest { state ->
                when (state) {
                    is AuthViewModel.AuthState.PasswordChanged -> {
                        Toast.makeText(requireContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack()
                        viewModel.resetState()
                    }
                    is AuthViewModel.AuthState.ResetLinkSent -> {
                        Toast.makeText(requireContext(), "Reset link sent! Please check your email inbox and SPAM folder.", Toast.LENGTH_LONG).show()
                        viewModel.resetState()
                    }
                    is AuthViewModel.AuthState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetState()
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
