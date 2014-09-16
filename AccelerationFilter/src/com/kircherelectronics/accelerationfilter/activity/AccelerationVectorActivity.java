package com.kircherelectronics.accelerationfilter.activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;

import com.kircherelectronics.accelerationfilter.R;
import com.kircherelectronics.accelerationfilter.dialog.FilterSettingsDialog;
import com.kircherelectronics.accelerationfilter.filter.LowPassFilter;
import com.kircherelectronics.accelerationfilter.filter.MeanFilter;
import com.kircherelectronics.accelerationfilter.plot.PlotPrefCallback;
import com.kircherelectronics.accelerationfilter.prefs.PrefUtils;
import com.kircherelectronics.accelerationfilter.view.AccelerationVectorView;

/**
 * Draws a two dimensional vector of the acceleration sensors measurements.
 * 
 * @author Kaleb
 * 
 */
public class AccelerationVectorActivity extends Activity implements
		SensorEventListener, PlotPrefCallback
{
	// Indicate if the Wiki LPF should be plotted
	private boolean lpfActive = false;

	// Indicate if the Mean Filter should be plotted
	private boolean meanFilterActive = false;
	
	private boolean invertAxisActive = false;

	private float lpfTimeConstant = 1;
	private float meanFilterTimeConstant = 1;

	private float[] acceleration = new float[3];
	private float[] lpfOutput = new float[3];
	private float[] meanFilterOutput = new float[3];

	private AccelerationVectorView view;

	// Low-Pass Filter
	private LowPassFilter lpf;

	// Mean filter
	private MeanFilter meanFilter;

	// Sensor manager to access the accelerometer sensor
	private SensorManager sensorManager;

	private FilterSettingsDialog settingsDialog;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.acceleration_vector_activity);

		view = (AccelerationVectorView) findViewById(R.id.vector_acceleration);

		sensorManager = (SensorManager) this
				.getSystemService(Context.SENSOR_SERVICE);

		readPrefs();
		initFilters();
	}

	@Override
	public void checkPlotPrefs()
	{
		readPrefs();

		lpf.setTimeConstant(this.lpfTimeConstant);
		meanFilter.setTimeConstant(this.meanFilterTimeConstant);
	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		System.arraycopy(event.values, 0, acceleration, 0, event.values.length);
		
		if(invertAxisActive)
		{
			acceleration[0] = -acceleration[0];
			acceleration[1] = -acceleration[1];
			acceleration[2] = -acceleration[2];
		}

		if (lpfActive && !meanFilterActive)
		{
			lpfOutput = lpf.addSamples(acceleration);

			view.updatePoint(lpfOutput[0], lpfOutput[1]);
		}

		if (meanFilterActive && !lpfActive)
		{
			meanFilterOutput = meanFilter.filterFloat(acceleration);

			view.updatePoint(meanFilterOutput[0], meanFilterOutput[1]);
		}

		if (lpfActive && meanFilterActive)
		{
			lpfOutput = lpf.addSamples(acceleration);
			meanFilterOutput = meanFilter.filterFloat(lpfOutput);

			view.updatePoint(meanFilterOutput[0], meanFilterOutput[1]);
		}

		if (!lpfActive && !meanFilterActive)
		{
			view.updatePoint(acceleration[0], acceleration[1]);
		}
	}

	@Override
	public void onPause()
	{
		super.onPause();

		sensorManager.unregisterListener(this);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		readPrefs();

		// Reset the filters
		lpf.reset();
		meanFilter.reset();

		// Register for sensor updates.
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.settings_vector_menu, menu);
		return true;
	}

	/**
	 * Event Handling for Individual menu item selected Identify single menu
	 * item by it's id
	 * */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		// Log the data
		case R.id.action_plot:
			Intent plotIntent = new Intent(this, AccelerationPlotActivity.class);
			startActivity(plotIntent);
			return true;

			// Log the data
		case R.id.menu_settings_filter:
			showSettingsDialog();
			return true;

			// Log the data
		case R.id.menu_settings_help:
			showHelpDialog();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Initialize the available filters.
	 */
	private void initFilters()
	{
		lpf = new LowPassFilter();
		lpf.setTimeConstant(this.lpfTimeConstant);

		meanFilter = new MeanFilter();
		meanFilter.setTimeConstant(this.meanFilterTimeConstant);
	}

	/**
	 * Read in the current user preferences.
	 */
	private void readPrefs()
	{
		SharedPreferences prefs = this.getSharedPreferences(
				PrefUtils.FILTER_PREFS, Activity.MODE_PRIVATE);

		this.lpfActive = prefs.getBoolean(PrefUtils.LPF_ACTIVE_PREF, false);
		this.meanFilterActive = prefs.getBoolean(
				PrefUtils.MEAN_FILTER_ACTIVE_PREF, false);
		this.invertAxisActive = prefs.getBoolean(
				PrefUtils.INVERT_AXIS_ACTIVE, false);

		this.lpfTimeConstant = prefs.getFloat(PrefUtils.LPF_TIME_CONSTANT, 1);
		this.meanFilterTimeConstant = prefs.getFloat(
				PrefUtils.MEAN_FILTER_TIME_CONSTANT, 1);
	}

	private void showHelpDialog()
	{
		Dialog helpDialog = new Dialog(this);
		helpDialog.setCancelable(true);
		helpDialog.setCanceledOnTouchOutside(true);

		helpDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

		helpDialog.setContentView(getLayoutInflater().inflate(R.layout.help_dialog_view,
				null));

		helpDialog.show();
	}

	/**
	 * Show a settings dialog.
	 */
	private void showSettingsDialog()
	{
		if (settingsDialog == null)
		{
			settingsDialog = new FilterSettingsDialog(this, this);
			settingsDialog.setCancelable(true);
			settingsDialog.setCanceledOnTouchOutside(true);
		}

		settingsDialog.show();
	}

	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1)
	{
		// TODO Auto-generated method stub

	}
}
