/*
 * Copyright (c) 2019, Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.service;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import android.os.Vibrator;
import android.provider.Settings;
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

import uk.co.yahoo.p1rpp.calendartrigger.BuildConfig;
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
	private AudioManager m_audio;
	private SQLtable m_lastModes;
	private int m_ringerMode;
	private int m_ringerVibrate;
	private int m_notifyVibrate;
	private int m_ringerVolume;
	private int m_notifyVolume;
	private int m_systemVolume;
	private int m_alarmVolume;
	private int m_ringerMute;
	private int m_notifyMute;
	private int m_systemMute;
	private int m_alarmMute;
	private NotificationManager m_notifManager;
	private int m_notifyFilter;
	private int m_alsoVibrate;

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

	private int getRingerMode(SQLtable table) {
		switch (table.getStringOK("RINGER_MODE"))
		{
			case "SILENT": return AudioManager.RINGER_MODE_SILENT;
			case "VIBRATE": return AudioManager.RINGER_MODE_VIBRATE;
			case "NORMAL": return AudioManager.RINGER_MODE_NORMAL;
			default: return -1;
		}
	}

	private int getVibrate(SQLtable table, String type) {
		switch (table.getStringOK(type))
		{
			case "OFF":
				//noinspection deprecation
				return AudioManager.VIBRATE_SETTING_OFF;
			case "ON":
				//noinspection deprecation
				return AudioManager.VIBRATE_SETTING_ON;
			case "SILENT":
				//noinspection deprecation
				return AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			default: return -1;
		}
	}

	private int getAlsoVibrate(SQLtable table) {
		switch (table.getStringOK("VIBRATE_ALSO"))
		{
			case "OFF": return 0;
			case "ON": return 1;
			default: return -1;
		}
	}

    @TargetApi(Build.VERSION_CODES.M)
	private int getFilterValue(SQLtable table) {
	    switch (table.getStringOK("DO_NOT_DISTURB_MODE"))
        {
            case "ALL": return NotificationManager.INTERRUPTION_FILTER_ALL;
            case "PRIORITY": return NotificationManager.INTERRUPTION_FILTER_PRIORITY;
            case "ALARMS": return NotificationManager.INTERRUPTION_FILTER_ALARMS;
            case "NONE": return NotificationManager.INTERRUPTION_FILTER_NONE;
			default: return -1;
        }
    }

	@TargetApi(Build.VERSION_CODES.M)
	private String getFilterName(int value) {
		switch (value)
		{
			default:
			case NotificationManager.INTERRUPTION_FILTER_ALL:
				return "ALL";
			case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
				return "PRIORITY";
			case NotificationManager.INTERRUPTION_FILTER_ALARMS:
				return "ALARMS";
			case NotificationManager.INTERRUPTION_FILTER_NONE:
				return "NONE";
		}
	}

	// Build 21:
	// Vibrate setting defaults to ONLY_SILENT
	// Ringer Mode defaults to NORMAL
	// 		gets set to VIBRATE if volume set to zero in settings
	// Changing "Also vibrate for calls" in Settings
	// doesn't change Ringer Mode or Vibrate setting.
	// Ringer volume changes are correctly reported.
	// DND modes are supported, can be set from the settings screen and
	// show icons on the status bar, but cannot be queried or modified from a program.

    // Set our initial guess at the modes we want to what the user last set.
	// However if the user has changed any mode since we last set it,
	// we update our record of what the user wants, and pretend that we set it
	// so that we don't update again.
	// The first time this is called after installation or upgrade,
	// the error recovery logic in SQLtable will create
	// the RINGERDNDMODES table
	// and the special rows last_we_set and last_user_set.
	@TargetApi(Build.VERSION_CODES.M)
	private void getUserRinger() {
		int buildVersion = android.os.Build.VERSION.SDK_INT;
        SQLtable userModes = new SQLtable(m_lastModes, "RINGERDNDMODES",
            "RINGER_CLASS_NAME", "last_user_set");
		ContentValues cv = new ContentValues();
		if (BuildConfig.RINGERMODETEST) {
			new MyLog(this, "API version is " + buildVersion);
			// debug hack - I just want to see if I need permission to do this
			// <uses-permission android:name="android.permission.VIBRATE"/>
			if (((Vibrator)getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator())
			{
				new MyLog(this, "Device has a vibrator");
			}
			else
			{
				new MyLog(this, "Device does not have a vibrator");
			}
		}
		int last = getRingerMode(m_lastModes);
		switch (m_audio.getRingerMode()) {
			case AudioManager.RINGER_MODE_NORMAL:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Mode is RINGER_MODE_NORMAL");
				}
				if (last != AudioManager.RINGER_MODE_NORMAL)
				{
					// ringer mode wasn't normal, but user set normal
					cv.put("RINGER_MODE", "NORMAL");
					m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				}
				else
				{
					m_ringerMode = getRingerMode(userModes);
				}
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Mode is RINGER_MODE_VIBRATE");
				}
				if (last != AudioManager.RINGER_MODE_VIBRATE)
				{
					// ringer mode wasn't vibrate, but user set vibrate
					cv.put("RINGER_MODE", "VIBRATE");
					m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				}
				else
				{
					m_ringerMode = getRingerMode(userModes);
				}
				break;
			case AudioManager.RINGER_MODE_SILENT:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Mode is RINGER_MODE_SILENT");
				}
				if (last != AudioManager.RINGER_MODE_SILENT)
				{
					// ringer mode wasn't vibrate, but user set vibrate
					cv.put("RINGER_MODE", "VIBRATE");
					m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				}
				else
				{
					m_ringerMode = getRingerMode(userModes);
				}
				break;
		}
		last = getVibrate(m_lastModes,"RINGER_VIBRATE");
		//noinspection deprecation
		switch (m_audio.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER)) {
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_OFF:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Ringer vibrate setting is OFF");
				}
				//noinspection deprecation
				if (last != AudioManager.VIBRATE_SETTING_OFF)
				{
					// vibrate wasn't off, but user turned it off
					cv.put("RINGER_VIBRATE", "OFF");
					//noinspection deprecation
					m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				}
				else
				{
					m_ringerVibrate = getVibrate(userModes,"RINGER_VIBRATE");
				}
				break;
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_ON:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Ringer vibrate setting is ON");
				}
				//noinspection deprecation
				if (last != AudioManager.VIBRATE_SETTING_ON)
				{
					// vibrate wasn't on, but user turned it on
					cv.put("RINGER_VIBRATE", "ON");
					//noinspection deprecation
					m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				}
				else
				{
					m_ringerVibrate = getVibrate(userModes,"RINGER_VIBRATE");
				}
				break;
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Ringer vibrate setting is SILENT");
				}
				//noinspection deprecation
				if (last != AudioManager.VIBRATE_SETTING_ONLY_SILENT)
				{
					// vibrate wasn't only silent, but user set it
					cv.put("RINGER_VIBRATE", "SILENT");
					//noinspection deprecation
					m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
				}
				else
				{
					m_ringerVibrate = getVibrate(userModes,"RINGER_VIBRATE");
				}
				break;
		}
		last = getVibrate(m_lastModes,"NOTIFY_VIBRATE");
		m_notifyVibrate = -1;
		//noinspection deprecation
		switch (m_audio.getVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION)) {
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_OFF:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Notification vibrate setting is OFF");
				}
				//noinspection deprecation
				if (last != AudioManager.VIBRATE_SETTING_OFF)
				{
					// vibrate wasn't off, but user turned it off
					cv.put("NOTIFY_VIBRATE", "OFF");
					//noinspection deprecation
					m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				}
				else
				{
					m_notifyVibrate = getVibrate(userModes,"NOTIFY_VIBRATE");
				}
				break;
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_ON:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Notification vibrate setting is ON");
				}
				//noinspection deprecation
				if (last != AudioManager.VIBRATE_SETTING_ON)
				{
					// vibrate wasn't on, but user turned it on
					cv.put("NOTIFY_VIBRATE", "ON");
					//noinspection deprecation
					m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				}
				else
				{
					m_notifyVibrate = getVibrate(userModes,"NOTIFY_VIBRATE");
				}
				break;
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Notification vibrate setting is SILENT");
				}
				//noinspection deprecation
				if (last != AudioManager.VIBRATE_SETTING_ONLY_SILENT)
				{
					// vibrate wasn't only silent, but user set it
					cv.put("NOTIFY_VIBRATE", "SILENT");
					//noinspection deprecation
					m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
				}
				else
				{
					m_notifyVibrate = getVibrate(userModes,"NOTIFY_VIBRATE");
				}
				break;
		}
		if (BuildConfig.RINGERMODETEST) {
			//noinspection deprecation
			new MyLog(this, "shouldVibrate(ringer) is "
				+ (m_audio.shouldVibrate(AudioManager.VIBRATE_TYPE_RINGER)));
			//noinspection deprecation
			new MyLog(this, "shouldVibrate(notifications) is "
				+ (m_audio.shouldVibrate(AudioManager.VIBRATE_TYPE_NOTIFICATION)));
		}
		int ringvol = m_audio.getStreamVolume(AudioManager.STREAM_RING);
		int notifvol = m_audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
		int systemvol = m_audio.getStreamVolume(AudioManager.STREAM_SYSTEM);
		int alarmvol = m_audio.getStreamVolume(AudioManager.STREAM_ALARM);
		if (BuildConfig.RINGERMODETEST) {
			new MyLog(this, "Ringer volume is " + ringvol);
			new MyLog(this, "Notification volume is " + notifvol);
			new MyLog(this, "System volume is " + systemvol);
			new MyLog(this, "Alarm volume is " + alarmvol);
		}

		last  = m_lastModes.getIntegerOK("RINGER_VOLUME");
		if (ringvol != last) {
			cv.put("RINGER_VOLUME", ringvol);
            m_ringerVolume = ringvol;
		}
		else
        {
            m_ringerVolume = userModes.getIntegerOK("RINGER_VOLUME");
        }
		// On standard devices, the notification and system sound volumes are set
		// by setting the ringer volume, but we handle the case where they aren't.
		int notifAffected = 1 << AudioManager.STREAM_NOTIFICATION;
		int systemAffected = 1 << AudioManager.STREAM_SYSTEM;
		int affected = Settings.System.getInt(getContentResolver(),
			Settings.System.MODE_RINGER_STREAMS_AFFECTED,
			notifAffected | systemAffected);
		if ((affected & notifAffected) == 0)
		{
			last = m_lastModes.getIntegerOK("NOTIFY_VOLUME");
			if (notifvol != last) {
				cv.put("NOTIFY_VOLUME", notifvol);
				m_notifyVolume = notifvol;
			}
			else
			{
				m_notifyVolume = userModes.getIntegerOK("NOTIFY_VOLUME");
			}
		}
		if ((affected & systemAffected) == 0)
		{
			last = m_lastModes.getIntegerOK("SYSTEM_VOLUME");
			if (systemvol != last) {
				cv.put("SYSTEM_VOLUME", systemvol);
				m_systemVolume = systemvol;
			}
			else
			{
				m_systemVolume = userModes.getIntegerOK("SYSTEM_VOLUME");
			}
		}
		last = m_lastModes.getIntegerOK("ALARM_VOLUME");
		if (alarmvol != last) {
			cv.put("ALARM_VOLUME", alarmvol);
			m_alarmVolume = alarmvol;
		}
		else
		{
			m_alarmVolume = userModes.getIntegerOK("ALARM_VOLUME");
		}
		boolean ringmuted = m_audio.isStreamMute (AudioManager.STREAM_RING);
		boolean notifmuted = m_audio.isStreamMute (AudioManager.STREAM_NOTIFICATION);
		boolean systemmuted = m_audio.isStreamMute (AudioManager.STREAM_SYSTEM);
		boolean alarmmuted = m_audio.isStreamMute (AudioManager.STREAM_ALARM);
		if (BuildConfig.RINGERMODETEST) {
			new MyLog(this,
				"Ringer is " + (ringmuted ? "muted" : "not muted"));
			new MyLog(this,
				"Notification sounds are " + (notifmuted ? "muted" : "not muted"));
			new MyLog(this,
				"System sounds are " + (systemmuted ? "muted" : "not muted"));
			new MyLog(this,
				"Alarms are " + (alarmmuted ? "muted" : "not muted"));
		}
		last = m_lastModes.getIntegerOK("RINGER_MUTE");
		if (ringmuted) {
			if (last != 1) {
				cv.put("RINGER_MUTE", 1);
				m_ringerMute = 1;
			}
		}
		else if (last != 0)
		{
			cv.put("RINGER_MUTE", 0);
			m_ringerMute = 0;
		}
		last = m_lastModes.getIntegerOK("NOTIFY_MUTE");
		if (notifmuted) {
			if (last != 1) {
				cv.put("NOTIFY_MUTE", 1);
				m_notifyMute = 1;
			}
		}
		else if (last != 0)
		{
			cv.put("NOTIFY_MUTE", 0);
			m_notifyMute = 0;
		}
		last = m_lastModes.getIntegerOK("SYSTEM_MUTE");
		if (systemmuted) {
			if (last != 1) {
				cv.put("SYSTEM_MUTE", 1);
				m_systemMute = 1;
			}
		}
		else if (last != 0)
		{
			cv.put("SYSTEM_MUTE", 0);
			m_systemMute = 0;
		}
		last = m_lastModes.getIntegerOK("ALARM_MUTE");
		if (alarmmuted) {
			if (last != 1) {
				cv.put("ALARM_MUTE", 1);
				m_alarmMute = 1;
			}
		}
		else if (last != 0)
		{
			cv.put("ALARM_MUTE", 0);
			m_alarmMute = 0;
		}
		m_notifyFilter = -1;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
			int value = Settings.System.getInt(
				getContentResolver(), Settings.System.VIBRATE_WHEN_RINGING, 0);
			if (BuildConfig.RINGERMODETEST) {
				new MyLog(this, "ALSO_VIBRATE is "
					+ ((value == 0) ? "off" : "on"));
			}
			last = getAlsoVibrate(m_lastModes);
			if (last != value)
			{
				cv.put("VIBRATE_ALSO", (last == 0) ? "OFF" : "ON");
				m_alsoVibrate = value;
			}
			else
			{
				m_alsoVibrate = getAlsoVibrate(userModes);
			}
			if ((m_notifManager != null)
				&& m_notifManager.isNotificationPolicyAccessGranted()) {
				value = m_notifManager.getCurrentInterruptionFilter();
				String got = m_lastModes.getStringOK("DO_NOT_DISTURB_MODE");
				m_notifyFilter = getFilterValue(userModes);
				switch (value) {
					default:
					case NotificationManager.INTERRUPTION_FILTER_ALL:
						if (BuildConfig.RINGERMODETEST) {
							new MyLog(this, "Do Not Disturb mode is ALL");
						}
						if (got.compareTo("ALL") != 0) {
							cv.put("DO_NOT_DISTURB_MODE", "ALL");
							m_notifyFilter = value;
						}
						break;
					case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
						if (BuildConfig.RINGERMODETEST) {
							new MyLog(this, "Do Not Disturb mode is PRIORITY");
						}
						if (got.compareTo("PRIORITY") != 0) {
							cv.put("DO_NOT_DISTURB_MODE", "PRIORITY");
						}
						break;
					case NotificationManager.INTERRUPTION_FILTER_ALARMS:
						if (BuildConfig.RINGERMODETEST) {
							new MyLog(this, "Do Not Disturb mode is ALARMS");
						}
						if (got.compareTo("ALARMS") != 0) {
							cv.put("DO_NOT_DISTURB_MODE", "ALARMS");
						}
						break;
					case NotificationManager.INTERRUPTION_FILTER_NONE:
						if (BuildConfig.RINGERMODETEST) {
							new MyLog(this, "Do Not Disturb mode is NONE");
						}
						if (got.compareTo("NONE") != 0) {
							cv.put("DO_NOT_DISTURB_MODE", "NONE");
						}
						break;
					case NotificationManager.INTERRUPTION_FILTER_UNKNOWN:
						if (BuildConfig.RINGERMODETEST) {
							new MyLog(this, "Do Not Disturb mode is UNKNOWN");
						}
				}
			}
		}
		if (cv.size() > 0) {
			m_lastModes.update("last_we_set", cv);
			m_lastModes.update("last_user_set", cv);
			if (BuildConfig.DEBUG) {
				userModes.close();
				userModes = new SQLtable(m_lastModes, "RINGERDNDMODES",
					"RINGER_CLASS_NAME", "last_user_set");
				new MyLog(this, "Updating last_user_set to " +
					userModes.rowToString());
			}
		}
        userModes.close();
	}

	// Updates for an active class, returns true if changed anything
	@TargetApi(Build.VERSION_CODES.M)
	private boolean ringerForClass(int classNum, String className, boolean first) {
		boolean result = false;
		ContentValues cv = new ContentValues();
		SQLtable classModes = new SQLtable(m_lastModes, "RINGERDNDMODES",
				"RINGER_CLASS_NAME", className);
		SQLtable userModes = new SQLtable(m_lastModes, "RINGERDNDMODES",
			"RINGER_CLASS_NAME", "last_user_set");
		String vib = classModes.getStringOK("RINGER_VIBRATE");
		if (vib.equals("OFF")) {
			//noinspection deprecation
			if (m_ringerVibrate != AudioManager.VIBRATE_SETTING_OFF) {
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				cv.put("RINGER_VIBRATE", "OFF");
				result = true;
			}
		}
		else if (vib.equals("ON")) {
			if (m_ringerVibrate == -1) {
				// ON is stronger than unchanged because we explicitly asked for it
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				cv.put("RINGER_VIBRATE", "OFF");
				result = true;
			}
		}
		int vol = classModes.getIntegerOK("RINGER_VOLUME");
		if (vol < m_ringerVolume) {
			m_ringerVolume = vol;
			result = true;
		}
		if (vol < userModes.getIntegerOK("RINGER_VOLUME")) {
			cv.put("RINGER_VOLUME", m_ringerVolume);
		}
		vol = classModes.getIntegerOK("ALARM_VOLUME");
		if (vol < m_alarmVolume) {
			m_alarmVolume = vol;
			result = true;
		}
		if (vol < userModes.getIntegerOK("ALARM_VOLUME")) {
			cv.put("ALARM_VOLUME", m_alarmVolume);
		}
		if (   (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
			&& (m_notifManager != null)
			&& m_notifManager.isNotificationPolicyAccessGranted())
		{
			String s = classModes.getStringOK("DO_NOT_DISTURB_MODE");
			String userDND = userModes.getStringOK("DO_NOT_DISTURB_MODE");
			switch (s)
			{
				case "ALL": break;
				case "PRIORITY":
					if (m_notifyFilter == NotificationManager.INTERRUPTION_FILTER_ALL)
					{
						m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
						result = true;
					}
					if (userDND.equals("ALL"))
					{
						cv.put("DO_NOT_DISTURB_MODE", "ALL");
					}
					break;
				case "ALARMS":
					if (   (m_notifyFilter == NotificationManager.
								INTERRUPTION_FILTER_ALL)
						|| (m_notifyFilter == NotificationManager.
								INTERRUPTION_FILTER_PRIORITY))
					{
						m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
						result = true;
					}
					if (userDND.equals("ALL") || userDND.equals("PRIORITY"))
					{
						cv.put("DO_NOT_DISTURB_MODE", "ALARMS");
					}
					break;
				case "NONE":
					if (m_notifyFilter != NotificationManager.INTERRUPTION_FILTER_NONE)
					{
						m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
						result = true;
					}
					if (!userDND.equals("NONE"))
					{
						cv.put("DO_NOT_DISTURB_MODE", "NONE");
					}
					break;
				default:
					new MyLog(this,
						"Unknown do-not-disturb mode " + s +
							", replacing it with ALL");
					classModes.update(
						"DO_NOT_DISTURB_MODE", "ALL");
			}
		}
		classModes.close();
		userModes.close();
		/* This is a fudge to make not restoring the ringer at
		 * the end of the event work. We quieten the user ringer
		 * mode to the mode that this class wants.
		 */
		if (   first
			&& (cv.size() > 0)
			&& !PrefsManager.getRestoreRinger(this, classNum)) {
			m_lastModes.update("last_user_set", cv);
		}
		return result;
	}

	// Setting VIBRATE_TYPE_RINGER doesn't work in API 24
	// Setting VIBRATE_TYPE_NOTIFICATION doesn't work in API 24
	// Setting Ringer volume to 0 sets DND in API 24
	// Setting ringer volume to a nonzero value clears in API 24
	// Setting alarm volume to any value works in API 24

	// Set the ringer modes. Returns true if anything changed.
	@TargetApi(android.os.Build.VERSION_CODES.N)
	boolean setCurrentRinger() {
		ContentValues cv = new ContentValues();
		int current = m_audio.getRingerMode();
		if (BuildConfig.RINGERMODETEST) {
			switch (current) {
				case AudioManager.RINGER_MODE_NORMAL:
					new MyLog(this,
						"Current ringer mode is RINGER_MODE_NORMAL");
					break;
				case AudioManager.RINGER_MODE_VIBRATE:
					new MyLog(this
						, "Current ringer mode is RINGER_MODE_VIBRATE");
					break;
				case AudioManager.RINGER_MODE_SILENT:
					new MyLog(this,
						"Current ringer mode is RINGER_MODE_SILENT");
					break;
			}
		}
		switch (m_ringerMode) {
			case AudioManager.RINGER_MODE_NORMAL:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this,
						"Requested ringer mode is RINGER_MODE_NORMAL");
				}
				if (current != AudioManager.RINGER_MODE_NORMAL)
				{
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting ringer mode to RINGER_MODE_NORMAL");
					}
					cv.put("RINGER_MODE", "NORMAL");
					m_audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
				}
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this,
						"Requested ringer mode is RINGER_MODE_VIBRATE");
				}
				if (current != AudioManager.RINGER_MODE_VIBRATE)
				{
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting ringer mode to RINGER_MODE_VIBRATE");
					}
					cv.put("RINGER_MODE", "VIBRATE");
					m_audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
				}
				break;
			case AudioManager.RINGER_MODE_SILENT:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this,
						"Requested ringer mode is RINGER_MODE_SILENT");
				}
				if (current != AudioManager.RINGER_MODE_SILENT)
				{
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting ringer mode to RINGER_MODE_SILENT");
					}
					cv.put("RINGER_MODE", "SILENT");
					m_audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
				}
				break;
			default:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this,
						"Requested ringer mode is unchanged");
				}
				break;
		}
		//noinspection deprecation
		current = m_audio.getVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER);
		if (BuildConfig.RINGERMODETEST) {
			switch (current) {
				//noinspection deprecation
				case AudioManager.VIBRATE_SETTING_OFF:
					new MyLog(this, "Current ringer vibrate setting is OFF");
					break;
				//noinspection deprecation
				case AudioManager.VIBRATE_SETTING_ON:
					new MyLog(this, "Current ringer vibrate setting is ON");
					break;
				//noinspection deprecation
				case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
					new MyLog(this,
						"Current ringer vibrate setting is SILENT");
					break;
			}
		}
		switch (m_ringerVibrate) {
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_OFF:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Requested ringer vibrate setting is OFF");
				}
				//noinspection deprecation
				if (current != AudioManager.VIBRATE_SETTING_OFF)
				{
					cv.put("RINGER_VIBRATE", "OFF");
					//noinspection deprecation
					m_audio.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
						AudioManager.VIBRATE_SETTING_OFF);
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting ringer vibrate setting to OFF");
					}
				}
				break;
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_ON:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Requested ringer vibrate setting is ON");
				}
				//noinspection deprecation
				if (current != AudioManager.VIBRATE_SETTING_ON)
				{
					cv.put("RINGER_VIBRATE", "ON");
					//noinspection deprecation
					m_audio.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
						AudioManager.VIBRATE_SETTING_ON);
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting ringer vibrate setting to ON");
					}
				}
				break;
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this,
						"Requested ringer vibrate setting is SILENT");
				}
				//noinspection deprecation
				if (current != AudioManager.VIBRATE_SETTING_ONLY_SILENT)
				{
					cv.put("RINGER_VIBRATE", "SILENT");
					//noinspection deprecation
					m_audio.setVibrateSetting(AudioManager.VIBRATE_TYPE_RINGER,
						AudioManager.VIBRATE_SETTING_ONLY_SILENT);
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting ringer vibrate setting to SILENT");
					}
				}
				break;
			default:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this,
						"No ringer vibrate setting is requested");
				}
				break;
		}
		//noinspection deprecation
		current = m_audio.getVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION);
		if (BuildConfig.RINGERMODETEST) {
			switch (current) {
				//noinspection deprecation
				case AudioManager.VIBRATE_SETTING_OFF:
					new MyLog(this, "Current notify vibrate setting is OFF");
					break;
				//noinspection deprecation
				case AudioManager.VIBRATE_SETTING_ON:
					new MyLog(this, "Current notify vibrate setting is ON");
					break;
				//noinspection deprecation
				case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
					new MyLog(this,
						"Current notify vibrate setting is SILENT");
					break;
			}
		}
		switch (m_notifyVibrate) {
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_OFF:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Requested notify vibrate setting is OFF");
				}
				//noinspection deprecation
				if (current != AudioManager.VIBRATE_SETTING_OFF)
				{
					cv.put("NOTIFY_VIBRATE", "OFF");
					//noinspection deprecation
					m_audio.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
						AudioManager.VIBRATE_SETTING_OFF);
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting notify vibrate setting to OFF");
					}
				}
				break;
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_ON:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this, "Requested notify vibrate setting is ON");
				}
				//noinspection deprecation
				if (current != AudioManager.VIBRATE_SETTING_ON)
				{
					cv.put("NOTIFY_VIBRATE", "ON");
					//noinspection deprecation
					m_audio.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
						AudioManager.VIBRATE_SETTING_ON);
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting notify vibrate setting to ON");
					}
				}
				break;
			//noinspection deprecation
			case AudioManager.VIBRATE_SETTING_ONLY_SILENT:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this,
						"Requested notify vibrate setting is SILENT");
				}
				//noinspection deprecation
				if (current != AudioManager.VIBRATE_SETTING_ONLY_SILENT)
				{
					cv.put("NOTIFY_VIBRATE", "SILENT");
					//noinspection deprecation
					m_audio.setVibrateSetting(AudioManager.VIBRATE_TYPE_NOTIFICATION,
						AudioManager.VIBRATE_SETTING_ONLY_SILENT);
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting notify vibrate setting to SILENT");
					}
				}
				break;
			default:
				if (BuildConfig.RINGERMODETEST) {
					new MyLog(this,
						"No notify vibrate setting is requested");
				}
				break;
		}
		int ringvol = m_audio.getStreamVolume(AudioManager.STREAM_RING);
		int notifvol = m_audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
		int systemvol = m_audio.getStreamVolume(AudioManager.STREAM_SYSTEM);
		int alarmvol = m_audio.getStreamVolume(AudioManager.STREAM_ALARM);
		if (BuildConfig.RINGERMODETEST) {
			new MyLog(this, "Current ring volume is " + ringvol);
			new MyLog(this, "Requested ring volume is " + m_ringerVolume);
			new MyLog(this, "Current notify volume is " + notifvol);
			new MyLog(this, "Requested notify volume is " + m_notifyVolume);
			new MyLog(this, "Current system volume is " + systemvol);
			new MyLog(this, "Requested system volume is " + m_systemVolume);
			new MyLog(this, "Current alarm volume is " + alarmvol);
			new MyLog(this, "Requested alarm volume is " + m_alarmVolume);
		}
		if ((m_ringerVolume != ringvol) && (m_ringerVolume != 1000)){
			cv.put("RINGER_VOLUME", m_ringerVolume);
			m_audio.setStreamVolume(AudioManager.STREAM_RING, m_ringerVolume, 0);
			if (BuildConfig.RINGERMODETEST) {
				new MyLog(this, "Setting ring volume to " + m_ringerVolume);
			}
		}
		int notifAffected = 1 << AudioManager.STREAM_NOTIFICATION;
		int systemAffected = 1 << AudioManager.STREAM_SYSTEM;
		int affected = Settings.System.getInt(getContentResolver(),
			Settings.System.MODE_RINGER_STREAMS_AFFECTED,
			notifAffected | systemAffected);
		if (   ((affected & notifAffected) == 0)
			&& (m_notifyVolume != notifvol)
			&& (m_notifyVolume != 1000))
		{
			cv.put("NOTIFY_VOLUME", m_notifyVolume);
			m_audio.setStreamVolume(AudioManager.STREAM_NOTIFICATION,
				m_notifyVolume, 0);
			if (BuildConfig.RINGERMODETEST) {
				new MyLog(this,
					"Setting notify volume to " + m_notifyVolume);
			}
		}
		if (   ((affected & systemAffected) == 0)
			&& (m_systemVolume != systemvol)
			&& (m_systemVolume != 1000))
		{
			cv.put("SYSTEM_VOLUME", m_systemVolume);
			m_audio.setStreamVolume(AudioManager.STREAM_SYSTEM,
				m_systemVolume, 0);
			if (BuildConfig.RINGERMODETEST) {
				new MyLog(this,
					"Setting system volume to " + m_systemVolume);
			}
		}
		if ((m_alarmVolume != alarmvol)  && (m_alarmVolume != 1000)){
			cv.put("ALARM_VOLUME", m_alarmVolume);
			m_audio.setStreamVolume(AudioManager.STREAM_ALARM, m_alarmVolume, 0);
			if (BuildConfig.RINGERMODETEST) {
				new MyLog(this, "Setting alarm volume to " + m_alarmVolume);
			}
		}
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
		{
			boolean ringmute = m_audio.isStreamMute(AudioManager.STREAM_RING);
			boolean notifmute = m_audio.isStreamMute(AudioManager.STREAM_NOTIFICATION);
			boolean systemmute = m_audio.isStreamMute(AudioManager.STREAM_SYSTEM);
			boolean alarmmute = m_audio.isStreamMute(AudioManager.STREAM_ALARM);
			if (BuildConfig.RINGERMODETEST) {
				new MyLog(this, "Ringer is currently "
					+ (ringmute ? "muted" : "not muted"));
				new MyLog(this, "Notification sounds are currently "
					+ (notifmute ? "muted" : "not muted"));
				new MyLog(this, "System sounds are currently "
					+ (systemmute ? "muted" : "not muted"));
				new MyLog(this, "Alarms are currently "
					+ (alarmmute ? "muted" : "not muted"));
			}
			if (   (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
				|| (   (m_notifManager != null)
					&& m_notifManager.isNotificationPolicyAccessGranted())) {
				switch (m_ringerMute) {
					case 0:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"Ringer Muting Requested setting is OFF");
						}
						if (ringmute) {
							cv.put("RINGER_MUTE", "OFF");
							m_audio.adjustStreamVolume(AudioManager.STREAM_RING,
								AudioManager.ADJUST_UNMUTE, 0);
							if (BuildConfig.RINGERMODETEST)
							{
								new MyLog(this,
									"Setting Ringer Muting to OFF");
							}
						}
					case 1:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"Ringer Muting Requested setting is ON");
						}
						if (!ringmute) {
							cv.put("RINGER_MUTE", "OFF");
							m_audio.adjustStreamVolume(AudioManager.STREAM_RING,
								AudioManager.ADJUST_MUTE, 0);
							if (BuildConfig.RINGERMODETEST)
							{
								new MyLog(this,
									"Setting Ringer muting to ON");
							}
						}
					default:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"No change to Ringer Muting requested");
						}
				}
				switch (m_notifyMute) {
					case 0:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"Notification Sounds Muting Requested setting is OFF");
						}
						if (notifmute) {
							cv.put("NOTIFY_MUTE", "OFF");
							m_audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION,
								AudioManager.ADJUST_UNMUTE, 0);
							if (BuildConfig.RINGERMODETEST)
							{
								new MyLog(this,
									"Setting Notification Sounds Muting to OFF");
							}
						}
					case 1:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"Notification Sounds Muting Requested setting is ON");
						}
						if (!notifmute) {
							cv.put("NOTIFY_MUTE", "OFF");
							m_audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION,
								AudioManager.ADJUST_MUTE, 0);
							if (BuildConfig.RINGERMODETEST)
							{
								new MyLog(this,
									"Setting Notification Sounds muting to ON");
							}
						}
					default:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"No change to Notification Sounds Muting requested");
						}
				}
				switch (m_systemMute) {
					case 0:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"System Sounds Muting Requested setting is OFF");
						}
						if (systemmute) {
							cv.put("RINGER_MUTE", "OFF");
							m_audio.adjustStreamVolume(AudioManager.STREAM_SYSTEM,
								AudioManager.ADJUST_UNMUTE, 0);
							if (BuildConfig.RINGERMODETEST)
							{
								new MyLog(this,
									"Setting System Sounds Muting to OFF");
							}
						}
					case 1:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"System Sounds Muting Requested setting is ON");
						}
						if (!systemmute) {
							cv.put("RINGER_MUTE", "OFF");
							m_audio.adjustStreamVolume(AudioManager.STREAM_SYSTEM,
								AudioManager.ADJUST_MUTE, 0);
							if (BuildConfig.RINGERMODETEST)
							{
								new MyLog(this,
									"Setting System Sounds muting to ON");
							}
						}
					default:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"No change to System Sounds Muting requested");
						}
				}
				switch (m_alarmMute) {
					case 0:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"Alarm Muting Requested setting is OFF");
						}
						if (alarmmute) {
							cv.put("ALARM_MUTE", "OFF");
							m_audio.adjustStreamVolume(AudioManager.STREAM_ALARM,
								AudioManager.ADJUST_UNMUTE, 0);
							if (BuildConfig.RINGERMODETEST)
							{
								new MyLog(this,
									"Setting Alarm Muting to OFF");
							}
						}
					case 1:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"Alarm Muting Requested setting is ON");
						}
						if (!alarmmute) {
							cv.put("RINGER_MUTE", "OFF");
							m_audio.adjustStreamVolume(AudioManager.STREAM_ALARM,
								AudioManager.ADJUST_MUTE, 0);
							if (BuildConfig.RINGERMODETEST)
							{
								new MyLog(this,
									"Setting Alarm muting to ON");
							}
						}
					default:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"No change to Alarm Muting requested");
						}
				}
			}
			if (Settings.System.canWrite(this)) {
				current = Settings.System.getInt(
					getContentResolver(), Settings.System.VIBRATE_WHEN_RINGING, 0);
				if (BuildConfig.RINGERMODETEST)
				{
					new MyLog(this,
						"Current ALSO_VIBRATE is "
							+ ((current == 0) ? "off" : "on"));
				}
				switch (m_alsoVibrate) {
					case 0:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"Requested ALSO_VIBRATE setting is off");
						}
						if (current != 0) {
							cv.put("VIBRATE_ALSO", "OFF");
							Settings.System.putInt(getContentResolver(),
								Settings.System.VIBRATE_WHEN_RINGING, 0);
							if (BuildConfig.RINGERMODETEST) {
								new MyLog(this,
									"Setting ALSO_VIBRATE to off");
							}
						}
						break;
					case 1:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"Requested ALSO_VIBRATE setting is on");
						}
						if (current != 1) {
							cv.put("VIBRATE_ALSO", "ON");
							Settings.System.putInt(getContentResolver(),
								Settings.System.VIBRATE_WHEN_RINGING, 1);
							if (BuildConfig.RINGERMODETEST) {
								new MyLog(this,
									"Setting ALSO_VIBRATE to on");
							}
						}
						break;
					default:
						if (BuildConfig.RINGERMODETEST)
						{
							new MyLog(this,
								"No change to ALSO_VIBRATE requested");
						}
				}
				new MyLog(this,
					"Cannot write to Settings.System");
			}
			if (   (m_notifManager != null)
				&& m_notifManager.isNotificationPolicyAccessGranted())
			{
				current = m_notifManager.getCurrentInterruptionFilter();
				String notifyName = getFilterName(m_notifyFilter);
				if (BuildConfig.RINGERMODETEST)
				{
					new MyLog(this,
						"Current DND mode is " + getFilterName(current));
					if (m_notifyFilter >= 0) {
						new MyLog(this,
							"Requested DND mode is " + notifyName);
					}
					else
					{
						new MyLog(this,
							"No DND mode requested");
					}
				}
				if (current != m_notifyFilter)
				{
					cv.put("DO_NOT_DISTURB_MODE", notifyName);
					m_notifManager.setInterruptionFilter(m_notifyFilter);
					if (BuildConfig.RINGERMODETEST) {
						new MyLog(this,
							"Setting DND mode to " + notifyName);
					}
				}
			}
		}
		if (cv.size() > 0) {
			m_lastModes.update("last_we_set", cv);
			return true;
		}
		else
		{
			if (BuildConfig.RINGERMODETEST) {
				new MyLog(this, "setCurrentRinger didn't change anything");
			}
			return false;
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
		m_audio = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		m_notifManager =
			(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		m_lastModes = new SQLtable(this, "RINGERDNDMODES",
			"RINGER_CLASS_NAME", "last_we_set");
		getUserRinger();

		if (BuildConfig.RINGERMODETEST) {
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = 0;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
			}
			else {
				m_notifyFilter = -1;
			}
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 1000;
			m_notifyVolume = 1000;
			m_systemVolume = 1000;
			m_alarmVolume = 1000;
			m_ringerMute = -1;
			m_notifyMute = -1;
			m_systemMute = -1;
			m_alarmMute = -1;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
			m_ringerVolume = 1000;
			m_notifyVolume = 1000;
			m_systemVolume = 1000;
			m_alarmVolume = 1000;
			m_ringerMute = -1;
			m_notifyMute = -1;
			m_systemMute = -1;
			m_alarmMute = -1;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
			m_ringerVolume = 1000;
			m_notifyVolume = 1000;
			m_systemVolume = 1000;
			m_alarmVolume = 1000;
			m_ringerMute = -1;
			m_notifyMute = -1;
			m_systemMute = -1;
			m_alarmMute = -1;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_SILENT;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 1000;
			m_notifyVolume = 1000;
			m_systemVolume = 1000;
			m_alarmVolume = 1000;
			m_ringerMute = -1;
			m_notifyMute = -1;
			m_systemMute = -1;
			m_alarmMute = -1;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_SILENT;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
			m_ringerVolume = 1000;
			m_notifyVolume = 1000;
			m_systemVolume = 1000;
			m_alarmVolume = 1000;
			m_ringerMute = -1;
			m_notifyMute = -1;
			m_systemMute = -1;
			m_alarmMute = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_SILENT;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
			m_ringerVolume = 1000;
			m_notifyVolume = 1000;
			m_systemVolume = 1000;
			m_alarmVolume = 1000;
			m_ringerMute = -1;
			m_notifyMute = -1;
			m_systemMute = -1;
			m_alarmMute = -1;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_SILENT;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			m_ringerVibrate = -1;
			m_notifyVibrate = -1;
			m_ringerVolume = 1000;
			m_notifyVolume = 1000;
			m_systemVolume = 1000;
			m_alarmVolume = 1000;
			m_ringerMute = -1;
			m_notifyMute = -1;
			m_systemMute = -1;
			m_alarmMute = -1;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = 0;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			m_ringerVibrate = -1;
			m_notifyVibrate = -1;
			m_ringerVolume = 1000;
			m_notifyVolume = 1000;
			m_systemVolume = 1000;
			m_alarmVolume = 1000;
			m_ringerMute = -1;
			m_notifyMute = -1;
			m_systemMute = -1;
			m_alarmMute = -1;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_notifyVibrate = -1;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
			m_notifyVibrate = -1;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
			m_notifyVibrate = -1;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			m_ringerVibrate = -1;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			m_ringerVibrate = -1;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			m_ringerVibrate = -1;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 0;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 0;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 0;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 0;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 1000;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 1;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 1;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 1000;
			m_notifyVolume = 1000;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 1;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 1000;
			m_systemVolume = 6;
			m_alarmVolume = 1000;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 1;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 7;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 1;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
			//noinspection deprecation
			m_ringerVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			//noinspection deprecation
			m_notifyVibrate = AudioManager.VIBRATE_SETTING_ONLY_SILENT;
			m_ringerVolume = 4;
			m_notifyVolume = 5;
			m_systemVolume = 6;
			m_alarmVolume = 1000;
			m_ringerMute = 0;
			m_notifyMute = 0;
			m_systemMute = 0;
			m_alarmMute = 0;
			m_alsoVibrate = -1;
			m_notifyFilter = -1;
			setCurrentRinger();
			getUserRinger();
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALL;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_NONE;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 0;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_NORMAL;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_VIBRATE;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_ON;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_ON;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
				m_ringerMode = AudioManager.RINGER_MODE_SILENT;
				//noinspection deprecation
				m_ringerVibrate = AudioManager.VIBRATE_SETTING_OFF;
				//noinspection deprecation
				m_notifyVibrate = AudioManager.VIBRATE_SETTING_OFF;
				m_ringerVolume = 4;
				m_notifyVolume = 5;
				m_systemVolume = 6;
				m_ringerMute = 0;
				m_notifyMute = 0;
				m_systemMute = 0;
				m_alarmMute = 0;
				m_alsoVibrate = 1;
				m_notifyFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY;
				setCurrentRinger();
				getUserRinger();
			}
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
		final boolean haveStepCounter =packageManager.hasSystemFeature(
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
			new SQLtable(m_lastModes, "ACTIVEINSTANCES");
		CheckTimeZone(provider, activeInstances);
		m_nextAlarm = provider.fillActive(activeInstances, this, m_timeNow);
		activeInstances.moveToPosition(-1);
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
					if (BuildConfig.DEBUG) {
						new MyLog(this,
							"Forcing ACTIVE_END_WAITING for deleted event of class "
								+ className);
					}
					// Force deleted event to end
					state = SQLtable.ACTIVE_END_WAITING;
				}
				if (BuildConfig.DEBUG) {
					new MyLog(this, "Switching on state "
						+ activeInstances.getActiveStateName(state)
						+ " for event " + eventName + " of class " + className);
				}
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
						if (BuildConfig.DEBUG) {
							new MyLog(this,
								"Advancing to state ACTIVE_STARTING for event "
									+ eventName + " of class " + className);
						}
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
						boolean ringChange =
							ringerForClass(classNum, className, true);
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
						if (BuildConfig.DEBUG) {
							new MyLog(this,
								"Advancing to state ACTIVE_START_SENDING for event "
									+ eventName + " of class " + className);
						}
					case SQLtable.ACTIVE_START_SENDING:
						if (state == SQLtable.ACTIVE_START_SENDING)
						{
							// We got here from switch, not from advancing.
                            if (   ringerForClass(classNum, className, false)
                                && (notifyType != null)
                                && notifyType.equals("end of ")
                                && notifySoundFile.isEmpty())
                            {
                                // Suppress any silent notification
                                // for an event which has ended
                                notifyType = null;
                            }
						}
						long endTime = tryMessage(classNum,
							PrefsManager.SEND_MESSAGE_AT_START, activeInstances);
						if (endTime == 0) {
							moveToStartSending("send wait at start of ",
								m_timeNow + CalendarProvider.ONE_HOUR,
								activeInstances);
						} else {
							if (BuildConfig.DEBUG) {
								new MyLog(this,
									"Advancing to state ACTIVE_STARTED for event "
										+ eventName + " of class " + className);
							}
							moveToStarted("for end of ", endTime, activeInstances);
						}
						continue;
					case SQLtable.ACTIVE_STARTED:
                        if (   ringerForClass(classNum, className, false)
                            && (notifyType != null)
                            && notifyType.equals("end of ")
                            && notifySoundFile.isEmpty())
                        {
                            // Suppress any silent notification
                            // for an event which has ended
                            notifyType = null;
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
							if (   ringerForClass(classNum, className, false)
                                && (notifyType != null)
                                && notifyType.equals("end of ")
                                && notifySoundFile.isEmpty())
                            {
                                // Suppress any silent notification
                                // for an event which has ended
                                notifyType = null;
                            }
							continue;
						}
						// We can advance to ENDING now
						if (BuildConfig.DEBUG) {
							new MyLog(this,
								"Advancing to state ACTIVE_ENDING for event "
									+ eventName + " of class " + className);
						}
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
						ringChange =
							PrefsManager.getRestoreRinger(this, classNum);
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
						if (BuildConfig.DEBUG) {
							new MyLog(this,
								"Advancing to state ACTIVE_END_SENDING for event "
									+ eventName + " of class " + className);
						}
					case SQLtable.ACTIVE_END_SENDING:
						endTime = tryMessage(classNum,
							PrefsManager.SEND_MESSAGE_AT_END, activeInstances);
						if (endTime == 0) {
							moveToEndSending("send wait at end of ",
								m_timeNow +
								CalendarProvider.ONE_HOUR, activeInstances);
						} else {
							if (BuildConfig.DEBUG) {
								new MyLog(this,
									"Finished with event "
										+ eventName + " of class " + className
										+ ", deleting it");
							}
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
// hack for testing
//		boolean ringerChange = setCurrentRinger();
		boolean ringerChange = false;
		if (notifyType != null)
		{
			if (ringerChange) {
				small = "Ringer modes updated";
			}
			else {
				small = "Ringer modes unchanged";
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
		resetting = false;
		m_lastModes.close();
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
		boolean needlock = false;
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
				needlock = true;
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
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
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
		if (needlock) { lock(); } else { unlock("updateState"); }
	}

	@Override
	public void onHandleIntent(Intent intent) {
		new MyLog(this, "onHandleIntent("
				  .concat(intent.toString())
				  .concat(")"));
		String action = intent.getAction();
		if (action != null) {
			if (action.equals(MUTESERVICE_RESET)) {
				resetting = true;
			} else if (intent.getAction().equals(MUTESERVICE_SMS_RESULT)) {
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
