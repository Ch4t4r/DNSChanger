package com.frostnerd.dnschanger.util;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.quicksettings.TileService;
import android.util.Base64;
import android.util.Base64InputStream;
import android.util.Base64OutputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.activities.PinActivity;
import com.frostnerd.dnschanger.activities.ShortcutActivity;
import com.frostnerd.dnschanger.database.DatabaseHelper;
import com.frostnerd.dnschanger.database.entities.IPPortPair;
import com.frostnerd.dnschanger.database.entities.Shortcut;
import com.frostnerd.dnschanger.services.ConnectivityBackgroundService;
import com.frostnerd.dnschanger.services.DNSVpnService;
import com.frostnerd.dnschanger.tiles.TilePauseResume;
import com.frostnerd.dnschanger.tiles.TileStartStop;
import com.frostnerd.general.StringUtil;
import com.frostnerd.general.Utils;
import com.frostnerd.networking.NetworkUtil;

import org.minidns.record.Data;
import org.minidns.record.Record;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


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
public final class Util {
    public static final String BROADCAST_SERVICE_STATUS_CHANGE = "com.frostnerd.dnschanger.VPN_SERVICE_CHANGE";
    public static final String BROADCAST_SERVICE_STATE_REQUEST = "com.frostnerd.dnschanger.VPN_STATE_CHANGE";
    public static final String BROADCAST_SHORTCUT_CREATED = "com.frostnerd.dnschanger.SHORTCUT_CREATED";
    private static final String LOG_TAG = "[Util]";
    static final Pattern ipv6WithPort = Pattern.compile("(\\[[0-9a-f:]+]:[0-9]{1,5})|([0-9a-f:]+)");
    static final Pattern ipv4WithPort = Pattern.compile("([0-9]{1,3}\\.){3}[0-9]{1,3}(:[0-9]{1,5})?");

    public static synchronized void updateTiles(Context context) {
        if (context == null)
            throw new IllegalStateException("The context passed to updateTiles is null.");
        LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Trying to update Tiles");
        TileService.requestListeningState(context, new ComponentName(context, TileStartStop.class));
        TileService.requestListeningState(context, new ComponentName(context, TilePauseResume.class));
        LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Tiles updated");
    }

    public static IPPortPair validateInput(String input, boolean iPv6, boolean allowEmpty, boolean allowLoopback, int defaultPort) {
        if (allowEmpty && input.equals("")) return IPPortPair.getEmptyPair();
        if (iPv6) {
            if (ipv6WithPort.matcher(input).matches()) {
                if (input.contains("[")) {
                    int port = Integer.parseInt(input.split("]")[1].split(":")[1]);
                    String address = input.split("]")[0].replace("[", "");
                    boolean addressValid = (allowLoopback && NetworkUtil.isIP(address, true)) || NetworkUtil.isAssignableAddress(address, true);
                    return port <= 65535 && port >= 1 && addressValid ? new IPPortPair(address, port, true) : null;
                } else {
                    boolean addressValid = (allowLoopback && NetworkUtil.isIP(input, true)) || NetworkUtil.isAssignableAddress(input, true);
                    return addressValid ? new IPPortPair(input, defaultPort, true) : null;
                }
            } else {
                return null;
            }
        } else {
            if (ipv4WithPort.matcher(input).matches()) {
                if (input.contains(":")) {
                    int port = Integer.parseInt(input.split(":")[1]);
                    String address = input.split(":")[0];
                    boolean addressValid = (allowLoopback && NetworkUtil.isIP(address, false)) || NetworkUtil.isAssignableAddress(address, false);
                    return port <= 65535 && port >= 1 && addressValid ? new IPPortPair(address, port, false) : null;
                } else {
                    boolean addressValid = (allowLoopback && NetworkUtil.isIP(input, false)) || NetworkUtil.isAssignableAddress(input, false);
                    return addressValid ? new IPPortPair(input, defaultPort, false) : null;
                }
            } else {
                return null;
            }
        }
    }

    public static IPPortPair validateInput(String input, boolean iPv6, boolean allowEmpty, int defPort) {
        return validateInput(input, iPv6, allowEmpty, false, defPort);
    }

    public static void updateAppShortcuts(Context context) {
        ShortcutManager shortcutManager = context.getSystemService(ShortcutManager.class);
        if (shortcutManager != null && !PreferencesAccessor.areAppShortcutsEnabled(context)) {
            try {
                shortcutManager.removeAllDynamicShortcuts();
            } catch (Exception ignored) {

            }
            return;
        } else if (shortcutManager == null) return;
        boolean pinProtected = PreferencesAccessor.isPinProtected(context, PreferencesAccessor.PinProtectable.APP_SHORTCUT);
        List<ShortcutInfo> shortcutInfos = new ArrayList<>();
        if (isServiceThreadRunning()) {
            Bundle extras1 = new Bundle();
            extras1.putBoolean("stop_vpn", true);
            extras1.putBoolean("redirectToService", true);
            Bundle extras2 = new Bundle();
            extras2.putBoolean("destroy", true);
            extras2.putBoolean("redirectToService", true);
            shortcutInfos.add(new ShortcutInfo.Builder(context, "id1").setShortLabel(context.getString(R.string.tile_pause))
                    .setLongLabel(context.getString(R.string.tile_pause)).setIcon(Icon.createWithResource(context, R.drawable.ic_stat_pause_dark))
                    .setIntent(pinProtected ? new Intent(context.getApplicationContext(), PinActivity.class).putExtras(extras1).setAction(StringUtil.randomString(40)) : DNSVpnService.getStopVPNIntent(context.getApplicationContext())).build());
            shortcutInfos.add(new ShortcutInfo.Builder(context, "id2").setShortLabel(context.getString(R.string.tile_stop))
                    .setLongLabel(context.getString(R.string.tile_stop)).setIcon(Icon.createWithResource(context, R.drawable.ic_stat_stop_dark))
                    .setIntent(pinProtected ? new Intent(context.getApplicationContext(), PinActivity.class).putExtras(extras2).setAction(StringUtil.randomString(40)) : DNSVpnService.getDestroyIntent(context.getApplicationContext())).build());
        } else if (isServiceRunning(context)) {
            Bundle extras = new Bundle();
            extras.putBoolean("start_vpn", true);
            extras.putBoolean("redirectToService", true);
            shortcutInfos.add(new ShortcutInfo.Builder(context, "id3").setShortLabel(context.getString(R.string.tile_resume))
                    .setLongLabel(context.getString(R.string.tile_resume)).setIcon(Icon.createWithResource(context, R.drawable.ic_stat_resume_dark))
                    .setIntent(pinProtected ? new Intent(context.getApplicationContext(), PinActivity.class).putExtras(extras).setAction(StringUtil.randomString(40)) : DNSVpnService.getStartVPNIntent(context.getApplicationContext())).build());
        } else {
            Bundle extras = new Bundle();
            extras.putBoolean("start_vpn", true);
            extras.putBoolean("redirectToService", true);
            shortcutInfos.add(new ShortcutInfo.Builder(context, "id4").setShortLabel(context.getString(R.string.tile_start)).
                    setLongLabel(context.getString(R.string.tile_start)).setIcon(Icon.createWithResource(context, R.drawable.ic_stat_resume_dark))
                    .setIntent(pinProtected ? new Intent(context.getApplicationContext(), PinActivity.class).putExtras(extras).setAction(StringUtil.randomString(40)) : DNSVpnService.getStartVPNIntent(context.getApplicationContext())).build());
        }
        shortcutManager.setDynamicShortcuts(shortcutInfos);
    }

    public static boolean isServiceRunning(Context c) {
        return DNSVpnService.isServiceRunning() || Utils.isServiceRunning(c, DNSVpnService.class);
    }

    public static boolean isServiceThreadRunning() {
        return DNSVpnService.isDNSThreadRunning();
    }

    public static boolean isTaskerInstalled(Context context) {
        return Utils.isPackageInstalled(context, "net.dinglisch.android.taskerm");
    }

    public static synchronized void deleteDatabase(Context context) {
        DatabaseHelper helper = DatabaseHelper.getInstance(context);
        if (helper != null) helper.close();
        context.deleteDatabase("data");
        context.getDatabasePath("data.db").delete();
    }

    public static void createShortcut(Context context, Shortcut shortcut) {
        if (shortcut == null) return;
        ArrayList<IPPortPair> servers = new ArrayList<>(4);
        servers.add(shortcut.getDns1());
        servers.add(shortcut.getDns2());
        servers.add(shortcut.getDns1v6());
        servers.add(shortcut.getDns2v6());
        createShortcut(context, servers, shortcut.getName());
    }

    public static void createShortcut(Context context, ArrayList<IPPortPair> servers, String name) {
        LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Creating shortcut");
        Intent shortcutIntent = new Intent(context, ShortcutActivity.class);
        shortcutIntent.setAction("com.frostnerd.dnschanger.RUN_VPN_FROM_SHORTCUT");
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        shortcutIntent.putExtra("servers", serializableToString(servers));
        ShortcutManager shortcutManager = Utils.requireNonNull((ShortcutManager) context.getSystemService(Activity.SHORTCUT_SERVICE));
        if (shortcutManager.isRequestPinShortcutSupported()) {
            ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(context, StringUtil.randomString(30))
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setShortLabel(name)
                    .setLongLabel(name)
                    .setIntent(shortcutIntent)
                    .build();
            PendingIntent intent = PendingIntent.getBroadcast(context, 5, new Intent(Util.BROADCAST_SHORTCUT_CREATED), PendingIntent.FLAG_IMMUTABLE);
            shortcutManager.requestPinShortcut(shortcutInfo, intent.getIntentSender());
            return;
        }
        Intent addIntent = new Intent();
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
        addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(context, R.mipmap.ic_launcher));
        addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
        LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Adding shortcut", shortcutIntent);
        LogFactory.writeMessage(context, new String[]{LOG_TAG, LogFactory.STATIC_TAG}, "Intent for adding to Screen:", addIntent);
        context.sendBroadcast(addIntent);
    }

    public static void startService(Context context, Intent intent) {
        if (PreferencesAccessor.isEverythingDisabled(context)) return;
        if ((intent.getComponent() != null && intent.getComponent().getClassName().equals(DNSVpnService.class.getName()) &&
                (PreferencesAccessor.isNotificationEnabled(context)) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)) {
            context.startForegroundService(intent);
        } else context.startService(intent);
    }

    public static String createNotificationChannel(Context context, boolean allowHiding) {
        NotificationManager notificationManager = Utils.requireNonNull((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
        if (allowHiding && PreferencesAccessor.shouldHideNotificationIcon(context)) {
            NotificationChannel channel = new NotificationChannel("noIconChannel", context.getString(R.string.notification_channel_hiddenicon), NotificationManager.IMPORTANCE_MIN);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setDescription(context.getString(R.string.notification_channel_hiddenicon_description));
            channel.setImportance(NotificationManager.IMPORTANCE_MIN);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
            return "noIconChannel";
        } else {
            NotificationChannel channel = new NotificationChannel("defaultchannel", context.getString(R.string.notification_channel_default), NotificationManager.IMPORTANCE_LOW);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setDescription(context.getString(R.string.notification_channel_default_description));
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
            return "defaultchannel";
        }
    }

    public static String createConnectivityCheckChannel(Context context) {
        NotificationManager notificationManager = Utils.requireNonNull((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
        NotificationChannel channel = new NotificationChannel("networkcheckchannel", context.getString(R.string.notification_channel_networkcheck), NotificationManager.IMPORTANCE_LOW);
        channel.enableLights(false);
        channel.enableVibration(false);
        channel.setDescription(context.getString(R.string.notification_channel_networkcheck_description));
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        channel.setImportance(NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
        return "networkcheckchannel";
    }

    public static String createImportantChannel(Context context) {
        NotificationManager notificationManager = Utils.requireNonNull((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
        NotificationChannel channel = new NotificationChannel("defaultchannel", context.getString(R.string.notification_channel_default), NotificationManager.IMPORTANCE_HIGH);
        channel.enableLights(false);
        channel.enableVibration(true);
        channel.setDescription(context.getString(R.string.notification_channel_default_description));
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notificationManager.createNotificationChannel(channel);
        return "defaultchannel";
    }

    public static void runBackgroundConnectivityCheck(Context context, boolean handleInitialState) {
        runBackgroundConnectivityCheck(context, handleInitialState, false);
    }

    public static void runBackgroundConnectivityCheck(Context context, boolean handleInitialState, boolean forceForeground) {
        if (shouldRunNetworkCheck(context) && !Util.isServiceRunning(context)) {
            Intent serviceIntent = new Intent(context, ConnectivityBackgroundService.class)
                    .putExtra("initial", handleInitialState);
            if (forceForeground) serviceIntent.putExtra("forceForeground", true);
            if (forceForeground || PreferencesAccessor.runConnectivityCheckWithPrivilege(context)) {
                startForegroundService(context, serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }

    public static void startForegroundService(Context context, Intent serviceIntent) {
        context.startForegroundService(serviceIntent);
    }

    public static void stopBackgroundConnectivityCheck(Context context) {
        LogFactory.writeMessage(context, LOG_TAG, "Stopping the background connectivity check..");
        if (isBackgroundConnectivityCheckRunning(context)) {
            LogFactory.writeMessage(context, LOG_TAG, "Stopping Service");
            context.stopService(new Intent(context, ConnectivityBackgroundService.class));
        } else {
            LogFactory.writeMessage(context, LOG_TAG, "Service is not running, thus not stopping.");
        }
    }

    @Nullable
    public static NetworkCheckHandle maybeCreateNetworkCheckHandle(@NonNull Context context, String logTag, boolean handleInitialState) {
        if (shouldRunNetworkCheck(context)) {
            boolean handleInitial = handleInitialState && !Preferences.getInstance(context).getBoolean("service_stopped_by_user", false);
            return new NetworkCheckHandle(context, logTag, handleInitialState);
        } else {
            return null;
        }
    }

    public static boolean shouldRunNetworkCheck(@NonNull Context context) {
        Preferences pref = Preferences.getInstance(context);
        return pref.getBoolean("setting_auto_wifi", false) ||
                pref.getBoolean("setting_auto_mobile", false) ||
                pref.getBoolean("setting_disable_netchange", false) ||
                pref.getBoolean("start_service_when_available", false);
    }

    public static boolean isBackgroundConnectivityCheckRunning(@NonNull Context context) {
        return Utils.isServiceRunning(context, ConnectivityBackgroundService.class);
    }

    /**
     * This Method is used instead of getActivity() in a fragment because getActivity() returns null in some rare cases
     *
     * @param fragment
     * @return
     */
    public static FragmentActivity getActivity(Fragment fragment) {
        if (fragment.getActivity() == null) {
            if (fragment.getContext() != null && fragment.getContext() instanceof FragmentActivity) {
                return (FragmentActivity) fragment.getContext();
            } else return null;
        } else return fragment.getActivity();
    }

    public static String serializableToString(Serializable serializable) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(
                    new Base64OutputStream(baos, Base64.NO_PADDING
                            | Base64.NO_WRAP));
            oos.writeObject(serializable);
            oos.close();
            return baos.toString("UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T extends Serializable> T serializableFromString(String s) {
        try {
            return (T) new ObjectInputStream(new Base64InputStream(
                    new ByteArrayInputStream(s.getBytes()), Base64.NO_PADDING
                    | Base64.NO_WRAP)).readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface ConnectivityCheckCallback {
        void onCheckDone(boolean result);
    }

    public interface DNSQueryResultListener {
        void onSuccess(List<Record<? extends Data>> response);

        void onError(@Nullable Exception e);
    }
}