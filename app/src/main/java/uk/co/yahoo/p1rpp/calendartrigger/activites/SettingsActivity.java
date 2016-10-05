/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.activites;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.DateFormat;

import uk.co.yahoo.p1rpp.calendartrigger.MyLog;
import uk.co.yahoo.p1rpp.calendartrigger.PrefsManager;
import uk.co.yahoo.p1rpp.calendartrigger.R;
import uk.co.yahoo.p1rpp.calendartrigger.service.MuteService;

/**
 * Created by rparkins on 29/08/16.
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
    }

    private void doReset() {
		int n = PrefsManager.getNumClasses(this);
		int classNum;
		for (classNum = 0; classNum < n; ++classNum)
        {
            if (PrefsManager.isClassUsed(this, classNum))
            {
				PrefsManager.setClassTriggered(this, classNum, false);
                PrefsManager.setClassActive(this, classNum, false);
				PrefsManager.setClassWaiting(this, classNum, false);
            }
        }
        String s = "CalendarTrigger.Location";
        PendingIntent pi = PendingIntent.getBroadcast(
            this, 0 /*requestCode*/,
            new Intent(s, Uri.EMPTY, this, MuteService.StartServiceReceiver.class),
            PendingIntent.FLAG_UPDATE_CURRENT);
        LocationManager lm = (LocationManager)getSystemService(
            Context.LOCATION_SERVICE);
        lm.removeUpdates(pi);
        MuteService.doReset();
    }

    @Override
    protected void onResume() {
        final SettingsActivity me = this;
        super.onResume();
        TextView tv = (TextView)findViewById (R.id.lastcalltext);
        DateFormat df = DateFormat.getDateTimeInstance();
        long t = PrefsManager.getLastInvocationTime(this);
        tv.setText(getString(R.string.lastcalldetail, df.format(t)));
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.lastCallHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv = (TextView)findViewById (R.id.lastalarmtext);
        t = PrefsManager.getLastAlarmTime(this);
        tv.setText(getString(R.string.lastalarmdetail, df.format(t)));
        tv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.lastAlarmHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        tv = (TextView)findViewById (R.id.logfiletext);
        String s = MyLog.LogFileName();
        tv.setText(getString(R.string.Logging, s));
        Button b = (Button)findViewById(R.id.radioLoggingOn);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PrefsManager.setLoggingMode(me, true);
            }
        });
        b = (Button)findViewById(R.id.radioLoggingOff);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                PrefsManager.setLoggingMode(me, false);
            }
        });
        RadioGroup rg = (RadioGroup)findViewById(R.id.radioGroupLogging);
        rg.check(PrefsManager.getLoggingMode(me)
                 ? R.id.radioLoggingOn : R.id.radioLoggingOff);
        b = (Button)findViewById(R.id.clear_log);
        b.setText(R.string.clearLog);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                (new File(MyLog.LogFileName())).delete();
                Toast.makeText(me, R.string.logCleared, Toast.LENGTH_SHORT)
                     .show();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.clearLogHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        b = (Button)findViewById(R.id.show_log);
        b.setText(R.string.showLog);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Uri u = (new Uri.Builder())
                        .scheme("file")
                        .appendEncodedPath(MyLog.LogFileName())
                        .build();
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(u, "text/plain");
                ComponentName c = i.resolveActivity(getPackageManager());
                if (c != null)
                {
                    startActivity(i);
                }
                else
                {
                    Toast.makeText(me, R.string.notexteditor,
                                   Toast.LENGTH_LONG).show();
                }
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.showLogHelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
        b = (Button)findViewById(R.id.resetbutton);
        b.setText(R.string.reset);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                doReset();
            }
        });
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(me, R.string.resethelp,
                               Toast.LENGTH_LONG).show();
                return true;
            }
        });
    }
}
