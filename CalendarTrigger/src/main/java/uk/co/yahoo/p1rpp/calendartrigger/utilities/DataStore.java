/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.utilities;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;

import uk.co.yahoo.p1rpp.calendartrigger.R;

public class DataStore extends Object {
    private static final String DATADIRECTORY
        = Environment.getExternalStorageDirectory().getPath().concat("/data");
    public static final String DATAPREFIX = "CalendarTrigger ";
    private static final String LOGFILE
        = DATADIRECTORY.concat("/").concat(DATAPREFIX).concat("Log.txt");
    public static String LogFileName() {
        return LOGFILE;
    }
    private static final String SETTINGSFILE
        = DATADIRECTORY.concat("/").concat(DATAPREFIX).concat("Settings.txt");
    public static String SettingsFileName() {
        return SETTINGSFILE;
    }
    private static final String OLDDATABASEFILE
        = DATADIRECTORY.concat("/").concat(DATAPREFIX)
            .concat("FloatingTimeEvents.sqlite");
    private static final String DATABASEFILE
        = DATADIRECTORY.concat("/").concat(DATAPREFIX)
        .concat("Database.sqlite");

    private static void report(
        Context context, String small, String big, String type) {
        if (type == context.getString(R.string.typelog))
        {
            // We can't use log in this case because we are reporting failure to create it.
            new Notifier(context, small, big);
        }
        else
        {
            new MyLog(context, small, big);
        }
    }

    public static boolean ensureDataDirectory(Context context, String type) {
        File dataDir = new File(DATADIRECTORY);
        if (dataDir.exists())
        {
            if (!(dataDir.isDirectory()))
            {
                String small = context.getString(R.string.lognodir, type);
                String big = small + context.getString(R.string.because) +
                             DATADIRECTORY + " " +
                             context.getString(R.string.lognodirdetail);
                report(context, small, big, type);
                return false;
            }
        }
        else if (!dataDir.mkdir())
        {
            String small = context.getString(R.string.lognodir, type);
            String big = small + context.getString(R.string.because) +
                         DATADIRECTORY + " " +
                         context.getString(R.string.nocreatedetail);
            report(context, small, big, type);
            return false;
        }
        return true;
    }

    public static String getDatabaseFile(Context context) {
        if (ensureDataDirectory(context, "Database")) {
            File f1 = new File(OLDDATABASEFILE);
            File f2 = new File(DATABASEFILE);
            if ((!f2.exists()) && f1.exists()) {
                try {
                    if (!f1.renameTo(f2)) {
                        String big = DataStore.DATABASEFILE +
                            context.getString(R.string.norename1)
                            + DataStore.OLDDATABASEFILE
                            + context.getString(R.string.norename2);
                        report(context, context.getString(
                            R.string.norename, OLDDATABASEFILE), big, "Database");
                        return null;
                    }
                } catch (NullPointerException e) { /* can't actually happen' */ }
            }
            return DATABASEFILE;
        }
        else { return null; }
    }
}
