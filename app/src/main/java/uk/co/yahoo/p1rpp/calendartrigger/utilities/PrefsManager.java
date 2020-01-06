/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.utilities;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.media.AudioManager;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.StringTokenizer;

import uk.co.yahoo.p1rpp.calendartrigger.R;

public class PrefsManager {

	private static final String PREFS_NAME = "mainPreferences";

	private static final String PREF_VERSIONCODE = "PrefsVersion";

	// This is the version code of the preferences, not the program.
	// If we are a later program version, we will update the preferences
	// and then update the prefs version to match the program version.
	public static void setPrefVersionCode(Context context, String v) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			.putString(PREF_VERSIONCODE, v).commit();
	}

	// The default is the last program version before we started using a
	// preferences version code.
	public static String getPrefVersionCode(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getString(PREF_VERSIONCODE, "3.0");
	}

	private static final String PREF_DEFAULTDIRECTORY = "DefaultDir";

	public static void setDefaultDir(Context context, String dir) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putString(PREF_DEFAULTDIRECTORY, dir).commit();
	}

	public static String getDefaultDir(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(PREF_DEFAULTDIRECTORY, null);
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

	private static final String PREF_LOGCYCLE = "logcycle";

	public static void setLogCycleMode(Context context, boolean IsOn) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putBoolean(PREF_LOGCYCLE, IsOn).commit();
	}

	public static boolean getLogcycleMode(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(PREF_LOGCYCLE, false);
	}

	private static final String PREF_LASTCYCLEDATE = "lastcycledate";

	public static void setLastCycleDate(Context context, long date) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putLong(PREF_LASTCYCLEDATE, date).commit();
	}

	public static long getLastcycleDate(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getLong(PREF_LASTCYCLEDATE, 0);
	}

	private static final String PREF_NEXT_LOCATION = "nextLocation";

	public static void setNextLocationMode(Context context, boolean IsOn) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putBoolean(PREF_NEXT_LOCATION, IsOn).commit();
	}

	public static boolean getNextLocationMode(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(PREF_NEXT_LOCATION, false);
	}

	// This is the last timezone offset that we adjusted for.
	private static final String PREF_LAST_TIMEZONE_OFFSET = "timezoneOffset";

	public static void setLastTimezoneOffset(Context context, int millis) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putInt(PREF_LAST_TIMEZONE_OFFSET, millis).commit();
	}

	public static int getLastTimezoneOffset(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
				      .getInt(PREF_LAST_TIMEZONE_OFFSET, 0);
	}

	// This is the last timezone offset that we saw,
	// but we may not have adjusted for it yet because we wait a bit.
	private static final String PREF_LAST_SEEN_OFFSET = "seenOffset";

	public static void setLastSeenOffset(Context context, int millis) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
				.putInt(PREF_LAST_SEEN_OFFSET, millis).commit();
	}

	public static int getLastSeenOffset(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
				.getInt(PREF_LAST_SEEN_OFFSET, 0);
	}

	// This is used to record when we should update for a time zone change.
	// We wait a bit to allow the Calendar Provider to settle.
	private static final String PREF_TIMEZONE_CHANGED = "timeToUpdate";

	public static void setUpdateTime(Context context, long millis) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
		       .putLong(PREF_TIMEZONE_CHANGED, millis).commit();
	}

	public static long getUpdateTime(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getLong(PREF_TIMEZONE_CHANGED, 0);
	}


	// This works around a nastiness in some Android versions:
	// If we try to mute the ringer, the behaviour depends on the previous state
	//    if it was normal, we get ALARMS_ONLY
	//    but if it was ALARMS_ONLY, we get normal!
	private static final String PREF_MUTE_RESULT = "muteresult";

	public static void setMuteResult(Context context, int state) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putInt(PREF_MUTE_RESULT, state).commit();
	}

	public static int getMuteResult(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(PREF_MUTE_RESULT, PHONE_IDLE);
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
					  .getInt(PREF_STEP_COUNT, STEP_COUNTER_IDLE);
	}

	private final static String PREF_ORIENTATION_STATE = "orientationState";
	public static final int ORIENTATION_IDLE = -2; // inactive
	public static final int ORIENTATION_WAITING = -1; // waiting for sensor
	public static final int ORIENTATION_DONE = 0; // just got a value

	public static void setOrientationState(Context context, int state) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putInt(PREF_ORIENTATION_STATE, state).commit();
	}

	public static int getOrientationState(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(PREF_ORIENTATION_STATE, ORIENTATION_IDLE);
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

	@SuppressLint("SwitchIntDef")
	@TargetApi(android.os.Build.VERSION_CODES.M)
	// Work out what the current ringer state should be from our set of states
	public static int getCurrentMode(Context context)
	{
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
		{
			// Marshmallow or later, has Do Not Disturb mode
			NotificationManager nm =
				(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (nm != null) {
				switch (nm.getCurrentInterruptionFilter()) {
					case NotificationManager.INTERRUPTION_FILTER_NONE:
						return RINGER_MODE_SILENT;
					case NotificationManager.INTERRUPTION_FILTER_ALARMS:
						return RINGER_MODE_ALARMS;
					case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
						return RINGER_MODE_DO_NOT_DISTURB;
					default: // INTERRUPTION_FILTER_ALL or unknown
						// fall out into non-Marshmallow case
				}
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

	public static String getEnglishStateName(int mode) {
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
		String prefName = IS_CLASS_USED + classNum;
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

	// (optional) name of class
	private static final String CLASS_NAME = "className";

	public static void setClassName(
		Context context, int classNum, String className) {
		String prefName = CLASS_NAME + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, className).commit();
	}

	private static String getClassName(SharedPreferences prefs, int classNum) {
		String prefName = CLASS_NAME + classNum ;
		return prefs.getString(prefName, ((Integer)classNum).toString());
	}

	public static String getClassName(Context context, int classNum) {
		String prefName = CLASS_NAME + classNum ;
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
		return getClassNum(context.getSharedPreferences(PREFS_NAME,
			Context.MODE_PRIVATE), className);
	}

	private static final String LAST_IMMEDIATE = "lastImmediate";

	// We store the class name so that we can behave correctly if the class gets deleted
	// and the class number gets re-used.
	public static void setLastImmediate(Context context, int classNum) {
		SharedPreferences prefs
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		if (isClassUsed(prefs, classNum))
		{
			String className = getClassName(prefs, classNum);
			prefs.edit().putString(LAST_IMMEDIATE, className).commit();
		}
		else
		{
			prefs.edit().putString(LAST_IMMEDIATE, "").commit();
		}
	}

	public static int getLastImmediate(Context context) {
		SharedPreferences prefs
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		String className = prefs.getString(LAST_IMMEDIATE, "");
		return getClassNum(prefs, className);
	}

	// string required in names of events which can be in class
	private static final String EVENT_NAME = "eventName";

	public static void removeEventName(Context context, int classNum) {
		String prefName = EVENT_NAME + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().remove(prefName).commit();
	}

	public static String getEventName(Context context, int classNum) {
		String prefName = EVENT_NAME + classNum ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// string required in locations of events which can be in class
	private static final String EVENT_LOCATION = "eventLocation";

	public static void removeEventLocation(
		Context context, int classNum) {
		String prefName = EVENT_LOCATION + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().remove(prefName).commit();
	}

	public static String getEventLocation(Context context, int classNum) {
		String prefName = EVENT_LOCATION + classNum ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// string required in descriptions of events which can be in class
	private static final String EVENT_DESCRIPTION = "eventDescription";

	public static void removeEventDescription(
		Context context, int classNum) {
		String prefName = EVENT_DESCRIPTION + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().remove(prefName).commit();
	}

	public static String getEventDescription(Context context, int classNum) {
		String prefName = EVENT_DESCRIPTION + classNum ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// Comparisons - there may be several of these
	private static final String EVENT_COMPARISON = "eventComparison";

	public static void setEventComparison(
		Context context, int classNum,
		int andIndex, // the number of the AND group
		int orIndex, // the number of the or item within the and group
		int whichName, // 0-> event name, 1-> event location, 2-: event description
		int containsornot, // 0-> contains, 1-> does not contain
		String matchtext) {
		String prefName = EVENT_COMPARISON + classNum
			+ " " + andIndex + " " + orIndex;
		SharedPreferences.Editor spe
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
		if (matchtext.isEmpty())
		{
			spe.remove(prefName);
		}
		else
		{
			spe.putString(prefName, whichName + " " + containsornot + " " + matchtext);
		}
		spe.commit();
	}
	public static String[] getEventComparison(
		Context context, int classNum, int andIndex, int orIndex) {
		String prefName = EVENT_COMPARISON + classNum
			+ " " + andIndex
			+ " " + orIndex;
		String s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getString(prefName, null);
		if (s == null )
		{
			return new String[] {"0", "0", ""};
		}
		else
		{
			return s.split(" ", 3);
		}
	}

	// colour of events which can be in class
	private static final String EVENT_COLOUR = "eventColour";

	public static void setEventColour(
		Context context, int classNum, String eventColour)
	{
		String prefName = EVENT_COLOUR + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, eventColour).commit();
	}

	public static String getEventColour(Context context, int classNum) {
		String prefName = EVENT_COLOUR + classNum ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// calendars whose events can be in class
	private static final String AGENDAS = "agendas";
	private static final String AGENDAS_DELIMITER = ",";

	public static void putCalendars(
		Context context, int classNum, ArrayList<Long> calendarIds)
	{
		String prefName = AGENDAS + classNum ;
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
		String prefName = AGENDAS + classNum ;
		// Create the string to save
		StringTokenizer tokenizer
			= new StringTokenizer(
				context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					.getString(prefName, ""), AGENDAS_DELIMITER);
		ArrayList<Long> calendarIds = new ArrayList<>();
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
		String prefName = WHETHER_BUSY + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putInt(prefName, whetherBusy).commit();
	}

	public static int getWhetherBusy(Context context, int classNum) {
		String prefName = WHETHER_BUSY + classNum ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(prefName, BUSY_AND_NOT);
	}

	// whether all day events, not not all day events, or both can be in class
	// note the values here are determined by the order of the radio buttons
	// in DefineClassFragment.java
	public static final int ONLY_ALL_DAY = 0;
	public static final int ONLY_NOT_ALL_DAY = 1;
	public static final int ALL_DAY_AND_NOT = 2;
	private static final String WHETHER_ALL_DAY = "whetherAllDay";

	public static void setWhetherAllDay(Context context, int classNum, int whetherAllDay) {
		String prefName = WHETHER_ALL_DAY + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putInt(prefName, whetherAllDay).commit();
	}

	public static int getWhetherAllDay(Context context, int classNum) {
		String prefName = WHETHER_ALL_DAY + classNum ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(prefName, ALL_DAY_AND_NOT);
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
		String prefName = WHETHER_RECURRENT + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherRecurrent).commit();
	}

	public static int getWhetherRecurrent(Context context, int classNum) {
		String prefName = WHETHER_RECURRENT + classNum ;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, RECURRENT_AND_NOT);
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
		String prefName = WHETHER_ORGANISER + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherOrganiser).commit();
	}

	public static int getWhetherOrganiser(Context context, int classNum) {
		String prefName = WHETHER_ORGANISER + classNum ;
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
		String prefName = WHETHER_PUBLIC + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherPublic).commit();
	}

	public static int getWhetherPublic(Context context, int classNum) {
		String prefName = WHETHER_PUBLIC + classNum ;
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
		String prefName = WHETHER_ATTENDEES + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherAttendees).commit();
	}

	public static int getWhetherAttendees(Context context, int classNum) {
		String prefName = WHETHER_ATTENDEES + (classNum);
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, ATTENDEES_AND_NOT);
	}

	// ringer state wanted during event of this class
	private static final String RINGER_ACTION = "ringerAction";

	public static void setRingerAction(Context context, int classNum, int action) {
		String prefName = RINGER_ACTION + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, action).commit();
	}

	@TargetApi(android.os.Build.VERSION_CODES.M)
	public static int getRingerAction(Context context, int classNum)
	{
		String prefName = RINGER_ACTION + (classNum);
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
		String prefName = RESTORE_RINGER + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, restore).commit();
	}

	public static boolean getRestoreRinger(Context context, int classNum) {
		String prefName = RESTORE_RINGER + (classNum);
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// minutes before start time event of this class to take actions
	private static final String BEFORE_MINUTES = "beforeMinutes";

	public static void setBeforeMinutes(
		Context context, int classNum, int beforeMinutes) {
		String prefName = BEFORE_MINUTES + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, beforeMinutes).commit();
	}

	public static int getBeforeMinutes(Context context, int classNum) {
		String prefName = BEFORE_MINUTES + (classNum);
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// required orientation for event of this class to start or end
	// originally only used for start, hence misleading names
	private static final String AFTER_ORIENTATION = "afterOrientation";
	private static final String BEFORE_ORIENTATION = "beforeOrientation";
	public static final int BEFORE_FACE_UP = 1;
	public static final int BEFORE_FACE_DOWN = 2;
	public static final int BEFORE_OTHER_POSITION = 4;
	public static final int BEFORE_ANY_POSITION =   BEFORE_FACE_UP
												  | BEFORE_FACE_DOWN
												  | BEFORE_OTHER_POSITION;

	public static void setAfterOrientation(
		Context context, int classNum, int afterOrientation) {
		String prefName = AFTER_ORIENTATION + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterOrientation).commit();
	}

	public static int getAfterOrientation(Context context, int classNum) {
		String prefName = AFTER_ORIENTATION + (classNum);
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, BEFORE_ANY_POSITION);
	}

	public static void setBeforeOrientation(
		Context context, int classNum, int beforeOrientation) {
		String prefName = BEFORE_ORIENTATION + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, beforeOrientation).commit();
	}

	public static int getBeforeOrientation(Context context, int classNum) {
		String prefName = BEFORE_ORIENTATION + (classNum);
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, BEFORE_ANY_POSITION);
	}

	// required connection state for event of this class to start
	// originally only used for start, hence misleading names
	private static final String AFTER_CONNECTION = "afterconnection";
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

	public static void setAfterConnection(
		Context context, int classNum, int afterConnection) {
		String prefName = AFTER_CONNECTION + classNum;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterConnection).commit();
	}

	public static int getAfterConnection(Context context, int classNum) {
		String prefName = AFTER_CONNECTION + classNum;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, BEFORE_ANY_CONNECTION);
	}

	public static void setBeforeConnection(
		Context context, int classNum, int beforeConnection) {
		String prefName = BEFORE_CONNECTION + classNum;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, beforeConnection).commit();
	}

	public static int getBeforeConnection(Context context, int classNum) {
		String prefName = BEFORE_CONNECTION + classNum;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, BEFORE_ANY_CONNECTION);
	}

	// minutes after end time event of this class to take actions
	private static final String AFTER_MINUTES = "afterMinutes";

	public static void setAfterMinutes(
		Context context, int classNum, int afterMinutes) {
		String prefName = AFTER_MINUTES + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterMinutes).commit();
	}

	public static int getAfterMinutes(Context context, int classNum) {
		String prefName = AFTER_MINUTES + (classNum);
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// steps moved after end time event of this class to take actions
	private static final String AFTER_STEPS = "afterSteps";

	public static void setAfterSteps(
		Context context, int classNum, int afterSteps) {
		String prefName = AFTER_STEPS + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterSteps).commit();
	}

	public static int getAfterSteps(Context context, int classNum) {
		String prefName = AFTER_STEPS + (classNum);
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// steps target after end time event of this class to take actions
	private static final String TARGET_STEPS = "targetSteps";

	public static void setTargetSteps(
		Context context, int classNum, int afterSteps) {
		String prefName = TARGET_STEPS + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterSteps).commit();
	}

	public static int getTargetSteps(Context context, int classNum) {
		String prefName = TARGET_STEPS + (classNum);
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// metres moved after end time event of this class to take actions
	private static final String AFTER_METRES = "afterMetres";

	public static void setAfterMetres(
		Context context, int classNum, int afterSteps) {
		String prefName = AFTER_METRES + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterSteps).commit();
	}

	public static int getAfterMetres(Context context, int classNum) {
		String prefName = AFTER_METRES + (classNum);
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
		String prefName = LATITUDE + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, String.valueOf(x)).commit();
	}


	public static Double getLatitude(Context context, int classNum) {
		String prefName = LATITUDE + (classNum);
		String s = context.getSharedPreferences(
			PREFS_NAME, Context .MODE_PRIVATE)
			.getString(prefName, String.valueOf(LATITUDE_IDLE));
		return Double.valueOf(s);
	}

	private static final String LONGITUDE = "longitude";

	public static void setLongitude(
		Context context, int classNum, double x) {
		String prefName = LONGITUDE + classNum;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putFloat(prefName, (float)x).commit();
	}

	public static double getLongitude(Context context, int classNum) {
		String prefName = LONGITUDE + classNum;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getFloat(prefName, 0);
	}

	// whether to display notification before start of event
	private static final String NOTIFY_START = "notifyStart";

	public static void setNotifyStart(
		Context context, int classNum, boolean notifyStart) {
		String prefName = NOTIFY_START + classNum;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, notifyStart).commit();
	}

	public static boolean getNotifyStart(Context context, int classNum) {
		String prefName = NOTIFY_START + classNum;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// whether to play a sound before start of event
	private static final String PLAYSOUND_START = "playsoundStart";

	public static void setPlaysoundStart(
		Context context, int classNum, boolean playsoundStart) {
		String prefName = PLAYSOUND_START + classNum;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, playsoundStart).commit();

	}

	public static boolean getPlaysoundStart(Context context, int classNum) {
		String prefName = PLAYSOUND_START + classNum;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// pathname of sound file to play before start of event
	private static final String SOUNDFILE_START = "soundfileStart";

	public static void setSoundFileStart(
		Context context, int classNum, String filename) {
		String prefName = SOUNDFILE_START + classNum;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, filename).commit();
	}

	public static String getSoundFileStart(Context context, int classNum) {
		String prefName = SOUNDFILE_START + classNum;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			          .getString(prefName, "");
	}

	// whether to display notification after end of event
	private static final String NOTIFY_END = "notifyEnd";

	public static void setNotifyEnd(Context context, int classNum,
		boolean notifyEnd) {
		String prefName = NOTIFY_END + classNum;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, notifyEnd).commit();
	}

	public static boolean getNotifyEnd(Context context, int classNum) {
		String prefName = NOTIFY_END + classNum;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// whether to play a sound after end of event
	private static final String PLAYSOUND_END = "playsoundEnd";

	public static void setPlaysoundEnd(
		Context context, int classNum, boolean playsoundEnd) {
		String prefName = PLAYSOUND_END + classNum;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, playsoundEnd).commit();

	}

	public static boolean getPlaysoundEnd(Context context, int classNum) {
		String prefName = PLAYSOUND_END + classNum;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// pathname of sound file to play after end of event
	private static final String SOUNDFILE_END = "soundfileEnd";

	public static void setSoundFileEnd(
		Context context, int classNum, String filename) {
		String prefName = SOUNDFILE_END + classNum;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, filename).commit();
	}

	public static String getSoundFileEnd(Context context, int classNum) {
		String prefName = SOUNDFILE_END + classNum;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// Distinguish messages at start and end of events
	public static final int SEND_MESSAGE_AT_START = 0;
	public static final int SEND_MESSAGE_AT_END = 1;

	// Message sending method(s)
	private static final String SEND_MESSAGE = "SendMessage";
	public static final int SEND_NO_MESSAGE = 0; // Don't send message
	public static final int SEND_MESSAGE_NOWHERE = 1; // Send enabled but no method
	public static final int SEND_MESSAGE_EMAIL = 2; // email
	public static final int SEND_MESSAGE_EMAIL_OR_SMS = 3; // try email then SMS
	public static final int SEND_MESSAGE_SMS = 4; // SMS
	public static final int SEND_MESSAGE_SMS_OR_EMAIL = 5; // Try SMS tthen email

	public static void setMessageType(
		Context context, int classNum, int startOrEnd, int type) {
		String prefName = SEND_MESSAGE + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putInt(prefName, type).commit();
	}

	public static int getMessageType(Context context, int classNum, int startOrEnd) {
		String prefName = SEND_MESSAGE + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(prefName, SEND_NO_MESSAGE);
	}

	// Message destinations
	private static final String MESSAGE_ADDRESS = "MessageAddress";
	private static final String MESSAGE_NUMBER = "MessageNumber";
	private static final String MESSAGE_CONTACT = "MessageContact";
	private static final String MESSAGE_EXTRACT = "MessageExtract";

	// Details for extracting contact name from event name
	private static final String MESSAGE_FIRST_COUNT = "MessageFirstCount";
	private static final String MESSAGE_FIRST_DIRECTION
		= "MessageFirstDirection";
	private static final String MESSAGE_LAST_COUNT = "MessageLastCount";
	private static final String MESSAGE_LAST_DIRECTION
		= "MessageLastDirection";
	private static final String MESSAGE_TRIM = "MessageTrim";
	public static final int MESSAGE_DIRECTION_RIGHT = 0;
	public static final int MESSAGE_DIRECTION_LEFT = 1;

	public static void setMessageAddress(
		Context context, int classNum, int startOrEnd, String addressee) {
		String prefName = MESSAGE_ADDRESS + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putString(prefName, addressee).commit();
	}

	public static String getMessageAddress(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_ADDRESS + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getString(prefName, null);
	}

	public static void setMessageNumber(
		Context context, int classNum, int startOrEnd, String addressee) {
		String prefName = MESSAGE_NUMBER + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putString(prefName, addressee).commit();
	}

	public static String getMessageNumber(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_NUMBER + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getString(prefName, null);
	}

	public static void setMessageContact(
		Context context, int classNum, int startOrEnd, String addressee) {
		String prefName = MESSAGE_CONTACT + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putString(prefName, addressee).commit();
	}

	public static String getMessageContact(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_CONTACT + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getString(prefName, null);
	}

	public static void setMessageExtract(
		Context context, int classNum, int startOrEnd, boolean enabled) {
		String prefName = MESSAGE_EXTRACT + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putBoolean(prefName, enabled).commit();
	}

	public static boolean getMessageExtract(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_EXTRACT + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getBoolean(prefName, false);
	}

	public static void setMessageFirstCount(
		Context context, int classNum, int startOrEnd, int count) {
		String prefName = MESSAGE_FIRST_COUNT + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putInt(prefName, count).commit();
	}

	public static int getMessageFirstCount(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_FIRST_COUNT + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(prefName, 0);
	}

	public static void setMessageLastCount(
		Context context, int classNum, int startOrEnd, int count) {
		String prefName = MESSAGE_LAST_COUNT + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putInt(prefName, count).commit();
	}

	public static int getMessageLastCount(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_LAST_COUNT + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(prefName, 0);
	}

	public static void setMessageFirstDir(
		Context context, int classNum, int startOrEnd, int dir) {
		String prefName = MESSAGE_FIRST_DIRECTION + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putInt(prefName, dir).commit();
	}

	public static int getMessageFirstDir(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_FIRST_DIRECTION + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(prefName, MESSAGE_DIRECTION_RIGHT);
	}

	public static void setMessageLastDir(
		Context context, int classNum, int startOrEnd, int dir) {
		String prefName = MESSAGE_LAST_DIRECTION + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putInt(prefName, dir).commit();
	}

	public static int getMessageLastDir(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_LAST_DIRECTION + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(prefName, MESSAGE_DIRECTION_RIGHT);
	}

	// If true, trim "," or "'s" from first and last name extracted from event name
	public static void setMessageTrim(
		Context context, int classNum, int startOrEnd, boolean trim) {
		String prefName = MESSAGE_TRIM + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putBoolean(prefName, trim).commit();
	}

	public static boolean getMessageTrim(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_TRIM + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getBoolean(prefName, false);
	}

	private static final String MESSAGE_TEXT_TYPE = "MessageTextType";
	public static final int MESSAGE_TEXT_NONE = 0;
	public static final int MESSAGE_TEXT_CLASSNAME = 1;
	public static final int MESSAGE_TEXT_EVENTNAME = 2;
	public static final int MESSAGE_TEXT_EVENTDESCRIPTION = 3;
	public static final int MESSAGE_TEXT_LITERAL = 4;
	private static final String MESSAGE_LITERAL_TEXT = "MessageLiteralText";

	public static void setMessageTextType(
		Context context, int classNum, int startOrEnd, int type) {
		String prefName = MESSAGE_TEXT_TYPE + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putInt(prefName, type).commit();
	}

	public static int getMessageTextType(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_TEXT_TYPE + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(prefName, MESSAGE_TEXT_NONE);
	}

	public static void setMessageLiteral(
		Context context, int classNum, int startOrEnd, String data) {
		String prefName = MESSAGE_LITERAL_TEXT + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putString(prefName, data).commit();
	}

	public static String getMessageLiteral(
		Context context, int classNum, int startOrEnd) {
		String prefName = MESSAGE_LITERAL_TEXT + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getString(prefName, "");
	}

	private static final String SUBJECT_TEXT_TYPE = "SubjectTextType";
	public static final int SUBJECT_TEXT_NONE = 0;
	public static final int SUBJECT_TEXT_CLASSNAME = 1;
	public static final int SUBJECT_TEXT_EVENTNAME = 2;
	public static final int SUBJECT_TEXT_EVENTDESCRIPTION = 3;
	public static final int SUBJECT_TEXT_LITERAL = 4;
	private static final String SUBJECT_LITERAL_TEXT = "SubjectLiteralText";

	public static void setSubjectTextType(
		Context context, int classNum, int startOrEnd, int type) {
		String prefName = SUBJECT_TEXT_TYPE + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putInt(prefName, type).commit();
	}

	public static int getSubjectTextType(
		Context context, int classNum, int startOrEnd) {
		String prefName = SUBJECT_TEXT_TYPE + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getInt(prefName, SUBJECT_TEXT_NONE);
	}

	public static void setSubjectLiteral(
		Context context, int classNum, int startOrEnd, String data) {
		String prefName = SUBJECT_LITERAL_TEXT + classNum + "#" + startOrEnd;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().putString(prefName, data).commit();
	}

	public static String getSubjectLiteral(
		Context context, int classNum, int startOrEnd) {
		String prefName = SUBJECT_LITERAL_TEXT + classNum + "#" + startOrEnd;
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getString(prefName, "");
	}

	// is an immediate event of this class currently requested?
	private static final String IS_TRIGGERED = "isTriggered";

	public static void removeTriggered(Context context, int classNum) {
		String prefName = IS_TRIGGERED + classNum ;
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit().remove(prefName).commit();
	}

	// last trigger time + AFTER_MINUTES
	private static final String LAST_TRIGGER_END = "lastTriggerEnd";

	public static void removeLastTriggerEnd(Context context, int classNum)
	{
		String prefName = LAST_TRIGGER_END + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().remove(prefName).commit();
	}

	// is an event of this class currently waiting after becoming inactive?
	private static final String IS_WAITING = "isWaiting";

	public static void removeClassWaiting(Context context, int classNum)
	{
		String prefName = IS_WAITING + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().remove(prefName).commit();
	}

	// name of last active event for this class
	private static final String LAST_ACTIVE_EVENT = "lastActiveEvent";

	public static void removeLastActive(
		Context context, int classNum)
	{
		String prefName = LAST_ACTIVE_EVENT + (classNum);
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().remove(prefName).commit();
	}

	private static void removeClass(SharedPreferences prefs, int classNum) {
		SharedPreferences.Editor editor = prefs.edit();
		for (int andIndex = 0; true; ++andIndex) {
			int orIndex;
			for (orIndex = 0; true; ++orIndex) {
				String prefName = EVENT_COMPARISON + classNum
					+ " " + andIndex + " " + orIndex;
				String s = prefs.getString(prefName, null);
				if (s == null) { break; }
				editor.remove(prefName);
				if (s.split(" ", 3)[2].isEmpty()) { break; }
			}
			if (orIndex == 0) {break; }
		}
		editor.putBoolean(IS_CLASS_USED + classNum, false)
			  .remove(CLASS_NAME + classNum)
			  .remove(EVENT_COLOUR + classNum)
			  .remove(AGENDAS + classNum)
			  .remove(WHETHER_BUSY + classNum)
			  .remove(WHETHER_ALL_DAY + classNum)
			  .remove(WHETHER_RECURRENT + classNum)
			  .remove(WHETHER_ORGANISER + classNum)
			  .remove(WHETHER_PUBLIC + classNum)
			  .remove(WHETHER_ATTENDEES + classNum)
			  .remove(RINGER_ACTION + classNum)
			  .remove(RESTORE_RINGER + classNum)
			  .remove(BEFORE_MINUTES + classNum)
			  .remove(BEFORE_ORIENTATION + classNum)
			  .remove(BEFORE_CONNECTION + classNum)
			  .remove(AFTER_MINUTES + classNum)
			  .remove(AFTER_STEPS + classNum)
			  .remove(TARGET_STEPS + classNum)
			  .remove(AFTER_METRES + classNum)
			  .remove(LATITUDE + classNum)
			  .remove(LONGITUDE + classNum)
			  .remove(NOTIFY_START + classNum)
			  .remove(NOTIFY_END + classNum)
			  .remove(SEND_MESSAGE + classNum + "#0")
			  .remove(SEND_MESSAGE + classNum + "#1")
			  .remove(MESSAGE_ADDRESS + classNum + "#0")
			  .remove(MESSAGE_ADDRESS + classNum + "#1")
			  .remove(MESSAGE_NUMBER + classNum + "#0")
			  .remove(MESSAGE_NUMBER + classNum + "#1")
			  .remove(MESSAGE_CONTACT + classNum + "#0")
			  .remove(MESSAGE_CONTACT + classNum + "#1")
			  .remove(MESSAGE_EXTRACT + classNum + "#0")
			  .remove(MESSAGE_EXTRACT + classNum + "#1")
			  .remove(MESSAGE_FIRST_COUNT + classNum + "#0")
			  .remove(MESSAGE_FIRST_COUNT + classNum + "#1")
			  .remove(MESSAGE_LAST_COUNT + classNum + "#0")
			  .remove(MESSAGE_LAST_COUNT + classNum + "#1")
			  .remove(MESSAGE_FIRST_DIRECTION + classNum + "#0")
			  .remove(MESSAGE_FIRST_DIRECTION + classNum + "#1")
			  .remove(MESSAGE_LAST_DIRECTION + classNum + "#0")
			  .remove(MESSAGE_LAST_DIRECTION + classNum + "#1")
			  .remove(MESSAGE_TRIM + classNum + "#0")
			  .remove(MESSAGE_TRIM + classNum + "#1")
			  .remove(MESSAGE_TEXT_TYPE + classNum + "#0")
			  .remove(MESSAGE_TEXT_TYPE + classNum + "#1")
			  .remove(MESSAGE_LITERAL_TEXT + classNum + "#0")
			  .remove(MESSAGE_LITERAL_TEXT + classNum + "#1")
			  .remove(IS_TRIGGERED + classNum)
			  .remove(LAST_TRIGGER_END + classNum)
			  .remove(IS_WAITING + classNum)
			  .remove(LAST_ACTIVE_EVENT + classNum)
			  .commit();
	}

	public static void removeClass(Context context, String name) {
		SharedPreferences prefs
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		removeClass(prefs, getClassNum(prefs, name));
	}

	// We assume you don't reinstall when CalendarTrigger is doing something
	// so some state variables which are stored in the preferences aren't
	// saved and restored on a reinstall.
	private static void saveClassSettings(
		Context context, PrintStream out, int i) {
		SharedPreferences prefs =
			context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		out.printf("Class=%s\n", PrefsManager.getClassName(context, i));
		for (int andIndex = 0; true; ++andIndex) {
			int orIndex;
			for (orIndex = 0; true; ++orIndex) {
				String[] ec = getEventComparison(context, i, andIndex, orIndex);
				if (ec[2].isEmpty()) { break; }
				out.printf("eventComparison=%s %s %s %s %s %s\n", i, andIndex, orIndex,
					Integer.valueOf(ec[0]), Integer.valueOf(ec[1]), ec[2]);
			}
			if (orIndex == 0) {break; }
		}
		out.printf("eventColour=%s\n",
				   PrefsManager.getEventColour(context, i));
		String prefName = AGENDAS + i;
		out.printf("agendas=%s\n", prefs.getString(prefName, ""));
		out.printf("whetherBusy=%d\n",
			PrefsManager.getWhetherBusy(context, i));
		out.printf("whetherAllDay=%d\n",
			PrefsManager.getWhetherAllDay(context, i));
		out.printf("whetherRecurrent=%d\n",
				   PrefsManager.getWhetherRecurrent(context, i));
		out.printf("whetherOrganiser=%d\n",
				   PrefsManager.getWhetherOrganiser(context, i));
		out.printf("whetherPublic=%d\n",
				   PrefsManager.getWhetherPublic(context, i));
		out.printf("whetherAttendees=%d\n",
				   PrefsManager.getWhetherAttendees(context, i));
		out.printf("ringerAction=%d\n",
				   PrefsManager.getRingerAction(context, i));
		out.printf("restoreRinger=%s\n",
				   getRestoreRinger(context, i) ? "true" : "false");
		out.printf("afterMinutes=%d\n",
				   PrefsManager.getAfterMinutes(context, i));
		out.printf("beforeMinutes=%d\n",
				   PrefsManager.getBeforeMinutes(context, i));
		out.printf("afterOrientation=%d\n",
				   PrefsManager.getAfterOrientation(context, i));
		out.printf("beforeOrientation=%d\n",
				   PrefsManager.getBeforeOrientation(context, i));
		out.printf("afterconnection=%d\n",
				   PrefsManager.getAfterConnection(context, i));
		out.printf("beforeconnection=%d\n",
				   PrefsManager.getBeforeConnection(context, i));
		out.printf("afterSteps=%d\n",
				   PrefsManager.getAfterSteps(context, i));
		// targetSteps not preserved across reinstall
		out.printf("afterMetres=%d\n",
				   PrefsManager.getAfterMetres(context, i));
		// latitude and longitude not preserved across reinstall
		out.printf("notifyStart=%s\n",
				   getNotifyStart(context, i) ? "true" : "false");
		out.printf("playsoundStart=%s\n",
				   getPlaysoundStart(context, i) ? "true" : "false");
		out.printf("soundfileStart=%s\n",
				   PrefsManager.getSoundFileStart(context, i));
		out.printf("notifyEnd=%s\n",
				   getNotifyEnd(context, i) ? "true" : "false");
		out.printf("playsoundEnd=%s\n",
				   getPlaysoundEnd(context, i) ? "true" : "false");
		out.printf("soundfileStart=%s\n",
				   PrefsManager.getSoundFileStart(context, i));
		out.printf("soundfileEnd=%s\n",
				   PrefsManager.getSoundFileEnd(context, i));
		out.printf("StartMessageType=%d\n",
			PrefsManager.getMessageType(context, i, SEND_MESSAGE_AT_START));
		out.printf("EndMessageType=%d\n",
			PrefsManager.getMessageType(context, i, SEND_MESSAGE_AT_END));
		String s = PrefsManager.getMessageAddress(context, i, SEND_MESSAGE_AT_START);
		if (s == null) {
			out.print("StartMessageAddressNull\n");
		}
		else
		{
			out.printf("StartMessageAddress=%s\n", s);
		}
		s = PrefsManager.getMessageAddress(context, i, SEND_MESSAGE_AT_END);
		if (s == null) {
			out.print("EndMessageAddressNull\n");
		}
		else
		{
			out.printf("EndMessageAddress=%s\n", s);
		}
		s = PrefsManager.getMessageNumber(context, i, SEND_MESSAGE_AT_START);
		if (s == null) {
			out.print("StartMessageNumberNull\n");
		}
		else {
			out.printf("StartMessageNumber=%s\n", s);
		}
		s = PrefsManager.getMessageNumber(context, i, SEND_MESSAGE_AT_END);
		if (s == null) {
			out.print("EndMessageNumberNull\n");
		}
		else {
			out.printf("EndMessageNumber=%s\n", s);
		}
		s = PrefsManager.getMessageContact(context, i, SEND_MESSAGE_AT_START);
		if (s == null) {
			out.print("StartMessageContactNull\n");
		}
		else {
			out.printf("StartMessageContact=%s\n", s);
		}
		s = PrefsManager.getMessageContact(context, i, SEND_MESSAGE_AT_END);
		if (s == null) {
			out.print("EndMessageContactNull\n");
		}
		else {
			out.printf("EndMessageContact=%s\n", s);
		}
		out.printf("StartMessageExtract=%b\n",
			PrefsManager.getMessageExtract(context, i, SEND_MESSAGE_AT_START));
		out.printf("EndMessageExtract=%b\n",
			PrefsManager.getMessageExtract(context, i, SEND_MESSAGE_AT_END));
		out.printf("StartMessageFirstCount=%d\n",
			PrefsManager.getMessageFirstCount(context, i, SEND_MESSAGE_AT_START));
		out.printf("EndMessageFirstCount=%d\n",
			PrefsManager.getMessageFirstCount(context, i, SEND_MESSAGE_AT_END));
		out.printf("StartMessageLastCount=%d\n",
			PrefsManager.getMessageLastCount(context, i, SEND_MESSAGE_AT_START));
		out.printf("EndMessageLastCount=%d\n",
			PrefsManager.getMessageLastCount(context, i, SEND_MESSAGE_AT_END));
		out.printf("StartMessageFirstDir=%d\n",
			PrefsManager.getMessageFirstDir(context, i, SEND_MESSAGE_AT_START));
		out.printf("EndMessageFirstDir=%d\n",
			PrefsManager.getMessageFirstDir(context, i, SEND_MESSAGE_AT_END));
		out.printf("StartMessageLastDir=%d\n",
			PrefsManager.getMessageLastDir(context, i, SEND_MESSAGE_AT_START));
		out.printf("EndMessageLastDir=%d\n",
			PrefsManager.getMessageLastDir(context, i, SEND_MESSAGE_AT_END));
		out.printf("StartMessageTrim=%b\n",
			PrefsManager.getMessageTrim(context, i, SEND_MESSAGE_AT_START));
		out.printf("EndMessageTrim=%b\n",
			PrefsManager.getMessageTrim(context, i, SEND_MESSAGE_AT_END));
		out.printf("StartMessageTextType=%d\n",
			PrefsManager.getMessageTextType(context, i, SEND_MESSAGE_AT_START));
		out.printf("EndMessageTextType=%d\n",
			PrefsManager.getMessageTextType(context, i, SEND_MESSAGE_AT_END));
		s = PrefsManager.getMessageLiteral(context, i, SEND_MESSAGE_AT_START);
		out.printf("StartMessageLiteral=%s\n", s.replace("%", "%p")
												.replace("\"", "%q")
												.replace("\n", "%n"));
		s = PrefsManager.getMessageLiteral(context, i, SEND_MESSAGE_AT_END);
		out.printf("EndMessageLiteral=%s\n", s.replace("%", "%p")
											  .replace("\"", "%q")
											  .replace("\n", "%n"));
		out.printf("StartSubjectTextType=%d\n",
			PrefsManager.getSubjectTextType(context, i, SEND_MESSAGE_AT_START));
		out.printf("EndSubjectTextType=%d\n",
			PrefsManager.getSubjectTextType(context, i, SEND_MESSAGE_AT_END));
		 s = PrefsManager.getSubjectLiteral(context, i, SEND_MESSAGE_AT_START);
		out.printf("StartSubjectLiteral=%s\n", s.replace("%", "%p")
			.replace("\"", "%q")
			.replace("\n", "%n"));
		s = PrefsManager.getSubjectLiteral(context, i, SEND_MESSAGE_AT_END);
		out.printf("EndSubjectLiteral=%s\n", s.replace("%", "%p")
			.replace("\"", "%q")
			.replace("\n", "%n"));
	}

	public static void saveSettings(Context context, PrintStream out) {
		try
		{
			PackageInfo packageInfo = context.getPackageManager()
											 .getPackageInfo(
												 context.getPackageName(),
												 PackageManager.GET_SIGNATURES);
			for (Signature signature : packageInfo.signatures)
			{
				out.printf("Signature=%s\n", signature.toCharsString());
			}
		} catch (Exception e) {
			String s = R.string.packageinfofail + " " +
					 e.getCause().toString() + " " +
					 e.getMessage();
			Toast.makeText(context, s, Toast.LENGTH_LONG).show();
		}
		// DefaultDir not preserved across reinstall
		out.printf("logging=%s\n",
				   PrefsManager.getLoggingMode(context) ? "true" : "false");
		out.printf("logcycle=%s\n",
			PrefsManager.getLogcycleMode(context) ? "true" : "false");
		// lastcycledate will be rest to the Epoch
		out.printf("nextLocation=%s\n",
				   getNextLocationMode(context) ? "true" : "false");
		out.printf("timezoneoffset=%d\n",
				   getLastTimezoneOffset(context));
		out.printf("seenOffset=%d\n",
				   getLastSeenOffset(context));
		// timeToUpdate reset to the epoch
		// muteresult not preserved across reinstall
		// phone state not preserved across reinstall
		// notifiedCannotReadPhoneState not preserved across reinstall
		// locationActive not preserved across reinstall
		// stepCounter not preserved across reinstall
		// orientationState not preserved across reinstall
		// lastInvocationTime only used for debugging, not preserved
		// lastAlarmTime only used for debugging, not preserved
		out.printf("userringer=%d\n",
				   getUserRinger(context));
		out.printf("lastRinger=%d\n",
			getLastRinger(context));
		int num = PrefsManager.getNumClasses(context);
		for (int i = 0; i < num; ++i) {
			if (PrefsManager.isClassUsed(context, i))
			{
				saveClassSettings(context, out, i);
			}
		}
		out.printf("lastImmediate=%d\n", getLastImmediate(context));
	}

	// We only try to read what we wrote, so we mostly ignore anything that
	// we don't understand, and we only check if booleans are "true" or not
	public static void loadSettings(Context context, BufferedReader in) {
		SharedPreferences prefs =
			context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		prefs.edit().clear().commit();
		int i = -1;
		int andIndex = 0;
		try {
			while (true) {
				String s = in.readLine();
				if (s == null) {break; }
				String[] parts = s.split("=", 2);
				if (parts[0].compareTo("logging") == 0)
				{
					if (parts[1].compareTo("true") == 0)
					{
						PrefsManager.setLoggingMode(context,true);
					}
					else
					{
						PrefsManager.setLoggingMode(context,false);
					}
				}
				if (parts[0].compareTo("logcycle") == 0)
				{
					if (parts[1].compareTo("true") == 0)
					{
						PrefsManager.setLogCycleMode(context,true);
					}
					else
					{
						PrefsManager.setLogCycleMode(context,false);
					}
				}
				else if (parts[0].compareTo("nextLocation") == 0)
				{
					if (parts[1].compareTo("true") == 0)
					{
						PrefsManager.setNextLocationMode(context,true);
					}
					else
					{
						PrefsManager.setNextLocationMode(context,false);
					}
				}
				else if (parts[0].compareTo("timezoneoffset") == 0)
				{
					try {
						PrefsManager.setLastTimezoneOffset(
							context, Integer.decode(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("seenOffset") == 0)
				{
					try {
						PrefsManager.setLastSeenOffset(
							context, Integer.decode(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("userringer") == 0)
				{
					try {
						PrefsManager.setUserRinger(
							context, Integer.decode(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("lastRinger") == 0)
				{
					try {
						PrefsManager.setLastRinger(
							context, Integer.decode(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("lastImmediate") == 0)
				{
					try {
						PrefsManager.setLastImmediate(context, Integer.decode(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("Class") == 0)
				{
					++i;
					i = PrefsManager.getNewClass(context);
					PrefsManager.setClassName(context, i, parts[1]);
					andIndex = 0;
				}
				else if (parts[0].compareTo("eventName") == 0)
				{
					PrefsManager.setEventComparison(context, i, andIndex,
						0, 0, 0, parts[1]);
					++andIndex;
				}
				else if (parts[0].compareTo("eventLocation") == 0)
				{
					PrefsManager.setEventComparison(context, i, andIndex,
						0, 1, 0, parts[1]);
					++andIndex;
				}
				else if (parts[0].compareTo("eventDescription") == 0)
				{
					PrefsManager.setEventComparison(context, i, andIndex,
						0, 2, 0, parts[1]);
					++andIndex;
				}
				else if (parts[0].compareTo("eventComparison") == 0)
				{
					parts = parts[1].split(" ", 6);
					PrefsManager.setEventComparison(context,
						Integer.valueOf(parts[0]),
						Integer.valueOf(parts[1]),
						Integer.valueOf(parts[2]),
						Integer.valueOf(parts[3]),
						Integer.valueOf(parts[4]),
						parts[5]);
				}
				else if (parts[0].compareTo("eventColour") == 0)
				{
					PrefsManager.setEventColour(context, i, parts[1]);
				}
				else if (parts[0].compareTo("agendas") == 0)
				{
					prefs.edit().putString(
						AGENDAS + i, parts[1]).commit();
				}
				else if (parts[0].compareTo("whetherBusy") == 0)
				{
					try
					{
						PrefsManager.setWhetherBusy(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) { }
				}
				else if (parts[0].compareTo("whetherAllDay") == 0)
				{
					try
					{
						PrefsManager.setWhetherAllDay(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) { }
				}
				else if (parts[0].compareTo("whetherRecurrent") == 0)
				{
					try
					{
						
						PrefsManager.setWhetherRecurrent(
							context, i, Integer.valueOf(parts[1]));
					}
					catch (NumberFormatException ignore) { }
				}
				else if (parts[0].compareTo("whetherOrganiser") == 0)
				{
					try
					{
						PrefsManager.setWhetherOrganiser(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("whetherPublic") == 0)
				{
					try
					{
						
						PrefsManager.setWhetherPublic(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("whetherAttendees") == 0)
				{
					try
					{
						
						PrefsManager.setWhetherAttendees(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("ringerAction=") == 0)
				{
					try
					{

						PrefsManager.setRingerAction(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("restoreRinger=") == 0)
				{
					if (parts[1].compareTo("true") == 0)
					{
						PrefsManager.setRestoreRinger(context, i,true);
					}
					else
					{
						PrefsManager.setRestoreRinger(context, i,false);
					}
				}
				else if (parts[0].compareTo("afterMinutes") == 0)
				{
					try
					{
						PrefsManager.setAfterMinutes(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("beforeMinutes") == 0)
				{
					try
					{
						PrefsManager.setBeforeMinutes(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("afterOrientation") == 0)
				{
					try
					{
						PrefsManager.setAfterOrientation(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("beforeOrientation") == 0)
				{
					try
					{
						PrefsManager.setBeforeOrientation(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("afterconnection") == 0)
				{
					try
					{
						PrefsManager.setAfterConnection(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("beforeconnection") == 0)
				{
					try
					{
						PrefsManager.setBeforeConnection(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("afterSteps") == 0)
				{
					try
					{
						PrefsManager.setAfterSteps(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("afterMetres") == 0)
				{
					try
					{
						PrefsManager.setAfterMetres(
							context, i, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("notifyStart") == 0)
				{
					if (parts[1].compareTo("true") == 0)
					{
						PrefsManager.setNotifyStart(context, i,true);
					}
					else
					{
						PrefsManager.setNotifyStart(context, i,false);
					}
				}
				else if (parts[0].compareTo("notifyEnd=") == 0)
				{
					if (parts[1].compareTo("true") == 0)
					{
						PrefsManager.setNotifyEnd(context, i,true);
					}
					else
					{
						PrefsManager.setNotifyEnd(context, i,false);
					}
				}
				else if (parts[0].compareTo("playsoundStart") == 0)
				{
					if (parts[1].compareTo("true") == 0)
					{
						PrefsManager.setPlaysoundStart(context, i,true);
					}
					else
					{
						PrefsManager.setPlaysoundStart(context, i,false);
					}
				}
				else if (parts[0].compareTo("playsoundEnd") == 0)
				{
					if (parts[1].compareTo("true") == 0)
					{
						PrefsManager.setPlaysoundEnd(context, i,true);
					}
					else
					{
						PrefsManager.setPlaysoundEnd(context, i,false);
					}
				}
				else if (parts[0].compareTo("soundfileStart") == 0)
				{
					PrefsManager.setSoundFileStart(context, i, parts[1]);
				}
				else if (parts[0].compareTo("soundfileEnd") == 0)
				{
					PrefsManager.setSoundFileEnd(context, i, parts[1]);
				}
				else if (parts[0].compareTo("StartMessageType") == 0)
				{
					try
					{
						PrefsManager.setMessageType(
							context, i, SEND_MESSAGE_AT_START, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("EndMessageType") == 0)
				{
					try
					{
						PrefsManager.setMessageType(
							context, i, SEND_MESSAGE_AT_END, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("StartMessageAddressNull") == 0)
				{
					PrefsManager.setMessageAddress(
						context, i, SEND_MESSAGE_AT_START, null);
				}
				else if (parts[0].compareTo("StartMessageAddress") == 0)
				{
					PrefsManager.setMessageAddress(
						context, i, SEND_MESSAGE_AT_START, parts[1]);
				}
				else if (parts[0].compareTo("EndMessageAddressNull") == 0)
				{
					PrefsManager.setMessageAddress(
						context, i, SEND_MESSAGE_AT_END, null);
				}
				else if (parts[0].compareTo("EndMessageAddress") == 0)
				{
					PrefsManager.setMessageAddress(
						context, i, SEND_MESSAGE_AT_END, parts[1]);
				}
				else if (parts[0].compareTo("StartMessageNumberNull") == 0)
				{
					PrefsManager.setMessageNumber(
						context, i, SEND_MESSAGE_AT_START, null);
				}
				else if (parts[0].compareTo("StartMessageNumber") == 0)
				{
					PrefsManager.setMessageNumber(
						context, i, SEND_MESSAGE_AT_START, parts[1]);
				}
				else if (parts[0].compareTo("EndMessageNumberNull") == 0)
				{
					PrefsManager.setMessageNumber(
						context, i, SEND_MESSAGE_AT_END, null);
				}
				else if (parts[0].compareTo("EndMessageNumber") == 0)
				{
					PrefsManager.setMessageNumber(
						context, i, SEND_MESSAGE_AT_END, parts[1]);
				}
				else if (parts[0].compareTo("StartMessageContactNull") == 0)
				{
					PrefsManager.setMessageContact(
						context, i, SEND_MESSAGE_AT_START, null);
				}
				else if (parts[0].compareTo("StartMessageContact") == 0)
				{
					PrefsManager.setMessageContact(
						context, i, SEND_MESSAGE_AT_START, parts[1]);
				}
				else if (parts[0].compareTo("EndMessageContactNull") == 0)
				{
					PrefsManager.setMessageContact(
						context, i, SEND_MESSAGE_AT_END, null);
				}
				else if (parts[0].compareTo("EndMessageContact") == 0)
				{
					PrefsManager.setMessageContact(
						context, i, SEND_MESSAGE_AT_END, parts[1]);
				}
				else if (parts[0].compareTo("StartMessageExtract") == 0)
				{
					if (parts[1].compareTo("true")== 0) {
						PrefsManager.setMessageExtract(
							context, i, SEND_MESSAGE_AT_START, true);
					}
					else if (parts[1].compareTo("false")== 0) {
						PrefsManager.setMessageExtract(
							context, i, SEND_MESSAGE_AT_START, false);
					}
				}
				else if (parts[0].compareTo("EndMessageExtract") == 0)
				{
					if (parts[1].compareTo("true")== 0) {
						PrefsManager.setMessageExtract(
							context, i, SEND_MESSAGE_AT_END, true);
					}
					else if (parts[1].compareTo("false")== 0) {
						PrefsManager.setMessageExtract(
							context, i, SEND_MESSAGE_AT_END, false);
					}
				}
				else if (parts[0].compareTo("StartMessageFirstCount") == 0)
				{
					try
					{
						PrefsManager.setMessageFirstCount(
							context, i, SEND_MESSAGE_AT_START, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("EndMessageFirstCount") == 0)
				{
					try
					{
						PrefsManager.setMessageFirstCount(
							context, i, SEND_MESSAGE_AT_END, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("StartMessageLastCount") == 0)
				{
					try
					{
						PrefsManager.setMessageLastCount(
							context, i, SEND_MESSAGE_AT_START, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("EndMessageLastCount") == 0)
				{
					try
					{
						PrefsManager.setMessageLastCount(
							context, i, SEND_MESSAGE_AT_END, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("StartMessageFirstDir") == 0)
				{
					try
					{
						PrefsManager.setMessageFirstDir(
							context, i, SEND_MESSAGE_AT_START, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("EndMessageFirstDir") == 0)
				{
					try
					{
						PrefsManager.setMessageFirstDir(
							context, i, SEND_MESSAGE_AT_END, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("StartMessageLastDir") == 0)
				{
					try
					{
						PrefsManager.setMessageLastDir(
							context, i, SEND_MESSAGE_AT_START, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("EndMessageLastDir") == 0)
				{
					try
					{
						PrefsManager.setMessageLastDir(
							context, i, SEND_MESSAGE_AT_END, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("StartMessageTrim") == 0)
				{
					if (parts[1].compareTo("true")== 0) {
						PrefsManager.setMessageTrim(
							context, i, SEND_MESSAGE_AT_START, true);
					}
					else if (parts[1].compareTo("false")== 0) {
						PrefsManager.setMessageTrim(
							context, i, SEND_MESSAGE_AT_START, false);
					}
				}
				else if (parts[0].compareTo("EndMessageTrim") == 0)
				{
					if (parts[1].compareTo("true")== 0) {
						PrefsManager.setMessageTrim(
							context, i, SEND_MESSAGE_AT_END, true);
					}
					else if (parts[1].compareTo("false")== 0) {
						PrefsManager.setMessageTrim(
							context, i, SEND_MESSAGE_AT_END, false);
					}
				}
				else if (parts[0].compareTo("StartMessageTextType") == 0)
				{
					try
					{
						PrefsManager.setMessageTextType(
							context, i, SEND_MESSAGE_AT_START, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("EndMessageTextType") == 0)
				{
					try
					{
						PrefsManager.setMessageTextType(
							context, i, SEND_MESSAGE_AT_END, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("StartMessageLiteral") == 0)
				{
					s = parts[1].replace("%n", "\n")
								.replace("%q", "\"")
								.replace("%p", "%");
					PrefsManager.setMessageLiteral(
						context, i, SEND_MESSAGE_AT_START, s);
				}
				else if (parts[0].compareTo("EndMessageLiteral") == 0)
				{
					s = parts[1].replace("%n", "\n")
								.replace("%q", "\"")
								.replace("%p", "%");
					PrefsManager.setMessageLiteral(
						context, i, SEND_MESSAGE_AT_END, s);
				}
				else if (parts[0].compareTo("StartSubjectTextType") == 0)
				{
					try
					{
						PrefsManager.setSubjectTextType(
							context, i, SEND_MESSAGE_AT_START, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("EndSubjectTextType") == 0)
				{
					try
					{
						PrefsManager.setSubjectTextType(
							context, i, SEND_MESSAGE_AT_END, Integer.valueOf(parts[1]));
					} catch (NumberFormatException ignore) {}
				}
				else if (parts[0].compareTo("StartSubjectLiteral") == 0)
				{
					s = parts[1].replace("%n", "\n")
						.replace("%q", "\"")
						.replace("%p", "%");
					PrefsManager.setSubjectLiteral(
						context, i, SEND_MESSAGE_AT_START, s);
				}
				else if (parts[0].compareTo("EndSubjectLiteral") == 0)
				{
					s = parts[1].replace("%n", "\n")
						.replace("%q", "\"")
						.replace("%p", "%");
					PrefsManager.setSubjectLiteral(
						context, i, SEND_MESSAGE_AT_END, s);
				}
				// Just ignore anything that we don't understand:
				// probably someone has tampered with the file.
			}
		} catch (Exception e) {
			String s = R.string.settingsfail
					   + " " + e.getCause().toString()
					   + " " + e.getMessage();
			Toast.makeText(context, s, Toast.LENGTH_LONG).show();
		}
	}
}
