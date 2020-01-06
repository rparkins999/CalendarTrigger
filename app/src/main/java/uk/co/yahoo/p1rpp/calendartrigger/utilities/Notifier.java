/*
 * Copyright (c) 2019. Richard P. Parkins, M. A.
 * Released under GPL V3 or later
 */

package uk.co.yahoo.p1rpp.calendartrigger.utilities;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.text.DateFormat;

import uk.co.yahoo.p1rpp.calendartrigger.R;

public class Notifier extends Object {
    private static final int NOTIFY_ID = 1427;

    private void makeit(Context context, String small, String big, String path) {
        if ((context instanceof Activity)
            && ((Activity) context).hasWindowFocus()) {
            Toast.makeText(context, big, Toast.LENGTH_LONG).show();
        } else {
            NotificationManager notifManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notifManager != null) {
                RemoteViews layout = new RemoteViews(
                    "uk.co.yahoo.p1rpp.calendartrigger",
                    R.layout.notification);
                layout.setTextViewText(R.id.notificationtext, big);
                DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT);
                layout.setTextViewText(R.id.notificationtime,
                    df.format(System.currentTimeMillis()));
                Notification.Builder builder
                    = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.notif_icon)
                    .setContentTitle(small)
                    .setContent(layout);
                if ((path != null) && !path.isEmpty()) {
                    Uri uri = new Uri.Builder().path(path).build();
                    AudioAttributes.Builder ABuilder
                        = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                        .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION);
                    builder.setSound(uri, ABuilder.build());
                }
                notifManager.notify(NOTIFY_ID, builder.build());
            }
        }
    }

    public Notifier(Context context, String small, String big, String path) {
        makeit(context, small, big, path);
    }

    public Notifier(Context context, String small, String big) {
        makeit(context, small, big, null);
    }
}
