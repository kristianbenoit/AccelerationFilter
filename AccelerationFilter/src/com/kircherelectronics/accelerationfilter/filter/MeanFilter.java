package com.kircherelectronics.accelerationfilter.filter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.util.Log;

import com.kircherelectronics.accelerationfilter.activity.AccelerationPlotActivity;

/*
 * Copyright 2013, Kircher Electronics
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Implements a mean filter designed to smooth the data points based on a mean.
 * 
 * @author Kaleb
 * @version %I%, %G%
 * 
 */
public class MeanFilter
{
	private static final String tag = MeanFilter.class.getSimpleName();

	private float timeConstant = 1;
	private float startTime = 0;
	private float timestamp = 0;
	private float hz = 0;
	
	private int count = 0;
	// The size of the mean filters rolling window.
	private int filterWindow = 20;

	private boolean dataInit;

	private ArrayList<LinkedList<Number>> dataLists;

	/**
	 * Initialize a new MeanFilter object.
	 */
	public MeanFilter()
	{
		dataLists = new ArrayList<LinkedList<Number>>();
		dataInit = false;
	}

	public void setTimeConstant(float timeConstant)
	{
		this.timeConstant = timeConstant;
	}
	
	public void reset()
	{
		startTime = 0;
		timestamp = 0;
		count = 0;
		hz = 0;
	}
	
	/**
	 * Filter the data.
	 * 
	 * @param iterator
	 *            contains input the data.
	 * @return the filtered output data.
	 */
	public float[] filterFloat(float[] data)
	{
		// Initialize the start time.
		if (startTime == 0)
		{
			startTime = System.nanoTime();
		}

		timestamp = System.nanoTime();

		// Find the sample period (between updates) and convert from
		// nanoseconds to seconds. Note that the sensor delivery rates can
		// individually vary by a relatively large time frame, so we use an
		// averaging technique with the number of sensor updates to
		// determine the delivery rate.
		hz = (count++ / ((timestamp - startTime) / 1000000000.0f));
		
		filterWindow = (int) (hz * timeConstant);

		for (int i = 0; i < data.length; i++)
		{
			// Initialize the data structures for the data set.
			if (!dataInit)
			{
				dataLists.add(new LinkedList<Number>());
			}

			dataLists.get(i).addLast(data[i]);

			if (dataLists.get(i).size() > filterWindow)
			{
				dataLists.get(i).removeFirst();
			}
		}

		dataInit = true;

		float[] means = new float[dataLists.size()];

		for (int i = 0; i < dataLists.size(); i++)
		{
			means[i] = (float) getMean(dataLists.get(i));
		}

		return means;
	}

	/**
	 * Get the mean of the data set.
	 * 
	 * @param data
	 *            the data set.
	 * @return the mean of the data set.
	 */
	private float getMean(List<Number> data)
	{
		float m = 0;
		float count = 0;

		for (int i = 0; i < data.size(); i++)
		{
			m += data.get(i).floatValue();
			count++;
		}

		if (count != 0)
		{
			m = m / count;
		}

		return m;
	}


}
