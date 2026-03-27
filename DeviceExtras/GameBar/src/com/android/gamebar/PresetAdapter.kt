/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PresetAdapter(
    private var presets: List<PresetManager.Preset>,
    private val onPresetClick: (PresetManager.Preset) -> Unit,
    private val onMenuClick: (PresetManager.Preset, View) -> Unit
) : RecyclerView.Adapter<PresetAdapter.PresetViewHolder>() {

    class PresetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.tv_preset_name)
        val dateTextView: TextView = itemView.findViewById(R.id.tv_preset_date)
        val menuButton: ImageView = itemView.findViewById(R.id.iv_preset_menu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preset, parent, false)
        return PresetViewHolder(view)
    }

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        val preset = presets[position]
        
        holder.nameTextView.text = preset.name
        holder.dateTextView.text = holder.itemView.context.getString(
            R.string.preset_created,
            preset.createdDate
        )
        
        holder.itemView.setOnClickListener {
            onPresetClick(preset)
        }
        
        holder.menuButton.setOnClickListener {
            onMenuClick(preset, it)
        }
    }

    override fun getItemCount(): Int = presets.size

    fun updatePresets(newPresets: List<PresetManager.Preset>) {
        presets = newPresets
        notifyDataSetChanged()
    }
}
