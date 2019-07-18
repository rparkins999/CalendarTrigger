/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;

public class DataStore extends Object {
    public static final int NOTIFY_ID = 1427;
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
    private static final String DATABASEFILE
        = DATADIRECTORY.concat("/").concat(DATAPREFIX)
            .concat("FloatingTimeEvents.sqlite");
    public static String DatabaseFileName() {
        return DATABASEFILE;
    }

    public static boolean ensureDataDirectory(
        Context context, String type, boolean background) {
        File dataDir = new File(DATADIRECTORY);
        if (dataDir.exists())
        {
            if (!(dataDir.isDirectory()))
            {
                Resources res = context.getResources();
                String s = DATADIRECTORY.concat(" ")
                           .concat(res.getString(R.string.lognodirdetail));
                if (background)
                {
                    Notification.Builder builder
                        = new Notification.Builder(context)
                        .setSmallIcon(R.drawable.notif_icon)
                        .setContentTitle(res.getString(R.string.lognodir, type))
                        .setContentText(s);
                    // Show notification
                    NotificationManager notifManager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notifManager.notify(NOTIFY_ID, builder.build());
                }
                else
                {
                    Toast.makeText(context, s, Toast.LENGTH_LONG).show();
                }
                return false;
            }
        }
        else if (!dataDir.mkdir())
        {
            Resources res = context.getResources();
            Notification.Builder builder
                    = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.notif_icon)
                    .setContentTitle(res.getString(R.string.lognodir, type))
                    .setContentText(DATADIRECTORY
                            .concat(" ")
                            .concat(res.getString(
                                    R.string.nocreatedetail)));
            // Show notification
            NotificationManager notifManager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            notifManager.notify(NOTIFY_ID, builder.build());
            return false;
        }
        return true;
    }

    public static SQLiteDatabase getFloatingEvents
        (Context context, boolean background) {
        SQLiteDatabase floatingEvents;
        String dbName = DataStore.DatabaseFileName();
        if (ensureDataDirectory(context, "Database", background))
        {
            try {
                floatingEvents = SQLiteDatabase.openOrCreateDatabase(
                    dbName, null, null);
                floatingEvents.execSQL(
                    "CREATE TABLE IF NOT EXISTS FLOATINGEVENTS "
                        + "(EVENT_ID INTEGER, START_WALLTIME_MILLIS INTEGER,"
                        + "END_WALLTIME_MILLIS INTEGER)"
                );
                return floatingEvents;
            } catch (SQLiteException e)
            {
                String s = dbName.concat(": ").concat(e.toString());
                new MyLog(context, s);
                if (background)
                {
                    Resources res = context.getResources();
                    Notification.Builder builder
                        = new Notification.Builder(context)
                        .setSmallIcon(R.drawable.notif_icon)
                        .setContentTitle(res.getString(R.string.nowrite, dbName))
                        .setContentText(s);
                    // Show notification
                    NotificationManager notifManager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notifManager.notify(NOTIFY_ID, builder.build());
                }
                else
                {
                    Toast.makeText(context, s, Toast.LENGTH_LONG).show();
                }
            } catch (SQLException e)
            {
                String s = dbName + " SQL error:" + e.toString();
                new MyLog(context, s);
                if (background)
                {
                    Resources res = context.getResources();
                    Notification.Builder builder
                        = new Notification.Builder(context)
                        .setSmallIcon(R.drawable.notif_icon)
                        .setContentTitle(res.getString(R.string.tablecreatefail))
                        .setContentText(s);
                    // Show notification
                    NotificationManager notifManager = (NotificationManager)
                        context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notifManager.notify(NOTIFY_ID, builder.build());
                }
                else
                {
                    Toast.makeText(context, s, Toast.LENGTH_LONG).show();
                }
            }
        }
        return null;
    }
}
