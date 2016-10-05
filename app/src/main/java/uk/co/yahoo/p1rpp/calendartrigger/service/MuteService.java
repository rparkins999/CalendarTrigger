/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.service;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.text.DateFormat;
import java.util.HashMap;

import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.calendar.CalendarProvider;

import static android.hardware.SensorManager.SENSOR_DELAY_FASTEST;
import static android.location.LocationManager.KEY_LOCATION_CHANGED;

public class MuteService extends IntentService
	implements SensorEventListener {

	public MuteService() {
		super("CalendarTriggerService");
	}

	public boolean muteRequested;
	public boolean vibrateRequested;
	public boolean canRestoreRinger;
	public boolean wantRestoreRinger;
	public boolean anyStepCountActive;
	public String startEvent;
	public String endEvent;

	// FIXME can we use a similar power saving trick as accelerometer?
	// -2 means the step counter is not active
	// -1 means  we've registered our listener but it hasn't been called yet
	// zero or positive is a real step count
	// -1 or greater means we're holding a wake lock because the step counter
	// isn't a wakeup sensor
	public static int lastCounterSteps = -2;

	// -2 means the orientation listener is not active
	// -1 means we've registered our listener but it hasn't been called yet
	//     we hold a wake lock while it's -1
	// zero means we just got a value
	//     if we're not there yet we reset to -2
	//     and set an alarm to check again 5 minutes from now
	private static int orientationState = -2;

	private static float accelerometerX;
	private static float accelerometerY;
	private static float accelerometerZ;
	private long nextAccelTime;

	private static PowerManager.WakeLock wakelock = null;

	// -2 means the location watcher is not active
	// -1 means  we've requested the initial location
	// zero means we're waiting for the next location update
	private static int locationState = -2;

	private static int notifyId = 1400;

    // 0 USB not connected or connected to dumb charger
    // 1 device is USB host to some peripheral
    // 2 device is USB slave and receiving power from a USB host
    private static int usbState;

	public static void doReset() {
		orientationState = -2;
		if (wakelock != null)
		{
			wakelock.release();
			wakelock = null;
		}
		locationState = -2;
	}

	// We don't know anything sensible to do here
	@Override
	public void onAccuracyChanged(Sensor sensor, int i) {
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		switch(sensorEvent.sensor.getType())
		{
			case Sensor.TYPE_STEP_COUNTER:
				int newCounterSteps = (int)sensorEvent.values[0];
				if (newCounterSteps != lastCounterSteps)
				{
					lastCounterSteps = newCounterSteps;
					startIfNecessary(this, "Step counter changed");
				}
				break;
			case Sensor.TYPE_ACCELEROMETER:
				SensorManager sm = (SensorManager)getSystemService(SENSOR_SERVICE);
				sm.unregisterListener(this);
				accelerometerX = sensorEvent.values[0];
				accelerometerY = sensorEvent.values[1];
				accelerometerZ = sensorEvent.values[2];
				orientationState = 0;
				startIfNecessary(this, "Accelerometer event");
				break;
			default:
				// do nothing, should never happen
		}
	}

	public static class StartServiceReceiver
		extends WakefulBroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			intent.setClass(context, MuteService.class);
			startWakefulService(context, intent);
		}
	}

	private void emitNotification(int resNum, String event) {
		Resources res = getResources();
		Notification.Builder builder
			= new Notification.Builder(this)
			.setSmallIcon(R.drawable.notif_icon)
			.setContentTitle(res.getString(R.string.mode_sonnerie_change))
			.setContentText(res.getString(resNum) + " " + event)
			.setAutoCancel(true);
		// Show notification
		NotificationManager notifManager = (NotificationManager)
			getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(notifyId++, builder.build());
	}

	// return true if step counter is now running
	private boolean StartStepCounter(int classNum) {
		if (lastCounterSteps == -2)
		{
			lastCounterSteps = -1;
			new MyLog(this,
					  "Step counter activated for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
			SensorManager sensorManager =
				(SensorManager)getSystemService(Activity.SENSOR_SERVICE);
			Sensor sensor =
				sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
			if (   (sensor != null)
				&& (sensorManager.registerListener(this, sensor,
						   SensorManager.SENSOR_DELAY_NORMAL)))
			{
				if (wakelock == null)
				{
					PowerManager powerManager
						= (PowerManager)getSystemService(POWER_SERVICE);
					wakelock = powerManager.newWakeLock(
						PowerManager.PARTIAL_WAKE_LOCK, "CalendarTrigger");
					wakelock.acquire();
				}
				return true;
			}
			else
			{
				return false; // could not activate step counter
			}
		}
		else
		{
			// already starting it for another class
			return true;
		}
	}

	private void startLocationWait(int classNum, Intent intent) {
		Location here = intent.getParcelableExtra(KEY_LOCATION_CHANGED);
		if (locationState == -2)
		{
			locationState = -1;
			LocationManager lm =
				(LocationManager)getSystemService(Context.LOCATION_SERVICE);
			Criteria cr = new Criteria();
			cr.setAccuracy(Criteria.ACCURACY_FINE);
			String s = "CalendarTrigger.Location_"
				.concat(String.valueOf(classNum))
				.concat("_")
				.concat(PrefsManager.getClassName(this, classNum));
			PendingIntent pi = PendingIntent.getBroadcast(
				this, 0 /*requestCode*/,
				new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
			PrefsManager.setLatitude(this, classNum, 300.0);
			lm.requestSingleUpdate(cr, pi);
			new MyLog(this,
					  "Starting location wait for class "
					  .concat(PrefsManager.getClassName(this, classNum)));
		}
		else if (here != null)
		{
			float meters = (float)PrefsManager.getAfterMetres(this, classNum);
			PrefsManager.setLatitude(this, classNum, here.getLatitude());
			PrefsManager.setLongitude(this, classNum, here.getLongitude());
			new MyLog(this,
					  "Set up geofence for class "
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(" at location ")
						  .concat(((Double)here.getLatitude()).toString())
						  .concat(", ")
						  .concat(((Double)here.getLongitude()).toString()));
			if (locationState == -1)
			{
				locationState = 0;
				LocationManager lm =
					(LocationManager)getSystemService(Context.LOCATION_SERVICE);
				Criteria cr = new Criteria();
				cr.setAccuracy(Criteria.ACCURACY_FINE);
				String s = "CalendarTrigger.Location";
				PendingIntent pi = PendingIntent.getBroadcast(
					this, 0 /*requestCode*/,
					new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
				lm.requestLocationUpdates(
					5 * 60 * 1000, (float)(meters), cr, pi);
				new MyLog(this,
						  "Requesting location updates for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
			}
		}
	}

	// return true if not left geofence yet
	private boolean checkLocationWait(
		int classNum, double latitude, Intent intent) {
		Location here = intent.getParcelableExtra(KEY_LOCATION_CHANGED);
		if (here != null)
		{
			float meters = (float)PrefsManager.getAfterMetres(this, classNum);
			if (locationState == -1)
			{
				locationState = 0;
				LocationManager lm =
					(LocationManager)getSystemService(Context.LOCATION_SERVICE);
				Criteria cr = new Criteria();
				cr.setAccuracy(Criteria.ACCURACY_FINE);
				String s = "CalendarTrigger.Location";
				PendingIntent pi = PendingIntent.getBroadcast(
					this, 0 /*requestCode*/,
					new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
				lm.requestLocationUpdates(
					5 * 60 * 1000, (float)(meters), cr, pi);
				new MyLog(this,
						  "Requesting location updates for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
			}
			if (latitude == 300.0)
			{
				// waiting for current location, and got it
				PrefsManager.setLatitude(this, classNum, here.getLatitude());
				PrefsManager.setLongitude(this, classNum, here.getLongitude());
				new MyLog(this,
						  "Set up geofence for class "
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(" at location ")
						  .concat(((Double)here.getLatitude()).toString())
						  .concat(", ")
						  .concat(((Double)here.getLongitude()).toString()));
				return true;
			}
			// waiting for geofence exit
			{
				float[] results = new float[1];
				double longitude = PrefsManager.getLongitude(this, classNum);
				Location.distanceBetween(latitude, longitude,
										 here.getLatitude(),
										 here.getLongitude(),
										 results);
				if (results[0] < meters * 0.9)
				{
					new MyLog(this,
							  "Still within geofence for class "
							  .concat(PrefsManager.getClassName(this, classNum))
							  .concat(" at location ")
							  .concat(((Double)here.getLatitude()).toString())
							  .concat(", ")
							  .concat(
								  ((Double)here.getLongitude()).toString()));
					return true;
				}
				// else we've exited the geofence
				PrefsManager.setLatitude(this, classNum, 360.0);
				new MyLog(this,
						  "Exited geofence for class "
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(" at location ")
						  .concat(((Double)here.getLatitude()).toString())
						  .concat(", ")
						  .concat(((Double)here.getLongitude()).toString()));
				return false;
			}
		}
		// location wait active, but no new location
		return true;
	}

	// return true if still waiting for correct orientation
	private boolean checkOrientationWait(int classNum)
	{
		int wanted = PrefsManager.getBeforeOrientation(this, classNum);
		if (   (wanted == 0)
			|| (wanted == PrefsManager.BEFORE_ANY_POSITION))
		{
			return false;
		}
		switch (orientationState)
		{
			case -2: // sensor currently not active
				SensorManager sm = (SensorManager)getSystemService(SENSOR_SERVICE);
				Sensor ams = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
				if (ams == null) { return false; }
				if (wakelock == null)
				{
					PowerManager powerManager
						= (PowerManager) getSystemService(POWER_SERVICE);
					wakelock = powerManager.newWakeLock(
						PowerManager.PARTIAL_WAKE_LOCK, "CalendarTrigger");
					wakelock.acquire();
				}
				orientationState = -1;
				sm.registerListener(this, ams, SENSOR_DELAY_FASTEST);
				new MyLog(this,
						  "Requested accelerometer value for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
				//FALLTHRU
			case -1: // waiting for value
				return true;
			case 0: // just got a value
				nextAccelTime = System.currentTimeMillis() + 5 * 60 * 1000;
				if (   (accelerometerX >= -0.3)
					&& (accelerometerX <= 0.3)
					&& (accelerometerY >= -0.3)
					&& (accelerometerY <= 0.3)
					&& (accelerometerZ >= 9.6)
					&& (accelerometerZ <= 10.0))
				{
					if ((wanted & PrefsManager.BEFORE_FACE_UP) != 0)
					{
						return false;
					}
				}
				else if (   (accelerometerX >= -0.3)
						 && (accelerometerX <= 0.3)
						 && (accelerometerY >= -0.3)
						 && (accelerometerY <= 0.3)
						 && (accelerometerZ >= -10.0)
						 && (accelerometerZ <= -9.6))
				{
					if ((wanted & PrefsManager.BEFORE_FACE_DOWN) != 0)
					{
						return false;
					}
				}
				else if ((wanted & PrefsManager.BEFORE_OTHER_POSITION) != 0)
				{
					return false;
				}
				nextAccelTime = System.currentTimeMillis() + 5 * 60 * 1000;
				orientationState = -2;
				new MyLog(this,
						  "Still waiting for orientation for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
				return true;
			default:
				return false;
		}
	}

	// return true if still waiting for correct connection
	private boolean checkConnectionWait(int classNum)
	{
		int wanted = PrefsManager.getBeforeConnection(this, classNum);
		if (   (wanted == 0)
			|| (wanted == PrefsManager.BEFORE_ANY_CONNECTION))
		{
			return false;
		}
		int charge
			= registerReceiver(
				null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED))
			.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
		UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
		HashMap<String, UsbDevice> map = manager.getDeviceList();
		if (   ((wanted & PrefsManager.BEFORE_WIRELESS_CHARGER) != 0)
			&& (charge == BatteryManager.BATTERY_PLUGGED_WIRELESS))
		{
			return false;
		}
		if (   ((wanted & PrefsManager.BEFORE_FAST_CHARGER) != 0)
			   && (charge == BatteryManager.BATTERY_PLUGGED_AC))
		{
			return false;
		}
		if (   ((wanted & PrefsManager.BEFORE_PLAIN_CHARGER) != 0)
			   && (charge == BatteryManager.BATTERY_PLUGGED_USB))
		{
			return false;
		}
		if ((wanted & PrefsManager.BEFORE_PERIPHERAL) != 0)
		{
			if (!map.isEmpty())
			{
				return false;
			}
		}
		if (   ((wanted & PrefsManager.BEFORE_UNCONNECTED) != 0)
			&& (charge == -1)
			&& map.isEmpty())
		{
			return false;
		}
		return true;
	}

	// Determine which event classes have become active
	// and which event classes have become inactive
	// and consequently what we need to do.
	// Incidentally we compute the next alarm time.
	public void updateState(Intent intent) {
		// Timestamp used in all requests (so it remains consistent)
		long currentTime = System.currentTimeMillis();
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int userRinger = audio.getRingerMode();
		canRestoreRinger = userRinger == PrefsManager.getLastRinger(this);
		wantRestoreRinger = false;
		muteRequested = false;
		vibrateRequested = false;
		long nextAlarmTime = Long.MAX_VALUE;
		nextAccelTime = Long.MAX_VALUE;
		startEvent = "";
		int classNum;
		String startClassName = "";
		endEvent = "";
		String endClassName = "";
		String alarmReason = "";
		anyStepCountActive = false;
		boolean anyLocationActive = false;
		int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		PackageManager packageManager = getPackageManager();
		final boolean haveStepCounter =
			currentApiVersion >= android.os.Build.VERSION_CODES.KITKAT
			&& packageManager.hasSystemFeature(
				PackageManager.FEATURE_SENSOR_STEP_COUNTER);
		final boolean havelocation =
			PackageManager.PERMISSION_GRANTED ==
			ActivityCompat.checkSelfPermission(
				this, Manifest.permission.ACCESS_FINE_LOCATION);
		if (lastCounterSteps >= 0)
		{
			new MyLog(this, "Step counter running");
		}
		int n = PrefsManager.getNumClasses(this);
		CalendarProvider provider = new CalendarProvider(this);
		for (classNum = 0; classNum < n; ++classNum)
		{
			if (PrefsManager.isClassUsed(this, classNum))
			{
				String className = PrefsManager.getClassName(this, classNum);
				int ringerAction = PrefsManager.getRingerAction(this, classNum);
				CalendarProvider.startAndEnd result
					= provider.nextActionTimes(this, currentTime, classNum);
				boolean triggered
					= PrefsManager.isClassTriggered(this, classNum);
				if (triggered) {
					result.startEventName = "<immediate>";
					result.endEventName = "<immediate>";
				}
				boolean active =    (result.startTime <= currentTime)
								 && (result.endTime > currentTime);
				if (   (triggered || active)
					&& (!PrefsManager.isClassActive(this, classNum)))
				{
					if (checkOrientationWait(classNum))
					{
						triggered = false;
						active = false;
						if ((nextAccelTime < nextAlarmTime)
							&& (nextAccelTime > currentTime))
						{
							nextAlarmTime = nextAccelTime;
							alarmReason =
								" for next orientation check for event "
									.concat(result.endEventName)
									.concat(" of class ")
									.concat(className);
						}
					}
					if (checkConnectionWait(classNum))
					{
						triggered = false;
						active = false;
					}
				}
				if (triggered)
				{
					PrefsManager.setClassTriggered(this, classNum, false);
				}
				if (triggered || active)
				{
					// class should be currently active
					int resNum = R.string.mode_sonnerie_pas_de_change_pour;
					if (ringerAction == AudioManager.RINGER_MODE_SILENT)
					{
						if (!muteRequested)
						{
							resNum = R.string.mode_sonnerie_change_silencieux_pour;
							muteRequested = true;
							wantRestoreRinger = false;
						}
					}
					else if (ringerAction == AudioManager.RINGER_MODE_VIBRATE)
					{
						if (!(muteRequested | vibrateRequested))
						{
							resNum = R.string.mode_sonnerie_change_vibreur_pour;
							vibrateRequested = true;
							wantRestoreRinger = false;
						}
					}
					if (!PrefsManager.isClassActive(this, classNum))
					{
						startEvent = result.startEventName;
						if (PrefsManager.getNotifyStart(this, classNum))
						{
							emitNotification(resNum, startEvent);
						}
						PrefsManager.setTargetSteps(this, classNum, 0);
						PrefsManager.setLatitude(this, classNum, 360.0);
						PrefsManager.setClassActive(this, classNum, true);
						startClassName = className;
					}
					if (result.endTime < nextAlarmTime)
					{
						nextAlarmTime = result.endTime;
						endClassName = className;
						alarmReason = " for end of event "
									  .concat(result.endEventName)
									  .concat(" of class ")
									  .concat(className);
					}
					PrefsManager.setLastActive(
						this, classNum, result.endEventName);
				}
				if (triggered || !active)
				{
					// class should not be currently active
					boolean done = false;
					boolean waiting = false;
					if (PrefsManager.isClassActive(this, classNum))
					{
						// ... but it is
						PrefsManager.setClassActive(this, classNum, false);
						done = true;
						if (haveStepCounter
							&& PrefsManager.getAfterSteps(this, classNum) > 0)
						{
							if (lastCounterSteps < 0)
							{
								// need to start up the sensor
								if (StartStepCounter(classNum))
								{
									PrefsManager.setTargetSteps(
										this, classNum, 0);
									anyStepCountActive = true;
									waiting = true;
									done = false;
								}
							}
							else if (lastCounterSteps >= 0)
							{
								int aftersteps =
									 PrefsManager.getAfterSteps(this, classNum);
								PrefsManager.setTargetSteps(
									this, classNum,
									lastCounterSteps + aftersteps);
								anyStepCountActive = true;
								new MyLog(this,
										  "Setting target steps for class "
										  + className
										  + " to "
										  + String.valueOf(lastCounterSteps)
										  + " + "
										  + String.valueOf(aftersteps));
								waiting = true;
								done = false;
							}
						}
						if (   havelocation
							&& (PrefsManager.getAfterMetres(
									this, classNum) > 0))
						{
							// keep it active while waiting for location
							startLocationWait(classNum, intent);
							anyLocationActive = true;
							waiting = true;
							done = false;
						}
						if (waiting)
						{
							PrefsManager.setClassWaiting(this, classNum, true);
						}
					}
					else if (PrefsManager.isClassWaiting(this, classNum))
					{
						done = true;
						int aftersteps
							= PrefsManager.getAfterSteps(this, classNum);
						if ((lastCounterSteps > -2) && (aftersteps > 0))
						{
							int steps
								= PrefsManager.getTargetSteps(this, classNum);
							if (steps == 0)
							{
								if (lastCounterSteps >= 0)
								{
									PrefsManager.setTargetSteps(
										this, classNum,
										lastCounterSteps + aftersteps);
								}
								anyStepCountActive = true;
								new MyLog(this,
										  "Setting target steps for class "
										  + className
										  + " to "
										  + String.valueOf(lastCounterSteps)
										  + " + "
										  + String.valueOf(aftersteps));
								waiting = true;
								done = false;
							}
							else if (lastCounterSteps < steps)
							{
								anyStepCountActive = true;
								waiting = true;
								done = false;
							}
						}
						double latitude
							= PrefsManager.getLatitude(this, classNum);
						if (   (latitude != 360.0)
							&& checkLocationWait(classNum, latitude, intent))
						{
							anyLocationActive = true;
							waiting = true;
							done = false;
						}
					}
					if (done)
					{
						String last =
							PrefsManager.getLastActive(this, classNum);
						if (   (PrefsManager.getRestoreRinger(this, classNum))
							   && !(muteRequested | vibrateRequested))
						{
							wantRestoreRinger = true;
							if (PrefsManager.getNotifyEnd(this, classNum))
							{
								int resNum =
									R.string.mode_sonnerie_pas_de_change_apres;
								int ringer = PrefsManager.getUserRinger(this);
								if (   (ringer != audio.getRingerMode())
									&& canRestoreRinger)
								{
									resNum =
										(ringer == AudioManager.RINGER_MODE_VIBRATE)
										? R.string.mode_sonnerie_change_vibreur_apres
										: R.string.mode_sonnerie_change_normale_apres;
								}
								emitNotification(resNum, last);
							}
							endEvent = last;
							endClassName = className;
						}
						PrefsManager.setClassActive(this, classNum, false);
						PrefsManager.setClassWaiting(this, classNum, false);
					}
					else if (waiting)
					{
						if (ringerAction == AudioManager.RINGER_MODE_SILENT)
						{
							if (!muteRequested)
							{
								muteRequested = true;
								wantRestoreRinger = false;
							}
						}
						else if (ringerAction == AudioManager.RINGER_MODE_VIBRATE)
						{
							if (!(muteRequested | vibrateRequested))
							{
								vibrateRequested = true;
								wantRestoreRinger = false;
							}
						}
					}
					if (   (result.startTime < nextAlarmTime)
					    && (result.startTime > currentTime))
					{
						nextAlarmTime = result.startTime;
						alarmReason = " for start of event "
							.concat(result.startEventName)
							.concat(" of class ")
							.concat(className);
					}
				}
			}
		}
		if (muteRequested)
		{
			if (PrefsManager.getLastRinger(this) == PrefsManager
				.RINGER_MODE_NONE)
			{
				PrefsManager.setUserRinger(this, userRinger);
			}
			audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
			PrefsManager.setLastRinger(this,AudioManager.RINGER_MODE_SILENT);
			if (!startEvent.equals(""))
			{
				new MyLog(this, "Setting ringer to "
					.concat(MyLog.rm(AudioManager.RINGER_MODE_SILENT))
					.concat(" for start of event ")
					.concat(startEvent)
					.concat(" of class ")
					.concat(startClassName));
			}
		}
		else if (vibrateRequested)
		{
			if (PrefsManager.getLastRinger(this) == PrefsManager
				.RINGER_MODE_NONE)
			{
				PrefsManager.setUserRinger(this, userRinger);
			}
			audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
			PrefsManager.setLastRinger(this,AudioManager.RINGER_MODE_VIBRATE);
			if (!startEvent.equals(""))
			{
				new MyLog(this, "Setting ringer to "
					.concat(MyLog.rm(AudioManager.RINGER_MODE_VIBRATE))
					.concat(" for start of event ")
					.concat(startEvent)
					.concat(" of class ")
					.concat(startClassName));
			}
		}
		else if (wantRestoreRinger)
		{
			int ringer = PrefsManager.getUserRinger(this);
			if ((ringer != userRinger) && canRestoreRinger)
			{
				audio.setRingerMode(ringer);
				if (!endEvent.equals(""))
				{
					new MyLog(this, "Restoring ringer to "
						.concat(MyLog.rm(ringer))
						.concat(" after event ")
						.concat(endEvent)
						.concat(" of class ")
						.concat(endClassName));
				}
			}
			PrefsManager.setLastRinger(this, PrefsManager.RINGER_MODE_NONE);
		}

		long lastAlarm = PrefsManager.getLastAlarmTime(this);
		// Try always setting alarm to see if it works better
		if (true/*nextAlarmTime != lastAlarm*/)
		{
			int flags = 0;
			PendingIntent pIntent = PendingIntent.getBroadcast(
				this, 0 /*requestCode*/,
				new Intent("CalendarTrigger.Alarm", Uri.EMPTY,
					this, StartServiceReceiver.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
			AlarmManager alarmManager = (AlarmManager)
				getSystemService(Context.ALARM_SERVICE);
			// Remove previous alarms
			if (lastAlarm != Long.MAX_VALUE)
			{
				alarmManager.cancel(pIntent);
				new MyLog(this, "Alarm cancelled");
			}

			if (nextAlarmTime != Long.MAX_VALUE)
			{
				// Add new alarm
				alarmManager.setExact(
					AlarmManager.RTC_WAKEUP, nextAlarmTime, pIntent);
				DateFormat df = DateFormat.getDateTimeInstance();
				new MyLog(this, "Alarm time set to "
								.concat(df.format(nextAlarmTime))
								.concat(alarmReason));
			}
		}
		else
		{
			DateFormat df = DateFormat.getDateTimeInstance();
			new MyLog(this, "Alarm unchanged at "
							.concat(df.format(nextAlarmTime)));
		}
		PrefsManager.setLastAlarmTime(this, nextAlarmTime);
		PrefsManager.setLastInvocationTime(this,currentTime);
		if (!anyStepCountActive)
		{
			if (lastCounterSteps >= 0)
			{
				lastCounterSteps = -2;
				SensorManager sensorManager =
					(SensorManager)getSystemService(Activity.SENSOR_SERVICE);
				sensorManager.unregisterListener(this);
				new MyLog(this, "Step counter deactivated");
			}
		}
		if (!anyLocationActive)
		{
			if (locationState == 0)
			{
				String s = "CalendarTrigger.Location";
				PendingIntent pi = PendingIntent.getBroadcast(
					this, 0 /*requestCode*/,
					new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
					PendingIntent.FLAG_UPDATE_CURRENT);
				LocationManager lm = (LocationManager)getSystemService(
					Context.LOCATION_SERVICE);
				lm.removeUpdates(pi);
			}
			locationState = -2;
		}
		if (   (wakelock != null)
			&& (lastCounterSteps == -2)
			&& (orientationState != -1))
		{
			wakelock.release();
			wakelock = null;
		}
	}


	@Override
	public void onHandleIntent(Intent intent) {
		new MyLog(this, "onHandleIntent("
				  .concat(intent.toString())
				  .concat(")"));
		updateState(intent);
		WakefulBroadcastReceiver.completeWakefulIntent(intent);
	}
	
	public static void startIfNecessary(Context c, String caller) {
			c.startService(new Intent(caller, null, c, MuteService.class));
	}
}
