/*
 * Copyright (c) 2016. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;

public class MyLog extends Object {

	public MyLog(Context context, String s, boolean noprefix) {
		if (PrefsManager.getLoggingMode(context))
		{
			String type = context.getResources().getString(R.string.typelog);
			if (DataStore.ensureDataDirectory(context, type, true))
			try
			{
				FileOutputStream out
                    = new FileOutputStream(DataStore.LogFileName(), true);
				PrintStream log = new PrintStream(out);
				if (noprefix)
				{
					log.printf("%s\n", s);
				}
				else
				{
					log.printf(DataStore.DATAPREFIX + "%s: %s\n",
						DateFormat.getDateTimeInstance().format(
                            new Date()), s);
				}
				log.close();
			} catch (Exception e) {
				Resources res = context.getResources();
				Notification.Builder builder
					= new Notification.Builder(context)
					.setSmallIcon(R.drawable.notif_icon)
					.setContentTitle(res.getString(R.string.nowrite, type))
					.setContentText(DataStore.LogFileName() + ": " + e.getMessage());
				// Show notification
				NotificationManager notifManager = (NotificationManager)
					context.getSystemService(Context.NOTIFICATION_SERVICE);
				notifManager.notify(DataStore.NOTIFY_ID, builder.build());

			}
		}
	}
	public MyLog(Context context, String s) {
		new MyLog(context, s, false);
	}
}
