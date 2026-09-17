package com.example.myfitnessapp.chat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentChatBinding

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            Toast.makeText(context, "Permission denied for voice input", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            setupUI()
            observeViewModel()
        }
    }

    private fun setupUI() {
        chatAdapter = ChatAdapter()
        binding.rvChat.adapter = chatAdapter

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString()
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
                binding.etMessage.text.clear()
            }
        }

        binding.btnMic.setOnClickListener {
            checkAndStartVoiceInput()
        }
    }

    private fun checkAndStartVoiceInput() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                val isListening = viewModel.isListening.value == true
                val isSpeaking = viewModel.isSpeaking.value == true
                
                if (isListening || isSpeaking) {
                    if (isListening) viewModel.stopListening()
                    if (isSpeaking) viewModel.stopSpeaking()
                } else {
                    viewModel.startListening()
                }
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    binding.rvChat.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val skeleton = binding.root.findViewById<View>(R.id.layout_skeleton)
            val mainContent = binding.root.findViewById<View>(R.id.main_content)
            val shimmerContainer = binding.root.findViewById<com.facebook.shimmer.ShimmerFrameLayout>(R.id.shimmer_view_container)

            // If it's the initial load (messages empty), show full page skeleton
            if (isLoading && chatAdapter.currentList.isEmpty()) {
                skeleton?.visibility = View.VISIBLE
                mainContent?.visibility = View.GONE
                shimmerContainer?.startShimmer()
            } else {
                skeleton?.visibility = View.GONE
                mainContent?.visibility = View.VISIBLE
                shimmerContainer?.stopShimmer()
            }

            binding.btnSend.isEnabled = !isLoading
            binding.etMessage.isEnabled = !isLoading
            binding.progressLoading.visibility = if (isLoading && chatAdapter.currentList.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.userName.observe(viewLifecycleOwner) { name ->
            binding.tvGreeting.text = getString(R.string.hello_name, name)
        }

        viewModel.userProfileImage.observe(viewLifecycleOwner) { profileImage ->
            chatAdapter.setUserProfileImage(profileImage)
        }

        viewModel.isListening.observe(viewLifecycleOwner) { updateMicButton() }
        viewModel.isSpeaking.observe(viewLifecycleOwner) { updateMicButton() }
    }

    private fun updateMicButton() {
        val isListening = viewModel.isListening.value == true
        val isSpeaking = viewModel.isSpeaking.value == true

        if (isListening || isSpeaking) {
            binding.btnMic.setIconResource(R.drawable.ic_stop_square)
            binding.btnMic.setIconTintResource(R.color.accent_red)
            binding.etMessage.hint = if (isListening) getString(R.string.listening) else getString(R.string.speaking)
        } else {
            binding.btnMic.setIconResource(R.drawable.ic_mic)
            binding.btnMic.setIconTintResource(R.color.white)
            binding.etMessage.hint = getString(R.string.type_a_message)
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopListening()
        viewModel.stopSpeaking()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
