package com.example.myfitnessapp.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myfitnessapp.R
import com.example.myfitnessapp.models.ChatMessage

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DiffCallback()) {

    private var userProfileImageBase64: String? = null

    fun setUserProfileImage(urlOrBase64: String?) {
        userProfileImageBase64 = urlOrBase64
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layout = if (viewType == VIEW_TYPE_USER) R.layout.item_chat_user else R.layout.item_chat_ai
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position), userProfileImageBase64)
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMessage: TextView = view.findViewById(R.id.tv_message)
        private val ivProfile: ImageView? = view.findViewById(R.id.iv_profile)

        fun bind(message: ChatMessage, profileBase64: String?) {
            tvMessage.text = message.text
            
            if (message.isUser && ivProfile != null && !profileBase64.isNullOrEmpty()) {
                if (profileBase64.startsWith("http")) {
                    Glide.with(itemView.context)
                        .load(profileBase64)
                        .circleCrop()
                        .into(ivProfile)
                } else {
                    val bitmap = base64ToBitmap(profileBase64)
                    if (bitmap != null) {
                        ivProfile.setImageBitmap(bitmap)
                    } else {
                        ivProfile.setImageResource(R.drawable.profile)
                    }
                }
            }
        }

        private fun base64ToBitmap(base64Str: String): Bitmap? {
            return try {
                val base64Data = if (base64Str.contains(",")) {
                    base64Str.substring(base64Str.indexOf(",") + 1)
                } else {
                    base64Str
                }
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean = oldItem.timestamp == newItem.timestamp
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean = oldItem == newItem
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }
}
