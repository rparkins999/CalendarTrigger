package com.RPP.calendartrigger;

import java.util.LinkedHashMap;
import java.util.StringTokenizer;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {

	private static final String PREFS_NAME = "mainPreferences";
	
	private static final String PREF_AGENDAS = "prefAgendas";
	private static final String PREF_AGENDAS_DELIMITER = ",";
	
	private static final String PREF_ACTION_RINGER = "actionSonnerie";
	
	private static final String PREF_RESTORE_STATE = "restaurerEtat";
	
	private static final String PREF_SAVED_MODE = "lastMode";
	
	private static final String PREF_SHOW_NOTIF = "afficherNotif";
	
	private static final String PREF_LAST_SET_RINGER_MODE = "lastSetRingerMode";
	
	private static final String PREF_ONLY_BUSY = "onlyBusy";
	
	private static final String PREF_DELAY = "delay";
	
	private static final String PREF_DELAY_ACTIVATED = "delayActivated";
	
	private static final String PREF_EARLY = "early";
	
	private static final String PREF_EARLY_ACTIVATED = "earlyActivated";
	
	public static final int RINGER_MODE_NONE = -99;
	
	private static final boolean PREF_RESTORE_STATE_DEFAULT = true;
	public static final boolean PREF_SHOW_NOTIF_DEFAULT = true;
	
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
			prefs.edit().putString(PREF_AGENDAS, "").commit(); // Suppress the last invalid preference to avoid crashing the app
			throw e;
		}
		
		return res;
	}
	
	/**
	 * Save selected calendars in preferences
	 */
	public static void saveCalendars(Context context, long[] checkedIds) {
		
		// Create the string to save
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for(long id : checkedIds) {
			if(first)
				first = false;
			else
				builder.append(PREF_AGENDAS_DELIMITER);
			
			builder.append(id);
		}
		
		// Save
		SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		preferences.edit().putString(PREF_AGENDAS, builder.toString()).commit();
	}
	
	public final static void saveMode(Context context, int mode) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_SAVED_MODE, mode).commit();
	}
	
	public final static void setActionSonnerie(Context context, int action) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putInt(PREF_ACTION_RINGER, action).commit();
	}
	
	public final static void setRestaurerEtat(Context context, boolean isChecked) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_RESTORE_STATE, isChecked).commit();
	}
	
	public final static void setAfficherNotif(Context context, boolean afficher) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_SHOW_NOTIF, afficher).commit();
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
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_SAVED_MODE, RINGER_MODE_NONE);
	}

	public static final int getRingerAction(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_ACTION_RINGER, RINGER_MODE_NONE);
	}

	public static final boolean getRestoreState(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_RESTORE_STATE, PREF_RESTORE_STATE_DEFAULT);
	}
	
	public static boolean getShowNotif(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_SHOW_NOTIF, PREF_SHOW_NOTIF_DEFAULT);
	}
	
	public static int getLastSetRingerMode(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_LAST_SET_RINGER_MODE, RINGER_MODE_NONE);
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
