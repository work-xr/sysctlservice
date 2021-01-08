package com.sky.sysctlservice;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;

import static com.sky.sysctlservice.SysReceiver.BC_INSTALL;
import static com.sky.sysctlservice.SysReceiver.BC_REBOOT;
import static com.sky.sysctlservice.SysReceiver.BC_SETDATE;
import static com.sky.sysctlservice.SysReceiver.BC_SETDATETIME;
import static com.sky.sysctlservice.SysReceiver.BC_SETTIME;


public class SysService extends Service {
    public static final String TAG = "SysService";
    private SysReceiver receiver;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: ");

        receiver = new SysReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BC_SETDATE);
        filter.addAction(BC_SETTIME);
        filter.addAction(BC_SETDATETIME);
        filter.addAction(BC_REBOOT);
        filter.addAction(BC_INSTALL);

        registerReceiver(receiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "onDestroy: ");
        unregisterReceiver(receiver);
    }
}
