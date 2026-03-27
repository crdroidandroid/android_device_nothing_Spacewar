/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.ImageView
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.android.gamebar.R

class ExpandablePreferenceCategory @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceCategoryStyle,
    defStyleRes: Int = 0
) : PreferenceCategory(context, attrs, defStyleAttr, defStyleRes) {

    private var isExpanded = false
    
    init {
        layoutResource = R.layout.preference_category_expandable
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        val expandIcon = holder.findViewById(R.id.expand_icon) as? ImageView
        val titleView = holder.findViewById(android.R.id.title) as? TextView
        
        // Update icon rotation based on expanded state with animation
        expandIcon?.animate()
            ?.rotation(if (isExpanded) 90f else 0f)
            ?.setDuration(200)
            ?.start()
        
        // Make the entire view clickable
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setOnClickListener {
            toggleExpanded()
        }
        
        // Update title style
        titleView?.let {
            it.textSize = 14f
            val typedValue = TypedValue()
            val theme = context.theme
            if (theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
                it.setTextColor(typedValue.data)
            } else {
                // Fallback
                it.setTextColor(context.getColor(android.R.color.white))
            }
        }
    }

    override fun onAttached() {
        super.onAttached()
        
        // Initially collapse all children
        if (!isExpanded) {
            syncChildrenVisibility()
        }
    }

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        syncChildrenVisibility()
        notifyChanged()
    }

    private fun syncChildrenVisibility() {
        for (i in 0 until preferenceCount) {
            getPreference(i)?.isVisible = isExpanded
        }
    }

    fun setExpanded(expanded: Boolean) {
        if (isExpanded != expanded) {
            isExpanded = expanded
            syncChildrenVisibility()
            notifyChanged()
        }
    }

    override fun addPreference(preference: Preference): Boolean {
        val result = super.addPreference(preference)
        if (result && !isExpanded) {
            preference.isVisible = false
        }
        return result
    }
}
