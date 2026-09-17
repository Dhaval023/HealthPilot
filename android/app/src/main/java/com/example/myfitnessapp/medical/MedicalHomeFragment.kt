package com.example.myfitnessapp.medical

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentMedicalHomeBinding

class MedicalHomeFragment : Fragment() {

    private var _binding: FragmentMedicalHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicalHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnStartAssessment.setOnClickListener {
            navigateToAssessment(getString(R.string.cat_general_triage))
        }

        setupCategories()
    }

    private fun setupCategories() {
        val categories = listOf(
            MedicalCategory(getString(R.string.cat_general_triage), "🩺"),
            MedicalCategory(getString(R.string.cat_fever_cold), "🤒"),
            MedicalCategory(getString(R.string.cat_stomach_digestion), "🤢"),
            MedicalCategory(getString(R.string.cat_headache_neuro), "🧠"),
            MedicalCategory(getString(R.string.cat_muscle_joints), "💪"),
            MedicalCategory(getString(R.string.cat_skin_rash), "🏥")
        )

        binding.rvCategories.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvCategories.adapter = CategoryAdapter(categories) { category ->
            navigateToAssessment(category.name)
        }
    }

    private fun navigateToAssessment(categoryName: String) {
        val bundle = Bundle().apply {
            putString("category", categoryName)
        }
        findNavController().navigate(
            R.id.action_medicalHomeFragment_to_medicalAssessmentFragment,
            bundle
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class MedicalCategory(val name: String, val icon: String)

    private class CategoryAdapter(
        private val list: List<MedicalCategory>,
        private val onClick: (MedicalCategory) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvIcon: TextView = view.findViewById(R.id.tv_category_icon)
            val tvName: TextView = view.findViewById(R.id.tv_category_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_medical_category, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvIcon.text = item.icon
            holder.tvName.text = item.name
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }
}
