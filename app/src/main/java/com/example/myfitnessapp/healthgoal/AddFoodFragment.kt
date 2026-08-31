package com.example.myfitnessapp.healthgoal

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.R
import com.example.myfitnessapp.models.FoodItem
import com.bumptech.glide.Glide
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddFoodFragment : Fragment() {

    private val viewModel: HealthGoalViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (requireActivity().application as HealthPilot).repository
                return HealthGoalViewModel(repository) as T
            }
        }
    }

    private lateinit var adapter: FoodAdapter
    private var isLossMode: Boolean = true
    private var photoUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoUri?.let { uri ->
                processImageWithVisionAI(uri)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to identify food", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        val photoFile = try {
            createImageFile()
        } catch (ex: Exception) {
            null
        }
        photoFile?.also {
            val uri = FileProvider.getUriForFile(requireContext(), "com.example.myfitnessapp.fileprovider", it)
            photoUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_add_food, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            setupUI(view)
            observeViewModel()
            viewModel.fetchCategories()
        }
    }

    private fun setupUI(view: View) {
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            findNavController().navigateUp()
        }

        val rvFoods = view.findViewById<RecyclerView>(R.id.rv_food_results)
        adapter = FoodAdapter(
            foods = emptyList(),
            onAddClick = { showFoodQuantityDialog(it) },
            onItemClick = { showFoodQuantityDialog(it) }
        )
        rvFoods.layoutManager = LinearLayoutManager(requireContext())
        rvFoods.adapter = adapter

        val etSearch = view.findViewById<EditText>(R.id.et_food_search)
        val tvLabel = view.findViewById<TextView>(R.id.tv_list_label)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && s.length >= 2) {
                    viewModel.searchFood(s.toString())
                    tvLabel.text = "Search Results"
                } else if (s.isNullOrEmpty()) {
                    tvLabel.text = "Recent Food"
                    viewModel.searchFood("")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Initial load of recent foods
        viewModel.searchFood("")

        view.findViewById<View>(R.id.btn_custom_food).setOnClickListener {
            findNavController().navigate(R.id.action_addFoodFragment_to_manualFoodFragment)
        }
        
        view.findViewById<View>(R.id.btn_categories).setOnClickListener {
            showCategoryDialog()
        }

        view.findViewById<TextInputLayout>(R.id.til_search).setEndIconOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }

    private fun processImageWithVisionAI(uri: Uri) {
        val view = view ?: return
        val tvLabel = view.findViewById<TextView>(R.id.tv_list_label)
        
        viewModel.setVisionLoading(true)
        tvLabel.text = "Identifying with Vision AI..."

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val input = requireContext().contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(input)
                }

                if (bitmap == null) {
                    viewModel.setVisionLoading(false)
                    tvLabel.text = "Recent Food"
                    return@launch
                }

                // Switch back to Groq (Llama 3.2 Vision) as your Gemini API is blocked
                val result = identifyWithGroqVision(bitmap)
                
                viewModel.setVisionLoading(false)
                tvLabel.text = "Recent Food"

                if (result != null) {
                    val bundle = Bundle().apply {
                        putString("foodName", result.optString("name", "Unknown Object"))
                        putInt("calories", result.optInt("calories", 0))
                        putDouble("protein", result.optDouble("protein", 0.0))
                        putDouble("carbs", result.optDouble("carbs", 0.0))
                        putDouble("fat", result.optDouble("fat", 0.0))
                    }
                    findNavController().navigate(R.id.action_addFoodFragment_to_manualFoodFragment, bundle)
                } else {
                    Toast.makeText(requireContext(), "Could not identify object. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                viewModel.setVisionLoading(false)
                tvLabel.text = "Recent Food"
                Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun identifyWithGroqVision(bitmap: Bitmap): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val apiKey = ""

            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            // Higher resolution for better food recognition
            val maxDim = 1024
            val scale = Math.min(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height)
            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            
            val bos = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos) // Better quality
            val base64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)

            val json = JSONObject().apply {
                // Using Grok-3 Vision (xAI flagship vision model)
                put("model", "grok-3")
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "Analyze this image as a professional nutritionist. \n" +
                                        "1. Identify the specific food item(s) and estimate the portion size shown.\n" +
                                        "2. Calculate the total nutritional values for the entire portion visible.\n" +
                                        "3. Return ONLY a valid JSON object with keys: \"name\" (be descriptive, e.g. '2 Slices of Pepperoni Pizza'), \"calories\" (integer), \"protein\" (float, in grams), \"carbs\" (float, in grams), \"fat\" (float, in grams).\n" +
                                        "If the image does not contain food, return 0 for all nutritional values and 'Non-food object' for name.\n" +
                                        "Do not include any other text, markdown, or explanation.")
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64")
                                })
                            })
                        })
                    })
                })
                put("temperature", 0.1)
            }

            val request = Request.Builder()
                .url("https://api.x.ai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    android.util.Log.e("GroqVision", "API Error 400 Debug: $body")
                    return@withContext null
                }
                val content = JSONObject(body!!).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                
                // Manually parse JSON from content string
                val jsonStartIndex = content.indexOf("{")
                val jsonEndIndex = content.lastIndexOf("}") + 1
                if (jsonStartIndex != -1 && jsonEndIndex != -1) {
                    JSONObject(content.substring(jsonStartIndex, jsonEndIndex))
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GroqVision", "Exception: ${e.message}")
            null
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val skeleton = view?.findViewById<View>(R.id.layout_skeleton)
                    val mainContent = view?.findViewById<View>(R.id.main_content)
                    val shimmerContainer = view?.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_view_container)

                    if (state.isLoading) {
                        skeleton?.visibility = View.VISIBLE
                        mainContent?.visibility = View.GONE
                        shimmerContainer?.startShimmer()
                    } else {
                        skeleton?.visibility = View.GONE
                        mainContent?.visibility = View.VISIBLE
                        shimmerContainer?.stopShimmer()
                    }

                    adapter.updateData(state.searchResults)
                    view?.findViewById<ProgressBar>(R.id.pb_loading)?.visibility = 
                        if (state.isCalculating || state.isFoodLoading || state.isVisionLoading) View.VISIBLE else View.GONE
                    isLossMode = state.isWeightLoss
                    updateTheme(isLossMode)
                    
                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateTheme(isLoss: Boolean) {
        val view = view ?: return
        val primaryColor = ContextCompat.getColor(requireContext(), if (isLoss) R.color.accent_blue else R.color.accent_green)
        val colorStateList = ColorStateList.valueOf(primaryColor)

        view.findViewById<ProgressBar>(R.id.pb_loading)?.indeterminateTintList = colorStateList
        
        adapter.setThemeColor(primaryColor)
    }

    private fun showCategoryDialog() {
        val categories = viewModel.uiState.value.categories
        if (categories.isEmpty()) {
            viewModel.fetchCategories()
            Toast.makeText(requireContext(), "Fetching categories...", Toast.LENGTH_SHORT).show()
            return
        }

        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_categories_list, null)
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .create()

        val rvCategories = dialogView.findViewById<RecyclerView>(R.id.rv_categories)
        rvCategories.layoutManager = LinearLayoutManager(requireContext())
        
        val categoryAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val category = categories[position]
                val tvName = holder.itemView.findViewById<TextView>(R.id.tv_category_name)
                tvName.text = category
                holder.itemView.setOnClickListener {
                    viewModel.filterByCategory(category)
                    view?.findViewById<TextView>(R.id.tv_list_label)?.text = "$category Foods"
                    dialog.dismiss()
                }
            }
            override fun getItemCount() = categories.size
        }
        rvCategories.adapter = categoryAdapter

        dialogView.findViewById<View>(R.id.btn_close).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showFoodQuantityDialog(food: FoodItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_food, null)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme).setView(dialogView).create()

        val primaryColor = ContextCompat.getColor(requireContext(), if (isLossMode) R.color.accent_blue else R.color.accent_green)
        val csl = ColorStateList.valueOf(primaryColor)

        dialogView.findViewById<TextView>(R.id.tv_food_name).text = food.name
        dialogView.findViewById<TextView>(R.id.tv_food_info).text = "${food.calories} kcal | P: ${food.protein}g | C: ${food.carbs}g | F: ${food.fat}g"
        
        val ivFood = dialogView.findViewById<android.widget.ImageView>(R.id.iv_food_dialog)
        if (food.imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(food.imageUrl)
                .placeholder(R.drawable.calories)
                .error(R.drawable.calories)
                .into(ivFood)
        } else {
            ivFood.setImageResource(R.drawable.calories)
            ivFood.imageTintList = csl
        }

        val tvQty = dialogView.findViewById<TextView>(R.id.tv_quantity)
        var qty = 1.0

        val btnAdd = dialogView.findViewById<MaterialButton>(R.id.btn_add)
        btnAdd.backgroundTintList = csl

        dialogView.findViewById<View>(R.id.btn_increase).setOnClickListener { 
            qty += 0.5
            tvQty.text = qty.toString() 
        }
        dialogView.findViewById<View>(R.id.btn_decrease).setOnClickListener { 
            if (qty > 0.5) {
                qty -= 0.5
                tvQty.text = qty.toString()
            }
        }
        
        btnAdd.setOnClickListener {
            viewModel.addFoodToDaily(food, qty)
            dialog.dismiss()
            Toast.makeText(requireContext(), "Added ${food.name} x$qty", Toast.LENGTH_SHORT).show()
        }
        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

}
