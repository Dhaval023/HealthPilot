package com.example.myfitnessapp.settings

import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.myfitnessapp.HealthPilot
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentSettingsBinding
import com.example.myfitnessapp.healthgoal.HealthGoalViewModel
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthGoalViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (requireActivity().application as HealthPilot).repository
                return HealthGoalViewModel(repository) as T
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null

    private val alarmTunes = listOf(
        Pair("Default", "default"),
        Pair("Tune 1", "tune1"),
        Pair("Tune 2", "tune2"),
        Pair("Tune 3", "tune3"),
        Pair("Tune 4", "tune4"),
        Pair("Tune 5", "tune5"),
        Pair("Tune 6", "tune6")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()

        binding.cardWeightGain.setOnClickListener { viewModel.setGoalType(false) }
        binding.cardWeightLoss.setOnClickListener { viewModel.setGoalType(true) }
        binding.btnBack.setOnClickListener { findNavController().navigateUp() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.rbWeightGain.isChecked = !state.isWeightLoss
                    binding.rbWeightLoss.isChecked = state.isWeightLoss
                    setupTuneSelector(state.alarmTune)
                }
            }
        }
    }

    private fun setupTuneSelector(currentTune: String) {
        binding.llTunesContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        alarmTunes.forEach { (name, id) ->
            val tuneView = inflater.inflate(R.layout.item_tune_card, binding.llTunesContainer, false)
            val card = tuneView.findViewById<MaterialCardView>(R.id.tune_card)
            val tvName = tuneView.findViewById<TextView>(R.id.tv_tune_name)
            val ivSpeaker = tuneView.findViewById<ImageView>(R.id.iv_speaker)

            tvName.text = name
            val isSelected = id == currentTune

            if (isSelected) {
                card.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.accent_blue))
                card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.accent_blue_transparent))
                tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_blue))
                ivSpeaker.setColorFilter(ContextCompat.getColor(requireContext(), R.color.accent_blue))
                // Change icon to show active selection
                ivSpeaker.setImageResource(android.R.drawable.ic_lock_silent_mode_off) 
            } else {
                card.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.card_border))
                card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.card_dark))
                tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_gray))
                ivSpeaker.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_gray))
            }

            card.setOnClickListener {
                viewModel.updateAlarmTune(id)
                playTunePreview(id)
            }

            binding.llTunesContainer.addView(tuneView)
        }
    }

    private val stopPreviewRunnable = Runnable {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playTunePreview(tune: String) {
        // Cancel any pending stop
        view?.removeCallbacks(stopPreviewRunnable)
        
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (tune == "default") return

        val resId = resources.getIdentifier(tune, "raw", requireContext().packageName)
        if (resId != 0) {
            mediaPlayer = MediaPlayer.create(requireContext(), resId)
            mediaPlayer?.start()
            
            // Stop after 5 seconds
            view?.postDelayed(stopPreviewRunnable, 5000)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        _binding = null
    }
}
