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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
 * Implements an Activity that is intended to run low-pass filters on
 * accelerometer inputs and then graph the outputs.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class AccelerationFilterActivity extends Activity implements
		SensorEventListener, Runnable, OnTouchListener
{

	// The size of the sample window that determines RMS Amplitude Noise
	// (standard deviation)
	private final static int SAMPLE_WINDOW = 50;

	// Indicate if the output should be logged to a .csv file
	private boolean logData = false;

	// Indicate if a static alpha should be used for the LPF Wikipedia
	private boolean staticWikiAlpha = false;

	// Indicate if a static alpha should be used for the LPF Android Developer
	private boolean staticAndDevAlpha = false;

	// Decimal formats for the UI outputs
	private DecimalFormat df;
	private DecimalFormat dfLong;

	// Graph plot for the UI outputs
	private DynamicPlot dynamicPlot;

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

	// Handler for the UI plots so everything plots smoothly
	private Handler handler;

	// Icon to indicate logging is active
	private ImageView iconLogger;

	// The generation of the log output
	private int generation = 0;

	// Plot keys for the acceleration plot
	private int plotAccelXAxisKey = 0;
	private int plotAccelYAxisKey = 1;
	private int plotAccelZAxisKey = 2;

	// Plot keys for the LPF Wikipedia plot
	private int plotLPFWikiXAxisKey = 3;
	private int plotLPFWikiYAxisKey = 4;
	private int plotLPFWikiZAxisKey = 5;

	// Plot keys for the LPF Android Developer plot
	private int plotLPFAndDevXAxisKey = 6;
	private int plotLPFAndDevYAxisKey = 7;
	private int plotLPFAndDevZAxisKey = 8;

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

	// Log output time stamp
	private long logTime = 0;

	// Low-Pass Filters
	private LowPassFilter lpfWiki;
	private LowPassFilter lpfAndDev;

	// Plot colors
	private PlotColor color;

	// Sensor manager to access the accelerometer sensor
	private SensorManager sensorManager;

	// RMS Noise levels bar chart series
	private SimpleXYSeries noiseLevelsSeries = null;

	// RMS Noise levels
	private StdDev varianceAccel;
	private StdDev varianceLPFWiki;
	private StdDev varianceLPFAndDev;

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

	// Output log
	private String log;

	// Acceleration UI outputs
	private TextView xAxis;
	private TextView yAxis;
	private TextView zAxis;

	// RMS Noise UI outputs
	private TextView rmsAccel;
	private TextView rmsLPFWiki;
	private TextView rmsLPFAndDev;

	// RMS Noise levels bar chart
	private XYPlot noiseLevelsPlot = null;

	/**
	 * Get the sample window size for the standard deviation.
	 * 
	 * @return Sample window size for the standard deviation.
	 */
	public static int getSampleWindow()
	{
		return SAMPLE_WINDOW;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.plot_sensor_activity);

		// Read in the saved prefs
		readPrefs();

		View view = findViewById(R.id.ScrollView01);
		view.setOnTouchListener(this);

		// Create the graph plot
		XYPlot plot = (XYPlot) findViewById(R.id.plot_sensor);
		plot.setTitle("Acceleration");
		dynamicPlot = new DynamicPlot(plot);
		dynamicPlot.setMaxRange(1.2);
		dynamicPlot.setMinRange(-1.2);

		// Create the acceleration UI outputs
		xAxis = (TextView) findViewById(R.id.value_x_axis);
		yAxis = (TextView) findViewById(R.id.value_y_axis);
		zAxis = (TextView) findViewById(R.id.value_z_axis);

		// Create the RMS Noise UI outputs
		rmsAccel = (TextView) findViewById(R.id.value_rms_acceleration);
		rmsLPFWiki = (TextView) findViewById(R.id.value_rms_lpf_wiki);
		rmsLPFAndDev = (TextView) findViewById(R.id.value_rms_lpf_and_dev);

		// Create the logger icon
		iconLogger = (ImageView) findViewById(R.id.icon_logger);
		iconLogger.setVisibility(View.INVISIBLE);

		// Format the UI outputs so they look nice
		df = new DecimalFormat("#.##");
		dfLong = new DecimalFormat("#.####");

		// Create the RMS Noise calculations
		varianceAccel = new StdDev();
		varianceLPFWiki = new StdDev();
		varianceLPFAndDev = new StdDev();

		// Get the sensor manager ready
		sensorManager = (SensorManager) this
				.getSystemService(Context.SENSOR_SERVICE);

		// Create the low-pass filters
		lpfWiki = new LPFWikipedia();
		lpfAndDev = new LPFAndroidDeveloper();

		// Initialize the low-pass filters with the saved prefs
		lpfWiki.setAlphaStatic(staticWikiAlpha);
		lpfWiki.setAlpha(wikiAlpha);

		lpfAndDev.setAlphaStatic(staticAndDevAlpha);
		lpfAndDev.setAlpha(andDevAlpha);

		// Initialize the plots
		initColor();
		initAccelerationPlot();
		initNoisePlot();

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

		lpfWikiOutput = lpfWiki.addSamples(acceleration);
		lpfAndDevOutput = lpfAndDev.addSamples(acceleration);
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
	 * Show a settings dialog.
	 */
	private void showSettingsDialog()
	{
		SettingsDialog dialog = new SettingsDialog(this, lpfWiki, lpfAndDev);
		dialog.setCancelable(true);
		dialog.setCanceledOnTouchOutside(true);

		dialog.show();
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
	}

	/**
	 * Create the output graph line chart.
	 */
	private void initAccelerationPlot()
	{
		addPlot(plotAccelXAxisTitle, plotAccelXAxisKey, plotAccelXAxisColor);
		addPlot(plotAccelYAxisTitle, plotAccelYAxisKey, plotAccelYAxisColor);
		addPlot(plotAccelZAxisTitle, plotAccelZAxisKey, plotAccelZAxisColor);

		addPlot(plotLPFWikiXAxisTitle, plotLPFWikiXAxisKey,
				plotLPFWikiXAxisColor);
		addPlot(plotLPFWikiYAxisTitle, plotLPFWikiYAxisKey,
				plotLPFWikiYAxisColor);
		addPlot(plotLPFWikiZAxisTitle, plotLPFWikiZAxisKey,
				plotLPFWikiZAxisColor);

		addPlot(plotLPFAndDevXAxisTitle, plotLPFAndDevXAxisKey,
				plotLPFAndDevXAxisColor);
		addPlot(plotLPFAndDevYAxisTitle, plotLPFAndDevYAxisKey,
				plotLPFAndDevYAxisColor);
		addPlot(plotLPFAndDevZAxisTitle, plotLPFAndDevZAxisKey,
				plotLPFAndDevZAxisColor);
	}

	/**
	 * Create the RMS Noise bar chart.
	 */
	private void initNoisePlot()
	{
		// setup the APR Levels plot:
		noiseLevelsPlot = (XYPlot) findViewById(R.id.plot_noise);
		noiseLevelsPlot.setTitle("Noise");

		noiseLevelsSeries = new SimpleXYSeries("Noise Levels");
		noiseLevelsSeries.useImplicitXVals();
		BarFormatter bf = new BarFormatter(Color.rgb(0, 153, 204), Color.rgb(0,
				153, 204));

		Paint fillPaint = new Paint();
		fillPaint.setAntiAlias(true);
		fillPaint.setStyle(Paint.Style.STROKE);
		fillPaint.setColor(Color.rgb(0, 153, 204));
		fillPaint.setStrokeWidth(20);

		bf.setFillPaint(fillPaint);

		noiseLevelsPlot.addSeries(noiseLevelsSeries, bf);

		noiseLevelsPlot.setDomainStepValue(3);
		noiseLevelsPlot.setTicksPerRangeLabel(3);
		noiseLevelsPlot.setRangeStepValue(1);

		// per the android documentation, the minimum and maximum readings we
		// can get from
		// any of the orientation sensors is -180 and 359 respectively so we
		// will fix our plot's
		// boundaries to those values. If we did not do this, the plot would
		// auto-range which
		// can be visually confusing in the case of dynamic plots.
		noiseLevelsPlot.setRangeBoundaries(0, 0.1, BoundaryMode.FIXED);

		// use our custom domain value formatter:
		noiseLevelsPlot.setDomainValueFormat(new NoiseIndexFormat());

		// update our domain and range axis labels:
		noiseLevelsPlot.setDomainLabel("Output");
		noiseLevelsPlot.getDomainLabelWidget().pack();
		noiseLevelsPlot.setRangeLabel("RMS Amplitude");
		noiseLevelsPlot.getRangeLabelWidget().pack();
		noiseLevelsPlot.setGridPadding(45, 0, 45, 0);

		noiseLevelsPlot.setGridPadding(15, 15, 15, 15);
		noiseLevelsPlot.getGraphWidget().setGridBackgroundPaint(null);
		noiseLevelsPlot.getGraphWidget().setGridDomainLinePaint(null);
		noiseLevelsPlot.getGraphWidget().setGridRangeLinePaint(null);
		noiseLevelsPlot.getGraphWidget().setBackgroundPaint(null);
		noiseLevelsPlot.getGraphWidget().setBorderPaint(null);

		Paint paint = new Paint();

		paint.setStyle(Paint.Style.FILL_AND_STROKE);
		paint.setColor(Color.rgb(119, 119, 119));
		paint.setStrokeWidth(2);

		noiseLevelsPlot.getGraphWidget().setDomainOriginLinePaint(null);
		noiseLevelsPlot.getGraphWidget().setRangeOriginLinePaint(paint);

		noiseLevelsPlot.setBorderPaint(null);
		noiseLevelsPlot.setBackgroundPaint(null);
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
	private void addPlot(String title, int key, int color)
	{
		dynamicPlot.addSeriesPlot(title, key, color);
	}

	/**
	 * Remove a plot from the graph.
	 * 
	 * @param key
	 */
	private void removePlot(int key)
	{
		dynamicPlot.removeSeriesPlot(key);
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
	 * Plot the output data in the UI.
	 */
	private void plotData()
	{
		dynamicPlot.setData(acceleration[0], plotAccelXAxisKey);
		dynamicPlot.setData(acceleration[1], plotAccelYAxisKey);
		dynamicPlot.setData(acceleration[2], plotAccelZAxisKey);

		dynamicPlot.setData(lpfWikiOutput[0], plotLPFWikiXAxisKey);
		dynamicPlot.setData(lpfWikiOutput[1], plotLPFWikiYAxisKey);
		dynamicPlot.setData(lpfWikiOutput[2], plotLPFWikiZAxisKey);

		dynamicPlot.setData(lpfAndDevOutput[0], plotLPFAndDevXAxisKey);
		dynamicPlot.setData(lpfAndDevOutput[1], plotLPFAndDevYAxisKey);
		dynamicPlot.setData(lpfAndDevOutput[2], plotLPFAndDevZAxisKey);

		dynamicPlot.draw();

		// Update the view with the new acceleration data
		xAxis.setText(df.format(acceleration[0]));
		yAxis.setText(df.format(acceleration[1]));
		zAxis.setText(df.format(acceleration[2]));

		// Update the view with the new acceleration data

		Number[] series1Numbers =
		{
				varianceAccel
						.addSample(Math.abs(acceleration[0])
								+ Math.abs(acceleration[1])
								+ Math.abs(acceleration[2])),
				varianceLPFWiki.addSample(Math.abs(lpfWikiOutput[0])
						+ Math.abs(lpfWikiOutput[1])
						+ Math.abs(lpfWikiOutput[2])),
				varianceLPFAndDev.addSample(Math.abs(lpfAndDevOutput[0])
						+ Math.abs(lpfAndDevOutput[1])
						+ Math.abs(lpfAndDevOutput[2])) };
		noiseLevelsSeries.setModel(Arrays.asList(series1Numbers),
				SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

		noiseLevelsPlot.redraw();

		rmsAccel.setText(dfLong.format(varianceAccel.addSample(Math
				.abs(acceleration[0])
				+ Math.abs(acceleration[1])
				+ Math.abs(acceleration[2]))));

		rmsLPFWiki.setText(dfLong.format(varianceLPFWiki.addSample(Math
				.abs(lpfWikiOutput[0])
				+ Math.abs(lpfWikiOutput[1])
				+ Math.abs(lpfWikiOutput[2]))));

		rmsLPFAndDev.setText(dfLong.format(varianceLPFAndDev.addSample(Math
				.abs(lpfAndDevOutput[0])
				+ Math.abs(lpfAndDevOutput[1])
				+ Math.abs(lpfAndDevOutput[2]))));

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
	 * Read in the current user preferences.
	 */
	private void readPrefs()
	{
		SharedPreferences prefs = this.getSharedPreferences("lpf_prefs",
				Activity.MODE_PRIVATE);

		this.staticWikiAlpha = prefs.getBoolean("show_alpha_wiki", false);
		this.staticAndDevAlpha = prefs.getBoolean("show_alpha_and_dev", false);

		this.wikiAlpha = prefs.getFloat("wiki_alpha", 0.1f);
		this.andDevAlpha = prefs.getFloat("and_dev_alpha", 0.1f);

	}

	/**
	 * A simple formatter to convert bar indexes into sensor names.
	 */
	private class NoiseIndexFormat extends Format
	{

		@Override
		public StringBuffer format(Object obj, StringBuffer toAppendTo,
				FieldPosition pos)
		{
			Number num = (Number) obj;

			// using num.intValue() will floor the value, so we add 0.5 to round
			// instead:
			int roundNum = (int) (num.floatValue() + 0.5f);
			switch (roundNum)
			{
			case 0:
				toAppendTo.append("Accel");
				break;
			case 1:
				toAppendTo.append("LPFWiki");
				break;
			case 2:
				toAppendTo.append("LPFAndDev");
				break;
			default:
				toAppendTo.append("Unknown");
			}
			return toAppendTo;
		}

		@Override
		public Object parseObject(String string, ParsePosition position)
		{
			// TODO Auto-generated method stub
			return null;
		}
	}
}
