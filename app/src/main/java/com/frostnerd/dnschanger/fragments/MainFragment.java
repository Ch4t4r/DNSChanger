package com.frostnerd.dnschanger.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.OrientationHelper;

import com.frostnerd.design.dialogs.LoadingDialog;
import com.frostnerd.dnschanger.DNSChanger;
import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.activities.MainActivity;
import com.frostnerd.dnschanger.database.entities.DNSEntry;
import com.frostnerd.dnschanger.database.entities.IPPortPair;
import com.frostnerd.dnschanger.dialogs.VPNInfoDialog;
import com.frostnerd.dnschanger.services.DNSVpnService;
import com.frostnerd.dnschanger.util.DNSQueryUtil;
import com.frostnerd.dnschanger.util.Preferences;
import com.frostnerd.dnschanger.util.PreferencesAccessor;
import com.frostnerd.dnschanger.util.ThemeHandler;
import com.frostnerd.dnschanger.util.Util;
import com.frostnerd.general.Utils;
import com.frostnerd.general.textfilers.InputCharacterFilter;
import com.google.android.material.textfield.TextInputLayout;

import org.minidns.record.Data;
import org.minidns.record.Record;

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
public class MainFragment extends Fragment {
    private Button startStopButton;
    private View running_indicator;
    private boolean vpnRunning, wasStartedWithTasker = false;
    private TextInputLayout met_dns1, met_dns2;
    public EditText dns1, dns2;
    private static final String LOG_TAG = "[MainActivity]";
    private TextView connectionText;
    private ImageView connectionImage;
    private boolean advancedMode;
    public boolean settingV6 = false;
    private final BroadcastReceiver serviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "Received ServiceState Answer", intent);
            vpnRunning = intent.getBooleanExtra("vpn_running",false);
            wasStartedWithTasker = intent.getBooleanExtra("started_with_tasker", false);
            setIndicatorState(intent.getBooleanExtra("vpn_running",false));
        }
    };
    private View contentView;

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startStopButton.callOnClick();
                }
            });

    private void setIndicatorState(boolean vpnRunning) {
        if(!isAdded() || isDetached()) return;
        LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "Changing IndicatorState to " + vpnRunning);
        if (vpnRunning) {
            connectionText.setText(R.string.running);
            if(connectionImage != null)connectionImage.setImageResource(R.drawable.ic_thumb_up);
            startStopButton.setText(R.string.stop);
            running_indicator.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = requireContext().getTheme();
            theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true);
            if(PreferencesAccessor.isEverythingDisabled(getContextWorkaround()))  connectionText.setText(R.string.info_functionality_disabled);
            else connectionText.setText(R.string.not_running);
            if(connectionImage != null)connectionImage.setImageResource(R.drawable.ic_thumb_down);
            startStopButton.setText(R.string.start);
            running_indicator.setBackgroundColor(typedValue.data);
        }
        LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "IndictorState set");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        contentView = inflater.inflate(R.layout.fragment_main, container, false);
        return contentView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        startStopButton = null;
        met_dns1 = met_dns2 = null;
        dns1 = dns2 = null;
        connectionText = null;
        connectionImage = null;
        contentView = null;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        settingV6 = !PreferencesAccessor.isIPv4Enabled(getContextWorkaround()) || (PreferencesAccessor.isIPv6Enabled(getContextWorkaround()) && settingV6);
        setHasOptionsMenu(true);
        boolean vertical = getResources().getConfiguration().orientation == OrientationHelper.VERTICAL;
        LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "Created Activity", Util.getActivity(this).getIntent());
        LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "Setting ContentView");
        met_dns1 = (TextInputLayout) findViewById(R.id.met_dns1);
        met_dns2 = (TextInputLayout) findViewById(R.id.met_dns2);
        dns1 = (EditText) findViewById(R.id.dns1);
        dns2 = (EditText) findViewById(R.id.dns2);
        connectionImage = vertical ? null : (ImageView)findViewById(R.id.connection_status_image);
        connectionText = (TextView)findViewById(R.id.connection_status_text);
        running_indicator = findViewById(R.id.running_indicator);
        startStopButton = (Button) findViewById(R.id.startStopButton);

        if(settingV6 || PreferencesAccessor.areCustomPortsEnabled(getContextWorkaround())){
            dns1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            dns2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View buttonView) {
                if(isDetached() || !isAdded()) return;
                final Context context = getContextWorkaround(buttonView);
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                        return;
                    }
                }

                Intent i;
                try {
                    i = VpnService.prepare(context);
                } catch (NullPointerException ex) {
                    i = null; // I have no idea why this sometimes occurs.
                }
                final Intent configureIntent = i;
                LogFactory.writeMessage(context, LOG_TAG, "Startbutton clicked. Configuring VPN if needed");
                if (i != null){
                    LogFactory.writeMessage(context, LOG_TAG, "VPN isn't prepared yet. Showing dialog explaining the VPN");
                    new VPNInfoDialog(context, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            try {
                                ((Activity)context).startActivityForResult(configureIntent, 0);
                            } catch (ActivityNotFoundException e) {
                                new AlertDialog.Builder(context)
                                        .setTitle(R.string.title_vpndialog_missing)
                                        .setMessage(R.string.summary_vpndialog_missing)
                                        .setNeutralButton(R.string.close,
                                                new DialogInterface.OnClickListener() {

                                                    @Override
                                                    public void onClick(
                                                            DialogInterface dialogInterface,
                                                            int i) {
                                                        dialogInterface.dismiss();
                                                    }
                                                }).show();
                            }
                            LogFactory.writeMessage(context, LOG_TAG, "Requesting VPN access", configureIntent);
                        }
                    });
                    LogFactory.writeMessage(context, LOG_TAG, "Dialog is now being shown");
                }else{
                    LogFactory.writeMessage(context, LOG_TAG, "VPNService is already configured");
                    onActivityResult(0, Activity.RESULT_OK, null);
                }
            }
        });
        dns1.addTextChangedListener(new TextWatcher() {
            private String before;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                before = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(this.before.equalsIgnoreCase(s.toString()))return;
                IPPortPair pair = Util.validateInput(s.toString(), settingV6, false,
                        PreferencesAccessor.isLoopbackAllowed(getContextWorkaround()), 53);
                if (pair == null || (pair.getPort() != 53 && !advancedMode)) {
                    met_dns1.setError(" ");
                } else {
                    met_dns1.setError(null);
                    if (settingV6) PreferencesAccessor.Type.DNS1_V6.saveDNSPair(getContextWorkaround(), pair);
                    else PreferencesAccessor.Type.DNS1.saveDNSPair(getContextWorkaround(), pair);
                    setEditTextLabel();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        dns2.addTextChangedListener(new TextWatcher() {
            private String before;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                before = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(this.before.equalsIgnoreCase(s.toString()))return;
                IPPortPair pair = Util.validateInput(s.toString(), settingV6, true,
                        PreferencesAccessor.isLoopbackAllowed(getContextWorkaround()), 53);
                if (pair == null || (pair != IPPortPair.getEmptyPair() && pair.getPort() != 53 && !advancedMode)) {
                    met_dns2.setError(" ");
                } else {
                    met_dns2.setError(null);
                    if (settingV6) PreferencesAccessor.Type.DNS2_V6.saveDNSPair(getContextWorkaround(), pair);
                    else PreferencesAccessor.Type.DNS2.saveDNSPair(getContextWorkaround(), pair);
                    setEditTextLabel();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        setEditTextLabel();
        LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "Done with OnCreate");
    }

    private void setEditTextLabel(){
        String label1 = getString(R.string.hint_dns1), label2 = getString(R.string.hint_dns2);
        if(settingV6){
            DNSEntry entry;
            if((entry = PreferencesAccessor.Type.DNS1_V6.findMatchingDatabaseEntry(getContextWorkaround())) != null)
                label1 += " (" + entry.getShortName() + ")";
            if((entry = PreferencesAccessor.Type.DNS2_V6.findMatchingDatabaseEntry(getContextWorkaround())) != null)
                label2 += " (" + entry.getShortName() + ")";
        }else{
            DNSEntry entry;
            if((entry = PreferencesAccessor.Type.DNS1.findMatchingDatabaseEntry(getContextWorkaround())) != null)
                label1 += " (" + entry.getShortName() + ")";
            if((entry = PreferencesAccessor.Type.DNS2.findMatchingDatabaseEntry(getContextWorkaround())) != null)
                label2 += " (" + entry.getShortName() + ")";
        }
        met_dns1.setHint(label1);
        met_dns2.setHint(label2);
    }

    private void setEditTextState(){
        boolean customPorts = PreferencesAccessor.areCustomPortsEnabled(getContextWorkaround());
        if(settingV6 || customPorts){
            dns1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            dns2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        if(!settingV6){
            InputFilter filter = new InputCharacterFilter(customPorts ?
                    Pattern.compile("[0-9.:]") : Pattern.compile("[0-9.]"));
            dns1.setFilters(new InputFilter[]{filter});
            dns2.setFilters(new InputFilter[]{filter});
            IPPortPair p1 = PreferencesAccessor.Type.DNS1.getPair(getContextWorkaround()),
                    p2 = PreferencesAccessor.Type.DNS2.getPair(getContextWorkaround());
            dns1.setText(p1.formatForTextfield(customPorts));
            dns2.setText(p2.formatForTextfield(customPorts));
        }else{
            InputFilter filter = new InputCharacterFilter(customPorts ?
                    Pattern.compile("[0-9:a-f\\[\\]]") : Pattern.compile("[0-9:a-f]"));
            dns1.setFilters(new InputFilter[]{filter});
            dns2.setFilters(new InputFilter[]{filter});
            IPPortPair p1 = PreferencesAccessor.Type.DNS1_V6.getPair(getContextWorkaround()),
                    p2 = PreferencesAccessor.Type.DNS2_V6.getPair(getContextWorkaround());
            dns1.setText(p1.formatForTextfield(customPorts));
            dns2.setText(p2.formatForTextfield(customPorts));
        }
    }

    private View findViewById(@IdRes int id){
        return contentView.findViewById(id);
    }

    @Override
    public void onResume() {
        super.onResume();
        advancedMode = PreferencesAccessor.isRunningInAdvancedMode(getContextWorkaround());
        settingV6 = !PreferencesAccessor.isIPv4Enabled(getContextWorkaround()) || (PreferencesAccessor.isIPv6Enabled(getContextWorkaround()) && settingV6);
        LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "Got OnResume");
        LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "Sending ServiceStateRequest as broadcast");
        vpnRunning = Util.isServiceRunning(getContextWorkaround());
        if(PreferencesAccessor.isEverythingDisabled(getContextWorkaround())){
            startStopButton.setEnabled(false);
            startStopButton.setClickable(false);
            startStopButton.setAlpha(0.50f);
            connectionText.setText(R.string.info_functionality_disabled);
        }else{
            startStopButton.setEnabled(true);
            startStopButton.setClickable(true);
            startStopButton.setAlpha(1f);
            setIndicatorState(vpnRunning);
        }
        LocalBroadcastManager.getInstance(getContextWorkaround()).registerReceiver(serviceStateReceiver, new IntentFilter(Util.BROADCAST_SERVICE_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(getContextWorkaround()).sendBroadcast(new Intent(Util.BROADCAST_SERVICE_STATE_REQUEST));
        setEditTextState();
        Utils.requireNonNull(((AppCompatActivity)getContextWorkaround()).getSupportActionBar()).setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
        Utils.requireNonNull(Util.getActivity(this)).invalidateOptionsMenu();
        Utils.requireNonNull(Util.getActivity(this)).getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    @Override
    public void onPause() {
        super.onPause();
        LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "Got OnPause");
        LocalBroadcastManager.getInstance(getContextWorkaround()).unregisterReceiver(serviceStateReceiver);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        final Context context = getContextWorkaround();
        LogFactory.writeMessage(context, LOG_TAG, "Got OnActivityResult" ,data);
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            if (!vpnRunning){
                if(!Preferences.getInstance(context).getBoolean("44explained", false) && Build.VERSION.SDK_INT == 19){
                    LogFactory.writeMessage(context, LOG_TAG, "Opening Dialog explaining that this might not work on Android 4.4");
                    new AlertDialog.Builder(
                            context, ThemeHandler.getDialogTheme(context)).setTitle(R.string.warning).setCancelable(false).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            startVpn(context);
                        }
                    }).setMessage(R.string.android4_4_warning).show();
                    LogFactory.writeMessage(context, LOG_TAG, "Dialog is now being shown");
                }else{
                    startVpn(context);
                }
                Preferences.getInstance(context).getBoolean("44explained", true);
            }else{
                if(wasStartedWithTasker){
                    LogFactory.writeMessage(context, LOG_TAG, "Opening dialog which warns that the app was started using Tasker");
                    new AlertDialog.Builder(
                            context,ThemeHandler.getDialogTheme(context)).setTitle(R.string.warning).setMessage(R.string.warning_started_using_tasker). setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            LogFactory.writeMessage(context, LOG_TAG, "User clicked OK in the dialog warning about Tasker");
                            stopVpn();
                            dialog.cancel();
                        }
                    }).setCancelable(false).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            LogFactory.writeMessage(context, LOG_TAG, "User cancelled stopping DNSChanger as it was started using tasker");
                        }
                    }).show();
                    LogFactory.writeMessage(context, LOG_TAG, "Dialog is now being shown");
                }else stopVpn();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startVpn(final Context ctx) {
        if(PreferencesAccessor.checkConnectivityOnStart(ctx)){
            final LoadingDialog dialog = new LoadingDialog(ctx, R.string.checking_connectivity, R.string.dialog_connectivity_description);
            dialog.show();
            checkDNSReachability(new DNSReachabilityCallback() {
                @Override
                public void checkFinished(@NonNull List<IPPortPair> unreachable, @NonNull List<IPPortPair> reachable) {
                    if(isDetached() || !isAdded()) return;
                    dialog.dismiss();
                    if(unreachable.size() == 0){
                        ((MainActivity)ctx).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                start();
                            }
                        });
                    }else{
                        String _text = getString(R.string.no_connectivity_warning_text);
                        StringBuilder builder = new StringBuilder();
                        _text = _text.replace("[x]", unreachable.size() + reachable.size() + "");
                        _text = _text.replace("[y]", unreachable.size() + "");
                        boolean customPorts = PreferencesAccessor.areCustomPortsEnabled(ctx);
                        for(IPPortPair p: unreachable) {
                            if(p == null)continue;
                            builder.append("- ").append(p.formatForTextfield(customPorts)).append("\n");
                        }
                        _text = _text.replace("[servers]", builder.toString());
                        final String text = _text;
                        ((MainActivity)ctx).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(ctx, ThemeHandler.getDialogTheme(ctx))
                                        .setTitle(R.string.warning).setCancelable(true).setPositiveButton(R.string.start, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        start();
                                    }
                                }).setNegativeButton(R.string.cancel, null).setMessage(text).show();
                            }
                        });
                    }
                }

                private void start(){
                    Intent i;
                    LogFactory.writeMessage(ctx, LOG_TAG, "Starting VPN",
                            i = DNSVpnService.getStartVPNIntent(ctx));
                    wasStartedWithTasker = false;
                    Util.startService(ctx, i);
                    vpnRunning = true;
                    setIndicatorState(true);
                }
            });
        }else{
            Intent i;
            LogFactory.writeMessage(ctx, LOG_TAG, "Starting VPN",
                    i = DNSVpnService.getStartVPNIntent(ctx));
            wasStartedWithTasker = false;
            Util.startService(ctx, i);
            vpnRunning = true;
            setIndicatorState(true);
        }
    }

    private void stopVpn() {
        Intent i;
        LogFactory.writeMessage(getContextWorkaround(), LOG_TAG, "Stopping VPN",
                i = DNSVpnService.getDestroyIntent(getContextWorkaround()));
        getContextWorkaround().startService(i);
        vpnRunning = false;
        setIndicatorState(false);
    }

    public void toggleVPN(){
        if (vpnRunning){
            stopVpn();
        }else startVpn(getContextWorkaround());
    }

    private Context getContextWorkaround() {
        Context ctx = getContext();
        if(ctx == null) return DNSChanger.context;
        return ctx;
    }

    private Context getContextWorkaround(@NonNull View view) {
        Context ctx = getContext();
        if(ctx == null) ctx = view.getContext();
        if(ctx == null) return DNSChanger.context;
        return ctx;
    }

    public boolean toggleCurrentInputFocus(){
        if(dns1 != null && dns1.hasFocus()){
            dns2.requestFocus();
        }else if(dns2 != null && dns2.hasFocus()){
            dns1.requestFocus();
        }else return false;
        return true;
    }

    public void checkDNSReachability(final DNSReachabilityCallback callback){
        List<IPPortPair> servers = PreferencesAccessor.getAllDNSPairs(getContextWorkaround(), true);
        callback.setServers(servers.size());
        for(final IPPortPair pair: servers){
            DNSQueryUtil.runAsyncDNSQuery(pair, "frostnerd.com", PreferencesAccessor.sendDNSOverTCP(getContextWorkaround()), Record.TYPE.A,
                    Record.CLASS.IN, new Util.DNSQueryResultListener() {
                @Override
                public void onSuccess(List<Record<? extends Data>> response) {
                    callback.checkProgress(pair, true);
                }

                @Override
                public void onError(@Nullable Exception e) {
                    callback.checkProgress(pair, false);
                }
            }, 1);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu,inflater);
        inflater.inflate(PreferencesAccessor.isIPv6Enabled(getContextWorkaround()) ? (PreferencesAccessor.isIPv4Enabled(getContextWorkaround()) ? ((settingV6 ? R.menu.menu_main_v6 : R.menu.menu_main)) : R.menu.menu_main_no_ipv6) : R.menu.menu_main_no_ipv6,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_switch_ip_version){
            settingV6 = !settingV6;
            Util.getActivity(this).invalidateOptionsMenu();
            setEditTextState();
            ((AppCompatActivity)requireContext()).getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
        }
        return super.onOptionsItemSelected(item);
    }

    public static abstract class DNSReachabilityCallback{
        @NonNull private final List<IPPortPair> unreachable = new ArrayList<>();
        @NonNull private final List<IPPortPair> reachable = new ArrayList<>();
        private int servers;

        public abstract void checkFinished(@NonNull List<IPPortPair> unreachable, @NonNull List<IPPortPair> reachable);

        public final void checkProgress(@NonNull IPPortPair server, boolean reachable){
            if(server == null || server.isEmpty())return;
            if(!reachable)unreachable.add(server);
            else this.reachable.add(server);
            if(this.unreachable.size() + this.reachable.size() >= servers)checkFinished(this.unreachable, this.reachable);
        }

        void setServers(int servers){
            this.servers = servers;
        }

    }
}
