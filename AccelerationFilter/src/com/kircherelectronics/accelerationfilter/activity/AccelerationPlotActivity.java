package com.kircherelectronics.accelerationfilter.activity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Calendar;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.XYPlot;
import com.kircherelectronics.accelerationfilter.R;
import com.kircherelectronics.accelerationfilter.dialog.SettingsDialog;
import com.kircherelectronics.accelerationfilter.filter.LowPassFilter;
import com.kircherelectronics.accelerationfilter.filter.MeanFilter;
import com.kircherelectronics.accelerationfilter.plot.DynamicBarPlot;
import com.kircherelectronics.accelerationfilter.plot.DynamicLinePlot;
import com.kircherelectronics.accelerationfilter.plot.PlotColor;
import com.kircherelectronics.accelerationfilter.plot.PlotPrefCallback;
import com.kircherelectronics.accelerationfilter.prefs.PrefUtils;

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
 * Implements an Activity that is intended to run filters on accelerometer
 * inputs and then graph the outputs. The user can select which filters should
 * be used and set key parameters for each filter.
 * 
 * Currently supports an IIR digital low-pass filter. The low-pass filters are
 * classified as recursive, or infinite response filters (IIR). The current, nth
 * sample output depends on both current and previous inputs as well as previous
 * outputs. It is essentially a weighted moving average, which comes in many
 * different flavors depending on the values for the coefficients, a and b. The
 * low-pass filter, the Wikipedia LPF, is an IIR single-pole implementation. The
 * coefficient, a (alpha), can be adjusted based on the sample period of the
 * sensor to produce the desired time constant that the filter will act on. It
 * takes a simple form of y[i] = y[i] + alpha * (x[i] - y[i]). Alpha is defined
 * as alpha = dt / (timeConstant + dt);) where the time constant is the length
 * of signals the filter should act on and dt is the sample period (1/frequency)
 * of the sensor.
 * 
 * A finite impulse response (FIR) moving average filter is also implemented.
 * This filter tends to be extremely effective at removing noise from the
 * signal, much more so than the low-pass filter.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class AccelerationPlotActivity extends Activity implements
		SensorEventListener, Runnable, OnTouchListener, PlotPrefCallback
{

	private static final String tag = AccelerationPlotActivity.class
			.getSimpleName();

	// Only noise below this threshold will be plotted
	private final static float MAX_NOISE_THRESHOLD = 0.1f;

	// The size of the sample window that determines RMS Amplitude Noise
	// (standard deviation)
	public static int STD_DEV_SAMPLE_WINDOW = 20;

	// Plot keys for the acceleration plot
	private final static int PLOT_ACCEL_X_AXIS_KEY = 0;
	private final static int PLOT_ACCEL_Y_AXIS_KEY = 1;
	private final static int PLOT_ACCEL_Z_AXIS_KEY = 2;

	// Plot keys for the LPF Android Developer plot
	private final static int PLOT_LPF_X_AXIS_KEY = 3;
	private final static int PLOT_LPF_Y_AXIS_KEY = 4;
	private final static int PLOT_LPF_Z_AXIS_KEY = 5;

	// Plot keys for the mean filter plot
	private final static int PLOT_MEAN_X_AXIS_KEY = 6;
	private final static int PLOT_MEAN_Y_AXIS_KEY = 7;
	private final static int PLOT_MEAN_Z_AXIS_KEY = 8;

	// Plot keys for the noise bar plot
	private final static int BAR_PLOT_ACCEL_KEY = 0;
	private final static int BAR_PLOT_LPF_KEY = 1;
	private final static int BAR_PLOT_MEAN_KEY = 2;

	private boolean dataReady = false;

	// Indicate if the output should be logged to a .csv file
	private boolean logData = false;

	// Indicate if the AndDev LPF should be plotted
	private boolean lpfActive = false;

	// Indicate if the Mean Filter should be plotted
	private boolean meanFilterActive = false;
	
	private boolean invertAxisActive = false;

	private boolean plotLPFReady = false;
	private boolean plotMeanReady = false;

	private boolean run = false;

	private double dStdDevMeanZAxis = 0;

	// Touch to zoom constants for the dynamicPlot
	private float distance = 0;
	private float zoom = 1.2f;

	private float lpfTimeConstant = 1;
	private float meanFilterTimeConstant = 1;

	// Outputs for the acceleration and LPFs
	private float[] acceleration = new float[3];
	private float[] lpfOutput = new float[3];
	private float[] meanFilterOutput = new float[3];

	// The generation of the log output
	private int generation = 0;

	// Color keys for the acceleration plot
	private int plotAccelXAxisColor;
	private int plotAccelYAxisColor;
	private int plotAccelZAxisColor;

	// Color keys for the LPF Android Developer plot
	private int plotLPFXAxisColor;
	private int plotLPFYAxisColor;
	private int plotLPFZAxisColor;

	// Color keys for the mean filter plot
	private int plotMeanXAxisColor;
	private int plotMeanYAxisColor;
	private int plotMeanZAxisColor;

	// Log output time stamp
	private long logTime = 0;

	// Decimal formats for the UI outputs
	private DecimalFormat df;

	private DynamicBarPlot barPlot;
	// Graph plot for the UI outputs
	private DynamicLinePlot dynamicPlot;

	// Handler for the UI plots so everything plots smoothly
	private Handler handler;

	// Icon to indicate logging is active
	private ImageView iconLogger;

	// Low-Pass Filter
	private LowPassFilter lpf;

	// Mean filter
	private MeanFilter meanFilter;

	// Plot colors
	private PlotColor color;

	private Runnable runnable;

	// Sensor manager to access the accelerometer sensor
	private SensorManager sensorManager;

	private SettingsDialog settingsDialog;

	// RMS Noise levels
	private DescriptiveStatistics stdDevMaginitudeAccel;
	private DescriptiveStatistics stdDevMaginitude;
	private DescriptiveStatistics stdDevMaginitudeMean;

	private DescriptiveStatistics stdDevMaginitudeMeanZAxis;

	// Acceleration plot titles
	private String plotAccelXAxisTitle = "A-X";
	private String plotAccelYAxisTitle = "A-Y";
	private String plotAccelZAxisTitle = "A-Z";

	// LPF Android Developer plot tiltes
	private String plotLPFXAxisTitle = "LPF-X";
	private String plotLPFYAxisTitle = "LPF-Y";
	private String plotLPFZAxisTitle = "LPF-Z";

	// Mean filter plot tiltes
	private String plotMeanXAxisTitle = "M-X";
	private String plotMeanYAxisTitle = "M-Y";
	private String plotMeanZAxisTitle = "M-Z";
	private String plotStdDevMeanZAxisTitle = "StdDevMZ";

	// Output log
	private String log;

	// Acceleration UI outputs
	private TextView xAxis;
	private TextView yAxis;
	private TextView zAxis;

	private Thread thread;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.plot_sensor_activity);

		// Read in the saved prefs
		readPrefs();

		initTextOutputs();

		initIcons();

		initStatistics();

		initFilters();

		initColor();

		initPlots();

		sensorManager = (SensorManager) this
				.getSystemService(Context.SENSOR_SERVICE);

		handler = new Handler();

		runnable = new Runnable()
		{
			@Override
			public void run()
			{
				handler.postDelayed(this, 100);

				plotData();
				updateAccelerationText();
			}
		};
	}

	@Override
	public void onPause()
	{
		super.onPause();

		sensorManager.unregisterListener(this);

		if (logData)
		{
			writeLogToFile();
		}

		if (run && thread != null)
		{
			run = false;

			thread.interrupt();

			thread = null;
		}

		handler.removeCallbacks(runnable);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		readPrefs();
		
		// Reset the filters
		lpf.reset();
		meanFilter.reset();

		thread = new Thread(this);

		if (!run)
		{
			run = true;

			thread.start();
		}

		handler.post(runnable);

		// Register for sensor updates.
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{

	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		// Get a local copy of the sensor values
		System.arraycopy(event.values, 0, acceleration, 0, event.values.length);
		
		if(invertAxisActive)
		{
			acceleration[0] = -acceleration[0];
			acceleration[1] = -acceleration[1];
			acceleration[2] = -acceleration[2];
		}

		if (lpfActive)
		{
			lpfOutput = lpf.addSamples(acceleration);
		}
		if (meanFilterActive)
		{
			meanFilterOutput = meanFilter.filterFloat(acceleration);

			stdDevMaginitudeMeanZAxis.addValue(meanFilterOutput[2]);

			this.dStdDevMeanZAxis = stdDevMaginitudeMeanZAxis
					.getStandardDeviation();
		}

		dataReady = true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.settings_plot_menu, menu);
		return true;
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void updateMenu()
	{
		invalidateOptionsMenu();
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
		case R.id.action_log_data:
			startDataLog();
			updateMenu();
			return true;

			// Log the data
		case R.id.action_vector:
			Intent myIntent = new Intent(this, AccelerationVectorActivity.class);
			startActivity(myIntent);
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
	 * Manage the content of the options menu dynamically.
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);

		if (logData)
		{
			menu.getItem(2).getSubMenu().getItem(0).setEnabled(false);
		}
		else
		{
			menu.getItem(2).getSubMenu().getItem(0).setEnabled(true);
		}

		return true;
	}

	/**
	 * Pinch to zoom.
	 */
	@Override
	public boolean onTouch(View v, MotionEvent e)
	{
		// MotionEvent reports input details from the touch screen
		// and other input controls.
		float newDist = 0;

		switch (e.getAction())
		{

		case MotionEvent.ACTION_MOVE:

			// pinch to zoom
			if (e.getPointerCount() == 2)
			{
				if (distance == 0)
				{
					distance = fingerDist(e);
				}

				newDist = fingerDist(e);

				zoom *= distance / newDist;

				dynamicPlot.setMaxRange(zoom * Math.log(zoom));
				dynamicPlot.setMinRange(-zoom * Math.log(zoom));

				distance = newDist;
			}
		}

		return false;
	}

	/**
	 * Output and logs are run on their own thread to keep the UI from hanging
	 * and the output smooth.
	 */
	@Override
	public void run()
	{
		while (run && !Thread.currentThread().isInterrupted())
		{
			try
			{
				Thread.sleep(50);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}

			logData();
		}

		Thread.currentThread().interrupt();
	}
	
	@Override
	public void checkPlotPrefs()
	{
		readPrefs();
		checkLPFActive();
		checkMeanActive();
		
		lpf.setTimeConstant(this.lpfTimeConstant);
		meanFilter.setTimeConstant(this.meanFilterTimeConstant);
	}

	/**
	 * Indicate if the Android Developer LPF should be plotted.
	 * 
	 * @param lpfActive
	 *            Plot the filter if true.
	 */
	private void checkLPFActive()
	{
		if (this.lpfActive)
		{
			addLPFPlot();
		}
		else
		{
			removeLPFAndDevPlot();
		}
	}

	/**
	 * Indicate if the Mean Filter should be plotted.
	 * 
	 * @param meanFilterActive
	 *            Plot the filter if true.
	 */
	private void checkMeanActive()
	{
		if (this.meanFilterActive)
		{
			addMeanFilterPlot();
		}
		else
		{
			removeMeanFilterPlot();
		}
	}

	/**
	 * Create the output graph line chart.
	 */
	private void addAccelerationPlot()
	{
		addGraphPlot(plotAccelXAxisTitle, PLOT_ACCEL_X_AXIS_KEY,
				plotAccelXAxisColor);
		addGraphPlot(plotAccelYAxisTitle, PLOT_ACCEL_Y_AXIS_KEY,
				plotAccelYAxisColor);
		addGraphPlot(plotAccelZAxisTitle, PLOT_ACCEL_Z_AXIS_KEY,
				plotAccelZAxisColor);
	}

	/**
	 * Add a plot to the graph.
	 * 
	 * @param title
	 *            The name of the plot.
	 * @param key
	 *            The unique plot key
	 * @param color
	 *            The color of the plot
	 */
	private void addGraphPlot(String title, int key, int color)
	{
		dynamicPlot.addSeriesPlot(title, key, color);
	}

	/**
	 * Add the Android Developer LPF plot.
	 */
	private void addLPFPlot()
	{
		if (lpfActive && !plotLPFReady)
		{
			addGraphPlot(plotLPFXAxisTitle, PLOT_LPF_X_AXIS_KEY,
					plotLPFXAxisColor);
			addGraphPlot(plotLPFYAxisTitle, PLOT_LPF_Y_AXIS_KEY,
					plotLPFYAxisColor);
			addGraphPlot(plotLPFZAxisTitle, PLOT_LPF_Z_AXIS_KEY,
					plotLPFZAxisColor);

			plotLPFReady = true;
		}
	}

	/**
	 * Add the Mean Filter plot.
	 */
	private void addMeanFilterPlot()
	{
		if (meanFilterActive && !plotMeanReady)
		{
			addGraphPlot(plotMeanXAxisTitle, PLOT_MEAN_X_AXIS_KEY,
					plotMeanXAxisColor);
			addGraphPlot(plotMeanYAxisTitle, PLOT_MEAN_Y_AXIS_KEY,
					plotMeanYAxisColor);
			addGraphPlot(plotMeanZAxisTitle, PLOT_MEAN_Z_AXIS_KEY,
					plotMeanZAxisColor);

			plotMeanReady = true;
		}
	}

	/**
	 * Get the distance between fingers for the touch to zoom.
	 * 
	 * @param event
	 * @return
	 */
	private final float fingerDist(MotionEvent event)
	{
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return (float) Math.sqrt(x * x + y * y);
	}

	/**
	 * Create the plot colors.
	 */
	private void initColor()
	{
		color = new PlotColor(this);

		plotAccelXAxisColor = color.getDarkBlue();
		plotAccelYAxisColor = color.getDarkGreen();
		plotAccelZAxisColor = color.getDarkRed();

		plotLPFXAxisColor = color.getLightBlue();
		plotLPFYAxisColor = color.getLightGreen();
		plotLPFZAxisColor = color.getLightRed();

		plotMeanXAxisColor = color.getLightBlue();
		plotMeanYAxisColor = color.getLightGreen();
		plotMeanZAxisColor = color.getLightRed();
	}

	/**
	 * Initialize the activity icons.
	 */
	private void initIcons()
	{
		// Create the logger icon
		iconLogger = (ImageView) findViewById(R.id.icon_logger);
		iconLogger.setVisibility(View.INVISIBLE);
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
	 * Initialize the plots.
	 */
	private void initPlots()
	{
		View view = findViewById(R.id.ScrollView01);
		view.setOnTouchListener(this);

		// Create the graph plot
		XYPlot plot = (XYPlot) findViewById(R.id.plot_sensor);
		plot.setTitle("Acceleration");
		dynamicPlot = new DynamicLinePlot(plot);
		dynamicPlot.setMaxRange(21);
		dynamicPlot.setMinRange(-21);

		// setup the APR Levels plot:
		XYPlot noiseLevelsPlot = (XYPlot) findViewById(R.id.plot_noise);
		noiseLevelsPlot.setTitle("Noise");

		barPlot = new DynamicBarPlot(noiseLevelsPlot, "Sensor Noise");

		addAccelerationPlot();
		addLPFPlot();
		addMeanFilterPlot();
	}

	/**
	 * Initialize the statistics.
	 */
	private void initStatistics()
	{
		// Create the RMS Noise calculations
		stdDevMaginitudeAccel = new DescriptiveStatistics();
		stdDevMaginitudeAccel.setWindowSize(STD_DEV_SAMPLE_WINDOW);

		stdDevMaginitude = new DescriptiveStatistics();
		stdDevMaginitude.setWindowSize(STD_DEV_SAMPLE_WINDOW);

		stdDevMaginitudeMean = new DescriptiveStatistics();
		stdDevMaginitudeMean.setWindowSize(STD_DEV_SAMPLE_WINDOW);

		stdDevMaginitudeMeanZAxis = new DescriptiveStatistics();
		stdDevMaginitudeMeanZAxis.setWindowSize(180);
	}

	/**
	 * Initialize the Text View sensor outputs.
	 */
	private void initTextOutputs()
	{
		// Format the UI outputs so they look nice
		df = new DecimalFormat("#.##");

		// Create the acceleration UI outputs
		xAxis = (TextView) findViewById(R.id.value_x_axis);
		yAxis = (TextView) findViewById(R.id.value_y_axis);
		zAxis = (TextView) findViewById(R.id.value_z_axis);
	}

	/**
	 * Plot the output data in the UI.
	 */
	private void plotData()
	{
		updateGraphPlot();

		updateBarPlot();
	}

	/**
	 * Remove the Mean Filter plot.
	 */
	private void removeMeanFilterPlot()
	{
		if (!meanFilterActive && plotMeanReady)
		{
			plotMeanReady = false;

			removeGraphPlot(PLOT_MEAN_X_AXIS_KEY);
			removeGraphPlot(PLOT_MEAN_Y_AXIS_KEY);
			removeGraphPlot(PLOT_MEAN_Z_AXIS_KEY);
		}
	}

	/**
	 * Remove the Android Developer LPF plot.
	 */
	private void removeLPFAndDevPlot()
	{
		if (!lpfActive && plotLPFReady)
		{
			plotLPFReady = false;

			removeGraphPlot(PLOT_LPF_X_AXIS_KEY);
			removeGraphPlot(PLOT_LPF_Y_AXIS_KEY);
			removeGraphPlot(PLOT_LPF_Z_AXIS_KEY);
		}
	}

	/**
	 * Remove a plot from the graph.
	 * 
	 * @param key
	 */
	private void removeGraphPlot(int key)
	{
		dynamicPlot.removeSeriesPlot(key);
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
			settingsDialog = new SettingsDialog(this, this);
			settingsDialog.setCancelable(true);
			settingsDialog.setCanceledOnTouchOutside(true);
		}

		settingsDialog.show();
	}

	/**
	 * Begin logging data to an external .csv file.
	 */
	private void startDataLog()
	{
		if (logData == false)
		{
			generation = 0;

			stdDevMaginitudeMeanZAxis.clear();

			CharSequence text = "Logging Data";
			int duration = Toast.LENGTH_SHORT;

			Toast toast = Toast.makeText(this, text, duration);
			toast.show();

			String headers = "Generation" + ",";

			headers += "Timestamp" + ",";

			headers += this.plotAccelXAxisTitle + ",";

			headers += this.plotAccelYAxisTitle + ",";

			headers += this.plotAccelZAxisTitle + ",";

			if (lpfActive)
			{
				headers += this.plotLPFXAxisTitle + ",";

				headers += this.plotLPFYAxisTitle + ",";

				headers += this.plotLPFZAxisTitle + ",";
			}

			if (meanFilterActive)
			{
				headers += this.plotMeanXAxisTitle + ",";

				headers += this.plotMeanYAxisTitle + ",";

				headers += this.plotMeanZAxisTitle + ",";

				headers += this.plotStdDevMeanZAxisTitle + ",";
			}

			log = headers;

			log += System.getProperty("line.separator");

			iconLogger.setVisibility(View.VISIBLE);

			logData = true;
		}
		else
		{
			iconLogger.setVisibility(View.INVISIBLE);

			logData = false;
			writeLogToFile();
		}
	}

	/**
	 * Log output data to an external .csv file.
	 */
	private void logData()
	{
		if (logData && dataReady)
		{
			if (generation == 0)
			{
				logTime = System.currentTimeMillis();
			}

			log += generation++ + ",";
			log += df.format((System.currentTimeMillis() - logTime) / 1000.0f)
					+ ",";

			log += acceleration[0] + ",";
			log += acceleration[1] + ",";
			log += acceleration[2] + ",";

			if (lpfActive)
			{
				log += lpfOutput[0] + ",";
				log += lpfOutput[1] + ",";
				log += lpfOutput[2] + ",";
			}

			if (meanFilterActive)
			{
				log += meanFilterOutput[0] + ",";
				log += meanFilterOutput[1] + ",";
				log += meanFilterOutput[2] + ",";
				log += this.dStdDevMeanZAxis + ",";
			}

			log += System.getProperty("line.separator");

			dataReady = false;
		}
	}

	/**
	 * Write the logged data out to a persisted file.
	 */
	private void writeLogToFile()
	{
		Calendar c = Calendar.getInstance();
		String filename = "AccelerationFilter-" + c.get(Calendar.YEAR) + "-"
				+ (c.get(Calendar.MONTH) + 1) + "-"
				+ c.get(Calendar.DAY_OF_MONTH) + "-" + c.get(Calendar.HOUR)
				+ "-" + c.get(Calendar.MINUTE) + "-" + c.get(Calendar.SECOND)
				+ ".csv";

		File dir = new File(Environment.getExternalStorageDirectory()
				+ File.separator + "AccelerationFilter" + File.separator
				+ "Logs");
		if (!dir.exists())
		{
			dir.mkdirs();
		}

		File file = new File(dir, filename);

		FileOutputStream fos;
		byte[] data = log.getBytes();
		try
		{
			fos = new FileOutputStream(file);
			fos.write(data);
			fos.flush();
			fos.close();

			CharSequence text = "Log Saved";
			int duration = Toast.LENGTH_SHORT;

			Toast toast = Toast.makeText(this, text, duration);
			toast.show();
		}
		catch (FileNotFoundException e)
		{
			CharSequence text = e.toString();
			int duration = Toast.LENGTH_SHORT;

			Toast toast = Toast.makeText(this, text, duration);
			toast.show();
		}
		catch (IOException e)
		{
			// handle exception
		}
		finally
		{
			// Update the MediaStore so we can view the file without rebooting.
			// Note that it appears that the ACTION_MEDIA_MOUNTED approach is
			// now blocked for non-system apps on Android 4.4.
			MediaScannerConnection.scanFile(this, new String[]
			{ file.getPath() }, null,
					new MediaScannerConnection.OnScanCompletedListener()
					{
						@Override
						public void onScanCompleted(final String path,
								final Uri uri)
						{

						}
					});
		}
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

	/**
	 * Update the acceleration sensor output Text Views.
	 */
	private void updateAccelerationText()
	{
		// Update the view with the new acceleration data
		xAxis.setText(df.format(acceleration[0]));
		yAxis.setText(df.format(acceleration[1]));
		zAxis.setText(df.format(acceleration[2]));
	}

	/**
	 * Update the graph plot.
	 */
	private void updateGraphPlot()
	{
		dynamicPlot.setData(acceleration[0], PLOT_ACCEL_X_AXIS_KEY);
		dynamicPlot.setData(acceleration[1], PLOT_ACCEL_Y_AXIS_KEY);
		dynamicPlot.setData(acceleration[2], PLOT_ACCEL_Z_AXIS_KEY);

		if (lpfActive)
		{
			dynamicPlot
					.setData(lpfOutput[0], PLOT_LPF_X_AXIS_KEY);
			dynamicPlot
					.setData(lpfOutput[1], PLOT_LPF_Y_AXIS_KEY);
			dynamicPlot
					.setData(lpfOutput[2], PLOT_LPF_Z_AXIS_KEY);
		}

		if (meanFilterActive)
		{
			dynamicPlot.setData(meanFilterOutput[0], PLOT_MEAN_X_AXIS_KEY);
			dynamicPlot.setData(meanFilterOutput[1], PLOT_MEAN_Y_AXIS_KEY);
			dynamicPlot.setData(meanFilterOutput[2], PLOT_MEAN_Z_AXIS_KEY);
		}

		dynamicPlot.draw();
	}

	/**
	 * Update the bar plot.
	 */
	private void updateBarPlot()
	{
		Number[] seriesNumbers = new Number[3];

		stdDevMaginitudeAccel.addValue(Math.sqrt(Math.pow(acceleration[0], 2)
				+ Math.pow(acceleration[1], 2) + Math.pow(acceleration[2], 2)));

		double var = stdDevMaginitudeAccel.getStandardDeviation();

		if (var > MAX_NOISE_THRESHOLD)
		{
			var = MAX_NOISE_THRESHOLD;
		}

		seriesNumbers[BAR_PLOT_ACCEL_KEY] = var;

		if (plotLPFReady)
		{
			stdDevMaginitude.addValue(Math.sqrt(Math.pow(
					lpfOutput[0], 2)
					+ Math.pow(lpfOutput[1], 2)
					+ Math.pow(lpfOutput[2], 2)));

			var = stdDevMaginitude.getStandardDeviation();

			if (var > MAX_NOISE_THRESHOLD)
			{
				var = MAX_NOISE_THRESHOLD;
			}

			seriesNumbers[BAR_PLOT_LPF_KEY] = var;
		}
		if (!plotLPFReady)
		{
			seriesNumbers[BAR_PLOT_LPF_KEY] = 0;
		}
	
		if (plotMeanReady)
		{
			stdDevMaginitudeMean.addValue(Math.abs(meanFilterOutput[0])
					+ Math.abs(meanFilterOutput[1])
					+ Math.abs(meanFilterOutput[2]));

			var = stdDevMaginitudeMean.getStandardDeviation();

			if (var > MAX_NOISE_THRESHOLD)
			{
				var = MAX_NOISE_THRESHOLD;
			}

			seriesNumbers[BAR_PLOT_MEAN_KEY] = var;
		}

		if (!plotMeanReady)
		{
			seriesNumbers[BAR_PLOT_MEAN_KEY] = 0;
		}

		barPlot.onDataAvailable(seriesNumbers);
	}
}
