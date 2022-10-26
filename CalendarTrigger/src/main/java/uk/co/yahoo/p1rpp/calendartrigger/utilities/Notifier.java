/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.utilities;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Looper;
import android.widget.Toast;

import uk.co.yahoo.p1rpp.calendartrigger.R;

public class Notifier extends Object {
    private static final int NOTIFY_ID = 1427;

    public Notifier(Context context, String small, String big) {
        if (   (Activity.class.isInstance(context))
            && ((Activity)context).hasWindowFocus()) {
            Toast.makeText(context, big, Toast.LENGTH_LONG).show();
        }
        else
        {
            Notification.Builder builder
                = new Notification.Builder(context)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(small)
                .setStyle(new Notification.BigTextStyle().bigText(big));
            NotificationManager notifManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
            notifManager.notify(NOTIFY_ID, builder.build());
        }
    }
}
