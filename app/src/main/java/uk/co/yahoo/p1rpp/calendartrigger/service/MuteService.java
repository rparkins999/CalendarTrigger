/*
 * Copyright (c) 2019, Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.service;

import android.Manifest;
import android.annotation.TargetApi;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
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
import uk.co.yahoo.p1rpp.calendartrigger.utilities.NoColumnException;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.Notifier;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.SQLtable;

public class MuteService extends IntentService
	implements SensorEventListener {

    // These only used within a single invocation of updateState().
    // They are class variables only to avoid a lot of argument passing.
    private long m_timeNow;
    private CalendarProvider.NextAlarm m_nextAlarm;

    public static final String MUTESERVICE_RESET =
		"CalendarTrigger.MuteService.Reset";

	public static final String MUTESERVICE_SMS_RESULT =
		"CalendarTrigger.MuteService.SmsFail";

	// some times in milliseconds
    private static final int SIXTYONE_SECONDS = 61 * 1000;

	private static final int MODE_WAIT = 0;
    private static final int DELAY_WAIT = 1;

	@SuppressLint(value = "HandlerLeak")
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
							  owner.getString(R.string.gotmode)
							  + PrefsManager.getRingerStateName(owner, mode));
					PrefsManager.setLastRinger(owner, mode);
				}
				else if (inputMessage.arg1 == DELAY_WAIT)
				{
                    new MyLog(owner,
						getString(R.string.delaywaitmessage) +
						getString(R.string.received));
                    startIfNecessary(owner, getString(R.string.delaywaitmessage));
					return;
				}
                unlock(getString(R.string.handlemessage));
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
	@SuppressLint("WakelockTimeout") // doesn't seem to work, done in project settings
	public static PowerManager.WakeLock wakelock = null;
	private void lock() { // get the wake lock if we don't already have it
		if (wakelock == null)
		{
			PowerManager powerManager
				= (PowerManager)getSystemService(POWER_SERVICE);
            if (powerManager == null) {
                String small = getString(R.string.nopowermanager);
                new MyLog(this, small,
                    small + getString(R.string.bignomanager));
            }
            else {
				new MyLog(this, getString(R.string.gettinglock));
				wakelock = powerManager.newWakeLock(
					PowerManager.PARTIAL_WAKE_LOCK, "CalendarTrigger:");
				wakelock.acquire();
			}
		}
	}
	// release the wake lock if we no longer need it
	private void unlock(String s) {
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
				new MyLog(this, getString(R.string.endof) +
					s + getString(R.string.releasinglock));
				wakelock.release();
				wakelock = null;
			}
			else
			{
				new MyLog(this, getString(R.string.endof) +
					s + getString(R.string.retaininglock));
			}
		}
		else
		{
			new MyLog(this, getString(R.string.endof) +
				s + getString(R.string.nolock));
		}
	}

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
				if (sm == null) {
				    String small = getString(R.string.noSensorManager);
				    new MyLog(this, true, small,
                        small + getString(R.string.noSensorManagerimpossible));
                }
				else
				{
                    sm.unregisterListener(this);
                    accelerometerX = sensorEvent.values[0];
                    accelerometerY = sensorEvent.values[1];
                    accelerometerZ = sensorEvent.values[2];
                    PrefsManager.setOrientationState(this,
                        PrefsManager.ORIENTATION_DONE);
                    startIfNecessary(this, "Accelerometer event");
                }
				break;
			default:
				// do nothing, should never happen
		}
	}

	private void PermissionFail(int mode)
	{
		new MyLog(this, getString(R.string.permissionfail),
			getString(R.string.setmodefail)
			+ PrefsManager.getRingerStateName(this, mode)
			+ getString(R.string.permissionfailbig));
	}

	// Check if there is a current call (not a ringing call).
	// If so we don't want to mute even if an event starts.
	int UpdatePhoneState(Intent intent) {
		// 0 idle
		// 1 incoming call ringing (but no active call)
		// 2 call active
		int phoneState = PrefsManager.getPhoneState(this);
		if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction()))
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
					new MyLog(this, getString(R.string.readphonefail),
						getString(R.string.readphonefailbig));
					PrefsManager.setNotifiedCannotReadPhoneState(
						this, true);
				}
			}
		}
		return phoneState;
	}

	// Check if the time zone has changed.
	// If it has, we wait a bit for the CalendarProvider to update before undoing
	// its changes for any floating time events.
	private void CheckTimeZone(CalendarProvider provider, SQLtable table) {
		int lastOffset = PrefsManager.getLastTimezoneOffset(this);
		int seenOffset = PrefsManager.getLastSeenOffset(this);
		int currentOffset = TimeZone.getDefault().getOffset(m_timeNow);
		if (currentOffset != lastOffset)
		{
			if (currentOffset != seenOffset) {
				PrefsManager.setLastSeenOffset(this, currentOffset);
				PrefsManager.setUpdateTime(
						this, m_timeNow + CalendarProvider.FIVE_MINUTES);
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
			new MyLog(this,  getString(R.string.cyclingnotenabled));
			return; // not enabled
		}
		long next = PrefsManager.getLastcycleDate(this);
		next = next + CalendarProvider.ONE_DAY;
		next -= next % CalendarProvider.ONE_DAY;
		if (m_timeNow < next)
		{
			new MyLog(this, getString(R.string.cyclingnotyet));
			return; // not time to do it yet
		}
		next = m_timeNow - m_timeNow % CalendarProvider.ONE_DAY;
		PrefsManager.setLastCycleDate(this, next);
		ArrayList<String> log = new ArrayList<>();
		try
		{
			boolean inBlock = false;
			DateFormat df = DateFormat.getDateTimeInstance();
			int pp = DataStore.DATAPREFIX.length();
			File f = new File(DataStore.LogFileName());
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
			//noinspection ResultOfMethodCallIgnored
			f.delete();
			BufferedWriter out = new BufferedWriter(new FileWriter(f));
			for (String s : log)
			{
				out.write(s);
				out.newLine();
			}
			out.close();
		}
		catch (FileNotFoundException ignore)
		{
			// no log file is OK if user just flushed it
		}
		catch (Exception e)
		{
			new MyLog(this, getString(R.string.logcyclingerror),
					  getString(R.string.cyclingerrorbig)
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
                new MyLog(this, getString(R.string.stepstarted)
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
		if (lm == null) {
			new MyLog(this, getString(R.string.nolocationmanager));
		}
		else
		{
			String s = "CalendarTrigger.Location";
			PendingIntent pi = PendingIntent.getBroadcast(
				this, 0 /*requestCode*/,
				new Intent(s, Uri.EMPTY, this, StartServiceReceiver.class),
				PendingIntent.FLAG_UPDATE_CURRENT);
			if (which == PrefsManager.LATITUDE_IDLE) {
				lm.removeUpdates(pi);
				PrefsManager.setLocationState(this, false);
				new MyLog(this, getString(R.string.locationstopped));
			} else {
				List<String> ls = lm.getProviders(true);
				if (which == PrefsManager.LATITUDE_FIRST) {
					if (ls.contains("gps")) {
						// The first update may take a long time if we are inside a
						// building, but this is OK because we won't want to restore
						// the state until we've left the building. If we don't
						// force the use of GPS here, we may get a cellular network
						// fix which can be some distance from our real position and
						// if we then get a GPS fix while we are still in the
						// building we can think that we have moved when in fact we
						// haven't.
						lm.requestSingleUpdate("gps", pi);
						new MyLog(this, getString(R.string.gpsstarting)
								.concat(PrefsManager.getClassName(
									this, classNum)));
					} else {
						// If we don't have GPS, we use whatever the device can give
						// us.
						Criteria cr = new Criteria();
						cr.setAccuracy(Criteria.ACCURACY_FINE);
						lm.requestSingleUpdate(cr, pi);
						new MyLog(this, getString(R.string.finestarting)
								.concat(PrefsManager.getClassName(
									this, classNum)));
					}
					PrefsManager.setLocationState(this, true);
					PrefsManager.setLatitude(
						this, classNum, PrefsManager.LATITUDE_FIRST);
				} else {
					float meters =
						(float) PrefsManager.getAfterMetres(this, classNum);
					if (ls.contains("gps")) {
						lm.requestLocationUpdates(
							"gps", 5 * 60 * 1000, meters, pi);
					} else {
						Criteria cr = new Criteria();
						cr.setAccuracy(Criteria.ACCURACY_FINE);
						lm.requestLocationUpdates(5 * 60 * 1000, meters, cr, pi);
					}
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
			new MyLog(this, getString(R.string.setgeofence)
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(getString(R.string.atlocation))
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
				new MyLog(this, getString(R.string.setgeofence)
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(getString(R.string.atlocation))
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
					new MyLog(this, getString(R.string.withingeofence)
							  .concat(PrefsManager.getClassName(this, classNum))
							  .concat(getString(R.string.atlocation))
							  .concat(((Double)here.getLatitude()).toString())
							  .concat(", ")
							  .concat(((Double)here.getLongitude()).toString()));
					return true;
				}
				// else we've exited the geofence
				PrefsManager.setLatitude(
					this, classNum, PrefsManager.LATITUDE_IDLE);
				new MyLog(this, getString(R.string.Exitedgeofence)
						  .concat(PrefsManager.getClassName(this, classNum))
						  .concat(getString(R.string.atlocation))
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
				if (sm == null) { return false; }
				Sensor ams = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
				if (ams == null) { return false; }
				lock();
				PrefsManager.setOrientationState(
					this, PrefsManager.ORIENTATION_WAITING);
				sm.registerListener(this, ams,
									SensorManager.SENSOR_DELAY_FASTEST);
				new MyLog(this, getString(R.string.Requestedaccelerometer)
						  .concat(PrefsManager.getClassName(this, classNum)));
				//FALLTHRU
			case PrefsManager.ORIENTATION_WAITING: // waiting for value
				return true;
			case PrefsManager.ORIENTATION_DONE: // just got a value
				m_nextAccelTime = m_timeNow + CalendarProvider.FIVE_MINUTES;
				new MyLog(this,
					getString(R.string.accelerometerx) + accelerometerX
					+ getString(R.string.accelerometery) + accelerometerY
					+ getString(R.string.accelerometerz) + accelerometerZ);
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
				m_nextAccelTime = m_timeNow + CalendarProvider.FIVE_MINUTES;
				PrefsManager.setOrientationState(
					this, PrefsManager.ORIENTATION_IDLE);
				new MyLog(this, getString(R.string.orientationwaiting)
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
		try {
			//noinspection ConstantConditions NullpointerEception caught
			int charge
				= registerReceiver(
				null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED))
				.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
			UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
			//noinspection ConstantConditions NullpointerEception caught
			HashMap<String, UsbDevice> map = manager.getDeviceList();
			if (((wanted & PrefsManager.BEFORE_WIRELESS_CHARGER) != 0)
				&& (charge == BatteryManager.BATTERY_PLUGGED_WIRELESS)) {
				return false;
			}
			if (((wanted & PrefsManager.BEFORE_FAST_CHARGER) != 0)
				&& (charge == BatteryManager.BATTERY_PLUGGED_AC)) {
				return false;
			}
			if (((wanted & PrefsManager.BEFORE_PLAIN_CHARGER) != 0)
				&& (charge == BatteryManager.BATTERY_PLUGGED_USB)) {
				return false;
			}
			if ((wanted & PrefsManager.BEFORE_PERIPHERAL) != 0) {
				if (!map.isEmpty()) {
					return false;
				}
			}
			return ((wanted & PrefsManager.BEFORE_UNCONNECTED) == 0)
				|| (charge != 0)
				|| !map.isEmpty();
		} catch (NullPointerException ignored) {
			return false;
		}
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	// Set the ringer mode. Returns true if mode changed.
	void setCurrentRinger(AudioManager audio,
		int apiVersion, int mode, int current) {
		if (   (current == mode)
			|| (   (mode == PrefsManager.RINGER_MODE_NONE)
			    && (current == PrefsManager.RINGER_MODE_NORMAL))
			|| (   (mode == PrefsManager.RINGER_MODE_MUTED)
				   && (current == PrefsManager.getMuteResult(this))))
		{
			return;
		}
		PrefsManager.setLastRinger(this, mode);
		NotificationManager nm = null;
		if (apiVersion >= android.os.Build.VERSION_CODES.M)
		{
			nm = (NotificationManager)
				getSystemService(Context.NOTIFICATION_SERVICE);
			if (nm != null ) {
				switch (mode) {
					case PrefsManager.RINGER_MODE_SILENT:
						audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
						if (nm.isNotificationPolicyAccessGranted()) {
							nm.setInterruptionFilter(
								NotificationManager.INTERRUPTION_FILTER_NONE);
						} else {
							PermissionFail(mode);
						}
						break;
					case PrefsManager.RINGER_MODE_ALARMS:
						if (nm.isNotificationPolicyAccessGranted()) {
							audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
							nm.setInterruptionFilter(
								NotificationManager.INTERRUPTION_FILTER_ALARMS);
							break;
						}
						/*FALLTHRU if no permission, treat as muted */
					case PrefsManager.RINGER_MODE_DO_NOT_DISTURB:
						if (nm.isNotificationPolicyAccessGranted()) {
							audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
							nm.setInterruptionFilter(
								NotificationManager.INTERRUPTION_FILTER_PRIORITY);
							break;
						}
						PermissionFail(mode);
						/*FALLTHRU if no permission, treat as muted */
					case PrefsManager.RINGER_MODE_MUTED:
						if (nm.isNotificationPolicyAccessGranted()) {
							nm.setInterruptionFilter(
								NotificationManager.INTERRUPTION_FILTER_ALL);
						}
						audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
						break;
					case PrefsManager.RINGER_MODE_VIBRATE:
						if (nm.isNotificationPolicyAccessGranted()) {
							nm.setInterruptionFilter(
								NotificationManager.INTERRUPTION_FILTER_ALL);
						}
						audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
						break;
					case PrefsManager.RINGER_MODE_NORMAL:
					case PrefsManager.RINGER_MODE_NONE:
						if (nm.isNotificationPolicyAccessGranted()) {
							nm.setInterruptionFilter(
								NotificationManager.INTERRUPTION_FILTER_ALL);
						}
						audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
						break;
					default: // unknown
						return;
				}
			}
		}
		if (nm == null)
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
					return;
			}
		}
		int gotmode = PrefsManager.getCurrentMode(this);
		new MyLog(this, getString(R.string.trysetmode)
				  + PrefsManager.getEnglishStateName(mode)
				  + getString(R.string.actuallygot)
				  + PrefsManager.getEnglishStateName(gotmode));
		// Some versions of Android give us a mode different from the one that
		// we asked for, and some versions of Android take a while to do it.
		// We use a Handler to delay getting the mode actually set.
		mHandler.sendMessageDelayed(
			mHandler.obtainMessage(what, MODE_WAIT, 0, this), 1000);
		lock();
	}

	private void moveToState (
		int newState, String cause, Long time, SQLtable activeInstances)
		throws NoColumnException
    {
        if ((m_timeNow < time) && (time < m_nextAlarm.time)) {
            m_nextAlarm.reason = cause;
            m_nextAlarm.time = time;
			m_nextAlarm.className =
				activeInstances.getString("ACTIVE_CLASS_NAME");
            m_nextAlarm.eventName =
				activeInstances.getString("ACTIVE_EVENT_NAME");
        }
        ContentValues cv = new ContentValues();
        cv.put("ACTIVE_NEXT_ALARM", time);
        cv.put("ACTIVE_STATE", newState);
        activeInstances.update(cv);
    }

	private void moveToStartWaiting(
		String cause, Long time, SQLtable activeInstances)
		throws NoColumnException
	{
		moveToState(SQLtable.ACTIVE_START_WAITING, cause, time, activeInstances);
	}

	private void moveToStartSending(
		@SuppressWarnings("SameParameterValue") String cause,
		Long time, SQLtable activeInstances)
		throws NoColumnException
	{
		moveToState(SQLtable.ACTIVE_START_SENDING, cause, time, activeInstances);
	}

	private void moveToStarted(
		@SuppressWarnings("SameParameterValue") String cause,
		Long time, SQLtable activeInstances)
		throws NoColumnException
	{
		moveToState(SQLtable.ACTIVE_STARTED, cause, time, activeInstances);
	}

	// Currently not used since this transition is done in CalendarProvider.fillActive().
	@SuppressWarnings("unused")
	private void moveToEndWaiting(
		String cause, Long time, SQLtable activeInstances)
		throws NoColumnException
	{
		moveToState(SQLtable.ACTIVE_END_WAITING, cause, time, activeInstances);
	}

	private void moveToEndSending(
		@SuppressWarnings("SameParameterValue") String cause,
		Long time, SQLtable activeInstances)
		throws NoColumnException
	{
		moveToState(SQLtable.ACTIVE_END_SENDING, cause, time, activeInstances);
	}

    // Try to get an email address and/or a phone number from a contact
	private String[] destinationsFromContact(
	    int classNum, int startOrEnd, String eventName) {
        String s = PrefsManager.getMessageContact(this, classNum, startOrEnd);
        if (s == null) {
            if (PrefsManager.getMessageExtract(this, classNum, startOrEnd)) {
                //Parse the event name to get a contact
				String rawEventName =
					eventName.replace(getString(R.string.event),"");
                String[] sen = rawEventName.split(" ");
                int len = sen.length;
                int first = PrefsManager.getMessageFirstCount(
                    this, classNum, startOrEnd);
                if (PrefsManager.getMessageFirstDir(
                    this, classNum, startOrEnd)
                    == PrefsManager.MESSAGE_DIRECTION_LEFT) {
                    first = len - first - 1;
                }
                if ((first >= len) || (first < 0)) {
                    new MyLog(this, getString(R.string.noword) +
						first + " in " + rawEventName);
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
						getString(R.string.noword) + last + " in " + rawEventName);
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
                    getString(R.string.nodestination)
					+ eventName + getString(R.string.ofclass)
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
	    int classNum, int startOrEnd, SQLtable activeInstances)
		throws NoColumnException
    {
		String rawEventName = null;
    	String eventName;
		String eventDescription = null;
		long endTime = m_timeNow;
		long zero = 0;
		try {
			switch ((int) activeInstances.getUnsignedLong("ACTIVE_LIVE")) {
				case SQLtable.ACTIVE_LIVE_NORMAL:
					rawEventName =
						activeInstances.getString("ACTIVE_EVENT_NAME");
					eventName = getString(R.string.event) + rawEventName;
					eventDescription =
						activeInstances.getString("ACTIVE_DESCRIPTION");
					endTime =
						activeInstances.getUnsignedLong("ACTIVE_END_TIME");
					if (startOrEnd == PrefsManager.SEND_MESSAGE_AT_START) {
						endTime -=
							PrefsManager.getBeforeMinutes(this, classNum);
					} else {
						long after =
							PrefsManager.getAfterMinutes(this, classNum);
						// Don't keep trying after end of event
						Long soon = m_timeNow + CalendarProvider.FIVE_MINUTES;
						if (after > 0) {
							endTime += after;
							if (endTime > soon) {
								zero = soon;
							}
						} else {
							if (endTime > soon) {
								zero = soon;
							}
							endTime += after;
						}
					}
					break;
				case SQLtable.ACTIVE_DELETED:
					eventName = getString(R.string.deleted);
					break;
				case SQLtable.ACTIVE_IMMEDIATE:
					eventName = getString(R.string.immediate);
					break;
				default:
					return m_timeNow;
			}
		} catch (NumberFormatException ignored) { return m_timeNow; }
        int messageType = PrefsManager.getMessageType(
            this, classNum,startOrEnd);
        if (   (messageType == PrefsManager.SEND_NO_MESSAGE)
            || (messageType == PrefsManager.SEND_MESSAGE_NOWHERE)) {
            // If the user doesn't want a message at all, don't bother
            // to check if we can send one.
            return m_timeNow;
        }
        String body;
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
                getString(R.string.nomsgbody)
				+ eventName + getString(R.string.ofclass)
				+ PrefsManager.getClassName(this, classNum));
            return m_timeNow;
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
                for (Network network : cm.getAllNetworks()) {
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
					getString(R.string.nophonenumber)
					+ eventName + getString(R.string.ofclass)
					+ PrefsManager.getClassName(this, classNum));
				canSms = false;
			}
			else if (PackageManager.PERMISSION_GRANTED !=
                PermissionChecker.checkSelfPermission(
                    this, Manifest.permission.SEND_SMS)) {
                new MyLog(this,
                    getString(R.string.smallnosms),
                    getString(R.string.bignosms) +
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

		// This is the last ringer mode we saw set by the user,
		// defaulting to RINGER_MODE_NONE if we've never seen the user set it.
		int userMode = PrefsManager.getUserRinger(this);

		// This is the last ringer mode that we set, used to catch the user changing it.
		int lastMode =  PrefsManager.getLastRinger(this);

		// This is the ringer mode now (on entry to updateState())
		int currentMode = PrefsManager.getCurrentMode(this);

		new MyLog(this,
			getString(R.string.lastis)
				+ PrefsManager.getEnglishStateName(lastMode)
				+ getString(R.string.currentis)
				+ PrefsManager.getEnglishStateName(currentMode));
		/* This will do the wrong thing if the user changes the mode during the
		 * one second that we are waiting for Android to set the new mode, but
		 * there seems to be no workaround because Android is a bit
		 * unpredictable in this area. Since Android can delay setting the
		 * mode that we asked for, or even set a different mode, but doesn't
		 * always do so, we can't tell if a change was done by Android or by the
		 * user.
		 */
		if (   (userMode == PrefsManager.RINGER_MODE_NONE)
			|| ((lastMode != currentMode) && !mHandler.hasMessages(what)))
		{
			// New installation of CalendarTrigger
			// or user changed ringer mode since we last set it
			userMode = currentMode;
			PrefsManager.setUserRinger(this, userMode);
			new MyLog(this,
				getString(R.string.setuser)
					+ PrefsManager.getEnglishStateName(userMode));
		}
		new MyLog(this, "userMode is "
				+ PrefsManager.getEnglishStateName(userMode));

		// This is the ringer mode we will set before exiting,
		// if no events are active we will normally restore the user's mode.
		int wantedMode = userMode;

		m_nextAccelTime = Long.MAX_VALUE;
		String notifyType = null;
		String notifyEvent = "";
		String notifyClassName = null;
		String notifySoundFile = "";
		new MyLog(this, "About to call UpdatePhoneState()");
		int phoneState = UpdatePhoneState(intent);
		new MyLog(this, "Returned from UpdatePhoneState()");
		boolean anyStepCountActive = false;
		boolean anyLocationActive = false;
		new MyLog(this, "About to call getPackageManager()");
		PackageManager packageManager = getPackageManager();
		new MyLog(this, "Returned from getPackageManager()");
		final boolean haveStepCounter =
			currentApiVersion >= android.os.Build.VERSION_CODES.KITKAT
				&& packageManager.hasSystemFeature(
				PackageManager.FEATURE_SENSOR_STEP_COUNTER);
		new MyLog(this, "haveStepCounter = "
			+ (haveStepCounter ? "true" : "false"));
		final boolean havelocation =
			PackageManager.PERMISSION_GRANTED ==
				PermissionChecker.checkSelfPermission(
					this, Manifest.permission.ACCESS_FINE_LOCATION);
		new MyLog(this, "havelocation = "
			+ (havelocation ? "true" : "false"));
		if (PrefsManager.getStepCount(this) >= 0)
		{
			new MyLog(this, getString(R.string.stepactive));
		}
		else
		{
			new MyLog(this, "Step counter not running");
		}
		CalendarProvider provider = new CalendarProvider();
		SQLtable activeInstances =
			new SQLtable(this, "ACTIVEINSTANCES");
		new MyLog(this, "About to call CheckTimeZone()");
		CheckTimeZone(provider, activeInstances);
		new MyLog(this, "About to call provider.fillActive()");
		m_nextAlarm = provider.fillActive(activeInstances, this, m_timeNow);
		activeInstances.moveToPosition(-1);
		new MyLog(this, "moveToNext() called from MuteService line 1755");
		while (activeInstances.moveToNext()) {
			try {
				String className =
					activeInstances.getString("ACTIVE_CLASS_NAME");
				int classNum = PrefsManager.getClassNum(this, className);
				if (classNum < 0) {
					// Class no longer exists or table entry corrupted:
					// delete the entry.
					String small = this.getString(R.string.classnotfound, className);
					new MyLog(this, small, small
                        + this.getString(R.string.forrow)
                        + activeInstances.rowToString()
                        + this.getString(R.string.deletingit));
					activeInstances.delete();
					continue;
				}
				int state;
				String eventName =
					activeInstances.getString("ACTIVE_EVENT_NAME");
				try {
					state =
						(int) activeInstances.getUnsignedLong("ACTIVE_STATE");
				} catch (NumberFormatException ignored) {
					String small = getString(R.string.badactivestate);
					String big = getString(
						R.string.bigbadactivestate,
						activeInstances.getString("ACTIVE_STATE"), eventName);
					new MyLog(this, small, big);
					activeInstances.delete();
					continue;
				}
				if (   (state < SQLtable.ACTIVE_END_WAITING)
					&& (   eventName.equals("deleted event")
                        || (activeInstances.getUnsignedLong("ACTIVE_LIVE")
                            == SQLtable.ACTIVE_DELETED))) {
					new MyLog(this,
						"Forcing ACTIVE_END_WAITING for deleted event of class "
						+ className);
					// Force deleted event to end
					state = SQLtable.ACTIVE_END_WAITING;
				}
				new MyLog(this, "Switching on state "
					+ activeInstances.getActiveStateName(state)
					+ " for event " + eventName + " of class " + className);
				switch (state) {
					case SQLtable.NOT_ACTIVE:
						activeInstances.delete();
						continue;
					case SQLtable.ACTIVE_START_WAITING:
						if (phoneState == PrefsManager.PHONE_CALL_ACTIVE) {
							// We can't start yet, the user is on the phone.
							// We'll get awakened again when the phone state changes,
							// but we check again after 5 minutes for safety.
							moveToStartWaiting("phone off hook check for",
								m_timeNow + CalendarProvider.FIVE_MINUTES,
								activeInstances);
							continue;
						} else if (checkOrientationWait(classNum, true)) {
							// waiting for device to be in the correct orientation
							moveToStartWaiting("orientation check for",
								m_nextAccelTime, activeInstances);
							continue;
						} else if (checkConnectionWait(classNum, true)) {
							moveToStartWaiting("USB connection check for",
								m_timeNow + CalendarProvider.FIVE_MINUTES,
								activeInstances);
							continue;
						}
						// We can advance to STARTING now
						new MyLog(this,
							"Advancing to state ACTIVE_STARTING for event "
								+ eventName + " of class " + className);
					case SQLtable.ACTIVE_STARTING:
						PrefsManager.setTargetSteps(this, classNum, 0);
						PrefsManager.setLatitude(this, classNum, 360.0);
						String soundFile = PrefsManager.getSoundFileStart(
							this, classNum);
						boolean sound;
						if (soundFile.isEmpty()) {
							sound = false;
						}
						else
                        {
                            sound = PrefsManager.getPlaysoundStart(
							this, classNum);
                        }
						int ringerAction = PrefsManager.getRingerAction(
							this, classNum);
						new MyLog(this, "wantedMode is "
							+ PrefsManager.getEnglishStateName(wantedMode)
							+ ", ringerAction is "
							+ PrefsManager.getEnglishStateName(ringerAction)
							+ " starting event " + eventName + " of class " + className);
						if (!PrefsManager.getRestoreRinger(this, classNum))
						{
							/* This is a fudge to make not restoring the ringer at
							 * the end of the event work. We raise the user ringer
							 * mode to the mode that this class wants.
							 */
							if (userMode < ringerAction) {
								userMode = ringerAction;
							}
						}
						boolean ringChange = ringerAction > wantedMode;
						if (ringerAction > wantedMode) {
							// Set quieter mode wanted by this class
							wantedMode = ringerAction;
						}
						/* We can only do one notification at a time .
						 * If a class sets a quieter ringer mode we show its
						 * notification: otherwise we show the first notification
						 * that we find which requests a sound, if there is one.
						 */
						if (   (ringChange || (sound && notifySoundFile.isEmpty()))
							&& PrefsManager.getNotifyStart(this, classNum)) {
							notifyType = "start of ";
							notifyEvent = eventName;
							notifyClassName = className;
							if (sound) {
								notifySoundFile = soundFile;
							}
						}
						// We can advance to START_SENDING or STARTED now
						new MyLog(this,
							"Advancing to state ACTIVE_START_SENDING for event "
								+ eventName + " of class " + className);
					case SQLtable.ACTIVE_START_SENDING:
						if (state == SQLtable.ACTIVE_START_SENDING)
						{
							// We got here from switch, not from advancing.
							ringerAction = PrefsManager.getRingerAction(
								this, classNum);
							new MyLog(this, "wantedMode is "
								+ PrefsManager.getEnglishStateName(wantedMode)
								+ ", ringerAction is "
								+ PrefsManager.getEnglishStateName(ringerAction)
								+ " during event " + eventName
								+ " of class " + className);
							if (ringerAction > wantedMode) {
								// Set quieter mode wanted by this class
								wantedMode = ringerAction;
								if (notifySoundFile.isEmpty()) {
									// Suppress any silent notification
									// for an event which has ended
									notifyType = null;
								}
							}
						}
						long endTime = tryMessage(classNum,
							PrefsManager.SEND_MESSAGE_AT_START, activeInstances);
						if (endTime == 0) {
							moveToStartSending("send wait at start of ",
								m_timeNow + CalendarProvider.ONE_HOUR,
								activeInstances);
						} else {
							new MyLog(this,
								"Advancing to state ACTIVE_STARTED for event "
									+ eventName + " of class " + className);
							moveToStarted("for end of ", endTime, activeInstances);
						}
						continue;
					case SQLtable.ACTIVE_STARTED:
						ringerAction = PrefsManager.getRingerAction(
							this, classNum);
						new MyLog(this, "wantedMode is "
							+ PrefsManager.getEnglishStateName(wantedMode)
							+ ", ringerAction is "
							+ PrefsManager.getEnglishStateName(ringerAction)
							+ " during event " + eventName
							+ " of class " + className);
						if (ringerAction > wantedMode) {
							// Set quieter mode wanted by this class
							wantedMode = ringerAction;
							if (notifySoundFile.isEmpty()) {
								// Suppress any silent notification
								// for an event which has ended
								notifyType = null;
							}
						}
						try {
							long time =
								activeInstances.getUnsignedLong(
									"ACTIVE_NEXT_ALARM");
							if ((m_timeNow < time) && (time < m_nextAlarm.time)) {
								m_nextAlarm.reason = "for end of ";
								m_nextAlarm.time = time;
								m_nextAlarm.eventName = eventName;
								m_nextAlarm.className = className;
							}
						} catch (NumberFormatException ignore) {
							activeInstances.deleteBad();
						}
						continue;
					case SQLtable.ACTIVE_END_WAITING:
						boolean cont = false;
						int aftersteps =
							PrefsManager.getAfterSteps(this, classNum);
						if (haveStepCounter && (aftersteps > 0)) {
							int lastCounterSteps =
								PrefsManager.getStepCount(this);
							int targetSteps = PrefsManager.getTargetSteps(
								this, classNum);
							if (targetSteps == 0) {
								if (lastCounterSteps == PrefsManager.STEP_COUNTER_IDLE)
								{
									// need to start up the sensor
									if (StartStepCounter(classNum)) {
										anyStepCountActive = true;
										cont = true;
									}
								} else {
									// sensor is now running, set target count
									new MyLog(this,
										"Setting target steps for " + eventName
											+ " of " + className + " to "
											+ lastCounterSteps + " + " + aftersteps);
									PrefsManager.setTargetSteps(this, classNum,
										lastCounterSteps + aftersteps);
									anyStepCountActive = true;
									cont = true;
								}
							} else if (lastCounterSteps < targetSteps) {
								// not reached target yet
								anyStepCountActive = true;
								cont = true;
							}
							// otherwise we reached the target
						}
						if (havelocation && (PrefsManager.getAfterMetres(
							this, classNum) > 0)) {
							// keep it active while waiting for location
							startLocationWait(classNum, intent);
							anyLocationActive = true;
							cont = true;
						}
						double latitude
							= PrefsManager.getLatitude(this, classNum);
						if ((latitude != 360.0)
							&& checkLocationWait(classNum, latitude, intent)) {
							anyLocationActive = true;
							cont = true;
						}
						if (checkOrientationWait(classNum, false)) {
							// waiting for device to be in the correct orientation
							moveToStartWaiting("orientation check for ",
								m_nextAccelTime, activeInstances);
							cont = true;
						} else if (checkConnectionWait(classNum, false)) {
							moveToStartWaiting("USB connection check for ",
								m_timeNow + CalendarProvider.FIVE_MINUTES,
								activeInstances);
							cont = true;
						}
						if (cont) {
							ringerAction = PrefsManager.getRingerAction(
								this, classNum);
							new MyLog(this, "wantedMode is "
								+ PrefsManager.getEnglishStateName(wantedMode)
								+ ", ringerAction is "
								+ PrefsManager.getEnglishStateName(ringerAction)
								+ " waiting for end of event " + eventName
								+ " of class " + className);
							if (ringerAction > wantedMode) {
								// Set quieter mode wanted by this class
								wantedMode = ringerAction;
								if (notifySoundFile.isEmpty()) {
									// Suppress any silent notification
									// for an event which has ended
									notifyType = null;
								}
							}
							continue;
						}
						// We can advance to ENDING now
						new MyLog(this,
							"Advancing to state ACTIVE_ENDING for event "
								+ eventName + " of class " + className);
					case SQLtable.ACTIVE_ENDING:
						PrefsManager.setTargetSteps(this, classNum, 0);
						soundFile = PrefsManager.getSoundFileEnd(
							this, classNum);
						if (soundFile.isEmpty()) {
							sound = false;
						}
						else
                        {
                            sound = PrefsManager.getPlaysoundEnd(this, classNum);
                        }
						new MyLog(this, "wantedMode is "
							+ PrefsManager.getEnglishStateName(wantedMode)
							+ ", currentMode is "
							+ PrefsManager.getEnglishStateName(currentMode)
							+ " ending event " + eventName + " of class " + className);
						ringChange =
							PrefsManager.getRestoreRinger(this, classNum)
							&& (wantedMode < currentMode);
						if (   (ringChange || (sound && notifySoundFile.isEmpty()))
							&& PrefsManager.getNotifyEnd(this, classNum)) {
							if (eventName.equals("deleted event")) {
								notifyType = "";
							} else {
								notifyType = "end of ";
							}
							notifyEvent = eventName;
							notifyClassName = className;
							if (sound) {
								notifySoundFile = soundFile;
							}
						}
						// We can advance to END_SENDING or NOT_ACTIVE now
						new MyLog(this,
							"Advancing to state ACTIVE_END_SENDING for event "
								+ eventName + " of class " + className);
					case SQLtable.ACTIVE_END_SENDING:
						endTime = tryMessage(classNum,
							PrefsManager.SEND_MESSAGE_AT_END, activeInstances);
						if (endTime == 0) {
							moveToEndSending("send wait at end of ",
								m_timeNow +
								CalendarProvider.ONE_HOUR, activeInstances);
						} else {
							new MyLog(this,
								"Finished with event "
								+ eventName + " of class " + className
								+ ", deleting it");
							// Finished with this instance
							activeInstances.delete();
						}
						continue;
					default:
						String small = getString(R.string.badactivestate);
 						new MyLog(this, small,
                            small + this.getString(R.string.forrow)
                            + activeInstances.rowToString()
                            + this.getString(R.string.deletingit));
                            activeInstances.delete();
				}
			} catch (NoColumnException ignored) { break; }
		}
		activeInstances.close();
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
				long slst = sl.startTime + CalendarProvider.ONE_MINUTE;
				if (   (slst < m_nextAlarm.time)
					&& (slst > m_timeNow))
				{
					m_nextAlarm.reason = getString(R.string.forstart);
					m_nextAlarm.time = slst;
					m_nextAlarm.eventName = sl.eventName;
					m_nextAlarm.className = "";
				}
			}
		}
		String small;
		if (wantedMode == PrefsManager.RINGER_MODE_NONE)
		{
			wantedMode = PrefsManager.RINGER_MODE_NORMAL;
		}
		setCurrentRinger(audio, currentApiVersion,  wantedMode, currentMode);
		if (notifyType != null)
		{
			if (wantedMode != currentMode) {
				small = getString(R.string.modeset) +
					PrefsManager.getRingerStateName(this, wantedMode);
			}
			else {
				small = getString(R.string.modeunchanged);
			}
			StringBuilder big = new StringBuilder(small);
			big.append(getString(R.string.bforb));
			big.append(notifyType).append(notifyEvent);
			if (notifyClassName != null) {
				big.append(getString(R.string.ofclass)).append(notifyClassName);
			}
			new Notifier(this, small, big.toString(), notifySoundFile);
			new MyLog(this, big.toString());
			if (!notifySoundFile.isEmpty()) {
				big.replace(0, big.length() - 1,
					"Playing sound for ").append(notifyType).append(notifyEvent);
				if (notifyClassName != null) {
					big.append(getString(R.string.ofclass)).append(notifyClassName);
				}
				new MyLog(this, big.toString());
			}
		}
		else if (resetting)
		{
			new MyLog(this,
				getString(R.string.settingaudio)
					+ PrefsManager.getEnglishStateName(wantedMode)
					+ getString(R.string.afterreset));
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
			m_nextAlarm.reason = getString(R.string.fortimezone);
			m_nextAlarm.eventName = "";
			m_nextAlarm.className = "";
		}
		if (m_nextAlarm.time == Long.MAX_VALUE)
		{
			alarmManager.cancel(pIntent);
			new MyLog(this, getString(R.string.alarmcancelled));
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
				String s = getString(R.string.delayedmessage)
					.concat(df.format(m_nextAlarm.time))
					.concat(m_nextAlarm.reason).concat(m_nextAlarm.eventName);
				if (!m_nextAlarm.eventName.isEmpty()) {
					s = s.concat(getString(R.string.ofclass))
						 .concat(m_nextAlarm.className);
				}
				new MyLog(this, s);
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
				String s = "Alarm time set to "
					.concat(df.format(m_nextAlarm.time))
					.concat(m_nextAlarm.reason).concat(m_nextAlarm.eventName);
				if (!m_nextAlarm.eventName.isEmpty()) {
					s = s.concat(" of class ").concat(m_nextAlarm.className);
				}
				new MyLog(this, s);
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
				new MyLog(this, getString(R.string.stepcounteroff));
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
		if (intent.getAction().equals(MUTESERVICE_RESET)) {
			resetting = true;
		}
		else if (intent.getAction().equals(MUTESERVICE_SMS_RESULT)) {
			int result = intent.getIntExtra("ResultCode", Activity.RESULT_OK);
			if (result != Activity.RESULT_OK) {
				StringBuilder sb = new StringBuilder(getString(R.string.smsfailed));
				sb.append(result).append(")");
				String small = sb.toString();
				sb.append(getString(R.string.forpart))
				  .append(intent.getIntExtra("PartNumber", 0))
				  .append(getString(R.string.msgforevent))
				  .append(intent.getStringExtra("EventName"));
				new MyLog(this,
					small, sb.toString(), null);
			}
			WakefulBroadcastReceiver.completeWakefulIntent(intent);
			return;
		}
		updateState(intent);
		WakefulBroadcastReceiver.completeWakefulIntent(intent);
	}

	public static class StartServiceReceiver
			extends WakefulBroadcastReceiver {

		@SuppressLint("UnsafeProtectedBroadcastReceiver")
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
