package com.example.myfitnessapp.music

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myfitnessapp.MainActivity
import com.example.myfitnessapp.R
import com.example.myfitnessapp.databinding.FragmentMusicBinding
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MusicFragment : Fragment() {

    private var _binding: FragmentMusicBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MusicViewModel by activityViewModels()
    private lateinit var songAdapter: SongAdapter
    private var youtubePlayer: YouTubePlayer? = null
    private var isUserSeeking = false
    private var youtubePlayerListener: AbstractYouTubePlayerListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (viewModel.youtubePlayerView == null) {
            viewModel.youtubePlayerView = YouTubePlayerView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isClickable = true
                isFocusable = true
                setOnTouchListener { v, _ ->
                    v.performClick()
                    true
                }
            }
            // Bind lifecycle to Activity lifecycle so navigating away from Fragment does NOT pause video
            requireActivity().lifecycle.addObserver(viewModel.youtubePlayerView!!)

            // Global listener to handle player state & video auto-advancing across all screens
            viewModel.youtubePlayerView?.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayerInstance: YouTubePlayer) {
                    viewModel.youtubePlayer = youTubePlayerInstance
                    viewModel.currentSong.value?.let { song ->
                        youTubePlayerInstance.loadVideo(song.videoId, 0f)
                    }
                }

                override fun onStateChange(
                    youTubePlayer: YouTubePlayer,
                    state: PlayerConstants.PlayerState
                ) {
                    super.onStateChange(youTubePlayer, state)
                    when (state) {
                        PlayerConstants.PlayerState.PLAYING -> viewModel.setPlaying(true)
                        PlayerConstants.PlayerState.PAUSED -> viewModel.setPlaying(false)
                        PlayerConstants.PlayerState.ENDED -> viewModel.nextSong()
                        else -> {}
                    }
                }

                override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                    viewModel.currentDuration = duration
                }

                override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                    viewModel.currentSecond = second
                }
            })
        }

        // Remove from previous parent (global container or old layout)
        (viewModel.youtubePlayerView?.parent as? ViewGroup)?.removeView(viewModel.youtubePlayerView)

        // Add player view to container
        binding.youtubePlayerContainer.addView(viewModel.youtubePlayerView)

        // Add transparent touch blocker view ON TOP of youtubePlayerView
        val touchBlocker = View(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            setOnTouchListener { v, _ ->
                v.performClick()
                true
            }
        }
        binding.youtubePlayerContainer.addView(touchBlocker)

        setupYouTubePlayer()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupYouTubePlayer() {
        viewModel.youtubePlayer?.let { player ->
            youtubePlayer = player
        }

        // Immediately restore cached duration and elapsed progress to avoid SeekBar defaulting max to 100
        if (viewModel.currentDuration > 0f) {
            binding.seekBarSong.max = viewModel.currentDuration.toInt()
            binding.tvTotalDuration.text = formatSeconds(viewModel.currentDuration.toInt())
            binding.seekBarSong.progress = viewModel.currentSecond.toInt()
            binding.tvCurrentTime.text = formatSeconds(viewModel.currentSecond.toInt())
        }

        youtubePlayerListener?.let { oldListener ->
            viewModel.youtubePlayerView?.removeYouTubePlayerListener(oldListener)
        }

        val listener = object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayerInstance: YouTubePlayer) {
                viewModel.youtubePlayer = youTubePlayerInstance
                youtubePlayer = youTubePlayerInstance
            }

            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                viewModel.currentSecond = second
                if (!isUserSeeking) {
                    _binding?.let { binding ->
                        if (viewModel.currentDuration > 0f && binding.seekBarSong.max != viewModel.currentDuration.toInt()) {
                            binding.seekBarSong.max = viewModel.currentDuration.toInt()
                            binding.tvTotalDuration.text = formatSeconds(viewModel.currentDuration.toInt())
                        }
                        binding.seekBarSong.progress = second.toInt()
                        binding.tvCurrentTime.text = formatSeconds(second.toInt())
                    }
                }
            }

            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                viewModel.currentDuration = duration
                _binding?.let { binding ->
                    binding.seekBarSong.max = duration.toInt()
                    binding.tvTotalDuration.text = formatSeconds(duration.toInt())
                }
            }
        }

        youtubePlayerListener = listener
        viewModel.youtubePlayerView?.addYouTubePlayerListener(listener)
    }

    private fun formatSeconds(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(emptyList()) { song ->
            viewModel.playSong(song)
        }
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }
    }

    private fun setupListeners() {
        binding.etSearchSongs.addTextChangedListener(object : TextWatcher {
            private var searchJob: Job? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(500L) // debounce
                    viewModel.searchSongs(s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnPlayPause.setOnClickListener {
            viewModel.togglePlayPause()
        }

        binding.btnNext.setOnClickListener {
            viewModel.nextSong()
        }

        binding.btnPrevious.setOnClickListener {
            viewModel.previousSong()
        }

        binding.btnShuffle.setOnClickListener {
            viewModel.toggleShuffle()
        }

        binding.btnRepeat.setOnClickListener {
            viewModel.toggleRepeat()
        }

        binding.seekBarSong.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = formatSeconds(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                seekBar?.let {
                    youtubePlayer?.seekTo(it.progress.toFloat())
                }
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredSongs.collectLatest { songs ->
                        val current = viewModel.currentSong.value
                        songAdapter.updateSongs(songs, current?.videoId)
                    }
                }
                launch {
                    viewModel.currentSong.collectLatest { song ->
                        val binding = _binding ?: return@collectLatest
                        if (song != null) {
                            binding.tvCurrentTitle.text = song.title
                            binding.tvCurrentArtist.text = song.artist
                            if (viewModel.currentVideoId != song.videoId) {
                                viewModel.currentVideoId = song.videoId
                                youtubePlayer?.loadVideo(song.videoId, 0f)
                            }
                            val current = viewModel.filteredSongs.value
                            songAdapter.updateSongs(current, song.videoId)
                        } else {
                            binding.tvCurrentTitle.text = getString(R.string.music_select_song_title)
                            binding.tvCurrentArtist.text = getString(R.string.music_select_song_artist)
                        }
                    }
                }
                launch {
                    viewModel.isPlaying.collectLatest { isPlaying ->
                        val binding = _binding ?: return@collectLatest
                        if (isPlaying) {
                            binding.ivPlayPauseIcon.setImageResource(R.drawable.ic_pause_circle)
                        } else {
                            binding.ivPlayPauseIcon.setImageResource(R.drawable.ic_play)
                        }
                    }
                }
                launch {
                    viewModel.isShuffle.collectLatest { isShuffle ->
                        val binding = _binding ?: return@collectLatest
                        val ctx = context ?: return@collectLatest
                        val tint = if (isShuffle) ctx.getColor(R.color.accent_lime) else ctx.getColor(R.color.text_gray)
                        binding.btnShuffle.setColorFilter(tint)
                    }
                }
                launch {
                    viewModel.isRepeat.collectLatest { isRepeat ->
                        val binding = _binding ?: return@collectLatest
                        val ctx = context ?: return@collectLatest
                        val tint = if (isRepeat) ctx.getColor(R.color.accent_lime) else ctx.getColor(R.color.text_gray)
                        binding.btnRepeat.setColorFilter(tint)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        youtubePlayerListener?.let { listener ->
            viewModel.youtubePlayerView?.removeYouTubePlayerListener(listener)
            youtubePlayerListener = null
        }
        (activity as? MainActivity)?.let { mainActivity ->
            (viewModel.youtubePlayerView?.parent as? ViewGroup)?.removeView(viewModel.youtubePlayerView)
            mainActivity.globalYouTubeContainer.addView(viewModel.youtubePlayerView)
        }
        _binding = null
    }
}
