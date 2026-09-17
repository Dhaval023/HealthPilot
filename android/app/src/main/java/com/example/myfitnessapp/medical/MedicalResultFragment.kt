package com.example.myfitnessapp.medical

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentMedicalResultBinding
import com.example.myfitnessapp.models.medical.MedicalAssessmentResult
import com.example.myfitnessapp.viewmodel.MedicalViewModel

class MedicalResultFragment : Fragment() {

    private var _binding: FragmentMedicalResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MedicalViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicalResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnClose.setOnClickListener {
            navigateToDashboard()
        }

        binding.btnDone.setOnClickListener {
            navigateToDashboard()
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.assessment.observe(viewLifecycleOwner) { assessment ->
            val result = assessment?.finalAssessment ?: return@observe
            displayResult(result)
            
            binding.btnShare.setOnClickListener {
                shareResultText(result)
            }
        }
    }

    private fun displayResult(result: MedicalAssessmentResult) {
        binding.tvSummary.text = result.summary

        // Setup Urgency Banner
        when (result.urgency.lowercase()) {
            "emergency" -> {
                binding.layoutUrgencyBg.setBackgroundColor(Color.parseColor("#B71C1C"))
                binding.tvUrgencyTitle.text = getString(R.string.urgency_emergency_title)
                binding.tvUrgencyDesc.text = getString(R.string.urgency_emergency_desc)
                binding.ivUrgencyIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            }
            "urgent" -> {
                binding.layoutUrgencyBg.setBackgroundColor(Color.parseColor("#E65100"))
                binding.tvUrgencyTitle.text = getString(R.string.urgency_urgent_title)
                binding.tvUrgencyDesc.text = getString(R.string.urgency_urgent_desc)
                binding.ivUrgencyIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            }
            "doctor_soon" -> {
                binding.layoutUrgencyBg.setBackgroundColor(Color.parseColor("#F57F17"))
                binding.tvUrgencyTitle.text = getString(R.string.urgency_doctor_soon_title)
                binding.tvUrgencyDesc.text = getString(R.string.urgency_doctor_soon_desc)
                binding.ivUrgencyIcon.setImageResource(android.R.drawable.ic_dialog_info)
            }
            "self_care" -> {
                binding.layoutUrgencyBg.setBackgroundColor(Color.parseColor("#006064"))
                binding.tvUrgencyTitle.text = getString(R.string.urgency_self_care_title)
                binding.tvUrgencyDesc.text = getString(R.string.urgency_self_care_desc)
                binding.ivUrgencyIcon.setImageResource(android.R.drawable.ic_dialog_info)
            }
            else -> {
                binding.layoutUrgencyBg.setBackgroundColor(Color.parseColor("#263238"))
                binding.tvUrgencyTitle.text = getString(R.string.urgency_routine_title)
                binding.tvUrgencyDesc.text = getString(R.string.urgency_routine_desc)
                binding.ivUrgencyIcon.setImageResource(android.R.drawable.ic_dialog_info)
            }
        }

        // Setup Dynamic Sections
        binding.layoutSections.removeAllViews()

        addSectionList(getString(R.string.section_explanations), result.possibleExplanations)
        addSectionList(getString(R.string.section_guidance), result.generalGuidance)
        addSectionList(getString(R.string.section_medication), result.medicationInformation)
        
        if (result.doctorRecommendation.isNotBlank()) {
            addSectionText(getString(R.string.section_doctor_rec), result.doctorRecommendation)
        }
        
        addSectionList(getString(R.string.section_warning_signs), result.warningSigns)
    }

    private fun addSectionList(title: String, items: List<String>) {
        if (items.isEmpty()) return

        val context = requireContext()
        val titleView = TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
        }
        binding.layoutSections.addView(titleView)

        for (item in items) {
            val itemView = TextView(context).apply {
                text = "• $item"
                textSize = 14f
                setTextColor(Color.parseColor("#B0BEC5"))
                setPadding(8.dpToPx(), 4.dpToPx(), 0, 4.dpToPx())
                setLineSpacing(0f, 1.1f)
            }
            binding.layoutSections.addView(itemView)
        }
    }

    private fun addSectionText(title: String, description: String) {
        val context = requireContext()
        val titleView = TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16.dpToPx(), 0, 8.dpToPx())
        }
        binding.layoutSections.addView(titleView)

        val descView = TextView(context).apply {
            text = description
            textSize = 14f
            setTextColor(Color.parseColor("#B0BEC5"))
            setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
            setLineSpacing(0f, 1.1f)
        }
        binding.layoutSections.addView(descView)
    }

    private fun shareResultText(result: MedicalAssessmentResult) {
        val shareBody = """
            ${getString(R.string.share_report_header)}
            ------------------------------------
            ${getString(R.string.share_urgency_label, result.urgency.uppercase())}
            
            ${getString(R.string.summary_label)}:
            ${result.summary}
            
            ${getString(R.string.section_doctor_rec)}:
            ${result.doctorRecommendation}
            
            ${getString(R.string.share_disclaimer)}
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_report_subject))
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
    }

    private fun navigateToDashboard() {
        viewModel.resetAssessment()
        findNavController().navigate(R.id.dashboardFragment)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
