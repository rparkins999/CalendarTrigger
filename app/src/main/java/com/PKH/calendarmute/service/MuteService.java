package com.PKH.calendarmute.service;

import com.PKH.calendarmute.PreferencesManager;
import com.PKH.calendarmute.R;
import com.PKH.calendarmute.activites.MainActivity;
import com.PKH.calendarmute.calendar.CalendarProvider;
import com.PKH.calendarmute.models.CalendarEvent;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;

public class MuteService extends Service {

	public static final String ACTION_CHECK_CALENDAR_STATUS = "checkStatus";
	
	public static final int NOTIF_ID = 1427;
	
	
	public class LocalBinder extends Binder {
		MuteService getService() {
			return MuteService.this;
		}
	}
	
	public static class StartServiceReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			MuteService.startIfNecessary(context);
		}
	}
	
	private LocalBinder localBinder = new LocalBinder();
	
	@Override
	public IBinder onBind(Intent intent) {
		return localBinder;
	}
	
	/**
	 * Update ringer status depending on settings and time
	 * @param event Current event
	 */
	private void updateStatutSonnerie(CalendarEvent event) {
		AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
		// Current ringer state
		int currentRingerMode = audio.getRingerMode();
		int savedMode = PreferencesManager.getSavedMode(this);
		int ringerAction = PreferencesManager.getRingerAction(this);
		
		if(event == null) { // No current event
			
			if(PreferencesManager.getRestoreState(this) // Restore is on
					&& savedMode != PreferencesManager.PREF_SAVED_MODE_NO_VALUE // There is a mode to restore
					// Check if current setting matches the action (do not restore if user changed the setting herself)
					&& ((ringerAction == PreferencesManager.PREF_ACTION_RINGER_SILENT && currentRingerMode == AudioManager.RINGER_MODE_SILENT)
						|| (ringerAction == PreferencesManager.PREF_ACTION_RINGER_VIBRATE && currentRingerMode == AudioManager.RINGER_MODE_VIBRATE))) {
				// Restore
				audio.setRingerMode(savedMode);
				
				// Close notification if necessary
				NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				manager.cancel(NOTIF_ID);
			}
			// Delete saved mode
			PreferencesManager.saveMode(this, PreferencesManager.PREF_SAVED_MODE_NO_VALUE);
			PreferencesManager.setLastSetRingerMode(this, PreferencesManager.PREF_LAST_SET_RINGER_MODE_NO_MODE);
		}
		else { // We are inside an event
			if(((ringerAction == PreferencesManager.PREF_ACTION_RINGER_SILENT && currentRingerMode != AudioManager.RINGER_MODE_SILENT) // Current ringer setting is different from action
					|| (ringerAction == PreferencesManager.PREF_ACTION_RINGER_VIBRATE && currentRingerMode != AudioManager.RINGER_MODE_VIBRATE))
					&& PreferencesManager.getLastSetRingerMode(this) == PreferencesManager.PREF_SAVED_MODE_NO_VALUE) { // And no action done (so no mode saved) -> the user may have changed the volume
				// Save state and change it if there is not already a saved state
				if(savedMode == PreferencesManager.PREF_SAVED_MODE_NO_VALUE)
					PreferencesManager.saveMode(this, currentRingerMode);
				
				if(ringerAction == PreferencesManager.PREF_ACTION_RINGER_SILENT) {
					audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
					PreferencesManager.setLastSetRingerMode(this, AudioManager.RINGER_MODE_SILENT);
				}
				else { // Must be vibrate
					audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
					PreferencesManager.setLastSetRingerMode(this, AudioManager.RINGER_MODE_VIBRATE);
				}
				
				// Notification
				if(PreferencesManager.getShowNotif(this))
					showNotif(ringerAction, event.getNom());
			}
			// No action if the current setting is already OK (and do not save the current setting either)
		}
	}
	
	private void showNotif(int ringerAction, String nomEven) {
		
		int resText = ringerAction == PreferencesManager.PREF_ACTION_RINGER_SILENT ? R.string.mode_sonnerie_change_silencieux_pour : R.string.mode_sonnerie_change_vibreur_pour;
		
		Resources res = getResources();
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
			.setSmallIcon(R.drawable.notif_icon)
			.setContentTitle(res.getString(R.string.mode_sonnerie_change))
			.setContentText(res.getString(resText) + " " + nomEven);
		
		Intent intent = new Intent(this, MainActivity.class);
		intent.setAction(MainActivity.ACTION_SHOW_ACTIONS);
		
		// Stack for the activity
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		stackBuilder.addParentStack(MainActivity.class);
		stackBuilder.addNextIntent(intent);
		
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);
		
		// Show notification
		NotificationManager notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(NOTIF_ID, builder.build());
	}
	
	private void setNextAlarm(CalendarEvent currentEvent, long timeNow, long delay, long early, boolean onlyBusy, CalendarProvider provider) {
		
		PendingIntent pIntent = PendingIntent.getService(this, 0, new Intent(this, MuteService.class), PendingIntent.FLAG_ONE_SHOT);
		AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
		
		long nextExecutionTime;
		if(currentEvent != null) { // There is an event right now: call again at the end of the event
			nextExecutionTime = currentEvent.getEndTime().getTimeInMillis() + delay;
		}
		else { // No event right now: call at the beginning of next event
			CalendarEvent nextEvent = provider.getNextEvent(timeNow, early, onlyBusy);
			
			if(nextEvent != null)
				nextExecutionTime = nextEvent.getStartTime().getTimeInMillis();
			else
				nextExecutionTime = -1;
		}
			
		
		if(nextExecutionTime != -1) {
			// Remove previous alarms
			alarmManager.cancel(pIntent);
			// Add new alarm
			alarmManager.set(AlarmManager.RTC, nextExecutionTime, pIntent);
		}
	}
	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		// Timestamp used in all requests (so it remains consistent)
		long timeNow = System.currentTimeMillis();
		boolean delayActivated = PreferencesManager.getDelayActivated(this);
		boolean earlyActivated = PreferencesManager.getEarlyActivated(this);
		long delay = delayActivated ? PreferencesManager.getDelay(this) * 60 * 1000 : 0;
		long early = earlyActivated ? PreferencesManager.getEarly(this) * 60 * 1000 : 0;
		
		boolean onlyBusy = PreferencesManager.getOnlyBusy(this);
		
		// Get the current event, if any
		CalendarProvider provider = new CalendarProvider(this);
		CalendarEvent currentEvent = provider.getCurrentEvent(timeNow, delay, early, onlyBusy);
		
		updateStatutSonnerie(currentEvent);
		
		// Setup next execution
		setNextAlarm(currentEvent, timeNow, delay, early, onlyBusy, provider);
		
		return START_NOT_STICKY; // The service can be destroyed now that it has finished its work
	}
	
	public static void startIfNecessary(Context c) {
		if(PreferencesManager.getRingerAction(c) != PreferencesManager.PREF_ACTION_RINGER_NOTHING)
			c.startService(new Intent(c, MuteService.class));
	}
}
