package uk.co.yahoo.p1rpp.calendartrigger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class PrefsManager {

	private static final String PREFS_NAME = "mainPreferences";
	
	private static final String PREF_LOGGING = "logging";

	public static void setLoggingMode(Context context, boolean IsOn) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			   .putBoolean(PREF_LOGGING, IsOn).commit();
	}

	public static boolean getLoggingMode(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(PREF_LOGGING, false);
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
		String prefName = IS_CLASS_USED.concat(String.valueOf(classNum));
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

	// (optional) name of class
	private static final String CLASS_NAME = "className";

	public static void setClassName(
		Context context, int classNum, String className) {
		String prefName = CLASS_NAME.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, className).commit();
	}

	private static String getClassName(SharedPreferences prefs, int classNum) {
		String prefName = CLASS_NAME.concat(String.valueOf(classNum));
		return prefs.getString(prefName, ((Integer)classNum).toString());
	}

	public static String getClassName(Context context, int classNum) {
		String prefName = CLASS_NAME.concat(String.valueOf(classNum));
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
		String prefName = EVENT_NAME.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, eventName).commit();
	}

	public static String getEventName(Context context, int classNum) {
		String prefName = EVENT_NAME.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// string required in locations of events which can be in class
	private static final String EVENT_LOCATION = "eventLocation";

	public static void setEventLocation(
		Context context, int classNum, String eventLocation) {
		String prefName = EVENT_LOCATION.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, eventLocation).commit();
	}

	public static String getEventLocation(Context context, int classNum) {
		String prefName = EVENT_LOCATION.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// string required in descriptions of events which can be in class
	private static final String EVENT_DESCRIPTION = "eventDescription";

	public static void setEventDescription(
		Context context, int classNum, String eventDescription) {
		String prefName = EVENT_DESCRIPTION.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, eventDescription).commit();
	}

	public static String getEventDescription(Context context, int classNum) {
		String prefName = EVENT_DESCRIPTION.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// colour of events which can be in class
	private static final String EVENT_COLOUR = "eventColour";

	public static void setEventColour(
		Context context, int classNum, String eventColour)
	{
		String prefName = EVENT_COLOUR.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, eventColour).commit();
	}

	public static String getEventColour(Context context, int classNum) {
		String prefName = EVENT_COLOUR.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	// calendars whose events can be in class
	private static final String AGENDAS = "agendas";
	private static final String AGENDAS_DELIMITER = ",";

	public static void putCalendars(
		Context context, int classNum, ArrayList<Long> calendarIds)
	{
		String prefName = AGENDAS.concat(String.valueOf(classNum));
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
		String prefName = AGENDAS.concat(String.valueOf(classNum));
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
		String prefName = WHETHER_BUSY.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherBusy).commit();
	}

	public static int getWhetherBusy(Context context, int classNum) {
		String prefName = WHETHER_BUSY.concat(String.valueOf(classNum));
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
		String prefName = WHETHER_RECURRENT.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherRecurrent).commit();
	}

	public static int getWhetherRecurrent(Context context, int classNum) {
		String prefName = WHETHER_RECURRENT.concat(String.valueOf(classNum));
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
		String prefName = WHETHER_ORGANISER.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherOrganiser).commit();
	}

	public static int getWhetherOrganiser(Context context, int classNum) {
		String prefName = WHETHER_ORGANISER.concat(String.valueOf(classNum));
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
		String prefName = WHETHER_PUBLIC.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherPublic).commit();
	}

	public static int getWhetherPublic(Context context, int classNum) {
		String prefName = WHETHER_PUBLIC.concat(String.valueOf(classNum));
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
		String prefName = WHETHER_ATTENDEES.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, whetherAttendees).commit();
	}

	public static int getWhetherAttendees(Context context, int classNum) {
		String prefName = WHETHER_ATTENDEES.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, ATTENDEES_AND_NOT);
	}

	// used for "nothing saved"
	public static final int RINGER_MODE_NONE = -99;

	// last user's ringer state
	private static final String USER_RINGER = "userRinger";

	public static void setUserRinger(Context context, int userRinger) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(USER_RINGER, userRinger).commit();
	}

	public static int getUserRinger(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(USER_RINGER, RINGER_MODE_NONE);
	}

	// last ringer state set by this app (to check if user changed it)
	private static final String LAST_RINGER = "lastRinger";

	public static void setLastRinger(Context context, int lastRinger) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(LAST_RINGER, lastRinger).commit();
	}

	public static int getLastRinger(Context context) {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(LAST_RINGER, RINGER_MODE_NONE);
	}

	// ringer state wanted during event of this class
	private static final String RINGER_ACTION = "ringerAction";

	public static void setRingerAction(Context context, int classNum, int action) {
		String prefName = RINGER_ACTION.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, action).commit();
	}

	public static int getRingerAction(Context context, int classNum)
	{
		String prefName = RINGER_ACTION.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, RINGER_MODE_NONE);
	}

	// whether to restore ringer after event of this class
	private static final String RESTORE_RINGER = "restoreRinger";

	public static void setRestoreRinger(
		Context context, int classNum, boolean restore)
	{
		String prefName = RESTORE_RINGER.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, restore).commit();
	}

	public static boolean getRestoreRinger(Context context, int classNum) {
		String prefName = RESTORE_RINGER.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// minutes before start time event of this class to take actions
	private static final String BEFORE_MINUTES = "beforeMinutes";

	public static void setBeforeMinutes(
		Context context, int classNum, int beforeMinutes) {
		String prefName = BEFORE_MINUTES.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, beforeMinutes).commit();
	}

	public static int getBeforeMinutes(Context context, int classNum) {
		String prefName = BEFORE_MINUTES.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// minutes after end time event of this class to take actions
	private static final String AFTER_MINUTES = "afterMinutes";

	public static void setAfterMinutes(
		Context context, int classNum, int afterMinutes) {
		String prefName = AFTER_MINUTES.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterMinutes).commit();
	}

	public static int getAfterMinutes(Context context, int classNum) {
		String prefName = AFTER_MINUTES.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// steps moved after end time event of this class to take actions
	private static final String AFTER_STEPS = "afterSteps";

	public static void setAfterSteps(
		Context context, int classNum, int afterSteps) {
		String prefName = AFTER_STEPS.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterSteps).commit();
	}

	public static int getAfterSteps(Context context, int classNum) {
		String prefName = AFTER_STEPS.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// steps target after end time event of this class to take actions
	private static final String TARGET_STEPS = "targetSteps";

	public static void setTargetSteps(
		Context context, int classNum, int afterSteps) {
		String prefName = TARGET_STEPS.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterSteps).commit();
	}

	public static int getTargetSteps(Context context, int classNum) {
		String prefName = TARGET_STEPS.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// metres moved after end time event of this class to take actions
	private static final String AFTER_METRES = "afterMetres";

	public static void setAfterMetres(
		Context context, int classNum, int afterSteps) {
		String prefName = AFTER_METRES.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putInt(prefName, afterSteps).commit();
	}

	public static int getAfterMetres(Context context, int classNum) {
		String prefName = AFTER_METRES.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getInt(prefName, 0);
	}

	// Location from which we're waiting to be getAfterMetres(...)
	// Impossible latitude of 360 means we aren't waiting
	private static final String LATITUDE = "latitude";

	public static void setLatitude(
		Context context, int classNum, double x) {
		String prefName = LATITUDE.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, String.valueOf(x)).commit();
	}


	public static Double getLatitude(Context context, int classNum) {
		String prefName = LATITUDE.concat(String.valueOf(classNum));
		String s = context.getSharedPreferences(
			PREFS_NAME, Context .MODE_PRIVATE) .getString(prefName, "360.0");
		return new Double(s);
	}

	private static final String LONGITUDE = "longitude";

	public static void setLongitude(
		Context context, int classNum, double x) {
		String prefName = LONGITUDE.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putFloat(prefName, (float)x).commit();
	}

	public static double getLongitude(Context context, int classNum) {
		String prefName = LONGITUDE.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getFloat(prefName, 0);
	}

	// whether to display notification before start of event
	private static final String NOTIFY_START = "notifyStart";

	public static void setNotifyStart(
		Context context, int classNum, boolean notifyStart) {
		String prefName = NOTIFY_START.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, notifyStart).commit();
	}

	public static boolean getNotifyStart(Context context, int classNum) {
		String prefName = NOTIFY_START.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// whether to display notification after end of event
	private static final String NOTIFY_END = "notifyEnd";

	public static void setNotifyEnd(Context context, int classNum,
		boolean notifyEnd) {
		String prefName = NOTIFY_END.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, notifyEnd).commit();
	}

	public static boolean getNotifyEnd(Context context, int classNum) {
		String prefName = NOTIFY_END.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// is an immediate event of this class currently requested?
	private static final String IS_TRIGGERED = "isTriggered";

	public static void setClassTriggered(
		Context context, int classNum, boolean isTriggered)
	{
		String prefName = IS_TRIGGERED.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, isTriggered).commit();
	}

	public static boolean isClassTriggered(Context context, int classNum) {
		String prefName = IS_TRIGGERED.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// is an event of this class currently active?
	private static final String IS_ACTIVE = "isActive";

	public static void setClassActive(
		Context context, int classNum, boolean isActive)
	{
		String prefName = IS_ACTIVE.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, isActive).commit();
	}

	public static boolean isClassActive(Context context, int classNum) {
		String prefName = IS_ACTIVE.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// is an event of this class currently waiting after becoming inactive?
	private static final String IS_WAITING = "isWaiting";

	public static void setClassWaiting(
		Context context, int classNum, boolean isWaiting)
	{
		String prefName = IS_WAITING.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putBoolean(prefName, isWaiting).commit();
	}

	public static boolean isClassWaiting(Context context, int classNum) {
		String prefName = IS_WAITING.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getBoolean(prefName, false);
	}

	// name of last active event for this class
	private static final String LAST_ACTIVE_EVENT = "lastActiveEvent";

	public static void setLastActive(
		Context context, int classNum, String name)
	{
		String prefName = LAST_ACTIVE_EVENT.concat(String.valueOf(classNum));
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			   .edit().putString(prefName, name).commit();
	}

	public static String getLastActive(Context context, int classNum) {
		String prefName = LAST_ACTIVE_EVENT.concat(String.valueOf(classNum));
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
					  .getString(prefName, "");
	}

	private static void removeClass(SharedPreferences prefs, int classNum) {
		String num = String.valueOf(classNum);
		prefs.edit().putBoolean(IS_CLASS_USED.concat(num), false)
			 .putString(CLASS_NAME.concat(num), "")
			 .putString(EVENT_NAME.concat(num), "")
			 .putString(EVENT_LOCATION.concat(num), "")
			 .putString(EVENT_DESCRIPTION.concat(num), "")
			 .putString(EVENT_COLOUR.concat(num), "")
			 .putString(AGENDAS.concat(num), "")
			 .putInt(WHETHER_BUSY.concat(num), BUSY_AND_NOT)
			 .putInt(WHETHER_RECURRENT.concat(num), RECURRENT_AND_NOT)
			 .putInt(WHETHER_ORGANISER.concat(num), ORGANISER_AND_NOT)
			 .putInt(WHETHER_PUBLIC.concat(num), PUBLIC_AND_PRIVATE)
			 .putInt(WHETHER_ATTENDEES.concat(num), ATTENDEES_AND_NOT)
			 .putInt(RINGER_ACTION.concat(num), RINGER_MODE_NONE)
			 .putBoolean(RESTORE_RINGER.concat(num), false)
			 .putInt(BEFORE_MINUTES.concat(num), 0)
			 .putInt(AFTER_MINUTES.concat(num), 0)
			 .putInt(AFTER_STEPS.concat(num), 0)
			 .putInt(TARGET_STEPS.concat(num), 0)
			 .putInt(AFTER_METRES.concat(num), 0)
			 .putString(LATITUDE.concat(num), "360.0")
			 .putString(LONGITUDE.concat(num), "360.0")
			 .putBoolean(NOTIFY_START.concat(num), false)
			 .putBoolean(NOTIFY_END.concat(num), false)
			 .putBoolean(IS_ACTIVE.concat(num), false)
			 .commit();
	}

	public static void removeClass(Context context, String name) {
		SharedPreferences prefs
			= context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		removeClass(prefs, getClassNum(prefs, name));
	}
}
