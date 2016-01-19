package com.RPP.calendartrigger.service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.WakefulBroadcastReceiver;

import com.RPP.calendartrigger.MyLog;
import com.RPP.calendartrigger.PrefsManager;
import com.RPP.calendartrigger.R;
import com.RPP.calendartrigger.calendar.CalendarProvider;

import java.text.DateFormat;
import java.util.Set;

import static com.RPP.calendartrigger.service.MuteService.StartServiceReceiver.clearCause;
import static com.RPP.calendartrigger.service.MuteService.StartServiceReceiver.getCategories;
import static com.RPP.calendartrigger.service.MuteService.StartServiceReceiver.getCause;
import static com.RPP.calendartrigger.service.MuteService.StartServiceReceiver.getKeys;
import static com.RPP.calendartrigger.service.MuteService.StartServiceReceiver.getWakeTime;

public class MuteService extends IntentService {

	public MuteService() { super("CalendarTriggerService"); }

	public int wantedRinger;
	public boolean canRestoreRinger;
	public boolean wantRestoreRinger;
	public String startEvent;
	public String endEvent;

	public static class StartServiceReceiver
			extends WakefulBroadcastReceiver {
		static long wakeTime;
		static String cause;
		static Set<String> categories;
		static Set<String> keys;

		@Override
		public void onReceive(Context context, Intent intent) {
			wakeTime = System.currentTimeMillis();
			cause = intent.toString();
			categories = intent.getCategories();
			Bundle b = intent.getExtras();
			keys = (b != null) ? b.keySet() : null;
			startWakefulService(context, new Intent(
				context, MuteService.class));
		}
		public static long getWakeTime() { return wakeTime; }
		public static String getCause() { return cause; }
		public static void clearCause() { cause = null; }
		public static Set<String> getCategories() { return categories; }
		public static Set<String> getKeys() { return keys; }
	}

	// Handle event class becoming active
	private void activateClass(int classNum, String name)
	{
		int ringerAction = PrefsManager.getRingerAction(this, classNum);
		if (   (   (ringerAction == AudioManager.RINGER_MODE_SILENT)
				   && (wantedRinger != AudioManager.RINGER_MODE_SILENT))
			   || (   (ringerAction == AudioManager.RINGER_MODE_VIBRATE)
					  && (wantedRinger == AudioManager.RINGER_MODE_NORMAL)))
		{
			wantedRinger = ringerAction;
			if (PrefsManager.getNotifyStart(this, classNum))
			{
				startEvent = name;
			}
			canRestoreRinger = false;
			wantRestoreRinger = false;
			endEvent = "";
		}
		PrefsManager.setClassActive(this, classNum, true);
	}

	// Handle event class becoming inactive
	private void deactivateClass(int classNum, String name)
	{
		if (   canRestoreRinger
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
	private void emitNotification(int resNum, String event)
	{
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

	// Determine which event classes have become active
	// and which event classes have become inactive
	// and consequently what we need to do.
	// Incidentally we compute the next alarm time
	public void updateState()
	{
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
		int n = PrefsManager.getNumClasses(this);
		for (int classNum = 0; classNum < n; ++classNum)
		{
			if (PrefsManager.isClassUsed(this, classNum))
			{
				CalendarProvider provider = new CalendarProvider(this);
				CalendarProvider.startAndEnd result = provider.nextActionTimes(
					this, currentTime, classNum);
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
						deactivateClass(classNum, result.eventName);
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
		}
		else if (wantRestoreRinger)
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

		// Set next alarm if needed
		long lastAlarm = PrefsManager.getLastAlarmTime(this);
		if (nextAlarmTime != lastAlarm)
		{
			PendingIntent pIntent = PendingIntent.getService(
				this, 0, new Intent(this, MuteService.class),
				PendingIntent.FLAG_ONE_SHOT);
			AlarmManager alarmManager;
			alarmManager = (AlarmManager)
				getSystemService(Context.ALARM_SERVICE);
			// Remove previous alarms
			if (lastAlarm != Long.MAX_VALUE) { alarmManager.cancel(pIntent); }

			if (nextAlarmTime != Long.MAX_VALUE)
			{
				// Add new alarm
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
				{
					alarmManager.setExact(
						AlarmManager.RTC_WAKEUP, nextAlarmTime, pIntent);
				} else
				{
					alarmManager.set(
						AlarmManager.RTC_WAKEUP, nextAlarmTime, pIntent);
				}
			}
			PrefsManager.setLastAlarmTime(this, nextAlarmTime);
		}
	}
	
	@Override
	public void onHandleIntent(Intent intent) {
		long wake = getWakeTime();
		DateFormat df = DateFormat.getDateTimeInstance();
		if (getCause() == null)
		{
			if (intent.getAction() == null)
			{
				new MyLog(this, "onStartCommand() from null action");
			}
			else
			{
				new MyLog(this, "onStartCommand() from "
					.concat(intent.getAction()));
			}
		}
		else
		{
			new MyLog(this, "onReceive() from "
				.concat(getCause()).concat(" at ")
				.concat(df.format(wake)));
			clearCause();
			if (getCategories() != null)
			{
				String cats = "Categories: ";
				boolean first = true;
				for (String s: getCategories())
				{
					if (first)
					{
						first = false;
						cats = cats.concat(s);
					}
					else
					{
						cats = cats.concat(", ").concat(s);
					}
				}
				new MyLog(this, cats);
			}
			if (getKeys() != null)
			{
				String cats = "Keys: ";
				boolean first = true;
				for (String s: getKeys())
				{
					if (first)
					{
						first = false;
						cats = cats.concat(s);
					}
					else
					{
						cats = cats.concat(", ").concat(s);
					}
				}
				new MyLog(this, cats);
			}
		}
		updateState();

		// Release the wake lock if we were called from WakefulBroadcastReceiver
		WakefulBroadcastReceiver.completeWakefulIntent(intent);
	}
	
	public static void startIfNecessary(Context c, String caller) {
			c.startService(new Intent(caller, null, c, MuteService.class));
	}
}
