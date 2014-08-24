package com.PKH.calendarmute.calendar;

import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;

import com.PKH.calendarmute.PreferencesManager;
import com.PKH.calendarmute.models.Calendar;
import com.PKH.calendarmute.models.CalendarEvent;

public class CalendarProvider {

	private Context context;
	private static Calendar[] savedCalendars;
	
	public CalendarProvider(Context context) {
		this.context = context;
	}
	
	// Projection pour les requêtes de calendrier
	public static final String[] CALENDAR_PROJECTION = new String[] {
		Calendars._ID,
		Calendars.CALENDAR_DISPLAY_NAME,
		Calendars.SYNC_EVENTS
	};
	
	public static final int CALENDAR_PROJECTION_ID_INDEX = 0;
	public static final int CALENDAR_PROJECTION_DISPLAY_NAME_INDEX = 1;
	public static final int CALENDAR_PROJECTION_IS_SYNCED_INDEX = 2;
	
	// Projection pour les requêtes d'évènements
	public static final String[] INSTANCE_PROJECTION = new String[] {
		Instances.TITLE,
		Instances.BEGIN,
		Instances.END,
		Instances.AVAILABILITY
	};
	
	public static final int INSTANCE_PROJECTION_TITLE_INDEX = 0;
	public static final int INSTANCE_PROJECTION_BEGIN_INDEX = 1;
	public static final int INSTANCE_PROJECTION_END_INDEX = 2;
	public static final int INSTANCE_PROJECTION_AVAILABILITY = 3;
	
	/**
	 * Récupération des derniers calendriers chargés par listCalendar
	 * @return Calendriers conservés en mémoire
	 */
	public static Calendar[] getCachedCalendars() {
		return savedCalendars;
	}
	
	/**
	 * Listing des calendriers de l'utilisateur
	 * @return Tous les calendriers de l'utilisateur
	 */
	public Calendar[] listCalendars(boolean forceRefresh) {
		
		if(savedCalendars != null && !forceRefresh)
			return savedCalendars;
		
		Cursor cur = null;
		ContentResolver cr = context.getContentResolver();
		
		Uri calendarUri = Calendars.CONTENT_URI;
		
		cur = cr.query(calendarUri, CALENDAR_PROJECTION, null, null, null);
		
		if(cur == null)
			return null;
		
		LinkedHashMap<Long, Boolean> checkedCalendars = PreferencesManager.getCheckedCalendars(context);
		
		Calendar[] res = new Calendar[cur.getCount()];
		
		int i=0;
		while(cur.moveToNext()) {
			
			long idAgenda = cur.getLong(CALENDAR_PROJECTION_ID_INDEX);
			res[i] = new Calendar(idAgenda, 
					cur.getString(CALENDAR_PROJECTION_DISPLAY_NAME_INDEX),
					cur.getInt(CALENDAR_PROJECTION_IS_SYNCED_INDEX) == 1,
					checkedCalendars.containsKey(idAgenda) && checkedCalendars.get(idAgenda)); // Agenda coché
			
			i++;
		}
		cur.close();
		
		savedCalendars = res;
		
		return res;
	}
	
	private Uri getInstancesQueryUri() {
		// Fenêtre de recherche des évènements : un mois avant et un mois après pour être large
		GregorianCalendar dateDebut = new GregorianCalendar();
		dateDebut.add(GregorianCalendar.MONTH, -1);
		GregorianCalendar dateFin = new GregorianCalendar();
		dateFin.add(GregorianCalendar.MONTH, 1);
		
		// Uri de recherche (contient la fenêtre)
		Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
		ContentUris.appendId(builder, dateDebut.getTimeInMillis());
		ContentUris.appendId(builder, dateFin.getTimeInMillis());
		
		return builder.build();
	}
	
	/**
	 * Renvoie l'évènement en cours dans un des calendriers enregistrés en préférences
	 * @param currentTime Heure à laquelle effectuer la recherche
	 * @param delay Délai étendant l'intervalle des événements à partir de la fin
	 * @param early Délai étendant l'intervalle des événements avant le début
	 * @return Le premier élément trouvé, ou null si aucun évènement ne l'est
	 */
	public CalendarEvent getCurrentEvent(long currentTime, long delay, long early, boolean onlyBusy) {
		ContentResolver cr = context.getContentResolver();
		
		// Fabrication de la chaîne de sélection des IDs de calendriers
		String calIdsSelect = getEventCalendarIdsSelectString();
		
		if(calIdsSelect.equals(""))
			return null;
		
		// La sélection est large sur le début de l'évènement et stricte sur la fin
		// Permet d'être considéré en-dehors de l'évènement en planifiant une alarme à l'heure de fin
		String selection = "(" + calIdsSelect + ") AND "
				+ Instances.BEGIN + " <= ? AND "
				+ Instances.END + " > ? AND " + Instances.ALL_DAY + " = 0";
		
		if(onlyBusy) {
			selection += " AND " + Instances.AVAILABILITY + " = " + Instances.AVAILABILITY_BUSY;
		}
		
		String strCurrentTimeEarly = String.valueOf(currentTime + early);
		String strCurrentTimeDelay = String.valueOf(currentTime - delay);
		String[] selectionArgs =  new String[] { strCurrentTimeEarly, strCurrentTimeDelay };
		
		Cursor cur = cr.query(getInstancesQueryUri(), INSTANCE_PROJECTION, selection, selectionArgs, Instances.END); // On prend l'évènement qui se termine le plus vite
		
		CalendarEvent res;
		if(cur.moveToNext()) {
			res = new CalendarEvent(cur.getString(INSTANCE_PROJECTION_TITLE_INDEX), cur.getLong(INSTANCE_PROJECTION_BEGIN_INDEX), cur.getLong(INSTANCE_PROJECTION_END_INDEX));
		}
		else
			res = null;
		
		cur.close();
		return res;
	}
	
	/**
	 * Renvoie le prochain évènement dans un des calendriers enregistrés en préférences
	 * @param currentTime Heure à laquelle effectuer la recherche
	 * @param early Délai à utiliser pour étendre les dates de début des événements avant le début réel
	 * @return Le premier élément trouvé, ou null si aucun évènement ne l'est
	 */
	public CalendarEvent getNextEvent(long currentTime, long early, boolean onlyBusy) {
		ContentResolver cr = context.getContentResolver();
		
		// Fabrication de la chaîne de sélection des IDs de calendriers
		String calIdsSelect = getEventCalendarIdsSelectString();
		
		if(calIdsSelect.equals(""))
			return null;
		
		// La sélection est large sur le début de l'évènement
		// Permet la compatibilité de logique avec getCurrentEvent
		String selection = "(" + calIdsSelect + ") AND "
				+ Instances.BEGIN + " >= ? AND " + Instances.ALL_DAY + " = 0";
		
		if(onlyBusy) {
			selection += " AND " + Instances.AVAILABILITY + " = " + Instances.AVAILABILITY_BUSY;
		}
		
		// On soustrait early à Instances.BEGIN -> revient à l'ajouter à currentTime dans la comparaison
		String strCurrentTime = String.valueOf(currentTime + early);
		String[] selectionArgs =  new String[] { strCurrentTime };
		
		Cursor cur = cr.query(getInstancesQueryUri(), INSTANCE_PROJECTION, selection, selectionArgs, Instances.BEGIN); // On trie par heure de début pour avoir le premier évèn'
		
		CalendarEvent res;
		if(cur.moveToNext())
			res = new CalendarEvent(cur.getString(INSTANCE_PROJECTION_TITLE_INDEX), cur.getLong(INSTANCE_PROJECTION_BEGIN_INDEX), cur.getLong(INSTANCE_PROJECTION_END_INDEX));
		else
			res = null;
		
		cur.close();
		return res;
	}
	
	/**
	 * Construit une clause WHERE filtrant les agendas sélectionné
	 * @return clause générée, ou une chaîne vide si aucun agenda n'est sélectionné
	 */
	private String getEventCalendarIdsSelectString() {
		LinkedHashMap<Long, Boolean> checkedCalendars = PreferencesManager.getCheckedCalendars(context);
		
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for(long idCalendar : checkedCalendars.keySet()) {
			if(first)
				first = false;
			else
				builder.append(" OR ");
			
			builder.append("(").append(Instances.CALENDAR_ID).append("=").append(idCalendar).append(")");
		}
		return builder.toString();
	}
	
	/**
	 * Récupération des prochains évènements des l'agendas de l'utilisateur
	 * @return Evènements récupérés
	 */
	public CalendarEvent[] getNextEvents() {
		return null;
	}
	
	public static void invalidateCalendars() {
		savedCalendars = null;
	}
}
