package uk.co.yahoo.p1rpp.calendartrigger.service;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.text.DateFormat;

import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.calendar.CalendarProvider;


public class MuteService extends IntentService
	implements SensorEventListener,
	GoogleApiClient.ConnectionCallbacks,
	GoogleApiClient.OnConnectionFailedListener {

	public MuteService() {
		super("CalendarTriggerService");
	}

	public int wantedRinger;
	public boolean canRestoreRinger;
	public boolean wantRestoreRinger;
	public boolean anyStepCountActive;
	public String startEvent;
	public String endEvent;
	public static int lastCounterSteps = -2;

	public static final String EXTRA_WAKE_TIME = "wakeTime";
	public static final String EXTRA_WAKE_CAUSE = "wakeCause";

	// We don't know anything sensible to do here
	@Override
	public void onAccuracyChanged(Sensor sensor, int i) {
	}

	@Override
	public void onSensorChanged(SensorEvent sensorEvent) {
		int newCounterSteps = (int)sensorEvent.values[0];
		if (newCounterSteps != lastCounterSteps)
		{
			lastCounterSteps = newCounterSteps;
			startIfNecessary(this, "Step counter changed");
		}
	}

	@Override
	public void onConnected(Bundle bundle) {

	}

	@Override
	public void onConnectionSuspended(int i) {

	}

	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {

	}

	public static class StartServiceReceiver
		extends WakefulBroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			long wakeTime = System.currentTimeMillis();
			String wakeCause = intent.toString();
			Intent mute = new Intent(context, MuteService.class);
			mute.putExtra(EXTRA_WAKE_TIME, wakeTime);
			mute.putExtra(EXTRA_WAKE_CAUSE, wakeCause);
			startWakefulService(context, mute);
		}
	}

	// Handle event class becoming active
	private void activateClass(int classNum, String name) {
		int ringerAction = PrefsManager.getRingerAction(this, classNum);
		if (((ringerAction == AudioManager.RINGER_MODE_SILENT)
			 && (wantedRinger != AudioManager.RINGER_MODE_SILENT))
			|| ((ringerAction == AudioManager.RINGER_MODE_VIBRATE)
				&& (wantedRinger == AudioManager.RINGER_MODE_NORMAL)))
		{
			wantedRinger = ringerAction;
			canRestoreRinger = false;
			wantRestoreRinger = false;
			if (PrefsManager.getNotifyStart(this, classNum))
			{
				startEvent = name;
			}
			endEvent = "";
		}
		if (   (PrefsManager.getNotifyStart(this, classNum))
			&& (startEvent.isEmpty()))
		{
			startEvent = name;
		}
		PrefsManager.setClassActive(this, classNum, true);
	}

	// Handle event class becoming inactive
	private void deactivateClass(int classNum, String name) {
		if (canRestoreRinger
			&& (PrefsManager.getRestoreRinger(this, classNum)))
		{
			wantRestoreRinger = true;
			if (PrefsManager.getNotifyEnd(this, classNum))
			{
				endEvent = name;
			}
		}
		PrefsManager.setClassActive(this, classNum, false);
	}

	private static final int NOTIFY_ID = 1427;

	private void emitNotification(int resNum, String event) {
		Resources res = getResources();
		NotificationCompat.Builder builder
			= new NotificationCompat.Builder(this)
			.setSmallIcon(R.drawable.notif_icon)
			.setContentTitle(res.getString(R.string.mode_sonnerie_change))
			.setContentText(res.getString(resNum) + " " + event);
		// Show notification
		NotificationManager notifManager = (NotificationManager)
			getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(NOTIFY_ID, builder.build());
	}


	private boolean handleStepWait(int classNum, String eventName) {
		int target = PrefsManager.getTargetSteps(this, classNum);
		if (target == 0)
		{
			if (lastCounterSteps == -2)
			{
				// need to startup the sensor
		        SensorManager sensorManager =
                	(SensorManager)getSystemService(Activity.SENSOR_SERVICE);
				Sensor sensor =
					sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
				if (sensor == null) { return false; }
				if (!sensorManager.registerListener(
						this, sensor, SensorManager.SENSOR_DELAY_NORMAL))
				{ return false; }
				lastCounterSteps = -1;
			}
			else if (lastCounterSteps >= 0)
			{
				target = lastCounterSteps
						 + PrefsManager.getAfterSteps(this, classNum);
				PrefsManager.setTargetSteps(this, classNum, target);
			}
		}
		else if (lastCounterSteps >= target)
		{
			return false;
		}
		anyStepCountActive = true;
		return true;
	}

	private boolean handleLocationWait(int classNum, String eventName) {
		GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(this)
			.addConnectionCallbacks(this)
			.addOnConnectionFailedListener(this)
			.addApi(LocationServices.API)
			.build();
	}

	// Determine which event classes have become active
	// and which event classes have become inactive
	// and consequently what we need to do.
	// Incidentally we compute the next alarm time.
	// Returns true if we don't need to keep our wake lock.
	public boolean updateState() {
		// Timestamp used in all requests (so it remains consistent)
		long currentTime = System.currentTimeMillis();
		AudioManager audio
			= (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		int userRinger = audio.getRingerMode();
		wantedRinger = userRinger;
		canRestoreRinger = userRinger == PrefsManager.getLastRinger(this);
		wantRestoreRinger = false;
		long nextAlarmTime = Long.MAX_VALUE;
		startEvent = "";
		endEvent = "";
		anyStepCountActive = false;
		int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		PackageManager packageManager = getPackageManager();
		final boolean haveStepCounter =
			currentApiVersion >= android.os.Build.VERSION_CODES.KITKAT
			&& packageManager.hasSystemFeature(
				PackageManager.FEATURE_SENSOR_STEP_COUNTER);
		final boolean havelocation =
			PackageManager.PERMISSION_GRANTED ==
			ActivityCompat.checkSelfPermission(this,
				Manifest.permission.ACCESS_FINE_LOCATION);
		int n = PrefsManager.getNumClasses(this);
		for (int classNum = 0; classNum < n; ++classNum)
		{
			if (PrefsManager.isClassUsed(this, classNum))
			{
				CalendarProvider provider = new CalendarProvider(this);
				CalendarProvider.startAndEnd result
					= provider.nextActionTimes(this, currentTime, classNum);
				if (result.startTime == Long.MAX_VALUE)
				{
					if (!PrefsManager.isClassActive(this, classNum))
					{
						activateClass(classNum, result.eventName);
					}
					if (result.endTime < nextAlarmTime)
					{
						nextAlarmTime = result.endTime;
					}
				}
				else
				{
					if (PrefsManager.isClassActive(this, classNum))
					{
						boolean done = true;
						if (haveStepCounter
							&& PrefsManager.getAfterSteps(this, classNum) > 0
							&& handleStepWait(classNum, result.eventName))
						{
							done = false;
						}
						if (havelocation
							     && PrefsManager.getAfterMetres(this, classNum) > 0
							     && handleLocationWait(classNum, result.eventName))
						{
							done = false;
						}
						if (done)
						{
							deactivateClass(classNum, result.eventName);
						}
					}
					if (result.startTime < nextAlarmTime)
					{
						nextAlarmTime = result.startTime;
					}
				}
			}
		}
		if (wantedRinger != userRinger)
		{
			PrefsManager.setUserRinger(this, userRinger);
			audio.setRingerMode(wantedRinger);
			PrefsManager.setLastRinger(this, wantedRinger);
			if (!startEvent.equals(""))
			{
				int resNum =
					(wantedRinger == AudioManager.RINGER_MODE_SILENT)
					? R.string.mode_sonnerie_change_silencieux_pour
					: R.string.mode_sonnerie_change_vibreur_pour;
				emitNotification(resNum, startEvent);
				new MyLog(this, "Setting ringer to "
					.concat(MyLog.rm(wantedRinger))
					.concat(" for event ")
					.concat(startEvent));
			}
		} else if (wantRestoreRinger)
		{
			wantedRinger = PrefsManager.getUserRinger(this);
			if (wantedRinger != userRinger)
			{
				audio.setRingerMode(wantedRinger);
				if (!endEvent.equals(""))
				{
					int resNum =
						(wantedRinger == AudioManager.RINGER_MODE_VIBRATE)
						? R.string.mode_sonnerie_change_vibreur_apres
						: R.string.mode_sonnerie_change_normale_apres;
					emitNotification(resNum, endEvent);
					new MyLog(this, "Restoring ringer to "
						.concat(MyLog.rm(wantedRinger))
						.concat(" after event ")
						.concat(endEvent));
				}
			}
			PrefsManager.setLastRinger(this, PrefsManager.RINGER_MODE_NONE);
		}

		long lastAlarm = PrefsManager.getLastAlarmTime(this);
		if (nextAlarmTime != lastAlarm)
		{
			int flags = 0;
			if (lastAlarm == Long.MAX_VALUE)
			{
				flags = PendingIntent.FLAG_NO_CREATE;
			}
			PendingIntent pIntent = PendingIntent.getBroadcast(
				this, 0 /*requestCode*/,
				new Intent(this, StartServiceReceiver.class), flags);
			AlarmManager alarmManager = (AlarmManager)
				getSystemService(Context.ALARM_SERVICE);
			// Remove previous alarms
			if (lastAlarm != Long.MAX_VALUE)
			{
				alarmManager.cancel(pIntent);
			}

			if (nextAlarmTime != Long.MAX_VALUE)
			{
				// Add new alarm
				alarmManager.setExact(
					AlarmManager.RTC_WAKEUP, nextAlarmTime, pIntent);
				DateFormat df = DateFormat.getDateTimeInstance();
				new MyLog(this, "Alarm time set to "
					.concat(df.format(nextAlarmTime)));
			}
		}
		PrefsManager.setLastAlarmTime(this, nextAlarmTime);
		PrefsManager.setLastInvocationTime(this,currentTime);
		if (anyStepCountActive)
		{
			// Step counter is a non wake up sensor so we need to hold
			// our wake lock.
			return false;
		}
		else
		{
			if (lastCounterSteps >= 0)
			{
				lastCounterSteps = -2;
				SensorManager sensorManager =
					(SensorManager)getSystemService(Activity.SENSOR_SERVICE);
				sensorManager.unregisterListener(this);
			}
			return true;
		}
	}


	@Override
	public void onHandleIntent(Intent intent) {
		String wake;
		DateFormat df = DateFormat.getDateTimeInstance();
		if (intent.hasExtra(EXTRA_WAKE_TIME))
		{
			wake = " received at "
				.concat(df.format(intent.getLongExtra(EXTRA_WAKE_TIME, 0)));
		}
		else
		{
			wake = "";
		}
		String cause;
		if (intent.hasExtra(EXTRA_WAKE_CAUSE))
		{
			cause = intent.getStringExtra(EXTRA_WAKE_CAUSE);
		}
		else
		{
			cause = "null action";
		}
		new MyLog(this, "onReceive() from "
						.concat(cause).concat(" at ")
						.concat(wake));

		if (updateState()) {
			// Release the wake lock
			// if we were called from WakefulBroadcastReceiver
			WakefulBroadcastReceiver.completeWakefulIntent(intent);
		}
	}
	
	public static void startIfNecessary(Context c, String caller) {
			c.startService(new Intent(caller, null, c, MuteService.class));
	}
}
