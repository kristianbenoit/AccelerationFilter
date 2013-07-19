package com.kircherelectronics.accelerationfilter.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import android.util.Log;

import com.kircherelectronics.accelerationfilter.AccelerationFilterActivity;

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
 * Currently not working.
 * 
 * @author Kaleb
 * @version %I%, %G%
 * 
 */
public class LPFWindowedSinc
{
	// The size of the mean filters rolling window.
	private int filterWindow = AccelerationFilterActivity.getSampleWindow();

	private boolean dataInit;

	private ArrayList<LinkedList<Number>> dataLists;

	/**
	 * Initialize a new MeanFilter object.
	 */
	public LPFWindowedSinc()
	{
		dataLists = new ArrayList<LinkedList<Number>>();
		dataInit = false;
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
			means[i] = (float) getOuput(dataLists.get(i));
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
	private float getOuput(List<Number> data)
	{
		float fc = 0.14f;
		int m = data.size();
		
		float[] h = new float[m];
		
		for (int i = 0; i < m; i++)
		{
			if(i - m/2 == 0)
			{
				h[i] = (float) (2*Math.PI*fc);
			}
			else
			{
				h[i] = (float) (Math.sin(2*Math.PI*fc * i-m/2)/i-m/2);
			}
			
			h[i] *= 0.54 - 0.46*Math.cos(2*Math.PI*i/m);
		}

		float sum = 0;
		
		for (int i = 0; i < m; i++)
		{
			sum += h[i];
		}
		
		for (int i = 0; i < m; i++)
		{
			h[i] /= sum;
		}
	
		float output = 0;
		
		for (int i = 0; i < m; i++)
		{
			output += data.get(i).floatValue() * h[i];
		}
		

		return output;
	}

	public void setWindowSize(int size)
	{
		this.filterWindow = size;
	}
}
