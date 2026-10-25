package com.zty.voiceintelligence.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zty.voiceintelligence.data.entity.ChatMessageEntity
import com.zty.voiceintelligence.databinding.ItemChatMessageBinding

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.Holder>() {
    private val items = mutableListOf<ChatMessageEntity>()

    fun submitList(newList: List<ChatMessageEntity>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    inner class Holder(val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.tvRoleHeader.text = if (item.isUser) "我 (语音/文本指令)" else "智音助手 (解析引擎响应)"
        holder.binding.tvMessageBody.text = item.text
        if (item.extractedSlotsJson.isNullOrBlank()) {
            holder.binding.tvEngineSlotBadge.visibility = View.GONE
        } else {
            holder.binding.tvEngineSlotBadge.visibility = View.VISIBLE
            holder.binding.tvEngineSlotBadge.text = item.extractedSlotsJson
        }
    }
}
