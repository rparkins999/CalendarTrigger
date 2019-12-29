/*
 * Copyright (c) 2019, Richard P. Parkins, M. A.
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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.support.v4.content.PermissionChecker;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.widget.RemoteViews;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.calendar.CalendarProvider;
import uk.co.yahoo.p1rpp.calendartrigger.contacts.ContactCreator;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.DataStore;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.SQLtable;

import static java.net.Proxy.Type.HTTP;

public class MuteService extends IntentService
	implements SensorEventListener {

    // These only used within a single invocation of updateState().
    // They are class variables only to avoid a lot of argument passing.
    private long m_timeNow;
    private CalendarProvider.NextAlarm m_nextAlarm;

    public static final String MUTESERVICE_RESET =
		"CalendarTrigger.MuteService.Reset";

	public static final String MUTESERVICE_SMS_RESULT =
		"CalendarTrigger.MuteService.SmaFail";

	// some times in milliseconds
    private static final int SIXTYONE_SECONDS = 61 * 1000;
	private static final int FIVE_MINUTES = 5 * 60 * 1000;

	private static final int MODE_WAIT = 0;
    private static final int DELAY_WAIT = 1;

	public MuteService() {
		super("CalendarTriggerService");
		mHandler = new Handler() {
			@Override
			public void handleMessage(Message inputMessage) {
                Context owner = (Context)inputMessage.obj;
				if (inputMessage.arg1 == MODE_WAIT)
				{
					int mode = PrefsManager.getCurrentMode(owner);
					int wantedMode = PrefsManager.getLastRinger(owner);
					if (wantedMode == PrefsManager.RINGER_MODE_MUTED)
					{
						PrefsManager.setMuteResult(owner, mode);
					}
					new MyLog(owner,
							  "Handler got mode "
							  + PrefsManager.getEnglishStateName(mode));
					PrefsManager.setLastRinger(owner, mode);
				}
				else if (inputMessage.arg1 == DELAY_WAIT)
				{
                    new MyLog(owner, "DELAY_WAIT message received");
                    startIfNecessary(owner, "DELAY_WAIT message");
					return;
				}
                unlock("handleMessage");
			}
		};
	}

	private static float accelerometerX;
	private static float accelerometerY;
	private static float accelerometerZ;
	private long m_nextAccelTime;

	// Safe for this to be local because if our class gets re-instantiated we
	// create a new one. If the handler is actually in use we have a wake lock
	// so our class instance shouldn't get destroyed.
	private static Handler mHandler =  null;
	private static final int what = 0;

	// Safe for this to be local because if it is not null we have a wakelock
	// and we won't get stopped.
	public static PowerManager.WakeLock wakelock = null;
	private void lock() { // get the wake lock if we don't already have it
		if (wakelock == null)
		{
			new MyLog(this, "Getting lock");
			PowerManager powerManager
				= (PowerManager)getSystemService(POWER_SERVICE);
			wakelock = powerManager.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK, "CalendarTrigger:");
			wakelock.acquire();
		}
	}
	private void unlock(String s) { // release the wake lock if we no longer
		// need it
		int lcs = PrefsManager.getStepCount(this);
		int orientation = PrefsManager.getOrientationState(this);
		if (wakelock != null)
		{
			if (   (   (lcs == PrefsManager.STEP_COUNTER_IDLE)
					|| (lcs == PrefsManager.STEP_COUNTER_WAKEUP))
				&& (orientation != PrefsManager.ORIENTATION_WAITING)
				&& ((mHandler == null)
					|| !mHandler.hasMessages(what)))
			{
				new MyLog(this, "End of " + s + ", releasing lock\n");
				wakelock.release();
				wakelock = null;
			}
			else
			{
				new MyLog(this, "End of " + s + ", retaining lock\n");
			}
		}
		else
		{
			new MyLog(this, "End of " + s + ", no lock\n");
		}
	}

	private static int notifyId = 1400;

	// Safe for this to be local because it is only used to transfer state from
	// doReset() to updateState() which is called after doReset().
	private static boolean resetting = false;

	// We don't know anything sensible to do here
	@Override
	public void onAccuracyChanged(Sensor sensor, int i) {
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		switch (sensorEvent.sensor.getType()) {
			case Sensor.TYPE_STEP_COUNTER: {
				int newCounterSteps = (int) sensorEvent.values[0];
				if (newCounterSteps != PrefsManager.getStepCount(this)) {
					PrefsManager.setStepCount(this, newCounterSteps);
					startIfNecessary(this, "Step counter changed");
				}
			}
			break;
			case Sensor.TYPE_ACCELEROMETER:
				SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
				sm.unregisterListener(this);
				accelerometerX = sensorEvent.values[0];
				accelerometerY = sensorEvent.values[1];
				accelerometerZ = sensorEvent.values[2];
				PrefsManager.setOrientationState(this,
						PrefsManager.ORIENTATION_DONE);
				startIfNecessary(this, "Accelerometer event");
				break;
			default:
				// do nothing, should never happen
		}
	}

	private void emitNotification(String smallText, String bigText, String path) {
		RemoteViews layout = new RemoteViews(
			"uk.co.yahoo.p1rpp.calendartrigger",
			R.layout.notification);
		layout.setTextViewText(R.id.notificationtext, bigText);
		DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT);
		layout.setTextViewText(R.id.notificationtime,
							   df.format(System.currentTimeMillis()));
		Notification.Builder NBuilder
			= new Notification.Builder(this)
			.setSmallIcon(R.drawable.notif_icon)
			.setContentTitle(smallText)
			.setContent(layout);
		if ((path != null) && !path.isEmpty())
		{
			Uri uri = new Uri.Builder().path(path).build();
			AudioAttributes.Builder ABuilder
				= new AudioAttributes.Builder()
				.setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
				.setLegacyStreamType(AudioManager.STREAM_NOTIFICATION);
			NBuilder.setSound(uri, ABuilder.build());
		}
		// Show notification
		NotificationManager notifManager = (NotificationManager)
			getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(notifyId++, NBuilder.build());
	}

	private void PermissionFail(int mode)
	{
		emitNotification(getString(R.string.permissionfail),
			getString(R.string.permissionfailbig), null);
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
				PermissionChecker.checkSelfPermission(
					this, Manifest.permission.READ_PHONE_STATE);
			if (!canCheckCallState)
			{
				if (!PrefsManager.getNotifiedCannotReadPhoneState(this))
				{
					emitNotification(getString(R.string.readphonefail),
						getString(R.string.readphonefailbig),
						null);
					new MyLog(this, "CalendarTrigger no longer has permission "
									+ "READ_PHONE_STATE.");
					PrefsManager.setNotifiedCannotReadPhoneState(this, true);
				}
			}
		}
		return phoneState;
	}

	private String logOffset(long offset) {
		DateFormat df = DateFormat.getTimeInstance();
		df.setTimeZone(TimeZone.getTimeZone("GMT"));
		if (offset >= 0)
		{
			return df.format(offset);
		}
		else
		{
			return "-".concat(df.format(-offset));
		}
	}

	// Check if the time zone has changed.
	// If it has, we wait a bit for the CalendarProvider to update before undoing
	// its changes for any floating time events.
	private void CheckTimeZone(CalendarProvider provider, SQLtable table) {
		int lastOffset = PrefsManager.getLastTimezoneOffset(this);
		new MyLog(this, "CheckTimeZone: lastoffset is "
			+ logOffset(lastOffset));
		int seenOffset = PrefsManager.getLastSeenOffset(this);
		new MyLog(this, "CheckTimeZone: seenoffset is "
			+ logOffset(seenOffset));
		int currentOffset = TimeZone.getDefault().getOffset(m_timeNow);
		new MyLog(this, "CheckTimeZone: currentoffset is "
			+ logOffset(currentOffset));
		if (currentOffset != lastOffset)
		{
			if (currentOffset != seenOffset) {
				PrefsManager.setLastSeenOffset(this, currentOffset);
				PrefsManager.setUpdateTime(
						this, m_timeNow + FIVE_MINUTES);
			}
			else if (PrefsManager.getUpdateTime(this) <= m_timeNow)
			{
				// At least 5 minutes since last time zone change
				PrefsManager.setUpdateTime(this, Long.MAX_VALUE);
				PrefsManager.setLastTimezoneOffset(this, currentOffset);
				provider.doTimeZoneAdjustment(this, currentOffset, table);
			}
			// else nothing to do yet
		}
		else
		{
			PrefsManager.setLastSeenOffset(this, currentOffset);
			PrefsManager.setUpdateTime(this, Long.MAX_VALUE);
		}
	}

	// Do log cycling if it is enabled and needed now
	private void doLogCycling()
	{
		if (!PrefsManager.getLogcycleMode(this))
		{
			new MyLog(this,
					  "Exited doLogCycling because log cycling disabled");
			return; // not enabled
		}
		long next = PrefsManager.getLastcycleDate(this);
		next = next + CalendarProvider.ONE_DAY;
		next -= next % CalendarProvider.ONE_DAY;
		if (m_timeNow < next)
		{
			new MyLog(this,
					  "Exited doLogCycling because not time yet");
			return; // not time to do it yet
		}
		next = m_timeNow - m_timeNow % CalendarProvider.ONE_DAY;
		PrefsManager.setLastCycleDate(this, next);
		ArrayList<String> log = new ArrayList<String>();
		try
		{
			boolean inBlock = false;
			DateFormat df = DateFormat.getDateTimeInstance();
			int pp = DataStore.DATAPREFIX.length();
			File f= new File(DataStore.LogFileName());
			BufferedReader in = new BufferedReader(new FileReader(f));
			String line;
			while ((line = in.readLine()) != null)
			{
				if (line.startsWith(DataStore.DATAPREFIX))
				{
					Date dd = df.parse(line, new ParsePosition(pp));
					if (dd != null)
					{
						inBlock = dd.getTime() > m_timeNow - CalendarProvider.ONE_DAY;
					}
				}
				if (inBlock)
				{
					log.add(line); // keep if < 1 day old
				}
			}
			in.close();
			f.delete();
			BufferedWriter out = new BufferedWriter(new FileWriter(f));
			for (String s : log)
			{
				out.write(s);
				out.newLine();
			}
			out.close();
		}
		catch (FileNotFoundException e)
		{
			return; // no log file is OK if user just flushed it
		}
		catch (Exception e)
		{
			emitNotification(getString(R.string.logcyclingerror),
				e.toString(), null);
			new MyLog(this,
					  "Exited doLogCycling because of exception "
						+ e.toString());
		}
	}

    // FIXME can we use a similar power saving trick as accelerometer?
	// return true if step counter is now running
	private boolean StartStepCounter(int classNum) {
		if (PrefsManager.getStepCount(this) == PrefsManager.STEP_COUNTER_IDLE)
		{
			SensorManager sensorManager =
				(SensorManager)getSystemService(Activity.SENSOR_SERVICE);
			if (sensorManager == null) { return false; }
			Sensor sensor = sensorManager.getDefaultSensor(
				Sensor.TYPE_STEP_COUNTER, true);
            if (sensor == null)
            {
                // if we can't get a wakeup step counter, try without
                sensor =
                    sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (sensor == null)
                {
                    // no step counter at all
                    return false;
                }
            }
			if (sensorManager.registerListener(
			    this, sensor, SensorManager.SENSOR_DELAY_NORMAL))
			{
                new MyLog(this, "Step counter activated for class "
                    .concat(PrefsManager.getClassName(this, classNum)));
                if (sensor.isWakeUpSensor())
                {
                    PrefsManager.setStepCount(
                        this, PrefsManager.STEP_COUNTER_WAKEUP);
                }
                else
                {
                    PrefsManager.setStepCount(
                        this, PrefsManager.STEP_COUNTER_WAKE_LOCK);
					lock();
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
					lm.requestLocationUpdates(
						"gps", 5 * 60 * 1000, meters, pi);
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
	private boolean checkOrientationWait(int classNum, boolean before)
	{
		int wanted;
		if (before)
		{
			wanted = PrefsManager.getBeforeOrientation(this, classNum);
		}
		else
		{
			wanted = PrefsManager.getAfterOrientation(this, classNum);
		}
		if (   (wanted == 0)
			|| (wanted == PrefsManager.BEFORE_ANY_POSITION))
		{
			return false;
		}
		switch (PrefsManager.getOrientationState(this))
		{
			case PrefsManager.ORIENTATION_IDLE: // sensor currently not active
				SensorManager sm = (SensorManager)getSystemService(SENSOR_SERVICE);
				Sensor ams = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
				if (ams == null) { return false; }
				lock();
				PrefsManager.setOrientationState(
					this, PrefsManager.ORIENTATION_WAITING);
				sm.registerListener(this, ams,
									SensorManager.SENSOR_DELAY_FASTEST);
				new MyLog(this,
						  "Requested accelerometer value for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
				//FALLTHRU
			case PrefsManager.ORIENTATION_WAITING: // waiting for value
				return true;
			case PrefsManager.ORIENTATION_DONE: // just got a value
				m_nextAccelTime = m_timeNow + FIVE_MINUTES;
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
				m_nextAccelTime = m_timeNow + FIVE_MINUTES;
				PrefsManager.setOrientationState(
					this, PrefsManager.ORIENTATION_IDLE);
				new MyLog(this,
						  "Still waiting for orientation for class "
						  .concat(PrefsManager.getClassName(this, classNum)));
				return true;
			default:
				return false;
		}
	}

	// return true if still waiting for correct connection
	private boolean checkConnectionWait(int classNum, boolean before)
	{
		int wanted;
		if (before) {
			wanted = PrefsManager.getBeforeConnection(this, classNum);
		}
		else
		{
			wanted = PrefsManager.getAfterConnection(this, classNum);
		}
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
	boolean setCurrentRinger(AudioManager audio,
		int apiVersion, int mode, int current) {
		if (   (current == mode)
			|| (   (mode == PrefsManager.RINGER_MODE_NONE)
			    && (current == PrefsManager.RINGER_MODE_NORMAL))
			|| (   (mode == PrefsManager.RINGER_MODE_MUTED)
				   && (current == PrefsManager.getMuteResult(this))))
		{
			return false;  // no change
		}
		PrefsManager.setLastRinger(this, mode);
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
		int gotmode = PrefsManager.getCurrentMode(this);
		new MyLog(this,
				  "Tried to set mode "
				  + PrefsManager.getEnglishStateName(mode)
				  + ", actually got "
				  + PrefsManager.getEnglishStateName(gotmode));
		// Some versions of Android give us a mode different from the one that
		// we asked for, and some versions of Android take a while to do it.
		// We use a Handler to delay getting the mode actually set.
		mHandler.sendMessageDelayed(
			mHandler.obtainMessage(what, MODE_WAIT, 0, this), 1000);
		new MyLog(this,
				  "mHandler.hasMessages() returns "
				  + (mHandler.hasMessages(what) ? "true" : "false"));
		lock();
		return true;
	}

	private String getEventName(SQLtable activeEvents) {
		try {
			if (activeEvents.getUnsignedLong(SQLtable.ACTIVE_IMMEDIATE) == 0) {
				long instanceId =
					activeEvents.getUnsignedLong(SQLtable.ACTIVE_INSTANCE_ID);
				Cursor cr = getContentResolver().query(
					ContentUris.withAppendedId(
						CalendarContract.Instances.CONTENT_URI, instanceId),
					new String[] { CalendarContract.Instances.TITLE },
					null, null, null);
				if (cr.moveToNext()) {
					return "event " + cr.getString(0);
				}
				else
				{
					return "deleted event";
				}
			}
		} catch (NumberFormatException e) {}
		return "immediate event";
	}

	private void moveToState(
		int newState, String cause, Long time, SQLtable activeEvents)
    {
        if ((m_timeNow < time) && (time < m_nextAlarm.time)) {
            m_nextAlarm.reason = cause;
            m_nextAlarm.time = time;
            m_nextAlarm.eventName = getEventName(activeEvents);
			m_nextAlarm.className = activeEvents.getString(SQLtable.ACTIVE_CLASS_NAME);
        }
        ContentValues cv = new ContentValues();
        cv.put("ACTIVE_NEXT_ALARM", time);
        cv.put("ACTIVE_STATE", newState);
        activeEvents.update(cv);
    }

	private void moveToStartWaiting(
		String cause, Long time, SQLtable activeEvents)
	{
		moveToState(SQLtable.ACTIVE_START_WAITING, cause, time, activeEvents);
	}

	private void moveToStartSending(
		String cause, Long time, SQLtable activeEvents)
	{
		moveToState(SQLtable.ACTIVE_START_SENDING, cause, time, activeEvents);
	}

	private void moveToStarted(
		String cause, Long time, SQLtable activeEvents)
	{
		moveToState(SQLtable.ACTIVE_STARTED, cause, time, activeEvents);
	}

	// Currently not used since this transition is done in CalendarProvider.fillActive().
	private void moveToEndWaiting(
		String cause, Long time, SQLtable activeEvents)
	{
		moveToState(SQLtable.ACTIVE_END_WAITING, cause, time, activeEvents);
	}

	private void moveToEndSending(
		String cause, Long time, SQLtable activeEvents)
	{
		moveToState(SQLtable.ACTIVE_END_SENDING, cause, time, activeEvents);
	}

    // Try to get an email address and/or a phone number from a contact
	private String[] destinationsFromContact(
	    int classNum, int startOrEnd, String eventName) {
        String s = PrefsManager.getMessageContact(this, classNum, startOrEnd);
        if (s == null) {
            if (PrefsManager.getMessageExtract(this, classNum, startOrEnd)) {
                //Parse the event name to get a contact
                String[] sen = eventName.replace("event ","")
										.split(" ");
                int len = sen.length;
                int first = PrefsManager.getMessageFirstCount(
                    this, classNum, startOrEnd);
                if (PrefsManager.getMessageFirstDir(
                    this, classNum, startOrEnd)
                    == PrefsManager.MESSAGE_DIRECTION_LEFT) {
                    first = len - first - 1;
                }
                if ((first >= len) || (first < 0)) {
                    new MyLog(this,
                        "No word " + first + " in " + eventName);
                    return new String[] { null, null };
                }
                int last = PrefsManager.getMessageLastCount(
                    this, classNum, startOrEnd);
                if (PrefsManager.getMessageLastDir(this, classNum, startOrEnd)
                    == PrefsManager.MESSAGE_DIRECTION_LEFT) {
                    last = len - last - 1;
                }
                if ((last >= len) || (last < 0)) {
                    new MyLog(this,
                        "No word " + last + " in " + eventName);
                    return new String[] { null, null };
                }
                String sf = sen[first];
                String sl = sen[last];
                if (PrefsManager.getMessageTrim(this, classNum, startOrEnd)) {
                    sf = sf.replace(",", "")
                           .replace("'s", "");
                    sl = sl.replace(",", "")
                           .replace("'s", "");
                }
                s = sf + " " + sl;
            } else {
                new MyLog(this,
                    "No destination specified for "
					+ eventName + " of class "
                    + PrefsManager.getClassName(this, classNum));
                return new String[] { null, null };
            }
        }
        return ContactCreator.getMessaging(this, s);
    }

    @TargetApi(android.os.Build.VERSION_CODES.M)
	// Return 0 (which causes the caller to wait and try again later)
	// if we want to send a message and we have a valid message and we have
	// permission to send it but we don't currently have network connectivity.
	// Return the end time of the event (don't wait) otherwise.
	private long tryMessage(
	    int classNum, int startOrEnd, SQLtable activeEvents)
    {
		String rawEventName = null;
    	String eventName;
		String eventDescription = null;
		long endTime;
		long zero = 0;
		try {
			if (activeEvents.getUnsignedLong(SQLtable.ACTIVE_IMMEDIATE) == 0) {
				long instanceId =
					activeEvents.getUnsignedLong(SQLtable.ACTIVE_INSTANCE_ID);
				Cursor cr = getContentResolver().query(
					ContentUris.withAppendedId(
						CalendarContract.Instances.CONTENT_URI, instanceId),
					new String[] {
						CalendarContract.Instances.TITLE,
						CalendarContract.Instances.DESCRIPTION,
						CalendarContract.Instances.END},
					null, null, null);
				if (cr.moveToNext()) {
					rawEventName = cr.getString(0);
					eventName = "event " + rawEventName;
					eventDescription = cr.getString(1);
					endTime = cr.getLong(2);
					if (startOrEnd == PrefsManager.SEND_MESSAGE_AT_START) {
						endTime -= PrefsManager.getBeforeMinutes(this, classNum);
					}
					else
					{
						long after = PrefsManager.getAfterMinutes(this, classNum);
						// Don't keep trying after end of event
						Long soon = m_timeNow + CalendarProvider.FIVE_MINUTES;
						if (after > 0)
						{
							endTime += after;
							if (endTime > soon) { zero = soon; }
						}
						else
						{
							if (endTime > soon) { zero = soon; }
							endTime += after;
						}
					}
				}
				else
				{
					eventName = "deleted event";
					endTime = m_timeNow;
				}
			}
			else
			{
				throw(new NumberFormatException());
			}
		} catch (NumberFormatException e) {
			eventName = "immediate event";
			endTime = m_timeNow;
		}
        int messageType = PrefsManager.getMessageType(
            this, classNum,startOrEnd);
        if (   (messageType == PrefsManager.SEND_NO_MESSAGE)
            || (messageType == PrefsManager.SEND_MESSAGE_NOWHERE)) {
            // If the user doesn't want a message at all, don't bother
            // to check if we can send one.
            return endTime;
        }
        String body = null;
        switch (PrefsManager.getMessageTextType(this, classNum, startOrEnd)) {
            case PrefsManager.MESSAGE_TEXT_NONE:
            default:
                body = null;
                break;
            case PrefsManager.MESSAGE_TEXT_CLASSNAME:
                body = PrefsManager.getClassName(this, classNum);
                break;
            case PrefsManager.MESSAGE_TEXT_EVENTNAME:
                body = rawEventName;
                break;
            case PrefsManager.MESSAGE_TEXT_EVENTDESCRIPTION:
                body = eventDescription;
                break;
            case PrefsManager.MESSAGE_TEXT_LITERAL:
                body = PrefsManager.getMessageLiteral(
                    this, classNum, startOrEnd);
                break;
        }
        if ((body == null) || body.isEmpty()) {
            new MyLog(this,
                "No message sent because of empty message body for "
				+ eventName + " of class "
				+ PrefsManager.getClassName(this, classNum));
            return endTime;
        }
        // Work out which kinds of message we can send at present.
        boolean smsAvailable = false;
        boolean emailAvailable = false;
        if (PackageManager.PERMISSION_GRANTED ==
            PermissionChecker.checkSelfPermission(
                this, Manifest.permission.ACCESS_NETWORK_STATE)) {
            ConnectivityManager cm =
                (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network networks[] = cm.getAllNetworks();
                for (Network network : networks) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                    if (   (nc.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED))
                        && (nc.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_VALIDATED)))
                    {
                        if (nc.hasTransport(
                            NetworkCapabilities.TRANSPORT_CELLULAR))
                        {
                            smsAvailable = true;
                        }
                        if (nc.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        {
                            emailAvailable = true;
                        }
                    }
                }
            }
            else
            {
                // if we can't check for connectivity, assume we have it, so we
                // just try to send and if we fail, that's just the user's bad luck.
                smsAvailable = true;
                emailAvailable = true;
            }
        }
        else
        {
            smsAvailable = true;
            emailAvailable = true;
        }
        final Intent intentSms
            = new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("smsto:"));
        boolean canSms = (intentSms.resolveActivity(getPackageManager()) != null);
        final Intent intentEmail
            = new Intent(Intent.ACTION_SENDTO).setData(Uri.parse("mailto:"));
        boolean canEmail = (intentEmail.resolveActivity(getPackageManager()) != null);

        // Now work out what to do
        String[] destinations =
			destinationsFromContact(classNum, startOrEnd, eventName);
        if (destinations[0] == null) {
            destinations[0] = PrefsManager.getMessageNumber(
                this, classNum, startOrEnd);
        }
        if (destinations[1] == null) {
            destinations[1] = PrefsManager.getMessageAddress(
                this, classNum, startOrEnd);
        }
        if (   canSms
            && (   (messageType == PrefsManager.SEND_MESSAGE_SMS)
                || (messageType == PrefsManager.SEND_MESSAGE_EMAIL_OR_SMS)
                || (   (!(canEmail && emailAvailable)
                    && (messageType == PrefsManager.SEND_MESSAGE_SMS_OR_EMAIL)))))
        {
			if (destinations[0] == null) {
				new MyLog(this,
					"Could not find phone number for "
					+ eventName + " of class "
					+ PrefsManager.getClassName(this, classNum));
				canSms = false;
			}
			else if (PackageManager.PERMISSION_GRANTED !=
                PermissionChecker.checkSelfPermission(
                    this, Manifest.permission.SEND_SMS)) {
                new MyLog(this,
                    "No permission to send SMS",
                    "CalendarTrigger does not have permission" +
                        " to send an SMS message for " +
                        eventName + " of class " +
                        PrefsManager.getClassName(this, classNum));
                canSms = false;
            }
            else if (!smsAvailable) {
                return zero;
            }
            else
            {
                SmsManager smsManager;
                if (android.os.Build.VERSION.SDK_INT >=
                    android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    int id = SmsManager.getDefaultSmsSubscriptionId();
                    if (id == -1) {
                        new MyLog(this, "No Default subscription ID",
                            "Running on API level 22 or later,"
                                + "but SmsManager.getDefaultSmsSubscriptionId() fails.");
                        smsManager = SmsManager.getDefault();
                    } else {
                        smsManager = SmsManager.getSmsManagerForSubscriptionId(id);
                    }
                } else {
                    smsManager = SmsManager.getDefault();
                }
                ArrayList<String> bodyParts = smsManager.divideMessage(body);
                int n = bodyParts.size();
                ArrayList<PendingIntent> intents = new ArrayList<>(n);
                for (int i = 0; i < n; ++i) {
                    Intent intent = new Intent(MUTESERVICE_SMS_RESULT, null,
                        this, MuteService.class);
                    intent.putExtra("PartNumber", i);
                    intent.putExtra("EventName", eventName);
                    intents.add(i, PendingIntent.getBroadcast(
                        this, i,
                        intent, 0));
                }
                new MyLog(this,
					"Sending SMS for " + eventName + " of class " +
					PrefsManager.getClassName(this, classNum));
                smsManager.sendMultipartTextMessage(destinations[0], null,
                    bodyParts, intents, null);
                return endTime;
            }
        }
        if (   canEmail
            && (   (messageType == PrefsManager.SEND_MESSAGE_EMAIL)
                || (messageType == PrefsManager.SEND_MESSAGE_EMAIL_OR_SMS)
                || (   (!canSms)
                    && ((messageType == PrefsManager.SEND_MESSAGE_SMS_OR_EMAIL)))))
        {
			if (destinations[1] == null) {
				new MyLog(this,
					"Could not find email address for " +
						eventName + " of class " +
						PrefsManager.getClassName(this, classNum));
			}
			else if (!emailAvailable) {
				return endTime; // don't wait for email until we can do it
			}
			else
			{
				return endTime;
			}
        }
        return endTime;
    }

    // Process the active event list, updating states if possible,
	// and perform any actions required.
	// Incidentally we compute the next time we need to run and either
	// set an alarm if it is later than 1 minute from now or send ourselves
	// a delayed message if it is sooner since Android won't let us set
	// an alarm so soon.
	@TargetApi(Build.VERSION_CODES.M)
	public void updateState(Intent intent) {
		// Timestamp used in all requests (so it remains consistent)
        m_timeNow = System.currentTimeMillis();
		if (resetting)
		{
			PrefsManager.setOrientationState(this, PrefsManager.ORIENTATION_IDLE);
			LocationUpdates(0, PrefsManager.LATITUDE_IDLE);
		}
		doLogCycling();
		int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int wantedMode = PrefsManager.RINGER_MODE_NONE;
		int user = PrefsManager.getUserRinger(this);
		int last =  PrefsManager.getLastRinger(this);
		int current = PrefsManager.getCurrentMode(this);
		new MyLog(this,
			"last mode is "
				+ PrefsManager.getEnglishStateName(last)
				+ ", current mode is "
				+ PrefsManager.getEnglishStateName(current));
		/* This will do the wrong thing if the user changes the mode during the
		 * one second that we are waiting for Android to set the new mode, but
		 * there seems to be no workaround because Android is a bit
		 * unpredictable in this area. Since Android can delay setting the
		 * mode that we asked for, or even set a different mode, but doesn't
		 * always do so, we can't tell if a change was done by Android or by the
		 * user.
		 */
		if (   (last != current)
			&& (last != PrefsManager.RINGER_MODE_NONE)
			&& (!mHandler.hasMessages(what)))
		{
			// user changed ringer mode
			user = current;
			PrefsManager.setUserRinger(this, user);
			new MyLog(this,
				"Setting user ringer to "
					+ PrefsManager.getEnglishStateName(user));
		}
		m_nextAccelTime = Long.MAX_VALUE;
		String notifyType = null;
		String notifyEvent = "";
		String notifyClassName = null;
		String notifySoundFile = "";
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
				PermissionChecker.checkSelfPermission(
					this, Manifest.permission.ACCESS_FINE_LOCATION);
		if (PrefsManager.getStepCount(this) >= 0)
		{
			new MyLog(this, "Step counter running");
		}
		CalendarProvider provider = new CalendarProvider(this);
		SQLtable activeEvents = new SQLtable(this, "ACTIVEEVENTS");
		CheckTimeZone(provider, activeEvents);
		m_nextAlarm = provider.fillActive(activeEvents, this, m_timeNow);
		while (activeEvents.moveToNext()) {
			String className = activeEvents.getString(SQLtable.ACTIVE_CLASS_NAME);
			int classNum = PrefsManager.getClassNum(this, className);
			if (classNum < 0) {
				// Class no longer exists or table entry corrupted:
				// delete the entry.
				String small = this.getString(R.string.classnotfound, className);
				StringBuilder builder = new StringBuilder(small);
				builder.append(this.getString(R.string.forrow));
				builder.append(activeEvents.rowToString());
				builder.append(this.getString(R.string.deletingit));
				new MyLog(this, small, builder.toString());
				activeEvents.delete();
				continue;
			}
			int state;
			String eventName = getEventName(activeEvents);
			try {
				state = (int) activeEvents.getUnsignedLong(SQLtable.ACTIVE_STATE);
			} catch (NumberFormatException e) {
				String small = getString(R.string.badactivestate);
				String big = getString(
					R.string.bigbadactivestate,
					activeEvents.getString(0), eventName);
				new MyLog(this, small, big);
				activeEvents.delete();
				continue;
			}
			if (   (state < SQLtable.ACTIVE_END_WAITING)
				&& eventName.equals("deleted event"))
			{
				// Force deleted event to end
				state = SQLtable.ACTIVE_END_WAITING;
			}
 			switch (state) {
				case SQLtable.NOT_ACTIVE:
					activeEvents.delete();
					continue;
				case SQLtable.ACTIVE_START_WAITING:
				    if (phoneState == PrefsManager.PHONE_CALL_ACTIVE) {
				        // We can't start yet, the user is on the phone.
                        // We'll get awakened again when the phone state changes,
                        // but we check again after 5 minutes for safety.
                        moveToStartWaiting("phone off hook check for",
                            m_timeNow + provider.FIVE_MINUTES, activeEvents);
				        continue;
                    }
				    else if (checkOrientationWait(classNum, true)) {
				        // waiting for device to be in the correct orientation
                        moveToStartWaiting("orientation check for",
                            m_nextAccelTime, activeEvents);
                        continue;
                    }
				    else if (checkConnectionWait(classNum, true)) {
                        moveToStartWaiting("USB connection check for",
                            m_timeNow + provider.FIVE_MINUTES, activeEvents);
                        continue;
                    }
				    // We can advance to STARTING now
				case SQLtable.ACTIVE_STARTING:
                    PrefsManager.setTargetSteps(this, classNum, 0);
                    PrefsManager.setLatitude(this, classNum, 360.0);
					String soundFile = PrefsManager.getSoundFileStart(
						this, classNum);
					boolean sound = PrefsManager.getPlaysoundStart(
                        this, classNum) && !soundFile.isEmpty();
					if (soundFile.isEmpty()) { sound = false; }
                    int ringerAction = PrefsManager.getRingerAction(
                        this, classNum);
                    boolean ringChange = ringerAction > wantedMode;
                    if (ringChange) {
                        wantedMode = ringerAction;
                    }
					if (   (ringChange || sound) && notifySoundFile.isEmpty()
						&& PrefsManager.getNotifyStart(this, classNum))
                    {
						notifyType = "start of ";
						notifyEvent = eventName;
						notifyClassName = className;
                        if (sound) { notifySoundFile = soundFile; }
                    }
                    // We can advance to START_SENDING or STARTED now
				case SQLtable.ACTIVE_START_SENDING:
                    long endTime = tryMessage(classNum,
                        PrefsManager.SEND_MESSAGE_AT_START, activeEvents);
                    if (endTime == 0)
					{
						moveToStartSending("send wait at start of ",
							m_timeNow + CalendarProvider.ONE_HOUR, activeEvents);
					}
                    else
					{
						moveToStarted("for end of ", endTime, activeEvents);
					}
					continue;
				case SQLtable.ACTIVE_STARTED:
					try {
						long time =
							activeEvents.getUnsignedLong(SQLtable.ACTIVE_NEXT_ALARM);
						if ((m_timeNow < time) && (time < m_nextAlarm.time)) {
							m_nextAlarm.reason = "for end of ";
							m_nextAlarm.time = time;
							m_nextAlarm.eventName = getEventName(activeEvents);
							m_nextAlarm.className =
								activeEvents.getString(SQLtable.ACTIVE_CLASS_NAME);
						}
					} catch (NumberFormatException e) {
						activeEvents.deleteBad();
					}
				    continue;
				case SQLtable.ACTIVE_END_WAITING:
					int aftersteps =
						PrefsManager.getAfterSteps(this, classNum);
					if (haveStepCounter && (aftersteps > 0))
					{
						int lastCounterSteps = PrefsManager.getStepCount(this);
						int targetSteps = PrefsManager.getTargetSteps(
							this, classNum);
						if (targetSteps == 0)
						{
							if (lastCounterSteps == PrefsManager.STEP_COUNTER_IDLE) {
								// need to start up the sensor
								if (StartStepCounter(classNum)) {
									anyStepCountActive = true;
									continue;
								}
							}
							else
							{
								// sensor is now running, set target count
								new MyLog(this,
									"Setting target steps for " + eventName
										+ " of " + className + " to " + lastCounterSteps
										+ " + " + aftersteps);
								PrefsManager.setTargetSteps(this, classNum,
									lastCounterSteps + aftersteps);
								anyStepCountActive = true;
								continue;
							}
						}
						else if (lastCounterSteps < targetSteps)
						{
							// not reached target yet
							anyStepCountActive = true;
							continue;
						}
						// otherwise we reached the target
					}
					if (havelocation && (PrefsManager.getAfterMetres(
							this, classNum) > 0))
					{
						// keep it active while waiting for location
						startLocationWait(classNum, intent);
						anyLocationActive = true;
						continue;
					}
					double latitude
						= PrefsManager.getLatitude(this, classNum);
					if (   (latitude != 360.0)
						&& checkLocationWait(classNum, latitude, intent))
					{
						anyLocationActive = true;
						continue;
					}
					if (checkOrientationWait(classNum, false)) {
						// waiting for device to be in the correct orientation
						moveToStartWaiting("orientation check for ",
							m_nextAccelTime, activeEvents);
						continue;
					}
					else if (checkConnectionWait(classNum, false)) {
						moveToStartWaiting("USB connection check for ",
							m_timeNow + provider.FIVE_MINUTES, activeEvents);
						continue;
					}
					// We can advance to ENDING now
				case SQLtable.ACTIVE_ENDING:
					PrefsManager.setTargetSteps(this, classNum, 0);
					soundFile = PrefsManager.getSoundFileEnd(
						this, classNum);
					sound = PrefsManager.getPlaysoundEnd(this, classNum)
							&& ! soundFile.isEmpty();
					if (soundFile.isEmpty()) { sound = false; }
					if (   (PrefsManager.getRestoreRinger(this, classNum))
						&& (last < current) && (user >= wantedMode))
					{
						wantedMode = last;
						ringChange = true;
					}
					else
					{
						ringChange = false;
					}
					if (   (ringChange || sound) && notifySoundFile.isEmpty()
						&& PrefsManager.getNotifyEnd(this, classNum))
					{
						if (eventName.equals("deleted event")) {
							notifyType = "";
						}
						else
						{
							notifyType = "end of ";
						}
						notifyEvent = eventName;
						notifyClassName = className;
					}
					if (sound) { notifySoundFile = soundFile; }

					// We can advance to END_SENDING or NOT_ACTIVE now
				case SQLtable.ACTIVE_END_SENDING:
					endTime = tryMessage(classNum,
						PrefsManager.SEND_MESSAGE_AT_END, activeEvents);
					if (endTime == 0)
					{
						moveToEndSending("send wait at end of ",
							m_timeNow + CalendarProvider.ONE_HOUR, activeEvents);
					}
					else
					{
						// Finished with this instance
						new MyLog(this, "Completed actions for " +
								eventName + " of " + className);
						activeEvents.delete();
					}
					continue;
				default:
					String small = getString(R.string.badactivestate);
					StringBuilder builder = new StringBuilder(small);
					builder.append(this.getString(R.string.forrow));
					builder.append(activeEvents.rowToString());
					builder.append(this.getString(R.string.deletingit));
					new MyLog(this, small, builder.toString());
                    activeEvents.delete();
					continue;
			}
		}
		if (   (PackageManager.PERMISSION_GRANTED ==
			PermissionChecker.checkSelfPermission(
				this, Manifest.permission.READ_CONTACTS))
			&& (PackageManager.PERMISSION_GRANTED ==
			PermissionChecker.checkSelfPermission(
				this, Manifest.permission.WRITE_CONTACTS))
			&& (PrefsManager.getNextLocationMode(this)))
		{
			CalendarProvider.StartAndLocation sl =
				provider.nextLocation(this, m_timeNow);
			if (sl != null)
			{
				ContactCreator cc = new  ContactCreator(this);
				cc.makeContact("!NextEventLocation", "", sl.location);
				long slst = sl.startTime + 60000;
				if (   (slst < m_nextAlarm.time)
					&& (slst > m_timeNow))
				{
					m_nextAlarm.reason = "start of event ";
					m_nextAlarm.time = slst;
					m_nextAlarm.eventName = sl.eventName;
					m_nextAlarm.className = null;
				}
			}
		}
		String small;
		if (wantedMode < user) { wantedMode = user; }
		else if (wantedMode == PrefsManager.RINGER_MODE_NONE)
		{
			wantedMode = PrefsManager.RINGER_MODE_NORMAL;
		}
		boolean changed =
			setCurrentRinger(audio, currentApiVersion,  wantedMode, current);
		if (notifyType != null)
		{
			if (changed) {
				small = "Ringer mode set to " +
					PrefsManager.getRingerStateName(this, wantedMode);
			}
			else {
				small = "Ringer mode unchanged";
			}
			StringBuilder big = new StringBuilder(small);
			if (notifyType != null) { big.append(notifyType).append(notifyEvent); }
			if (notifyClassName != null) {
				big.append(" of class ").append(notifyClassName);
			}
			emitNotification(small, big.toString(), notifySoundFile);
			new MyLog(this, big.toString());
			if (!notifySoundFile.isEmpty()) {
				big.replace(0, big.length() - 1,
					"Playing sound for ").append(notifyType).append(notifyEvent);
				if (notifyClassName != null) {
					big.append(" of class ").append(notifyClassName);
				}
				new MyLog(this, big.toString());
			}
		}
		else if (resetting)
		{
			new MyLog(this,
				"Setting audio mode to "
					+ PrefsManager.getEnglishStateName(wantedMode)
					+ " after reset");
		}
		resetting = false;
		PendingIntent pIntent = PendingIntent.getBroadcast(
			this, 0 /*requestCode*/,
			new Intent("CalendarTrigger.Alarm", Uri.EMPTY,
				this, StartServiceReceiver.class),
			PendingIntent.FLAG_UPDATE_CURRENT);
		AlarmManager alarmManager = (AlarmManager)
			getSystemService(Context.ALARM_SERVICE);
		long updateTime = PrefsManager.getUpdateTime(this);
		if (updateTime < m_nextAlarm.time)
		{
			m_nextAlarm.time = updateTime;
			m_nextAlarm.reason = " for time zone change";
		}
		if (m_nextAlarm.time == Long.MAX_VALUE)
		{
			alarmManager.cancel(pIntent);
			new MyLog(this, "Alarm cancelled");
		}
		else
		{
			// Sometimes Android delivers an alarm a few seconds early. In that
			// case we don't do the actions for the alarm (because it isn't
			// time for it yet), but we used not to set the alarm if it had not
			// changed. This resulted in the actions getting lost until we got
			// called again for some reason. However Android also won't set an
			// alarm for less than 1 minute in the future, so in this case we
			// use a handler to schedule another invocation instead.
			long delay = m_nextAlarm.time - System.currentTimeMillis();
			DateFormat df = DateFormat.getDateTimeInstance();
			if (delay < SIXTYONE_SECONDS)
			{
				// If we took a long time executing this procedure, we may have
				// gone past the next alarm time: in that case we reschedule
				// with a zero delay.
				if (delay < 0) { delay = 0; }
				mHandler.sendMessageDelayed(
					mHandler.obtainMessage(
						what, DELAY_WAIT, 0, this), delay);
				lock();
				new MyLog(this, "Delayed message set for "
					.concat(df.format(m_nextAlarm.time))
					.concat(m_nextAlarm.reason));
			}
			else
			{
				if (currentApiVersion >= android.os.Build.VERSION_CODES.M)
				{
					alarmManager.setExactAndAllowWhileIdle(
						AlarmManager.RTC_WAKEUP, m_nextAlarm.time, pIntent);
				}
				else
				{
					alarmManager.setExact(
						AlarmManager.RTC_WAKEUP, m_nextAlarm.time, pIntent);
				}
				new MyLog(this, "Alarm time set to "
					.concat(df.format(m_nextAlarm.time))
					.concat(m_nextAlarm.reason));
			}
			PrefsManager.setLastAlarmTime(this, m_nextAlarm.time);
		}

		PrefsManager.setLastInvocationTime(this, m_timeNow);
		if (!anyStepCountActive)
		{
			if (PrefsManager.getStepCount(this)
				!= PrefsManager.STEP_COUNTER_IDLE)
			{
				PrefsManager.setStepCount(this, PrefsManager.STEP_COUNTER_IDLE);
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
		unlock("updateState");
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
			resetting = true;
		}
		else if (intent.getAction() == MUTESERVICE_SMS_RESULT) {
			int result = intent.getIntExtra("ResultCode", Activity.RESULT_OK);
			if (result != Activity.RESULT_OK) {
				String bigText = "Failed (" + result + ") to send SMS message part "
					+ intent.getIntExtra("PartNumber", 0)
					+ " for event "
					+ intent.getStringExtra("EventName");
				emitNotification(
					"SMS send failure " + result, bigText, null);
				new MyLog(this, bigText);
			}
			WakefulBroadcastReceiver.completeWakefulIntent(intent);
			return;
		}
		updateState(intent);
		WakefulBroadcastReceiver.completeWakefulIntent(intent);
	}

	public static class StartServiceReceiver
			extends WakefulBroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			intent.setClass(context, MuteService.class);
			intent.putExtra("ResultCode", getResultCode());
			startWakefulService(context, intent);
		}
	}

	public static void startIfNecessary(Context c, String caller) {
			c.startService(new Intent(caller, null, c, MuteService.class));
	}
}
