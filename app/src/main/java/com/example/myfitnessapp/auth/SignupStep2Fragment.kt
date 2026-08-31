package com.example.myfitnessapp.auth

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentSignupStep2Binding
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import android.app.AlertDialog
import java.io.File
import com.bumptech.glide.Glide

class SignupStep2Fragment : Fragment() {

    private var _binding: FragmentSignupStep2Binding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by activityViewModels()
    private val calendar = Calendar.getInstance()
    private var calculatedAge = 0
    private var photoUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            handleSelectedImage(it)
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let {
                handleSelectedImage(it)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSelectedImage(uri: Uri) {
        binding.ivProfile.setImageURI(uri)
        binding.ivProfile.setPadding(0, 0, 0, 0)
        binding.ivProfile.imageTintList = null // Clear tint
        binding.ivProfile.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        encodeImageToBase64(uri)
    }

    private fun openCamera() {
        val photoFile = File(requireContext().cacheDir, "temp_profile_photo.jpg")
        photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )
        takePhotoLauncher.launch(photoUri)
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select Profile Picture")
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    if (ContextCompat.checkSelfPermission(
                            requireContext(),
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        openCamera()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
                1 -> pickImageLauncher.launch("image/*")
                else -> dialog.dismiss()
            }
        }
        builder.show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSignupStep2Binding.inflate(inflater, container, false)
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

        setupDatePicker()

        // Show existing profile image if available (e.g. from Google login)
        if (viewModel.tempImageBase64.isNotEmpty()) {
            if (viewModel.tempImageBase64.startsWith("http")) {
                Glide.with(this)
                    .load(viewModel.tempImageBase64)
                    .circleCrop()
                    .into(binding.ivProfile)
                binding.ivProfile.setPadding(0, 0, 0, 0)
                binding.ivProfile.imageTintList = null
            } else {
                try {
                    val decodedBytes = Base64.decode(viewModel.tempImageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    binding.ivProfile.setImageBitmap(bitmap)
                    binding.ivProfile.setPadding(0, 0, 0, 0)
                    binding.ivProfile.imageTintList = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        binding.btnSignupComplete.setOnClickListener {
            val heightStr = binding.etHeight.text.toString().trim()
            val weightStr = binding.etWeight.text.toString().trim()
            val dobStr = binding.etDob.text.toString().trim()
            
            val selectedGenderId = binding.rgGender.checkedRadioButtonId
            val gender = if (selectedGenderId != -1) {
                view.findViewById<RadioButton>(selectedGenderId).text.toString()
            } else ""

            if (heightStr.isNotEmpty() && weightStr.isNotEmpty() && dobStr.isNotEmpty() && gender.isNotEmpty()) {
                viewModel.tempHeight = heightStr.toInt()
                viewModel.tempWeight = weightStr.toDouble()
                viewModel.tempAge = calculatedAge
                viewModel.tempDob = dobStr
                viewModel.tempGender = gender
                
                findNavController().navigate(R.id.action_signupStep2Fragment_to_signupStep3Fragment)
            } else {
                Toast.makeText(context, "Please fill all fields including gender", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSelectPhoto.setOnClickListener {
            showImagePickerOptions()
        }

        observeViewModel()
    }

    private fun encodeImageToBase64(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val bytes = outputStream.toByteArray()
            viewModel.tempImageBase64 = Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)
            
            updateDobLabel()
            calculateAge(year)
        }

        binding.etDob.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                R.style.CustomPickerTheme,
                dateSetListener,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun updateDobLabel() {
        val myFormat = "dd/MM/yyyy"
        val sdf = SimpleDateFormat(myFormat, Locale.US)
        binding.etDob.setText(sdf.format(calendar.time))
    }

    private fun calculateAge(year: Int) {
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - year
        
        if (today.get(Calendar.DAY_OF_YEAR) < calendar.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        
        calculatedAge = age
        binding.etAge.setText(age.toString())
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
                        binding.btnSignupComplete.isEnabled = false
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnSignupComplete.text = ""
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
                        binding.btnSignupComplete.isEnabled = true
                        binding.btnSignupComplete.text = "Complete Signup"
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
