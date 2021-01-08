package com.sky.sysctlservice;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StartupReceiver extends BroadcastReceiver {
    private static final String TAG = "StartupReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        Log.d(TAG, "Receive action : " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            //Intent service_intent = new Intent();
            //service_intent.setComponent(new ComponentName("com.sky.sysctlservice", "com.sky.sysctlservice.SysService"));
            context.getApplicationContext().startService(new Intent(context.getApplicationContext(), SysService.class));
        }
    }
}
