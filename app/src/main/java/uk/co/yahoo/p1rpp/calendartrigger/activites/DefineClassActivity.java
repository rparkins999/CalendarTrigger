/*
 * Copyright (c) 2021. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import uk.co.yahoo.p1rpp.calendartrigger.Layouts.AndList;
import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;

public class DefineClassActivity extends Activity
    implements AdapterView.OnItemSelectedListener {

    private String className;

    // Projection for calendar queries
    public static final String[] CALENDAR_PROJECTION = new String[] {
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
    };
    public static final int CALENDAR_PROJECTION_ID_INDEX = 0;
    public static final int CALENDAR_PROJECTION_DISPLAY_NAME_INDEX = 1;
    private static class calendarCheck extends CheckBox {
        public long id;
        calendarCheck(Context context, long calId) {
            super(context);
            id = calId;
        }
    }
    private ArrayList<DefineClassActivity.calendarCheck> calChecks;
    private TextView invisible;
    private RadioGroup busyState;
    private RadioGroup recurrentState;
    private RadioGroup organiserState;
    private RadioGroup publicState;
    private RadioGroup attendeeState;
    private AndList comparisons;

    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
        String item = parent.getItemAtPosition(pos).toString();
        Toast.makeText(this, item, Toast.LENGTH_LONG).show();
    }
    public void onNothingSelected (AdapterView<?> parent) {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_define_class);
    }

    public void makeComparisons(
        DefineClassActivity ac, int classNum, boolean first)
    {
        comparisons = new AndList(ac);
        comparisons.setup(this, classNum, first);
    }

    // This prevents a button from getting focussed
    public void forceTouchMode() {
        invisible.requestFocus();
    }

    /**
     * Called after {@link #onRestoreInstanceState}, {@link #onRestart}, or
     * {@link #onPause}, for your activity to start interacting with the user.
     * This is a good place to begin animations, open exclusive-access devices
     * (such as the camera), etc.
     *
     * <p>Keep in mind that onResume is not the best indicator that your activity
     * is visible to the user; a system window such as the keyguard may be in
     * front.  Use {@link #onWindowFocusChanged} to know for certain that your
     * activity is visible to the user (for example, to resume a game).
     *
     * <p><em>Derived classes must call through to the super class's
     * implementation of this method.  If they do not, an exception will be
     * thrown.</em></p>
     *
     * @see #onRestoreInstanceState
     * @see #onRestart
     * @see #onPostResume
     * @see #onPause
     */
    @Override
    protected void onResume() {
        super.onResume();
        final DefineClassActivity ac = this;
        Intent i = getIntent();
        className = i.getStringExtra("classname");
        Configuration config = getResources().getConfiguration();
        final String italicName = "<i>" + htmlEncode(className) + "</i>";
        float scale = getResources().getDisplayMetrics().density;
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        boolean first = true;
        calChecks = new ArrayList<>();
        invisible = (TextView)ac.findViewById(R.id.defineinvisible);
        LinearLayout ll =
            (LinearLayout)ac.findViewById(R.id.defineclasslayout);
        ll.removeAllViews();
        TextView tv = new TextView(ac);
        tv.setText(R.string.longpresslabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                    fromHtml(getString(R.string.defineclasspopup, italicName)),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        ll.addView(tv, ww);
        tv = new TextView(ac);
        tv.setText(fromHtml(getString(R.string.defineclasslist, italicName)));
        ll.addView(tv, ww);
        int classNum = PrefsManager.getClassNum(this, className);
        ArrayList<Long> checkedCalendarIds
            = PrefsManager.getCalendars(ac, classNum);
        ContentResolver cr = ac.getContentResolver();
        Uri calendarUri = CalendarContract.Calendars.CONTENT_URI;
        Cursor cur
            = cr.query(calendarUri, CALENDAR_PROJECTION, null, null, null);
        if (cur != null) {
            tv = new TextView(ac);
            tv.setPadding((int)(scale * 25.0), 0, 0, 0);
            tv.setText(getString(R.string.ifanycalendar));
            tv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac,
                        fromHtml(getString(R.string.allcalendars, className)),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            ll.addView(tv, ww);
            first = false;
            LinearLayout lll = new LinearLayout(this);
            lll.setOrientation(LinearLayout.VERTICAL);
            lll.setPadding((int)(scale * 50.0), 0, 0, 0);
            while (cur.moveToNext()) {
                long calId = cur.getLong(CALENDAR_PROJECTION_ID_INDEX);
                String calName
                    = cur.getString(CALENDAR_PROJECTION_DISPLAY_NAME_INDEX);
                DefineClassActivity.calendarCheck cc =
                    new DefineClassActivity.calendarCheck(this, calId);
                cc.setText(calName);
                cc.setChecked(checkedCalendarIds.contains(calId));
                lll.addView(cc, ww);
                calChecks.add(cc);
            }
            ll.addView(lll, ww);
            cur.close();
        }
        makeComparisons(this, classNum, first);
        ll.addView(comparisons);
        LinearLayout lll = new LinearLayout(ac);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        tv = new TextView(ac);
        tv.setText(R.string.busylabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                    fromHtml(getString(R.string.busyhelp, className)),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            lll.setPadding((int)(scale * 25.0), 0, 0, 0);
            tv.setPadding(0, (int)(scale * 7.0), 0, 0);
            lll.addView(tv, ww);
        } else {
            lll.setPadding((int)(scale * 50.0), 0, 0, 0);
            tv.setPadding((int)(scale * 25.0), 0, 0, 0);
            ll.addView(tv, ww);
        }
        busyState = new RadioGroup(ac);
        busyState.setOrientation(LinearLayout.HORIZONTAL);
        int index = PrefsManager.getWhetherBusy(ac, classNum);
        int id = -1;
        RadioButton rb = new RadioButton(ac);
        rb.setText(R.string.onlybusy);
        busyState.addView(rb, PrefsManager.ONLY_BUSY, ww);
        if (index == PrefsManager.ONLY_BUSY) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.onlynotbusy);
        busyState.addView(rb, PrefsManager.ONLY_NOT_BUSY, ww);
        if (index == PrefsManager.ONLY_NOT_BUSY) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.busyandnot);
        busyState.addView(rb, PrefsManager.BUSY_AND_NOT, ww);
        if (index == PrefsManager.BUSY_AND_NOT) { id = rb.getId(); }
        busyState.check(id);
        lll.addView(busyState, ww);
        ll.addView(lll, ww);
        lll = new LinearLayout(ac);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        tv = new TextView(ac);
        tv.setText(R.string.recurrentlabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                    fromHtml(getString(R.string.recurrenthelp, className)),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            lll.setPadding((int)(scale * 25.0), 0, 0, 0);
            tv.setPadding(0, (int)(scale * 7.0), 0, 0);
            lll.addView(tv, ww);
        } else {
            lll.setPadding((int)(scale * 50.0), 0, 0, 0);
            tv.setPadding((int)(scale * 25.0), 0, 0, 0);
            ll.addView(tv, ww);
        }
        recurrentState = new RadioGroup(ac);
        recurrentState.setOrientation(LinearLayout.HORIZONTAL);
        index = PrefsManager.getWhetherRecurrent(ac, classNum);
        id = -1;
        rb = new RadioButton(ac);
        rb.setText(R.string.onlyrecurrent);
        recurrentState.addView(rb, PrefsManager.ONLY_RECURRENT, ww);
        if (index == PrefsManager.ONLY_RECURRENT) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.onlynotrecurrent);
        recurrentState.addView(rb, PrefsManager.ONLY_NOT_RECURRENT, ww);
        if (index == PrefsManager.ONLY_NOT_RECURRENT) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.recurrentandnot);
        recurrentState.addView(rb, PrefsManager.RECURRENT_AND_NOT, ww);
        if (index == PrefsManager.RECURRENT_AND_NOT) { id = rb.getId(); }
        recurrentState.check(id);
        lll.addView(recurrentState, ww);
        ll.addView(lll, ww);
        lll = new LinearLayout(ac);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        tv = new TextView(ac);
        tv.setText(R.string.organiserlabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                    fromHtml(getString(R.string.organiserhelp, className)),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            lll.setPadding((int)(scale * 25.0), 0, 0, 0);
            tv.setPadding(0, (int)(scale * 7.0), 0, 0);
            lll.addView(tv, ww);
        } else {
            lll.setPadding((int)(scale * 50.0), 0, 0, 0);
            tv.setPadding((int)(scale * 25.0), 0, 0, 0);
            ll.addView(tv, ww);
        }
        organiserState = new RadioGroup(ac);
        organiserState.setOrientation(LinearLayout.HORIZONTAL);
        index = PrefsManager.getWhetherOrganiser(ac, classNum);
        id = -1;
        rb = new RadioButton(ac);
        rb.setText(R.string.onlyorganiser);
        organiserState.addView(rb, PrefsManager.ONLY_ORGANISER, ww);
        if (index == PrefsManager.ONLY_ORGANISER) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.onlynotorganiser);
        organiserState.addView(rb, PrefsManager.ONLY_NOT_ORGANISER, ww);
        if (index == PrefsManager.ONLY_NOT_ORGANISER) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.organiserandnot);
        organiserState.addView(rb, PrefsManager.ORGANISER_AND_NOT, ww);
        if (index == PrefsManager.ORGANISER_AND_NOT) { id = rb.getId(); }
        organiserState.check(id);
        lll.addView(organiserState, ww);
        ll.addView(lll, ww);
        lll = new LinearLayout(ac);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        tv = new TextView(ac);
        tv.setText(R.string.privatelabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                    fromHtml(getString(R.string.privatehelp, className)),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            lll.setPadding((int)(scale * 25.0), 0, 0, 0);
            tv.setPadding(0, (int)(scale * 7.0), 0, 0);
            lll.addView(tv, ww);
        } else {
            lll.setPadding((int)(scale * 50.0), 0, 0, 0);
            tv.setPadding((int)(scale * 25.0), 0, 0, 0);
            ll.addView(tv, ww);
        }
        publicState = new RadioGroup(ac);
        publicState.setOrientation(LinearLayout.HORIZONTAL);
        index = PrefsManager.getWhetherPublic(ac, classNum);
        id = -1;
        rb = new RadioButton(ac);
        rb.setText(R.string.onlyprivate);
        publicState.addView(rb, PrefsManager.ONLY_PUBLIC, ww);
        if (index == PrefsManager.ONLY_PUBLIC) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.onlynotprivate);
        publicState.addView(rb, PrefsManager.ONLY_PRIVATE, ww);
        if (index == PrefsManager.ONLY_PRIVATE) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.privateandnot);
        publicState.addView(rb, PrefsManager.PUBLIC_AND_PRIVATE, ww);
        if (index == PrefsManager.PUBLIC_AND_PRIVATE) { id = rb.getId(); }
        publicState.check(id);
        lll.addView(publicState, ww);
        ll.addView(lll, ww);
        lll = new LinearLayout(ac);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        tv = new TextView(ac);
        tv.setText(R.string.attendeeslabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                    fromHtml(getString(R.string.attendeeshelp, className)),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        attendeeState = new RadioGroup(ac);
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            attendeeState.setOrientation(LinearLayout.HORIZONTAL);
            lll.setPadding((int)(scale * 25.0), 0, 0, 0);
            tv.setPadding(0, (int)(scale * 7.0), 0, 0);
            lll.addView(tv, ww);
        } else {
            attendeeState.setOrientation(LinearLayout.VERTICAL);
            lll.setPadding((int)(scale * 50.0), 0, 0, 0);
            tv.setPadding((int)(scale * 25.0), 0, 0, 0);
            ll.addView(tv, ww);
        }
        index = PrefsManager.getWhetherAttendees(ac, classNum);
        id = -1;
        rb = new RadioButton(ac);
        rb.setText(R.string.onlyattendees);
        attendeeState.addView(rb, PrefsManager.ONLY_WITH_ATTENDEES, ww);
        if (index == PrefsManager.ONLY_WITH_ATTENDEES) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.onlynoattendees);
        attendeeState.addView(rb, PrefsManager.ONLY_WITHOUT_ATTENDEES, ww);
        if (index == PrefsManager.ONLY_WITHOUT_ATTENDEES) { id = rb.getId(); }
        rb = new RadioButton(ac);
        rb.setText(R.string.attendeesandnot);
        attendeeState.addView(rb, PrefsManager.ATTENDEES_AND_NOT, ww);
        if (index == PrefsManager.ATTENDEES_AND_NOT) { id = rb.getId(); }
        attendeeState.check(id);
        lll.addView(attendeeState, ww);
        ll.addView(lll, ww);
    }

    /**
     * Called as part of the activity lifecycle when an activity is going into
     * the background, but has not (yet) been killed.  The counterpart to
     * {@link #onResume}.
     *
     * <p>When activity B is launched in front of activity A, this callback will
     * be invoked on A.  B will not be created until A's onPause returns,
     * so be sure to not do anything lengthy here.
     *
     * <p>This callback is mostly used for saving any persistent state the
     * activity is editing, to present a "edit in place" model to the user and
     * making sure nothing is lost if there are not enough resources to start
     * the new activity without first killing this one.  This is also a good
     * place to do things like stop animations and other things that consume a
     * noticeable amount of CPU in order to make the switch to the next activity
     * as fast as possible, or to close resources that are exclusive access
     * such as the camera.
     *
     * <p>In situations where the system needs more memory it may kill paused
     * processes to reclaim resources.  Because of this, you should be sure
     * that all of your state is saved by the time you return from
     * this function.  In general {@link #onSaveInstanceState} is used to save
     * per-instance state in the activity and this method is used to store
     * global persistent data (in content providers, files, etc.)
     *
     * <p>After receiving this call you will usually receive a following call
     * to {@link #onStop} (after the next activity has been resumed and
     * displayed), however in some cases there will be a direct call back to
     * {@link #onResume} without going through the stopped state.
     *
     * <p><em>Derived classes must call through to the super class's
     * implementation of this method.  If they do not, an exception will be
     * thrown.</em></p>
     *
     * @see #onResume
     * @see #onSaveInstanceState
     * @see #onStop
     */
    @Override
    protected void onPause() {
        super.onPause();
        int classNum = PrefsManager.getClassNum(this, className);
        ArrayList<Long> checkedCalendarIds
            = new ArrayList<>(calChecks.size());
        for (calendarCheck ctv : calChecks) {
            if (ctv.isChecked()) {
                checkedCalendarIds.add(ctv.id);
            }
        }
        PrefsManager.putCalendars(this, classNum, checkedCalendarIds);
        comparisons.updatePreferences(classNum);
        int id = busyState.getCheckedRadioButtonId();
        int index;
        for (index = 0; index <= PrefsManager.BUSY_AND_NOT; ++index) {
            if (busyState.getChildAt(index).getId() == id) {
                PrefsManager.setWhetherBusy(this, classNum, index);
            }
        }
        id = recurrentState.getCheckedRadioButtonId();
        for (index = 0; index <= PrefsManager.RECURRENT_AND_NOT; ++index) {
            if (recurrentState.getChildAt(index).getId() == id) {
                PrefsManager.setWhetherRecurrent(this, classNum, index);
            }
        }
        id = organiserState.getCheckedRadioButtonId();
        for (index = 0; index <= PrefsManager.ORGANISER_AND_NOT; ++index) {
            if (organiserState.getChildAt(index).getId() == id) {
                PrefsManager.setWhetherOrganiser(this, classNum, index);
            }
        }
        id = publicState.getCheckedRadioButtonId();
        for (index = 0; index <= PrefsManager.PUBLIC_AND_PRIVATE; ++index) {
            if (publicState.getChildAt(index).getId() == id) {
                PrefsManager.setWhetherPublic(this, classNum, index);
            }
        }
        id = attendeeState.getCheckedRadioButtonId();
        for (index = 0; index <= PrefsManager.ATTENDEES_AND_NOT; ++index) {
            if (attendeeState.getChildAt(index).getId() == id) {
                PrefsManager.setWhetherAttendees(this, classNum, index);
            }
        }
    }
}
