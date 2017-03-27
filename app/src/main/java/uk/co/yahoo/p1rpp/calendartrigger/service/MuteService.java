/*
 * Copyright (c) 2017, Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.service;

import android.Manifest;
import android.annotation.TargetApi;
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
import android.os.Build;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.telephony.TelephonyManager;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.List;

import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.calendar.CalendarProvider;

public class MuteService extends IntentService
	implements SensorEventListener {

	public static final String MUTESERVICE_RESET =
		"CalendarTrigger.MuteService.Reset";

	public MuteService() {
		super("CalendarTriggerService");
	}

	// need to stash these persistent state variables somewhere
	// (Preferences?) since Android doesn't
	// keep even static variables for a shut-down service

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

	public static PowerManager.WakeLock wakelock = null;

	private static int notifyId = 1400;

    // 0 USB not connected or connected to dumb charger
    // 1 device is USB host to some peripheral
    // 2 device is USB slave and receiving power from a USB host
    private static int usbState;

	private void doReset() {
		int n = PrefsManager.getNumClasses(this);
		int classNum;
		for (classNum = 0; classNum < n; ++classNum)
		{
			if (PrefsManager.isClassUsed(this, classNum))
			{
				PrefsManager.setClassTriggered(this, classNum, false);
				PrefsManager.setClassActive(this, classNum, false);
				PrefsManager.setClassWaiting(this, classNum, false);
			}
		}
		orientationState = -2;
		if (wakelock != null)
		{
			wakelock.release();
			wakelock = null;
		}
		LocationUpdates(0, PrefsManager.LATITUDE_IDLE);
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

	//FIXME add sound here?
	private void emitNotification(String text, String bigText) {
		Resources res = getResources();
		Notification.Builder builder
			= new Notification.Builder(this)
			.setSmallIcon(R.drawable.notif_icon)
			.setContentTitle(res.getString(R.string.mode_sonnerie_change))
			.setContentText(text)
			.setStyle(new Notification.BigTextStyle().bigText(bigText));
		// Show notification
		NotificationManager notifManager = (NotificationManager)
			getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(notifyId++, builder.build());
	}

	private void PermissionFail(int mode)
	{
		Notification.Builder builder
			= new Notification.Builder(this)
			.setSmallIcon(R.drawable.notif_icon)
			.setContentText(getString(R.string.permissionfail))
			.setStyle(new Notification.BigTextStyle()
                .bigText(getString(R.string.permissionfailbig)));
		// Show notification
		NotificationManager notifManager = (NotificationManager)
			getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(notifyId++, builder.build());
		new MyLog(this, "Cannot set mode "
						+ PrefsManager.getRingerStateName(this, mode)
				  		+ " because CalendarTrigger no longer has permission "
				  		+ "ACCESS_NOTIFICATION_POLICY.");
	}

	// Check if there is a current call (not a ringing call).
	// If so we don't want to mute even if an event starts.
	int UpdatePhoneState(Intent intent) {
		// 0 idle
		// 1 incoming call ringing (but no active call)
		// 2 call active
		int phoneState = PrefsManager.getPhoneState(this);
		if (intent.getAction() == TelephonyManager.ACTION_PHONE_STATE_CHANGED)
		{
			String event = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
			switch (event)
			{
				case "IDLE":
					phoneState = PrefsManager.PHONE_IDLE;
					PrefsManager.setPhoneState(this, phoneState);
					break;
				case "OFFHOOK":
					phoneState = PrefsManager.PHONE_CALL_ACTIVE;
					PrefsManager.setPhoneState(this, phoneState);
					break;
				case "RINGING":
					// Tricky case - can be ringing when already in a call
					if (phoneState == PrefsManager.PHONE_IDLE)
					{
						phoneState = PrefsManager.PHONE_RINGING;
						PrefsManager.setPhoneState(this, phoneState);
					}
					break;
			}
			PrefsManager.setNotifiedCannotReadPhoneState(this, false);
		}
		else
		{
			boolean canCheckCallState =
				PackageManager.PERMISSION_GRANTED ==
				ActivityCompat.checkSelfPermission(
					this, Manifest.permission.READ_PHONE_STATE);
			if (!canCheckCallState)
			{
				if (!PrefsManager.getNotifiedCannotReadPhoneState(this))
				{
					Notification.Builder builder
						= new Notification.Builder(this)
						.setSmallIcon(R.drawable.notif_icon)
						.setContentText(getString(R.string.readphonefail))
						.setStyle(new Notification.BigTextStyle()
									  .bigText(getString(R.string.readphonefailbig)));
					// Show notification
					NotificationManager notifManager = (NotificationManager)
						getSystemService(Context.NOTIFICATION_SERVICE);
					notifManager.notify(notifyId++, builder.build());
					new MyLog(this, "CalendarTrigger no longer has permission "
									+ "READ_PHONE_STATE.");
					PrefsManager.setNotifiedCannotReadPhoneState(this, true);
				}
			}
		}
		return phoneState;
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

	public void LocationUpdates(int classNum, double which) {
		LocationManager lm =
			(LocationManager)getSystemService(Context.LOCATION_SERVICE);
		String s = "CalendarTrigger.Location";
		PendingIntent pi = PendingIntent.getBroadcast(
			this, 0 /*requestCode*/,
			new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
			PendingIntent.FLAG_UPDATE_CURRENT);
		if (which == PrefsManager.LATITUDE_IDLE)
		{
			lm.removeUpdates(pi);
			PrefsManager.setLocationState(this, false);
			new MyLog(this, "Removing location updates");
		}
		else
		{
			List<String> ls = lm.getProviders(true);
			if (which == PrefsManager.LATITUDE_FIRST)
			{
				if (ls.contains("gps"))
				{
					// The first update may take a long time if we are inside a
					// building, but this is OK because we won't want to restore
					// the state until we've left the building. If we don't
					// force the use of GPS here, we may get a cellular network
					// fix which can be some distance from our real position and
					// if we then get a GPS fix while we are still in the
					// building we can think that we have moved when in fact we
					// haven't.
					lm.requestSingleUpdate("gps", pi);
					new MyLog(this,
							  "Requesting first gps location for class "
							  .concat(PrefsManager.getClassName(
								  this, classNum)));
				}
				else
				{
					// If we don't have GPS, we use whatever the device can give
					// us.
					Criteria cr = new Criteria();
					cr.setAccuracy(Criteria.ACCURACY_FINE);
					lm.requestSingleUpdate(cr, pi);
					new MyLog(this,
							  "Requesting first fine location for class "
							  .concat(PrefsManager.getClassName(
								  this, classNum)));
				}
				PrefsManager.setLocationState(this, true);
				PrefsManager.setLatitude(
					this, classNum, PrefsManager.LATITUDE_FIRST);
			}
			else
			{
				float meters =
					(float)PrefsManager.getAfterMetres(this, classNum);
				if (ls.contains("gps"))
				{
					lm.requestLocationUpdates("gps", 5 * 60 * 1000, meters, pi);
				}
				else
				{
					Criteria cr = new Criteria();
					cr.setAccuracy(Criteria.ACCURACY_FINE);
					lm.requestLocationUpdates(5 * 60 * 1000, meters, cr, pi);
				}
			}
		}
	}

	private void startLocationWait(int classNum, Intent intent) {
		Location here =
			intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED);
		if (!PrefsManager.getLocationState(this))
		{
			LocationUpdates(classNum, PrefsManager.LATITUDE_FIRST);
		}
		else if (here != null)
		{
			PrefsManager.setLatitude(this, classNum, here.getLatitude());
			PrefsManager.setLongitude(this, classNum, here.getLongitude());
			new MyLog(this,
					  "Set up geofence for class "
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(" at location ")
						  .concat(((Double)here.getLatitude()).toString())
						  .concat(", ")
						  .concat(((Double)here.getLongitude()).toString()));
		}
		else
		{
			PrefsManager.setLatitude(
				this, classNum, PrefsManager.LATITUDE_FIRST);
		}
	}

	// return true if not left geofence yet
	private boolean checkLocationWait(
		int classNum, double latitude, Intent intent) {
		if (!PrefsManager.getLocationState(this))
		{
			// we got reset, pretend we left the geofence
			PrefsManager.setLatitude(
				this, classNum, PrefsManager.LATITUDE_IDLE);
			return false;
		}
		Location here =
			intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED);
		if (here != null)
		{
			if (latitude == PrefsManager.LATITUDE_FIRST)
			{
				// waiting for current location, and got it
				LocationUpdates(classNum, 0.0);
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
				float meters = (float)PrefsManager.getAfterMetres(this, classNum);
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
				PrefsManager.setLatitude(
					this, classNum, PrefsManager.LATITUDE_IDLE);
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
				sm.registerListener(this, ams,
									SensorManager.SENSOR_DELAY_FASTEST);
				new MyLog(this,
						  "Requested accelerometer value for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
				//FALLTHRU
			case -1: // waiting for value
				return true;
			case 0: // just got a value
				nextAccelTime = System.currentTimeMillis() + 5 * 60 * 1000;
				new MyLog(this, "accelerometerX = "
						        + String.valueOf(accelerometerX)
						  		+ ", accelerometerY = "
						        + String.valueOf(accelerometerY)
						  		+ ", accelerometerZ = "
						        + String.valueOf(accelerometerZ));
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
			&& (charge == 0)
			&& map.isEmpty())
		{
			return false;
		}
		return true;
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	// Set the ringer mode. Returns true if mode changed.
	boolean setCurrentRinger(AudioManager audio, int apiVersion, int mode) {
		int current = PrefsManager.getCurrentMode(this);
        int last =  PrefsManager.getLastRinger(this);
		if (   (last != PrefsManager.RINGER_MODE_NONE)
            && (last != current))
		{
			// user changed ringer mode
			PrefsManager.setUserRinger(this, current);
			if (current > mode)
			{
				return false;  // user set quieter mode since we last set it
			}
		}
		if (   (current == mode)
			|| (   (mode == PrefsManager.RINGER_MODE_NONE)
			    && (current == PrefsManager.RINGER_MODE_NORMAL)))
		{
			return false;  // no change
		}
		if (apiVersion >= android.os.Build.VERSION_CODES.M)
		{
			NotificationManager nm = (NotificationManager)
				getSystemService(Context.NOTIFICATION_SERVICE);
			switch (mode)
			{
				case PrefsManager.RINGER_MODE_SILENT:
					audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
					if (nm.isNotificationPolicyAccessGranted())
					{
						nm.setInterruptionFilter(
							NotificationManager.INTERRUPTION_FILTER_NONE);
					}
					else
					{
						PermissionFail(mode);
					}
					break;
				case PrefsManager.RINGER_MODE_ALARMS:
					if (nm.isNotificationPolicyAccessGranted())
					{
						audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
						nm.setInterruptionFilter(
							NotificationManager.INTERRUPTION_FILTER_ALARMS);
						break;
					}
					/*FALLTHRU if no permission, treat as muted */
				case PrefsManager.RINGER_MODE_DO_NOT_DISTURB:
					if (nm.isNotificationPolicyAccessGranted())
					{
						audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
						nm.setInterruptionFilter(
							NotificationManager.INTERRUPTION_FILTER_PRIORITY);
						break;
					}
					PermissionFail(mode);
					/*FALLTHRU if no permission, treat as muted */
				case PrefsManager.RINGER_MODE_MUTED:
					if (nm.isNotificationPolicyAccessGranted())
					{
						nm.setInterruptionFilter(
							NotificationManager.INTERRUPTION_FILTER_ALL);
					}
					audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
					break;
				case PrefsManager.RINGER_MODE_VIBRATE:
					if (nm.isNotificationPolicyAccessGranted())
					{
						nm.setInterruptionFilter(
							NotificationManager.INTERRUPTION_FILTER_ALL);
					}
					audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
					break;
                case PrefsManager.RINGER_MODE_NORMAL:
				case PrefsManager.RINGER_MODE_NONE:
					if (nm.isNotificationPolicyAccessGranted())
					{
						nm.setInterruptionFilter(
							NotificationManager.INTERRUPTION_FILTER_ALL);
					}
					audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
					break;
				default: // unknown
					return false;
			}
		}
		else
		{
			switch (mode) {
				case PrefsManager.RINGER_MODE_MUTED:
					audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
					break;
				case PrefsManager.RINGER_MODE_VIBRATE:
					audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
					break;
				case PrefsManager.RINGER_MODE_NONE:
				case PrefsManager.RINGER_MODE_NORMAL:
					audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
					break;
				default: // unknown
					return false;
			}
		}
		int user = PrefsManager.getUserRinger(this);
		if (mode == PrefsManager.RINGER_MODE_NONE)
		{
			// restoring saved user ringer
			PrefsManager.setUserRinger(this, PrefsManager.RINGER_MODE_NONE);
		}
		else if (user == PrefsManager.RINGER_MODE_NONE)
		{
			// no saved user ringer, save it
			PrefsManager.setUserRinger(this, current);
		}
		// "Neither of the above" is possible if events with different
		// ringer requirements overlap.
		PrefsManager.setLastRinger(this, mode);
		return true;
	}

	// Determine which event classes have become active
	// and which event classes have become inactive
	// and consequently what we need to do.
	// Incidentally we compute the next alarm time.
	@TargetApi(Build.VERSION_CODES.M)
	public void updateState(Intent intent) {
		// Timestamp used in all requests (so it remains consistent)
		long currentTime = System.currentTimeMillis();
		int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int wantedMode = PrefsManager.RINGER_MODE_NONE;
		long nextAlarmTime = Long.MAX_VALUE;
		nextAccelTime = Long.MAX_VALUE;
		int classNum;
		String startEvent = "";
		String startClassName = "";
		String endEvent = "";
		String endClassName = "";
		String alarmReason = "";
		boolean startNotifyWanted = false;
		boolean endNotifyWanted = false;
		int phoneState = UpdatePhoneState(intent);
		boolean anyStepCountActive = false;
		boolean anyLocationActive = false;
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

				// true if user requested immediate event of this class
				boolean triggered
					= PrefsManager.isClassTriggered(this, classNum);

				// true if an event of this class should be currently active
				boolean active = (result.startTime <= currentTime)
								 && (result.endTime > currentTime);

				if (triggered)
				{
					long after = PrefsManager.getAfterMinutes(this, classNum)
								 * 60000;
					if (after > 0)
					{
						long triggerEnd = currentTime + after;
						if (   (active && (triggerEnd > result.endTime))
							|| ((!active) && (triggerEnd < result.startTime)))
						{
							result.endEventName = "<immediate>";
							result.endTime = triggerEnd;
						}
						PrefsManager.setLastTriggerEnd(
							this, classNum, result.endTime);
					}
					else if (!active)
					{
						// We will start the event (possibly after a state
						// delay) and end it immediately: this is probably not
						// a useful case, but we have to handle it correctly.
						result.endEventName = "<immediate>";

						// We do need this because we may have found an event
						// which starts and ends in the future.
						result.endTime = currentTime;
					}
					if (!active)
					{
						result.startEventName = "<immediate>";
						result.startTime = currentTime;
					}
					if (after > 0)
					{
						// need to do this after above test
						active = true;
					}
				}
				if (triggered || active)
				{
					if (!PrefsManager.isClassActive(this, classNum))
					{
						// need to start this class

						// True if not ready to start it yet.
						boolean notNow =
							phoneState == PrefsManager.PHONE_CALL_ACTIVE;
						if (!notNow)
						{
							notNow = checkOrientationWait(classNum);
							new MyLog(this, "checkOrientationWait("
											+ className
											+ ") returns "
											+ (notNow ? "true" : "false"));
							if (notNow)
							{
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
							else
							{
								notNow = checkConnectionWait(classNum);
								new MyLog(this, "checkConnectionWait("
												+ className
												+ ") returns "
												+ (notNow ? "true" : "false"));
							}
						}
						if (notNow)
						{
							// we can't start it yet
							triggered = false;
							active = false;
						}
						else
						{
							if (   (PrefsManager.getNotifyStart(this, classNum))
								&& (ringerAction > wantedMode))
							{
								startNotifyWanted = true;
								startEvent = result.startEventName;
								startClassName = className;
							}
							PrefsManager.setTargetSteps(this, classNum, 0);
							PrefsManager.setLatitude(this, classNum, 360.0);
							PrefsManager.setClassActive(this, classNum, true);
						}
					}
				}
				if (active) // may have changed
				{
					// class should be currently active
					if (ringerAction > wantedMode)
					{
						wantedMode = ringerAction;
						startEvent = result.startEventName;
						startClassName = className;
					}
					PrefsManager.setLastActive(
						this, classNum, result.endEventName);
				}
				if (!active)
				{
					// class should not be currently active

					// check if we're waiting for movement
					boolean done = false;
					boolean waiting = false;
					int aftersteps =
						PrefsManager.getAfterSteps(this, classNum);
					if (PrefsManager.isClassActive(this, classNum))
					{
						done = true;
						if (haveStepCounter && (aftersteps > 0))
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
							else // sensor already running
							{
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
						if (havelocation
							&& (PrefsManager.getAfterMetres(this, classNum)
								> 0))
						{
							// keep it active while waiting for location
							startLocationWait(classNum, intent);
							anyLocationActive = true;
							waiting = true;
							done = false;
						}
						PrefsManager.setClassActive(this, classNum, false);
					}
					if (PrefsManager.isClassWaiting(this, classNum))
					{
						done = true;
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
					else if (waiting)
					{
						PrefsManager.setClassWaiting(this, classNum, true);
					}
					if (done)
					{
						if (   (PrefsManager.getRestoreRinger(this, classNum))
							&& (wantedMode == PrefsManager.RINGER_MODE_NONE))
						{
							wantedMode = PrefsManager.getUserRinger(this);
							if (PrefsManager.getNotifyEnd(this, classNum))
							{
								endNotifyWanted = true;
							}
							endEvent =
								PrefsManager.getLastActive(this, classNum);
							endClassName = className;
						}
						PrefsManager.setClassActive(this, classNum, false);
						PrefsManager.setClassWaiting(this, classNum, false);
					}
					else if (waiting && (wantedMode < ringerAction))
					{
						wantedMode = ringerAction;
					}
				}
				if (   (result.endTime < nextAlarmTime)
					&& (result.endTime > currentTime))
				{
					nextAlarmTime = result.endTime;
					alarmReason = " for end of event "
						.concat(result.endEventName)
						.concat(" of class ")
						.concat(className);
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
				if (triggered)
				{
					PrefsManager.setClassTriggered(this, classNum, false);
				}
			}
		}
		String shortText;
		String longText;
		if (startNotifyWanted)
		{
			shortText = PrefsManager.getRingerSetting(this, wantedMode);
			longText = shortText
					   + " "
					   + getString(R.string.eventstart)
					   + " "
					   + startEvent
					   + " "
					   + getString(R.string.ofclass)
					   + " "
					   + startClassName;
			emitNotification(shortText, longText);
		}
		if (endNotifyWanted)
		{
				shortText = PrefsManager.getRingerSetting(this, wantedMode);
				longText = shortText
						   + " "
						   + getString(R.string.eventend)
						   + " "
						   + endEvent
						   + " "
						   + getString(R.string.ofclass)
						   + " "
						   + endClassName;
				emitNotification(shortText, longText);
		}
		if (setCurrentRinger(audio, currentApiVersion, wantedMode))
		{
			if (startEvent != "")
			{
				new MyLog(this,
						  "Setting ringer mode to "
						  + PrefsManager.getEnglishStateName(this,
															 wantedMode)
						  + " for start of event "
						  + startEvent
						  + " of class "
						  + startClassName);

			}
			if (endEvent != "")
			{
				new MyLog(this,
						  "Setting ringer mode to "
						  + PrefsManager.getEnglishStateName(this,
															 wantedMode)
						  + " for end of event "
						  + endEvent
						  + " of class "
						  + endClassName);

			}
		}
		PendingIntent pIntent = PendingIntent.getBroadcast(
			this, 0 /*requestCode*/,
			new Intent("CalendarTrigger.Alarm", Uri.EMPTY,
				this, StartServiceReceiver.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
		AlarmManager alarmManager = (AlarmManager)
			getSystemService(Context.ALARM_SERVICE);
		// Update alarm if it has changed
		// Avoid recreating alarm if it has not changed because Android can
		// silently fail to do so if the alarm time is too soon
		if (PrefsManager.getLastAlarmTime(this) != nextAlarmTime)
		{
			if (nextAlarmTime == Long.MAX_VALUE)
			{
				alarmManager.cancel(pIntent);
				new MyLog(this, "Alarm cancelled");
			}
			else
			{
				if (currentApiVersion >= android.os.Build.VERSION_CODES.M)
				{
					alarmManager.setExactAndAllowWhileIdle(
						AlarmManager.RTC_WAKEUP, nextAlarmTime, pIntent);
				}
				else
				{
					alarmManager.setExact(
						AlarmManager.RTC_WAKEUP, nextAlarmTime, pIntent);
				}
				DateFormat df = DateFormat.getDateTimeInstance();
				new MyLog(this, "Alarm time set to "
					.concat(df.format(nextAlarmTime))
					.concat(alarmReason));
				PrefsManager.setLastAlarmTime(this, nextAlarmTime);
			}
		}

		PrefsManager.setLastInvocationTime(this, currentTime);
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
			if (PrefsManager.getLocationState(this))
			{
				LocationUpdates(0, PrefsManager.LATITUDE_IDLE);
			}
		}
		if (   (wakelock != null)
			&& (lastCounterSteps == -2)
			&& (orientationState != -1))
		{
			wakelock.release();
			wakelock = null;
		}
	}

// Commented out as we don't want this debugging code in the real app
// It runs a test to see which combinations of interruption filter and ringer
// mode are actually valid on Marshmallow and later versions
/*
	@TargetApi(android.os.Build.VERSION_CODES.M)
	// debugging code
	private void currentRingerMode() {
		String s = "Current state is ";
		int filter =
			((NotificationManager)
				getSystemService(Context.NOTIFICATION_SERVICE))
				.getCurrentInterruptionFilter();
		switch (filter) {
			case  NotificationManager.INTERRUPTION_FILTER_NONE:
				s += "INTERRUPTION_FILTER_NONE";
				break;
			case  NotificationManager.INTERRUPTION_FILTER_ALARMS:
				s += "INTERRUPTION_FILTER_ALARMS";
				break;
			case  NotificationManager.INTERRUPTION_FILTER_PRIORITY:
				s += "INTERRUPTION_FILTER_PRIORITY";
				break;
			case  NotificationManager.INTERRUPTION_FILTER_ALL:
				s += "INTERRUPTION_FILTER_ALL";
				break;
			default:
				s += "Unknown interruption filter " + String.valueOf(filter);
				break;
		}
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int mode = audio.getRingerMode();
		switch (mode) {
			case AudioManager.RINGER_MODE_NORMAL:
				s += ", RINGER_MODE_NORMAL";
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				s += ", RINGER_MODE_VIBRATE";
				break;
			case AudioManager.RINGER_MODE_SILENT:
				s += ", RINGER_MODE_SILENT";
				break;
			default:
				s += ", Unknown ringer mode " + String.valueOf(mode);
				break;
		}
		new MyLog(this, s);
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	// debugging code
	private void testRingerMode1(int filter, int mode) {
		String s = "Setting state to ";
		switch (filter)
		{
			case NotificationManager.INTERRUPTION_FILTER_NONE:
				s += "INTERRUPTION_FILTER_NONE";
				break;
			case NotificationManager.INTERRUPTION_FILTER_ALARMS:
				s += "INTERRUPTION_FILTER_ALARMS";
				break;
			case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
				s += "INTERRUPTION_FILTER_PRIORITY";
				break;
			case NotificationManager.INTERRUPTION_FILTER_ALL:
				s += "INTERRUPTION_FILTER_ALL";
				break;
			default:
				s += "Unknown interruption filter " + String.valueOf(filter);
				break;
		}
		((NotificationManager)
			getSystemService(Context.NOTIFICATION_SERVICE))
			.setInterruptionFilter(filter);
		switch (mode)
		{
			case AudioManager.RINGER_MODE_NORMAL:
				s += ", RINGER_MODE_NORMAL";
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				s += ", RINGER_MODE_VIBRATE";
				break;
			case AudioManager.RINGER_MODE_SILENT:
				s += ", RINGER_MODE_SILENT";
				break;
			default:
				s += ", Unknown ringer mode " + String.valueOf(mode);
				break;
		}
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		audio.setRingerMode(mode);
		new MyLog(this, s);
		currentRingerMode();
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	// debugging code
	private void testRingerMode2(int filter, int mode) {
		String s = "Setting state to ";
		switch (mode) {
			case AudioManager.RINGER_MODE_NORMAL:
				s += "RINGER_MODE_NORMAL";
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				s += "RINGER_MODE_VIBRATE";
				break;
			case AudioManager.RINGER_MODE_SILENT:
				s += "RINGER_MODE_SILENT";
				break;
			default:
				s += "Unknown ringer mode " + String.valueOf(mode);
				break;
		}
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		audio.setRingerMode(mode);
		switch (filter) {
			case  NotificationManager.INTERRUPTION_FILTER_NONE:
				s += ", INTERRUPTION_FILTER_NONE";
				break;
			case  NotificationManager.INTERRUPTION_FILTER_ALARMS:
				s += ", INTERRUPTION_FILTER_ALARMS";
				break;
			case  NotificationManager.INTERRUPTION_FILTER_PRIORITY:
				s += ", INTERRUPTION_FILTER_PRIORITY";
				break;
			case  NotificationManager.INTERRUPTION_FILTER_ALL:
				s += ", INTERRUPTION_FILTER_ALL";
				break;
			default:
				s += ", Unknown interruption filter " + String.valueOf(filter);
				break;
		}
		((NotificationManager)
			getSystemService(Context.NOTIFICATION_SERVICE))
			.setInterruptionFilter(filter);
		new MyLog(this, s);
		currentRingerMode();
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	// debugging code
	private void exerciseModes() {
		currentRingerMode();
		int filter = NotificationManager.INTERRUPTION_FILTER_NONE;
		int mode =  AudioManager.RINGER_MODE_SILENT;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		mode =  AudioManager.RINGER_MODE_VIBRATE;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		mode =  AudioManager.RINGER_MODE_NORMAL;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		filter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
		mode =  AudioManager.RINGER_MODE_SILENT;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		mode =  AudioManager.RINGER_MODE_VIBRATE;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		mode =  AudioManager.RINGER_MODE_NORMAL;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		filter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
		mode =  AudioManager.RINGER_MODE_SILENT;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		mode =  AudioManager.RINGER_MODE_VIBRATE;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		mode =  AudioManager.RINGER_MODE_NORMAL;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		filter = NotificationManager.INTERRUPTION_FILTER_ALL;
		mode =  AudioManager.RINGER_MODE_SILENT;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		mode =  AudioManager.RINGER_MODE_VIBRATE;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
		mode =  AudioManager.RINGER_MODE_NORMAL;
		testRingerMode1(filter, mode);
		testRingerMode2(filter, mode);
	}
*/

	@Override
	public void onHandleIntent(Intent intent) {
		new MyLog(this, "onHandleIntent("
				  .concat(intent.toString())
				  .concat(")"));
		if (intent.getAction() == MUTESERVICE_RESET) {
			doReset();
		}
		updateState(intent);
		WakefulBroadcastReceiver.completeWakefulIntent(intent);
	}
	
	public static void startIfNecessary(Context c, String caller) {
			c.startService(new Intent(caller, null, c, MuteService.class));
	}
}
