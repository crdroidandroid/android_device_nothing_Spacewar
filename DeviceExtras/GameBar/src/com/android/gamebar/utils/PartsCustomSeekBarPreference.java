/*
 * Copyright (C) 2016-2017 The Dirty Unicorns Project
 * SPDX-FileCopyrightText: 2025 kenway214
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.gamebar.utils;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import androidx.preference.*;
import androidx.core.content.res.TypedArrayUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.gamebar.R;
import com.android.settingslib.widget.SettingsThemeHelper;

import com.google.android.material.slider.LabelFormatter;
import com.google.android.material.slider.Slider;

public class PartsCustomSeekBarPreference extends Preference implements Slider.OnChangeListener,
        Slider.OnSliderTouchListener, View.OnClickListener, View.OnLongClickListener {
    protected final String TAG = getClass().getName();
    private static final String SETTINGS_NS = "http://schemas.android.com/apk/res/com.android.settings";
    private static final String SETTINGS_NS_ALT = "http://schemas.android.com/apk/res-auto";
    protected static final String ANDROIDNS = "http://schemas.android.com/apk/res/android";

    protected int mInterval = 1;
    protected boolean mShowSign = false;
    protected String mUnits = "";
    protected boolean mContinuousUpdates = false;

    protected int mMinValue = 0;
    protected int mMaxValue = 100;
    protected boolean mDefaultValueExists = false;
    protected int mDefaultValue;

    protected int mValue;

    protected TextView mValueTextView;
    protected ImageView mResetImageView;
    protected ImageView mMinusImageView;
    protected ImageView mPlusImageView;
    protected Slider mSlider;

    protected boolean mTrackingTouch = false;
    protected int mTrackingValue;

    // Custom value mapping for non-linear values (like time durations)
    protected int[] mCustomValues = null;
    protected String[] mCustomLabels = null;

    public PartsCustomSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PartsCustomSeekBarPreference);
        try {
            mShowSign = a.getBoolean(R.styleable.PartsCustomSeekBarPreference_showSign, mShowSign);
            String units = a.getString(R.styleable.PartsCustomSeekBarPreference_units);
            if (units != null)
                mUnits = " " + units;
            mContinuousUpdates = a.getBoolean(
                    R.styleable.PartsCustomSeekBarPreference_continuousUpdates, false);
        } finally {
            a.recycle();
        }

        String newInterval = attrs.getAttributeValue(SETTINGS_NS, "interval");
        if (newInterval != null) {
            mInterval = Integer.parseInt(newInterval);
        }
        if (newInterval == null) {
            newInterval = attrs.getAttributeValue(SETTINGS_NS_ALT, "interval");
            if (newInterval != null) mInterval = Integer.parseInt(newInterval);
        }
        if (newInterval == null) {
            newInterval = attrs.getAttributeValue(ANDROIDNS, "interval");
            if (newInterval != null) mInterval = Integer.parseInt(newInterval);
        }

        mMinValue = attrs.getAttributeIntValue(SETTINGS_NS, "min", mMinValue);
        if (mMinValue == 0) {
            int min = attrs.getAttributeIntValue(SETTINGS_NS_ALT, "min", mMinValue);
            if (min != 0) mMinValue = min;
        }
        if (mMinValue == 0) {
            int min = attrs.getAttributeIntValue(ANDROIDNS, "min", mMinValue);
            if (min != 0) mMinValue = min;
        }

        mMaxValue = attrs.getAttributeIntValue(ANDROIDNS, "max", mMaxValue);
        if (mMaxValue == 100) {
            int max = attrs.getAttributeIntValue(SETTINGS_NS, "max", mMaxValue);
            if (max != 100) mMaxValue = max;
        }
        if (mMaxValue == 100) {
            int max = attrs.getAttributeIntValue(SETTINGS_NS_ALT, "max", mMaxValue);
            if (max != 100) mMaxValue = max;
        }
        if (mMaxValue < mMinValue)
            mMaxValue = mMinValue;

        String defaultValue = attrs.getAttributeValue(ANDROIDNS, "defaultValue");
        mDefaultValueExists = defaultValue != null && !defaultValue.isEmpty();
        if (!mDefaultValueExists) {
            defaultValue = attrs.getAttributeValue(SETTINGS_NS, "defaultValue");
            mDefaultValueExists = defaultValue != null && !defaultValue.isEmpty();
        }
        if (!mDefaultValueExists) {
            defaultValue = attrs.getAttributeValue(SETTINGS_NS_ALT, "defaultValue");
            mDefaultValueExists = defaultValue != null && !defaultValue.isEmpty();
        }
        if (mDefaultValueExists) {
            mDefaultValue = getLimitedValue(Integer.parseInt(defaultValue));
            mValue = mDefaultValue;
        } else {
            mValue = mMinValue;
        }

        setLayoutResource(R.layout.preference_custom_seekbar);
    }

    public PartsCustomSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PartsCustomSeekBarPreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context,
                androidx.preference.R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    public PartsCustomSeekBarPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        final Slider boundSlider = (Slider) holder.findViewById(R.id.slider);
        if (mSlider != null && mSlider != boundSlider) {
            mSlider.removeOnChangeListener(this);
            mSlider.removeOnSliderTouchListener(this);
        }
        mSlider = boundSlider;
        mSlider.removeOnChangeListener(this);
        mSlider.removeOnSliderTouchListener(this);
        mSlider.setValueTo(mMaxValue);
        mSlider.setValueFrom(mMinValue);
        mSlider.setValue(mValue);
        mSlider.setEnabled(isEnabled());
        mSlider.setLabelBehavior(LabelFormatter.LABEL_GONE);
        mSlider.setTickVisible(false);
        if (mInterval > 0) {
            mSlider.setStepSize(mInterval);
        } else {
            Log.w(TAG, "Step size is zero or invalid: " + mInterval);
        }

        // Set up slider size
        if (SettingsThemeHelper.isExpressiveTheme(getContext())) {
            Resources res = getContext().getResources();
            mSlider.setTrackHeight(res.getDimensionPixelSize(
                    com.android.settingslib.widget.preference.slider.R.dimen
                    .settingslib_expressive_slider_track_height));
            mSlider.setTrackInsideCornerSize(res.getDimensionPixelSize(
                    com.android.settingslib.widget.preference.slider.R.dimen
                    .settingslib_expressive_slider_track_inside_corner_size));
            mSlider.setTrackStopIndicatorSize(res.getDimensionPixelSize(
                    com.android.settingslib.widget.preference.slider.R.dimen
                    .settingslib_expressive_slider_track_stop_indicator_size));
            mSlider.setThumbWidth(res.getDimensionPixelSize(
                    com.android.settingslib.widget.preference.slider.R.dimen
                    .settingslib_expressive_slider_thumb_width));
            mSlider.setThumbHeight(res.getDimensionPixelSize(
                    com.android.settingslib.widget.preference.slider.R.dimen
                    .settingslib_expressive_slider_thumb_height));
            mSlider.setThumbElevation(res.getDimensionPixelSize(
                    com.android.settingslib.widget.preference.slider.R.dimen
                    .settingslib_expressive_slider_thumb_elevation));
            mSlider.setThumbStrokeWidth(0);
            mSlider.setThumbTrackGapSize(res.getDimensionPixelSize(
                    com.android.settingslib.widget.preference.slider.R.dimen
                    .settingslib_expressive_slider_thumb_track_gap_size));
            mSlider.setTickActiveRadius(res.getDimensionPixelSize(
                    com.android.settingslib.widget.preference.slider.R.dimen
                    .settingslib_expressive_slider_tick_radius));
            mSlider.setTickInactiveRadius(res.getDimensionPixelSize(
                    com.android.settingslib.widget.preference.slider.R
                    .dimen.settingslib_expressive_slider_tick_radius));
        }

        mValueTextView = (TextView) holder.findViewById(R.id.value);
        mResetImageView = (ImageView) holder.findViewById(R.id.reset);
        mMinusImageView = (ImageView) holder.findViewById(R.id.minus);
        mPlusImageView = (ImageView) holder.findViewById(R.id.plus);

        updateValueViews();

        mSlider.addOnChangeListener(this);
        mSlider.addOnSliderTouchListener(this);
        mResetImageView.setOnClickListener(this);
        mMinusImageView.setOnClickListener(this);
        mPlusImageView.setOnClickListener(this);
        mResetImageView.setOnLongClickListener(this);
        mMinusImageView.setOnLongClickListener(this);
        mPlusImageView.setOnLongClickListener(this);
    }

    protected int getLimitedValue(int v) {
        return v < mMinValue ? mMinValue : (v > mMaxValue ? mMaxValue : v);
    }

    protected String getTextValue(int v) {
        if (mCustomLabels != null && v >= 0 && v < mCustomLabels.length) {
            return mCustomLabels[v];
        }
        return (mShowSign && v > 0 ? "+" : "") + String.valueOf(v) + mUnits;
    }

    protected void updateValueViews() {
        if (mValueTextView != null) {
            String add = "";
            if (mDefaultValueExists && mValue == mDefaultValue) {
                add = " (" + getContext().getString(
                        R.string.custom_seekbar_default_value) + ")";
            }
            String textValue = getTextValue(mValue) + add;
            if (mTrackingTouch && !mContinuousUpdates) {
                textValue = getTextValue(mTrackingValue);
            }
            mValueTextView.setText(getContext().getString(
                    R.string.custom_seekbar_value, textValue));
        }

        if (mResetImageView != null) {
            if (!mDefaultValueExists || mValue == mDefaultValue || mTrackingTouch)
                mResetImageView.setVisibility(View.INVISIBLE);
            else
                mResetImageView.setVisibility(View.VISIBLE);
        }

        if (mMinusImageView != null) {
            if (mValue == mMinValue || mTrackingTouch) {
                mMinusImageView.setClickable(false);
                mMinusImageView.setColorFilter(getContext().getColor(R.color.disabled_text_color),
                        PorterDuff.Mode.MULTIPLY);
            } else {
                mMinusImageView.setClickable(true);
                mMinusImageView.clearColorFilter();
            }
        }

        if (mPlusImageView != null) {
            if (mValue == mMaxValue || mTrackingTouch) {
                mPlusImageView.setClickable(false);
                mPlusImageView.setColorFilter(getContext().getColor(R.color.disabled_text_color),
                        PorterDuff.Mode.MULTIPLY);
            } else {
                mPlusImageView.setClickable(true);
                mPlusImageView.clearColorFilter();
            }
        }
    }

    protected void changeValue(int newValue) {
        // for subclasses
    }

    @Override
    public void onValueChange(Slider slider, float value, boolean fromUser) {
        int newValue = getLimitedValue(Math.round(value));
        if (mTrackingTouch && !mContinuousUpdates) {
            mTrackingValue = newValue;
        } else if (mValue != newValue) {
            // change rejected, revert to the previous value
            if (!callChangeListener(newValue)) {
                mSlider.setValue(mValue);
                return;
            }
            // change accepted, store it
            changeValue(newValue);
            persistInt(newValue);

            mValue = newValue;
        }
        updateValueViews();
    }

    @Override
    public void onStartTrackingTouch(Slider slider) {
        mTrackingValue = mValue;
        mTrackingTouch = true;
    }

    @Override
    public void onStopTrackingTouch(Slider slider) {
        mTrackingTouch = false;
        if (!mContinuousUpdates)
            onValueChange(mSlider, mTrackingValue, false);
        notifyChanged();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.reset) {
            Toast.makeText(getContext(), getContext().getString(
                    R.string.custom_seekbar_default_value_to_set, getTextValue(mDefaultValue)),
                    Toast.LENGTH_LONG).show();
        } else if (id == R.id.minus) {
            setValue(mValue - mInterval, true);
        } else if (id == R.id.plus) {
            setValue(mValue + mInterval, true);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        int id = v.getId();
        if (id == R.id.reset) {
            setValue(mDefaultValue, true);
        } else if (id == R.id.minus) {
            int value = mMinValue;
            if (mMaxValue - mMinValue > mInterval * 2 && mMaxValue + mMinValue < mValue * 2) {
                value = Math.floorDiv(mMaxValue + mMinValue, 2);
            }
            setValue(value, true);
        } else if (id == R.id.plus) {
            int value = mMaxValue;
            if (mMaxValue - mMinValue > mInterval * 2 && mMaxValue + mMinValue > mValue * 2) {
                value = -1 * Math.floorDiv(-1 * (mMaxValue + mMinValue), 2);
            }
            setValue(value, true);
        }
        return true;
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue)
            mValue = getPersistedInt(mValue);
    }

    @Override
    public void setDefaultValue(Object defaultValue) {
        if (defaultValue instanceof Integer)
            setDefaultValue((Integer) defaultValue, mSlider != null);
        else
            setDefaultValue(defaultValue == null ? (String) null : defaultValue.toString(), mSlider != null);
    }

    public void setDefaultValue(int newValue, boolean update) {
        newValue = getLimitedValue(newValue);
        if (!mDefaultValueExists || mDefaultValue != newValue) {
            mDefaultValueExists = true;
            mDefaultValue = newValue;
            if (update)
                updateValueViews();
        }
    }

    public void setDefaultValue(String newValue, boolean update) {
        if (mDefaultValueExists && (newValue == null || newValue.isEmpty())) {
            mDefaultValueExists = false;
            if (update)
                updateValueViews();
        } else if (newValue != null && !newValue.isEmpty()) {
            setDefaultValue(Integer.parseInt(newValue), update);
        }
    }

    public void setMax(int max) {
        mMaxValue = max;
        if (mSlider != null) mSlider.setValueTo(mMaxValue);
    }

    public int getMax() {
        return mMaxValue;
    }

    public void setMin(int min) {
        mMinValue = min;
        if (mSlider != null) mSlider.setValueFrom(mMinValue);
    }

    public void setValue(int newValue) {
        mValue = getLimitedValue(newValue);
        if (mSlider != null) mSlider.setValue(mValue);
        onValueChange(mSlider, mValue, false);
        notifyChanged();
    }

    public void setValue(int newValue, boolean update) {
        newValue = getLimitedValue(newValue);
        if (mValue != newValue) {
            if (!callChangeListener(newValue)) {
                return;
            }

            mValue = newValue;
            persistInt(newValue);
            changeValue(newValue);  // if needed
            if (update && mSlider != null)
                mSlider.setValue(newValue);

            updateValueViews();
            notifyChanged();
        }
    }

    public int getValue() {
        return mValue;
    }

    public void setUnits(String units) {
        mUnits = units;
        updateValueViews();
    }

    public String getUnits() {
        return mUnits;
    }

    // Custom value mapping methods for time durations and other non-linear scales
    public void setCustomValues(int[] values, String[] labels) {
        mCustomValues = values;
        mCustomLabels = labels;
        if (values != null && values.length > 0) {
            setMax(values.length - 1);
            setMin(0);
        }
        updateValueViews();
    }

    public int getMappedValue() {
        if (mCustomValues != null && mValue >= 0 && mValue < mCustomValues.length) {
            return mCustomValues[mValue];
        }
        return mValue;
    }

    public void refresh(int newValue) {
        // Update the value without triggering change listeners to avoid infinite recursion
        mValue = getLimitedValue(newValue);
        if (mSlider != null) {
            // Temporarily remove listener to prevent triggering onValueChange
            mSlider.removeOnChangeListener(this);
            mSlider.setValue(mValue);
            mSlider.addOnChangeListener(this);
        }
        updateValueViews();
        persistInt(mValue);
    }
}