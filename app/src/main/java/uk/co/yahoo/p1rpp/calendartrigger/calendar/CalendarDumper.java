/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.calendar;

import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.widget.DatePicker;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import uk.co.yahoo.p1rpp.calendartrigger.BuildConfig;
import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.activites.ArrayAdapter;
import uk.co.yahoo.p1rpp.calendartrigger.activites.SettingsActivity;

import static android.provider.CalendarContract.Events.AVAILABILITY_BUSY;
import static android.provider.CalendarContract.Events.AVAILABILITY_FREE;
import static android.provider.CalendarContract.Events.ACCESS_PRIVATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;

public class CalendarDumper implements OnDateSetListener {

    private SettingsActivity caller;
    private ArrayAdapter<String> adapter;
    private int index;
    private long startDate;
    private long endDate;

    private void putline(String line) {
        if (BuildConfig.DEBUG)
        {
            if (adapter == null)
            {
                new MyLog(caller, line, true);
            } else
            {
                adapter.add(line);
            }
        }
    }

    // Projection for event instance queries
    private static final String[] INSTANCE_PROJECTION = new String[] {
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.START_DAY,
            CalendarContract.Instances.START_MINUTE,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Instances.END,
            CalendarContract.Instances.END_DAY,
            CalendarContract.Instances.END_MINUTE,
            CalendarContract.Events.EVENT_END_TIMEZONE,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.HAS_ATTENDEE_DATA,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.RRULE
    };

    private static final int INDEX_CALENDAR_DISPLAY_NAME = 0;
    private static final int INDEX_TITLE = 1;
    private static final int INDEX_ALL_DAY = 2;
    private static final int INDEX_DTSTART = 3;
    private static final int INDEX_DTEND = 4;
    private static final int INDEX_DURATION = 5;
    private static final int INDEX_BEGIN = 6;
    private static final int INDEX_START_DAY = 7;
    private static final int INDEX_START_MINUTE = 8;
    private static final int INDEX_START_TIMEZONE = 9;
    private static final int INDEX_END = 10;
    private static final int INDEX_END_DAY = 11;
    private static final int INDEX_END_MINUTE = 12;
    private static final int INDEX_END_TIMEZONE = 13;
    private static final int INDEX_AVAILABILITY = 14;
    private static final int INDEX_ACCESS_LEVEL = 15;
    private static final int INDEX_HAS_ATTENDEES = 16;
    private static final int INDEX_ORGANIZER = 17;
    private static final int INDEX_LOCATION = 18;
    private static final int INDEX_DESCRIPTION = 19;
    private static final int INDEX_RRULE = 20;

    private void doOne(Cursor cu)
    {
        if (BuildConfig.DEBUG) {
            DateFormat df = DateFormat.getDateInstance();
            DateFormat dtf0 = DateFormat.getDateTimeInstance();
            dtf0.setTimeZone(TimeZone.getTimeZone("GMT"));
            DateFormat dtf1 = DateFormat.getDateTimeInstance();
            TimeZone tz = TimeZone.getDefault();
            String tzname = tz.getDisplayName(
                    tz.inDaylightTime(new Date()), TimeZone.SHORT);
            putline(String.format(
                "Calendar: %s", cu.getString(INDEX_CALENDAR_DISPLAY_NAME)));
            putline( String.format(
                "  Event %s", cu.getString(INDEX_TITLE)));
            long millis = (
                    ((long)cu.getInt(INDEX_START_DAY) - 2440588) * 24 * 60
                            + cu.getInt(INDEX_START_MINUTE)) * 60 * 1000;
            putline(String.format(
                    "    Instance START %s %s",
                    dtf0.format(millis), tzname));
            millis = ((long)(cu.getInt(INDEX_END_DAY) - 2440588) * 24 * 60
                    + cu.getInt(INDEX_END_MINUTE)) * 60 * 1000;
            putline(String.format(
                    "    Instance END %s %s",
                    dtf0.format(millis), tzname));
            putline(String.format(
                    "    Instance BEGIN %s UTC, Instance END %s UTC",
                    dtf0.format(cu.getLong(INDEX_BEGIN)),
                    dtf0.format(cu.getLong(INDEX_END))));
            putline(String.format(
                "    DTSTART %s UTC, DTEND %s UTC, DURATION %s",
                dtf0.format(cu.getLong(INDEX_DTSTART)),
                dtf0.format(cu.getLong(INDEX_DTEND)),
                cu.getString(INDEX_DURATION)));
            putline(String.format(
                "    DTSTART %s %s, DTEND %s %s",
                dtf1.format(cu.getLong(INDEX_DTSTART)), tzname,
                dtf1.format(cu.getLong(INDEX_DTEND)), tzname));
            putline(String.format(
                "    EVENT_TIMEZONE %s, EVENT_END_TIMEZONE %s",
                cu.getString(INDEX_START_TIMEZONE),
                cu.getString(INDEX_END_TIMEZONE)));
            int avail = cu.getInt(INDEX_AVAILABILITY);
            putline(String.format(
                "    %s%s, %s, %s",
                (cu.getInt(INDEX_ALL_DAY) != 0) ? "ALL DAY, " : "",
                avail == AVAILABILITY_BUSY ? "BUSY" :
                    (avail == AVAILABILITY_FREE ? "FREE" : "TENTATIVE"),
                (cu.getInt(INDEX_ACCESS_LEVEL) == ACCESS_PRIVATE)
                ? "PRIVATE" : "PUBLIC",
                cu.getInt(INDEX_HAS_ATTENDEES) == 0
                    ? "NO ATTENDEES" : "HAS ATTENDEES"));
            putline(String.format(
                    "ORGANIZER %s", cu.getString(INDEX_ORGANIZER)));
            putline(String.format(
                    "LOCATION %s", cu.getString(INDEX_LOCATION)));
            putline(String.format(
                    "DESCRIPTION %s", cu.getString(INDEX_DESCRIPTION)));
            String rrule = cu.getString(INDEX_RRULE);
            if (rrule == null)
            {
                putline("RRULE is null");
            }
            else
            {
                putline(String.format("RRULE %s", rrule));
            }
            putline("");
        }
    }

    private void doAll() {
        if (BuildConfig.DEBUG) {
            if (adapter == null) {
                new MyLog(caller, "Dump of calendar events:");
            }
            ContentResolver cr = caller.getContentResolver();
            Uri.Builder builder =
                    CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, startDate);
            ContentUris.appendId(builder, endDate);
            Cursor cu = cr.query(
                    builder.build(), INSTANCE_PROJECTION,
                    null,null,
                    CalendarContract.Instances.BEGIN);
            if (cu != null) {
                while (cu.moveToNext()) {
                    doOne(cu);
                }
            }
            caller.doneCalendar(adapter);
        }
    }

    public void onDateSet(
            DatePicker view, int year, int month, int day) {
        if (BuildConfig.DEBUG) {
            GregorianCalendar dd = new GregorianCalendar();
            dd.setTimeInMillis(0);
            dd.set(YEAR, year);
            dd.set(MONTH, month);
            dd.set(DAY_OF_MONTH, day);
            long millis = dd.getTimeInMillis();
            if (index == 0) {
                startDate = millis;
                DatePickerDialog myDialog = new DatePickerDialog(
                        caller, this, year, month, day);
                myDialog.setTitle("Dump End Date");
                index = 1;
                myDialog.show();
            } else {
                endDate = millis;
                doAll();
            }
        }
    }
    public CalendarDumper(
            SettingsActivity owner, ArrayAdapter<String> theAdapter) {
        if (BuildConfig.DEBUG) {
            caller = owner;
            adapter = theAdapter;
            final Calendar c = Calendar.getInstance();
            int year = c.get(YEAR);
            int month = c.get(MONTH);
            int day = c.get(DAY_OF_MONTH);
            DatePickerDialog myDialog = new DatePickerDialog(
                    caller, this, year, month, day);
            myDialog.setTitle("Dump Start Date");
            index = 0;
            myDialog.show();
        }
    }
}
