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
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.app.ActivityCompat;
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
import uk.co.yahoo.p1rpp.calendartrigger.calendar.CalendarProvider.NextAlarm;
import uk.co.yahoo.p1rpp.calendartrigger.contacts.ContactCreator;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.DataStore;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.sqlite;

import static java.net.Proxy.Type.HTTP;

public class MuteService extends IntentService
	implements SensorEventListener {

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
							  + PrefsManager.getEnglishStateName(owner, mode));
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
	private long nextAccelTime;

	// Safe for this to be local because if our class gets re-instantiated we
	// create a new one. If the handler is actually in use we have a wake lock
	// so our class instance won't get destroyed.
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
		PrefsManager.setOrientationState(this, PrefsManager.ORIENTATION_IDLE);
		LocationUpdates(0, PrefsManager.LATITUDE_IDLE);
		resetting = true;
	}

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

	// Try to get an email address and/or a phone number from a contact
	private String[] destinationFromContact(
	int classNum, int startOrEnd, String eventname) {
		String s = PrefsManager.getMessageContact(this, classNum, startOrEnd);
		if (s == null) {
			if (PrefsManager.getMessageExtract(this, classNum, startOrEnd)) {
				//Parse the event name to get a contact
				String[] sen = eventname.split(" ");
				int len = sen.length;
				int first = PrefsManager.getMessageFirstCount(
					this, classNum, startOrEnd);
				if (PrefsManager.getMessageFirstDir(this, classNum, startOrEnd)
					== PrefsManager.MESSAGE_DIRECTION_LEFT) {
					first = len - first - 1;
				}
				if ((first >= len) || (first < 0)){
					new MyLog(this,
						"No word " + first + " in event name " + eventname);
					return null;
				}
				int last = PrefsManager.getMessageLastCount(
					this, classNum, startOrEnd);
				if (PrefsManager.getMessageLastDir(this, classNum, startOrEnd)
					== PrefsManager.MESSAGE_DIRECTION_LEFT) {
					last = len - last - 1;
				}
				if ((last >= len) || (last < 0)){
					new MyLog(this,
						"No word " + last + " in event name " + eventname);
					return null;
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
			}
			else
			{
				new MyLog(this,
					"No destination specified for class "
						+ PrefsManager.getClassName(this, classNum)
						+ " event " + eventname);
				return null;
			}
		}
		return ContactCreator.getMessaging(this, s);
	}

	// Send a text message
	@TargetApi(android.os.Build.VERSION_CODES.M)
	private void sendSMS(int apiVersion, int classNum, int startOrEnd,
						 CalendarProvider.startAndEnd result) {
		String eventname = startOrEnd == PrefsManager.SEND_MESSAGE_AT_START
			? result.startEventName
			: result.endEventName;
		String dest = PrefsManager.getMessageNumber(this, classNum, startOrEnd);
		if ((dest == null) || dest.isEmpty()){
			dest = destinationFromContact(classNum, startOrEnd, eventname)[0];
			if ((dest == null) || dest.isEmpty()){
				new MyLog(this,
					"Could not find phone number for class "
						+ PrefsManager.getClassName(this, classNum)
						+ " event " + eventname);
				return;
			}
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
				body = eventname;
				break;
			case PrefsManager.MESSAGE_TEXT_EVENTDESCRIPTION:
				body = startOrEnd == PrefsManager.SEND_MESSAGE_AT_START
					? result.startEventDescription
					: result.endEventDescription;
				break;
			case PrefsManager.MESSAGE_TEXT_LITERAL:
				body = PrefsManager.getMessageLiteral(this, classNum, startOrEnd);
		}
		if ((body == null) || body.isEmpty()) {
			new MyLog(this,
				"No message sent because of empty message body for class "
					+ PrefsManager.getClassName(this, classNum)
					+ " event " + eventname);
			return;
		}
		boolean canSendSms =
			PackageManager.PERMISSION_GRANTED ==
				PermissionChecker.checkSelfPermission(
					this, Manifest.permission.SEND_SMS);
		if (!canSendSms) {
			emitNotification("No Permission to send SMS messages",
				"CalendarTrigger does not have permission" +
					" to send SMS messages.",
				null);
			new MyLog(this,
				"No permission to send SMS messages");
			return;
		}
		SmsManager smsManager;
		if (apiVersion >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
			int id = SmsManager.getDefaultSmsSubscriptionId();
			if (id == -1) {
				emitNotification("No Default subscription ID",
					"Running on API level 22 or later,"
						+ "but SmsManager.getDefaultSmsSubscriptionId() fails.",
					null);
				new MyLog(this,
					"Running on API level 22 or later,"
					+ "but SmsManager.getDefaultSmsSubscriptionId() fails.");
				smsManager = SmsManager.getDefault();
			}
			else
			{
				smsManager = SmsManager.getSmsManagerForSubscriptionId(id);
			}
		}
		else
		{
			smsManager = SmsManager.getDefault();
		}
		ArrayList<String> bodyParts = smsManager.divideMessage(body);
		int n = bodyParts.size();
		ArrayList<PendingIntent> intents = new ArrayList<PendingIntent>(n);
		for (int i = 0; i < n; ++i) {
			Intent intent = new Intent(MUTESERVICE_SMS_RESULT, null,
				this, MuteService.class);
			intent.putExtra("PartNumber", i);
			intent.putExtra("EventName", eventname);
			intents.add(i, PendingIntent.getBroadcast(
				this, i,
				intent,0));
		}
		new MyLog(this, "Sending SMS for event " + eventname);
		smsManager.sendMultipartTextMessage(
			dest, null, bodyParts, intents, null);
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
	private void CheckTimeZone(long currentTime, CalendarProvider provider) {
		int lastOffset = PrefsManager.getLastTimezoneOffset(this);
		new MyLog(this, "CheckTimeZone: lastoffset is "
			+ logOffset(lastOffset));
		int seenOffset = PrefsManager.getLastSeenOffset(this);
		new MyLog(this, "CheckTimeZone: seenoffset is "
			+ logOffset(seenOffset));
		int currentOffset = TimeZone.getDefault().getOffset(currentTime);
		new MyLog(this, "CheckTimeZone: currentoffset is "
			+ logOffset(currentOffset));
		if (currentOffset != lastOffset)
		{
			if (currentOffset != seenOffset) {
				PrefsManager.setLastSeenOffset(this, currentOffset);
				PrefsManager.setUpdateTime(
						this, currentTime + FIVE_MINUTES);
			}
			else if (PrefsManager.getUpdateTime(this) <= currentTime)
			{
				// At least 5 minutes since last time zone change
				PrefsManager.setUpdateTime(this, Long.MAX_VALUE);
				PrefsManager.setLastTimezoneOffset(this, currentOffset);
				provider.doTimeZoneAdjustment(this, currentOffset);
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
	private void doLogCycling(long time)
	{
		if (!PrefsManager.getLogcycleMode(this))
		{
			new MyLog(this,
					  "Exited doLogCycling because log cycling disabled");
			return; // not enabled
		}
		final long ONEDAY = 1000*60*60*24;
		long next = PrefsManager.getLastcycleDate(this);
		next = next + ONEDAY;
		next -= next % ONEDAY;
		if (time < next)
		{
			new MyLog(this,
					  "Exited doLogCycling because not time yet");
			return; // not time to do it yet
		}
		next = time - time % ONEDAY;
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
						inBlock = dd.getTime() > time - ONEDAY;
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
			Sensor sensor;
			try {
				sensor =
					sensorManager.getDefaultSensor(
						Sensor.TYPE_STEP_COUNTER, true);
			} catch (NullPointerException e) { sensor = null; }
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
				nextAccelTime = System.currentTimeMillis() + FIVE_MINUTES;
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
				nextAccelTime = System.currentTimeMillis() + FIVE_MINUTES;
				PrefsManager.setOrientationState(this, PrefsManager.ORIENTATION_IDLE);
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
				  + PrefsManager.getEnglishStateName(this, mode)
				  + ", actually got "
				  + PrefsManager.getEnglishStateName(this, gotmode));
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

	// Process the active event list, updating states if possible,
	// and perform any actions required.
	// Incidentally we compute the next time we need to run and either
	// set an alarm if it is later than 1 minute from now or send ourselves
	// a delayed message if it is sooner since Android won't let us set
	// an alarm so soon.
	@TargetApi(Build.VERSION_CODES.M)
	public void updateState(Intent intent) {
		// Timestamp used in all requests (so it remains consistent)
		long currentTime = System.currentTimeMillis();
		CalendarProvider provider = new CalendarProvider(this);
		sqlite db = new sqlite(this);
		CheckTimeZone(currentTime, provider);
		doLogCycling(currentTime);
		int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int wantedMode = PrefsManager.RINGER_MODE_NONE;
		int user = PrefsManager.getUserRinger(this);
		int last =  PrefsManager.getLastRinger(this);
		int current = PrefsManager.getCurrentMode(this);
		new MyLog(this,
			"last mode is "
				+ PrefsManager.getEnglishStateName(this, last)
				+ ", current mode is "
				+ PrefsManager.getEnglishStateName(this, current));
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
					+ PrefsManager.getEnglishStateName(this, user));
		}
		nextAccelTime = Long.MAX_VALUE;
		String endEvent = "";
		String endClassName = "";
		int endClassNum = 0;
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
				PermissionChecker.checkSelfPermission(
					this, Manifest.permission.ACCESS_FINE_LOCATION);
		if (PrefsManager.getStepCount(this) >= 0)
		{
			new MyLog(this, "Step counter running");
		}
		int numClasses = PrefsManager.getNumClasses(this);
		CalendarProvider.NextAlarm nextAlarm = null;
		Cursor cr = db.rawQuery(
			"SELECT * FROM ACTIVEEVENTS", null);
		while (cr.moveToNext()) {
			int classNum = PrefsManager.getClassNum(
				db.getString(cr, sqlite.ACTIVE_CLASS_NAME));
		}
		if (   (PackageManager.PERMISSION_GRANTED ==
			ActivityCompat.checkSelfPermission(
				this, Manifest.permission.READ_CONTACTS))
			&& (PackageManager.PERMISSION_GRANTED ==
			ActivityCompat.checkSelfPermission(
				this, Manifest.permission.WRITE_CONTACTS))
			&& (PrefsManager.getNextLocationMode(this)))
		{
			CalendarProvider.StartAndLocation sl =
				provider.nextLocation(this, currentTime);
			if (sl != null)
			{
				ContactCreator cc = new  ContactCreator(this);
				cc.makeContact("!NextEventLocation", "", sl.location);
				long slst = sl.startTime + 60000;
				if (   (slst < nextAlarmTime)
					&& (slst > currentTime))
				{
					nextAlarmTime = slst;
					alarmReason = " after start of event "
						.concat(sl.eventName);
				}
			}
		}
		String shortText;
		String longText;
		if (wantedMode < user) { wantedMode = user; }
		else if (wantedMode == PrefsManager.RINGER_MODE_NONE)
		{
			wantedMode = PrefsManager.RINGER_MODE_NORMAL;
		}
		boolean changed =
			setCurrentRinger(audio, currentApiVersion,  wantedMode, current);
		if (changed) {
			shortText = PrefsManager.getRingerStateName(this, wantedMode);
		}
		else {
			shortText = getString(R.string.ringerModeNone);
		}
		if (endNotifyWanted)
		{
			longText = shortText
				+ " "
				+ getString(R.string.eventend)
				+ " "
				+ endEvent
				+ " "
				+ getString(R.string.ofclass)
				+ " "
				+ endClassName;
			if (PrefsManager.getPlaysoundEnd(this, endClassNum)) {
				String path = PrefsManager.getSoundFileEnd(
					this, endClassNum);
				emitNotification(getString(R.string.mode_sonnerie_change),
					longText, path);
				new MyLog(this,
					"Playing sound for end of event "
						+ endEvent + " of class " + endClassName);
			}
			else if (changed && (wantedMode < current)) {
				emitNotification(getString(R.string.mode_sonnerie_change),
					longText, "");
			}
		}
		if (startNotifyWanted)
		{
			longText = shortText
				+ " " + getString(R.string.eventstart)
				+ " " + startEvent
				+ " " + getString(R.string.ofclass)
				+ " " + startClassName;
			if (PrefsManager.getPlaysoundStart(this, startClassNum)) {
				String path = PrefsManager.getSoundFileStart(
					this, startClassNum);
				emitNotification(shortText, longText, path);
				new MyLog(this,
					"Playing sound for start of event "
						+ startEvent + " of class " + startClassName);
			}
			else if (changed) {
				emitNotification(longText, "", null);
			}
		}
		if (!startEvent.isEmpty())
		{
			new MyLog(this,
				"Setting audio mode to "
					+ PrefsManager.getEnglishStateName(this,
					wantedMode)
					+ " for start of event "
					+ startEvent
					+ " of class "
					+ startClassName);
		}
		else if (!endEvent.isEmpty())
		{
			new MyLog(this,
				"Setting audio mode to "
					+ PrefsManager.getEnglishStateName(this,
					wantedMode)
					+ " for end of event "
					+ endEvent
					+ " of class "
					+ endClassName);
		}
		else if (resetting)
		{
			new MyLog(this,
				"Setting audio mode to "
					+ PrefsManager.getEnglishStateName(this,
					wantedMode)
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
		if (updateTime < nextAlarmTime)
		{
			nextAlarmTime = updateTime;
			alarmReason = " for time zone change";
		}
		if (nextAlarmTime == Long.MAX_VALUE)
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
			long delay = nextAlarmTime - System.currentTimeMillis();
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
					.concat(df.format(nextAlarmTime))
					.concat(alarmReason));
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
				new MyLog(this, "Alarm time set to "
					.concat(df.format(nextAlarmTime))
					.concat(alarmReason));
			}
			PrefsManager.setLastAlarmTime(this, nextAlarmTime);
		}

		PrefsManager.setLastInvocationTime(this, currentTime);
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
	// Determine which event classes have become active
	// and which event classes have become inactive
	// and consequently what we need to do.
	// Incidentally we compute the next alarm time.
	@TargetApi(Build.VERSION_CODES.M)
	public void oldupdateState(Intent intent) {
		// Timestamp used in all requests (so it remains consistent)
		long currentTime = System.currentTimeMillis();
		CalendarProvider provider = new CalendarProvider(this);
		CheckTimeZone(currentTime, provider);
		doLogCycling(currentTime);
		long nextTime =  currentTime + FIVE_MINUTES;
		int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int wantedMode = PrefsManager.RINGER_MODE_NONE;
		int user = PrefsManager.getUserRinger(this);
		int last =  PrefsManager.getLastRinger(this);
		int current = PrefsManager.getCurrentMode(this);
		new MyLog(this,
			"last mode is "
				+ PrefsManager.getEnglishStateName(this, last)
				+ ", current mode is "
				+ PrefsManager.getEnglishStateName(this, current));
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
					+ PrefsManager.getEnglishStateName(this, user));
		}
		long nextAlarmTime = Long.MAX_VALUE;
		String alarmReason = "";
		nextAccelTime = Long.MAX_VALUE;
		int classNum;
		String startEvent = "";
		String startClassName = "";
		int startClassNum = 0;
		String endEvent = "";
		String endClassName = "";
		int endClassNum = 0;
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
				PermissionChecker.checkSelfPermission(
					this, Manifest.permission.ACCESS_FINE_LOCATION);
		if (PrefsManager.getStepCount(this) >= 0)
		{
			new MyLog(this, "Step counter running");
		}
		int n = PrefsManager.getNumClasses(this);
		for (classNum = 0; classNum < n; ++classNum)
		{
			if (PrefsManager.isClassUsed(this, classNum))
			{
				String className = PrefsManager.getClassName(this, classNum);
				int ringerAction = PrefsManager.getRingerAction(
					this, classNum);
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
							notNow = checkOrientationWait(classNum, true);
							new MyLog(this, "checkOrientationWait("
								+ className
								+ ", true) returns "
								+ (notNow ? "true" : "false"));
							if (notNow)
							{
								if ((nextAccelTime < nextAlarmTime)
									&& (nextAccelTime > currentTime))
								{
									nextAlarmTime = nextAccelTime;
									alarmReason =
										" for next orientation check for event "
											.concat(result.startEventName)
											.concat(" of class ")
											.concat(className);
								}
							}
							else
							{
								notNow = checkConnectionWait(classNum, true);
								if (notNow)
								{
									if (nextTime < nextAlarmTime)
									{
										nextAlarmTime = nextTime;
										alarmReason =
											(" for next connection check for "
												+ "event ")
												.concat(result.startEventName)
												.concat(" of class ")
												.concat(className);
									}
								}
							}
						}
						if (notNow)
						{
							// we can't start it yet
							triggered = false;
							active = false;
							result.endTime = Long.MAX_VALUE;
						}
						else
						{
							if (ringerAction > wantedMode)
							{
								if (PrefsManager.getNotifyStart(this, classNum))
								{
									startNotifyWanted = true;
								}
								startEvent = result.startEventName;
								startClassName = className;
								startClassNum = classNum;
								PrefsManager.setTargetSteps(this, classNum, 0);
								PrefsManager.setLatitude(this, classNum, 360.0);
								PrefsManager.setClassActive(
									this, classNum, true);
							}
							int messageType =
								PrefsManager.getMessageType(
									this, classNum,
									PrefsManager.SEND_MESSAGE_AT_START);
							if (messageType == PrefsManager.SEND_MESSAGE_SMS) {
								sendSMS(currentApiVersion, classNum,
									PrefsManager.SEND_MESSAGE_AT_START, result);
							}
						}
					}
				}
				if (active) // may have changed
				{
					// class should be currently active
					if (ringerAction > wantedMode)
					{
						wantedMode = ringerAction;
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
					int lastCounterSteps = PrefsManager.getStepCount(this);
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
						if (checkOrientationWait(classNum, false))
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
							waiting = true;
							done = false;
						}
						else if (checkConnectionWait(classNum, false))
						{
							if (nextTime < nextAlarmTime)
							{
								nextAlarmTime = nextTime;
								alarmReason =
									(" for next connection check for "
										+ "event ")
										.concat(result.endEventName)
										.concat(" of class ")
										.concat(className);
							}
							waiting = true;
							done = false;
						}
						if (done) {
							int messageType =
								PrefsManager.getMessageType(
									this, classNum,
									PrefsManager.SEND_MESSAGE_AT_END);
							if (messageType == PrefsManager.SEND_MESSAGE_SMS) {
								sendSMS(currentApiVersion, classNum,
									PrefsManager.SEND_MESSAGE_AT_END, result);
							}
						}
						PrefsManager.setClassActive(this, classNum, false);
					}
					if (PrefsManager.isClassWaiting(this, classNum))
					{
						done = true;
						if ((lastCounterSteps != PrefsManager.STEP_COUNTER_IDLE)
							&& (aftersteps > 0))
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
						if (checkOrientationWait(classNum, false))
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
							waiting = true;
							done = false;
						}
						else if (checkConnectionWait(classNum, false))
						{
							if (nextTime < nextAlarmTime)
							{
								nextAlarmTime = nextTime;
								alarmReason =
									(" for next connection check for "
										+ "event ")
										.concat(result.endEventName)
										.concat(" of class ")
										.concat(className);
							}
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
						if (   (PrefsManager.getRestoreRinger(this,classNum))
							|| (endEvent.isEmpty()))
						{
							if (PrefsManager.getNotifyEnd(this, classNum))
							{
								endNotifyWanted = true;
								endEvent = PrefsManager.getLastActive(
									this, classNum);
								endClassName = className;
								endClassNum = classNum;
							}
						}
						int messageType =
							PrefsManager.getMessageType(
								this, classNum,
								PrefsManager.SEND_MESSAGE_AT_END);
						if (messageType == PrefsManager.SEND_MESSAGE_SMS) {
							sendSMS(currentApiVersion, classNum,
								PrefsManager.SEND_MESSAGE_AT_END, result);
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
		if (   (PackageManager.PERMISSION_GRANTED ==
			ActivityCompat.checkSelfPermission(
				this, Manifest.permission.READ_CONTACTS))
			&& (PackageManager.PERMISSION_GRANTED ==
			ActivityCompat.checkSelfPermission(
				this, Manifest.permission.WRITE_CONTACTS))
			&& (PrefsManager.getNextLocationMode(this)))
		{
			CalendarProvider.StartAndLocation sl =
				provider.nextLocation(this, currentTime);
			if (sl != null)
			{
				ContactCreator cc = new  ContactCreator(this);
				cc.makeContact("!NextEventLocation", "", sl.location);
				long slst = sl.startTime + 60000;
				if (   (slst < nextAlarmTime)
					&& (slst > currentTime))
				{
					nextAlarmTime = slst;
					alarmReason = " after start of event "
						.concat(sl.eventName);
				}
			}
		}
		String shortText;
		String longText;
		if (wantedMode < user) { wantedMode = user; }
		else if (wantedMode == PrefsManager.RINGER_MODE_NONE)
		{
			wantedMode = PrefsManager.RINGER_MODE_NORMAL;
		}
		boolean changed =
			setCurrentRinger(audio, currentApiVersion,  wantedMode, current);
		if (changed) {
			shortText = PrefsManager.getRingerStateName(this, wantedMode);
		}
		else {
			shortText = getString(R.string.ringerModeNone);
		}
		if (endNotifyWanted)
		{
			longText = shortText
				+ " "
				+ getString(R.string.eventend)
				+ " "
				+ endEvent
				+ " "
				+ getString(R.string.ofclass)
				+ " "
				+ endClassName;
			if (PrefsManager.getPlaysoundEnd(this, endClassNum)) {
				String path = PrefsManager.getSoundFileEnd(
					this, endClassNum);
				emitNotification(getString(R.string.mode_sonnerie_change),
					longText, path);
				new MyLog(this,
					"Playing sound for end of event "
						+ endEvent + " of class " + endClassName);
			}
			else if (changed && (wantedMode < current)) {
				emitNotification(getString(R.string.mode_sonnerie_change),
					longText, "");
			}
		}
		if (startNotifyWanted)
		{
			longText = shortText
				+ " " + getString(R.string.eventstart)
				+ " " + startEvent
				+ " " + getString(R.string.ofclass)
				+ " " + startClassName;
			if (PrefsManager.getPlaysoundStart(this, startClassNum)) {
				String path = PrefsManager.getSoundFileStart(
					this, startClassNum);
				emitNotification(shortText, longText, path);
				new MyLog(this,
					"Playing sound for start of event "
						+ startEvent + " of class " + startClassName);
			}
			else if (changed) {
				emitNotification(longText, "", null);
			}
		}
		if (!startEvent.isEmpty())
		{
			new MyLog(this,
				"Setting audio mode to "
					+ PrefsManager.getEnglishStateName(this,
					wantedMode)
					+ " for start of event "
					+ startEvent
					+ " of class "
					+ startClassName);
		}
		else if (!endEvent.isEmpty())
		{
			new MyLog(this,
				"Setting audio mode to "
					+ PrefsManager.getEnglishStateName(this,
					wantedMode)
					+ " for end of event "
					+ endEvent
					+ " of class "
					+ endClassName);
		}
		else if (resetting)
		{
			new MyLog(this,
				"Setting audio mode to "
					+ PrefsManager.getEnglishStateName(this,
					wantedMode)
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
		if (updateTime < nextAlarmTime)
		{
			nextAlarmTime = updateTime;
			alarmReason = " for time zone change";
		}
		if (nextAlarmTime == Long.MAX_VALUE)
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
			long delay = nextAlarmTime - System.currentTimeMillis();
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
					.concat(df.format(nextAlarmTime))
					.concat(alarmReason));
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
				new MyLog(this, "Alarm time set to "
					.concat(df.format(nextAlarmTime))
					.concat(alarmReason));
			}
			PrefsManager.setLastAlarmTime(this, nextAlarmTime);
		}

		PrefsManager.setLastInvocationTime(this, currentTime);
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
			doReset();
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
