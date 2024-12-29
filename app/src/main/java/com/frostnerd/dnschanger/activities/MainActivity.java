package com.frostnerd.dnschanger.activities;

import android.Manifest;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.collection.ArraySet;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

import com.frostnerd.design.DesignUtil;
import com.frostnerd.design.dialogs.FileChooserDialog;
import com.frostnerd.design.dialogs.LoadingDialog;
import com.frostnerd.design.navigationdrawer.DrawerItem;
import com.frostnerd.design.navigationdrawer.DrawerItemCreator;
import com.frostnerd.design.navigationdrawer.NavigationDrawerActivity;
import com.frostnerd.design.navigationdrawer.StyleOptions;
import com.frostnerd.dnschanger.BuildConfig;
import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.database.entities.IPPortPair;
import com.frostnerd.dnschanger.dialogs.DNSEntryListDialog;
import com.frostnerd.dnschanger.dialogs.ExportSettingsDialog;
import com.frostnerd.dnschanger.fragments.CurrentNetworksFragment;
import com.frostnerd.dnschanger.fragments.DnsQueryFragment;
import com.frostnerd.dnschanger.fragments.MainFragment;
import com.frostnerd.dnschanger.fragments.QueryLogFragment;
import com.frostnerd.dnschanger.fragments.RulesFragment;
import com.frostnerd.dnschanger.fragments.SettingsFragment;
import com.frostnerd.dnschanger.services.DNSVpnService;
import com.frostnerd.dnschanger.services.RuleImportService;
import com.frostnerd.dnschanger.tasker.ConfigureActivity;
import com.frostnerd.dnschanger.util.Preferences;
import com.frostnerd.dnschanger.util.PreferencesAccessor;
import com.frostnerd.dnschanger.util.RuleImport;
import com.frostnerd.dnschanger.util.ThemeHandler;
import com.frostnerd.dnschanger.util.Util;
import com.frostnerd.general.Utils;
import com.frostnerd.general.permissions.PermissionsUtil;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.tasks.Task;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
public class MainActivity extends NavigationDrawerActivity implements RuleImport.ImportStartedListener {
    private static final String LOG_TAG = "[MainActivity]";
    private static final int REQUEST_PERMISSION_IMPORT_SETTINGS = 131, REQUEST_PERMISSION_EXPORT_SETTINGS = 130, REQUEST_ADVANCED_SETTINGS = 132, REQUEST_APP_UPDATE = 133;
    private AlertDialog dialog1;
    private DNSEntryListDialog dnsEntryListDialog;
    private MainFragment mainFragment;
    private SettingsFragment settingsFragment;
    private DrawerItem defaultDrawerItem, settingsDrawerItem;
    @ColorInt private int backgroundColor;
    @ColorInt private int textColor, navDrawableColor;
    private boolean startedActivity = false, importingRules = false;
    private final BroadcastReceiver shortcutReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Snackbar snackbar = Snackbar.make(getContentFrame(), R.string.shortcut_created, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(R.string.show, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snackbar.dismiss();
                    Utils.goToLauncher(MainActivity.this);
                }
            });
            snackbar.show();
        }
    };
    private BroadcastReceiver importFinishedReceiver;

    @Override
    protected void onResume() {
        super.onResume();
        startedActivity = false;
        // Receiver is not unregistered in onPause() because the app is in the background when a shortcut
        // is created
        registerReceiver(shortcutReceiver, new IntentFilter(Util.BROADCAST_SHORTCUT_CREATED), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHandler.getAppTheme(this));
        backgroundColor = ThemeHandler.resolveThemeAttribute(getTheme(), android.R.attr.colorBackground);
        textColor = ThemeHandler.resolveThemeAttribute(getTheme(), android.R.attr.textColor);
        navDrawableColor = ThemeHandler.resolveThemeAttribute(getTheme(), R.attr.navDrawableColor);
        super.onCreate(savedInstanceState);
        Util.updateAppShortcuts(this);
        Util.runBackgroundConnectivityCheck(this, true);
        final Preferences preferences = Preferences.getInstance(this);
        if(preferences.getBoolean( "first_run", true)) preferences.put( "excluded_apps", new ArraySet<>(Arrays.asList(getResources().getStringArray(R.array.default_blacklist))));
        if(preferences.getBoolean( "first_run", true) && Util.isTaskerInstalled(this)){
            LogFactory.writeMessage(this, LOG_TAG, "Showing dialog telling the user that this app supports Tasker");
            new AlertDialog.Builder(this,ThemeHandler.getDialogTheme(this)).setTitle(R.string.tasker_support).setMessage(R.string.app_supports_tasker_text).setPositiveButton(com.frostnerd.utils.design.R.string.got_it, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            }).show();
            LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
        }
        int random = new Random().nextInt(100), launches = preferences.getInteger( "launches", 0);
        preferences.put( "launches", launches+1);
        if(launches >= 5 && !preferences.getBoolean("first_run", true) &&
                !preferences.getBoolean("rated", false) && random <= 16){
            LogFactory.writeMessage(this, LOG_TAG, "Showing dialog reqesting rating");
            new AlertDialog.Builder(this,ThemeHandler.getDialogTheme(this)).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    rateApp();
                }
            }).setNegativeButton(R.string.dont_ask_again, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    preferences.put("rated",true);
                    dialog.cancel();
                }
            }).setNeutralButton(R.string.not_now, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            }).setMessage(R.string.rate_request_text).setTitle(R.string.rate).show();
            LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
        } else if(launches >= 5 && !preferences.getBoolean("nebulo_shown", false) && random <= 20) {
            showNebuloDialog();
        }
        Util.updateTiles(this);
        preferences.put( "first_run", false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        tryCheckUpdate();
    }

    private void tryCheckUpdate() {
        try {
            AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(this);
            Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
            appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
                try {
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                        int stalenessDays = appUpdateInfo.clientVersionStalenessDays() != null ? appUpdateInfo.clientVersionStalenessDays() : Integer.MIN_VALUE;
                        boolean shouldHoldUpdate = System.currentTimeMillis() >= Preferences.getInstance(this).getLong("ask_update_later_than", 0);
                        boolean shouldUpdateImmediate = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) && appUpdateInfo.updatePriority() >= 4;
                        boolean shouldUpdate = !shouldHoldUpdate && stalenessDays >= 24;
                        shouldUpdate = shouldUpdate || (stalenessDays >= 14 && !shouldHoldUpdate && appUpdateInfo.updatePriority() >= 1);
                        shouldUpdate = shouldUpdate || (stalenessDays >= 7 && !shouldHoldUpdate && appUpdateInfo.updatePriority() >= 2);
                        shouldUpdate = shouldUpdate || (stalenessDays >= 3 && appUpdateInfo.updatePriority() >= 3);
                        shouldUpdate = shouldUpdate || (stalenessDays >= 1 && appUpdateInfo.updatePriority() >= 4);
                        shouldUpdate = shouldUpdate || appUpdateInfo.updatePriority() >= 5;
                        if (shouldUpdate) {
                            appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    shouldUpdateImmediate ? AppUpdateType.IMMEDIATE : AppUpdateType.FLEXIBLE,
                                    this,
                                    REQUEST_APP_UPDATE);

                        }
                    }
                } catch (Throwable ex2) {
                   ex2.printStackTrace();
                }
            });
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(getCurrentFragment() == settingsFragment){
            getMenuInflater().inflate(R.menu.menu_settings, menu);

            SearchManager searchManager = Utils.requireNonNull((SearchManager)getSystemService(Context.SEARCH_SERVICE));
            SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            searchView.setIconifiedByDefault(false); // Do not iconify the widget; expand it by default
            searchView.setOnQueryTextListener(settingsFragment);
            return true;
        }else return super.onCreateOptionsMenu(menu);
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return Preferences.getInstance(this);
    }

    @Override
    protected void onDestroy() {
        if(dialog1 != null && dialog1.isShowing())dialog1.cancel();
        if(dnsEntryListDialog != null && dnsEntryListDialog.isShowing()) dnsEntryListDialog.cancel();
        if(importFinishedReceiver != null)unregisterReceiver(importFinishedReceiver);
        unregisterReceiver(shortcutReceiver);
        super.onDestroy();
    }

    @Override
    protected Configuration getConfiguration() {
        Configuration config = Configuration.Companion.withDefaults();
        config.setDismissFragmentsOnPause$com_frostnerd_utilskt_lifecycle(true);
        return config;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = false;
        switch(keyCode){
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if(currentFragment() instanceof MainFragment){
                    handled = true;
                    ((MainFragment)currentFragment()).toggleVPN();
                }break;
            case KeyEvent.KEYCODE_DPAD_DOWN:case KeyEvent.KEYCODE_PAGE_DOWN:case KeyEvent.KEYCODE_DPAD_UP:case KeyEvent.KEYCODE_PAGE_UP:
                if(currentFragment() instanceof MainFragment){
                    if(((MainFragment)currentFragment()).toggleCurrentInputFocus())handled = true;
                }
        }
        return handled || super.onKeyDown(keyCode, event);
    }

    @NonNull
    @Override
    public DrawerItem getDefaultItem() {
        return defaultDrawerItem;
    }

    @Override
    public void onItemClicked(DrawerItem item, boolean handle) {
    }

    public Fragment currentFragment(){
        return getCurrentFragment();
    }

    @Override
    public List<DrawerItem> createDrawerItems() {
        DrawerItemCreator itemCreator = new DrawerItemCreator(this);
        itemCreator.createItemAndContinue(R.string.nav_title_main);
        itemCreator.createItemAndContinue(R.string.nav_title_dns, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_home)), new DrawerItem.FragmentCreator() {
            @Override
            public Fragment getFragment(@Nullable Bundle arguments) {
                return mainFragment=new MainFragment();
            }
        }).accessLastItemAndContinue(new DrawerItemCreator.ItemAccessor() {
            @Override
            public void access(DrawerItem item) {
                defaultDrawerItem = item;
                item.setRecreateFragmentOnConfigChange(true);
            }
        });
        itemCreator.createItemAndContinue(R.string.settings, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_settings)), new DrawerItem.FragmentCreator() {
            @Override
            public Fragment getFragment(@Nullable Bundle arguments) {
                settingsFragment = new SettingsFragment();
                if(arguments != null)settingsFragment.setArguments(arguments);
                return settingsFragment;
            }
        }).accessLastItemAndContinue(new DrawerItemCreator.ItemAccessor() {
            @Override
            public void access(DrawerItem item) {
                settingsDrawerItem = item;
                item.setInvalidateActivityMenu(true);
            }
        });
        itemCreator.createItemAndContinue(R.string.nav_title_dns_query, setDrawableColor(DesignUtil.getDrawable(this, android.R.drawable.ic_menu_search)), new DrawerItem.FragmentCreator() {
            @Override
            public Fragment getFragment(@Nullable Bundle arguments) {
                return new DnsQueryFragment();
            }
        }).accessLastItemAndContinue(new DrawerItemCreator.ItemAccessor() {
            @Override
            public void access(DrawerItem item) {
                item.setInvalidateActivityMenu(true);
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            itemCreator.createItemAndContinue(R.string.nav_title_current_networks, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_network_check)), new DrawerItem.FragmentCreator() {
                @NonNull
                @Override
                public Fragment getFragment(@Nullable Bundle arguments) {
                    return new CurrentNetworksFragment();
                }
            }).accessLastItemAndContinue(new DrawerItemCreator.ItemAccessor() {
                @Override
                public void access(DrawerItem item) {
                    item.setInvalidateActivityMenu(true);
                }
            });
        }
        if(PreferencesAccessor.isAdvancedModeEnabled(this)){
            itemCreator.createItemAndContinue(R.string.nav_title_advanced);
            itemCreator.createItemAndContinue(R.string.title_advanced_settings, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_settings)), new DrawerItem.ClickListener() {
                @Override
                public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                    startActivityForResult(new Intent(MainActivity.this, AdvancedSettingsActivity.class), REQUEST_ADVANCED_SETTINGS);
                    return false;
                }

                @Override
                public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                    return false;
                }
            });
            if(PreferencesAccessor.areRulesEnabled(this)){
                itemCreator.createItemAndContinue(R.string.nav_title_rules, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_list_bullet_point)), new DrawerItem.FragmentCreator() {
                    @Override
                    public Fragment getFragment(@Nullable Bundle arguments) {
                        return new RulesFragment();
                    }
                });
            }
            if(PreferencesAccessor.isQueryLoggingEnabled(this)){
                itemCreator.createItemAndContinue(R.string.nav_title_query_log, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_timelapse)), new DrawerItem.FragmentCreator() {
                    @Override
                    public Fragment getFragment(@Nullable Bundle arguments) {
                        return new QueryLogFragment();
                    }
                }).accessLastItemAndContinue(new DrawerItemCreator.ItemAccessor() {
                    @Override
                    public void access(DrawerItem item) {
                        item.setRecreateFragmentOnConfigChange(true);
                    }
                });
            }
        }
        itemCreator.createItemAndContinue(R.string.nav_title_learn);
        itemCreator.createItemAndContinue(R.string.nav_title_how_does_it_work, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_wrench)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                dialog1 = new AlertDialog.Builder(MainActivity.this, ThemeHandler.getDialogTheme(MainActivity.this)).setTitle(R.string.nav_title_how_does_it_work)
                        .setMessage(R.string.info_text_how_does_it_work).setNeutralButton(R.string.close, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        }).show();
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.nav_title_what_is_dns, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_help)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Opening Dialog with info about DNS");
                dialog1 = new AlertDialog.Builder(MainActivity.this, ThemeHandler.getDialogTheme(MainActivity.this)).setTitle(R.string.info_dns_button).setMessage(R.string.info_text_dns).setCancelable(true).setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                }).show();
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Dialog is now being shown");
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.nav_title_features);
        itemCreator.createItemAndContinue(R.string.shortcuts, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_open_in_new)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                dialog1 = new AlertDialog.Builder(MainActivity.this).setTitle(R.string.shortcuts).setNegativeButton(R.string.close, null)
                        .setPositiveButton("pos", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                openSettingsAndScrollToKey("shortcut_category");
                            }
                        })
                        .setNeutralButton(R.string.create_one, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int cnt) {
                                Intent i;
                                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "User wants to create a shortcut",
                                        i = new Intent(MainActivity.this, ConfigureActivity.class).putExtra("creatingShortcut", true));
                                startActivityForResult(i,1);
                            }
                        }).setMessage(R.string.feature_shortcuts).create();
                dialog1.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialogInterface) {
                        Button button = dialog1.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                        button.setText("");
                        Drawable gear = setDrawableColor(getResources().getDrawable(R.drawable.ic_settings));
                        button.setCompoundDrawablesWithIntrinsicBounds(gear, null, null, null);
                    }
                });
                dialog1.show();
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        if(Util.isTaskerInstalled(this)){
            itemCreator.createItemAndContinue(R.string.tasker_support, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_thumb_up)), new DrawerItem.ClickListener() {
                @Override
                public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                    return false;
                }

                @Override
                public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                    return false;
                }
            });
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            itemCreator.createItemAndContinue(R.string.nav_title_tiles, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_viewquilt)), new DrawerItem.ClickListener() {
                @Override
                public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                    dialog1 = new AlertDialog.Builder(MainActivity.this).setTitle(R.string.nav_title_tiles).setMessage(R.string.feature_tiles)
                            .setNeutralButton(R.string.close, null).show();
                    return false;
                }

                @Override
                public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                    return false;
                }
            });
        }
        itemCreator.createItemAndContinue(R.string.nav_title_pin_protection, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_action_key)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                dialog1 = new AlertDialog.Builder(MainActivity.this).setTitle(item.getTitle()).setNegativeButton(R.string.close, null)
                        .setPositiveButton("pos", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                openSettingsAndScrollToKey("pin_category");
                            }
                        }).setMessage(R.string.feature_pin_protection).create();
                dialog1.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialogInterface) {
                        Button button = dialog1.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
                        button.setText("");
                        Drawable gear = setDrawableColor(getResources().getDrawable(R.drawable.ic_settings));
                        button.setCompoundDrawablesWithIntrinsicBounds(gear, null, null, null);
                    }
                });
                dialog1.show();
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.nav_title_more, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_ellipsis)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.nav_title_importexport);
        itemCreator.createItemAndContinue(R.string.title_import_settings, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_action_import)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                if(!PermissionsUtil.canReadExternalStorage(MainActivity.this)){
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION_IMPORT_SETTINGS);
                }else{
                    importSettings();
                }
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.title_export_settings,setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_action_export)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                if(!PermissionsUtil.canWriteExternalStorage(MainActivity.this)){
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_EXPORT_SETTINGS);
                }else{
                    new ExportSettingsDialog(MainActivity.this);
                }
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.app_name);
        itemCreator.createItemAndContinue("Nebulo", setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_nebulo)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem drawerItem, NavigationDrawerActivity navigationDrawerActivity, @Nullable Bundle bundle) {
                showNebuloDialog();
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem drawerItem, NavigationDrawerActivity navigationDrawerActivity) {
                return false;
            }
        });
        itemCreator.accessLastItemAndContinue(new DrawerItemCreator.ItemAccessor() {
            @Override
            public void access(DrawerItem drawerItem) {
                drawerItem.setDrawableRight(DesignUtil.getDrawable(MainActivity.this, R.drawable.ic_nebulo_ad));
            }
        });
        itemCreator.createItemAndContinue(R.string.rate, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_star)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                rateApp();
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.title_share_app, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_share)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.app_name));
                sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, getString(R.string.app_share_text));
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Showing chooser for share", sharingIntent);
                startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.share_using)));
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.contact_developer, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_person)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                        "mailto","support@frostnerd.com", null));
                String body = "\n\n\n\n\n\n\nSystem:\nApp version: " + BuildConfig.VERSION_CODE + " (" + BuildConfig.VERSION_NAME + ")\n"+
                        "Android: " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + " - " + Build.VERSION.CODENAME + ")";
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
                emailIntent.putExtra(Intent.EXTRA_EMAIL, "support@frostnerd.com");
                emailIntent.putExtra(Intent.EXTRA_TEXT, body);
                LogFactory.writeMessage(MainActivity.this, LOG_TAG, "Now showing chooser for contacting dev", emailIntent);
                startActivity(Intent.createChooser(emailIntent, getString(R.string.contact_developer)));
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.nav_title_libraries, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_library_books)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                String licenseText = getString(R.string.dialog_libraries_text) + "\n\n- - - - - - - - - - - -\ndnsjava by Brian Wellington - http://www.xbill.org/dnsjava/\n\n" + getString(R.string.license_bsd_2).replace("[yearrange]", "1998-2011").replace("[author]", "Brian Wellington");
                licenseText += "\n\n- - - - - - - - - - - -\nfirebase-jobdispatcher-android by Google\n\nAvailable under the [1]Apache License 2.0[2]";
                licenseText += "\n\n- - - - - - - - - - - -\npcap4j by Kaito Yamada\n\nAvailable under the [3]MIT License[4]";
                licenseText += "\n\n- - - - - - - - - - - -\nMiniDNS by Measite\n\nAvailable under the [5]Apache License 2.0[6]";
                licenseText += "\n\n- - - - - - - - - - - -\nMaterial icon pack by Google\n\nAvailable under the [7]Apache License 2.0[8]";
                licenseText += "\n\n- - - - - - - - - - - -\nGson by google\n\nAvailable under the [9]Apache License 2.0[a]";
                ClickableSpan span = new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        new AlertDialog.Builder(MainActivity.this).setTitle("Apache License 2.0").setPositiveButton(R.string.close, null).setMessage(R.string.license_apache_2).show();
                    }
                }, span2 = new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        String text = getString(R.string.mit_license);
                        text = text.replace("[name]", "Pcap4J");
                        text = text.replace("[author]", "Pcap4J.org");
                        text = text.replace("[yearrange]", "2011-2017");
                        new AlertDialog.Builder(MainActivity.this).setTitle("MIT License").setPositiveButton(R.string.close, null).setMessage(text).show();
                    }
                }, span3 = new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        new AlertDialog.Builder(MainActivity.this).setTitle("Apache License 2.0").setPositiveButton(R.string.close, null).setMessage(R.string.license_apache_2).show();
                    }
                }, span4 = new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        new AlertDialog.Builder(MainActivity.this).setTitle("Apache License 2.0").setPositiveButton(R.string.close, null).setMessage(R.string.license_apache_2).show();
                    }
                }, span5 = new ClickableSpan() {
                    @Override
                    public void onClick(View view) {
                        new AlertDialog.Builder(MainActivity.this).setTitle("Apache License 2.0").setPositiveButton(R.string.close, null).setMessage(R.string.license_apache_2).show();
                    }
                };
                SpannableString spannable = new SpannableString(licenseText.replaceAll("\\[.]",""));
                spannable.setSpan(span3, licenseText.indexOf("[1]"), licenseText.indexOf("[2]")-3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(span2, licenseText.indexOf("[3]")-6, licenseText.indexOf("[4]")-9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(span, licenseText.indexOf("[5]")-12, licenseText.indexOf("[6]")-15, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(span4, licenseText.indexOf("[7]")-18, licenseText.indexOf("[8]")-21, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(span5, licenseText.indexOf("[9]")-24, licenseText.indexOf("[a]")-27, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                dialog1 = new AlertDialog.Builder(MainActivity.this).setTitle(R.string.nav_title_libraries).setNegativeButton(R.string.close, null)
                        .setMessage(spannable).show();
                ((TextView)dialog1.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                return false;
            }
        });
        itemCreator.createItemAndContinue(R.string.title_about, setDrawableColor(DesignUtil.getDrawable(this, R.drawable.ic_info)), new DrawerItem.ClickListener() {
            @Override
            public boolean onClick(DrawerItem item, NavigationDrawerActivity drawerActivity, @Nullable Bundle arguments) {
                String text = getString(R.string.about_text).replace("[[version]]", BuildConfig.VERSION_NAME).replace("[[build]]", BuildConfig.VERSION_CODE + "");
                new AlertDialog.Builder(MainActivity.this).setTitle(R.string.title_about).setMessage(text)
                        .setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        }).show();
                return false;
            }

            @Override
            public boolean onLongClick(DrawerItem item, NavigationDrawerActivity drawerActivity) {
                new AlertDialog.Builder(MainActivity.this).setMessage(R.string.easter_egg).setTitle("(╯°□°）╯︵ ┻━┻")
                        .setPositiveButton("Okay :(", null).show();
                return true;
            }
        });
        return itemCreator.getDrawerItemsAndDestroy();
    }

    private void showNebuloDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this, ThemeHandler.getDialogTheme(this))
                .setTitle("Nebulo")
                .setMessage(R.string.nebulo_download_text)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Preferences.getInstance(MainActivity.this).putBoolean("nebulo_shown", true);
                        openMarket("com.frostnerd.smokescreen");
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Preferences.getInstance(MainActivity.this).putBoolean("nebulo_shown", true);
                        dialog.dismiss();
                    }
                })
                .setCancelable(false)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_PERMISSION_EXPORT_SETTINGS && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            new ExportSettingsDialog(this);
        }else if(requestCode == REQUEST_PERMISSION_IMPORT_SETTINGS && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            importSettings();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_ADVANCED_SETTINGS && resultCode == AppCompatActivity.RESULT_FIRST_USER) {
            reloadMenuItems();
        } else if(requestCode == REQUEST_APP_UPDATE) {
            if(resultCode != RESULT_OK) {
                long updateAskDelay = 2*24*60*60*1000; //2 Days
                Preferences.getInstance(this).putLong("ask_update_later_than", System.currentTimeMillis() + updateAskDelay);
            }
        } else {
            Fragment f = currentFragment();
            if(f instanceof MainFragment) {
                f.onActivityResult(requestCode, resultCode, data);
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void importSettings(){
        LogFactory.writeMessage(this, new String[]{LOG_TAG, "[IMPORTSETTINGS]"}, "Importing Setting. Showing chooser dialog.");
        new FileChooserDialog(this, false, FileChooserDialog.SelectionMode.FILE).setFileListener(new FileChooserDialog.FileSelectedListener() {
            @Override
            public void fileSelected(File file, FileChooserDialog.SelectionMode selectionMode) {
                LogFactory.writeMessage(MainActivity.this, new String[]{LOG_TAG,"[IMPORTSETTINGS]"}, "User choose File " + file);
                LogFactory.writeMessage(MainActivity.this, new String[]{LOG_TAG, "[IMPORTSETTINGS]"}, "Finishing Activity");
                finish();
                LogFactory.writeMessage(MainActivity.this, new String[]{LOG_TAG, "[IMPORTSETTINGS]"}, "Starting import (Opening SettingsImportActivity");
                SettingsImportActivity.importFromFile(MainActivity.this, file);
            }

            @Override
            public void multipleFilesSelected(File... files) {

            }
        }).showDialog();
        LogFactory.writeMessage(this, new String[]{LOG_TAG, "[IMPORTSETTINGS]"}, "Dialog is now showing");

    }

    private void openSettingsAndScrollToKey(String key){
        if(getCurrentFragment() instanceof SettingsFragment)((SettingsFragment)getCurrentFragment()).scrollToPreference(key);
        else{
            Bundle arguments = new Bundle();
            arguments.putString(SettingsFragment.ARGUMENT_SCROLL_TO_SETTING, key);
            clickItem(settingsDrawerItem, arguments);
        }
    }

    private Drawable setDrawableColor(Drawable drawable){
        DrawableCompat.setTint(drawable.mutate(), navDrawableColor);
        return drawable;
    }

    @Override
    public StyleOptions getStyleOptions() {
        return new StyleOptions().setListItemBackgroundColor(backgroundColor)
                .setSelectedListItemTextColor(textColor)
                .setSelectedListItemColor(ThemeHandler.getColor(this, R.attr.inputElementColor, -1))
                .setListItemTextColor(textColor)
                .setListViewBackgroundColor(backgroundColor)
                .setAlphaNormal(1.0f)
                .setHeaderTextColor(textColor)
                .setAlphaSelected(1.0f);
    }

    @Override
    public boolean useItemBackStack() {
        return true;
    }

    @Override
    public int maxBackStackRecursion() {
        return 0;
    }

    public void rateApp() {
        LogFactory.writeMessage(this, LOG_TAG, "Opening site to rate app");
        openMarket(getPackageName());
        Preferences.getInstance(this).put("rated",true);
    }

    private void openMarket(String packageName) {
        try {
            LogFactory.writeMessage(this, LOG_TAG, "Trying to open market");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
            LogFactory.writeMessage(this, LOG_TAG, "Market was opened");
        } catch (android.content.ActivityNotFoundException e) {
            LogFactory.writeMessage(this, LOG_TAG, "Market not present. Opening with general ACTION_VIEW");
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(MainActivity.this, R.string.nebulo_no_browser, Toast.LENGTH_LONG).show();
            }
        }
    }

    public void openDefaultDNSDialog(View v) {
        LogFactory.writeMessage(this, LOG_TAG, "Opening DNSEntryListDialog");
        dnsEntryListDialog = new DNSEntryListDialog(this, ThemeHandler.getDialogTheme(this), new DNSEntryListDialog.OnProviderSelectedListener() {
            @Override
            public void onProviderSelected(String name, IPPortPair dns1, IPPortPair dns2, IPPortPair dns1V6, IPPortPair dns2V6) {
                boolean port = PreferencesAccessor.areCustomPortsEnabled(MainActivity.this);
                if(mainFragment.settingV6){
                    mainFragment.dns1.setText(dns1V6.toString(port));
                    mainFragment.dns2.setText(dns2V6.toString(port));
                    boolean ipEnabled = PreferencesAccessor.isIPv4Enabled(MainActivity.this);
                    if(ipEnabled)PreferencesAccessor.Type.DNS1.saveDNSPair(MainActivity.this, dns1);
                    if(ipEnabled)PreferencesAccessor.Type.DNS2.saveDNSPair(MainActivity.this, dns2);
                }else{
                    mainFragment.dns1.setText(dns1.toString(port));
                    mainFragment.dns2.setText(dns2.toString(port));
                    boolean ipEnabled = PreferencesAccessor.isIPv6Enabled(MainActivity.this);
                    if(ipEnabled)PreferencesAccessor.Type.DNS1_V6.saveDNSPair(MainActivity.this, dns1V6);
                    if(ipEnabled)PreferencesAccessor.Type.DNS2_V6.saveDNSPair(MainActivity.this, dns2V6);
                }
                applyDNSServersInstant();
            }
        });
        dnsEntryListDialog.show();
        LogFactory.writeMessage(this, LOG_TAG, "Dialog is now being shown");
    }

    private void applyDNSServersInstant(){
        if(Util.isServiceRunning(MainActivity.this)){
            if(PreferencesAccessor.checkConnectivityOnStart(this)){
                if (currentFragment() instanceof MainFragment){
                    final LoadingDialog dialog = new LoadingDialog(this, R.string.checking_connectivity, R.string.dialog_connectivity_description);
                    dialog.show();
                    ((MainFragment)currentFragment()).checkDNSReachability(new MainFragment.DNSReachabilityCallback() {
                        @Override
                        public void checkFinished(@NonNull List<IPPortPair> unreachable, @NonNull List<IPPortPair> reachable) {
                            dialog.dismiss();
                            if(unreachable.size() == 0){
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        MainActivity.this.startService(DNSVpnService.getUpdateServersIntent(MainActivity.this, true, false));
                                    }
                                });
                            }else{
                                String _text = getString(R.string.no_connectivity_warning_text);
                                StringBuilder builder = new StringBuilder();
                                _text = _text.replace("[x]", unreachable.size() + reachable.size() + "");
                                _text = _text.replace("[y]", unreachable.size() + "");
                                boolean customPorts = PreferencesAccessor.areCustomPortsEnabled(MainActivity.this);
                                for(IPPortPair p: unreachable)if(p != null) builder.append("- ").append(p.formatForTextfield(customPorts)).append("\n");
                                _text = _text.replace("[servers]", builder.toString());
                                final String text = _text;
                                MainActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if(!isFinishing()) {
                                            new AlertDialog.Builder(MainActivity.this, ThemeHandler.getDialogTheme(MainActivity.this))
                                                    .setTitle(R.string.warning).setCancelable(true).setPositiveButton(R.string.start, new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    MainActivity.this.startService(DNSVpnService.getUpdateServersIntent(MainActivity.this, true, false));
                                                }
                                            }).setNegativeButton(R.string.cancel, null).setMessage(text).show();
                                        }
                                    }
                                });
                            }
                        }
                    });
                }else {
                    this.startService(DNSVpnService.getUpdateServersIntent(MainActivity.this, true, false));
                }
            }else{
                this.startService(DNSVpnService.getUpdateServersIntent(MainActivity.this, true, false));
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(!startedActivity && (PreferencesAccessor.isPinProtected(this, PreferencesAccessor.PinProtectable.APP))) finish();
    }

    private void updatePinState(Intent intent){
        if((intent.getAction() != null && intent.getAction().equals(Intent.ACTION_CHOOSER)) || (intent.getComponent() != null && intent.getComponent().getPackageName().equals("com.frostnerd.dnschanger"))){
            startedActivity = true;
        }
    }

    @Override
    public void startActivity(Intent intent) {
        updatePinState(intent);
        super.startActivity(intent);
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        updatePinState(intent);
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void startActivityFromFragment(@NonNull android.app.Fragment fragment, Intent intent, int requestCode) {
        updatePinState(intent);
        super.startActivityFromFragment(fragment, intent, requestCode);
    }

    @Override
    public void startActivityFromFragment(@NonNull android.app.Fragment fragment, Intent intent, int requestCode, @Nullable Bundle options) {
        updatePinState(intent);
        super.startActivityFromFragment(fragment, intent, requestCode, options);
    }

    @Override
    public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode) {
        updatePinState(intent);
        super.startActivityFromFragment(fragment, intent, requestCode);
    }

    @Override
    public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode, @Nullable Bundle options) {
        updatePinState(intent);
        super.startActivityFromFragment(fragment, intent, requestCode, options);
    }

    @Override
    public void importStarted(int combinedLines) {
        final LoadingDialog loadingDialog = new LoadingDialog(this, ThemeHandler.getDialogTheme(this),
                getString(R.string.importing_x_rules).replace("[x]", combinedLines + ""),
                getString(R.string.info_importing_rules_app_unusable));
        loadingDialog.setCancelable(false);
        loadingDialog.setCanceledOnTouchOutside(false);
        loadingDialog.show();
        importingRules = true;
        updateConfiguration();
        registerReceiver(importFinishedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loadingDialog.dismiss();
                unregisterReceiver(this);
                importFinishedReceiver = null;
                importingRules = false;
                updateConfiguration();
            }
        }, new IntentFilter(RuleImportService.BROADCAST_IMPORT_FINISHED));
    }
}
