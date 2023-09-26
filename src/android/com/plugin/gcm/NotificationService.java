package com.plugin.gcm;

import android.util.Log;

import com.onesignal.OSNotification;
import com.onesignal.OneSignal.NotificationReceivedHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class NotificationService implements NotificationReceivedHandler {

    private static String TAG = "NotificationReceivedHandler";

    @Override
    public void notificationReceived(OSNotification notification) {
        //OSNotification notification = notificationReceivedEvent.getNotification();

        JSONObject data = notification.toJSONObject();

        String innerJsonString = data.optString("this");

        JSONObject innerJson = null;
        try {

            innerJson = new JSONObject(innerJsonString);

            String VIN = innerJson.optString("VIN");
            String ClientId = innerJson.optString("ClientId");
            String ClientSecret = innerJson.optString("ClientSecret");
            String TennantId = innerJson.optString("TennantId");


            JSONArray jsonArray = new JSONArray();
            jsonArray.put(VIN);
            jsonArray.put(ClientId);
            jsonArray.put(ClientSecret);
            jsonArray.put(TennantId);

            //new LogcatHistoryFile().generateZipFile(this, VIN, ClientId, ClientSecret, TennantId);
            new OneSignalPush.execute("generateZipFile", jsonArray, null);
        } catch (JSONException e) {
            Log.e(TAG, "Something went wrong while receiving notification", e);
            throw new RuntimeException(e);
        }

        //notificationReceivedEvent.complete(notification);
    }

}
