package uk.co.yahoo.p1rpp.calendartrigger.service;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.WakefulBroadcastReceiver;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.activites.MainActivity;
import uk.co.yahoo.p1rpp.calendartrigger.calendar.CalendarProvider;
import uk.co.yahoo.p1rpp.calendartrigger.models.CalendarEvent;

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
			// Actually we could show a notification
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

	public static final String EXTRA_WAKE_TIME = "wakeTime";

	public static final String EXTRA_WAKE_CAUSE = "wakeCause";

	public static class StartServiceReceiver
			extends WakefulBroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			long wakeTime = System.currentTimeMillis();
			String wakeCause = intent.toString();
			if(PrefsManager.getRingerAction(context)
			   != PrefsManager.RINGER_MODE_NONE)
			{
				Intent mute = new Intent(context, MuteService.class);
				mute.putExtra(EXTRA_WAKE_TIME, wakeTime);
				mute.putExtra(EXTRA_WAKE_CAUSE, wakeCause);
				startWakefulService(context, mute);
			}
		}
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
		
		int resText = ringerAction == AudioManager.RINGER_MODE_SILENT
					  ? R.string.mode_sonnerie_change_silencieux_pour
					  : R.string.mode_sonnerie_change_vibreur_pour;
		
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
			
		int flags;
		if (nextEvent != null)
		{
			nextEventTime = nextEvent.getStartTime().getTimeInMillis();
			evName = " for start of ".concat(nextEvent.getNom());
			flags = 0;
		}
		else
		{
			flags = PendingIntent.FLAG_NO_CREATE;
		}
		if (currentEndTime < nextEventTime)
		{
			nextEventTime = currentEndTime;
			evName = " for end of ".concat(currentEvent.getNom());
		}

		PendingIntent pIntent = PendingIntent.getBroadcast(
			this, 0 /*requestCode*/,
			new Intent(this, StartServiceReceiver.class), flags);
		AlarmManager alarmManager
			= (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
		long lastAlarm = PrefsManager.getLastAlarmTime(this);
		DateFormat df = DateFormat.getDateTimeInstance();
		if (nextEventTime != lastAlarm)
		{
			// Remove previous alarms
			if (lastAlarm != Long.MAX_VALUE) { alarmManager.cancel(pIntent); }

			if (nextEventTime != Long.MAX_VALUE)
			{
				// Add new alarm
					alarmManager.setExact(
						AlarmManager.RTC_WAKEUP, nextEventTime, pIntent);
				myLog("Alarm time set to "
						  .concat(df.format(nextEventTime))
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

		myLog("onReceive() from ".concat(cause).concat(wake));

		// Timestamp used in all requests (so it remains consistent)
		long timeNow = System.currentTimeMillis();
		myLog("timeNow is ".concat(df.format(timeNow)));

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
