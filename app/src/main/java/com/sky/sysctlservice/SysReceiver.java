package com.sky.sysctlservice;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;

public class SysReceiver extends BroadcastReceiver {
    public static final String TAG = "SysReceiver";

    public static final String BC_SETDATE = "sysctl.BROADCAST_SETDATE";
    public static final String BC_SETTIME = "sysctl.BROADCAST_SETTIME";
    public static final String BC_SETDATETIME = "sysctl.BROADCAST_SETDATETIME";
    public static final String BC_REBOOT = "sysctl.BROADCAST_REBOOT";
    public static final String BC_INSTALL = "sysctl.BROADCAST_INSTALL";
    private static final String ACTION_INSTALL_COMMIT = "com.sky.sysctlservice.INTENT_PACKAGE_INSTALL_COMMIT";
    private static final String ACTION_INSTALL_STATUS = "com.sky.sysctlservice.INTENT_PACKAGE_INSTALL_STATUS";

    protected static final int PACKAGE_INSTALLER_STATUS_UNDEFINED = -1000;
    protected final Object mPackageInstallerTimeoutLock = new Object();
    protected int mCallbackStatus;


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "onReceive: action = " + action);

        if (action.equals(BC_SETDATE))
        {
            int year = intent.getIntExtra("year", 2021);
            int month = intent.getIntExtra("month", 8);
            int day = intent.getIntExtra("day", 8);

            sysctrSetDate(context, year, month, day);
        }
        else if (action.equals(BC_SETTIME))
        {
            int hourOfDay = intent.getIntExtra("hourOfDay", 8);
            int minute = intent.getIntExtra("minute", 8);

            sysctrSetTime(context, hourOfDay, minute);
        }
        else if (action.equals(BC_SETDATETIME))
        {
            long timestamp = intent.getLongExtra("timestamp", 1613078033752L);
            Log.d(TAG, "onReceive: timestamp = " + timestamp);
            sysctrSetDateTime(context, timestamp);
        }
        else if (action.equals(BC_REBOOT))
        {
            sysctrRebootSystem(context);
        }
        else if (action.equals(BC_INSTALL))
        {
            String apkPath = intent.getStringExtra("apkFilePath");

            sysctrInstallApp(context, apkPath);
        }
    }

    /**
     * set system date
     */
    public void sysctrSetDate(Context context, int year, int month, int day) {
        Log.d(TAG, "sysctrSetDate: ");

        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        c.set(Calendar.DAY_OF_MONTH, day);
        long when = c.getTimeInMillis();
        if (when / 1000 < Integer.MAX_VALUE)
        {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    /**
     * set system time
     */
    public void sysctrSetTime(Context context, int hourOfDay, int minute) {
        Log.d(TAG, "sysctrSetTime: ");

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hourOfDay);
        c.set(Calendar.MINUTE, minute);
        long when = c.getTimeInMillis();
        if (when / 1000 < Integer.MAX_VALUE)
        {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    /**
     * set system date&time with timestamp
     */
    public void sysctrSetDateTime(Context context, long timestamp) {
        Log.d(TAG, "sysctrSetDateTime: timestamp = " + timestamp);

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timestamp);
    }

    /**
     * reboot system
     */
    public void sysctrRebootSystem(Context context)
    {
        Log.d(TAG, "sysctrRebootSystem: ");

//        PowerManager pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
//        pManager.reboot("reboot now");
        Intent intent = new Intent(Intent.ACTION_REBOOT);
        intent.putExtra("nowait", 1);
        intent.putExtra("interval", 1);
        intent.putExtra("window", 0);
        context.sendBroadcast(intent);
    }

    /**
     * install silently

     适配android9.0的安装方法
     */
    public void sysctrInstallApp(Context context, String apkFilePath) {
        Log.d(TAG, "sysctrInstallApp path=" + apkFilePath);
        File apkFile = new File(apkFilePath);
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sessionParams
                = new PackageInstaller.SessionParams(PackageInstaller
                .SessionParams.MODE_FULL_INSTALL);
        sessionParams.setSize(apkFile.length());

        int sessionId = createSession(packageInstaller, sessionParams);
        Log.d(TAG, "sysctrInstallApp  sessionId=" + sessionId);
        if (sessionId != -1) {
            boolean copySuccess = copyInstallFile(packageInstaller, sessionId, apkFilePath);
            Log.d(TAG, "sysctrInstallApp  copySuccess=" + copySuccess);
            if (copySuccess) {
                execInstallCommand(context, packageInstaller, sessionId);
            }
        }
    }

    private int createSession(PackageInstaller packageInstaller,
                              PackageInstaller.SessionParams sessionParams) {
        int sessionId = -1;
        try {
            sessionId = packageInstaller.createSession(sessionParams);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sessionId;
    }

    private boolean copyInstallFile(PackageInstaller packageInstaller,
                                    int sessionId, String apkFilePath) {
        InputStream in = null;
        OutputStream out = null;
        PackageInstaller.Session session = null;
        boolean success = false;
        try {
            File apkFile = new File(apkFilePath);
            session = packageInstaller.openSession(sessionId);
            out = session.openWrite("base.apk", 0, apkFile.length());
            in = new FileInputStream(apkFile);
            int total = 0, c;
            byte[] buffer = new byte[65536];
            while ((c = in.read(buffer)) != -1) {
                total += c;
                out.write(buffer, 0, c);
            }
            session.fsync(out);
            Log.i(TAG, "streamed " + total + " bytes");
            success = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
                in.close();
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return success;
    }

    private void execInstallCommand(Context context, PackageInstaller packageInstaller, int sessionId) {
        PackageInstaller.Session session = null;
        try {
            session = packageInstaller.openSession(sessionId);
            /*Intent intent = new Intent(context, InstallResultReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context,
                    1, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(pendingIntent.getIntentSender());*/
            session.commit(getCommitCallback(context, sessionId));
            Log.i(TAG, "begin session");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            session.close();
        }
    }
    /*
    class InstallResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: install finish");
            if (intent != null) {
                final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE);
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    Log.d(TAG, "intall success");
                } else {
                    Log.e(TAG, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
                }
            }
        }
    }
*/

    private IntentSender getCommitCallback(Context context, int sessionId) {
        // Create an intent-filter and register the receiver
        String action = ACTION_INSTALL_COMMIT + "." + sessionId;
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(action);
        context.registerReceiver(mBroadcastReceiver, intentFilter);

        // Create a PendingIntent and use it to generate the IntentSender
        Intent broadcastIntent = new Intent(action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent.getIntentSender();
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: ");
            synchronized (mPackageInstallerTimeoutLock) {
                mCallbackStatus = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PACKAGE_INSTALLER_STATUS_UNDEFINED);

                if (mCallbackStatus == PackageInstaller.STATUS_SUCCESS) {
                    Log.d(TAG, "onReceive: install success");
                } else if (mCallbackStatus == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Log.d(TAG, "onReceive: install failed pending -1");
                }
                else
                {
                    Log.d(TAG, "onReceive: install failed status = " + mCallbackStatus);
                }
                context.unregisterReceiver(this);
                Intent i = new Intent(ACTION_INSTALL_STATUS);
                i.addFlags(0x01000000);
                i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                i.putExtra("status", mCallbackStatus);
                context.sendBroadcast(i);

                mPackageInstallerTimeoutLock.notify();
            }
        }
    };
}
