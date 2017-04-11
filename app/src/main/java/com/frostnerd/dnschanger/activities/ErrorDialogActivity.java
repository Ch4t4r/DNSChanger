package com.frostnerd.dnschanger.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.icu.text.IDNA;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;

import com.frostnerd.dnschanger.BuildConfig;
import com.frostnerd.dnschanger.LogFactory;
import com.frostnerd.dnschanger.R;

/**
 * Copyright Daniel Wolf 2017
 * All rights reserved.
 *
 * Terms on usage of my code can be found here: https://git.frostnerd.com/PublicAndroidApps/DnsChanger/blob/master/README.md
 *
 * <p>
 * development@frostnerd.com
 */
public class ErrorDialogActivity extends Activity {
    private static final String LOG_TAG = "[ErrorDialogActivity]";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogFactory.writeMessage(this, LOG_TAG,"Created Activity", getIntent());
        final String crashReport = getIntent() != null ? getIntent().getStringExtra("stacktrace") : "";
        LogFactory.writeMessage(this, LOG_TAG,"Creating Dialog displaying the user that an error occurred");
        new AlertDialog.Builder(this).setTitle(getString(R.string.error) + " - " + getString(R.string.app_name)).setMessage(R.string.vpn_error_explain)
                .setCancelable(false).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                LogFactory.writeMessage(ErrorDialogActivity.this, LOG_TAG,"User choose to cancel action");
                dialog.cancel();
            }
        }).setNeutralButton(R.string.send_crash_report, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                        "mailto","support@frostnerd.com", null));
                String body = "\n\n\n\n\n\n\nSystem:\nApp version: " + BuildConfig.VERSION_CODE + " (" + BuildConfig.VERSION_NAME + ")\n"+
                        "Android: " + Build.VERSION.SDK_INT + " (" + Build.VERSION.RELEASE + " - " + Build.VERSION.CODENAME + ")\n\n\nStacktrace:\n" + crashReport;
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " - crash");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, "support@frostnerd.com");
                emailIntent.putExtra(Intent.EXTRA_TEXT, body);
                LogFactory.writeMessage(ErrorDialogActivity.this, LOG_TAG,"User choose to send Email to dev", emailIntent);
                startActivity(Intent.createChooser(emailIntent, getString(R.string.contact_developer)));
            }
        }).show();
        LogFactory.writeMessage(this, LOG_TAG,"Showing Dialog");
    }
}
