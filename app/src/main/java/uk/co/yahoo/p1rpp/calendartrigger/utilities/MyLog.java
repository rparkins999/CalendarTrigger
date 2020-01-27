/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.utilities;

import android.content.Context;

import java.io.Console;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;

import uk.co.yahoo.p1rpp.calendartrigger.R;

public class MyLog extends Object {

	private void logIt(Context context, String big, boolean noprefix) {
		String type = context.getResources().getString(R.string.typelog);
		if (   (PrefsManager.getLoggingMode(context))
			&& DataStore.ensureDataDirectory(context, type))
		{
			try
			{
				FileOutputStream out
					= new FileOutputStream(DataStore.LogFileName(), true);
				PrintStream log = new PrintStream(out);
				if (noprefix)
				{
					log.printf("%s\n", big);
				}
				else
				{
					log.printf(DataStore.DATAPREFIX + "%s: %s\n",
						DateFormat.getDateTimeInstance().format(
							new Date()), big);
				}
				log.close();
			} catch (Exception e) {
				String small = context.getResources().getString(R.string.nowrite, type);
				big = DataStore.LogFileName() + " " + e.getLocalizedMessage();
				new Notifier(context, small, big);
			}
		}
	}

	public MyLog(Context context, String big, boolean noprefix) {
		logIt(context, big, noprefix);
	}
	public MyLog(Context context, String big) {
		logIt(context, big, false);
	}
	public MyLog(Context context, String small, String big) {
		new Notifier(context, small, big);
		logIt(context, big, false);
	}
	public MyLog(Context context, String small, String big, String path) {
		new Notifier(context, small, big, path);
		logIt(context, big, false);
	}

	// Always invoked with fatal = true
	// Report a fatal error.
	// We send a message to the log file (if logging is enabled).
	// If this thread is the UI thread, we display a Toast:
	// otherwise we show a notification.
	// Then we throw an Error exception which will cause Android to
	// terminate the thread and display a (not so helpful) message.
	public MyLog(Context context,
				 @SuppressWarnings("unused") boolean fatal, String small, String big) {
		new Notifier(context, small, big);
		logIt(context, big, false);
		throw(new Error());
	}
}
