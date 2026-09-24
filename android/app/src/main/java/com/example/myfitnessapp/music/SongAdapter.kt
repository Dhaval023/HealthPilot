package com.example.myfitnessapp.music

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myfitnessapp.R

class SongAdapter(
    private var songs: List<Song>,
    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var currentPlayingVideoId: String? = null

    fun updateSongs(newSongs: List<Song>, playingVideoId: String?) {
        songs = newSongs
        currentPlayingVideoId = playingVideoId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.bind(song, song.videoId == currentPlayingVideoId, onSongClick)
    }

    override fun getItemCount(): Int = songs.size

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivSongThumbnail)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvSongTitle)
        private val tvArtist: TextView = itemView.findViewById(R.id.tvSongArtist)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvSongDuration)

        fun bind(song: Song, isPlaying: Boolean, onClick: (Song) -> Unit) {
            tvTitle.text = song.title
            tvArtist.text = song.artist
            tvDuration.text = song.duration

            Glide.with(itemView.context)
                .load(song.thumbnailUrl)
                .placeholder(R.drawable.ic_music)
                .error(R.drawable.ic_music)
                .centerCrop()
                .into(ivThumbnail)

            if (isPlaying) {
                tvTitle.setTextColor(itemView.context.getColor(R.color.accent_lime))
            } else {
                tvTitle.setTextColor(itemView.context.getColor(R.color.white))
            }

            itemView.setOnClickListener { onClick(song) }
        }
    }
}
