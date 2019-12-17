/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.utilities;

import android.content.Context;
import android.os.Looper;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;

import uk.co.yahoo.p1rpp.calendartrigger.R;

public class MyLog extends Object {

	public MyLog(Context context, String s, boolean noprefix) {
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
				String small = context.getResources().getString(R.string.nowrite, type);
				String big = DataStore.LogFileName() + " " + e.getLocalizedMessage();
				new Notifier(context, small, big);
			}
		}
	}
	public MyLog(Context context, String s) {
		new MyLog(context, s, false);
	}
	public MyLog(Context context, String small, String big) {
		new Notifier(context, small, big);
		new MyLog(context, big, false);
	}
}
