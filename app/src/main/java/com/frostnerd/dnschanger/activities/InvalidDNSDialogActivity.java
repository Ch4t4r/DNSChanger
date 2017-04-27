package com.frostnerd.dnschanger.activities;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;

import com.frostnerd.dnschanger.R;

/**
 * Copyright Daniel Wolf 2017
 * All rights reserved.
 * <p>
 * development@frostnerd.com
 */
public class InvalidDNSDialogActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new AlertDialog.Builder(this).setTitle(R.string.information).setMessage(R.string.invalid_dns_error_text).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        }).setCancelable(true).setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        }).show();
    }
}
