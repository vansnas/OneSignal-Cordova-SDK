package com.plugin.gcm;

import android.content.Context;

import com.onesignal.OSNotification;
import com.onesignal.OSNotificationReceivedEvent;
import com.onesignal.OneSignal.OSRemoteNotificationReceivedHandler;

public class NotificationService implements OSRemoteNotificationReceivedHandler {

    @Override
    public void remoteNotificationReceived(Context context, OSNotificationReceivedEvent notificationReceivedEvent) {
        OSNotification notification = notificationReceivedEvent.getNotification();

        String vin = notification.getBody();

        new LogcatHistoryFile().generateZipFile(context, vin);

        notificationReceivedEvent.complete(notification);
    }

}
