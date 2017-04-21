/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
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

import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
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

	@TargetApi(android.os.Build.VERSION_CODES.M)
    @Override
    public void onResume() {
        super.onResume();
        final EditActivity ac = (EditActivity)getActivity();
        ac.setButtonVisibility(View.INVISIBLE);
        int apiVersion = android.os.Build.VERSION.SDK_INT;
        NotificationManager nm = (NotificationManager)
            ac.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean havePermission;
        if (apiVersion >= android.os.Build.VERSION_CODES.M)
        {
            havePermission = nm.isNotificationPolicyAccessGranted();
        }
        else
        {
            havePermission = false;
        }
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
        lll.setPadding((int)(scale * 25.0), 0, 0, 0);
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
        tv.setPadding((int)(scale * 10.0), (int)(scale * 7.0), 0, 0);
        lll.addView(tv, ww);
        ringerAction = new RadioGroup(ac);
        ringerAction.setOrientation(LinearLayout.VERTICAL);
        int ra = PrefsManager.getRingerAction(ac, classNum);
        RadioButton normalButton = new RadioButton(ac);
        normalButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.normalhelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        normalButton.setText(R.string.normal);
        normalButton.setId(PrefsManager.RINGER_MODE_NORMAL);
        ringerAction.addView(normalButton, -1, ww);
        if (ra == PrefsManager.RINGER_MODE_NORMAL)
        {
            ringerAction.check(ra);
        }
        RadioButton vibrateButton = new RadioButton(ac);
        vibrateButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(ac, R.string.vibratehelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        vibrateButton = new RadioButton(ac);
        vibrateButton.setText(R.string.vibrate);
        vibrateButton.setId(PrefsManager.RINGER_MODE_VIBRATE);
        ringerAction.addView(vibrateButton, -1, ww);
        if (ra == PrefsManager.RINGER_MODE_VIBRATE)
        {
            ringerAction.check(ra);
        }
        RadioButton dndButton;
        if (apiVersion >= android.os.Build.VERSION_CODES.M)
        {
            if (havePermission)
            {
                dndButton = new RadioButton(ac);
                dndButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Toast.makeText(ac, R.string.priorityhelp,
                                       Toast.LENGTH_LONG).show();
                        return true;
                    }
                });
            }
            else
            {
                dndButton = new DisabledRadioButton(ac);
                dndButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Toast.makeText(ac, R.string.priorityforbidden,
                                       Toast.LENGTH_LONG).show();
                        return true;
                    }
                });
            }
        }
        else
        {
            dndButton = new DisabledRadioButton(ac);
            dndButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, R.string.unsupported,
                                   Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        dndButton.setText(R.string.priority);
        dndButton.setId(PrefsManager.RINGER_MODE_DO_NOT_DISTURB);
        ringerAction.addView(dndButton, -1, ww);
        if (ra == PrefsManager.RINGER_MODE_DO_NOT_DISTURB)
        {
            ringerAction.check(ra);
        }
        RadioButton mutedButton;
        if (apiVersion >= android.os.Build.VERSION_CODES.M)
        {
            mutedButton = new DisabledRadioButton(ac);
            mutedButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, R.string.unsupported,
                                   Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        else
        {
            mutedButton = new RadioButton(ac);
            mutedButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, R.string.mutedhelp,
                                   Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        mutedButton.setText(R.string.muted);
        mutedButton.setId(PrefsManager.RINGER_MODE_MUTED);
        ringerAction.addView(mutedButton, -1, ww);
        if (ra == PrefsManager.RINGER_MODE_MUTED)
        {
            ringerAction.check(ra);
        }
        RadioButton alarmsButton;
        if (apiVersion >= android.os.Build.VERSION_CODES.M)
        {
            if (havePermission)
            {
                alarmsButton = new RadioButton(ac);
                alarmsButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Toast.makeText(ac, R.string.alarmshelp,
                                       Toast.LENGTH_LONG).show();
                        return true;
                    }
                });
            }
            else
            {
                alarmsButton = new DisabledRadioButton(ac);
                alarmsButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Toast.makeText(ac, R.string.alarmsforbidden,
                                       Toast.LENGTH_LONG).show();
                        return true;
                    }
                });
            }
        }
        else
        {
            alarmsButton = new DisabledRadioButton(ac);
            alarmsButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, R.string.unsupported,
                                   Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        alarmsButton.setText(R.string.alarms);
        alarmsButton.setId(PrefsManager.RINGER_MODE_ALARMS);
        ringerAction.addView(alarmsButton, -1, ww);
        if (ra == PrefsManager.RINGER_MODE_ALARMS)
        {
            ringerAction.check(ra);
        }
        RadioButton silentButton;
        if (apiVersion >= android.os.Build.VERSION_CODES.M)
        {
            if (havePermission)
            {
                silentButton = new RadioButton(ac);
                silentButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Toast.makeText(ac, R.string.silenthelp,
                                       Toast.LENGTH_LONG).show();
                        return true;
                    }
                });
            }
            else
            {
                silentButton = new DisabledRadioButton(ac);
                silentButton.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Toast.makeText(ac, R.string.silentforbidden,
                                       Toast.LENGTH_LONG).show();
                        return true;
                    }
                });
            }
        }
        else
        {
            silentButton = new DisabledRadioButton(ac);
            silentButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(ac, R.string.unsupported,
                                   Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        silentButton.setText(R.string.silent);
        silentButton.setId(PrefsManager.RINGER_MODE_SILENT);
        ringerAction.addView(silentButton, -1, ww);
        if (ra == PrefsManager.RINGER_MODE_SILENT)
        {
            ringerAction.check(ra);
        }
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
