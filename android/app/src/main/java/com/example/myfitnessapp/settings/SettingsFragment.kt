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
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HealthGoalViewModel by activityViewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (requireActivity().application as HealthPilot).repository
                return HealthGoalViewModel(requireActivity().application, repository) as T
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

        binding.cardVoiceArya.setOnClickListener { viewModel.updateAssistantVoice("Arya") }
        binding.cardVoiceVed.setOnClickListener { viewModel.updateAssistantVoice("Ved") }

        setupLanguageSelector()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.rbWeightGain.isChecked = !state.isWeightLoss
                    binding.rbWeightLoss.isChecked = state.isWeightLoss
                    setupTuneSelector(state.alarmTune)
                    setupVoiceSelector(state.assistantVoice)
                }
            }
        }
    }

    private fun setupVoiceSelector(currentVoice: String) {
        val context = requireContext()
        val isArya = currentVoice.equals("Arya", ignoreCase = true)
        val isVed = currentVoice.equals("Ved", ignoreCase = true)

        if (isArya) {
            binding.cardVoiceArya.setStrokeColor(ContextCompat.getColor(context, R.color.accent_blue))
            binding.cardVoiceArya.setCardBackgroundColor(ContextCompat.getColor(context, R.color.accent_blue_transparent))
            binding.ivCheckArya.visibility = View.VISIBLE
            binding.tvVoiceAryaTitle.setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
        } else {
            binding.cardVoiceArya.setStrokeColor(ContextCompat.getColor(context, R.color.card_border))
            binding.cardVoiceArya.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_dark))
            binding.ivCheckArya.visibility = View.GONE
            binding.tvVoiceAryaTitle.setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        if (isVed) {
            binding.cardVoiceVed.setStrokeColor(ContextCompat.getColor(context, R.color.accent_blue))
            binding.cardVoiceVed.setCardBackgroundColor(ContextCompat.getColor(context, R.color.accent_blue_transparent))
            binding.ivCheckVed.visibility = View.VISIBLE
            binding.tvVoiceVedTitle.setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
        } else {
            binding.cardVoiceVed.setStrokeColor(ContextCompat.getColor(context, R.color.card_border))
            binding.cardVoiceVed.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_dark))
            binding.ivCheckVed.visibility = View.GONE
            binding.tvVoiceVedTitle.setTextColor(ContextCompat.getColor(context, R.color.white))
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

    private fun setupLanguageSelector() {
        val currentLang = AppCompatDelegate.getApplicationLocales().get(0)?.language ?: Locale.getDefault().language
        val context = requireContext()

        // English Card styling
        if (currentLang == "en") {
            binding.cardLangEn.setStrokeColor(ContextCompat.getColor(context, R.color.accent_blue))
            binding.cardLangEn.setCardBackgroundColor(ContextCompat.getColor(context, R.color.accent_blue_transparent))
            binding.ivCheckLangEn.visibility = View.VISIBLE
            binding.tvLangEnTitle.setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
        } else {
            binding.cardLangEn.setStrokeColor(ContextCompat.getColor(context, R.color.card_border))
            binding.cardLangEn.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_dark))
            binding.ivCheckLangEn.visibility = View.GONE
            binding.tvLangEnTitle.setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        // Hindi Card styling
        if (currentLang == "hi") {
            binding.cardLangHi.setStrokeColor(ContextCompat.getColor(context, R.color.accent_blue))
            binding.cardLangHi.setCardBackgroundColor(ContextCompat.getColor(context, R.color.accent_blue_transparent))
            binding.ivCheckLangHi.visibility = View.VISIBLE
            binding.tvLangHiTitle.setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
        } else {
            binding.cardLangHi.setStrokeColor(ContextCompat.getColor(context, R.color.card_border))
            binding.cardLangHi.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_dark))
            binding.ivCheckLangHi.visibility = View.GONE
            binding.tvLangHiTitle.setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        // Gujarati Card styling
        if (currentLang == "gu") {
            binding.cardLangGu.setStrokeColor(ContextCompat.getColor(context, R.color.accent_blue))
            binding.cardLangGu.setCardBackgroundColor(ContextCompat.getColor(context, R.color.accent_blue_transparent))
            binding.ivCheckLangGu.visibility = View.VISIBLE
            binding.tvLangGuTitle.setTextColor(ContextCompat.getColor(context, R.color.accent_blue))
        } else {
            binding.cardLangGu.setStrokeColor(ContextCompat.getColor(context, R.color.card_border))
            binding.cardLangGu.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_dark))
            binding.ivCheckLangGu.visibility = View.GONE
            binding.tvLangGuTitle.setTextColor(ContextCompat.getColor(context, R.color.white))
        }

        binding.cardLangEn.setOnClickListener {
            changeLanguage("en")
        }
        binding.cardLangHi.setOnClickListener {
            changeLanguage("hi")
        }
        binding.cardLangGu.setOnClickListener {
            changeLanguage("gu")
        }
    }

    private fun changeLanguage(langCode: String) {
        val appLocale = LocaleListCompat.forLanguageTags(langCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        _binding = null
    }
}
