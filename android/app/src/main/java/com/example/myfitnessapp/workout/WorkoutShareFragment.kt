package com.example.myfitnessapp.workout

import android.graphics.BitmapFactory
import android.util.Base64
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.R
import com.example.myfitnessapp.data.workout.WorkoutRecord
import com.example.myfitnessapp.databinding.FragmentWorkoutShareBinding
import com.example.myfitnessapp.databinding.ItemShareTemplateBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

data class ShareTemplate(
    val name: String,
    val bgColor: Int,
    val textColor: Int,
    val accentColor: Int,
    val previewColor: Int,
    val isDark: Boolean = true,
    val fontFamily: String? = null
)

data class ShareBackground(
    val name: String,
    val resId: Int? = null,
    val gradientColors: IntArray? = null
)

class WorkoutShareFragment : Fragment() {

    private var _binding: FragmentWorkoutShareBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private var currentWorkout: WorkoutRecord? = null
    private var selectedTemplate: ShareTemplate? = null
    private var selectedBackground: ShareBackground? = null
    
    private var customBgStart = Color.BLACK
    private var customBgEnd = Color.parseColor("#1A1A2E")

    private val quotes = listOf(
        "Discipline beats motivation.",
        "Every step counts.",
        "Consistency creates champions.",
        "The body achieves what the mind believes.",
        "Push yourself because no one else will.",
        "Progress is progress.",
        "Sweat is just fat crying.",
        "Don't stop when you're tired. Stop when you're done."
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkoutShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val workoutId = arguments?.getString("workoutId") ?: ""
        val workoutType = arguments?.getString("workoutType") ?: ""

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        setupTemplates()
        setupBackgrounds()
        setupColorPalette()
        setupListeners()
        
        if (workoutId.isNotEmpty() && workoutType.isNotEmpty()) {
            loadWorkoutDetails(workoutId, workoutType)
        } else {
            // Check for direct stats
            val steps = arguments?.getInt("steps", 0) ?: 0
            val distance = arguments?.getFloat("distance", 0f) ?: 0f
            val calories = arguments?.getFloat("calories", 0f) ?: 0f
            val duration = arguments?.getLong("duration", 0L) ?: 0L
            val type = arguments?.getString("type") ?: "Workout"
            val heartRate = arguments?.getInt("heartRate", 80) ?: 80

            val record = WorkoutRecord(
                workoutType = type,
                totalSteps = steps,
                totalDistance = distance.toDouble(),
                caloriesBurned = calories.toDouble(),
                duration = duration,
                startTime = System.currentTimeMillis(),
                averageHeartRate = heartRate
            )
            currentWorkout = record
            displayWorkout(record)
            
            // Still try to load user profile
            loadUserProfileOnly()
        }

        refreshQuote()
    }

    private fun loadUserProfileOnly() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: doc.getString("fullName") ?: "Fitness Enthusiast"
            binding.sharePreview.tvUserName.text = name
            
            val profileBase64 = doc.getString("profileImageUrl")
            if (profileBase64 != null && !profileBase64.startsWith("http")) {
                try {
                    val decodedString: ByteArray = Base64.decode(profileBase64, Base64.DEFAULT)
                    val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    binding.sharePreview.ivUserProfile.setImageBitmap(decodedByte)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun setupBackgrounds() {
        val backgrounds = listOf(
            ShareBackground("Midnight", gradientColors = intArrayOf(Color.parseColor("#0F2027"), Color.parseColor("#203A43"), Color.parseColor("#2C5364"))),
            ShareBackground("Royal", gradientColors = intArrayOf(Color.parseColor("#141E30"), Color.parseColor("#243B55"))),
            ShareBackground("Deep Ocean", gradientColors = intArrayOf(Color.parseColor("#000428"), Color.parseColor("#004e92"))),
            ShareBackground("Lush", gradientColors = intArrayOf(Color.parseColor("#56ab2f"), Color.parseColor("#a8e063"))),
            ShareBackground("Fire", gradientColors = intArrayOf(Color.parseColor("#f12711"), Color.parseColor("#f5af19"))),
            ShareBackground("Sky", gradientColors = intArrayOf(Color.parseColor("#00c6ff"), Color.parseColor("#0072ff"))),
            ShareBackground("Violet", gradientColors = intArrayOf(Color.parseColor("#654ea3"), Color.parseColor("#eaafc8"))),
            ShareBackground("Cyber", gradientColors = intArrayOf(Color.parseColor("#8E2DE2"), Color.parseColor("#4A00E0"))),
            ShareBackground("Peach", gradientColors = intArrayOf(Color.parseColor("#ED4264"), Color.parseColor("#FFEDBC"))),
            ShareBackground("Sea", gradientColors = intArrayOf(Color.parseColor("#2193b0"), Color.parseColor("#6dd5ed"))),
            ShareBackground("Rose", gradientColors = intArrayOf(Color.parseColor("#e65c00"), Color.parseColor("#F9D423"))),
            ShareBackground("Purp", gradientColors = intArrayOf(Color.parseColor("#9D50BB"), Color.parseColor("#6E7AFF"))),
            ShareBackground("Mint", gradientColors = intArrayOf(Color.parseColor("#00b09b"), Color.parseColor("#96c93d"))),
            ShareBackground("Dark Knight", gradientColors = intArrayOf(Color.parseColor("#232526"), Color.parseColor("#414345"))),
            ShareBackground("Electric", gradientColors = intArrayOf(Color.parseColor("#000000"), Color.parseColor("#004e92"), Color.parseColor("#000000"))),
            ShareBackground("Neon", gradientColors = intArrayOf(Color.parseColor("#00F2FE"), Color.parseColor("#4FACFE"))),
            ShareBackground("Gold", gradientColors = intArrayOf(Color.parseColor("#BF953F"), Color.parseColor("#FCF6BA"), Color.parseColor("#B38728"), Color.parseColor("#FBF5B7"), Color.parseColor("#AA771C"))),
            ShareBackground("Magma", gradientColors = intArrayOf(Color.parseColor("#000000"), Color.parseColor("#434343"))),
            ShareBackground("Pride", gradientColors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE, Color.MAGENTA)),
            ShareBackground("Candy", gradientColors = intArrayOf(Color.parseColor("#D3959B"), Color.parseColor("#BFE6BA"))),
            ShareBackground("Misty", gradientColors = intArrayOf(Color.parseColor("#E0EAFC"), Color.parseColor("#CFDEF3"))),
            ShareBackground("Venice", gradientColors = intArrayOf(Color.parseColor("#085078"), Color.parseColor("#85D8CE"))),
            ShareBackground("Bora Bora", gradientColors = intArrayOf(Color.parseColor("#2BC0E4"), Color.parseColor("#EAECC6"))),
            ShareBackground("Horizon", gradientColors = intArrayOf(Color.parseColor("#003973"), Color.parseColor("#E5E5BE"))),
            ShareBackground("Rose Water", gradientColors = intArrayOf(Color.parseColor("#E55D87"), Color.parseColor("#5FC3E4"))),
            ShareBackground("Lemon", gradientColors = intArrayOf(Color.parseColor("#fbef60"), Color.parseColor("#f7971e"))),
            ShareBackground("Turquoise", gradientColors = intArrayOf(Color.parseColor("#136a8a"), Color.parseColor("#267871"))),
            ShareBackground("Blood", gradientColors = intArrayOf(Color.parseColor("#f85032"), Color.parseColor("#e73827"))),
            ShareBackground("Kashmir", gradientColors = intArrayOf(Color.parseColor("#614385"), Color.parseColor("#516395")))
        )

        val adapter = BackgroundAdapter(backgrounds) { bg ->
            applyBackground(bg)
        }
        binding.rvBackgrounds.adapter = adapter
        
        // Apply first one by default
        applyBackground(backgrounds[0])
    }

    private fun applyBackground(bg: ShareBackground) {
        selectedBackground = bg
        val preview = binding.sharePreview
        
        if (bg.resId != null) {
            preview.ivBackground.setImageResource(bg.resId)
            preview.ivBackground.visibility = View.VISIBLE
            preview.vOverlay.visibility = View.VISIBLE
            preview.previewContainer.background = null
        } else if (bg.gradientColors != null) {
            val gd = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                bg.gradientColors
            )
            preview.previewContainer.background = gd
            preview.ivBackground.visibility = View.GONE
            preview.vOverlay.visibility = View.GONE
        }
    }

    private fun setupTemplates() {
        val templates = listOf(
            ShareTemplate("Dark Neon", Color.BLACK, Color.WHITE, Color.parseColor("#00F2FE"), Color.BLACK),
            ShareTemplate("Minimal", Color.WHITE, Color.BLACK, Color.parseColor("#5E5CE6"), Color.WHITE, false),
            ShareTemplate("Premium Gold", Color.BLACK, Color.parseColor("#FFD700"), Color.parseColor("#FFD700"), Color.BLACK),
            ShareTemplate("Vibrant", Color.parseColor("#8E2DE2"), Color.WHITE, Color.WHITE, Color.parseColor("#8E2DE2")),
            ShareTemplate("Ocean Blue", Color.parseColor("#00B4DB"), Color.WHITE, Color.WHITE, Color.parseColor("#00B4DB")),
            ShareTemplate("Sunset", Color.parseColor("#F12711"), Color.WHITE, Color.parseColor("#F5AF19"), Color.parseColor("#F12711")),
            ShareTemplate("Gym Mode", Color.parseColor("#1C1E23"), Color.WHITE, Color.parseColor("#B6FF40"), Color.parseColor("#1C1E23")),
            ShareTemplate("Nature", Color.parseColor("#11998e"), Color.WHITE, Color.parseColor("#38ef7d"), Color.parseColor("#11998e"))
        )

        val adapter = TemplateAdapter(templates) { template ->
            applyTemplate(template)
        }
        binding.rvTemplates.adapter = adapter
        
        applyTemplate(templates[0])
    }

    private fun applyTemplate(template: ShareTemplate) {
        selectedTemplate = template
        val preview = binding.sharePreview
        
        preview.tvWorkoutTitle.setTextColor(template.textColor)
        preview.tvUserName.setTextColor(template.textColor)
        preview.tvWorkoutDate.setTextColor(if (template.isDark) Color.LTGRAY else Color.DKGRAY)
        preview.tvQuote.setTextColor(template.textColor)
        
        preview.pbGoal.progressTintList = android.content.res.ColorStateList.valueOf(template.accentColor)
        preview.tvAppBrand.setTextColor(template.accentColor)
        preview.tvGoalProgress.setTextColor(template.textColor)
        
        // Adjust overlay for light/dark themes
        preview.vOverlay.alpha = if (template.isDark) 0.7f else 0.3f
        
        if (template.name == "Gold") {
            preview.tvWorkoutTitle.setTextColor(Color.parseColor("#FFD700"))
            preview.tvAppBrand.setTextColor(Color.parseColor("#FFD700"))
        }
    }

    private fun setupListeners() {
        binding.btnExportShare.setOnClickListener {
            exportAndShare()
        }

        binding.btnRefreshQuote.setOnClickListener {
            refreshQuote()
        }

        binding.chipShowSteps.setOnCheckedChangeListener { _, isChecked ->
            binding.sharePreview.llSteps.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.chipShowDistance.setOnCheckedChangeListener { _, isChecked ->
            binding.sharePreview.layoutDistance.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.chipShowCalories.setOnCheckedChangeListener { _, isChecked ->
            binding.sharePreview.llCalories.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.chipShowHeartRate.setOnCheckedChangeListener { _, isChecked ->
            binding.sharePreview.llHeartRate.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.chipShowQuote.setOnCheckedChangeListener { _, isChecked ->
            binding.sharePreview.tvQuote.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.chipShowProfile.setOnCheckedChangeListener { _, isChecked ->
            binding.sharePreview.ivUserProfile.visibility = if (isChecked) View.VISIBLE else View.GONE
            binding.sharePreview.llUserInfo.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun refreshQuote() {
        binding.sharePreview.tvQuote.text = "\"${quotes.random()}\""
    }

    private fun loadWorkoutDetails(id: String, type: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("workouts").document(type)
            .collection("dailyRecords").document(id)
            .get()
            .addOnSuccessListener { document ->
                currentWorkout = document.toObject(WorkoutRecord::class.java)
                currentWorkout?.let { displayWorkout(it) }
            }
        
        db.collection("users").document(userId).get().addOnSuccessListener { doc ->
            val name = doc.getString("name") ?: doc.getString("fullName") ?: "Fitness Enthusiast"
            binding.sharePreview.tvUserName.text = name
            
            val profileBase64 = doc.getString("profileImageUrl")
            if (profileBase64 != null && !profileBase64.startsWith("http")) {
                try {
                    val decodedString: ByteArray = Base64.decode(profileBase64, Base64.DEFAULT)
                    val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    binding.sharePreview.ivUserProfile.setImageBitmap(decodedByte)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun displayWorkout(it: WorkoutRecord) {
        val preview = binding.sharePreview
        
        val type = it.workoutType.lowercase()
        preview.tvWorkoutTitle.text = it.workoutType.uppercase()
        
        // Manage visible stats based on workout type
        val isYoga = type.contains("yoga")
        val isStrength = type.contains("strength") || type.contains("gym") || type.contains("weight")
        
        if (isYoga || isStrength) {
            preview.layoutDistance.visibility = View.GONE
            preview.llSteps.visibility = View.GONE
            // Show only Duration, Calories, Heart Rate
        } else {
            preview.layoutDistance.visibility = View.VISIBLE
            preview.llSteps.visibility = View.VISIBLE
        }

        val dateSdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
        preview.tvWorkoutDate.text = dateSdf.format(Date(it.startTime))

        val durationSec = it.duration / 1000
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        preview.tvDurationValue.text = if (minutes > 0) minutes.toString() else "0:${String.format(Locale.getDefault(), "%02d", seconds)}"

        preview.tvStepsValue.text = String.format(Locale.getDefault(), "%,d", it.totalSteps)
        preview.tvDistanceValue.text = String.format(Locale.getDefault(), "%.1f", it.totalDistance)
        preview.tvCaloriesValue.text = String.format(Locale.getDefault(), "%.0f", it.caloriesBurned)
        preview.tvHeartRateValue.text = (it.averageHeartRate ?: 80).toString()
        
        val progress = if (it.totalSteps > 0) {
            ((it.totalSteps.toFloat() / 6000) * 100).toInt().coerceIn(0, 100)
        } else if (it.duration > 0) {
            ((it.duration.toFloat() / (30 * 60 * 1000)) * 100).toInt().coerceIn(0, 100)
        } else 0
        
        preview.pbGoal.progress = progress
        preview.tvGoalProgress.text = "$progress%"
        preview.llAchievement.visibility = if (progress >= 100) View.VISIBLE else View.GONE

        // Update Icons
        val iconRes = when {
            type.contains("run") -> R.drawable.running
            type.contains("cycle") -> R.drawable.cycling
            type.contains("yoga") -> R.drawable.yoga
            type.contains("box") -> R.drawable.boxing
            type.contains("swim") -> R.drawable.swimming
            type.contains("foot") -> R.drawable.football
            type.contains("cricket") -> R.drawable.cricket
            type.contains("badminton") -> R.drawable.badminton
            type.contains("tennis") -> R.drawable.tennis
            type.contains("hockey") -> R.drawable.hockey
            type.contains("basket") -> R.drawable.basketball
            type.contains("volley") -> R.drawable.volleyball
            type.contains("dance") || type.contains("zumba") -> R.drawable.zumba
            type.contains("weight") || type.contains("gym") -> R.drawable.weightlifting
            type.contains("kaba") -> R.drawable.kabaddi
            type.contains("wrest") -> R.drawable.wrestling
            else -> R.drawable.ic_walk
        }
        preview.ivCenterWorkout.setImageResource(iconRes)
        preview.ivWorkoutIconSmall.setImageResource(iconRes)

        // Set initial background based on workout type
        if (selectedBackground == null) {
            preview.ivBackground.setImageResource(iconRes)
        }
    }

    private fun exportAndShare() {
        val bitmap = createBitmapFromView(binding.previewCard)
        saveAndShareImage(bitmap)
    }

    private fun createBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun saveAndShareImage(bitmap: Bitmap) {
        val filename = "HealthPilot_Workout_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var uri: Uri? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/HealthPilot")
            }
            val contentResolver = requireContext().contentResolver
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = uri?.let { contentResolver.openOutputStream(it) }
        } else {
            val imagesDir = java.io.File(requireContext().getExternalFilesDir(null), "Pictures")
            if (!imagesDir.exists()) imagesDir.mkdir()
            val image = java.io.File(imagesDir, filename)
            fos = FileOutputStream(image)
            uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", image)
        }

        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            Toast.makeText(requireContext(), "Image saved to gallery", Toast.LENGTH_SHORT).show()
            shareImageUri(uri)
        }
    }

    private fun shareImageUri(uri: Uri?) {
        uri ?: return
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "image/png"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Workout via"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupColorPalette() {
        val colors = listOf(
            Color.WHITE, Color.BLACK, Color.LTGRAY, Color.DKGRAY,
            Color.parseColor("#FF2D55"), Color.parseColor("#FF9F0A"),
            Color.parseColor("#FFD60A"), Color.parseColor("#34C759"),
            Color.parseColor("#64D2FF"), Color.parseColor("#007AFF"),
            Color.parseColor("#5856D6"), Color.parseColor("#AF52DE"),
            Color.parseColor("#FF375F"), Color.parseColor("#B6FF40"),
            Color.parseColor("#00F2FE"), Color.parseColor("#4FACFE"),
            Color.parseColor("#f12711"), Color.parseColor("#f5af19"),
            Color.parseColor("#8E2DE2"), Color.parseColor("#4A00E0"),
            Color.parseColor("#ED4264"), Color.parseColor("#FFEDBC"),
            Color.parseColor("#D85FD3"), Color.parseColor("#7E57C2")
        )

        val adapter = ColorAdapter(colors) { color ->
            applyColorToSelectedTarget(color)
        }
        binding.rvColorPalette.adapter = adapter
    }

    private fun applyColorToSelectedTarget(color: Int) {
        val preview = binding.sharePreview
        when {
            binding.chipTargetBgStart.isChecked -> {
                customBgStart = color
                updateCustomGradient()
            }
            binding.chipTargetBgEnd.isChecked -> {
                customBgEnd = color
                updateCustomGradient()
            }
            binding.chipTargetTitle.isChecked -> {
                preview.tvWorkoutTitle.setTextColor(color)
            }
            binding.chipTargetQuote.isChecked -> {
                preview.tvQuote.setTextColor(color)
            }
            binding.chipTargetBrand.isChecked -> {
                preview.tvAppBrand.setTextColor(color)
            }
            binding.chipTargetUserName.isChecked -> {
                preview.tvUserName.setTextColor(color)
            }
            binding.chipTargetStats.isChecked -> {
                preview.tvDistanceValue.setTextColor(color)
                preview.tvDurationValue.setTextColor(color)
                preview.tvStepsValue.setTextColor(color)
                preview.tvCaloriesValue.setTextColor(color)
                preview.tvHeartRateValue.setTextColor(color)
            }
        }
    }

    private fun updateCustomGradient() {
        val gd = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(customBgStart, customBgEnd)
        )
        binding.sharePreview.previewContainer.background = gd
        binding.sharePreview.ivBackground.visibility = View.GONE
        binding.sharePreview.vOverlay.visibility = View.GONE
        selectedBackground = null // Marking that we are using custom
    }

    inner class ColorAdapter(
        private val colors: List<Int>,
        private val onColorSelected: (Int) -> Unit
    ) : RecyclerView.Adapter<ColorAdapter.ViewHolder>() {

        inner class ViewHolder(val colorBinding: com.example.myfitnessapp.databinding.ItemColorPresetBinding) : 
            RecyclerView.ViewHolder(colorBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val colorBinding = com.example.myfitnessapp.databinding.ItemColorPresetBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(colorBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val color = colors[position]
            holder.colorBinding.viewColor.setBackgroundColor(color)
            holder.itemView.setOnClickListener { onColorSelected(color) }
        }

        override fun getItemCount() = colors.size
    }

    inner class TemplateAdapter(
        private val templates: List<ShareTemplate>,
        private val onTemplateSelected: (ShareTemplate) -> Unit
    ) : RecyclerView.Adapter<TemplateAdapter.ViewHolder>() {

        private var selectedIndex = 0

        inner class ViewHolder(val itmBinding: ItemShareTemplateBinding) : RecyclerView.ViewHolder(itmBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itmBinding = ItemShareTemplateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(itmBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val template = templates[position]
            holder.itmBinding.tvTemplateName.text = template.name
            
            holder.itmBinding.vTemplatePreview.setBackgroundColor(template.previewColor)
            holder.itmBinding.tvTemplateName.setTextColor(if (template.isDark) Color.WHITE else Color.BLACK)
            
            holder.itmBinding.cardTemplate.strokeWidth = if (selectedIndex == position) 4 else 0
            
            holder.itemView.setOnClickListener {
                val oldIndex = selectedIndex
                selectedIndex = holder.adapterPosition
                notifyItemChanged(oldIndex)
                notifyItemChanged(selectedIndex)
                onTemplateSelected(template)
            }
        }

        override fun getItemCount() = templates.size
    }

    inner class BackgroundAdapter(
        private val backgrounds: List<ShareBackground>,
        private val onBackgroundSelected: (ShareBackground) -> Unit
    ) : RecyclerView.Adapter<BackgroundAdapter.ViewHolder>() {

        private var selectedIndex = 0

        inner class ViewHolder(val itmBinding: ItemShareTemplateBinding) : RecyclerView.ViewHolder(itmBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itmBinding = ItemShareTemplateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(itmBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val bg = backgrounds[position]
            holder.itmBinding.tvTemplateName.text = bg.name
            
            if (bg.resId != null) {
                holder.itmBinding.vTemplatePreview.setBackgroundResource(bg.resId)
            } else if (bg.gradientColors != null) {
                val gd = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bg.gradientColors)
                holder.itmBinding.vTemplatePreview.background = gd
            }
            
            holder.itmBinding.cardTemplate.strokeWidth = if (selectedIndex == position) 4 else 0
            
            holder.itemView.setOnClickListener {
                val oldIndex = selectedIndex
                selectedIndex = holder.adapterPosition
                notifyItemChanged(oldIndex)
                notifyItemChanged(selectedIndex)
                onBackgroundSelected(bg)
            }
        }

        override fun getItemCount() = backgrounds.size
    }
}
