package com.frostnerd.dnschanger.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.activities.BackgroundVpnConfigureActivity;
import com.frostnerd.dnschanger.activities.PinActivity;
import com.frostnerd.dnschanger.database.DatabaseHelper;
import com.frostnerd.dnschanger.database.entities.DNSEntry;
import com.frostnerd.dnschanger.database.entities.IPPortPair;
import com.frostnerd.dnschanger.threading.VPNRunnable;
import com.frostnerd.dnschanger.util.NetworkCheckHandle;
import com.frostnerd.dnschanger.util.Preferences;
import com.frostnerd.dnschanger.util.PreferencesAccessor;
import com.frostnerd.dnschanger.util.Util;
import com.frostnerd.dnschanger.util.VPNServiceArgument;
import com.frostnerd.general.IntentUtil;
import com.frostnerd.general.StringUtil;
import com.frostnerd.general.Utils;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

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
public class DNSVpnService extends VpnService {
    private static final String LOG_TAG = "[DNSVpnService]";
    private static boolean serviceRunning = false;
    private Preferences preferences;
    private NotificationCompat.Builder notificationBuilder;
    private NotificationManager notificationManager;
    private final int NOTIFICATION_ID = 112;
    private String stopReason;
    private boolean fixedDNS = false, startedWithTasker = false, autoPaused = false, variablesCleared = false;
    private BroadcastReceiver stateRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogFactory.writeMessage(DNSVpnService.this, new String[]{LOG_TAG, "[StateRequestReceiver]"}, "Received broadcast", intent);
            broadcastCurrentState();
        }
    };
    private Set<String> excludedApps;
    private boolean excludedWhitelisted;
    private static VPNRunnable vpnRunnable;
    private Thread vpnThread;
    private ArrayList<IPPortPair> upstreamServers;
    private NetworkCheckHandle networkCheckHandle;

    private synchronized void clearVars(boolean stopSelf){
        if(variablesCleared)return;
        variablesCleared = true;
        LogFactory.writeMessage(this, LOG_TAG, "Clearing Variables");
        if(stopReason != null && notificationManager != null && preferences.getBoolean( "notification_on_stop", false)){
            String reasonText = getString(R.string.notification_reason_stopped).replace("[reason]", stopReason);
            notificationManager.notify(NOTIFICATION_ID+1, new NotificationCompat.Builder(this, Util.createNotificationChannel(this, false)).setAutoCancel(true)
                    .setOngoing(false).setContentText(reasonText).setStyle(new NotificationCompat.BigTextStyle().bigText(reasonText))
                    .setSmallIcon(R.drawable.ic_stat_small_icon).setContentIntent(PendingIntent.getActivity(this, 13, new Intent(this, PinActivity.class), 0))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT).build());
        }
        serviceRunning = false;
        stopThread();
        if(stateRequestReceiver != null)LocalBroadcastManager.getInstance(this).unregisterReceiver(stateRequestReceiver);
        notificationManager.cancel(NOTIFICATION_ID);
        stateRequestReceiver = null;
        LogFactory.writeMessage(this, LOG_TAG, "Variables cleared");
        if(stopSelf){
            stopForeground(false);
            stopSelf();
        }
    }

    public void updateNotification() { //Well, this method is a mess.
        if(!serviceRunning)return;
        if(preferences == null) preferences = Preferences.getInstance(this);
        LogFactory.writeMessage(this, new String[]{LOG_TAG, "[NOTIFICATION]"}, "Updating notification");
        initNotification();
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O && !preferences.getBoolean( "setting_show_notification",true)){
            LogFactory.writeMessage(this, new String[]{LOG_TAG, "[NOTIFICATION]"}, "Notification is disabled");
            stopForeground(true);
            return;
        }else LogFactory.writeMessage(this, new String[]{LOG_TAG, "[NOTIFICATION]"}, "Notification is enabled");
        boolean pinProtected = preferences.getBoolean( "pin_notification",false),
                threadRunning = vpnRunnable != null && vpnRunnable.isThreadRunning();
        NotificationCompat.Action a1 = notificationBuilder.mActions.get(0);
        a1.icon = threadRunning ? R.drawable.ic_stat_pause : R.drawable.ic_stat_resume;
        a1.title = getString(threadRunning ? R.string.action_pause : R.string.action_resume);
        a1.actionIntent = pinProtected ? PendingIntent.getActivity(this,14,new Intent(this, PinActivity.class).setAction(new Random().nextInt(50) + "_action").
                putExtra(threadRunning ? "stop_vpn" : "start_vpn", true).putExtra("redirectToService",true), PendingIntent.FLAG_UPDATE_CURRENT) :
                PendingIntent.getService(this, 15, new Intent(this, DNSVpnService.class).setAction(new Random().nextInt(50) + "_action").putExtra(threadRunning ? VPNServiceArgument.COMMAND_STOP_VPN.getArgument() : VPNServiceArgument.COMMAND_START_VPN.getArgument(), true), PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        notificationBuilder.mActions.get(1).actionIntent = pinProtected ? PendingIntent.getActivity(this,16,new Intent(this, PinActivity.class).
                setAction(new Random().nextInt(50) + "_action").putExtra("destroy", true).putExtra("redirectToService",true), PendingIntent.FLAG_UPDATE_CURRENT) : PendingIntent.getService(this, 7, new Intent(this, DNSVpnService.class)
                .setAction(StringUtil.randomString(80) + "_action").putExtra(VPNServiceArgument.COMMAND_STOP_SERVICE.getArgument(), true), PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        notificationBuilder.setContentTitle(getString(threadRunning ? R.string.active : R.string.paused));
        String excludedAppsText = (excludedApps.size() != 0 ? "\n" +
                getString(excludedWhitelisted ? R.string.notification_x_whitelisted : R.string.notification_x_blacklisted)
                        .replace("[x]",""+excludedApps.size()) : "");
        excludedAppsText = !preferences.getBoolean( "app_whitelist_configured",false) ? "" : excludedAppsText;
        if(preferences.getBoolean( "show_used_dns",true)){
            LogFactory.writeMessage(this, new String[]{LOG_TAG, "[NOTIFICATION]"}, "Showing used DNS servers in notification");
            boolean ipv6Enabled = PreferencesAccessor.isIPv6Enabled(this),
                    ipv4Enabled = PreferencesAccessor.isIPv4Enabled(this),
                    customPorts = PreferencesAccessor.areCustomPortsEnabled(this);
            StringBuilder contentText = new StringBuilder();
            DNSEntry matchingEntry;
            for(IPPortPair pair: upstreamServers){
                if((ipv4Enabled && !pair.isIpv6()) || (ipv6Enabled && pair.isIpv6())){
                    matchingEntry = DatabaseHelper.getInstance(this).findMatchingDNSEntry(pair.getAddress());
                    contentText.append(pair.formatForTextfield(customPorts));
                    if(matchingEntry != null) contentText.append(" (").append(matchingEntry.getShortName()).append(")");
                    contentText.append("\n");
                }
            }
            if(!excludedAppsText.equals("")){
                contentText.append("\n");
                contentText.append(excludedAppsText);
            }else{
                if(contentText.length() != 0) contentText.setLength(contentText.length()-1);
            }
            boolean advancedMode = PreferencesAccessor.isRunningInAdvancedMode(this);
            if(!ipv6Enabled){
                contentText.append("\n").append(getString(R.string.notification_ipv6_text));
            }
            if(advancedMode)contentText.append("\n").append(getString(R.string.notification_text_running_in_advanced_mode));
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().
                    bigText(contentText.toString()));
            notificationBuilder.setSubText(getString(threadRunning ? R.string.notification_running : R.string.notification_paused));
        }else{
            LogFactory.writeMessage(this, new String[]{LOG_TAG, "[NOTIFICATION]"}, "Not showing used DNS Servers in notification");
            notificationBuilder.setSubText("");
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().setSummaryText(getString(threadRunning ? R.string.notification_running : R.string.notification_paused) + excludedAppsText));
            notificationBuilder.setContentText(getString(threadRunning ? R.string.notification_running : R.string.notification_paused));
        }
        notificationBuilder.setUsesChronometer(threadRunning);
        boolean hideIcon = preferences.getBoolean("hide_notification_icon", false);
        notificationBuilder.setPriority(hideIcon ?
                NotificationCompat.PRIORITY_MIN : NotificationCompat.PRIORITY_LOW);
        notificationBuilder.setChannelId(Util.createNotificationChannel(this, true));
        LogFactory.writeMessage(DNSVpnService.this, new String[]{LOG_TAG, "[NOTIFICATION]"}, "Notification was posted");
        startForeground(NOTIFICATION_ID, notificationBuilder.build());
    }

    private void initNotification(){
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationBuilder == null) {
            LogFactory.writeMessage(this,new String[]{LOG_TAG, "[NOTIFICATION]"} , "Initiating Notification");
            notificationBuilder = new NotificationCompat.Builder(this, Util.createNotificationChannel(this, true));
            notificationBuilder.setSmallIcon(R.drawable.ic_stat_small_icon); //TODO Update Image
            notificationBuilder.setContentTitle(getString(R.string.app_name));
            notificationBuilder.setContentIntent(PendingIntent.getActivity(this, 6, new Intent(this, PinActivity.class), PendingIntent.FLAG_IMMUTABLE));
            notificationBuilder.setAutoCancel(false);
            notificationBuilder.setOngoing(true);
            notificationBuilder.setUsesChronometer(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notificationBuilder.setBadgeIconType(NotificationCompat.BADGE_ICON_NONE);
            }
            notificationBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
            notificationBuilder.addAction(new NotificationCompat.Action(R.drawable.ic_stat_pause, getString(R.string.action_pause),null));
            notificationBuilder.addAction(new NotificationCompat.Action(R.drawable.ic_stat_stop, getString(R.string.action_stop),null));
            notificationBuilder.setColorized(false);
            LogFactory.writeMessage(this, new String[]{LOG_TAG, "[NOTIFICATION]"}, "Notification created (Not yet posted)");
        }
    }

    public void broadcastCurrentState(){
        Intent i;
        LogFactory.writeMessage(this, LOG_TAG, "Broadcasting current Service state",
                i = new Intent(Util.BROADCAST_SERVICE_STATUS_CHANGE).
                        putExtra(VPNServiceArgument.ARGUMENT_UPSTREAM_SERVERS.getArgument(), upstreamServers).
                        putExtra("vpn_running", vpnRunnable != null && vpnRunnable.isThreadRunning()).putExtra("started_with_tasker", startedWithTasker).putExtra("fixedDNS", fixedDNS));
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        LogFactory.writeMessage(this, LOG_TAG, "Broadcasted service state.");
    }

    private void updateDNSServers(Intent intent){
        LogFactory.writeMessage(this, LOG_TAG, "Updating DNS Servers");
        if(fixedDNS){
            LogFactory.writeMessage(this, LOG_TAG, "DNSVPNService is using fixed DNS servers (Not those from settings)");
            LogFactory.writeMessage(this, LOG_TAG, "Current DNS Servers:" + upstreamServers);
            if(intent != null){
                if(intent.hasExtra(VPNServiceArgument.ARGUMENT_UPSTREAM_SERVERS.getArgument()))
                    upstreamServers = (ArrayList<IPPortPair>) intent.getSerializableExtra(VPNServiceArgument.ARGUMENT_UPSTREAM_SERVERS.getArgument());
                LogFactory.writeMessage(this, LOG_TAG, "DNS Servers set to: " + upstreamServers);
            }
        }else{
            LogFactory.writeMessage(this, LOG_TAG, "Not using fixed DNS. Fetching DNS from settings");
            LogFactory.writeMessage(this, LOG_TAG, "Current DNS Servers" + upstreamServers);
            this.upstreamServers = PreferencesAccessor.getAllDNSPairs(this, true);
            LogFactory.writeMessage(this, LOG_TAG, "DNS Servers set to: " + upstreamServers);
        }
    }

    // Hello potential Source-Code lurker!
    // I've stumbled upon many os-based problems developing this application. For example some devices don't accept the IP used (172.31.255.253/30 is preferred)
    // and mark it as "bad address". Other devices have a problem with the underlying VPN structure which prevents it from working on the first try.
    // Other devices don't even reach the while-loop (by design) and thus I have to catch every exception occurring.
    // Overall this lead to a messy thread and a lot of flawed design, but there is no other way to do it which makes it work
    // on all devices.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        //intent = intent == null ? intent : restoreSettings(intent);
        LogFactory.writeMessage(this, new String[]{LOG_TAG, "[ONSTARTCOMMAND]"}, "Got StartCommand", intent);
        if(notificationBuilder != null) startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        else {
            initNotification();
            startForeground(NOTIFICATION_ID, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        }
        if(Utils.isServiceRunning(this, RuleImportService.class)){
            LogFactory.writeMessage(this, new String[]{LOG_TAG, "[ONSTARTCOMMAND]"}, "Not starting the service because rules are currently being imported");
            stopSelf();
            return START_NOT_STICKY;
        }
        if(variablesCleared && intent != null && !(IntentUtil.checkExtra(VPNServiceArgument.COMMAND_STOP_SERVICE.getArgument(),intent) ||
                IntentUtil.checkExtra(VPNServiceArgument.COMMAND_STOP_VPN.getArgument(),intent)))return START_STICKY;
        serviceRunning = intent == null || !intent.getBooleanExtra(VPNServiceArgument.COMMAND_STOP_SERVICE.getArgument(), false);
        if(preferences != null){
            excludedApps = preferences.getStringSet( "excluded_apps");
            excludedWhitelisted = preferences.getBoolean("excluded_whitelist", false);
        }
        if(intent!=null){
            fixedDNS = intent.hasExtra(VPNServiceArgument.FLAG_FIXED_DNS.getArgument()) ? intent.getBooleanExtra(VPNServiceArgument.FLAG_FIXED_DNS.getArgument(), false) : fixedDNS;
            if(!intent.hasExtra(VPNServiceArgument.FLAG_DONT_UPDATE_DNS.getArgument()))updateDNSServers(intent);
            startedWithTasker = intent.hasExtra(VPNServiceArgument.FLAG_STARTED_WITH_TASKER.getArgument()) ? intent.getBooleanExtra(VPNServiceArgument.FLAG_STARTED_WITH_TASKER.getArgument(), false) : startedWithTasker;
            if(IntentUtil.checkExtra(VPNServiceArgument.COMMAND_STOP_SERVICE.getArgument(),intent)){
                Preferences.getInstance(this).putBoolean("service_stopped_by_user", true);
                LogFactory.writeMessage(this, new String[]{LOG_TAG, "[ONSTARTCOMMAND]"}, "Got destroy. Destroying DNSVPNService");
                if(intent.hasExtra(VPNServiceArgument.ARGUMENT_STOP_REASON.getArgument()))stopReason = intent.getStringExtra(VPNServiceArgument.ARGUMENT_STOP_REASON.getArgument());
                LogFactory.writeMessage(this, new String[]{LOG_TAG, "[ONSTARTCOMMAND]"}, "Stopping self");
                stopService();
            }else if (IntentUtil.checkExtra(VPNServiceArgument.COMMAND_START_VPN.getArgument(),intent) || (intent.getAction() != null && intent.getAction().equals("android.net.VpnService"))) {
                Preferences.getInstance(this).putBoolean("service_stopped_by_user", false);
                if(prepare(this) == null) {
                    LogFactory.writeMessage(this, new String[]{LOG_TAG, "[ONSTARTCOMMAND]"}, "Starting VPN");
                    createAndRunThread(!IntentUtil.checkExtra(VPNServiceArgument.FLAG_DONT_START_IF_RUNNING.getArgument(), intent),
                            !IntentUtil.checkExtra(VPNServiceArgument.FLAG_DONT_START_IF_NOT_RUNNING.getArgument(), intent));
                    LogFactory.writeMessage(this, new String[]{LOG_TAG, "[ONSTARTCOMMAND]"}, "Creating Thread");
                } else {
                    BackgroundVpnConfigureActivity.startBackgroundConfigure(this, true);
                }
            }else if (IntentUtil.checkExtra(VPNServiceArgument.COMMAND_STOP_VPN.getArgument(),intent)){
                LogFactory.writeMessage(this, new String[]{LOG_TAG, "[ONSTARTCOMMAND]"}, "Stopping VPN");
                stopThread();
            }
            Util.updateTiles(this);
        }else LogFactory.writeMessage(this, new String[]{LOG_TAG, "[ONSTARTCOMMAND]", LogFactory.Tag.ERROR.toString()}, "Intent given is null. This isn't normal behavior");
        if(upstreamServers != null && upstreamServers.size() != 0) updateNotification();
        return START_REDELIVER_INTENT;
    }

    private void createAndRunThread(boolean runIfAlreadyRunning, boolean runIfNotRunning){
        preferences.put( "start_service_when_available", false);
        if(runIfNotRunning && (vpnRunnable == null || !vpnRunnable.isThreadRunning())){
            synchronized (DNSVpnService.this){
                vpnRunnable = new VPNRunnable(this, upstreamServers, excludedApps, excludedWhitelisted);
                vpnThread = new Thread(vpnRunnable, "DNSChanger");
                vpnThread.start();
            }
        }else if(runIfAlreadyRunning && (vpnRunnable != null && vpnRunnable.isThreadRunning())){
            vpnRunnable.addAfterThreadStop(new Runnable() {
                @Override
                public void run() {
                    synchronized (DNSVpnService.this){
                        vpnRunnable = new VPNRunnable(DNSVpnService.this, upstreamServers, excludedApps, excludedWhitelisted);
                        vpnThread = new Thread(vpnRunnable, "DNSChanger");
                        vpnThread.start();
                    }
                }
            });
            vpnRunnable.stop(vpnThread);
        }
    }

    private void stopThread(){
        LogFactory.writeMessage(this, LOG_TAG, "Trying to stop thread.");
        if (vpnThread != null) {
            LogFactory.writeMessage(this, LOG_TAG, "VPNThread already running. Interrupting");
            if(vpnRunnable != null){
                vpnRunnable.addAfterThreadStop(new Runnable() {
                    @Override
                    public void run() {
                        if(vpnRunnable != null) vpnRunnable.destroy();
                        vpnThread = null;
                        vpnRunnable = null;
                    }
                });
                if(vpnRunnable != null) vpnRunnable.stop(vpnThread);
            }else{
                vpnRunnable = null;
                vpnThread = null;
            }
        }else{
            LogFactory.writeMessage(this, LOG_TAG, "VPNThread not running. No need to interrupt.");
        }
    }

    private void stopService(){
        clearVars(true);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        LogFactory.writeMessage(this, LOG_TAG, "Created Service");
        preferences = Preferences.getInstance(this);
        initNotification();
        LocalBroadcastManager.getInstance(this).registerReceiver(stateRequestReceiver, new IntentFilter(Util.BROADCAST_SERVICE_STATE_REQUEST));
        Util.stopBackgroundConnectivityCheck(this);
        networkCheckHandle = Util.maybeCreateNetworkCheckHandle(this, LOG_TAG, true);
    }

    @Override
    public void onDestroy() {
        LogFactory.writeMessage(this, LOG_TAG, "Destroying");
        super.onDestroy();
        Util.updateTiles(this);
        if(networkCheckHandle != null) {
            networkCheckHandle.stop();
            networkCheckHandle = null;
        }
        if(!Util.isBackgroundConnectivityCheckRunning(this)) {
            Util.runBackgroundConnectivityCheck(this, false);
        }
        LogFactory.writeMessage(this, LOG_TAG, "Destroyed.");
    }

    @Override
    public void onRevoke() {
        super.onRevoke();
        stopService();
        if(networkCheckHandle != null) {
            networkCheckHandle.stop();
            networkCheckHandle = null;
        }
        if(!Util.isBackgroundConnectivityCheckRunning(this)) {
            Util.runBackgroundConnectivityCheck(this, false);
        }
        if(Preferences.getInstance(this).getBoolean("setting_protect_other_vpns", false)) {
            BackgroundVpnConfigureActivity.startBackgroundConfigure(this, true);
        } else {
            preferences.put( "start_service_when_available", true);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        LogFactory.writeMessage(this, LOG_TAG, "Got onLowMemory");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        LogFactory.writeMessage(this, LOG_TAG, "Memory trimmed. Level: " + level);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        LogFactory.writeMessage(this, LOG_TAG, "Task is being removed. ", rootIntent);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return (intent.getBooleanExtra(VPNServiceArgument.FLAG_GET_BINDER.getArgument(),false) && serviceRunning) ?
                new ServiceBinder() : super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    public Builder createBuilder(){
        return new Builder();
    }

    public static Intent getDestroyIntent(Context context){
        return getDestroyIntent(context, null);
    }

    public static Intent getDestroyIntent(Context context, String reason){
        return new Intent(context, DNSVpnService.class).putExtra(VPNServiceArgument.COMMAND_STOP_SERVICE.getArgument(), true).
                putExtra(VPNServiceArgument.ARGUMENT_STOP_REASON.getArgument(), reason).
                putExtra(VPNServiceArgument.ARGUMENT_CALLER_TRACE.getArgument(), LogFactory.stacktraceToString(new Throwable())).setAction(StringUtil.randomString(40).replace("\n", " <<->> "));
    }

    public static Intent getStartVPNIntent(Context context){
        return getStartVPNIntent(context, false);
    }

    public static Intent getStartVPNIntent(Context context, boolean startedWithTasker){
        return new Intent(context, DNSVpnService.class).putExtra(VPNServiceArgument.COMMAND_START_VPN.getArgument(),true).putExtra(VPNServiceArgument.FLAG_STARTED_WITH_TASKER.getArgument(), startedWithTasker).
                putExtra(VPNServiceArgument.ARGUMENT_CALLER_TRACE.getArgument(), LogFactory.stacktraceToString(new Throwable(),true).replace("\n", " <<->> ")).setAction(StringUtil.randomString(40));
    }

    public static Intent getStopVPNIntent(Context context){
        return new Intent(context, DNSVpnService.class).putExtra(VPNServiceArgument.COMMAND_STOP_VPN.getArgument(),true).
                putExtra(VPNServiceArgument.ARGUMENT_CALLER_TRACE.getArgument(), LogFactory.stacktraceToString(new Throwable(),true).replace("\n", " <<->> ")).setAction(StringUtil.randomString(40));
    }

    public static Intent getStartVPNIntent(Context context, ArrayList<IPPortPair> upstreamServers, boolean startedWithTasker, boolean fixedDNS){
        return new Intent(context, DNSVpnService.class).putExtra(VPNServiceArgument.COMMAND_START_VPN.getArgument(),true).
                putExtra(VPNServiceArgument.ARGUMENT_UPSTREAM_SERVERS.getArgument(), upstreamServers).
                putExtra(VPNServiceArgument.FLAG_STARTED_WITH_TASKER.getArgument(), startedWithTasker). putExtra(VPNServiceArgument.FLAG_FIXED_DNS.getArgument(), fixedDNS).
                putExtra(VPNServiceArgument.ARGUMENT_CALLER_TRACE.getArgument(), LogFactory.stacktraceToString(new Throwable(),true).replace("\n", " <<->> ")).setAction(StringUtil.randomString(40));
    }

    public static Intent getStartVPNIntent(Context context, ArrayList<IPPortPair> upstreamServers, boolean startedWithTasker){
        return new Intent(context, DNSVpnService.class).putExtra(VPNServiceArgument.COMMAND_START_VPN.getArgument(),true).
                putExtra(VPNServiceArgument.ARGUMENT_UPSTREAM_SERVERS.getArgument(), upstreamServers).
                putExtra(VPNServiceArgument.FLAG_STARTED_WITH_TASKER.getArgument(), startedWithTasker).
                putExtra(VPNServiceArgument.ARGUMENT_CALLER_TRACE.getArgument(), LogFactory.stacktraceToString(new Throwable(),true).replace("\n", " <<->> ")).setAction(StringUtil.randomString(40));
    }

    public static Intent getUpdateServersIntent(Context context, boolean restartVPN, boolean startIfNotRunning){
        Intent intent = new Intent(context, DNSVpnService.class).putExtra("supercooldummy", "data");
        if(restartVPN)intent.putExtra(VPNServiceArgument.COMMAND_START_VPN.getArgument(), true);
        if(!startIfNotRunning)intent.putExtra(VPNServiceArgument.FLAG_DONT_START_IF_NOT_RUNNING.getArgument(), true);
        return intent;
    }

    public static Intent getBinderIntent(Context context){
        return new Intent(context, DNSVpnService.class).putExtra(VPNServiceArgument.FLAG_GET_BINDER.getArgument(),true);
    }

    public boolean wasStartedFromShortcut(){
        return fixedDNS && !startedWithTasker;
    }

    public static boolean isDNSThreadRunning(){
        return isServiceRunning() && vpnRunnable != null && vpnRunnable.isThreadRunning();
    }

    public static boolean isServiceRunning(){
        return serviceRunning;
    }

    public boolean addressesMatch(ArrayList<IPPortPair> servers){
        boolean found;
        for(IPPortPair pair1: servers){
            found = false;
            for (IPPortPair server : upstreamServers) {
                if(pair1.getAddress().equalsIgnoreCase(server.getAddress()) &&
                        pair1.getPort() == server.getPort()){
                    found = true;
                    break;
                }
            }
            if(!found)return false;
        }
        return true;
    }

    public class ServiceBinder extends Binder{
        public DNSVpnService getService(){
            return DNSVpnService.this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if ( code == IBinder.LAST_CALL_TRANSACTION ) {
                onRevoke();
                return true;
            }
            return super.onTransact( code, data, reply, flags );
        }
    }
}
