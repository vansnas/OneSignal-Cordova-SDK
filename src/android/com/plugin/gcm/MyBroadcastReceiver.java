package com.plugin.gcm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "LogCatReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) {
            Log.e(TAG, "Received null context or intent, cannot proceed.");
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "onReceive: action=" + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            // Intent for starting the foreground service
            Intent serviceIntent = new Intent(context, MyForegroundService.class);
            try {
                context.startForegroundService(serviceIntent);
                Log.i(TAG, "Foreground service started successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start foreground service", e);
            }
        }
    }
}
