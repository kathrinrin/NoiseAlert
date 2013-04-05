/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.noisealert;

import java.io.IOException;

import com.google.android.noisealert.SmsRemote.SmsRemoteReceiver;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

public class NoiseAlert extends Activity implements SmsRemoteReceiver {
	/* constants */
	// private static final String LOG_TAG = "NoiseAlert";
	private static final int POLL_INTERVAL = 300;
	private static final int NO_NUM_DIALOG_ID = 1;
	// private static final String[] REMOTE_CMDS = { "start", "stop" };

	/** running state **/
	private boolean mAutoResume = false;
	private boolean mRunning = false;
	private int mTickCount = 0;
	private int mHitCount = 0;

	/** config state **/
	private int mThreshold = 5;
	private int mPollDelay;

	private PowerManager.WakeLock mWakeLock;

	private Handler mHandler = new Handler();

	/* References to view elements */
	private TextView mStatusView;
	private SoundLevelView mDisplay;

	/* data source */
	private SoundMeter mSensor;

	private Runnable mSleepTask = new Runnable() {
		public void run() {
			start();
		}
	};
	private Runnable mPollTask = new Runnable() {
		public void run() {
			double amp = mSensor.getAmplitude();

			updateDisplay("monitoring...", amp);

			if ((amp > mThreshold)) {
				mHitCount++;
				if (mHitCount > 5) {
					return;
				}
			}

			mTickCount++;

			if ((mPollDelay > 0) && mTickCount > 100) {
				sleep();
			} else {
				mHandler.postDelayed(mPollTask, POLL_INTERVAL);
			}
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);
		mStatusView = (TextView) findViewById(R.id.status);

		mSensor = new SoundMeter();
		mDisplay = (SoundLevelView) findViewById(R.id.volume);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
				"NoiseAlert");
	}

	@Override
	public void onResume() {
		super.onResume();

		mDisplay.setLevel(0, mThreshold);
		if (mAutoResume) {
			start();
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		stop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		if (mRunning) {
			menu.findItem(R.id.start_stop).setTitle(R.string.stop);
		} else {
			menu.findItem(R.id.start_stop).setTitle(R.string.start);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.start_stop:
			if (!mRunning) {

				mAutoResume = true;
				mRunning = true;
				start();
			} else {
				mAutoResume = false;
				mRunning = false;
				stop();
			}
			break;

		}
		return true;
	}

	public void receive(String cmd) {
		if (cmd == "start" & !mRunning) {

			mAutoResume = true;
			mRunning = true;
			start();

		} else if (cmd == "stop" & mRunning) {
			mAutoResume = false;
			mRunning = false;
			stop();
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		if (id == NO_NUM_DIALOG_ID) {
			return new AlertDialog.Builder(this).setIcon(R.drawable.icon)
					.setTitle(R.string.no_num_title)
					.setMessage(R.string.no_num_msg)
					.setNeutralButton(R.string.ok, null).create();
		} else
			return null;
	}

	private void start() {
		mTickCount = 0;
		mHitCount = 0;
		try {
			mSensor.start();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (!mWakeLock.isHeld()) {
			mWakeLock.acquire();
		}
		mHandler.postDelayed(mPollTask, POLL_INTERVAL);
	}

	private void stop() {
		if (mWakeLock.isHeld()) {
			mWakeLock.release();
		}
		mHandler.removeCallbacks(mSleepTask);
		mHandler.removeCallbacks(mPollTask);
		mSensor.stop();
		mDisplay.setLevel(0, 0);
		updateDisplay("stopped...", 0.0);
		mRunning = false;
	}

	private void sleep() {
		mSensor.stop();
		updateDisplay("paused...", 0.0);
		mHandler.postDelayed(mSleepTask, 1000 * mPollDelay);
	}

	private void updateDisplay(String status, double signalEMA) {
		mStatusView.setText(status);

		mDisplay.setLevel((int) signalEMA, mThreshold);
	}

};