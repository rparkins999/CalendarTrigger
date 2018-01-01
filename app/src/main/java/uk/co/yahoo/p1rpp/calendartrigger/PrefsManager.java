/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class PrefsManager {

	private static final String PREFS_NAME = "mainPreferences";

	private static final String PREF_DEFAULTDIRECTORY = "DefaultDir";

	public static final void setDefaultDir(Context context, String dir) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putString(PREF_DEFAULTDIRECTORY, dir).commit();
	}

	public static final String getDefaultDir(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(PREF_DEFAULTDIRECTORY, "/");
	}

	private static final String PREF_LOGGING = "logging";

	public static void setLoggingMode(Context context, boolean IsOn) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putBoolean(PREF_LOGGING, IsOn).commit();
	}

	public static boolean getLoggingMode(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(PREF_LOGGING, false);
	}

	private static final String PREF_PHONE_STATE = "phoneState";

	// Our idea of the phone state differs from Android's because we consider
	// ringing when a call is active to be "active" whereas Android thinks it
	// is "ringing".
	public static final int PHONE_IDLE = 0;
	public static final int PHONE_RINGING = 1;
	public static final int PHONE_CALL_ACTIVE = 2;

	public static void setPhoneState(Context context, int state) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putInt(PREF_PHONE_STATE, state).commit();
	}

	public static int getPhoneState(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(PREF_PHONE_STATE, PHONE_IDLE);
	}

	private static final String PREF_PHONE_WARNED =
		"notifiedCannotReadPhoneState";

	public static void setNotifiedCannotReadPhoneState(
		Context context, boolean state) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			.putBoolean(PREF_PHONE_WARNED, state).commit();

	}

	public static boolean getNotifiedCannotReadPhoneState(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(PREF_PHONE_WARNED, false);
	}

	private static final String PREF_LOCATION_ACTIVE = "locationActive";

	public static void setLocationState(Context context, boolean state) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putBoolean(PREF_LOCATION_ACTIVE, state).commit();
	}

	public static boolean getLocationState(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(PREF_LOCATION_ACTIVE, false);
	}

	private static final String PREF_STEP_COUNT = "stepCounter";

	// step counter is not active	
	public static final int STEP_COUNTER_IDLE = -3;
	
	// wakeup step counter listener registered but not responded yet
	public static final int STEP_COUNTER_WAKEUP = -2;
	
	// non wakeup step counter listener registered but not responded yet
	// (and we hold a wake lock)
	public static final int STEP_COUNTER_WAKE_LOCK = -1;

	// zero or positive is a real step count

	public static void setStepCount(Context context, int steps) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putInt(PREF_STEP_COUNT, steps).commit();
	}

	public static int getStepCount(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(PREF_STEP_COUNT, -3);
	}

	private static final String NUM_CLASSES = "numClasses";

	private static int getNumClasses(SharedPreferences prefs) {
		// hack for first use of new version only
		if (prefs.contains("delay"))
		{
			// old style preferences, remove
			prefs.edit().clear().commit();
		}
		return prefs.getInt(NUM_CLASSES, 0);
	}

	public static int getNumClasses(Context context) {
		SharedPreferences prefs
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		return getNumClasses(prefs);
	}

	private static final String IS_CLASS_USED = "isClassUsed";

	private static boolean isClassUsed(SharedPreferences prefs, int classNum) {
		String prefName = IS_CLASS_USED + String.valueOf(classNum);
		return prefs.getBoolean(prefName, false);
	}

	public static boolean isClassUsed(Context context, int classNum) {
		SharedPreferences prefs
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		return isClassUsed(prefs, classNum);
	}

	public static int getNewClass(Context context) {
		SharedPreferences prefs
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		int n = getNumClasses(prefs);
		StringBuilder builder = new StringBuilder(IS_CLASS_USED);
		for (int classNum = 0; classNum < n; ++classNum)
		{
			if (!isClassUsed(prefs, classNum))
			{
				builder.append(classNum);
				prefs.edit().putBoolean(builder.toString(), true).commit();
				return classNum;
			}
		}
		builder.append(n);
		prefs.edit().putInt(NUM_CLASSES, n + 1)
			 .putBoolean(builder.toString(), true).commit();
		return n;
	}

	private static final String PREF_LAST_INVOCATION = "lastInvocationTime";

		public static void setLastInvocationTime(Context context, long time) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putLong(PREF_LAST_INVOCATION, time).commit();
	}

	public static long getLastInvocationTime(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getLong(PREF_LAST_INVOCATION, Long.MAX_VALUE);
	}

	private static final String PREF_LAST_ALARM = "lastAlarmTime";

	public static void setLastAlarmTime(Context context, long time) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putLong(PREF_LAST_ALARM, time).commit();
	}

	public static long getLastAlarmTime(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getLong(PREF_LAST_ALARM, Long.MAX_VALUE);
	}

	// used for "nothing saved"
	public static final int RINGER_MODE_NONE = -99;

	// Our own set of states: AudioManager			NotificationManager
	// 						  RINGER_MODE_NORMAL	INTERRUPTION_FILTER_ALL
	public static final int RINGER_MODE_NORMAL = 10;

	//                        RINGER_MODE_VIBRATE	INTERRUPTION_FILTER_ALL
	public static final int RINGER_MODE_VIBRATE = 20;

	// (do not disturb)       RINGER_MODE_NORMAL	INTERRUPTION_FILTER_PRIORITY
	public static final int RINGER_MODE_DO_NOT_DISTURB = 30;

	//                        RINGER_MODE_SILENT    INTERRUPTION_FILTER_ALL
	public static final int RINGER_MODE_MUTED = 40;

	//                        RINGER_MODE_NORMAL    INTERRUPTION_FILTER_ALARMS
	public static final int RINGER_MODE_ALARMS = 50;

	//                        RINGER_MODE_SILENT    INTERRUPTION_FILTER_NONE
	public static final int RINGER_MODE_SILENT = 60;

	@TargetApi(android.os.Build.VERSION_CODES.M)
	// Work out what the current ringer state should be from our set of states
	public static int getCurrentMode(Context context)
	{
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
		{
			// Marshmallow or later, has Do Not Disturb mode
			switch (
				((NotificationManager)
					context.getSystemService(Context.NOTIFICATION_SERVICE)
				).getCurrentInterruptionFilter())
			{
				case  NotificationManager.INTERRUPTION_FILTER_NONE:
					return RINGER_MODE_SILENT;
				case  NotificationManager.INTERRUPTION_FILTER_ALARMS:
					return RINGER_MODE_ALARMS;
				case  NotificationManager.INTERRUPTION_FILTER_PRIORITY:
					return RINGER_MODE_DO_NOT_DISTURB;
				default: // INTERRUPTION_FILTER_ALL or unknown
					// fall out into non-Marshmallow case
			}
		}
		// older OS, just use basic ringer modes
		AudioManager audio
			= (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		switch (audio.getRingerMode())
		{
			case AudioManager.RINGER_MODE_SILENT:
				return RINGER_MODE_MUTED;
			case AudioManager.RINGER_MODE_VIBRATE:
				return RINGER_MODE_VIBRATE;
			default:
				return RINGER_MODE_NORMAL;
		}
	}

	public static String getRingerStateName(Context context, int mode) {
		int res;
		switch (mode)
		{
			case RINGER_MODE_NONE:
				res = R.string.ringerModeNone;
				break;
			case RINGER_MODE_NORMAL:
				res = R.string.ringerModeNormal;
				break;
			case RINGER_MODE_VIBRATE:
				res = R.string.ringerModeVibrate;
				break;
			case RINGER_MODE_DO_NOT_DISTURB:
				res = R.string.ringerModeNoDisturb;
				break;
			case RINGER_MODE_MUTED:
				res = R.string.ringerModeMuted;
				break;
			case RINGER_MODE_ALARMS:
				res = R.string.ringerModeAlarms;
				break;
			case RINGER_MODE_SILENT:
				res = R.string.ringerModeSilent;
				break;
			default:
				res = R.string.invalidmode;
		}
		return context.getString(res);
	}

	public static String getEnglishStateName(Context context, int mode) {
		switch (mode)
		{
			case RINGER_MODE_NONE:
				return "unchanged";
			case RINGER_MODE_NORMAL:
				return "normal";
			case RINGER_MODE_VIBRATE:
				return "vibrate";
			case RINGER_MODE_DO_NOT_DISTURB:
				return "do-not-disturb";
			case RINGER_MODE_MUTED:
				return "muted";
			case RINGER_MODE_ALARMS:
				return "alarms only";
			case RINGER_MODE_SILENT:
				return "silent";
			default:
				return "[error-invalid]";
		}
	}

	public static String getRingerSetting(Context context, int mode) {
		return context.getString(R.string.settingTo)
			   + " "
			   + getRingerStateName(context, mode);
	}

	// last user's ringer state
	private static final String USER_RINGER = "userRinger";

	public static void setUserRinger(Context context, int userRinger) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(USER_RINGER, userRinger).commit();
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	public static int getUserRinger(Context context) {
		int userRinger
			=  context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(USER_RINGER, RINGER_MODE_NONE);
		// handle old-style preference
		switch (userRinger)
		{
			case AudioManager.RINGER_MODE_NORMAL:
				userRinger = RINGER_MODE_NORMAL;
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				userRinger = RINGER_MODE_VIBRATE;
				break;
			case AudioManager.RINGER_MODE_SILENT:
				userRinger = RINGER_MODE_MUTED;
				break;
			default: break;
		}
		return userRinger;
	}

	// last ringer state set by this app (to check if user changed it)
	private static final String LAST_RINGER = "lastRinger";

	public static void setLastRinger(Context context, int lastRinger) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(LAST_RINGER, lastRinger).commit();
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	public static int getLastRinger(Context context) {
		int lastRinger
			=  context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(LAST_RINGER, RINGER_MODE_NONE);
		// handle old-style preference
		switch (lastRinger)
		{
			case AudioManager.RINGER_MODE_NORMAL:
				lastRinger = RINGER_MODE_NORMAL;
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				lastRinger = RINGER_MODE_VIBRATE;
				break;
			case AudioManager.RINGER_MODE_SILENT:
				lastRinger = RINGER_MODE_MUTED;
				break;
			default: break;
		}
		return lastRinger;
	}

	// (optional) name of class
	private static final String CLASS_NAME = "className";

	public static void setClassName(
		Context context, int classNum, String className) {
		String prefName = CLASS_NAME + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, className).commit();
	}

	private static String getClassName(SharedPreferences prefs, int classNum) {
		String prefName = CLASS_NAME + String.valueOf(classNum) ;
		return prefs.getString(prefName, ((Integer)classNum).toString());
	}

	public static String getClassName(Context context, int classNum) {
		String prefName = CLASS_NAME + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, ((Integer)classNum).toString());
	}

	private static int getClassNum(SharedPreferences prefs, String className) {
		int n = getNumClasses(prefs);
		for (int classNum = 0; classNum < n; ++classNum)
		{
			if (   isClassUsed(prefs, classNum)
				   && getClassName(prefs, classNum).equals(className))
			{
				return classNum;
			}
		}
		return -1; // className not found
	}

	public static int getClassNum(Context context, String className) {
		return getClassNum(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), className);
	}

	// string required in names of events which can be in class
	private static final String EVENT_NAME = "eventName";

	public static void setEventName(Context context, int classNum, String eventName) {
		String prefName = EVENT_NAME + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, eventName).commit();
	}

	public static String getEventName(Context context, int classNum) {
		String prefName = EVENT_NAME + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// string required in locations of events which can be in class
	private static final String EVENT_LOCATION = "eventLocation";

	public static void setEventLocation(
		Context context, int classNum, String eventLocation) {
		String prefName = EVENT_LOCATION + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, eventLocation).commit();
	}

	public static String getEventLocation(Context context, int classNum) {
		String prefName = EVENT_LOCATION + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// string required in descriptions of events which can be in class
	private static final String EVENT_DESCRIPTION = "eventDescription";

	public static void setEventDescription(
		Context context, int classNum, String eventDescription) {
		String prefName = EVENT_DESCRIPTION + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, eventDescription).commit();
	}

	public static String getEventDescription(Context context, int classNum) {
		String prefName = EVENT_DESCRIPTION + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// colour of events which can be in class
	private static final String EVENT_COLOUR = "eventColour";

	public static void setEventColour(
		Context context, int classNum, String eventColour)
	{
		String prefName = EVENT_COLOUR + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, eventColour).commit();
	}

	public static String getEventColour(Context context, int classNum) {
		String prefName = EVENT_COLOUR + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// calendars whose events can be in class
	private static final String AGENDAS = "agendas";
	private static final String AGENDAS_DELIMITER = ",";

	public static void putCalendars(
		Context context, int classNum, ArrayList<Long> calendarIds)
	{
		String prefName = AGENDAS + String.valueOf(classNum) ;
		// Create the string to save
		StringBuilder agendaList = new StringBuilder();
		boolean first = true;
		for (long id : calendarIds)
		{
			if (first)
				first = false;
			else
				agendaList.append(AGENDAS_DELIMITER);

			agendaList.append(id);
		}
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, agendaList.toString())
			   .commit();
	}

	public static ArrayList<Long> getCalendars(Context context, int classNum) {
		String prefName = AGENDAS + String.valueOf(classNum) ;
		// Create the string to save
		StringTokenizer tokenizer
			= new StringTokenizer(
				context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					.getString(prefName, ""), AGENDAS_DELIMITER);
		ArrayList<Long> calendarIds = new ArrayList<Long>();
		while (tokenizer.hasMoreTokens())
		{
			long nextId = Long.parseLong(tokenizer.nextToken());
			calendarIds.add(nextId);
		}
		return calendarIds;
	}

	// whether busy events, not busy events, or both can be in class
	// note the values here are determined by the order of the radio buttons
	// in DefineClassFragment.java
	public static final int ONLY_BUSY = 0;
	public static final int ONLY_NOT_BUSY = 1;
	public static final int BUSY_AND_NOT = 2;
	private static final String WHETHER_BUSY = "whetherBusy";

	public static void setWhetherBusy(Context context, int classNum, int whetherBusy) {
		String prefName = WHETHER_BUSY + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherBusy).commit();
	}

	public static int getWhetherBusy(Context context, int classNum) {
		String prefName = WHETHER_BUSY + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, BUSY_AND_NOT);
	}

	// whether recurrent events, non-recurrent events, or both can be in class
	// note the values here are determined by the order of the radio buttons
	// in DefineClassFragment.java
	public static final int ONLY_RECURRENT = 0;
	public static final int ONLY_NOT_RECURRENT = 1;
	public static final int RECURRENT_AND_NOT = 2;
	private static final String WHETHER_RECURRENT = "whetherRecurrent";

	public static void setWhetherRecurrent(
		Context context, int classNum, int whetherRecurrent) {
		String prefName = WHETHER_RECURRENT + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherRecurrent).commit();
	}

	public static int getWhetherRecurrent(Context context, int classNum) {
		String prefName = WHETHER_RECURRENT + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName.toString(), RECURRENT_AND_NOT);
	}

	// whether events organised by phone owner, or not, or both can be in class
	// note the values here are determined by the order of the radio buttons
	// in DefineClassFragment.java
	public static final int ONLY_ORGANISER = 0;
	public static final int ONLY_NOT_ORGANISER = 1;
	public static final int ORGANISER_AND_NOT = 2;
	private static final String WHETHER_ORGANISER = "whetherOrganiser";

	public static void setWhetherOrganiser(
		Context context, int classNum, int whetherOrganiser) {
		String prefName = WHETHER_ORGANISER + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherOrganiser).commit();
	}

	public static int getWhetherOrganiser(Context context, int classNum) {
		String prefName = WHETHER_ORGANISER + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, ORGANISER_AND_NOT);
	}

	// whether publicly visible events, private events, or both can be in class
	// note the values here are determined by the order of the radio buttons
	// in DefineClassFragment.java
	public static final int ONLY_PUBLIC = 0;
	public static final int ONLY_PRIVATE = 1;
	public static final int PUBLIC_AND_PRIVATE = 2;
	private static final String WHETHER_PUBLIC = "whetherPublic";

	public static void setWhetherPublic(
		Context context, int classNum, int whetherPublic) {
		String prefName = WHETHER_PUBLIC + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherPublic).commit();
	}

	public static int getWhetherPublic(Context context, int classNum) {
		String prefName = WHETHER_PUBLIC + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, PUBLIC_AND_PRIVATE);
	}

	// whether events with attendees, or without, or both can be in class
	// note the values here are determined by the order of the radio buttons
	// in DefineClassFragment.java
	public static final int ONLY_WITH_ATTENDEES = 0;
	public static final int ONLY_WITHOUT_ATTENDEES = 1;
	public static final int ATTENDEES_AND_NOT = 2;
	private static final String WHETHER_ATTENDEES = "whetherAttendees";

	public
	static void setWhetherAttendees(
		Context context, int classNum, int whetherAttendees) {
		String prefName = WHETHER_ATTENDEES + String.valueOf(classNum) ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherAttendees).commit();
	}

	public static int getWhetherAttendees(Context context, int classNum) {
		String prefName = WHETHER_ATTENDEES + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, ATTENDEES_AND_NOT);
	}

	// ringer state wanted during event of this class
	private static final String RINGER_ACTION = "ringerAction";

	public static void setRingerAction(Context context, int classNum, int action) {
		String prefName = RINGER_ACTION + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, action).commit();
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	public static int getRingerAction(Context context, int classNum)
	{
		String prefName = RINGER_ACTION + (String.valueOf(classNum));
		int action
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					 .getInt(prefName, RINGER_MODE_NONE);
		// handle old-style preference
		switch (action)
		{
			case AudioManager.RINGER_MODE_NORMAL:
				action = RINGER_MODE_NORMAL;
				break;
			case AudioManager.RINGER_MODE_VIBRATE:
				action = RINGER_MODE_VIBRATE;
				break;
			case AudioManager.RINGER_MODE_SILENT:
				action = RINGER_MODE_MUTED;
				break;
			default: break;
		}
		return action;
	}

	// whether to restore ringer after event of this class
	private static final String RESTORE_RINGER = "restoreRinger";

	public static void setRestoreRinger(
		Context context, int classNum, boolean restore)
	{
		String prefName = RESTORE_RINGER + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, restore).commit();
	}

	public static boolean getRestoreRinger(Context context, int classNum) {
		String prefName = RESTORE_RINGER + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// minutes before start time event of this class to take actions
	private static final String BEFORE_MINUTES = "beforeMinutes";

	public static void setBeforeMinutes(
		Context context, int classNum, int beforeMinutes) {
		String prefName = BEFORE_MINUTES + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, beforeMinutes).commit();
	}

	public static int getBeforeMinutes(Context context, int classNum) {
		String prefName = BEFORE_MINUTES + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// required orientation for event of this class to start
	private static final String BEFORE_ORIENTATION = "beforeOrientation";
	public static final int BEFORE_FACE_UP = 1;
	public static final int BEFORE_FACE_DOWN = 2;
	public static final int BEFORE_OTHER_POSITION = 4;
	public static final int BEFORE_ANY_POSITION =   BEFORE_FACE_UP
												  | BEFORE_FACE_DOWN
												  | BEFORE_OTHER_POSITION;

	public static void setBeforeOrientation(
		Context context, int classNum, int beforeOrientation) {
		String prefName = BEFORE_ORIENTATION + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, beforeOrientation).commit();
	}

	public static int getBeforeOrientation(Context context, int classNum) {
		String prefName = BEFORE_ORIENTATION + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, BEFORE_ANY_POSITION);
	}

	// required connection state for event of this class to start
	private static final String BEFORE_CONNECTION = "beforeconnection";
	public static final int BEFORE_WIRELESS_CHARGER = 1;
	public static final int BEFORE_FAST_CHARGER = 2;
	public static final int BEFORE_PLAIN_CHARGER = 4;
	public static final int BEFORE_PERIPHERAL = 8;
	public static final int BEFORE_UNCONNECTED = 16;
	public static final int BEFORE_ANY_CONNECTION
		=   BEFORE_WIRELESS_CHARGER | BEFORE_FAST_CHARGER
		  | BEFORE_PLAIN_CHARGER |  BEFORE_PERIPHERAL
		  | BEFORE_UNCONNECTED;

	public static void setBeforeConnection(
		Context context, int classNum, int beforeConnection) {
		String prefName = BEFORE_CONNECTION + String.valueOf(classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, beforeConnection).commit();
	}

	public static int getBeforeConnection(Context context, int classNum) {
		String prefName = BEFORE_CONNECTION + String.valueOf(classNum);
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, BEFORE_ANY_CONNECTION);
	}

	// minutes after end time event of this class to take actions
	private static final String AFTER_MINUTES = "afterMinutes";

	public static void setAfterMinutes(
		Context context, int classNum, int afterMinutes) {
		String prefName = AFTER_MINUTES + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterMinutes).commit();
	}

	public static int getAfterMinutes(Context context, int classNum) {
		String prefName = AFTER_MINUTES + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// steps moved after end time event of this class to take actions
	private static final String AFTER_STEPS = "afterSteps";

	public static void setAfterSteps(
		Context context, int classNum, int afterSteps) {
		String prefName = AFTER_STEPS + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterSteps).commit();
	}

	public static int getAfterSteps(Context context, int classNum) {
		String prefName = AFTER_STEPS + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// steps target after end time event of this class to take actions
	private static final String TARGET_STEPS = "targetSteps";

	public static void setTargetSteps(
		Context context, int classNum, int afterSteps) {
		String prefName = TARGET_STEPS + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterSteps).commit();
	}

	public static int getTargetSteps(Context context, int classNum) {
		String prefName = TARGET_STEPS + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// metres moved after end time event of this class to take actions
	private static final String AFTER_METRES = "afterMetres";

	public static void setAfterMetres(
		Context context, int classNum, int afterSteps) {
		String prefName = AFTER_METRES + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterSteps).commit();
	}

	public static int getAfterMetres(Context context, int classNum) {
		String prefName = AFTER_METRES + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// Location from which we're waiting to be getAfterMetres(...)
	private static final String LATITUDE = "latitude";
	
	// Not waiting
	public static final Double LATITUDE_IDLE = 360.0;
	
	// Waiting for current location
	public static final Double LATITUDE_FIRST = 300.0;
	
	// Any other value (can be between -90 and +90) is the initial location
	// which is the centre of the geofence.

	public static void setLatitude(
		Context context, int classNum, double x) {
		String prefName = LATITUDE + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, String.valueOf(x)).commit();
	}


	public static Double getLatitude(Context context, int classNum) {
		String prefName = LATITUDE + (String.valueOf(classNum));
		String s = context.getSharedPreferences(
			PREFS_NAME, Context .MODE_PRIVATE)
			.getString(prefName, String.valueOf(LATITUDE_IDLE));
		return new Double(s);
	}

	private static final String LONGITUDE = "longitude";

	public static void setLongitude(
		Context context, int classNum, double x) {
		String prefName = LONGITUDE + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putFloat(prefName, (float)x).commit();
	}

	public static double getLongitude(Context context, int classNum) {
		String prefName = LONGITUDE + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getFloat(prefName, 0);
	}

	// whether to display notification before start of event
	private static final String NOTIFY_START = "notifyStart";

	public static void setNotifyStart(
		Context context, int classNum, boolean notifyStart) {
		String prefName = NOTIFY_START + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, notifyStart).commit();
	}

	public static boolean getNotifyStart(Context context, int classNum) {
		String prefName = NOTIFY_START + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// whether to play a sound before start of event
	private static final String PLAYSOUND_START = "playsoundStart";

	public static void setPlaysoundStart(
		Context context, int classNum, boolean playsoundStart) {
		String prefName = PLAYSOUND_START + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, playsoundStart).commit();

	}

	public static boolean getPlaysoundStart(Context context, int classNum) {
		String prefName = PLAYSOUND_START + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// pathname of sound file to play before start of event
	private static final String SOUNDFILE_START = "soundfileStart";

	public static void setSoundFileStart(
		Context context, int classNum, String filename) {
		String prefName = SOUNDFILE_START + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, filename).commit();
	}

	public static String getSoundFileStart(Context context, int classNum) {
		String prefName = SOUNDFILE_START + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			          .getString(prefName, "");
	}

	// whether to display notification after end of event
	private static final String NOTIFY_END = "notifyEnd";

	public static void setNotifyEnd(Context context, int classNum,
		boolean notifyEnd) {
		String prefName = NOTIFY_END + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, notifyEnd).commit();
	}

	public static boolean getNotifyEnd(Context context, int classNum) {
		String prefName = NOTIFY_END + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// whether to play a sound after end of event
	private static final String PLAYSOUND_END = "playsoundEnd";

	public static void setPlaysoundEnd(
		Context context, int classNum, boolean playsoundEnd) {
		String prefName = PLAYSOUND_END + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, playsoundEnd).commit();

	}

	public static boolean getPlaysoundEnd(Context context, int classNum) {
		String prefName = PLAYSOUND_END + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// pathname of sound file to play after end of event
	private static final String SOUNDFILE_END = "soundfileEnd";

	public static void setSoundFileEnd(
		Context context, int classNum, String filename) {
		String prefName = SOUNDFILE_END + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, filename).commit();
	}

	public static String getSoundFileEnd(Context context, int classNum) {
		String prefName = SOUNDFILE_END + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// is an immediate event of this class currently requested?
	private static final String IS_TRIGGERED = "isTriggered";

	public static void setClassTriggered(
		Context context, int classNum, boolean isTriggered)
	{
		String prefName = IS_TRIGGERED + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, isTriggered).commit();
	}

	public static boolean isClassTriggered(Context context, int classNum) {
		String prefName = IS_TRIGGERED + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// last trigger time + AFTER_MINUTES
	private static final String LAST_TRIGGER_END = "lastTriggerEnd";

	public static void setLastTriggerEnd(
		Context context, int classNum, long endTime)
	{
		String prefName = LAST_TRIGGER_END + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putLong(prefName, endTime).commit();
	}

	public static long getLastTriggerEnd(Context context, int classNum) {
		String prefName = LAST_TRIGGER_END + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getLong(prefName, Long.MIN_VALUE);
	}

	// is an event of this class currently active?
	private static final String IS_ACTIVE = "isActive";

	public static void setClassActive(
		Context context, int classNum, boolean isActive)
	{
		String prefName = IS_ACTIVE + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, isActive).commit();
	}

	public static boolean isClassActive(Context context, int classNum) {
		String prefName = IS_ACTIVE + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// is an event of this class currently waiting after becoming inactive?
	private static final String IS_WAITING = "isWaiting";

	public static void setClassWaiting(
		Context context, int classNum, boolean isWaiting)
	{
		String prefName = IS_WAITING + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, isWaiting).commit();
	}

	public static boolean isClassWaiting(Context context, int classNum) {
		String prefName = IS_WAITING + (String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// name of last active event for this class
	private static final String LAST_ACTIVE_EVENT = "lastActiveEvent";

	public static void setLastActive(
		Context context, int classNum, String name)
	{
		String prefName = LAST_ACTIVE_EVENT + (String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, name).commit();
	}

	public static String getLastActive(Context context, int classNum) {
		String prefName = LAST_ACTIVE_EVENT + String.valueOf(classNum) ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	private static void removeClass(SharedPreferences prefs, int classNum) {
		String num = String.valueOf(classNum);
		prefs.edit().putBoolean(IS_CLASS_USED + (num), false)
			 .putString(CLASS_NAME + (num), "")
			 .putString(EVENT_NAME + (num), "")
			 .putString(EVENT_LOCATION + (num), "")
			 .putString(EVENT_DESCRIPTION + (num), "")
			 .putString(EVENT_COLOUR + (num), "")
			 .putString(AGENDAS + (num), "")
			 .putInt(WHETHER_BUSY + (num), BUSY_AND_NOT)
			 .putInt(WHETHER_RECURRENT + (num), RECURRENT_AND_NOT)
			 .putInt(WHETHER_ORGANISER + (num), ORGANISER_AND_NOT)
			 .putInt(WHETHER_PUBLIC + (num), PUBLIC_AND_PRIVATE)
			 .putInt(WHETHER_ATTENDEES + (num), ATTENDEES_AND_NOT)
			 .putInt(RINGER_ACTION + (num), RINGER_MODE_NONE)
			 .putBoolean(RESTORE_RINGER + (num), false)
			 .putInt(BEFORE_MINUTES + (num), 0)
			 .putInt(BEFORE_ORIENTATION + (num), BEFORE_ANY_POSITION)
			 .putInt(BEFORE_CONNECTION + (num), BEFORE_ANY_CONNECTION)
			 .putInt(AFTER_MINUTES + (num), 0)
			 .putInt(AFTER_STEPS + (num), 0)
			 .putInt(TARGET_STEPS + (num), 0)
			 .putInt(AFTER_METRES + (num), 0)
			 .putString(LATITUDE + (num), "360.0")
			 .putString(LONGITUDE + (num), "360.0")
			 .putBoolean(NOTIFY_START + (num), false)
			 .putBoolean(NOTIFY_END + (num), false)
			 .putBoolean(IS_TRIGGERED + (num), false)
			 .putLong(LAST_TRIGGER_END + (num), Long.MIN_VALUE)
			 .putBoolean(IS_ACTIVE + (num), false)
			 .putBoolean(IS_WAITING + (num), false)
			 .putString(LAST_ACTIVE_EVENT + (num), "")
			 .commit();
	}

	public static void removeClass(Context context, String name) {
		SharedPreferences prefs
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		removeClass(prefs, getClassNum(prefs, name));
	}
}
