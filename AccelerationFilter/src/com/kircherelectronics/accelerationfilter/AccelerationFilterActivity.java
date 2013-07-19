package com.kircherelectronics.accelerationfilter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Arrays;
import java.util.Calendar;

import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.kircherelectronics.accelerationfilter.dialog.SettingsDialog;
import com.kircherelectronics.accelerationfilter.filter.LPFAndroidDeveloper;
import com.kircherelectronics.accelerationfilter.filter.LPFWikipedia;
import com.kircherelectronics.accelerationfilter.filter.LowPassFilter;
import com.kircherelectronics.accelerationfilter.filter.MeanFilter;
import com.kircherelectronics.accelerationfilter.plot.DynamicBarPlot;
import com.kircherelectronics.accelerationfilter.plot.DynamicLinePlot;
import com.kircherelectronics.accelerationfilter.plot.PlotColor;
import com.kircherelectronics.accelerationfilter.statistics.StdDev;

import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

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
 * Currently supports two versions of IIR digital low-pass filter. The low-pass
 * filters are classified as recursive, or infinite response filters (IIR). The
 * current, nth sample output depends on both current and previous inputs as
 * well as previous outputs. It is essentially a weighted moving average, which
 * comes in many different flavors depending on the values for the coefficients,
 * a and b.
 * 
 * The first low-pass filter, the Wikipedia LPF, is an IIR single-pole
 * implementation. The coefficient, a (alpha), can be adjusted based on the
 * sample period of the sensor to produce the desired time constant that the
 * filter will act on. It takes a simple form of y[i] = y[i] + alpha * (x[i] -
 * y[i]). Alpha is defined as alpha = dt / (timeConstant + dt);) where the time
 * constant is the length of signals the filter should act on and dt is the
 * sample period (1/frequency) of the sensor.
 * 
 * The second low-pass filter, the Android Developer LPF, is an IIR single-pole
 * implementation. The coefficient, a (alpha), can be adjusted based on the
 * sample period of the sensor to produce the desired time constant that the
 * filter will act on. It is essentially the same as the Wikipedia LPF. It takes
 * a simple form of y[0] = alpha * y[0] + (1 - alpha) * x[0]. Alpha is defined
 * as alpha = timeConstant / (timeConstant + dt) where the time constant is the
 * length of signals the filter should act on and dt is the sample period
 * (1/frequency) of the sensor.
 * 
 * A finite impulse response (FIR) moving average filter is also implemented.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class AccelerationFilterActivity extends Activity implements
		SensorEventListener, Runnable, OnTouchListener
{

	// Only noise below this threshold will be plotted
	private final static float MAX_NOISE_THRESHOLD = 0.03f;

	// The size of the sample window that determines RMS Amplitude Noise
	// (standard deviation)
	private static int MEAN_SAMPLE_WINDOW = 20;

	// Plot keys for the acceleration plot
	private final static int PLOT_ACCEL_X_AXIS_KEY = 0;
	private final static int PLOT_ACCEL_Y_AXIS_KEY = 1;
	private final static int PLOT_ACCEL_Z_AXIS_KEY = 2;

	// Plot keys for the LPF Wikipedia plot
	private final static int PLOT_LPF_WIKI_X_AXIS_KEY = 3;
	private final static int PLOT_LPF_WIKI_Y_AXIS_KEY = 4;
	private final static int PLOT_LPF_WIKI_Z_AXIS_KEY = 5;

	// Plot keys for the LPF Android Developer plot
	private final static int PLOT_LPF_AND_DEV_X_AXIS_KEY = 6;
	private final static int PLOT_LPF_AND_DEV_Y_AXIS_KEY = 7;
	private final static int PLOT_LPF_AND_DEV_Z_AXIS_KEY = 8;

	// Plot keys for the mean filter plot
	private final static int PLOT_MEAN_X_AXIS_KEY = 9;
	private final static int PLOT_MEAN_Y_AXIS_KEY = 10;
	private final static int PLOT_MEAN_Z_AXIS_KEY = 11;

	// Plot keys for the noise bar plot
	private final static int BAR_PLOT_ACCEL_KEY = 0;
	private final static int BAR_PLOT_LPF_WIKI_KEY = 1;
	private final static int BAR_PLOT_LPF_AND_DEV_KEY = 2;
	private final static int BAR_PLOT_MEAN_KEY = 3;

	// Indicate if the output should be logged to a .csv file
	private boolean logData = false;

	// Indicate if the AndDev LPF should be plotted
	private boolean plotLPFAndDev = false;

	// Indicate if the Wiki LPF should be plotted
	private boolean plotLPFWiki = false;

	// Indicate if the Mean Filter should be plotted
	private boolean plotMeanFilter = false;

	// Indicate the plots are ready to accept inputs
	private boolean plotLPFWikiReady = false;
	private boolean plotLPFAndDevReady = false;
	private boolean plotMeanReady = false;

	// Decimal formats for the UI outputs
	private DecimalFormat df;

	private SettingsDialog settingsDialog;

	private DynamicBarPlot barPlot;
	// Graph plot for the UI outputs
	private DynamicLinePlot dynamicPlot;

	// The static alpha for the LPF Wikipedia
	private float wikiAlpha;
	// The static alpha for the LPF Android Developer
	private float andDevAlpha;

	// Touch to zoom constants for the dynamicPlot
	private float distance = 0;
	private float zoom = 1.2f;

	// Outputs for the acceleration and LPFs
	private float[] acceleration = new float[3];
	private float[] lpfWikiOutput = new float[3];
	private float[] lpfAndDevOutput = new float[3];
	private float[] meanFilterOutput = new float[3];

	// Handler for the UI plots so everything plots smoothly
	private Handler handler;

	// Icon to indicate logging is active
	private ImageView iconLogger;

	// The generation of the log output
	private int generation = 0;

	// Color keys for the acceleration plot
	private int plotAccelXAxisColor;
	private int plotAccelYAxisColor;
	private int plotAccelZAxisColor;

	// Color keys for the LPF Wikipedia plot
	private int plotLPFWikiXAxisColor;
	private int plotLPFWikiYAxisColor;
	private int plotLPFWikiZAxisColor;

	// Color keys for the LPF Android Developer plot
	private int plotLPFAndDevXAxisColor;
	private int plotLPFAndDevYAxisColor;
	private int plotLPFAndDevZAxisColor;

	// Color keys for the mean filter plot
	private int plotMeanXAxisColor;
	private int plotMeanYAxisColor;
	private int plotMeanZAxisColor;

	// Log output time stamp
	private long logTime = 0;

	// Low-Pass Filters
	private LowPassFilter lpfWiki;
	private LowPassFilter lpfAndDev;

	private MeanFilter meanFilter;

	// Plot colors
	private PlotColor color;

	// Sensor manager to access the accelerometer sensor
	private SensorManager sensorManager;

	// RMS Noise levels
	private StdDev varianceAccel;
	private StdDev varianceLPFWiki;
	private StdDev varianceLPFAndDev;
	private StdDev varianceMean;

	// Acceleration plot titles
	private String plotAccelXAxisTitle = "AX";
	private String plotAccelYAxisTitle = "AY";
	private String plotAccelZAxisTitle = "AZ";

	// LPF Wikipedia plot titles
	private String plotLPFWikiXAxisTitle = "WX";
	private String plotLPFWikiYAxisTitle = "WY";
	private String plotLPFWikiZAxisTitle = "WZ";

	// LPF Android Developer plot tiltes
	private String plotLPFAndDevXAxisTitle = "ADX";
	private String plotLPFAndDevYAxisTitle = "ADY";
	private String plotLPFAndDevZAxisTitle = "ADZ";

	// Mean filter plot tiltes
	private String plotMeanXAxisTitle = "MX";
	private String plotMeanYAxisTitle = "MY";
	private String plotMeanZAxisTitle = "MZ";

	// Output log
	private String log;

	// Acceleration UI outputs
	private TextView xAxis;
	private TextView yAxis;
	private TextView zAxis;

	/**
	 * Get the sample window size for the standard deviation.
	 * 
	 * @return Sample window size for the standard deviation.
	 */
	public static int getSampleWindow()
	{
		return MEAN_SAMPLE_WINDOW;
	}

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

		handler.removeCallbacks(this);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		readPrefs();

		handler.post(this);

		// Register for sensor updates.
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_FASTEST);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		// Get a local copy of the sensor values
		System.arraycopy(event.values, 0, acceleration, 0, event.values.length);

		acceleration[0] = acceleration[0] / SensorManager.GRAVITY_EARTH;
		acceleration[1] = acceleration[1] / SensorManager.GRAVITY_EARTH;
		acceleration[2] = acceleration[2] / SensorManager.GRAVITY_EARTH;

		if (plotLPFWiki)
		{
			lpfWikiOutput = lpfWiki.addSamples(acceleration);
		}
		if (plotLPFAndDev)
		{
			lpfAndDevOutput = lpfAndDev.addSamples(acceleration);
		}
		if (plotMeanFilter)
		{
			meanFilterOutput = meanFilter.filterFloat(acceleration);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.settings_logger_menu, menu);
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
		case R.id.menu_settings_logger_plotdata:
			startDataLog();
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
		handler.postDelayed(this, 100);

		plotData();
		logData();
	}

	/**
	 * Indicate if the Wikipedia LPF should be plotted.
	 * 
	 * @param plotLPFWiki
	 *            Plot the filter if true.
	 */
	public void setPlotLPFWiki(boolean plotLPFWiki)
	{
		this.plotLPFWiki = plotLPFWiki;

		if (this.plotLPFWiki)
		{
			addLPFWikiPlot();
		}
		else
		{
			removeLPFWikiPlot();
		}
	}

	/**
	 * Indicate if the Android Developer LPF should be plotted.
	 * 
	 * @param plotLPFAndDev
	 *            Plot the filter if true.
	 */
	public void setPlotLPFAndDev(boolean plotLPFAndDev)
	{
		this.plotLPFAndDev = plotLPFAndDev;

		if (this.plotLPFAndDev)
		{
			addLPFAndDevPlot();
		}
		else
		{
			removeLPFAndDevPlot();
		}
	}

	/**
	 * Indicate if the Mean Filter should be plotted.
	 * 
	 * @param plotMean
	 *            Plot the filter if true.
	 */
	public void setPlotMean(boolean plotMean)
	{
		this.plotMeanFilter = plotMean;

		if (this.plotMeanFilter)
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
	private void addLPFAndDevPlot()
	{
		if (plotLPFAndDev)
		{
			addGraphPlot(plotLPFAndDevXAxisTitle, PLOT_LPF_AND_DEV_X_AXIS_KEY,
					plotLPFAndDevXAxisColor);
			addGraphPlot(plotLPFAndDevYAxisTitle, PLOT_LPF_AND_DEV_Y_AXIS_KEY,
					plotLPFAndDevYAxisColor);
			addGraphPlot(plotLPFAndDevZAxisTitle, PLOT_LPF_AND_DEV_Z_AXIS_KEY,
					plotLPFAndDevZAxisColor);

			plotLPFAndDevReady = true;
		}
	}

	/**
	 * Add the Wikipedia LPF plot.
	 */
	private void addLPFWikiPlot()
	{
		if (plotLPFWiki)
		{
			addGraphPlot(plotLPFWikiXAxisTitle, PLOT_LPF_WIKI_X_AXIS_KEY,
					plotLPFWikiXAxisColor);
			addGraphPlot(plotLPFWikiYAxisTitle, PLOT_LPF_WIKI_Y_AXIS_KEY,
					plotLPFWikiYAxisColor);
			addGraphPlot(plotLPFWikiZAxisTitle, PLOT_LPF_WIKI_Z_AXIS_KEY,
					plotLPFWikiZAxisColor);

			plotLPFWikiReady = true;
		}
	}

	/**
	 * Add the Mean Filter plot.
	 */
	private void addMeanFilterPlot()
	{
		if (plotMeanFilter)
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

		plotLPFWikiXAxisColor = color.getMidBlue();
		plotLPFWikiYAxisColor = color.getMidGreen();
		plotLPFWikiZAxisColor = color.getMidRed();

		plotLPFAndDevXAxisColor = color.getLightBlue();
		plotLPFAndDevYAxisColor = color.getLightGreen();
		plotLPFAndDevZAxisColor = color.getLightRed();

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
		// Create the low-pass filters
		lpfWiki = new LPFWikipedia();
		lpfAndDev = new LPFAndroidDeveloper();
		meanFilter = new MeanFilter();

		meanFilter.setWindowSize(MEAN_SAMPLE_WINDOW);

		// Initialize the low-pass filters with the saved prefs
		lpfWiki.setAlphaStatic(plotLPFAndDev);
		lpfWiki.setAlpha(wikiAlpha);

		lpfAndDev.setAlphaStatic(plotLPFWiki);
		lpfAndDev.setAlpha(andDevAlpha);

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
		dynamicPlot.setMaxRange(1.2);
		dynamicPlot.setMinRange(-1.2);

		// setup the APR Levels plot:
		XYPlot noiseLevelsPlot = (XYPlot) findViewById(R.id.plot_noise);
		noiseLevelsPlot.setTitle("Noise");

		barPlot = new DynamicBarPlot(noiseLevelsPlot, "Sensor Noise");

		addAccelerationPlot();
		addLPFAndDevPlot();
		addLPFWikiPlot();
		addMeanFilterPlot();
	}

	/**
	 * Initialize the statistics.
	 */
	private void initStatistics()
	{
		// Create the RMS Noise calculations
		varianceAccel = new StdDev();
		varianceLPFWiki = new StdDev();
		varianceLPFAndDev = new StdDev();
		varianceMean = new StdDev();
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

		updateAccelerationText();

		updateBarPlot();
	}

	/**
	 * Remove the Mean Filter plot.
	 */
	private void removeMeanFilterPlot()
	{
		if (!plotMeanFilter)
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
		if (!plotLPFAndDev)
		{
			plotLPFAndDevReady = false;

			removeGraphPlot(PLOT_LPF_AND_DEV_X_AXIS_KEY);
			removeGraphPlot(PLOT_LPF_AND_DEV_Y_AXIS_KEY);
			removeGraphPlot(PLOT_LPF_AND_DEV_Z_AXIS_KEY);
		}
	}

	/**
	 * Remove the Wikipedia LPF plot.
	 */
	private void removeLPFWikiPlot()
	{
		if (!plotLPFWiki)
		{
			plotLPFWikiReady = false;

			removeGraphPlot(PLOT_LPF_WIKI_X_AXIS_KEY);
			removeGraphPlot(PLOT_LPF_WIKI_Y_AXIS_KEY);
			removeGraphPlot(PLOT_LPF_WIKI_Z_AXIS_KEY);
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

		helpDialog.setContentView(getLayoutInflater().inflate(R.layout.help,
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
			settingsDialog = new SettingsDialog(this, lpfWiki, lpfAndDev,
					meanFilter);
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
			CharSequence text = "Logging Data";
			int duration = Toast.LENGTH_SHORT;

			Toast toast = Toast.makeText(this, text, duration);
			toast.show();

			String headers = "Generation" + ",";

			headers += "Timestamp" + ",";

			headers += this.plotAccelXAxisTitle + ",";

			headers += this.plotAccelYAxisTitle + ",";

			headers += this.plotAccelZAxisTitle + ",";

			headers += this.plotLPFWikiXAxisTitle + ",";

			headers += this.plotLPFWikiYAxisTitle + ",";

			headers += this.plotLPFWikiZAxisTitle + ",";

			headers += this.plotLPFAndDevXAxisTitle + ",";

			headers += this.plotLPFAndDevYAxisTitle + ",";

			headers += this.plotLPFAndDevZAxisTitle + ",";

			headers += this.plotMeanXAxisTitle + ",";

			headers += this.plotMeanYAxisTitle + ",";

			headers += this.plotMeanZAxisTitle + ",";

			log = headers + "\n";

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
		if (logData)
		{
			if (generation == 0)
			{
				logTime = System.currentTimeMillis();
			}

			log += System.getProperty("line.separator");
			log += generation++ + ",";
			log += System.currentTimeMillis() - logTime + ",";

			log += acceleration[0] + ",";
			log += acceleration[1] + ",";
			log += acceleration[2] + ",";

			log += lpfWikiOutput[0] + ",";
			log += lpfWikiOutput[1] + ",";
			log += lpfWikiOutput[2] + ",";

			log += lpfAndDevOutput[0] + ",";
			log += lpfAndDevOutput[1] + ",";
			log += lpfAndDevOutput[2] + ",";

			log += meanFilterOutput[0] + ",";
			log += meanFilterOutput[1] + ",";
			log += meanFilterOutput[2] + ",";
		}
	}

	/**
	 * Write the logged data out to a persisted file.
	 */
	private void writeLogToFile()
	{
		Calendar c = Calendar.getInstance();
		String filename = "AccelerationFilter-" + c.get(Calendar.YEAR) + "-"
				+ c.get(Calendar.DAY_OF_WEEK_IN_MONTH) + "-"
				+ c.get(Calendar.HOUR) + "-" + c.get(Calendar.HOUR) + "-"
				+ c.get(Calendar.MINUTE) + "-" + c.get(Calendar.SECOND)
				+ ".csv";

		File dir = new File(Environment.getExternalStorageDirectory()
				+ File.separator + "AccelerationFilter" + File.separator
				+ "Logs" + File.separator + "Acceleration");
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
			this.sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri
					.parse("file://"
							+ Environment.getExternalStorageDirectory())));
		}
	}

	/**
	 * Read in the current user preferences.
	 */
	private void readPrefs()
	{
		SharedPreferences prefs = this.getSharedPreferences("lpf_prefs",
				Activity.MODE_PRIVATE);

		this.plotLPFAndDev = prefs.getBoolean("plot_lpf_and_dev", false);
		this.plotLPFWiki = prefs.getBoolean("plot_lpf_wiki", false);
		this.plotMeanFilter = prefs.getBoolean("plot_mean", false);

		this.wikiAlpha = prefs.getFloat("lpf_wiki_alpha", 0.1f);
		this.andDevAlpha = prefs.getFloat("lpf_and_dev_alpha", 0.9f);
		MEAN_SAMPLE_WINDOW = prefs.getInt("window_mean", 50);
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

		if (plotLPFWiki)
		{
			dynamicPlot.setData(lpfWikiOutput[0], PLOT_LPF_WIKI_X_AXIS_KEY);
			dynamicPlot.setData(lpfWikiOutput[1], PLOT_LPF_WIKI_Y_AXIS_KEY);
			dynamicPlot.setData(lpfWikiOutput[2], PLOT_LPF_WIKI_Z_AXIS_KEY);
		}

		if (plotLPFAndDev)
		{
			dynamicPlot
					.setData(lpfAndDevOutput[0], PLOT_LPF_AND_DEV_X_AXIS_KEY);
			dynamicPlot
					.setData(lpfAndDevOutput[1], PLOT_LPF_AND_DEV_Y_AXIS_KEY);
			dynamicPlot
					.setData(lpfAndDevOutput[2], PLOT_LPF_AND_DEV_Z_AXIS_KEY);
		}

		if (plotMeanFilter)
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
		Number[] seriesNumbers = new Number[4];

		double var = varianceAccel.addSample(Math.abs(acceleration[0])
				+ Math.abs(acceleration[1]) + Math.abs(acceleration[2]));

		if (var > MAX_NOISE_THRESHOLD)
		{
			var = MAX_NOISE_THRESHOLD;
		}

		seriesNumbers[BAR_PLOT_ACCEL_KEY] = var;

		if (plotLPFAndDevReady)
		{

			var = varianceLPFAndDev.addSample(Math.abs(lpfAndDevOutput[0])
					+ Math.abs(lpfAndDevOutput[1])
					+ Math.abs(lpfAndDevOutput[2]));

			if (var > MAX_NOISE_THRESHOLD)
			{
				var = MAX_NOISE_THRESHOLD;
			}

			seriesNumbers[BAR_PLOT_LPF_AND_DEV_KEY] = var;
		}
		if (!plotLPFAndDevReady)
		{
			seriesNumbers[BAR_PLOT_LPF_AND_DEV_KEY] = 0;
		}

		if (plotLPFWikiReady)
		{
			var = varianceLPFWiki.addSample(Math.abs(lpfWikiOutput[0])
					+ Math.abs(lpfWikiOutput[1]) + Math.abs(lpfWikiOutput[2]));

			if (var > MAX_NOISE_THRESHOLD)
			{
				var = MAX_NOISE_THRESHOLD;
			}

			seriesNumbers[BAR_PLOT_LPF_WIKI_KEY] = var;
		}
		if (!plotLPFWikiReady)
		{
			seriesNumbers[BAR_PLOT_LPF_WIKI_KEY] = 0;
		}

		if (plotMeanReady)
		{
			var = varianceMean.addSample(Math.abs(meanFilterOutput[0])
					+ Math.abs(meanFilterOutput[1])
					+ Math.abs(meanFilterOutput[2]));

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
