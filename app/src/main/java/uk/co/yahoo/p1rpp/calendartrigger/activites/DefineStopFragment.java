/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.content.PermissionChecker;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.Widgets.ClickableSpinner;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;

/**
 * Created by rparkins on 05/07/16.
 */
public class DefineStopFragment extends DefineFragment {

    private boolean haveStepCounter;
    private EditText stepCountEditor;
    private boolean havelocation;
    private EditText metresEditor;

    public DefineStopFragment() {
    }

    public static DefineStopFragment newInstance(String className ) {
        DefineStopFragment fragment = new DefineStopFragment();
        fragment.className = className;
        fragment.italicClassName = "<i>" + htmlEncode(className) + "</i>";
        return fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        owner = (EditActivity)getActivity();
        owner.setButtonVisibility(View.INVISIBLE);
        int classNum = PrefsManager.getClassNum(owner, className);
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        InputFilter[] lf = {
            new InputFilter.LengthFilter(6)
        };
        LinearLayout ll =
            (LinearLayout)owner.findViewById(R.id.dynamicscrollview);
        ll.removeAllViews();
        TextView tv = new TextView(owner);
        tv.setText(R.string.longpresslabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner,
                               fromHtml(getString(R.string.definestoppopup,
                                                  className)),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        ll.addView(tv, ww);
        tv = new TextView(owner);
        tv.setText(fromHtml(getString(R.string.definestoplist, className)));
        ll.addView(tv, ww);
        LinearLayout lll = new LinearLayout(owner);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        lll.setPadding((int)(scale * 25.0), 0, 0, 0);
        minutesEditor = new EditText(owner);
        minutesEditor.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        minutesEditor.setFilters(lf);
        int i = PrefsManager.getAfterMinutes(owner, classNum);
        minutesEditor.setText(String.valueOf(i > 0 ? i : -i),
            TextView.BufferType.EDITABLE);
        lll.addView(minutesEditor);
        tv = new TextView(owner);
        tv.setText(R.string.minutes);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, getString(R.string.stopminuteshelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        lll.addView(tv, ww);
        ArrayAdapter<?> ad = ArrayAdapter.createFromResource(
            owner, R.array.beforeorafter, R.layout.activity_text_viewer);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        beforeAfter = new ClickableSpinner(owner);
        beforeAfter.setAdapter(ad);
        beforeAfter.setSelection((i <= 0 ? 0 : 1));
        View.OnLongClickListener beforeafterlistener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, getString(R.string.beforeafterendhelp),
                    Toast.LENGTH_LONG).show();
                return true;
            }
        };
        beforeAfter.setup(beforeafterlistener, this);
        lll.addView(beforeAfter);
        tv = new TextView(owner);
        tv.setText(R.string.stopminuteslabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, getString(R.string.stopminuteshelp),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        lll.addView(tv, ww);
        ll.addView(lll, ww);
        tv = new TextView(owner);
        tv.setText(R.string.stopnotuntillabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, getString(R.string.stopnotuntilhelp),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        ll.addView(tv, ww);
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;
        // Check that the device supports the step counter sensor
        PackageManager packageManager = owner.getPackageManager();
        haveStepCounter =
            currentApiVersion >= android.os.Build.VERSION_CODES.KITKAT
            && packageManager.hasSystemFeature(
                   PackageManager.FEATURE_SENSOR_STEP_COUNTER);
        int colour =  haveStepCounter ? 0xFF000000 : 0x80000000;
        lll = new LinearLayout(owner);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        lll.setPadding((int)(scale * 25.0), 0, 0, 0);
        lll.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner,
                               haveStepCounter ?
                               getString(R.string.stepcounteryes) :
                               getString(R.string.stepcounterno),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        TextView firstStepsLabel = new TextView(owner);
        firstStepsLabel.setText(R.string.firststepslabel);
        firstStepsLabel.setTextColor(colour);
        stepCountEditor = new EditText(owner);
        stepCountEditor.setInputType(
            android.text.InputType.TYPE_CLASS_NUMBER);
        stepCountEditor.setFilters(lf);
        i = haveStepCounter ? PrefsManager.getAfterSteps(owner, classNum) : 0;
        stepCountEditor.setText(String.valueOf(i), TextView.BufferType.EDITABLE);
        stepCountEditor.setEnabled(true);
        stepCountEditor.setTextColor(colour);
        TextView lastStepsLabel = new TextView(owner);
        lastStepsLabel.setText(R.string.laststepslabel);
        lastStepsLabel.setTextColor(colour);
        lll.addView(firstStepsLabel, ww);
        lll.addView(stepCountEditor, ww);
        lll.addView(lastStepsLabel, ww);
        ll.addView(lll, ww);
        havelocation = PackageManager.PERMISSION_GRANTED ==
                       PermissionChecker.checkSelfPermission(
                           owner, Manifest.permission.ACCESS_FINE_LOCATION);
        colour =  havelocation ? 0xFF000000 : 0x80000000;
        lll = new LinearLayout(owner);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        lll.setPadding((int)(scale * 25.0), 0, 0, 0);
        lll.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner,
                               havelocation ?
                               getString(R.string.locationyes) :
                               getString(R.string.locationno),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        TextView firstMetresLabel = new TextView(owner);
        firstMetresLabel.setText(R.string.firstlocationlabel);
        firstMetresLabel.setTextColor(colour);
        metresEditor = new EditText(owner);
        metresEditor.setInputType(
            android.text.InputType.TYPE_CLASS_NUMBER);
        metresEditor.setFilters(lf);
        i = havelocation ? PrefsManager.getAfterMetres(owner, classNum) : 0;
        metresEditor.setText(String.valueOf(i), TextView.BufferType.EDITABLE);
        metresEditor.setEnabled(havelocation);
        metresEditor.setTextColor(colour);
        TextView lastMetresLabel = new TextView(owner);
        lastMetresLabel.setText(R.string.lastlocationlabel);
        lastMetresLabel.setTextColor(colour);
        lll.addView(firstMetresLabel, ww);
        lll.addView(metresEditor);
        lll.addView(lastMetresLabel, ww);
        ll.addView(lll, ww);
        tv = new TextView(owner);
        tv.setPadding((int)(scale * 25.0), 0, 0, 0);
        tv.setText(R.string.devicepositionlabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(owner, R.string.devicepositionhelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        ll.addView(tv, ww);
        setupStateUi(ll, ww, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        int classNum = PrefsManager.getClassNum(owner, className);
        String s = minutesEditor.getText().toString();
        int i = s.isEmpty() ? 0 : Integer.parseInt(s);
        if (beforeAfter.getSelectedItemPosition() == 0) { i = -i; }
        PrefsManager.setAfterMinutes(owner, classNum, i);
        s = stepCountEditor.getText().toString();
        if (s.isEmpty()) { s = "0"; }
        PrefsManager.setAfterSteps(owner, classNum, Integer.valueOf(s));
        s = metresEditor.getText().toString();
        if (s.isEmpty()) { s = "0"; }
        PrefsManager.setAfterMetres(owner, classNum, Integer.valueOf(s));
    }
}
