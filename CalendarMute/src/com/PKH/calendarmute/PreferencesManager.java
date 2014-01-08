package com.PKH.calendarmute;

import java.util.LinkedHashMap;
import java.util.StringTokenizer;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesManager {

	private static final String PREFS_NAME = "mainPreferences";
	
	private static final String PREF_AGENDAS = "prefAgendas";
	private static final String PREF_AGENDAS_DELIMITER = ",";
	
	private static final String PREF_ACTION_SONNERIE = "actionSonnerie";
	
	private static final String PREF_RESTAURER_ETAT = "restaurerEtat";
	
	private static final String PREF_SAUVEGARDE_MODE = "lastMode";
	
	private static final String PREF_AFFICHER_NOTIF = "afficherNotif";
	
	private static final String PREF_LAST_SET_RINGER_MODE = "lastSetRingerMode";
	
	private static final String PREF_ONLY_BUSY = "onlyBusy";
	
	private static final String PREF_DELAY = "delay";
	
	private static final String PREF_DELAY_ACTIVATED = "delayActivated";
	
	private static final String PREF_EARLY = "early";
	
	private static final String PREF_EARLY_ACTIVATED = "earlyActivated";
	
	public static final int PREF_ACTION_SONNERIE_RIEN = 0;
	public static final int PREF_ACTION_SONNERIE_SILENCIEUX = 1;
	public static final int PREF_ACTION_SONNERIE_VIBREUR = 2;
	
	public static final int PREF_SAVED_MODE_NO_VALUE = -99;
	
	private static final boolean PREF_RESTAURER_ETAT_DEFAULT = true;
	private static final int PREF_ACTION_SONNERIE_DEFAULT = PREF_ACTION_SONNERIE_RIEN;
	
	public static final boolean PREF_AFFICHER_NOTIF_DEFAULT = true;
	
	public static final int PREF_LAST_SET_RINGER_MODE_NO_MODE = -99;
	
	public static final boolean PREF_ONLY_BUSY_DEFAULT = false;
	
	public static final int PREF_DELAY_DEFAULT = 5;
	
	public static final int PREF_EARLY_DEFAULT = 0;
	
	public static final boolean PREF_DELAY_ACTIVATED_DEFAULT = false;
	
	public static final boolean PREF_EARLY_ACTIVATED_DEFAULT = false;
	
	public static LinkedHashMap<Long, Boolean> getCheckedCalendars(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE); 
		String strChecked = prefs.getString(PREF_AGENDAS, "");
		
		StringTokenizer tokenizer = new StringTokenizer(strChecked, PREF_AGENDAS_DELIMITER);
		
		LinkedHashMap<Long, Boolean> res = new LinkedHashMap<Long, Boolean>();
		
		try {
			while(tokenizer.hasMoreTokens()) {
				long nextId = Long.parseLong(tokenizer.nextToken());
				res.put(nextId, true);
			}
		}
		catch(NumberFormatException e) {
			prefs.edit().putString(PREF_AGENDAS, "").commit(); // Suppression de la préférence invalide pour ne pas bloquer l'appli
			throw e;
		}
		
		return res;
	}
	
	/**
	 * Sauvegarde des calendriers sélectionnés dans les préférences
	 */
	public static void saveCalendars(Context context, long[] checkedIds) {
		
		// Création de la chaîne à sauvegarder
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for(long id : checkedIds) {
			if(first)
				first = false;
			else
				builder.append(PREF_AGENDAS_DELIMITER);
			
			builder.append(id);
		}
		
		// Sauvegarde
		SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		preferences.edit().putString(PREF_AGENDAS, builder.toString()).commit();
	}
	
	public final static void saveMode(Context context, int mode) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_SAUVEGARDE_MODE, mode).commit();
	}
	
	public final static void setActionSonnerie(Context context, int action) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_ACTION_SONNERIE, action).commit();
	}
	
	public final static void setRestaurerEtat(Context context, boolean isChecked) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_RESTAURER_ETAT, isChecked).commit();
	}
	
	public final static void setAfficherNotif(Context context, boolean afficher) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_AFFICHER_NOTIF, afficher).commit();
	}
	
	public final static void setLastSetRingerMode(Context context, int ringerMode) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_LAST_SET_RINGER_MODE, ringerMode).commit();
	}
	
	public final static void setOnlyBusy(Context context, boolean onlyBusy) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_ONLY_BUSY, onlyBusy).commit();
	}
	
	public final static void setDelay(Context context, int delay) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_DELAY, delay).commit();
	}
	
	public final static void setDelayActived(Context context, boolean delayActivated) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_DELAY_ACTIVATED, delayActivated).commit();
	}
	
	public final static void setEarly(Context context, int early) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_EARLY, early).commit();
	}
	
	public final static void setEarlyActived(Context context, boolean earlyActivated) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_EARLY_ACTIVATED, earlyActivated).commit();
	}

	public final static int getSavedMode(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_SAUVEGARDE_MODE, PREF_SAVED_MODE_NO_VALUE);
	}

	public static final int getActionSonnerie(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_ACTION_SONNERIE, PREF_ACTION_SONNERIE_DEFAULT);
	}

	public static final boolean getRestaurerEtat(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_RESTAURER_ETAT, PREF_RESTAURER_ETAT_DEFAULT);
	}
	
	public static boolean getAfficherNotif(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_AFFICHER_NOTIF, PREF_AFFICHER_NOTIF_DEFAULT);
	}
	
	public static int getLastSetRingerMode(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_LAST_SET_RINGER_MODE, PREF_LAST_SET_RINGER_MODE_NO_MODE);
	}
	
	public static boolean getOnlyBusy(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_ONLY_BUSY, PREF_ONLY_BUSY_DEFAULT);
	}
	
	public static boolean getDelayActivated(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_DELAY_ACTIVATED, PREF_DELAY_ACTIVATED_DEFAULT);
	}
	
	public static boolean getEarlyActivated(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_EARLY_ACTIVATED, PREF_EARLY_ACTIVATED_DEFAULT);
	}
	
	public static int getDelay(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_DELAY, PREF_DELAY_DEFAULT);
	}
	
	public static int getEarly(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_EARLY, PREF_EARLY_DEFAULT);
	}
}
