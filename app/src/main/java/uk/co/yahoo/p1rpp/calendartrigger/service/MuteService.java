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
import android.support.v4.content.WakefulBroadcastReceiver;

import com.RPP.calendartrigger.MyLog;
import com.RPP.calendartrigger.PrefsManager;
import com.RPP.calendartrigger.R;
import com.RPP.calendartrigger.calendar.CalendarProvider;

import java.text.DateFormat;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.activites.MainActivity;
import uk.co.yahoo.p1rpp.calendartrigger.calendar.CalendarProvider;
import uk.co.yahoo.p1rpp.calendartrigger.models.CalendarEvent;

public class MuteService extends IntentService {

	public MuteService() { super("CalendarTriggerService"); }

	public int wantedRinger;
	public boolean canRestoreRinger;
	public boolean wantRestoreRinger;
	public String startEvent;
	public String endEvent;
			// Actually we could show a notification

	public static final String EXTRA_WAKE_TIME = "wakeTime";

	public static final String EXTRA_WAKE_CAUSE = "wakeCause";

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
					  ? R.string.mode_sonnerie_change_silencieux_pour
					  : R.string.mode_sonnerie_change_vibreur_pour;
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
		int flags;
		if (nextEvent != null)
					{
						nextAlarmTime = result.endTime;
			flags = 0;
					}
		else
		{
			flags = PendingIntent.FLAG_NO_CREATE;
		}
		if (currentEndTime < nextEventTime)
		{
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

		PendingIntent pIntent = PendingIntent.getBroadcast(
			this, 0 /*requestCode*/,
			new Intent(this, StartServiceReceiver.class), flags);
		AlarmManager alarmManager
			= (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
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
					alarmManager.setExact(
						AlarmManager.RTC_WAKEUP, nextAlarmTime, pIntent);
				myLog("Alarm time set to "
						  .concat(df.format(nextEventTime))
						AlarmManager.RTC_WAKEUP, nextAlarmTime, pIntent);
				}
			}
			PrefsManager.setLastAlarmTime(this, nextAlarmTime);
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
				new MyLog(this, "onStartCommand() from null action");
				new MyLog(this, "onStartCommand() from "
					.concat(intent.getAction()));
			new MyLog(this, "onReceive() from "
				.concat(getCause()).concat(" at ")
				.concat(df.format(wake)));
		}
		else
		{
			wake = "";
		}
		String cause;
				new MyLog(this, cats);
		{
			cause = intent.getStringExtra(EXTRA_WAKE_CAUSE);
		}
		else
		{
			cause = "null action";
		}

				new MyLog(this, cats);

		updateState();
		myLog("timeNow is ".concat(df.format(timeNow)));


		// Release the wake lock if we were called from WakefulBroadcastReceiver
		WakefulBroadcastReceiver.completeWakefulIntent(intent);
	}
	
	public static void startIfNecessary(Context c, String caller) {
			c.startService(new Intent(caller, null, c, MuteService.class));
	}
}
