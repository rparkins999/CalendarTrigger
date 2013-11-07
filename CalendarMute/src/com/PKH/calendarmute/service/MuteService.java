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
	 * Met à jour le statut de la sonnerie selon les paramètres et l'heure
	 * @param isInEvent Il y a un évènement en cours
	 */
	private void updateStatutSonnerie(CalendarEvent event) {
		AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		
		// Etat actuel de la sonnerie
		int currentRingerMode = audio.getRingerMode();
		int savedMode = PreferencesManager.getSavedMode(this);
		int actionSonnerie = PreferencesManager.getActionSonnerie(this);
		
		if(event == null) { // Pas d'évènement en cours
			
			if(PreferencesManager.getRestaurerEtat(this) // La restauration est demandée
					&& savedMode != PreferencesManager.PREF_SAVED_MODE_NO_VALUE // Il y a un mode sauvegardé
					&& ((actionSonnerie == PreferencesManager.PREF_ACTION_SONNERIE_SILENCIEUX && currentRingerMode == AudioManager.RINGER_MODE_SILENT) // Le réglage en cours correspond à l'action
						|| (actionSonnerie == PreferencesManager.PREF_ACTION_SONNERIE_VIBREUR && currentRingerMode == AudioManager.RINGER_MODE_VIBRATE))) { 
				// Restauration
				audio.setRingerMode(savedMode);
				
				// Suppression de la notif au besoin
				NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				manager.cancel(NOTIF_ID);
			}
			// Suppression du réglage sauvegardé
			PreferencesManager.saveMode(this, PreferencesManager.PREF_SAVED_MODE_NO_VALUE);
			PreferencesManager.setLastSetRingerMode(this, PreferencesManager.PREF_LAST_SET_RINGER_MODE_NO_MODE);
		}
		else { // Un évènement est en cours
			if(((actionSonnerie == PreferencesManager.PREF_ACTION_SONNERIE_SILENCIEUX && currentRingerMode != AudioManager.RINGER_MODE_SILENT) // Réglage actuel différent de l'action à effectuer
					|| (actionSonnerie == PreferencesManager.PREF_ACTION_SONNERIE_VIBREUR && currentRingerMode != AudioManager.RINGER_MODE_VIBRATE))
					&& PreferencesManager.getLastSetRingerMode(this) == PreferencesManager.PREF_SAVED_MODE_NO_VALUE) { // Et aucune action effectuée (donc pas de valeur sauvegardée) -> l'utilisateur peut avoir changé son volume
				// Sauvegarde de l'état et changement si pas d'était déjà enregistré
				if(savedMode == PreferencesManager.PREF_SAVED_MODE_NO_VALUE)
					PreferencesManager.saveMode(this, currentRingerMode);
				
				if(actionSonnerie == PreferencesManager.PREF_ACTION_SONNERIE_SILENCIEUX) {
					audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
					PreferencesManager.setLastSetRingerMode(this, AudioManager.RINGER_MODE_SILENT);
				}
				else if(actionSonnerie == PreferencesManager.PREF_ACTION_SONNERIE_VIBREUR) {
					audio.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
					PreferencesManager.setLastSetRingerMode(this, AudioManager.RINGER_MODE_VIBRATE);
				}
				
				// Notification
				if(PreferencesManager.getAfficherNotif(this))
					showNotif(actionSonnerie, event.getNom());
			}
			// Pas d'action si le réglage actuel correspond déjà (pas de sauvegarde non plus)
		}
	}
	
	private void showNotif(int actionSonnerie, String nomEven) {
		
		int resText = actionSonnerie == PreferencesManager.PREF_ACTION_SONNERIE_SILENCIEUX ? R.string.mode_sonnerie_change_silencieux_pour : R.string.mode_sonnerie_change_vibreur_pour;
		
		Resources res = getResources();
		NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
			.setSmallIcon(R.drawable.notif_icon)
			.setContentTitle(res.getString(R.string.mode_sonnerie_change))
			.setContentText(res.getString(resText) + " " + nomEven);
		
		Intent intent = new Intent(this, MainActivity.class);
		intent.setAction(MainActivity.ACTION_SHOW_ACTIONS);
		
		// Stack pour l'activité
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		stackBuilder.addParentStack(MainActivity.class);
		stackBuilder.addNextIntent(intent);
		
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(resultPendingIntent);
		
		// Affichage de la notif
		NotificationManager notifManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notifManager.notify(NOTIF_ID, builder.build());
	}
	
	private void setNextAlarm(CalendarEvent currentEvent, long timeNow, long delay, boolean onlyBusy, CalendarProvider provider) {
		
		PendingIntent pIntent = PendingIntent.getService(this, 0, new Intent(this, MuteService.class), PendingIntent.FLAG_ONE_SHOT);
		AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
		
		long timeProchainAppel;
		if(currentEvent != null) { // Evènement en cours : prochain appel à la fin de l'évèn'
			timeProchainAppel = currentEvent.getEndTime().getTimeInMillis() + delay;
		}
		else { // Pas d'évèn : appel au début du prochain évènement
			CalendarEvent nextEvent = provider.getNextEvent(timeNow, onlyBusy);
			
			if(nextEvent != null)
				timeProchainAppel = nextEvent.getStartTime().getTimeInMillis();
			else
				timeProchainAppel = -1;
		}
			
		
		if(timeProchainAppel != -1) {
			// Suppression des alarmes précédents
			alarmManager.cancel(pIntent);
			// Ajout de la nouvelle alarme
			alarmManager.set(AlarmManager.RTC, timeProchainAppel, pIntent);
		}
	}
	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		// Timestamp utilisé pour toutes les requêtes (consistance de l'instant)
		long timeNow = System.currentTimeMillis();
		boolean delayActivated = PreferencesManager.getDelayActivated(this);
		long delay = delayActivated ? PreferencesManager.getDelay(this) * 60 * 1000 : 0;
		
		boolean onlyBusy = PreferencesManager.getOnlyBusy(this);
		
		// Récupération de l'éventuel évènement en cours
		CalendarProvider provider = new CalendarProvider(this);
		CalendarEvent currentEvent = provider.getCurrentEvent(timeNow, delay, onlyBusy);
		
		updateStatutSonnerie(currentEvent);
		
		// Planification du prochain appel
		setNextAlarm(currentEvent, timeNow, delay, onlyBusy, provider);
		
		return START_NOT_STICKY; // Le service peut être détruit maintenant qu'il a fini son boulot
	}
	
	public static void startIfNecessary(Context c) {
		if(PreferencesManager.getActionSonnerie(c) != PreferencesManager.PREF_ACTION_SONNERIE_RIEN)
			c.startService(new Intent(c, MuteService.class));
	}
}
