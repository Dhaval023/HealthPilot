package com.example.myfitnessapp.medical

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentMedicalAssessmentBinding
import com.example.myfitnessapp.models.medical.MedicalQuestion
import com.example.myfitnessapp.viewmodel.MedicalViewModel
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MedicalAssessmentFragment : Fragment() {

    private var _binding: FragmentMedicalAssessmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MedicalViewModel by activityViewModels()
    private var selectedOption: String? = null
    private var category: String = "General"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicalAssessmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        category = arguments?.getString("category") ?: "General"

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            showExitConfirmationDialog()
        }

        binding.btnBackAssessment.setOnClickListener {
            showExitConfirmationDialog()
        }

        // Initial State: Asking for the symptom
        binding.tvQuestion.text = getString(R.string.symptoms_question)
        binding.tvSubtitle.text = getString(R.string.symptoms_subtitle)
        binding.etInput.visibility = View.VISIBLE
        binding.rvOptions.visibility = View.GONE
        binding.progressIndicator.progress = 10
        binding.tvProgressLabel.text = getString(R.string.step_initial_complaint)

        binding.btnContinue.setOnClickListener {
            val currentAssessment = viewModel.assessment.value
            if (currentAssessment == null || currentAssessment.currentQuestion == null) {
                // Submit initial complaint
                val complaint = binding.etInput.text.toString().trim()
                if (complaint.isEmpty()) {
                    Toast.makeText(context, getString(R.string.enter_symptoms_error), Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.startAssessment(complaint, category)
                }
            } else {
                // Submit answer to current question
                val question = currentAssessment.currentQuestion!!
                if (question.options.isNotEmpty()) {
                    if (selectedOption == null) {
                        Toast.makeText(context, getString(R.string.select_option_error), Toast.LENGTH_SHORT).show()
                    } else if (selectedOption!!.contains("Other", ignoreCase = true)) {
                        val customAns = binding.etInput.text.toString().trim()
                        if (customAns.isEmpty()) {
                            Toast.makeText(context, getString(R.string.specify_other_error), Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.submitAnswer("Other: $customAns")
                        }
                    } else {
                        viewModel.submitAnswer(selectedOption!!)
                    }
                } else {
                    val ans = binding.etInput.text.toString().trim()
                    if (ans.isEmpty() && question.required) {
                        Toast.makeText(context, getString(R.string.enter_answer_error), Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.submitAnswer(ans)
                    }
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.assessment.observe(viewLifecycleOwner) { assessment ->
            if (assessment == null) return@observe

            if (assessment.finalAssessment != null) {
                findNavController().navigate(R.id.action_medicalAssessmentFragment_to_medicalResultFragment)
                return@observe
            }

            val question = assessment.currentQuestion
            if (question != null) {
                displayQuestion(question, assessment.answers.size)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.isEmergency.observe(viewLifecycleOwner) { isEmergency ->
            if (isEmergency) {
                showEmergencyDialog()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun displayQuestion(question: MedicalQuestion, answerCount: Int) {
        binding.tvQuestion.text = question.question
        binding.tvSubtitle.text = if (question.options.isNotEmpty()) getString(R.string.select_option_subtitle) else getString(R.string.type_response_subtitle)
        selectedOption = null
        binding.etInput.setText("")
        binding.etInput.visibility = View.GONE

        // Update progress indicators
        val stepNumber = answerCount + 2
        binding.tvProgressLabel.text = getString(R.string.question_number_format, answerCount)
        binding.progressIndicator.progress = (stepNumber * 15).coerceAtMost(100)

        if (question.options.isNotEmpty()) {
            binding.rvOptions.visibility = View.VISIBLE
            binding.rvOptions.layoutManager = LinearLayoutManager(requireContext())
            binding.rvOptions.adapter = OptionsAdapter(question.options) { option ->
                selectedOption = option
                if (option.contains("Other", ignoreCase = true)) {
                    binding.etInput.visibility = View.VISIBLE
                    binding.etInput.hint = getString(R.string.specify_other_error)
                } else {
                    binding.etInput.visibility = View.GONE
                }
            }
        } else {
            binding.etInput.visibility = View.VISIBLE
            binding.etInput.hint = getString(R.string.type_response_hint)
            binding.rvOptions.visibility = View.GONE
        }
    }

    private fun showEmergencyDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.emergency_alert_title))
            .setMessage(getString(R.string.emergency_alert_msg))
            .setCancelable(false)
            .setPositiveButton(getString(R.string.i_understand)) { dialog, _ ->
                dialog.dismiss()
                viewModel.resetAssessment()
                findNavController().navigateUp()
            }
            .show()
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.cancel_assessment_title))
            .setMessage(getString(R.string.cancel_assessment_msg))
            .setPositiveButton(getString(R.string.exit)) { _, _ ->
                viewModel.resetAssessment()
                findNavController().navigateUp()
            }
            .setNegativeButton(getString(R.string.resume), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class OptionsAdapter(
        private val options: List<String>,
        private val onOptionSelected: (String) -> Unit
    ) : RecyclerView.Adapter<OptionsAdapter.ViewHolder>() {

        private var selectedPosition = -1

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cardView: MaterialCardView = view.findViewById(R.id.card_option)
            val tvText: TextView = view.findViewById(R.id.tv_option_text)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_medical_option, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val text = options[position]
            holder.tvText.text = text

            val context = holder.itemView.context
            if (position == selectedPosition) {
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.accent_blue))
                holder.cardView.strokeColor = ContextCompat.getColor(context, R.color.white)
            } else {
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_dark))
                holder.cardView.strokeColor = ContextCompat.getColor(context, R.color.card_border)
            }

            holder.itemView.setOnClickListener {
                val previousSelected = selectedPosition
                selectedPosition = holder.bindingAdapterPosition
                notifyItemChanged(previousSelected)
                notifyItemChanged(selectedPosition)
                onOptionSelected(text)
            }
        }

        override fun getItemCount() = options.size
    }
}
