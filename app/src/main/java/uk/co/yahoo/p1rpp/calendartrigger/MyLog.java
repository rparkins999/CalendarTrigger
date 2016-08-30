package uk.co.yahoo.p1rpp.calendartrigger;

import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;

public class MyLog extends Object {

	private static final int NOTIFY_ID = 1427;
	private static final String LOGFILE
		= Environment.getExternalStorageDirectory().getPath()
					 .concat("/data/CalendarTriggerLog.txt");
	public static String LogFileName() {
		return LOGFILE;
	}
	public MyLog(Context c, String s) {
		if (PrefsManager.getLoggingMode(c))
		{
			try
			{
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
									.concat(LOGFILE)
									.concat(":\n")
									.concat(e.getLocalizedMessage()));
				// Show notification
				NotificationManager notifManager = (NotificationManager)
					c.getSystemService(Context.NOTIFICATION_SERVICE);
				notifManager.notify(NOTIFY_ID, builder.build());
			}
		}
	}

	public static String rm(int mode) {
		switch (mode) {
			case AudioManager.RINGER_MODE_NORMAL:
				return "RINGER_MODE_NORMAL";
			case AudioManager.RINGER_MODE_VIBRATE:
				return "RINGER_MODE_VIBRATE";
			case AudioManager.RINGER_MODE_SILENT:
				return "RINGER_MODE_SILENT";
			case PrefsManager.RINGER_MODE_NONE:
				return "NONE";
			default:
				return "<unknown ringer mode>";
		}
	}
}
