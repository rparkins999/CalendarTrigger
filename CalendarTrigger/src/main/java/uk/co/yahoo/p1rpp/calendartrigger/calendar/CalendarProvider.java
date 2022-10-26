/*
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.calendar;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.sqlite;

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
		String es = s.replace("*", "**")
					 .replace("%", "*%")
					 .replace("_", "*_")
					 .replace("'", "''");
		result.append(es).append("%' ESCAPE '*'");
		return result.toString();
	}

	// Make selection string for an event class
	private StringBuilder selection(Context context, int classNum) {
		ArrayList<Long> calendarIds
			= PrefsManager.getCalendars(context, classNum);
		StringBuilder selClause = new StringBuilder();
		selClause.append("( ").append(Instances.END)
			.append(" > ? )");
		if (!calendarIds.isEmpty())
		{
			selClause.append(" AND (");
			boolean first = true;
			for (long id : calendarIds)
			{
				if (first)
				{
					first = false;
				}
				else
				{
					selClause.append(" OR ");
				}
				selClause.append("(").append(Instances.CALENDAR_ID)
						 .append("=").append(id).append(")");
			}
			selClause.append(")");
		}
		for (int andIndex = 0; true; ++andIndex) {
			int orIndex = 0;
			for (; true; ++orIndex) {
				String[] sa = PrefsManager.getEventComparison(
					context, classNum, andIndex, orIndex);
				if (sa[2].length() == 0)
				{
					if (orIndex != 0)
					{
						selClause.append(" )");
					}
					break;
				}
				if (orIndex == 0)
				{
					selClause.append(" AND ( ( ");
				}
				else
				{
					selClause.append(" OR ( ");
				}
				switch (Integer.valueOf(sa[0])) {
					case 0:
						selClause.append(Instances.TITLE);
						break;
					case 1:
						selClause.append(Instances.EVENT_LOCATION);
						break;
					case 2:
						selClause.append(Instances.DESCRIPTION);
						break;
				}
				if (sa[1].compareTo("0") != 0)
				{
					selClause.append(" NOT");
				}
				selClause.append(likeQuote(sa[2]));
				selClause.append(" )");
			}
			if (orIndex == 0) {
				break;
			}
		}
		// Event colour is not currently selectable from the UI
		String s = PrefsManager.getEventColour(context, classNum);
		if (!s.isEmpty())
		{
			selClause.append(" AND ").append(Instances.EVENT_COLOR)
					 .append("=").append(likeQuote(s));
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
		switch (PrefsManager.getWhetherAllDay(context, classNum))
		{
			case PrefsManager.ONLY_ALL_DAY:
				selClause.append(" AND ").append(Instances.ALL_DAY)
						 .append("=").append("1");
				break;
			case PrefsManager.ONLY_NOT_ALL_DAY:
				selClause.append(" AND ").append(Instances.ALL_DAY)
						 .append("=").append("0");
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
		public String startEventDescription;

		// End time of current or next event, currentTime if none
		public long endTime;
		public String endEventName;
		public String endEventDescription;
	}

	private static final String[] INSTANCE_PROJECTION = new String[] {
		Instances.BEGIN,
		Instances.END,
		Instances.TITLE,
		Instances.DESCRIPTION
	};

	private static final int INSTANCE_PROJECTION_BEGIN_INDEX = 0;
	private static final int INSTANCE_PROJECTION_END_INDEX = 1;
	private static final int INSTANCE_PROJECTION_TITLE_INDEX = 2;
	private static final int INSTANCE_PROJECTION_DESCRIPTION_INDEX = 3;

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
			result.startEventDescription = "<immediate>";
			result.endEventDescription = "<immediate>";
		}
		else
		{
			result.startTime = Long.MAX_VALUE;
			result.endTime = currentTime;
			result.startEventName = "";
			result.endEventName = "";
			result.startEventDescription = "";
			result.endEventDescription = "";
		}
		ContentResolver cr = context.getContentResolver();
		StringBuilder selClause = selection(context, classNum);
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
				// FIXME THIS IS BIG
				// Several events can start at the same time: if the action is to
				// send a message, I need to send one for each event
				result.startTime = start;
				if (end > result.endTime)
				{
					result.endTime = end;
					result.startEventName =
						cur.getString(INSTANCE_PROJECTION_TITLE_INDEX);
					result.startEventName =
						cur.getString(INSTANCE_PROJECTION_TITLE_INDEX);
					result.startEventDescription =
						cur.getString(INSTANCE_PROJECTION_DESCRIPTION_INDEX);
					result.endEventName = result.startEventName;
					result.endEventDescription = result.startEventDescription;
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
					result.endEventDescription =
						cur.getString(INSTANCE_PROJECTION_DESCRIPTION_INDEX);
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
				 .append(" )")
				 .append(" AND ( ").append(Instances.BEGIN)
				 .append(" < ")
				 .append(String.valueOf(dateFin.getTimeInMillis()))
				 .append(" )")
				 .append(" AND ( ").append(Instances.EVENT_LOCATION)
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

	// Returns -1 if the record was corrupted and we deleted it.
	private long getUnsignedLong(
		Context context, sqlite floatingEvents, Cursor cursor, int columnIndex) {
		try {
			return floatingEvents.getUnsignedLong(cursor, columnIndex);
		} catch (NumberFormatException e) {
			String eventId = cursor.getString(0);
			String wallStart = cursor.getString(1);
			String wallEnd = cursor.getString(2);
			new MyLog(context,context.getString(R.string.deletinginvalid) +
				eventId + ", " + wallStart + ", " + wallEnd +
				context.getString(R.string.fromfloating));
			String[] args = new String[] { eventId, wallStart, wallEnd };
			String whereClause =
				"EVENT_ID IS ?" +
				" AND START_WALLTIME_MILLIS IS ?" +
				" AND END_WALLTIME_MILLIS IS ?";
			floatingEvents.delete("FLOATINGEVENTS", whereClause, args);
			return -1;
		}
	}

	private static final String[] TIMEZONE_PROJECTION = new String[] {
		Events.DURATION,
		Events.RRULE,
		Events.EVENT_TIMEZONE,
		Events.ALL_DAY,
		Events.TITLE
	};

	private static final int TIMEZONE_DURATION_INDEX = 0;
	private static final int TIMEZONE_RRULE_INDEX = 1;
	private static final int TIMEZONE_EVENT_TIMEZONE_INDEX = 2;
	private static final int TIMEZONE_EVENT_ALL_DAY_INDEX = 3;
	private static final int TIMEZONE_EVENT_TITLE_INDEX = 4;

	/* update a single event for time zone change
	 * c1 is the row in floatingEvents
	 * c2 is the data read from the existing events table
	 */
	private boolean updateEvent(
		Context context, ContentResolver cr, sqlite floatingEvents,
		Uri uri, int tzOffset, Cursor c1, Cursor c2)
	{
		String title = c2.getString(TIMEZONE_EVENT_TITLE_INDEX);
		ContentValues cv = new ContentValues();
		long dtstart = getUnsignedLong(context, floatingEvents, c1,  1);
		if (dtstart < 0) { return false; }
		dtstart = dtstart - tzOffset;
		cv.put(Events.DTSTART, dtstart);
		DateFormat dtf = DateFormat.getDateTimeInstance();
		dtf.setTimeZone(TimeZone.getTimeZone("GMT"));
		cv.put(Events.EVENT_TIMEZONE, c2.getString(TIMEZONE_EVENT_TIMEZONE_INDEX));
		String rrule = c2.getString(TIMEZONE_RRULE_INDEX);
		if ((rrule == null) || rrule.isEmpty())
		{
			// not a recurrent event
			long dtend = c1.getLong(2);
			if (dtend < 0) { return false; }
			dtend = dtend - tzOffset;
			cv.put(Events.DTEND, dtend);
			cv.put(Events.DURATION, (String) null);
			new MyLog(context,
				context.getString(R.string.nonrecurring) + title +
				context.getString(R.string.settingstart) + dtf.format(dtstart) +
				context.getString(R.string.settingend) + dtf.format(dtend) + " UTC");
		}
		else
        {
            // recurrent event
            cv.put(Events.DTEND, (String) null);
            String duration = c2.getString(TIMEZONE_DURATION_INDEX);
            cv.put(Events.DURATION, duration);
			cv.put(Events.RRULE, rrule);
			new MyLog(context,
				context.getString(R.string.recurring) + title +
				context.getString(R.string.settingstart) + dtf.format(dtstart) +
				context.getString(R.string.settingduration) + duration +
				context.getString(R.string.settingrrule) + rrule);
        }
		cr.update(uri, cv, null, null);
		return true;
	}

	public void doTimeZoneAdjustment(Context context, int tzOffset) {
		sqlite floatingEvents = new sqlite(context);
		String small = context.getString(R.string.doingTZ);
		String big = context.getString(R.string.doingTZby) +
			(tzOffset >= 0 ? "+" : "") + (tzOffset / 60000) + " " +
			context.getString(R.string.minutes) + ".";
		new MyLog(context, small, big);
		if (floatingEvents != null)
		{
			Cursor c1 = floatingEvents.rawQuery(
				"SELECT EVENT_ID, START_WALLTIME_MILLIS,"
					+ "END_WALLTIME_MILLIS FROM FLOATINGEVENTS", null);
			while (c1.moveToNext()) {
				long eventId =
					getUnsignedLong(context, floatingEvents, c1, 0);
				if (eventId >= 0) {
					ContentResolver cr = context.getContentResolver();
					Uri uri = ContentUris.withAppendedId
						(CalendarContract.Events.CONTENT_URI, eventId);
					Cursor c2 = cr.query(uri, TIMEZONE_PROJECTION,
						null, null, null);
					if (c2.moveToNext()) {
						// All day events are done by the Calendar Provider
						if (c2.getInt(TIMEZONE_EVENT_ALL_DAY_INDEX) == 0) {
							if (!updateEvent(context, cr, floatingEvents,
								uri, tzOffset, c1, c2)) {
								// The event is corrupted, remove from our database
								String[] args = new String[] { String.valueOf(eventId) };
								floatingEvents.delete("FLOATINGEVENTS",
									"EVENT_ID IS ?", args);
								new MyLog(context,
									context.getString(R.string.event) +
									c2.getString(TIMEZONE_EVENT_TITLE_INDEX) +
									context.getString(R.string.corrupted));
								continue;
							}
						} else {
							new MyLog(context,
								context.getString(R.string.event) +
									c2.getString(TIMEZONE_EVENT_TITLE_INDEX) +
									context.getString(R.string.isallday));
						}
					} else {
						// The event no longer exists, remove from our database
						String[] args = new String[] { String.valueOf(eventId) };
						floatingEvents.delete("FLOATINGEVENTS",
							"EVENT_ID IS ?", args);
						new MyLog(context,
							context.getString(R.string.event) +
								c2.getString(TIMEZONE_EVENT_TITLE_INDEX) +
								context.getString(R.string.nonexistent));
					}
					c2.close();
				}
			}
			c1.close();
			floatingEvents.close();
		}
	}
}
