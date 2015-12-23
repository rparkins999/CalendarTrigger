package com.RPP.calendartrigger.calendar;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;

import com.RPP.calendartrigger.PrefsManager;
import com.RPP.calendartrigger.models.Calendar;
import com.RPP.calendartrigger.models.CalendarEvent;

public class CalendarProvider {

	private Context context;
	private static Calendar[] savedCalendars;
	
	public CalendarProvider(Context context) {
		this.context = context;
	}
	
	// Projection for calendar queries
	public static final String[] CALENDAR_PROJECTION = new String[] {
		Calendars._ID,
		Calendars.CALENDAR_DISPLAY_NAME,
		Calendars.SYNC_EVENTS
	};
	
	public static final int CALENDAR_PROJECTION_ID_INDEX = 0;
	public static final int CALENDAR_PROJECTION_DISPLAY_NAME_INDEX = 1;
	public static final int CALENDAR_PROJECTION_IS_SYNCED_INDEX = 2;
	
	// Projection for event queries
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
	 * Get the last calendars fetched by listCalendar
	 * @return Calendars in memory
	 */
	public static Calendar[] getCachedCalendars() {
		return savedCalendars;
	}
	
	/**
	 * List the user's calendars
	 * @return All calendars of the user
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
		
		LinkedHashMap<Long, Boolean> checkedCalendars = PrefsManager.getCheckedCalendars(context);
		
		Calendar[] res = new Calendar[cur.getCount()];
		
		int i=0;
		while(cur.moveToNext()) {
			
			long calendarId = cur.getLong(CALENDAR_PROJECTION_ID_INDEX);
			res[i] = new Calendar(calendarId,
					cur.getString(CALENDAR_PROJECTION_DISPLAY_NAME_INDEX),
					cur.getInt(CALENDAR_PROJECTION_IS_SYNCED_INDEX) == 1,
					checkedCalendars.containsKey(calendarId) && checkedCalendars.get(calendarId)); // Calendar is checked
			
			i++;
		}
		cur.close();
		
		savedCalendars = res;
		
		return res;
	}
	
	private Uri getInstancesQueryUri() {
		// Event search window : from one month before to one month after, to be sure
		GregorianCalendar dateDebut = new GregorianCalendar();
		dateDebut.add(GregorianCalendar.MONTH, -1);
		GregorianCalendar dateFin = new GregorianCalendar();
		dateFin.add(GregorianCalendar.MONTH, 1);
		
		// search URI (contains the search window)
		Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
		ContentUris.appendId(builder, dateDebut.getTimeInMillis());
		ContentUris.appendId(builder, dateFin.getTimeInMillis());
		
		return builder.build();
	}
	
	/**
	 * Get the current event in one of the calendars set in the preferences
	 * @param currentTime Time at which the event should be searched
	 * @param delay Delay that extends the search interval towards the end
	 * @param early Delay that extends the search interval towards the beginning
	 * @param onlyBusy True if only to search for busy events
	 * @return The first event found, or null if there is none
	 */
	public CalendarEvent getCurrentEvent(long currentTime, long delay, long early, boolean onlyBusy) {
		ContentResolver cr = context.getContentResolver();
		
		// Make the calendar ID selection string
		String calIdsSelect = getEventCalendarIdsSelectString();
		
		if(calIdsSelect.equals(""))
			return null;
		
		// Selection must be inclusive on the start time, and exclusive on
		// the end time.
		// This way when setting an alarm at the end of the event, this moment is considered outside of the event
		String selection = "(" + calIdsSelect + ") AND "
				+ Instances.BEGIN + " <= ? AND "
				+ Instances.END + " > ? AND " + Instances.ALL_DAY + " = 0";
		
		if(onlyBusy) {
			selection += " AND " + Instances.AVAILABILITY + " = " + Instances.AVAILABILITY_BUSY;
		}
		
		String strCurrentTimeEarly = String.valueOf(currentTime + early);
		String strCurrentTimeDelay = String.valueOf(currentTime - delay);
		String[] selectionArgs =  new String[] { strCurrentTimeEarly, strCurrentTimeDelay };
		
		Cursor cur = cr.query(getInstancesQueryUri(), INSTANCE_PROJECTION, selection, selectionArgs, Instances.END); // Take the event that ends first
		
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
	 * Get the next event in the calendars set in the preferences
	 * @param currentTime Time to use to search for events
	 * @param early Delay to extend event start time before the real start time
	 * @return The first event found, or null if there is none
	 */
	public CalendarEvent getNextEvent(long currentTime, long early, boolean onlyBusy) {
		ContentResolver cr = context.getContentResolver();
		
		// Make the calendar ID selection string
		String calIdsSelect = getEventCalendarIdsSelectString();
		
		if(calIdsSelect.equals(""))
			return null;
		
		// Selection is inclusive on event start time.
		// This way we are consistent wih getCurrentEvent
		String selection = "(" + calIdsSelect + ") AND "
				+ Instances.BEGIN + " >= ? AND " + Instances.ALL_DAY + " = 0";
		
		if(onlyBusy) {
			selection += " AND " + Instances.AVAILABILITY + " = " + Instances.AVAILABILITY_BUSY;
		}
		
		// Substract early from Instances.BEGIN -> same as adding early to currentTime when comparing
		String strCurrentTime = String.valueOf(currentTime + early);
		String[] selectionArgs =  new String[] { strCurrentTime };
		
		Cursor cur = cr.query(getInstancesQueryUri(), INSTANCE_PROJECTION, selection, selectionArgs, Instances.BEGIN); // Sort by start time to get the first event
		
		CalendarEvent res;
		if(cur.moveToNext())
			res = new CalendarEvent(cur.getString(INSTANCE_PROJECTION_TITLE_INDEX), cur.getLong(INSTANCE_PROJECTION_BEGIN_INDEX), cur.getLong(INSTANCE_PROJECTION_END_INDEX));
		else
			res = null;
		
		cur.close();
		return res;
	}
	
	private String likeQuote(String s) {
		StringBuilder result = new StringBuilder(" LIKE \"%");
		String es = s.replace("*", "**").replace("%", "*%")
					 .replace("_", "*_").replace("\"", "\"\"");
		result.append(es).append("%\" ESCAPE \"*\"");
		return result.toString();
	}

	// Make selection string for an event class
	private StringBuilder selection(Context context, int classNum)
	{
		ArrayList<Long> calendarIds
			= PrefsManager.getCalendars(context, classNum);
		StringBuilder selClause;
		// for now we don't handle state-only events with no calendars
		if(calendarIds.isEmpty()) { return selClause; }
		selClause.append("(");
		boolean first = true;
		for(long id : calendarIds) {
			if(first) { first = false; }
			else { selClause.append(" OR "); }
			selClause.append("(").append(Instances.CALENDAR_ID)
					 .append("=").append(id).append(")");
		}
		selClause.append(") AND ").append(Instances.ALL_DAY)
				 .append(" = 0");
		String s = PrefsManager.getEventName(context, classNum);
		if (!s.isEmpty()) {
			selClause.append(" AND ").append(Instances.TITLE)
					 .append(likeQuote(s));
		}
		s = PrefsManager.getEventLocation(context, classNum);
		if (!s.isEmpty()) {
			selClause.append(" AND ").append(Instances.EVENT_LOCATION)
					 .append(likeQuote(s));
		}
		s = PrefsManager.getEventDescription(context, classNum);
		if (!s.isEmpty()) {
			selClause.append(" AND ").append(Instances.DESCRIPTION)
					 .append(likeQuote(s));
		}
		s = PrefsManager.getEventColour(context, classNum);
		if (!s.isEmpty()) {
			selClause.append(" AND ").append(Instances.EVENT_COLOR)
				 .append(likeQuote(s));
		}
		switch (PrefsManager.getWhetherBusy(context, classNum))
		{
			case 1:
				selClause.append(" AND ").append(Instances.AVAILABILITY)
						 .append("=").append(Instances.AVAILABILITY_BUSY);
				break;
			case 2:
				selClause.append(" AND ").append(Instances.AVAILABILITY)
						 .append("=").append(Instances.AVAILABILITY_FREE);
				break;
			default:
		}
		switch (PrefsManager.getWhetherRecurrent(context, classNum))
		{
		// check if missing entry is null or ""
			case 1:
				selClause.append(" AND ").append(Instances.RRULE)
						 .append(" IS NOT NULL");
				break;
			case 2:
				selClause.append(" AND ").append(Instances.RRULE)
						 .append(" IS NULL");
				break;
			default:
		}
		switch (PrefsManager.getWhetherOrganiser(context, classNum))
		{
			case 1:
				selClause.append(" AND ").append(Instances.IS_ORGANIZER )
						 .append(" = 1");
				break;
			case 2:
				selClause.append(" AND ").append(Instances.IS_ORGANIZER )
						 .append(" != 1");
				break;
			default:
		}
		switch (PrefsManager.getWhetherPublic(context, classNum))
		{
			case 1:
				selClause.append(" AND ").append(Instances.ACCESS_LEVEL )
						 .append(" != ").append(Instances.ACCESS_PRIVATE);
				break;
			case 2:
				selClause.append(" AND ").append(Instances.IS_ORGANIZER )
						 .append(" = ").append(Instances.ACCESS_PRIVATE);
				break;
			default:
		}
		switch (PrefsManager.getWhetherAttendees(context, classNum))
		{
			case 1:
				selClause.append(" AND ").append(Instances.HAS_ATTENDEE_DATA )
						 .append(" = 1");
				break;
			case 2:
				selClause.append(" AND ").append(Instances.HAS_ATTENDEE_DATA )
						 .append(" = 0");
				break;
			default:
		}
		return selClause;
	}

	// time of next start or end of an event
	public int nextActionTime(
		Context context, long currentTime)
	{
		long result = Long.MAX_VALUE;
		String[] projection = new String[] { Instances.BEGIN, Instances.END };
		ContentResolver cr = context.getContentResolver();
		int n = PrefsManager.getNumClasses(context);
		for (int classNum = 0; classNum < n; ++classNum)
		{
			if (!PrefsManager.isClassUsed(context, classNum)) { continue; }
			StringBuilder selClause = selection(context, classNum);
			selClause.append(" AND ( ").append(Instances.BEGIN)
					 .append(" > ? OR ").append(Instances.END)
					 .append(" > ? )");
			int before = PrefsManager.getBeforeMinutes(context, classNum));
			int after = PrefsManager.getBeforeMinutes(context, classNum));
			String[] selectionArgs = new String[] {
				String.valueOf(currentTime + before),
				String.valueOf(currentTime - after)
			};
			Cursor cur = cr.query(getInstancesQueryUri(), projection,
								  selClause.toString(), selectionArgs, null);
			while (cur.moveToNext())
			{
				long t = cur.getLong(1) - before;
				if ((t > currentTime) && (t < result)) { result = t; }
				t = cur.getLong(2) + after;
				if ((t > currentTime) && (t < result)) { result = t; }
			}
		}
		return result;
	}
}

	// is an event of this class active?
	public boolean isNowActive(
		Context context, int classNum, long currentTime)
	{
		StringBuilder selClause = selection(context, classNum);
		selClause.append(" AND ").append(Instances.BEGIN)
				 .append(" <= ? AND ").append(Instances.END)
				 .append(" > ?");
		String[] selectionArgs = new String[] {
			String.valueOf(currentTime
						   + PrefsManager.getBeforeMinutes(context, classNum)),
			String.valueOf(currentTime
						   - PrefsManager.getAfterMinutes(context, classNum))
		};
		String[] projection = new String[] { Instances.TITLE };
		ContentResolver cr = context.getContentResolver();
		Cursor cur = cr.query(getInstancesQueryUri(), projection,
							  selClause.toString(), selectionArgs, null);
		return cur.getCount() > 0;
	}
}
