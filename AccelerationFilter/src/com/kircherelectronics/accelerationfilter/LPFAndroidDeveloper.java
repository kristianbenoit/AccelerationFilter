package com.kircherelectronics.accelerationfilter;

import android.util.Log;

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
 * An implementation of the Android Developer low-pass filter.
 * 
 * @author Kaleb
 * @see http://developer.android.com/reference/android/hardware/SensorEvent.html
 * @version %I%, %G%
 */
public class LPFAndroidDeveloper implements LowPassFilter
{
	private boolean alphaStatic = false;

	// Constants for the low-pass filters
	private float timeConstant = 0.18f;
	private float alpha = 0.9f;
	private float dt = 0;

	// Timestamps for the low-pass filters
	private float timestamp = System.nanoTime();
	private float timestampOld = System.nanoTime();

	private int count = 0;

	// Gravity and linear accelerations components for the
	// Wikipedia low-pass filter
	private float[] output = new float[]
	{ 0, 0, 0 };

	// Raw accelerometer data
	private float[] input = new float[]
	{ 0, 0, 0 };

	/**
	 * Add a sample.
	 * @param acceleration The acceleration data.
	 * @return Returns the output of the filter.
	 */
	public float[] addSamples(float[] acceleration)
	{
		// Get a local copy of the sensor values
		System.arraycopy(acceleration, 0, this.input, 0, acceleration.length);

		if (!alphaStatic)
		{
			timestamp = System.nanoTime();

			// Find the sample period (between updates).
			// Convert from nanoseconds to seconds
			dt = 1 / (count / ((timestamp - timestampOld) / 1000000000.0f));

			alpha = timeConstant / (timeConstant + dt);
			
		}
		
		count++;

		if (count > 5)
		{
			output[0] = alpha * output[0] + (1 - alpha) * input[0];
			output[1] = alpha * output[1] + (1 - alpha) * input[1];
			output[2] = alpha * output[2] + (1 - alpha) * input[2];
		}

		return output;
	}

	/**
	 * Indicate if alpha should be static.
	 * @param alphaStatic A static value for alpha
	 */
	public void setAlphaStatic(boolean alphaStatic)
	{
		this.alphaStatic = alphaStatic;
	}
	
	/**
	 * Set static alpha.
	 * @param alpha The value for alpha, 0 < alpha <= 1
	 */
	public void setAlpha(float alpha)
	{
		this.alpha = alpha;
	}
}
