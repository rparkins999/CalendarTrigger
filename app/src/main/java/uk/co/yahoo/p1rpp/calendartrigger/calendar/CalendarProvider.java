/*
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.calendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;

import java.util.ArrayList;
import java.util.GregorianCalendar;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;

public class CalendarProvider {

	private Context context;

	public CalendarProvider(Context context) {
		this.context = context;
	}

	// Projection for calendar queries
	public static final String[] CALENDAR_PROJECTION = new String[] {
		Calendars._ID,
		Calendars.CALENDAR_DISPLAY_NAME,
		Calendars.SYNC_EVENTS
	};

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

	private String likeQuote(String s) {
		StringBuilder result = new StringBuilder(" LIKE '%");
		String es = s.replace("*", "**").replace("%", "*%")
					 .replace("_", "*_").replace("'", "''");
		result.append(es).append("%' ESCAPE '*'");
		return result.toString();
	}

	// Make selection string for an event class
	private StringBuilder selection(Context context, int classNum) {
		ArrayList<Long> calendarIds
			= PrefsManager.getCalendars(context, classNum);
		StringBuilder selClause = new StringBuilder();
		if (!calendarIds.isEmpty())
		{
			selClause.append("(");
			boolean first = true;
			for (long id : calendarIds)
			{
				if (first)
				{
					first = false;
				} else
				{
					selClause.append(" OR ");
				}
				selClause.append("(").append(Instances.CALENDAR_ID)
						 .append("=").append(id).append(")");
			}
			selClause.append(") AND ");
		}
		selClause.append(Instances.ALL_DAY)
				 .append(" = 0");
		String s = PrefsManager.getEventName(context, classNum);
		if (!s.isEmpty())
		{
			selClause.append(" AND ").append(Instances.TITLE)
					 .append(likeQuote(s));
		}
		s = PrefsManager.getEventLocation(context, classNum);
		if (!s.isEmpty())
		{
			selClause.append(" AND ").append(Instances.EVENT_LOCATION)
					 .append(likeQuote(s));
		}
		s = PrefsManager.getEventDescription(context, classNum);
		if (!s.isEmpty())
		{
			selClause.append(" AND ").append(Instances.DESCRIPTION)
					 .append(likeQuote(s));
		}
		// Event colour is not currently selectable from the UI
		s = PrefsManager.getEventColour(context, classNum);
		if (!s.isEmpty())
		{
			selClause.append(" AND ").append(Instances.EVENT_COLOR)
					 .append(likeQuote(s));
		}
		switch (PrefsManager.getWhetherBusy(context, classNum))
		{
			case PrefsManager.ONLY_BUSY:
				selClause.append(" AND ").append(Instances.AVAILABILITY)
						 .append("=").append(Instances.AVAILABILITY_BUSY);
				break;
			case PrefsManager.ONLY_NOT_BUSY:
				selClause.append(" AND ").append(Instances.AVAILABILITY)
						 .append("=").append(Instances.AVAILABILITY_FREE);
				break;
			default:
		}
		switch (PrefsManager.getWhetherRecurrent(context, classNum))
		{
			// check if missing entry is null or ""
			case PrefsManager.ONLY_RECURRENT:
				selClause.append(" AND ").append(Instances.RRULE)
						 .append(" IS NOT NULL");
				break;
			case PrefsManager.ONLY_NOT_RECURRENT:
				selClause.append(" AND ").append(Instances.RRULE)
						 .append(" IS NULL");
				break;
			default:
		}
		switch (PrefsManager.getWhetherOrganiser(context, classNum))
		{
			case PrefsManager.ONLY_ORGANISER:
				selClause.append(" AND ").append(Instances.IS_ORGANIZER)
						 .append(" = 1");
				break;
			case PrefsManager.ONLY_NOT_ORGANISER:
				selClause.append(" AND ").append(Instances.IS_ORGANIZER)
						 .append(" != 1");
				break;
			default:
		}
		switch (PrefsManager.getWhetherPublic(context, classNum))
		{
			case PrefsManager.ONLY_PUBLIC:
				selClause.append(" AND ").append(Instances.ACCESS_LEVEL)
						 .append(" != ").append(Instances.ACCESS_PRIVATE);
				break;
			case PrefsManager.ONLY_PRIVATE:
				selClause.append(" AND ").append(Instances.ACCESS_LEVEL)
						 .append(" = ").append(Instances.ACCESS_PRIVATE);
				break;
			default:
		}
		switch (PrefsManager.getWhetherAttendees(context, classNum))
		{
			case PrefsManager.ONLY_WITH_ATTENDEES:
				selClause.append(" AND ").append(Instances.HAS_ATTENDEE_DATA)
						 .append(" = 1");
				break;
			case PrefsManager.ONLY_WITHOUT_ATTENDEES:
				selClause.append(" AND ").append(Instances.HAS_ATTENDEE_DATA)
						 .append(" = 0");
				break;
			default:
		}
		return selClause;
	}

	// get next action times for event class
	public class startAndEnd {
		// Start time of current or next event, Long.MAX_VALUE if none
		public long startTime;
		public String startEventName;
		
		// End time of current or next event, currentTime if none
		public long endTime;
		public String endEventName;
	}

	private static final String[] INSTANCE_PROJECTION = new String[] {
		Instances.BEGIN,
		Instances.END,
		Instances.TITLE,
	};

	private static final int INSTANCE_PROJECTION_BEGIN_INDEX = 0;
	private static final int INSTANCE_PROJECTION_END_INDEX = 1;
	private static final int INSTANCE_PROJECTION_TITLE_INDEX = 2;

	public startAndEnd nextActionTimes(
		Context context, long currentTime, int classNum) {
		int before = PrefsManager.getBeforeMinutes(context, classNum) * 60000;
		int after = PrefsManager.getAfterMinutes(context, classNum) * 60000;
		startAndEnd result = new startAndEnd();
		long triggerEnd =  PrefsManager.getLastTriggerEnd(context, classNum);
		if (triggerEnd > currentTime)
		{
			result.startTime = currentTime;
			result.endTime = triggerEnd;
			result.startEventName = "<immediate>";
			result.endEventName = "<immediate>";
		}
		else
		{
			result.startTime = Long.MAX_VALUE;
			result.endTime = currentTime;
			result.startEventName = "";
			result.endEventName = "";
		}
		ContentResolver cr = context.getContentResolver();
		StringBuilder selClause = selection(context, classNum);
		selClause.append(" AND ( ").append(Instances.END)
				 .append(" > ? )");
		String[] selectionArgs = new String[] {
			String.valueOf(currentTime - after)};
		// Do query sorted by start time
		Cursor cur = cr.query(getInstancesQueryUri(), INSTANCE_PROJECTION,
							  selClause.toString(), selectionArgs,
							  Instances.BEGIN);
		while (cur.moveToNext())
		{
			long start = cur.getLong(INSTANCE_PROJECTION_BEGIN_INDEX) - before;
			long end = cur.getLong(INSTANCE_PROJECTION_END_INDEX) + after;
			if (start < result.startTime)
			{
				// This can only happen once, because we sort the
				// query on ascending start time
				result.startTime = start;
				if (end > result.endTime)
				{
					result.endTime = end;
					result.startEventName =
						cur.getString(INSTANCE_PROJECTION_TITLE_INDEX);
					result.endEventName = result.startEventName;
				}
			}
			else if (start <= result.endTime)
			{
				// This event starts or started before our current end
				if (end > result.endTime)
				{
					// extend end time for overlapping event
					result.endTime = end;
					result.endEventName =
						cur.getString(INSTANCE_PROJECTION_TITLE_INDEX);
				}
			}
			if (start > currentTime)
			{
				// This event starts in the future
				// We need not consider any later ones, because we will
				// set an alarm for its start time or earlier and look again
				break;
			}
		}
		cur.close();
		return result;
	}

	// get start time and location for next event with a location
	public class StartAndLocation {
		public long startTime;
		public String location;
		public String eventName;
	}

	private static final String[] LOCATION_PROJECTION = new String[] {
		Instances.BEGIN,
		Instances.EVENT_LOCATION,
		Instances.TITLE,
		};

	private static final int LOCATION_PROJECTION_BEGIN_INDEX = 0;
	private static final int LOCATION_PROJECTION_LOCATION_INDEX = 1;
	private static final int LOCATION_PROJECTION_TITLE_INDEX = 2;

	public StartAndLocation nextLocation(Context context, long currentTime) {
		GregorianCalendar dateFin = new GregorianCalendar();
		dateFin.add(GregorianCalendar.MONTH, 1);
		StringBuilder selClause = new StringBuilder();
		selClause.append("( ").append(Instances.BEGIN)
				 .append(" > ").append(String.valueOf(currentTime))
				 .append(" )");
		selClause.append(" AND ( ").append(Instances.BEGIN)
				 .append(" < ").append(String.valueOf(dateFin.getTimeInMillis
			()))
				 .append(" )");
		selClause.append(" AND ( ").append(Instances.EVENT_LOCATION)
				 .append(" IS NOT NULL )");
		ContentResolver cr = context.getContentResolver();
		Cursor cur = cr.query(getInstancesQueryUri(), LOCATION_PROJECTION,
							  selClause.toString(), null,
							  Instances.BEGIN);
		if (cur.moveToFirst())
		{
			StartAndLocation result = new StartAndLocation();
			result.startTime = cur.getLong(LOCATION_PROJECTION_BEGIN_INDEX);
			result.location = cur.getString(LOCATION_PROJECTION_LOCATION_INDEX);
			result.eventName = cur.getString(LOCATION_PROJECTION_TITLE_INDEX);
			return result;
		}
		else
		{
			return null;
		}
	}

	private static final String[] TIMEZONE_PROJECTION = new String[] {
			Instances.BEGIN,
			Instances.END,
			Instances.END_DAY,
			Instances.END_MINUTE,
			Instances.START_DAY,
			Instances.START_MINUTE,
			Events.EVENT_END_TIMEZONE,
			Events.EVENT_TIMEZONE
	};

	private static final int TIMEZONE_PROJECTION_BEGIN_INDEX = 0;
	private static final int TIMEZONE_PROJECTION_END_INDEX = 1;
	private static final int TIMEZONE_PROJECTION_END_DAY_INDEX = 2;
	private static final int TIMEZONE_PROJECTION_END_MINUTE_INDEX = 3;
	private static final int TIMEZONE_PROJECTION_START_DAY_INDEX = 4;
	private static final int TIMEZONE_PROJECTION_START_MINUTE_INDEX = 5;
	private static final int TIMEZONE_PROJECTION_END_TIMEZONE_INDEX = 6;
	private static final int TIMEZONE_PROJECTION_START_TIMEZONE_INDEX = 7;

	public void doTimeZoneAdjustment(Context context, int classNum, int tzOffset) {
		ContentResolver cr = context.getContentResolver();
		StringBuilder selClause = selection(context, classNum);
		Cursor cur = cr.query(getInstancesQueryUri(), TIMEZONE_PROJECTION,
							  selection(context, classNum).toString(),
				null, null);
		while (cur.moveToNext())
		{

		}
	}
}
