/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger;

import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;

public class MyLog extends Object {

	private class notDirectoryException extends Exception {}
	private class cannotCreateException extends Exception {}

	private static final int NOTIFY_ID = 1427;
	private static final String LOGFILEDIRECTORY
		= Environment.getExternalStorageDirectory().getPath()
					 .concat("/data");
	private static final String LOGFILE
		= LOGFILEDIRECTORY.concat("/CalendarTriggerLog.txt");
	public static String LogFileName() {
		return LOGFILE;
	}
	public static String SettingsFileName() {
		return LOGFILEDIRECTORY + "/CalendarTriggerSettings.txt";
	}

	public static boolean ensureLogDirectory(Context context, String type) {
		File logdir = new File(LOGFILEDIRECTORY);
		if (logdir.exists())
		{
			if (!(logdir.isDirectory()))
			{
				Resources res = context.getResources();
				NotificationCompat.Builder builder
					= new NotificationCompat.Builder(context)
					.setSmallIcon(R.drawable.notif_icon)
					.setContentTitle(res.getString(R.string.lognodir, type))
					.setContentText(LOGFILEDIRECTORY
										.concat(" ")
										.concat(res.getString(
											R.string.lognodirdetail)));
				// Show notification
				NotificationManager notifManager = (NotificationManager)
					context.getSystemService(Context.NOTIFICATION_SERVICE);
				notifManager.notify(NOTIFY_ID, builder.build());
				return false;
			}
		}
		else if (!(logdir.mkdir()))
		{
			Resources res = context.getResources();
			NotificationCompat.Builder builder
				= new NotificationCompat.Builder(context)
				.setSmallIcon(R.drawable.notif_icon)
				.setContentTitle(res.getString(R.string.lognodir, type))
				.setContentText(LOGFILEDIRECTORY
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
	
	public MyLog(Context context, String s, boolean noprefix) {
		if (PrefsManager.getLoggingMode(context))
		{
			String type = context.getResources().getString(R.string.typelog);
			if (ensureLogDirectory(context, type))
			try
			{
				FileOutputStream out = new FileOutputStream(LOGFILE, true);
				PrintStream log = new PrintStream(out);
				if (noprefix)
				{
					log.printf("%s\n", s);
				}
				else
				{
					log.printf("CalendarTrigger %s: %s\n",
						DateFormat.getDateTimeInstance().format(new Date()), s);
				}
				log.close();
			} catch (Exception e) {
				Resources res = context.getResources();
				NotificationCompat.Builder builder
					= new NotificationCompat.Builder(context)
					.setSmallIcon(R.drawable.notif_icon)
					.setContentTitle(res.getString(R.string.nowrite, type))
					.setContentText(LOGFILE + ": " + e.getMessage());
				// Show notification
				NotificationManager notifManager = (NotificationManager)
					context.getSystemService(Context.NOTIFICATION_SERVICE);
				notifManager.notify(NOTIFY_ID, builder.build());

			}
		}
	}
	public MyLog(Context context, String s) {
		new MyLog(context, s, false);
	}
}
