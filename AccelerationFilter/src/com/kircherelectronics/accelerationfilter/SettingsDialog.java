package com.kircherelectronics.accelerationfilter;

import java.text.DecimalFormat;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RelativeLayout;
import android.widget.TextView;

/*
 * Acceleration Filter
 * Copyright (C) 2013, Kaleb Kircher - Boki Software, Kircher Engineering, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * A special dialog for the settings of the application.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class SettingsDialog extends Dialog implements
		NumberPicker.OnValueChangeListener, OnCheckedChangeListener
{
	private boolean showWikiAlpha = false;
	private boolean showAndDevAlpha = false;

	private LayoutInflater inflater;

	private View settingsWikiFilterView;
	private View settingsAndDevFilterView;

	private View settingsWikiAlphaView;
	private View settingsAndDevAlphaView;

	private NumberPicker wikiAlphaNP;
	private NumberPicker andDevAlphaNP;

	private TextView wikiFilterTextView;
	private TextView wikiAlphaTextView;

	private TextView andDevFilterTextView;
	private TextView andDevAlphaTextView;

	private DecimalFormat df;

	private CheckBox wikiAlphaCheckBox;

	private CheckBox andDevAlphaCheckBox;

	private RelativeLayout wikiStaticAlpha;

	private RelativeLayout andDevStaticAlpha;

	private LowPassFilter lpfWiki;

	private LowPassFilter lpfAndDev;

	private float wikiAlpha;
	private float andDevAlpha;

	/**
	 * Create a dialog.
	 * @param context The context.
	 * @param lpfWiki The Wikipedia LPF.
	 * @param lpfAndDev The Android Developer LPF.
	 */
	public SettingsDialog(Context context, final LowPassFilter lpfWiki,
			LowPassFilter lpfAndDev)
	{
		super(context);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		this.lpfWiki = lpfWiki;
		this.lpfAndDev = lpfAndDev;

		readPrefs();

		inflater = getLayoutInflater();

		View settingsView = inflater.inflate(R.layout.settings, null, false);

		LinearLayout layout = (LinearLayout) settingsView
				.findViewById(R.id.layout_settings_content);

		createWikiAlphaSettings();
		createAndDevAlphaSettings();

		layout.addView(settingsWikiFilterView);
		layout.addView(settingsAndDevFilterView);

		this.setContentView(settingsView);

		df = new DecimalFormat("#.####");
	}

	@Override
	public void onStop()
	{
		super.onStop();

		writePrefs();
	}

	@Override
	public void onValueChange(NumberPicker picker, int oldVal, int newVal)
	{
		if (picker.equals(wikiAlphaNP))
		{
			wikiAlphaTextView.setText(df.format(newVal * 0.001));

			if (showWikiAlpha)
			{
				wikiAlpha = newVal * 0.001f;

				lpfWiki.setAlpha(wikiAlpha);
			}
		}

		if (picker.equals(andDevAlphaNP))
		{
			andDevAlphaTextView.setText(df.format(newVal * 0.001));

			if (showAndDevAlpha)
			{
				andDevAlpha = newVal * 0.001f;

				lpfAndDev.setAlpha(andDevAlpha);
			}
		}
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
	{
		Log.d("tag", String.valueOf(isChecked));

		if (buttonView.equals(this.wikiAlphaCheckBox))
		{
			if (isChecked)
			{
				showWikiAlpha = true;

				showWikiStaticAlphaView();
				removeWikiStaticAlphaView();

				lpfWiki.setAlphaStatic(true);
			}
			else
			{
				showWikiAlpha = false;

				showWikiStaticAlphaView();
				removeWikiStaticAlphaView();

				lpfWiki.setAlphaStatic(false);
			}
		}

		if (buttonView.equals(this.andDevAlphaCheckBox))
		{
			if (isChecked)
			{
				showAndDevAlpha = true;

				showAndDevStaticAlphaView();
				removeAndDevStaticAlphaView();

				lpfAndDev.setAlphaStatic(true);
			}
			else
			{
				showAndDevAlpha = false;

				showAndDevStaticAlphaView();
				removeAndDevStaticAlphaView();

				lpfAndDev.setAlphaStatic(false);
			}
		}
	}

	/**
	 * Create the Android Developer Settings.
	 */
	private void createAndDevAlphaSettings()
	{
		settingsAndDevFilterView = inflater.inflate(R.layout.settings_filter,
				null, false);

		andDevAlphaCheckBox = (CheckBox) settingsAndDevFilterView
				.findViewById(R.id.checkBox1);

		andDevAlphaCheckBox.setOnCheckedChangeListener(this);

		if (showAndDevAlpha)
		{
			andDevAlphaCheckBox.setChecked(true);
		}
		else
		{
			andDevAlphaCheckBox.setChecked(false);
		}

		andDevFilterTextView = (TextView) settingsAndDevFilterView
				.findViewById(R.id.label_filter_name);

		andDevFilterTextView.setText("LPFAndDev");
	}


	/**
	 * Create the Wikipedia Settings.
	 */
	private void createWikiAlphaSettings()
	{
		settingsWikiFilterView = inflater.inflate(R.layout.settings_filter,
				null, false);

		wikiAlphaCheckBox = (CheckBox) settingsWikiFilterView
				.findViewById(R.id.checkBox1);

		wikiAlphaCheckBox.setOnCheckedChangeListener(this);

		if (showWikiAlpha)
		{
			wikiAlphaCheckBox.setChecked(true);
		}
		else
		{
			wikiAlphaCheckBox.setChecked(false);
		}

		wikiFilterTextView = (TextView) settingsWikiFilterView
				.findViewById(R.id.label_filter_name);

		wikiFilterTextView.setText("LPFWiki");
	}


	/**
	 * Show the Android Developer Settings.
	 */
	private void showAndDevStaticAlphaView()
	{
		if (showAndDevAlpha)
		{
			if (settingsAndDevAlphaView == null)
			{
				settingsAndDevAlphaView = inflater.inflate(
						R.layout.settings_filter_alpha, null, false);
			}

			andDevAlphaTextView = (TextView) settingsAndDevAlphaView
					.findViewById(R.id.value_alpha);
			andDevAlphaTextView.setText(String.valueOf(0.1));

			andDevAlphaNP = (NumberPicker) settingsAndDevAlphaView
					.findViewById(R.id.numberPicker1);
			andDevAlphaNP.setMaxValue(1000);
			andDevAlphaNP.setMinValue(0);
			andDevAlphaNP.setValue(100);

			andDevAlphaNP.setOnValueChangedListener(this);

			andDevStaticAlpha = (RelativeLayout) settingsAndDevFilterView
					.findViewById(R.id.layout_static_alpha);

			andDevStaticAlpha.addView(settingsAndDevAlphaView);
		}
	}


	/**
	 * Remove the Android Developer Settings.
	 */
	private void removeAndDevStaticAlphaView()
	{
		if (!showAndDevAlpha)
		{
			andDevStaticAlpha = (RelativeLayout) settingsAndDevFilterView
					.findViewById(R.id.layout_static_alpha);

			andDevStaticAlpha.removeView(settingsAndDevAlphaView);

			settingsAndDevFilterView.invalidate();
		}
	}


	/**
	 * Show the Wikipedia Settings.
	 */
	private void showWikiStaticAlphaView()
	{
		if (showWikiAlpha)
		{
			if (settingsWikiAlphaView == null)
			{
				settingsWikiAlphaView = inflater.inflate(
						R.layout.settings_filter_alpha, null, false);
			}

			wikiAlphaTextView = (TextView) settingsWikiAlphaView
					.findViewById(R.id.value_alpha);
			wikiAlphaTextView.setText(String.valueOf(0.1));

			wikiAlphaNP = (NumberPicker) settingsWikiAlphaView
					.findViewById(R.id.numberPicker1);
			wikiAlphaNP.setMaxValue(1000);
			wikiAlphaNP.setMinValue(0);
			wikiAlphaNP.setValue(100);

			wikiAlphaNP.setOnValueChangedListener(this);

			wikiStaticAlpha = (RelativeLayout) settingsWikiFilterView
					.findViewById(R.id.layout_static_alpha);

			wikiStaticAlpha.addView(settingsWikiAlphaView);
		}
	}

	/**
	 * Remove the Wikipedia Settings.
	 */
	private void removeWikiStaticAlphaView()
	{
		if (!showWikiAlpha)
		{
			wikiStaticAlpha = (RelativeLayout) settingsWikiFilterView
					.findViewById(R.id.layout_static_alpha);

			wikiStaticAlpha.removeView(settingsWikiAlphaView);

			settingsWikiFilterView.invalidate();
		}
	}

	/**
	 * Read in the current user preferences.
	 */
	private void readPrefs()
	{
		SharedPreferences prefs = this.getContext().getSharedPreferences(
				"lpf_prefs", Activity.MODE_PRIVATE);

		this.showWikiAlpha = prefs.getBoolean("show_alpha_wiki", false);
		this.showAndDevAlpha = prefs.getBoolean("show_alpha_and_dev", false);

		this.wikiAlpha = prefs.getFloat("wiki_alpha", 0.1f);
		this.andDevAlpha = prefs.getFloat("and_dev_alpha", 0.1f);

	}

	/**
	 * Write the preferences.
	 */
	private void writePrefs()
	{
		// Write out the offsets to the user preferences.
		SharedPreferences.Editor editor = this.getContext()
				.getSharedPreferences("lpf_prefs", Activity.MODE_PRIVATE)
				.edit();

		editor.putBoolean("show_alpha_wiki", this.showWikiAlpha);
		editor.putBoolean("show_alpha_and_dev", this.showAndDevAlpha);

		editor.putFloat("wiki_alpha", this.wikiAlpha);
		editor.putFloat("and_dev_alpha", this.andDevAlpha);

		editor.commit();
	}
}
