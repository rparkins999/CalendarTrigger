package com.RPP.calendartrigger.service;

import com.RPP.calendartrigger.PrefsManager;
import com.RPP.calendartrigger.R;
import com.RPP.calendartrigger.activites.MainActivity;
import com.RPP.calendartrigger.calendar.CalendarProvider;
import com.RPP.calendartrigger.models.CalendarEvent;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Set;

import static com.RPP.calendartrigger.service.MuteService
				  .StartServiceReceiver.clearCause;
import static com.RPP.calendartrigger.service.MuteService.StartServiceReceiver.getCategories;
import static com.RPP.calendartrigger.service.MuteService
				  .StartServiceReceiver.getCause;
import static com.RPP.calendartrigger.service.MuteService.StartServiceReceiver.getKeys;
import static com.RPP.calendartrigger.service.MuteService
				  .StartServiceReceiver.getWakeTime;
import static com.RPP.calendartrigger.service.MuteService
				  .StartServiceReceiver.getCategories;
import static com.RPP.calendartrigger.service.MuteService
				  .StartServiceReceiver.getKeys;

public class MuteService extends IntentService {

	public MuteService() { super("CalendarTriggerService"); }

	private void myLog(String s) {
		final String LOGFILE = "/sdcard/data/CalendarTriggerLog.txt";
		try
		{
			FileOutputStream out = new FileOutputStream(LOGFILE, true);
			PrintStream log = new PrintStream(out);
			log.printf("CalendarTrigger %s: %s\n",
					   DateFormat.getDateTimeInstance().format(new Date()), s);
			log.close();
		} catch (FileNotFoundException e) {
			// We can't do anything here because we're a background thread
		}
	}

	private String ringerModeName(int mode) {
		switch (mode) {
			case AudioManager.RINGER_MODE_NORMAL:
				return "RINGER_MODE_NORMAL";
			case AudioManager.RINGER_MODE_VIBRATE:
				return "RINGER_MODE_VIBRATE";
			case AudioManager.RINGER_MODE_SILENT:
				return "RINGER_MODE_SILENT";
			case PrefsManager.RINGER_MODE_NONE:
				return "NONE";
			default:
				return "<unknown ringer mode>";
		}
	}

	public static final String ACTION_CHECK_CALENDAR_STATUS = "checkStatus";
	
	public static final int NOTIF_ID = 1427;
	
	
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
			if(PrefsManager.getRingerAction(context)
			   != PrefsManager.RINGER_MODE_NONE)
			{
				startWakefulService(context, new Intent(
					context, MuteService.class));
			}
		}
		public static long getWakeTime() { return wakeTime; }
		public static String getCause() { return cause; }
		public static void clearCause() { cause = null; }
		public static Set<String> getCategories() { return categories; }
		public static Set<String> getKeys() { return keys; }
	}
	
	/**
	 * Update ringer status depending on settings and time
	 * @param event Current event
	 */
	private void updateStatutSonnerie(CalendarEvent event) {
		AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
		// Current ringer state
		int currentRingerMode = audio.getRingerMode();
		int savedMode = PrefsManager.getSavedMode(this);
		int ringerAction = PrefsManager.getRingerAction(this);
		int lastSetMode = PrefsManager.getLastSetRingerMode(this);
		myLog("Current mode is ".concat(ringerModeName(currentRingerMode)));
		myLog("Saved mode is ".concat(ringerModeName(savedMode)));
		myLog("Requested action is ".concat(ringerModeName(ringerAction)));
		myLog("Last set mode is ".concat(ringerModeName(lastSetMode)));

		if(event == null) { // No current event
			myLog("Handling null event");
			if(PrefsManager.getRestoreState(this) // Restore is on
					&& savedMode != PrefsManager.RINGER_MODE_NONE // There is a mode to restore
					// Check if current setting matches the action (do not restore if user changed the setting herself)
					&& (   (ringerAction == AudioManager.RINGER_MODE_SILENT && currentRingerMode == AudioManager.RINGER_MODE_SILENT)
						|| (ringerAction == AudioManager.RINGER_MODE_VIBRATE && currentRingerMode == AudioManager.RINGER_MODE_VIBRATE))) {
				// Restore
				myLog("Restoring ".concat(ringerModeName(savedMode)));
				audio.setRingerMode(savedMode);

				// Close notification if necessary
				NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				manager.cancel(NOTIF_ID);
			}
			// Delete saved mode
			PrefsManager.saveMode(this, PrefsManager.RINGER_MODE_NONE);
			PrefsManager.setLastSetRingerMode(
			   this, PrefsManager.RINGER_MODE_NONE);
		}
		else { // We are inside an event
			myLog("Handling event: ".concat(event.getNom()));
			if(   (   (   (ringerAction == AudioManager.RINGER_MODE_SILENT)
				       && (currentRingerMode != AudioManager.RINGER_MODE_SILENT)
					  )
				   || (   (ringerAction == AudioManager.RINGER_MODE_VIBRATE)
					   && (currentRingerMode != AudioManager.RINGER_MODE_VIBRATE)
					  )
				  ) // Current ringer setting is different from action
			   && (   (lastSetMode == currentRingerMode)
				   || (lastSetMode == PrefsManager.RINGER_MODE_NONE)
				  ) // And the user has not changed the volume
			  )
			{
				// Save state and change it if there is not already a saved state
				if(savedMode == PrefsManager.RINGER_MODE_NONE)
					PrefsManager.saveMode(this, currentRingerMode);
				
				if(ringerAction == AudioManager.RINGER_MODE_SILENT) {
					myLog("Setting RINGER_MODE_SILENT");
					audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
					PrefsManager.setLastSetRingerMode(this, AudioManager.RINGER_MODE_SILENT);
				} else
				{ // Must be vibrate
					myLog("Setting RINGER_MODE_VIBRATE");
					audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
					PrefsManager.setLastSetRingerMode(this, AudioManager.RINGER_MODE_VIBRATE);
				}
				
				// Notification
				if(PrefsManager.getShowNotif(this))
					showNotif(ringerAction, event.getNom());
			}
			// No action if the current setting is already OK
			// (and do not save the current setting either)
		}
	}
	
	private void showNotif(int ringerAction, String nomEven) {
		
		int resText = ringerAction == AudioManager.RINGER_MODE_SILENT ? R.string.mode_sonnerie_change_silencieux_pour : R.string.mode_sonnerie_change_vibreur_pour;
		
		Resources res = getResources();
		NotificationCompat.Builder builder
			= new NotificationCompat.Builder(this)
			.setSmallIcon(R.drawable.notif_icon)
			.setContentTitle(res.getString(R.string.mode_sonnerie_change))
			.setContentText(res.getString(resText) + " " + nomEven);
		
		Intent intent = new Intent(this, MainActivity.class);
		intent.setAction(MainActivity.ACTION_SHOW_ACTIONS);
		
		// Stack for the activity
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		stackBuilder.addParentStack(MainActivity.class);
		stackBuilder.addNextIntent(intent);
		
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
			0, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);
		
		// Show notification
		NotificationManager notifManager = (NotificationManager)
			getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(NOTIF_ID, builder.build());
	}
	
	private void setNextAlarm(CalendarEvent currentEvent, long timeNow,
							  long delay, long early, boolean onlyBusy,
							  CalendarProvider provider)
	{
		
		PendingIntent pIntent = PendingIntent.getService(
			this, 0, new Intent(this, MuteService.class),
			PendingIntent.FLAG_ONE_SHOT);
		AlarmManager alarmManager
			= (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
		
		long nextEventTime = Long.MAX_VALUE;
		long currentEndTime = Long.MAX_VALUE;
		String evName = "";
		if(currentEvent != null) {
			// There is an event right now: call again at the end of the event
			currentEndTime
				= currentEvent.getEndTime().getTimeInMillis() + delay;
		}
		CalendarEvent nextEvent
			= provider.getNextEvent(timeNow, early, onlyBusy);
			
		if(nextEvent != null)
		{
			nextEventTime = nextEvent.getStartTime().getTimeInMillis();
			evName = " for start of ".concat(nextEvent.getNom());
		}
		if (currentEndTime < nextEventTime) {
			nextEventTime = currentEndTime;
			evName = " for end of ".concat(currentEvent.getNom());
		}

		long lastAlarm = PrefsManager.getLastAlarmTime(this);
		DateFormat df = DateFormat.getDateTimeInstance();
		if (nextEventTime != lastAlarm)
		{
			// Remove previous alarms
			if (lastAlarm != Long.MAX_VALUE) { alarmManager.cancel(pIntent); }

			if (nextEventTime != Long.MAX_VALUE)
			{
				// Add new alarm
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
				{
					alarmManager.setExact(
						AlarmManager.RTC_WAKEUP, nextEventTime, pIntent);
				} else
				{
					alarmManager.set(
						AlarmManager.RTC_WAKEUP, nextEventTime, pIntent);
				}
				myLog("Setting alarm ".concat(df.format(nextEventTime))
									  .concat(evName));
			}
			PrefsManager.setLastAlarmTime(this, nextEventTime);
		}
		else
		{
			myLog("Alarm time unchanged from "
					  .concat(df.format(nextEventTime)));
		}
		}
		else
		{
			myLog("Alarm time unchanged from "
					  .concat(df.format(nextEventTime)));
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
				myLog("onStartCommand() from null action");
			}
			else
			{
				myLog("onStartCommand() from ".concat(intent.getAction()));
			}
		}
		else
		{
			myLog("onReceive() from ".concat(getCause()).concat(" at ")
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
				myLog(cats);
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
				myLog(cats);
			}
		}
		// Timestamp used in all requests (so it remains consistent)
		long timeNow = System.currentTimeMillis();
		boolean delayActivated = PrefsManager.getDelayActivated(this);
		boolean earlyActivated = PrefsManager.getEarlyActivated(this);
		long delay = delayActivated ? PrefsManager.getDelay(this) * 60 * 1000
									: 0;
		long early = earlyActivated ? PrefsManager.getEarly(this) * 60 * 1000
									: 0;
		
		boolean onlyBusy = PrefsManager.getOnlyBusy(this);

		// Get the current event, if any
		CalendarProvider provider = new CalendarProvider(this);
		CalendarEvent currentEvent
			= provider.getCurrentEvent(timeNow, delay, early, onlyBusy);
		
		updateStatutSonnerie(currentEvent);
		
		// Setup next execution
		setNextAlarm(currentEvent, timeNow, delay, early, onlyBusy, provider);

		// Release the wake lock if we were called from WakefulBroadcastReceiver
		WakefulBroadcastReceiver.completeWakefulIntent (intent);
	}
	
	public static void startIfNecessary(Context c, String caller) {
		if(PrefsManager.getRingerAction(c) != PrefsManager.RINGER_MODE_NONE)
			c.startService(new Intent(caller, null, c, MuteService.class));
	}
}
