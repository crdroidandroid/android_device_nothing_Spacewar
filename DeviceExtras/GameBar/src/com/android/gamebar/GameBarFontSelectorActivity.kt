/*
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity

class GameBarFontSelectorActivity : CollapsingToolbarBaseActivity() {

    private lateinit var searchBox: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var fontAdapter: FontAdapter
    private val fontList = mutableListOf<FontItem>()
    private val allFonts = mutableListOf<FontItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_font_selector)
        title = "Select Overlay Font"

        searchBox = findViewById(R.id.font_search_box)
        recyclerView = findViewById(R.id.font_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadFonts()
        setupAdapter()
        setupSearch()
    }

    private fun loadFonts() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentFontPath = prefs.getString("game_bar_font_path", "fonts/Circular-Std-Bold.ttf") ?: "fonts/Circular-Std-Bold.ttf"

        // Add default font
        fontList.add(
            FontItem(
                name = "default",
                displayName = "System Default",
                path = "default",
                isSelected = (currentFontPath == "fonts/Circular-Std-Bold.ttf")
            )
        )

        // Load fonts from assets
        try {
            val fontFiles = assets.list("fonts") ?: emptyArray()
            
            fontFiles.filter { fileName ->
                fileName.endsWith(".ttf", ignoreCase = true) || 
                fileName.endsWith(".otf", ignoreCase = true)
            }.forEach { fileName ->
                val nameWithoutExt = fileName.substringBeforeLast(".")
                val displayName = nameWithoutExt
                    .replace("-", " ")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }

                fontList.add(
                    FontItem(
                        name = nameWithoutExt,
                        displayName = displayName,
                        path = "fonts/$fileName",  // Asset path
                        isSelected = ("fonts/$fileName" == currentFontPath)
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("FontSelector", "Error loading fonts from assets: ${e.message}")
        }

        // Sort fonts alphabetically (keep default at top)
        if (fontList.isNotEmpty()) {
            val defaultFont = fontList.removeAt(0)
            fontList.sortBy { it.displayName }
            fontList.add(0, defaultFont)
        }
        
        // Store all fonts for filtering
        allFonts.addAll(fontList)
    }

    private fun setupAdapter() {
        fontAdapter = FontAdapter(fontList) { selectedFont ->
            // Save selected font
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            prefs.edit()
                .putString("game_bar_font_path", selectedFont.path)
                .putString("game_bar_font_name", selectedFont.displayName)
                .apply()

            // Update GameBar font (will refresh overlay if showing)
            if (GameBar.isInstanceCreated()) {
                val gameBar = GameBar.getInstance(this)
                gameBar.updateFont(selectedFont.path)
            }

            Toast.makeText(
                this,
                "Font changed to ${selectedFont.displayName}",
                Toast.LENGTH_SHORT
            ).show()
        }

        recyclerView.adapter = fontAdapter
    }
    
    private fun setupSearch() {
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterFonts(s.toString())
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun filterFonts(query: String) {
        val filtered = if (query.isEmpty()) {
            allFonts
        } else {
            allFonts.filter { font ->
                font.displayName.contains(query, ignoreCase = true) ||
                font.name.contains(query, ignoreCase = true)
            }
        }
        
        fontAdapter.updateFonts(filtered)
    }
}
