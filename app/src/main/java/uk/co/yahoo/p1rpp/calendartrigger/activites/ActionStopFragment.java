/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;

/**
 * Created by rparkins on 05/07/16.
 */
public class ActionStopFragment extends Fragment {
    private static final String ARG_CLASS_NAME = "class name";
    private float scale;
    private CheckBox ringerRestore;
    private CheckBox showNotification;

    public ActionStopFragment() {
    }

    public static ActionStopFragment newInstance(String className ) {
        ActionStopFragment fragment = new ActionStopFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CLASS_NAME, className);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        View rootView =
            inflater.inflate(R.layout.fragment_action_stop, container, false);
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
            (LinearLayout)ac.findViewById(R.id.actionstoplayout);
        ll.removeAllViews();
        TextView tv = new TextView(ac);
        tv.setText(R.string.longpresslabel);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac,
                               fromHtml(getString(R.string.actionstoppopup,
                                                  className)),
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        ll.addView(tv, ww);
        tv = new TextView(ac);
        tv.setText(fromHtml(getString(R.string.actionstoplist, className)));
        ll.addView(tv, ww);
        LinearLayout lll = new LinearLayout(ac);
        lll.setOrientation(LinearLayout.VERTICAL);
        lll.setPadding((int)(scale * 25.0), 0, 0, 0);
        ringerRestore = new CheckBox(ac);
        ringerRestore.setText(R.string.restaurer_etat_precedent);
        ringerRestore.setChecked(PrefsManager.getRestoreRinger(ac, classNum));
        ringerRestore.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.restoreRingerHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        lll.addView(ringerRestore, ww);
        showNotification = new CheckBox(ac);
        showNotification.setText(R.string.afficher_notification);
        showNotification.setChecked(PrefsManager.getNotifyEnd(ac, classNum));
        showNotification.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.endNotifyHelp,
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
        PrefsManager.setRestoreRinger(ac, classNum, ringerRestore.isChecked());
        PrefsManager.setNotifyEnd(ac, classNum, showNotification.isChecked());
        ac.setButtonVisibility(View.VISIBLE);
    }
}
