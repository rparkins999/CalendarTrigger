/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.v4.content.PermissionChecker;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import uk.co.yahoo.p1rpp.calendartrigger.DataStore;
import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.R;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;

public class FloatActivity extends Activity
        implements DatePickerDialog.OnDateSetListener,
        DatePickerDialog.OnCancelListener {
    public FloatActivity floatactivity;
    private SQLiteDatabase floatingTimeEvents;

    private boolean isFloating (long eventid) {
        String sql = "SELECT EVENT_ID FROM FLOATINGEVENTS WHERE EVENT_ID IS ?";
        String[] args = new String[] { String.valueOf(eventid) };
        Cursor cu = floatingTimeEvents.rawQuery(sql, args);
        return cu.getCount() > 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dynamicscrollview);
        floatactivity = this;
        floatingTimeEvents =
            DataStore.getFloatingEvents(this, false);
    }

    private void showDialog() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(YEAR);
        int month = c.get(MONTH);
        int day = c.get(DAY_OF_MONTH);
        DatePickerDialog myDialog = new DatePickerDialog(
            this, this, year, month, day);
        myDialog.setOnCancelListener(this);
        myDialog.setTitle(getString(R.string.eventdate));
        myDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PackageManager.PERMISSION_GRANTED !=
                PermissionChecker.checkSelfPermission(
                        this, Manifest.permission.WRITE_CALENDAR))
        {
            LinearLayout ll =
                    (LinearLayout)findViewById(R.id.dynamicscrollview);
            TextView tv = new TextView(this);
            tv.setText(getString(R.string.nomodifypermission));
            ll.addView(tv);
        }
        else if (PackageManager.PERMISSION_GRANTED !=
                PermissionChecker.checkSelfPermission(
                        this, Manifest.permission.READ_EXTERNAL_STORAGE))
        {
            LinearLayout ll =
                (LinearLayout)findViewById(R.id.dynamicscrollview);
            TextView tv = new TextView(this);
            tv.setText(getString(R.string.floatnoreadpermission));
            ll.addView(tv);
        }
        else if (PackageManager.PERMISSION_GRANTED !=
            PermissionChecker.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        {
            LinearLayout ll =
                (LinearLayout)findViewById(R.id.dynamicscrollview);
            TextView tv = new TextView(this);
            tv.setText(getString(R.string.floatnowritepermission));
            ll.addView(tv);
        }
        else
        {
            showDialog();
        }
    }

    private static final String[] EVENT_PROJECTION = new String[] {
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND
    };

    private static final int INDEX_TITLE = 0;
    private static final int INDEX_DTSTART = 1;
    private static final int INDEX_DTEND = 2;

    public void doChanged(long eventId, boolean isChecked, boolean recurring) {
        // paranoid test if really changed
        if (isChecked != isFloating(eventId)) {
            ContentResolver cr = getContentResolver();
            Uri uri = ContentUris.withAppendedId
                (CalendarContract.Events.CONTENT_URI, eventId);
            Cursor cu = cr.query(uri, EVENT_PROJECTION,
                null, null, null);
            DateFormat df = DateFormat.getTimeInstance();
            cu.moveToNext();
            String title = cu.getString(INDEX_TITLE);
            long dtstart = cu.getLong(INDEX_DTSTART);
            if (isChecked) {
                long offset = TimeZone.getDefault().getOffset(new Date().getTime());
                long dtend = cu.getLong(INDEX_DTEND);
                ContentValues uvf = new ContentValues();
                uvf.put("EVENT_ID", eventId);
                uvf.put("START_WALLTIME_MILLIS", dtstart + offset);
                if (dtend > 0) { dtend += offset; }
                uvf.put("END_WALLTIME_MILLIS", dtend);
                floatingTimeEvents.insert("FLOATINGEVENTS",
                    null, uvf);
                new MyLog(this,
                    (recurring ?"All instances of recurring event " :  "Event ")
                    + title + " set to begin at " + df.format(dtstart)
                    + " wall clock time");
            }
            else
            {
                String[] args = new String[] {String.valueOf(eventId)};
                floatingTimeEvents.delete("FLOATINGEVENTS",
                    "EVENT_ID IS ?", args);
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                new MyLog(this,
                    (recurring ? "Event " : "All instances of recurring event ")
                    + title + " set to begin at " + df.format(dtstart)
                    + " UTC");
            }
        }

    }

    private static final String[] INSTANCE_PROJECTION = new String[] {
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.RRULE
    };

    private static final int INDEX_EVENT_ID = 0;
    private static final int INDEX_EVENT_TITLE = 1;
    private static final int INDEX_EVENT_RRULE = 2;

    public void onDateSet(DatePicker view, int year, int month, int day) {
        LinearLayout ll = (LinearLayout)findViewById(R.id.dynamicscrollview);
        ll.removeAllViews();
        TextView tv = new TextView(this);
        ContentResolver cr = getContentResolver();
        GregorianCalendar dd = new GregorianCalendar();
        dd.setTimeInMillis(0);
        dd.set(YEAR, year);
        dd.set(MONTH, month);
        dd.set(DAY_OF_MONTH, day);
        CharSequence date = DateFormat.getDateInstance().format(dd.getTimeInMillis());
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, dd.getTimeInMillis());
        dd.roll(DAY_OF_MONTH,1);
        ContentUris.appendId(builder, dd.getTimeInMillis());
        Cursor cu = cr.query(builder.build(), INSTANCE_PROJECTION,
                null, null, CalendarContract.Instances.BEGIN);
        if (cu.getCount() == 0)
        {
            tv.setText(getString(R.string.noevents) + " " + date);
            ll.addView(tv);
            showDialog();
        }
        else
        {
            tv.setText(getString(R.string.checkfloat));
            tv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(floatactivity,
                        getString(R.string.checkfloathelp),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            ll.addView(tv);
            while (cu.moveToNext()) {
                final long eventId = cu.getLong(INDEX_EVENT_ID);
                final boolean recurring = cu.getString(INDEX_EVENT_RRULE) != null;
                CheckBox cb = new CheckBox(this);
                cb.setText(cu.getString(INDEX_EVENT_TITLE));
                cb.setTextColor(recurring ? 0xFFFF0000 : 0xFF000000);
                cb.setChecked(isFloating(eventId));
                cb.setOnCheckedChangeListener (
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(
                            CompoundButton buttonView, boolean isChecked) {
                                doChanged(eventId, isChecked, recurring);
                        }
                    });
                cb.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        String help = recurring ?
                            getString(R.string.checkfloatrecurringhelp) :
                            getString(R.string.checkfloatnormalhelp) ;
                        Toast.makeText(floatactivity, help,
                            Toast.LENGTH_LONG).show();
                        return true;
                    }
                });
                ll.addView(cb);
            }
        }
    }

    public void onCancel(DialogInterface dialog) {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (floatingTimeEvents != null) {
            floatingTimeEvents.close();
        }
    }
}
