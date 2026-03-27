/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FontAdapter(
    private var fonts: List<FontItem>,
    private val onFontSelected: (FontItem) -> Unit
) : RecyclerView.Adapter<FontAdapter.FontViewHolder>() {

    private var selectedPosition = fonts.indexOfFirst { it.isSelected }

    inner class FontViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fontName: TextView = view.findViewById(R.id.font_name)
        val fontPreview: TextView = view.findViewById(R.id.font_preview)
        val radioButton: RadioButton = view.findViewById(R.id.font_radio)

        init {
            view.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    selectFont(position)
                }
            }
            radioButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    selectFont(position)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_font, parent, false)
        return FontViewHolder(view)
    }

    override fun onBindViewHolder(holder: FontViewHolder, position: Int) {
        val fontItem = fonts[position]
        
        holder.fontName.text = fontItem.displayName
        holder.fontPreview.text = "AaBbCc 123"
        holder.radioButton.isChecked = (position == selectedPosition)

        // Apply the font to preview
        try {
            val typeface = if (fontItem.path == "default") {
                Typeface.DEFAULT
            } else {
                // Load from assets
                Typeface.createFromAsset(holder.itemView.context.assets, fontItem.path)
            }
            holder.fontPreview.setTypeface(typeface, Typeface.NORMAL)
        } catch (e: Exception) {
            // If font fails to load, use default
            holder.fontPreview.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            android.util.Log.e("FontAdapter", "Failed to load font ${fontItem.path}: ${e.message}")
        }
    }

    override fun getItemCount() = fonts.size

    private fun selectFont(position: Int) {
        if (selectedPosition != position) {
            val oldPosition = selectedPosition
            selectedPosition = position
            
            // Update selection state
            if (oldPosition >= 0 && oldPosition < fonts.size) {
                fonts[oldPosition].isSelected = false
                notifyItemChanged(oldPosition)
            }
            
            fonts[position].isSelected = true
            notifyItemChanged(position)
            
            onFontSelected(fonts[position])
        }
    }
    
    fun updateFonts(newFonts: List<FontItem>) {
        fonts = newFonts
        selectedPosition = fonts.indexOfFirst { it.isSelected }
        notifyDataSetChanged()
    }
}
