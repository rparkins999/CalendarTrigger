/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Fragment;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;

/**
 * Created by rparkins on 05/07/16.
 */
public class ActionStartFragment extends Fragment {
    private static final String ARG_CLASS_NAME = "class name";
    private float scale;
    private RadioGroup ringerAction;
    private CheckBox showNotification;

    public ActionStartFragment() {
    }

    public static ActionStartFragment newInstance(String className ) {
        ActionStartFragment fragment = new ActionStartFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CLASS_NAME, className);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        View rootView =
            inflater.inflate(R.layout.fragment_action_start, container, false);
        scale = getResources().getDisplayMetrics().density;
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        final EditActivity ac = (EditActivity)getActivity();
        ac.setButtonVisibility(View.INVISIBLE);
        int classNum = PrefsManager.getClassNum(
            ac, getArguments().getString(ARG_CLASS_NAME));
        final String className =
            "<i>" + htmlEncode(getArguments().getString(ARG_CLASS_NAME)) +
            "</i>";
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        LinearLayout ll =
            (LinearLayout)ac.findViewById(R.id.actionstartlayout);
        ll.removeAllViews();
        TextView tv = new TextView(ac);
        tv.setText(R.string.longpresslabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                               fromHtml(getString(R.string.actionstartpopup,
                                                  className)),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        ll.addView(tv, ww);
        tv = new TextView(ac);
        tv.setText(fromHtml(getString(R.string.actionstartlist, className)));
        ll.addView(tv, ww);
        LinearLayout lll = new LinearLayout(ac);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        tv = new TextView(ac);
        tv.setText(R.string.setRinger);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.setRingerHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            lll.setPadding((int)(scale * 25.0), 0, 0, 0);
            tv.setPadding(0, (int)(scale * 7.0), 0, 0);
            lll.addView(tv, ww);
        } else {
            lll.setPadding((int)(scale * 50.0), 0, 0, 0);
            tv.setPadding((int)(scale * 25.0), 0, 0, 0);
            ll.addView(tv, ww);
        }
        ringerAction = new RadioGroup(ac);
        ringerAction.setOrientation(LinearLayout.HORIZONTAL);
        int ra = PrefsManager.getRingerAction(ac, classNum);
        int id = -1;
        RadioButton rb = new RadioButton(ac);
        rb.setText(R.string.normal);
        rb.setId(AudioManager.RINGER_MODE_NORMAL);
        ringerAction.addView(rb, -1, ww);
        if (ra == AudioManager.RINGER_MODE_NORMAL) { ringerAction.check(ra); }
        rb = new RadioButton(ac);
        rb.setText(R.string.vibrate);
        rb.setId(AudioManager.RINGER_MODE_VIBRATE);
        ringerAction.addView(rb, -1, ww);
        if (ra == AudioManager.RINGER_MODE_VIBRATE) { ringerAction.check(ra); }
        rb = new RadioButton(ac);
        rb.setText(R.string.silent);
        rb.setId(AudioManager.RINGER_MODE_SILENT);
        ringerAction.addView(rb, -1, ww);
        if (ra == AudioManager.RINGER_MODE_SILENT) { ringerAction.check(ra); }
        lll.addView(ringerAction, ww);
        ll.addView(lll, ww);
        lll = new LinearLayout(ac);
        lll.setPadding((int)(scale * 25.0), 0, 0, 0);
        showNotification = new CheckBox(ac);
        showNotification.setText(R.string.afficher_notification);
        showNotification.setChecked(PrefsManager.getNotifyStart(ac, classNum));
        showNotification.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.startNotifyHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        lll.addView(showNotification, ww);
        ll.addView(lll, ww);
    }

    @Override
    public void onPause() {
        super.onPause();
        final EditActivity ac = (EditActivity)getActivity();
        int classNum = PrefsManager.getClassNum(
            ac, getArguments().getString(ARG_CLASS_NAME));
        int id = ringerAction.getCheckedRadioButtonId();
        PrefsManager.setRingerAction(ac, classNum, id);
        PrefsManager.setNotifyStart(ac, classNum, showNotification.isChecked());
        ac.setButtonVisibility(View.VISIBLE);
    }
}
