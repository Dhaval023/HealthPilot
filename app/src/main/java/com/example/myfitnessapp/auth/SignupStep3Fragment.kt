package com.example.myfitnessapp.auth

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentSignupStep3Binding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class SignupStep3Fragment : Fragment() {

    private var _binding: FragmentSignupStep3Binding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignupStep3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Prevent going back during signup process
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(context, "Please complete your profile to continue", Toast.LENGTH_SHORT).show()
            }
        })

        setupTimePickers()
        setupWorkStylePicker()

        binding.btnCompleteSignup.setOnClickListener {
            val wakeUp = binding.etWakeUp.tag?.toString() ?: ""
            val sleep = binding.etSleep.tag?.toString() ?: ""
            val workStyle = binding.etWorkStyle.text.toString().trim()
            val workStart = binding.etWorkStart.tag?.toString() ?: ""
            val workEnd = binding.etWorkEnd.tag?.toString() ?: ""
            val screenTimeStr = binding.etScreenTime.text.toString().trim()

            if (wakeUp.isNotEmpty() && sleep.isNotEmpty() && workStyle.isNotEmpty() && 
                workStart.isNotEmpty() && workEnd.isNotEmpty() && screenTimeStr.isNotEmpty()) {
                
                viewModel.signUp(
                    wakeUpTime = wakeUp,
                    sleepTime = sleep,
                    workStyle = workStyle,
                    workStartTime = workStart,
                    workEndTime = workEnd,
                    screenTime = screenTimeStr.toIntOrNull() ?: 0
                )
            } else {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        observeViewModel()
    }

    private fun setupTimePickers() {
        val timePickerListener = { view: View ->
            val editText = view as android.widget.EditText
            val current24h = editText.tag?.toString() ?: "08:00"
            val parts = current24h.split(":")
            val hour = if (parts.size == 2) parts[0].toInt() else 8
            val minute = if (parts.size == 2) parts[1].toInt() else 0

            TimePickerDialog(requireContext(), R.style.CustomPickerTheme, { _, h, m ->
                val time24h = "%02d:%02d".format(h, m)
                editText.tag = time24h
                editText.setText(formatTo12h(time24h))
            }, hour, minute, false).show()
        }

        binding.etWakeUp.setOnClickListener { timePickerListener(it) }
        binding.etSleep.setOnClickListener { timePickerListener(it) }
        binding.etWorkStart.setOnClickListener { timePickerListener(it) }
        binding.etWorkEnd.setOnClickListener { timePickerListener(it) }
        
        // Set default values
        setDefaultTime(binding.etWakeUp, "07:00")
        setDefaultTime(binding.etSleep, "22:00")
        setDefaultTime(binding.etWorkStart, "09:00")
        setDefaultTime(binding.etWorkEnd, "17:00")
    }

    private fun setDefaultTime(editText: android.widget.EditText, time24h: String) {
        editText.tag = time24h
        editText.setText(formatTo12h(time24h))
    }

    private fun formatTo12h(time24h: String): String {
        return try {
            val parts = time24h.split(":")
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val suffix = if (h >= 12) "PM" else "AM"
            val h12 = if (h % 12 == 0) 12 else h % 12
            "%02d:%02d %s".format(h12, m, suffix)
        } catch (e: Exception) { time24h }
    }

    private fun setupWorkStylePicker() {
        binding.etWorkStyle.setOnClickListener {
            val styles = arrayOf("Mostly Sitting", "Mostly Standing", "Mixed Activity", "Heavy Physical Work", "Driving", "Home & Care")
            val dialog = BottomSheetDialog(requireContext(), R.style.CustomDialogTheme)
            val view = layoutInflater.inflate(R.layout.dialog_work_style_picker, null)
            dialog.setContentView(view)

            val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_work_styles)
            rv.addItemDecoration(androidx.recyclerview.widget.DividerItemDecoration(requireContext(), androidx.recyclerview.widget.DividerItemDecoration.VERTICAL))
            
            val adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                    val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_work_style, parent, false)
                    return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {}
                }

                override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                    val textView = holder.itemView.findViewById<android.widget.TextView>(R.id.tv_style_name)
                    textView.text = styles[position]
                    holder.itemView.setOnClickListener {
                        binding.etWorkStyle.setText(styles[position])
                        dialog.dismiss()
                    }
                }

                override fun getItemCount() = styles.size
            }

            rv.adapter = adapter
            dialog.show()
        }
    }

    private fun showErrorDialog(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
                        binding.btnCompleteSignup.isEnabled = false
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnCompleteSignup.text = ""
                    }
                    is AuthViewModel.AuthState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.loginFragment, true)
                            .build()
                        findNavController().navigate(R.id.dashboardFragment, null, navOptions)
                    }
                    is AuthViewModel.AuthState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnCompleteSignup.isEnabled = true
                        binding.btnCompleteSignup.text = "Complete Signup"
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
