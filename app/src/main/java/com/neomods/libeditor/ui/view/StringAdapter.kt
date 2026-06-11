package com.neomods.libeditor.ui.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.neomods.libeditor.R
import com.neomods.libeditor.model.ExtractedString

class StringAdapter(
    private val onStringClick: (ExtractedString) -> Unit
) : ListAdapter<ExtractedString, StringAdapter.StringViewHolder>(StringDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StringViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_string, parent, false)
        return StringViewHolder(view)
    }

    override fun onBindViewHolder(holder: StringViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StringViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stringValue: TextView = itemView.findViewById(R.id.stringValue)
        private val stringOffset: TextView = itemView.findViewById(R.id.stringOffset)
        private val stringEncoding: TextView = itemView.findViewById(R.id.stringEncoding)
        private val stringLength: TextView = itemView.findViewById(R.id.stringLength)

        fun bind(string: ExtractedString) {
            stringValue.text = string.value
            stringOffset.text = "0x${string.offset.toString(16).uppercase()}"
            stringEncoding.text = string.encoding.displayName
            stringLength.text = "${string.length}B"

            itemView.setOnClickListener { onStringClick(string) }
        }
    }

    class StringDiffCallback : DiffUtil.ItemCallback<ExtractedString>() {
        override fun areItemsTheSame(oldItem: ExtractedString, newItem: ExtractedString): Boolean {
            return oldItem.offset == newItem.offset && oldItem.value == newItem.value
        }

        override fun areContentsTheSame(oldItem: ExtractedString, newItem: ExtractedString): Boolean {
            return oldItem == newItem
        }
    }
}
