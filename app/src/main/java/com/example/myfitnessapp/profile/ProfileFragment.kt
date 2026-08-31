package com.example.myfitnessapp.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.app.DatePickerDialog
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentProfileBinding
import com.example.myfitnessapp.viewmodel.DashboardViewModel
import com.example.myfitnessapp.utils.BiometricHelper
import com.google.firebase.auth.FirebaseAuth
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private var selectedImageBitmap: Bitmap? = null
    private var photoUri: android.net.Uri? = null
    private val calendar = Calendar.getInstance()
    private var calculatedAge = 0

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { uri ->
                val bitmap = uriToBitmap(uri)
                bitmap?.let {
                    binding.ivProfileImage.setImageBitmap(it)
                    selectedImageBitmap = it
                }
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val bitmap = uriToBitmap(it)
            bitmap?.let { b ->
                binding.ivProfileImage.setImageBitmap(b)
                selectedImageBitmap = b
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

    private fun openCamera() {
        photoUri = createImageFileUri()
        takePhotoLauncher.launch(photoUri)
    }

    private fun uriToBitmap(uri: android.net.Uri): Bitmap? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createImageFileUri(): android.net.Uri {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val storageDir = requireContext().cacheDir
        val file = java.io.File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        return FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        val builder = android.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Change Profile Picture")
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
                2 -> dialog.dismiss()
            }
        }
        builder.show()
    }

    private val viewModel: DashboardViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = requireActivity().application as HealthPilot
                val repository = app.repository
                return DashboardViewModel(app, repository) as T
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnMenu.setOnClickListener {
            (activity as? com.example.myfitnessapp.MainActivity)?.openDrawer()
        }

        binding.ivProfileImage.setOnClickListener {
            if (binding.viewSwitcher.displayedChild == 1) {
                showImagePickerOptions()
            }
        }

        binding.btnLogout.setOnClickListener {
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    BiometricHelper.setBiometricEnabled(requireContext(), false)
                    FirebaseAuth.getInstance().signOut()
                    val intent = android.content.Intent(requireActivity(), com.example.myfitnessapp.MainActivity::class.java)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnEditProfile.setOnClickListener {
            if (binding.viewSwitcher.displayedChild == 0) {
                showEditMode()
            } else {
                showViewMode()
            }
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        binding.btnChangePassword.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_changePasswordFragment)
        }

        setupTimePicker(binding.etWakeUp)
        setupTimePicker(binding.etSleep)
        setupTimePicker(binding.etWorkStart)
        setupTimePicker(binding.etWorkEnd)

        binding.etWorkStyle.isFocusable = false
        binding.etWorkStyle.setOnClickListener {
            val styles = arrayOf("Mostly Sitting", "Mostly Standing", "Mixed Activity", "Heavy Physical Work", "Driving", "Home & Care")
            android.app.AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
                .setTitle("Select Work Style")
                .setItems(styles) { _, which ->
                    binding.etWorkStyle.setText(styles[which])
                }
                .show()
        }

        setupDatePicker()
        binding.etAge.isFocusable = false
        binding.etAge.isClickable = false
    }

    private fun setupDatePicker() {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)

            val myFormat = "dd/MM/yyyy"
            val sdf = SimpleDateFormat(myFormat, Locale.US)
            binding.etDob.setText(sdf.format(calendar.time))

            // Calculate age
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - year
            if (today.get(Calendar.DAY_OF_YEAR) < calendar.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            calculatedAge = age
            binding.etAge.setText(age.toString())
        }

        binding.etDob.isFocusable = false
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

    private fun setupTimePicker(editText: android.widget.EditText) {
        editText.isFocusable = false
        editText.isClickable = true
        editText.setOnClickListener {
            val current24h = editText.tag?.toString() ?: "08:00"
            val parts = current24h.split(":")
            val hour = if (parts.size == 2) parts[0].toInt() else 8
            val minute = if (parts.size == 2) parts[1].toInt() else 0

            android.app.TimePickerDialog(requireContext(), R.style.CustomPickerTheme, { _, h, m ->
                val time24h = "%02d:%02d".format(h, m)
                editText.tag = time24h
                editText.setText(formatTo12h(time24h))
            }, hour, minute, false).show()
        }
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

    private fun showEditMode() {
        val user = viewModel.user.value ?: return
        binding.etName.setText(user.name)
        binding.etEmail.setText(user.email)
        binding.etPhone.setText(user.phone)
        binding.etAge.setText(user.age.toString())
        binding.etGender.setText(user.gender)
        binding.etHeight.setText(user.height.toString())
        binding.etWeight.setText(user.weight.toString())
        binding.etDob.setText(user.dob)

        setFieldTime(binding.etWakeUp, user.wakeUpTime)
        setFieldTime(binding.etSleep, user.sleepTime)
        binding.etWorkStyle.setText(user.workStyle)
        setFieldTime(binding.etWorkStart, user.workStartTime)
        setFieldTime(binding.etWorkEnd, user.workEndTime)
        binding.etScreenTime.setText(user.dailyScreenTime.toString())

        binding.viewSwitcher.showNext()
        binding.btnEditProfile.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
    }

    private fun setFieldTime(editText: android.widget.EditText, time24h: String) {
        editText.tag = time24h
        editText.setText(formatTo12h(time24h))
    }

    private fun showViewMode() {
        binding.viewSwitcher.showPrevious()
        binding.btnEditProfile.setImageResource(R.drawable.ic_edit)
    }

    private fun saveProfile() {
        val currentUser = viewModel.user.value ?: return
        
        val imageBase64 = selectedImageBitmap?.let { bitmapToBase64(it) } ?: currentUser.profileImageUrl

        val updatedUser = currentUser.copy(
            name = binding.etName.text.toString(),
            phone = binding.etPhone.text.toString(),
            age = binding.etAge.text.toString().toIntOrNull() ?: currentUser.age,
            gender = binding.etGender.text.toString(),
            height = binding.etHeight.text.toString().toIntOrNull() ?: currentUser.height,
            weight = binding.etWeight.text.toString().toDoubleOrNull() ?: currentUser.weight,
            dob = binding.etDob.text.toString(),
            wakeUpTime = binding.etWakeUp.tag?.toString() ?: currentUser.wakeUpTime,
            sleepTime = binding.etSleep.tag?.toString() ?: currentUser.sleepTime,
            workStyle = binding.etWorkStyle.text.toString(),
            workStartTime = binding.etWorkStart.tag?.toString() ?: currentUser.workStartTime,
            workEndTime = binding.etWorkEnd.tag?.toString() ?: currentUser.workEndTime,
            dailyScreenTime = binding.etScreenTime.text.toString().toIntOrNull() ?: currentUser.dailyScreenTime,
            profileImageUrl = imageBase64
        )
        
        // Generate schedule and update user in one flow to avoid race conditions
        viewModel.generateIdealSchedule(updatedUser)

        showViewMode()
        selectedImageBitmap = null
        android.widget.Toast.makeText(context, "Profile Updated", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val resizedBitmap = resizeBitmap(bitmap, 400, 400)
        val outputStream = java.io.ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height

        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        // Show main progress bar if we're in edit mode or saving
//                        if (binding.viewSwitcher.displayedChild == 1) {
////                            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
////                            binding.btnSaveProfile.isEnabled = !isLoading
//                        } else {
                            val skeleton = binding.root.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_view_container)
                            skeleton?.visibility = if (isLoading) View.VISIBLE else View.GONE
                            binding.mainContent.visibility = if (isLoading) View.GONE else View.VISIBLE

                            if (isLoading) skeleton?.startShimmer()
                            else skeleton?.stopShimmer()
//                        }
                    }
                }
                launch {
                    viewModel.user.collect { user ->
                        user?.let {
                            binding.tvUserName.text = it.name
                            binding.tvUserEmail.text = it.email
                            binding.tvProfileAge.text = it.age.toString()
                            binding.tvProfileGender.text = it.gender
                            binding.tvProfileHeight.text = "${it.height} cm"
                            binding.tvProfileWeight.text = "${it.weight} kg"
                            binding.tvProfilePhone.text = it.phone
                            binding.tvProfileDob.text = it.dob

                            binding.tvProfileScheduleRange.text = "${formatTo12h(it.wakeUpTime)} - ${formatTo12h(it.sleepTime)}"
                            binding.tvProfileWorkStyle.text = it.workStyle
                            binding.tvProfileWorkMeta.text = "${formatTo12h(it.workStartTime)} - ${formatTo12h(it.workEndTime)} | ${it.dailyScreenTime} hrs screen"

                            if (it.profileImageUrl.isNotEmpty()) {
                                if (it.profileImageUrl.startsWith("http")) {
                                    Glide.with(this@ProfileFragment)
                                        .load(it.profileImageUrl)
                                        .circleCrop()
                                        .into(binding.ivProfileImage)
                                } else {
                                    val bitmap = base64ToBitmap(it.profileImageUrl)
                                    if (bitmap != null) {
                                        binding.ivProfileImage.setImageBitmap(bitmap)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val base64Data = if (base64Str.contains(",")) {
                base64Str.substring(base64Str.indexOf(",") + 1)
            } else {
                base64Str
            }
            val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
