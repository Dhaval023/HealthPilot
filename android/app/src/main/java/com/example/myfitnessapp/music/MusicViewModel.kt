package com.example.myfitnessapp.music

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val apiKey = "AIzaSyDNN0RqOVBR7GwjFJaXJKzHKtkb0VY2NC0"
    private val client = OkHttpClient()

    @SuppressLint("StaticFieldLeak")
    var youtubePlayerView: YouTubePlayerView? = null
    var youtubePlayer: YouTubePlayer? = null
    var currentVideoId: String? = null
    var currentDuration: Float = 0f
    var currentSecond: Float = 0f

    private val _filteredSongs = MutableStateFlow<List<Song>>(emptyList())
    val filteredSongs: StateFlow<List<Song>> = _filteredSongs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    init {
        searchSongs("Hindi Bollywood workout songs")
    }

    private fun parseIsoDuration(isoDuration: String): String {
        try {
            var hours = 0
            var minutes = 0
            var seconds = 0
            val tIndex = isoDuration.indexOf('T')
            if (tIndex == -1) return "3:45"
            val timePart = isoDuration.substring(tIndex + 1)
            
            val hIndex = timePart.indexOf('H')
            if (hIndex != -1) {
                hours = timePart.substring(0, hIndex).toInt()
                val mIndex = timePart.indexOf('M', hIndex)
                if (mIndex != -1) {
                    minutes = timePart.substring(hIndex + 1, mIndex).toInt()
                    val sIndex = timePart.indexOf('S', mIndex)
                    if (sIndex != -1) {
                        seconds = timePart.substring(mIndex + 1, sIndex).toInt()
                    }
                } else {
                    val sIndex = timePart.indexOf('S', hIndex)
                    if (sIndex != -1) {
                        seconds = timePart.substring(hIndex + 1, sIndex).toInt()
                    }
                }
            } else {
                val mIndex = timePart.indexOf('M')
                if (mIndex != -1) {
                    minutes = timePart.substring(0, mIndex).toInt()
                    val sIndex = timePart.indexOf('S', mIndex)
                    if (sIndex != -1) {
                        seconds = timePart.substring(mIndex + 1, sIndex).toInt()
                    }
                } else {
                    val sIndex = timePart.indexOf('S')
                    if (sIndex != -1) {
                        seconds = timePart.substring(0, sIndex).toInt()
                    }
                }
            }
            
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        } catch (_: Exception) {
            return "3:45"
        }
    }

    fun searchSongs(query: String) {
        val searchQuery = if (query.isBlank()) "Hindi Bollywood workout songs" else "$query hindi songs"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val searchUrl = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=30&q=${URLEncoder.encode(searchQuery, "UTF-8")}&key=$apiKey"
                val searchRequest = Request.Builder().url(searchUrl).build()
                client.newCall(searchRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val items = json.getJSONArray("items")
                        val videoIdsList = mutableListOf<String>()
                        val songMap = mutableMapOf<String, Song>()

                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            val idObj = item.getJSONObject("id")
                            if (!idObj.has("videoId")) continue
                            val videoId = idObj.getString("videoId")
                            val snippet = item.getJSONObject("snippet")
                            val title = snippet.getString("title")
                                .replace("&amp;", "&")
                                .replace("&#39;", "'")
                                .replace("&quot;", "\"")
                            val channelTitle = snippet.getString("channelTitle")
                            val thumbnails = snippet.getJSONObject("thumbnails")
                            val thumbUrl = if (thumbnails.has("high")) {
                                thumbnails.getJSONObject("high").getString("url")
                            } else {
                                thumbnails.getJSONObject("default").getString("url")
                            }

                            val song = Song(
                                id = videoId,
                                videoId = videoId,
                                title = title,
                                artist = channelTitle,
                                duration = "3:45",
                                thumbnailUrl = thumbUrl
                            )
                            songMap[videoId] = song
                            videoIdsList.add(videoId)
                        }

                        if (videoIdsList.isNotEmpty()) {
                            val idsParam = videoIdsList.joinToString(",")
                            val detailsUrl = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id=$idsParam&key=$apiKey"
                            val detailsRequest = Request.Builder().url(detailsUrl).build()
                            client.newCall(detailsRequest).execute().use { detailsResponse ->
                                if (detailsResponse.isSuccessful) {
                                    val detailsBody = detailsResponse.body?.string()
                                    if (detailsBody != null) {
                                        val detailsJson = JSONObject(detailsBody)
                                        val detailsItems = detailsJson.getJSONArray("items")
                                        for (j in 0 until detailsItems.length()) {
                                            val dItem = detailsItems.getJSONObject(j)
                                            val vId = dItem.getString("id")
                                            val contentDetails = dItem.getJSONObject("contentDetails")
                                            val durationIso = contentDetails.getString("duration")
                                            val formattedDuration = parseIsoDuration(durationIso)
                                            
                                            songMap[vId]?.let { originalSong ->
                                                songMap[vId] = originalSong.copy(duration = formattedDuration)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val songsList = videoIdsList.mapNotNull { songMap[it] }
                        _filteredSongs.value = songsList
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playSong(song: Song) {
        _currentSong.value = song
        _isPlaying.value = true
        if (currentVideoId != song.videoId) {
            currentVideoId = song.videoId
            currentDuration = 0f
            currentSecond = 0f
            youtubePlayer?.loadVideo(song.videoId, 0f)
        } else {
            youtubePlayer?.play()
        }
    }

    fun togglePlayPause() {
        val playing = !_isPlaying.value
        _isPlaying.value = playing
        if (playing) {
            youtubePlayer?.play()
        } else {
            youtubePlayer?.pause()
        }
    }

    fun nextSong() {
        val current = _currentSong.value ?: return
        val list = if (_isShuffle.value) _filteredSongs.value.shuffled() else _filteredSongs.value
        val index = list.indexOfFirst { it.videoId == current.videoId }
        if (index != -1 && index < list.size - 1) {
            playSong(list[index + 1])
        } else if (list.isNotEmpty()) {
            playSong(list[0])
        }
    }

    fun previousSong() {
        val current = _currentSong.value ?: return
        val list = if (_isShuffle.value) _filteredSongs.value.shuffled() else _filteredSongs.value
        val index = list.indexOfFirst { it.videoId == current.videoId }
        if (index > 0) {
            playSong(list[index - 1])
        } else if (list.isNotEmpty()) {
            playSong(list[list.size - 1])
        }
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        _isRepeat.value = !_isRepeat.value
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    override fun onCleared() {
        super.onCleared()
        try {
            youtubePlayerView?.release()
        } catch (_: Exception) {
        }
        youtubePlayerView = null
        youtubePlayer = null
    }
}
