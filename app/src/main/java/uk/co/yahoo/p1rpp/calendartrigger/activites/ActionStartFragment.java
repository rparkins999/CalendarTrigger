/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.Widgets.DisabledCheckBox;
import uk.co.yahoo.p1rpp.calendartrigger.Widgets.DisabledRadioButton;
import uk.co.yahoo.p1rpp.calendartrigger.utilities.SQLtable;

import static android.text.Html.fromHtml;
import static android.text.TextUtils.htmlEncode;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static android.view.Gravity.CENTER_VERTICAL;

/**
 * Created by rparkins on 05/07/16.
 */
public class ActionStartFragment extends ActionFragment {
    private static final String ARG_CLASS_NAME = "class name";

    private CheckBox m_vibrateOn;
    private CheckBox m_vibrateOff;
    private CheckBox m_muteRinger;
    private CheckBox m_muteAlarms;
    private DisabledRadioButton m_dndNormal;
    private DisabledRadioButton m_dndPriority;
    private DisabledRadioButton m_dndAlarms;
    private DisabledRadioButton m_dndSilent;
    private EditActivity m_owner;
    private SQLtable m_classModes;

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
    public void openThis(File file) {
        final EditActivity ac = (EditActivity)getActivity();
        PrefsManager.setDefaultDir(ac, file.getParent());
        int classNum = PrefsManager.getClassNum(
            ac, getArguments().getString(ARG_CLASS_NAME));
        PrefsManager.setSoundFileStart(
            ac, classNum, file.getPath());
        getFragmentManager().popBackStack();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        View rootView =
            inflater.inflate(R.layout.dynamicscrollview, container, false);
        scale = getResources().getDisplayMetrics().density;
        return rootView;
    }

    private void adjustDND() {
        if (   m_vibrateOn.isChecked()
            || m_vibrateOff.isChecked()
            || m_muteRinger.isChecked()
            || m_muteAlarms.isChecked())
        {
            m_dndNormal.setChecked(true);
            m_dndNormal.setEnabled(false);
            m_dndPriority.setChecked(false);
            m_dndPriority.setEnabled(false);
            m_dndAlarms.setChecked(false);
            m_dndAlarms.setEnabled(false);
            m_dndSilent.setChecked(false);
            m_dndSilent.setEnabled(false);
            m_dndNormal.setEnabled(false);
            m_dndPriority.setEnabled(false);
            m_dndAlarms.setEnabled(false);
            m_dndSilent.setEnabled(false);
            View.OnLongClickListener longClickListener = new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(m_owner,
                        "Do-not-disturb modes cannot be combined"
                            + " with any mute or vibrate mode: uncheck all"
                            + " those modes to enable this button.",
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            };
            m_dndNormal.setOnLongClickListener(longClickListener);
            m_dndPriority.setOnLongClickListener(longClickListener);
            m_dndAlarms.setOnLongClickListener(longClickListener);
            m_dndSilent.setOnLongClickListener(longClickListener);
        }
        else
        {
            m_dndNormal.setEnabled(true);
            m_dndPriority.setEnabled(true);
            m_dndAlarms.setEnabled(true);
            m_dndSilent.setEnabled(true);
            View.OnClickListener clickListener = new View.OnClickListener() {
                public void onClick(View v) {
                    if (((RadioButton) v).isChecked()) {
                        uncheckMutes();
                    }
                }
            };
            m_dndPriority.setOnClickListener(clickListener);
            m_dndAlarms.setOnClickListener(clickListener);
            m_dndSilent.setOnClickListener(clickListener);
            m_dndNormal.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(m_owner,
                        "Leave do-not-disturb mode as it is.",
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            m_dndPriority.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(m_owner,
                        "Set do-not-disturb mode to allow only"
                            + " priority calls and notifications."
                            + " The do-not-disturb priority mode can be"
                            + " configured from the device's settings page.",
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            m_dndAlarms.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(m_owner,
                        "Set do-not-disturb mode to allow only alarms.",
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            m_dndSilent.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(m_owner,
                        "Set do-not-disturb mode to allow"
                            + " no interruptions at all.",
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
            switch (m_classModes.getStringOK("DO_NOT_DISTURB_MODE"))
            {
                case "ALL": m_dndNormal.setChecked(true); break;
                case "PRIORITY": m_dndPriority.setChecked(true); uncheckMutes(); break;
                case "ALARMS": m_dndAlarms.setChecked(true); uncheckMutes(); break;
                case "NONE": m_dndSilent.setChecked(true); uncheckMutes(); break;
            }
        }
    }

    private void uncheckMutes() {
        m_vibrateOn.setChecked(false);
        m_vibrateOff.setChecked(false);
        m_muteRinger.setChecked(false);
        m_muteAlarms.setChecked(false);
    }

    @TargetApi(android.os.Build.VERSION_CODES.M)
    @Override
    public void onResume() {
        super.onResume();
        m_owner = (EditActivity) getActivity();
        m_owner.setButtonVisibility(View.INVISIBLE);
        boolean need_permission = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
        {
            if (!Settings.System.canWrite(m_owner))
            {
                need_permission = true;
            }
        }
        String s = getArguments().getString(ARG_CLASS_NAME);
        m_classModes = new SQLtable(m_owner, "RINGERDNDMODES",
            "RINGER_CLASS_NAME", s);
        gettingFile = false;
        int classNum = PrefsManager.getClassNum(m_owner, s);
        final String className = "<i>" + htmlEncode(s) + "</i>";
        ViewGroup.LayoutParams ww = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        LinearLayout ll =
            (LinearLayout) m_owner.findViewById(R.id.dynamicscrollview);
        ll.removeAllViews();
        View.OnLongClickListener longClickListener =
            new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(m_owner,
                        fromHtml(getString(R.string.actionstartpopup,
                            className)),
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            };
        TextView tv = new TextView(m_owner);
        tv.setText(R.string.longpresslabel);
        tv.setOnLongClickListener(longClickListener);
        ll.addView(tv, ww);
        tv = new TextView(m_owner);
        tv.setText(fromHtml(getString(R.string.actionstartlist, className)));
        tv.setOnLongClickListener(longClickListener);
        ll.addView(tv, ww);
        LinearLayout lll = new LinearLayout(m_owner);
        lll.setOrientation(LinearLayout.HORIZONTAL);
        lll.setGravity(CENTER_VERTICAL);
        LinearLayout llll = new LinearLayout(m_owner);
        llll.setOrientation(LinearLayout.VERTICAL);
        s = m_classModes.getStringOK("RINGER_VIBRATE");
        m_vibrateOn = new CheckBox(m_owner);
        m_vibrateOn.setText("vibrate");
        m_vibrateOn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                adjustDND();
            }
        });
        if (need_permission) {
            m_vibrateOn.setTextColor(0xF808000);
            m_vibrateOn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(m_owner, "Press Back button when done",
                        Toast.LENGTH_LONG).show();
                    startActivity(new Intent(
                        android.provider.Settings
                            .ACTION_MANAGE_WRITE_SETTINGS));
                }
            });
            m_vibrateOn.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(m_owner,
                        "Vibrate on incoming call."
                            + " CalendarTrigger needs permission WRITE_SETTINGS"
                            + " to set this option: this will be requested if you"
                            + " select it."
                            + " If neither this nor 'no vibrate' is checked,"
                            + " the user's vibrate setting will be unchanged."
                            + " This cannot be combined with any do-not-disturb mode.",
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        else
        {
            m_vibrateOn.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                Toast.makeText(m_owner,
                    "Vibrate on incoming call."
                        + " If neither this nor 'no vibrate' is checked,"
                        + " the user's vibrate setting will be unchanged."
                        + " This cannot be combined with any do-not-disturb mode.",
                    Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        m_vibrateOn.setChecked(s.equals("ON"));
        llll.addView(m_vibrateOn, ww);
        m_vibrateOff = new CheckBox(m_owner);
        m_vibrateOff.setText("no vibrate");
        m_vibrateOff.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                adjustDND();
            }
        });
        if (need_permission) {
            m_vibrateOff.setTextColor(0xF808000);
            m_vibrateOff.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(m_owner, "Press Back button when done",
                        Toast.LENGTH_LONG).show();
                    startActivity(new Intent(
                        android.provider.Settings
                            .ACTION_MANAGE_WRITE_SETTINGS));
                }
            });
            m_vibrateOff.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(m_owner,
                        "Do not vibrate on incoming call."
                            + " CalendarTrigger needs permission WRITE_SETTINGS"
                            + " to set this option: this will be requested if you"
                            + " select it."
                            + " If neither this nor 'vibrate' is checked,"
                            + " the user's vibrate setting will be unchanged."
                            + " This cannot be combined with any do-not-disturb mode.",
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        else
        {
            m_vibrateOff.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Toast.makeText(m_owner,
                        "Do not vibrate on incoming call."
                            + " If neither this nor 'vibrate' is checked,"
                            + " the user's vibrate setting will be unchanged."
                            + " This cannot be combined with any do-not-disturb mode.",
                        Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        }
        m_vibrateOff.setChecked(s.equals("OFF"));
        llll.addView(m_vibrateOff, ww);
        int vol = m_classModes.getIntegerOK("RINGER_VOLUME");
        m_muteRinger = new CheckBox(m_owner);
        m_muteRinger.setText("mute ringer");
        m_muteRinger.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                adjustDND();
            }
        });
        m_muteRinger.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(m_owner,
                    "Mute the ringer:"
                        + " also mutes system sounds and notification sounds."
                        + " This cannot be combined with any do-not-disturb mode.",
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        m_muteRinger.setChecked(vol == 0);
        llll.addView(m_muteRinger, ww);
        vol = m_classModes.getIntegerOK("ALARM_VOLUME");
        m_muteAlarms = new CheckBox(m_owner);
        m_muteAlarms.setText("mute alarms");
        m_muteAlarms.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                adjustDND();
            }
        });
        m_muteAlarms.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(m_owner,
                    "Mute alarm sounds."
                        + " This cannot be combined with any do-not-disturb mode",
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        m_muteAlarms.setChecked(vol == 0);
        llll.addView(m_muteAlarms, ww);
        lll.addView(llll, ww);
        llll = new LinearLayout(m_owner);
        llll.setOrientation(LinearLayout.VERTICAL);
        tv = new TextView(m_owner);
        tv.setText("  OR  ");
        float f = tv.getTextSize();
        tv.setTextSize(COMPLEX_UNIT_PX, f + f / 2);
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(m_owner,
                    "Muting or vibrating are mutually exclusive"
                        + " with do-not-disturb modes.",
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        llll.addView(tv, ww);
        lll.addView(llll, ww);
        llll = new LinearLayout(m_owner);
        llll.setOrientation(LinearLayout.VERTICAL);
        RadioGroup dndAction = new RadioGroup(m_owner);
        dndAction.setOrientation(LinearLayout.VERTICAL);
        m_dndNormal = new DisabledRadioButton(m_owner);
        m_dndNormal.setText("no change");
        m_dndNormal.setUnclickable(false);
        dndAction.addView(m_dndNormal, -1, ww);
        m_dndPriority = new DisabledRadioButton(m_owner);
        m_dndPriority.setText("priority");
        m_dndPriority.setUnclickable(false);
        dndAction.addView(m_dndPriority, -1, ww);
        m_dndAlarms = new DisabledRadioButton(m_owner);
        m_dndAlarms.setText("alarms");
        m_dndAlarms.setUnclickable(false);
        dndAction.addView(m_dndAlarms, -1, ww);
        m_dndSilent = new DisabledRadioButton(m_owner);
        m_dndSilent.setText("silent");
        m_dndSilent.setUnclickable(false);
        dndAction.addView(m_dndSilent, -1, ww);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
        {
            NotificationManager nm = (NotificationManager)
                m_owner.getSystemService(Context.NOTIFICATION_SERVICE);
            if (   (nm != null)
                && nm.isNotificationPolicyAccessGranted())
            {
                tv = new TextView(m_owner);
                tv.setText(R.string.donotdisturb);
                tv.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Toast.makeText(m_owner,
                            "These buttons allow you to select a do-not-disturb"
                                + " mode at the start of the event."
                                + " This cannot be combined with any"
                                + " muting or vibrate mode",
                            Toast.LENGTH_LONG).show();
                        return true;
                    }
                });
                llll.addView(tv, ww);
                adjustDND();
            }
            else
            {
                Button b = new Button(m_owner);
                b.setText(R.string.donotdisturb);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(m_owner, "Press Back button when done",
                            Toast.LENGTH_LONG).show();
                        startActivity(new Intent(
                            android.provider.Settings
                                .ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    }
                });
                b.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Toast.makeText(m_owner, R.string.requestdisturbhelp,
                            Toast.LENGTH_LONG).show();
                        return true;
                    }
                });
                llll.addView(b, ww);
                longClickListener = new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            Toast.makeText(m_owner, R.string.priorityforbidden,
                                Toast.LENGTH_LONG).show();
                            return true;
                        }
                    };
                m_dndNormal.setOnLongClickListener(longClickListener);
                m_dndPriority.setOnLongClickListener(longClickListener);
                m_dndAlarms.setOnLongClickListener(longClickListener);
                m_dndSilent.setOnLongClickListener(longClickListener);
            }
        }
        else
        {
            longClickListener = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        Toast.makeText(m_owner, R.string.unsupported,
                            Toast.LENGTH_LONG).show();
                        return true;
                    }
                };
            m_dndNormal.setOnLongClickListener(longClickListener);
            m_dndPriority.setOnLongClickListener(longClickListener);
            m_dndAlarms.setOnLongClickListener(longClickListener);
            m_dndSilent.setOnLongClickListener(longClickListener);
        }
        llll.addView(dndAction, ww);
        lll.addView(llll, ww);
        ll.addView(lll, ww);
        showNotification = new CheckBox(m_owner);
        showNotification.setText(R.string.afficher_notification);
        boolean notif = PrefsManager.getNotifyStart(m_owner, classNum);
        showNotification.setChecked(notif);
        showNotification.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(m_owner, R.string.startNotifyHelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        showNotification.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(
                    CompoundButton v, boolean isChecked) {
                    playSound.setEnabled(isChecked);
                    soundFilename.setEnabled(isChecked);
                }
            });
        ll.addView(showNotification, ww);
        lll = new LinearLayout(m_owner);
        lll.setPadding((int) (scale * 40.0), 0, 0, 0);
        playSound = new DisabledCheckBox(m_owner);
        playSound.setEnabled(notif);
        playSound.setText(R.string.playsound);
        playSound.setChecked(PrefsManager.getPlaysoundStart(m_owner, classNum));
        playSound.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(m_owner, R.string.startPlaySoundHelp,
                    Toast.LENGTH_LONG).show();
                return true;
            }
        });
        lll.addView(playSound, ww);
        ll.addView(lll, ww);
        lll = new LinearLayout(m_owner);
        lll.setPadding((int) (scale * 55.0), 0, 0, 0);
        soundFilename = new TextView(m_owner);
        soundFilename.setEnabled(notif);
        String sf = PrefsManager.getSoundFileStart(m_owner, classNum);
        if (sf.isEmpty()) {
            hasFileName = false;
            String browse = "<i>" +
                htmlEncode(getString(R.string.browsenofile)) +
                "</i>";
            soundFilename.setText(fromHtml(browse));
        } else {
            hasFileName = true;
            soundFilename.setText(sf);
        }
        soundFilename.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getFile();
            }
        });
        soundFilename.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (hasFileName) {
                    Toast.makeText(m_owner, R.string.browsefileHelp,
                        Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(m_owner, R.string.browsenofileHelp,
                        Toast.LENGTH_LONG).show();
                }
                return true;
            }
        });
        lll.addView(soundFilename, ww);
        ll.addView(lll, ww);
        layoutSendMessage(ll,  ww, classNum, PrefsManager.SEND_MESSAGE_AT_START);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!gettingFile) {
            m_owner.setButtonVisibility(View.VISIBLE);
        }
        int classNum = PrefsManager.getClassNum(
            m_owner, getArguments().getString(ARG_CLASS_NAME));
        ContentValues cv = new ContentValues();
        cv.put("RINGER_VOLUME", m_muteRinger.isChecked() ? 0 : 1000);
        cv.put("RINGER_VIBRATE",
            m_vibrateOn.isChecked() ? "ON" : (m_vibrateOff.isChecked() ? "OFF" : "-"));
        cv.put("ALARM_VOLUME", m_muteAlarms.isChecked() ? 0 : 1000);
        if (m_dndNormal.isChecked()) { cv.put("DO_NOT_DISTURB_MODE", "ALL"); }
        else if (m_dndPriority.isChecked())
        {
            cv.put("DO_NOT_DISTURB_MODE", "PRIORITY");
        }
        else if (m_dndAlarms.isChecked()) { cv.put("DO_NOT_DISTURB_MODE", "ALARMS"); }
        else if (m_dndSilent.isChecked()) { cv.put("DO_NOT_DISTURB_MODE", "NONE"); }
        m_classModes.update(cv);
        m_classModes.close();
        PrefsManager.setNotifyStart(m_owner, classNum, showNotification.isChecked());
        PrefsManager.setPlaysoundStart(
            m_owner, classNum, playSound.isChecked());
        if (hasFileName) {
            PrefsManager.setSoundFileStart(
                m_owner, classNum, soundFilename.getText().toString());
        }
        else {
            PrefsManager.setSoundFileStart(m_owner, classNum, "");
        }
        saveOnPause(m_owner, classNum, PrefsManager.SEND_MESSAGE_AT_START);
    }
}
