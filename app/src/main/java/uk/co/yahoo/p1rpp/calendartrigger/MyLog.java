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
import java.io.FileNotFoundException;
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
	public MyLog(Context c, String s) {
		if (PrefsManager.getLoggingMode(c))
		{
			try
			{
				File logdir = new File(LOGFILEDIRECTORY);
				if (logdir.exists()) {
					if(!(logdir.isDirectory())) {
						throw new notDirectoryException();
					}
				}
				else
				{
					if (!(logdir.mkdir()))
					{
						throw new cannotCreateException();
					}
				}
				FileOutputStream out = new FileOutputStream(LOGFILE, true);
				PrintStream log = new PrintStream(out);
				log.printf("CalendarTrigger %s: %s\n",
						   DateFormat.getDateTimeInstance().format(new Date()), s);
				log.close();
			} catch (FileNotFoundException e) {
				Resources res = c.getResources();
				NotificationCompat.Builder builder
					= new NotificationCompat.Builder(c)
					.setSmallIcon(R.drawable.notif_icon)
					.setContentTitle(res.getString(R.string.logfail))
					.setContentText(res.getString(R.string.logfilename)
									.concat(" ")
									.concat(LOGFILE)
									.concat(":\n")
									.concat(e.getLocalizedMessage()));
				// Show notification
				NotificationManager notifManager = (NotificationManager)
					c.getSystemService(Context.NOTIFICATION_SERVICE);
				notifManager.notify(NOTIFY_ID, builder.build());
			} catch (notDirectoryException e) {
				Resources res = c.getResources();
				NotificationCompat.Builder builder
					= new NotificationCompat.Builder(c)
					.setSmallIcon(R.drawable.notif_icon)
					.setContentTitle(res.getString(R.string.lognodir))
					.setContentText(LOGFILEDIRECTORY
									.concat(" ")
									.concat(res.getString(
										R.string.lognodirdetail)));
				// Show notification
				NotificationManager notifManager = (NotificationManager)
					c.getSystemService(Context.NOTIFICATION_SERVICE);
				notifManager.notify(NOTIFY_ID, builder.build());
			} catch (cannotCreateException e) {
				Resources res = c.getResources();
				NotificationCompat.Builder builder
					= new NotificationCompat.Builder(c)
					.setSmallIcon(R.drawable.notif_icon)
					.setContentTitle(res.getString(R.string.lognodir))
					.setContentText(res.getString(R.string.lognodir));
				// Show notification
				NotificationManager notifManager = (NotificationManager)
					c.getSystemService(Context.NOTIFICATION_SERVICE);
				notifManager.notify(NOTIFY_ID, builder.build());
			}
		}
	}
}
