package com.kircherelectronics.accelerationfilter.plot;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Arrays;

import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.util.SparseArray;

import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BarRenderer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYStepMode;

/**
 * Bar plot is responsible for plotting data on a bar graph. It is capable of
 * dynamically adding and removing plots as required by the user. However, the
 * total number of plots that could be plotted and names of the plots must be
 * known in advance.
 * 
 * @author Kaleb
 * @version %I%, %G%
 */
public class DynamicBarPlot
{
	// RMS Noise levels bar chart
	private XYPlot levelsPlot = null;

	// RMS Noise levels bar chart series
	private SimpleXYSeries levelsSeries = null;

	private String seriesTitle;

	private int count = 0;

	/**
	 * Initialize a new DynamicBarPlot.
	 * 
	 * @param noiseLevelsPlot
	 *            The plot.
	 * @param seriesTitle
	 *            The name of the plot.
	 */
	public DynamicBarPlot(XYPlot noiseLevelsPlot, String seriesTitle)
	{
		super();

		this.levelsPlot = noiseLevelsPlot;
		this.seriesTitle = seriesTitle;

		initPlot();
	}

	/**
	 * Add data to the plot.
	 * 
	 * @param seriesNumbers
	 *            The data to be plotted.
	 */
	public synchronized void onDataAvailable(Number[] seriesNumbers)
	{
		levelsSeries.setModel(Arrays.asList(seriesNumbers),
				SimpleXYSeries.ArrayFormat.Y_VALS_ONLY);

		levelsPlot.redraw();
	}

	/**
	 * Initialize the plot.
	 */
	private void initPlot()
	{
		levelsSeries = new SimpleXYSeries(seriesTitle);
		levelsSeries.useImplicitXVals();

		levelsPlot
				.addSeries(
						levelsSeries,
						new BarFormatter(Color.rgb(0, 153, 204), Color.rgb(0,
								153, 204)));

		// This needs to be changed with the number of plots, must be >= 1
		levelsPlot.setDomainStepValue(4);

		levelsPlot.setRangeStep(XYStepMode.INCREMENT_BY_VAL, .02);
		levelsPlot.setRangeValueFormat(new DecimalFormat("#.###"));

		// Fir the range. If we did not do this, the plot would
		// auto-range which can be visually confusing in the case of dynamic
		// plots.
		levelsPlot.setRangeBoundaries(0, 0.12, BoundaryMode.FIXED);

		// use our custom domain value formatter:
		levelsPlot.setDomainValueFormat(new DomainIndexFormat());

		// update our domain and range axis labels:
		levelsPlot.setDomainLabel("Output");
		levelsPlot.getDomainLabelWidget().pack();
		levelsPlot.setRangeLabel("RMS Amplitude");
		levelsPlot.getRangeLabelWidget().pack();
		levelsPlot.setGridPadding(15, 0, 15, 0);

		levelsPlot.getGraphWidget().setGridBackgroundPaint(null);
		levelsPlot.getGraphWidget().setGridDomainLinePaint(null);
		levelsPlot.getGraphWidget().setBackgroundPaint(null);
		levelsPlot.getGraphWidget().setBorderPaint(null);
		levelsPlot.getGraphWidget().setDomainOriginLinePaint(null);

		Paint paint = new Paint();

		paint.setStyle(Paint.Style.FILL_AND_STROKE);
		paint.setColor(Color.rgb(119, 119, 119));
		paint.setStrokeWidth(2);

		levelsPlot.getGraphWidget().setRangeOriginLinePaint(paint);
		// levelsPlot.getGraphWidget().setRangeValueFormat(new
		// NoiseRangeFormat());

		levelsPlot.setBorderPaint(null);
		levelsPlot.setBackgroundPaint(null);

		// get a ref to the BarRenderer so we can make some changes to it:
		BarRenderer barRenderer = (BarRenderer) levelsPlot
				.getRenderer(BarRenderer.class);
		if (barRenderer != null)
		{
			// make our bars a little thicker than the default so they can be
			// seen better:
			barRenderer.setBarWidth(25);
		}
	}

	/**
	 * A simple formatter to convert bar indexes into sensor names.
	 */
	private class DomainIndexFormat extends Format
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
				toAppendTo.append("WLPF");
				break;
			case 2:
				toAppendTo.append("ADLPF");
				break;
			case 3:
				toAppendTo.append("Mean");
				break;
			default:
				toAppendTo.append("Unknown");
			}
			return toAppendTo;
		}

		@Override
		public Object parseObject(String string, ParsePosition position)
		{
			return null;
		}
	}
}
