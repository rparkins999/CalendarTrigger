package uk.co.yahoo.p1rpp.calendartrigger.calendar;

import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.models.Calendar;
import uk.co.yahoo.p1rpp.calendartrigger.models.CalendarEvent;

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
	
	/**
	 * Make a WHERE clause to filter selected calendars
	 * @return generated WHERE clause, or en empty string if there is no calendar selected
	 */
	private String getEventCalendarIdsSelectString() {
		LinkedHashMap<Long, Boolean> checkedCalendars = PrefsManager.getCheckedCalendars(context);
		
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
	
	public static void invalidateCalendars() {
		savedCalendars = null;
	}
}
