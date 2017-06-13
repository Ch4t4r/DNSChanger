package com.frostnerd.dnschanger.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostnerd.dnschanger.API.API;
import com.frostnerd.dnschanger.API.ThemeHandler;
import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.dialogs.DefaultDNSDialog;
import com.frostnerd.dnschanger.services.ConnectivityBackgroundService;
import com.frostnerd.dnschanger.services.DNSVpnService;
import com.frostnerd.dnschanger.tasker.ConfigureActivity;
import com.frostnerd.utils.design.MaterialEditText;
import com.frostnerd.utils.general.IntentUtil;
import com.frostnerd.utils.general.Utils;
import com.frostnerd.utils.networking.NetworkUtil;
import com.frostnerd.utils.preferences.Preferences;

import java.util.Random;

/**
 * Copyright Daniel Wolf 2017
 * All rights reserved.
 *
 * Terms on usage of my code can be found here: https://git.frostnerd.com/PublicAndroidApps/DnsChanger/blob/master/README.md
 *
 * <p>
 * development@frostnerd.com
 */
public class MainActivity extends AppCompatActivity {
    private Button startStopButton;
    private boolean vpnRunning, wasStartedWithTasker = false;
    private MaterialEditText met_dns1, met_dns2;
    private EditText dns1, dns2;
    private boolean doStopVPN = true;
    private static final String LOG_TAG = "[MainActivity]";

    private TextView connectionText;
    private ImageView connectionImage;
    private Button rate, info;
    private ImageButton importButton;
    private View running_indicator;
    private DefaultDNSDialog defaultDnsDialog;
    private LinearLayout wrapper;
    private boolean settingV6 = false;
    private final int REQUEST_SETTINGS = 13;

    @Override
    protected void onDestroy() {
        LogFactory.writeMessage(this, LOG_TAG, "Destroying");
        if(dialog1 != null)dialog1.cancel();
        if(dialog2 != null)dialog2.cancel();
        if(defaultDnsDialog != null)defaultDnsDialog.cancel();
        LogFactory.writeMessage(this, LOG_TAG, "Destroyed");
        super.onDestroy();
    }

    private AlertDialog dialog1, dialog2;

    private BroadcastReceiver serviceStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Received ServiceState Answer", intent);
            vpnRunning = intent.getBooleanExtra("vpn_running",false);
            wasStartedWithTasker = intent.getBooleanExtra("started_with_tasker", false);
            setIndicatorState(intent.getBooleanExtra("vpn_running",false));
        }
    };

    private void setIndicatorState(boolean vpnRunning) {
        LogFactory.writeMessage(this, LOG_TAG, "Changing IndicatorState to " + vpnRunning);
        if (vpnRunning) {
            int color = Color.parseColor("#42A5F5");
            connectionText.setText(R.string.running);
            connectionImage.setImageResource(R.drawable.ic_thumb_up);
            startStopButton.setText(R.string.stop);
            running_indicator.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else {
            TypedValue typedValue = new TypedValue();
            Resources.Theme theme = getTheme();
            theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true);
            connectionText.setText(R.string.not_running);
            connectionImage.setImageResource(R.drawable.ic_thumb_down);
            startStopButton.setText(R.string.start);
            running_indicator.setBackgroundColor(typedValue.data);
        }
        LogFactory.writeMessage(this, LOG_TAG, "IndictorState set");
    }

    public void rateApp(View v) {
        final String appPackageName = getPackageName();
        LogFactory.writeMessage(this, LOG_TAG, "Opening site to rate app");
        try {
            LogFactory.writeMessage(this, LOG_TAG, "Trying to open market");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            LogFactory.writeMessage(this, LOG_TAG, "Market was opened");
        } catch (android.content.ActivityNotFoundException e) {
            LogFactory.writeMessage(this, LOG_TAG, "Market not present. Opening with general ACTION_VIEW");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
        Preferences.put(MainActivity.this, "rated",true);
    }

    public void openDNSInfoDialog(View v) {
        LogFactory.writeMessage(this, LOG_TAG, "Opening Dialog with info about DNS");
        dialog1 = new AlertDialog.Builder(this,ThemeHandler.getDialogTheme(this)).setTitle(R.string.info_dns_button).setMessage(R.string.dns_info_text).setCancelable(true).setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        }).show();
        LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(ThemeHandler.getAppTheme(this));
        LogFactory.writeMessage(this, LOG_TAG, "Created Activity", getIntent());
        API.updateTiles(this);
        LogFactory.writeMessage(this, LOG_TAG, "Launching ConnectivityBackgroundService");
        startService(new Intent(this, ConnectivityBackgroundService.class));
        LogFactory.writeMessage(this, LOG_TAG, "Setting ContentView");
        setContentView(R.layout.activity_main);
        met_dns1 = (MaterialEditText) findViewById(R.id.met_dns1);
        met_dns2 = (MaterialEditText) findViewById(R.id.met_dns2);
        dns1 = (EditText) findViewById(R.id.dns1);
        dns2 = (EditText) findViewById(R.id.dns2);
        connectionImage = (ImageView)findViewById(R.id.connection_status_image);
        connectionText = (TextView)findViewById(R.id.connection_status_text);
        rate = (Button)findViewById(R.id.rate);
        info = (Button)findViewById(R.id.dnsInfo);
        wrapper = (LinearLayout)findViewById(R.id.activity_main);
        importButton = (ImageButton)findViewById(R.id.default_dns_view_image);
        running_indicator = findViewById(R.id.running_indicator);
        dns1.setText(Preferences.getString(MainActivity.this, "dns1", "8.8.8.8"));
        dns2.setText(Preferences.getString(MainActivity.this, "dns2", "8.8.4.4"));
        startStopButton = (Button) findViewById(R.id.startStopButton);
        startStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent i = VpnService.prepare(MainActivity.this);
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Startbutton clicked. Configuring VPN if needed");
                if (i != null){
                    LogFactory.writeMessage(MainActivity.this, LOG_TAG, "VPN isn't prepared yet. Showing dialog explaining the VPN");
                    dialog2 = new AlertDialog.Builder(MainActivity.this,ThemeHandler.getDialogTheme(MainActivity.this)).setTitle(R.string.information).setMessage(R.string.vpn_explain)
                            .setCancelable(false).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Requesting VPN access", i);
                            startActivityForResult(i, 0);
                        }
                    }).show();
                    LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Dialog is now being shown");
                }else{
                    LogFactory.writeMessage(MainActivity.this, LOG_TAG, "VPNService is already configured");
                    onActivityResult(0, RESULT_OK, null);
                }
            }
        });
        dns1.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(vpnRunning && doStopVPN && !wasStartedWithTasker)stopVpn();
                if (!NetworkUtil.isAssignableAddress(s.toString(),settingV6,false)) {
                    met_dns1.setIndicatorState(MaterialEditText.IndicatorState.INCORRECT);
                } else {
                    met_dns1.setIndicatorState(MaterialEditText.IndicatorState.UNDEFINED);
                    Preferences.put(MainActivity.this, settingV6 ? "dns1-v6" :"dns1", s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        dns2.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(vpnRunning && doStopVPN && !wasStartedWithTasker)stopVpn();
                if (!NetworkUtil.isAssignableAddress(s.toString(),settingV6, true)) {
                    met_dns2.setIndicatorState(MaterialEditText.IndicatorState.INCORRECT);
                } else {
                    met_dns2.setIndicatorState(MaterialEditText.IndicatorState.UNDEFINED);
                    Preferences.put(MainActivity.this, settingV6 ? "dns2-v6" : "dns2", s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        findViewById(R.id.settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i;
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Opening Settings",
                        i = new Intent(MainActivity.this, SettingsActivity.class));
                startActivityForResult(i,REQUEST_SETTINGS);
            }
        });
        getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
        if(!Preferences.getBoolean(this, "first_run",true) && !Preferences.getBoolean(this, "rated",false) && new Random().nextInt(100) <= 8){
            LogFactory.writeMessage(this, LOG_TAG, "Showing dialog requesting rating");
            new AlertDialog.Builder(this,ThemeHandler.getDialogTheme(this)).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    rateApp(null);
                }
            }).setNegativeButton(R.string.dont_ask_again, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Preferences.put(MainActivity.this, "rated",true);
                    dialog.cancel();
                }
            }).setNeutralButton(R.string.not_now, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            }).setMessage(R.string.rate_request_text).setTitle(R.string.rate).show();
            LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
        }
        if(Preferences.getBoolean(this, "first_run", true) && API.isTaskerInstalled(this)){
            LogFactory.writeMessage(this, LOG_TAG, "Showing dialog telling the user that this app supports Tasker");
            new AlertDialog.Builder(this,ThemeHandler.getDialogTheme(this)).setTitle(R.string.tasker_support).setMessage(R.string.app_supports_tasker_text).setPositiveButton(R.string.got_it, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            }).show();
            LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
        }
        API.updateAppShortcuts(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                API.getDBHelper(MainActivity.this).getReadableDatabase();
            }
        });
        LogFactory.writeMessage(this, LOG_TAG, "Done with OnCreate");
        Preferences.put(this, "first_run", false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        settingV6 = !API.isIPv4Enabled(this) || (API.isIPv6Enabled(this) && settingV6);
        LogFactory.writeMessage(this, LOG_TAG, "Got OnResume");
        LogFactory.writeMessage(this, LOG_TAG, "Sending ServiceStateRequest as broadcast");
        vpnRunning = API.isServiceRunning(this);
        setIndicatorState(vpnRunning);
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceStateReceiver, new IntentFilter(API.BROADCAST_SERVICE_STATUS_CHANGE));
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(API.BROADCAST_SERVICE_STATE_REQUEST));
        doStopVPN = false;
        if(!settingV6){
            dns1.setText(Preferences.getString(this, "dns1", "8.8.8.8"));
            dns2.setText(Preferences.getString(this, "dns2", "8.8.4.4"));
        }else{
            dns1.setText(Preferences.getString(this, "dns1-v6", "2001:4860:4860::8888"));
            dns2.setText(Preferences.getString(this, "dns2-v6", "2001:4860:4860::8844"));
        }
        doStopVPN = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogFactory.writeMessage(this, LOG_TAG, "Got OnPause");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStateReceiver);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        invalidateOptionsMenu();
        LogFactory.writeMessage(this, LOG_TAG, "Got onPostResume");
    }

    public void openDefaultDNSDialog(View v) {
        LogFactory.writeMessage(this, LOG_TAG, "Opening DefaultDNSDialog");
        defaultDnsDialog = new DefaultDNSDialog(this, ThemeHandler.getDialogTheme(this), new DefaultDNSDialog.OnProviderSelectedListener(){
            @Override
            public void onProviderSelected(String name, String dns1, String dns2, String dns1V6, String dns2V6) {
                if(settingV6){
                    if(!dns1V6.equals(""))MainActivity.this.dns1.setText(dns1V6);
                    MainActivity.this.dns2.setText(dns2V6);
                    if(!dns1.equals(""))Preferences.put(MainActivity.this, "dns1", dns1);
                    Preferences.put(MainActivity.this, "dns2", dns2);
                }else{
                    if(!dns1.equals(""))MainActivity.this.dns1.setText(dns1);
                    MainActivity.this.dns2.setText(dns2);
                    if(!dns1V6.equals(""))Preferences.put(MainActivity.this, "dns1-v6", dns1V6);
                    Preferences.put(MainActivity.this, "dns2-v6", dns2V6);
                }
            }
        });
        defaultDnsDialog.show();
        LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogFactory.writeMessage(this, LOG_TAG, "Got OnActivityResult" ,data);
        if (requestCode == 0 && resultCode == RESULT_OK) {
            if (!vpnRunning){
                if(!Preferences.getBoolean(this, "44explained", false) && Build.VERSION.SDK_INT == 19){
                    LogFactory.writeMessage(this, LOG_TAG, "Opening Dialog explaining that this might not work on Android 4.4");
                    new AlertDialog.Builder(this, ThemeHandler.getDialogTheme(this)).setTitle(R.string.warning).setCancelable(false).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            startVpn();
                        }
                    }).setMessage(R.string.android4_4_warning).show();
                    LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
                }else{
                    startVpn();
                }
                Preferences.getBoolean(this, "44explained", true);
            }else{
                if(wasStartedWithTasker){
                    LogFactory.writeMessage(this, LOG_TAG, "Opening dialog which warns that the app was started using Tasker");
                    new AlertDialog.Builder(this,ThemeHandler.getDialogTheme(this)).setTitle(R.string.warning).setMessage(R.string.warning_started_using_tasker). setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            LogFactory.writeMessage(MainActivity.this, LOG_TAG, "User clicked OK in the dialog warning about Tasker");
                            stopVpn();
                            dialog.cancel();
                        }
                    }).setCancelable(false).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            LogFactory.writeMessage(MainActivity.this, LOG_TAG, "User cancelled stopping DNSChanger as it was started using tasker");
                        }
                    }).show();
                    LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
                }else stopVpn();
            }
        }else if(requestCode == 1 && resultCode == RESULT_OK){
            final Snackbar snackbar = Snackbar.make(wrapper, R.string.shortcut_created, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(R.string.show, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snackbar.dismiss();
                    Utils.goToLauncher(MainActivity.this);
                }
            });
            snackbar.show();
        }else if(requestCode == REQUEST_SETTINGS && resultCode == RESULT_FIRST_USER){
            if(IntentUtil.checkExtra("themeupdated",data))IntentUtil.restartActivity(this);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startVpn() {
        Intent i;
        LogFactory.writeMessage(this, LOG_TAG, "Starting VPN",
                i = DNSVpnService.getStartVPNIntent(this));
        wasStartedWithTasker = false;
        startService(i);
        vpnRunning = true;
        setIndicatorState(true);
    }

    private void stopVpn() {
        Intent i;
        LogFactory.writeMessage(this, LOG_TAG, "Stopping VPN",
                i = DNSVpnService.getDestroyIntent(this));
        startService(i);
        stopService(new Intent(this, DNSVpnService.class));
        vpnRunning = false;
        setIndicatorState(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(API.isIPv6Enabled(this) ? (API.isIPv4Enabled(this) ? ((settingV6 ? R.menu.menu_main_v6 : R.menu.menu_main)) : R.menu.menu_main_no_ipv6) : R.menu.menu_main_no_ipv6,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_switch_ip_version){
            doStopVPN = false;
            settingV6 = !settingV6;
            invalidateOptionsMenu();
            dns1.setText(Preferences.getString(this,settingV6 ? "dns1-v6" : "dns1", settingV6 ? "2001:4860:4860::8888" : "8.8.8.8"));
            dns2.setText(Preferences.getString(this,settingV6 ? "dns2-v6" : "dns2", settingV6 ? "2001:4860:4860::8844" : "8.8.4.4"));
            dns1.setInputType(InputType.TYPE_CLASS_TEXT);
            dns2.setInputType(InputType.TYPE_CLASS_TEXT);
            getSupportActionBar().setSubtitle(getString(R.string.subtitle_configuring).replace("[[x]]",settingV6 ? "Ipv6" : "Ipv4"));
            doStopVPN = true;
        }else if(item.getItemId() == R.id.create_shortcut){
            Intent i;
            LogFactory.writeMessage(this, LOG_TAG, "User wants to create a shortcut",
                    i = new Intent(this, ConfigureActivity.class).putExtra("creatingShortcut", true));
            startActivityForResult(i,1);
        }
        return super.onOptionsItemSelected(item);
    }
}
