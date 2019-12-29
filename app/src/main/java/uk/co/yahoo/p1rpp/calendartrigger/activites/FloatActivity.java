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
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.support.v4.content.PermissionChecker;
import android.view.Menu;
import android.view.MenuItem;
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
import java.util.TimeZone;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.SQLtable;

import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.YEAR;

public class FloatActivity extends Activity
        implements DatePickerDialog.OnDateSetListener,
        DatePickerDialog.OnCancelListener {
    public FloatActivity floatactivity;
    private SQLtable floatingTimeEvents;
    private DatePickerDialog myDialog;
    private int m_year;
    private int m_month;
    private int m_day;

    private boolean isFloating (long eventid) {
        String[] args = new String[] { String.valueOf(eventid) };
        SQLtable table = new SQLtable(floatingTimeEvents, "FLOATINGEVENTS",
            "WHERE EVENT_ID IS ?", args, null);
        boolean result = !table.isEmpty();
        table.close();
        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dynamicscrollview);
        floatactivity = this;
        floatingTimeEvents = new SQLtable(this, "FLOATINGEVENTS");
        myDialog = null;
    }

    private void showDialog() {
        final Calendar c = Calendar.getInstance();
        m_year = c.get(YEAR);
        m_month = c.get(MONTH);
        m_day = c.get(DAY_OF_MONTH);
        myDialog = new DatePickerDialog(
            this, this, m_year, m_month, m_day);
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
                floatingTimeEvents.insert(uvf);
                new MyLog(this,
                    getString(recurring ?
                        R.string.allinstances : R.string.event)
                    + title + getString(R.string.settobegin) + df.format(dtstart)
                    + getString(R.string.walltime));
            }
            else
            {
                String[] args = new String[] { String.valueOf(eventId) };
                SQLtable table = new SQLtable(floatingTimeEvents,
                    "FLOATINGEVENTS",
                    "WHERE EVENT_ID IS ?", args, null);
                table.moveToNext();
                table.delete();
                table.close();
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                new MyLog(this,
                    getString(recurring ?
                        R.string.allinstances :  R.string.event)
                    + title + getString(R.string.settobegin) + df.format(dtstart)
                    + getString(R.string.utc));
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
        m_year = year;
        m_month = month;
        m_day = day;
        LinearLayout ll = (LinearLayout)findViewById(R.id.dynamicscrollview);
        ll.removeAllViews();
        TextView tv = new TextView(this);
        ContentResolver cr = getContentResolver();
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        Calendar beginTime = Calendar.getInstance();
        beginTime.set(year, month, day, 0, 0);
        Calendar endTime = Calendar.getInstance();
        endTime.set(year, month, day + 1, 0, 0);
        ContentUris.appendId(builder, beginTime.getTimeInMillis());
        ContentUris.appendId(builder, endTime.getTimeInMillis());
        Cursor cu = cr.query(builder.build(), INSTANCE_PROJECTION,
                null, null, CalendarContract.Instances.BEGIN);
        if (cu.getCount() == 0)
        {
            CharSequence date =
                DateFormat.getDateInstance().format(beginTime.getTimeInMillis());
            tv.setText(getString(R.string.noevents) + date);
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
    public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
        boolean displayIt = super.onPrepareOptionsMenu(menu);
        if ((myDialog != null) && !myDialog.isShowing()) {
            MenuItem mi = menu.add(Menu.NONE, 1, Menu.NONE, R.string.anotherdate);
		    mi.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            return true;
        }
        return displayIt;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getTitle().equals(getString(R.string.anotherdate))) {
            myDialog = new DatePickerDialog(
                this, this, m_year, m_month, m_day);
            myDialog.setOnCancelListener(this);
            myDialog.setTitle(getString(R.string.eventdate));
            myDialog.show();
            showDialog();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        floatingTimeEvents.close();
        super.onDestroy();
    }
}
