/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */


package com.android.gamebar

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.android.gamebar.R

import java.util.Locale

import com.android.settingslib.widget.SettingsBasePreferenceFragment

class GameBarPerAppConfigFragment : SettingsBasePreferenceFragment() {
    
    companion object {
        const val PREF_AUTO_APPS = "game_bar_auto_apps"
    }

    private var searchBar: EditText? = null
    private var category: PreferenceCategory? = null
    private var allApps: List<ApplicationInfo>? = null
    private var pm: PackageManager? = null
    private var autoApps: Set<String>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        val context = requireContext()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        searchBar = EditText(context).apply {
            id = View.generateViewId()
            hint = "Search apps..."
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setBackgroundResource(R.drawable.bg_search_rounded)
            setPadding(24, 24, 24, 24)
            setTextColor(context.getColor(R.color.app_name_text_selector))
            setHintTextColor(context.getColor(R.color.app_package_text_selector))
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val margin = (context.resources.displayMetrics.density * 16).toInt() // 16dp
        params.setMargins(margin, 0, margin, 0)
        layout.addView(searchBar, params)
        root?.let { layout.addView(it) }
        
        searchBar?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populateAppList(s.toString().lowercase())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        return layout
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        category = PreferenceCategory(requireContext()).apply {
            title = "Configure Per-App GameBar"
        }
        preferenceScreen.addPreference(category!!)
        pm = requireContext().packageManager
        allApps = pm!!.getInstalledApplications(PackageManager.GET_META_DATA)
        autoApps = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getStringSet(PREF_AUTO_APPS, emptySet())
        populateAppList("")
    }

    private fun populateAppList(filter: String) {
        category?.removeAll()
        allApps?.forEach { app ->
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@forEach
            if (app.packageName == requireContext().packageName) return@forEach
            val label = app.loadLabel(pm!!).toString().lowercase()
            val pkg = app.packageName.lowercase()
            if (filter.isNotEmpty() && !(label.contains(filter) || pkg.contains(filter))) return@forEach
            
            val pref = SwitchPreferenceCompat(requireContext()).apply {
                title = app.loadLabel(pm!!)
                summary = app.packageName
                key = "gamebar_${app.packageName}"
                isChecked = autoApps?.contains(app.packageName) == true
                icon = app.loadIcon(pm!!)
                onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    val updated = PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .getStringSet(PREF_AUTO_APPS, emptySet())?.toMutableSet() ?: mutableSetOf()
                    if (newValue as Boolean) {
                        updated.add(app.packageName)
                    } else {
                        updated.remove(app.packageName)
                    }
                    PreferenceManager.getDefaultSharedPreferences(requireContext())
                            .edit().putStringSet(PREF_AUTO_APPS, updated).apply()
                    true
                }
            }
            category?.addPreference(pref)
        }
    }
}
