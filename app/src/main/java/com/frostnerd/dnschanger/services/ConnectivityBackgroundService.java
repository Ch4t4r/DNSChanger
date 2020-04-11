package com.frostnerd.dnschanger.services;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.util.NetworkCheckHandle;
import com.frostnerd.dnschanger.util.Util;

/*
 * Copyright (C) 2019 Daniel Wolf (Ch4t4r)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * You can contact the developer at daniel.wolf@frostnerd.com.
 */
public class ConnectivityBackgroundService extends Service {
    private NetworkCheckHandle handle;
    private static final String LOG_TAG = "[ConnectivityBackgroundService]";
    private boolean enabled = false;
    private NotificationCompat.Builder notificationBuilder;
    private boolean restartingSelf = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogFactory.writeMessage(this, LOG_TAG, "Service created.");
        notificationBuilder = new NotificationCompat.Builder(this, Util.createNotificationChannel(this, true));
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher);
        notificationBuilder.setOngoing(true);
        notificationBuilder.setContentTitle(getString(R.string.notification_connectivity_service));
        notificationBuilder.setContentText(getString(R.string.notification_connectivity_service_message));
        notificationBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
        startForeground(1285, notificationBuilder.build());
        // I have no idea whether this actually helps.
        // The intention is to trick the system into believing that this service only runs 45 seconds.
        // I hope that this timer is reset by killing & restarting this service
        final Handler handler = new Handler();
        if(!restartingSelf) handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(restartingSelf) return;
                restartingSelf = true;
                stopSelf();
                LogFactory.writeMessage(ConnectivityBackgroundService.this, LOG_TAG, "Restarting self.");
                Util.startForegroundService(ConnectivityBackgroundService.this, new Intent(ConnectivityBackgroundService.this, ConnectivityCheckRestartService.class));
            }
        }, 45000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogFactory.writeMessage(this, LOG_TAG, "Start command received");
        startForeground(1285, notificationBuilder.build());
        handle = Util.maybeCreateNetworkCheckHandle(this, LOG_TAG, intent == null || intent.getBooleanExtra("initial", true));
        stopForeground(false);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(1285);
        if(handle == null){
            LogFactory.writeMessage(this, LOG_TAG, "Not starting handle because the respective settings aren't enabled");
            stopSelf();
            return START_NOT_STICKY;
        }
        enabled = true;
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogFactory.writeMessage(this, LOG_TAG, "Service destroyed.");
        if(handle != null) handle.stop();
        if(enabled && !restartingSelf) {
            Util.runBackgroundConnectivityCheck(this, true);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        LogFactory.writeMessage(this, LOG_TAG, "Task removed.");
    }
}
