package com.frostnerd.dnschanger.dialogs;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.annotation.StyleRes;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.frostnerd.dnschanger.API.API;
import com.frostnerd.dnschanger.API.DNSCreationDialog;
import com.frostnerd.dnschanger.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class DefaultDNSDialog extends AlertDialog {
    View view;
    private static final List<DNSEntry> entries = new ArrayList<>();
    private OnProviderSelectedListener listener;
    static {
        entries.add(new DNSEntry("", "", "", "", ""));
        entries.add(new DNSEntry("Google", "8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844"));
        entries.add(new DNSEntry("OpenDNS", "208.67.222.222", "208.67.220.220", "2620:0:ccc::2", "2620:0:ccd::2"));
        entries.add(new DNSEntry("Level3", "209.244.0.3", "209.244.0.4", "", ""));
        entries.add(new DNSEntry("FreeDNS", "37.235.1.174", "37.235.1.177", "", ""));
        entries.add(new DNSEntry("Yandex", "77.88.8.8", "77.88.8.1", "2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff"));
        entries.add(new DNSEntry("Verisign", "64.6.64.6", "64.6.65.6", "2620:74:1b::1:1", "2620:74:1c::2:2"));
        entries.add(new DNSEntry("Alternate", "198.101.242.72", "23.253.163.53", "", ""));
        entries.add(new DNSEntry("Norton Connectsafe - Security", "199.85.126.10", "199.85.127.10", "", ""));
        entries.add(new DNSEntry("Norton Connectsafe - Security + Pornography", "199.85.126.20", "199.85.127.20", "", ""));
        entries.add(new DNSEntry("Norton Connectsafe - Security + Portnography + Other", "199.85.126.30", "199.85.127.30", "", ""));
        Collections.sort(entries);
    }
    private List<DNSEntry> localEntries = new ArrayList<>();
    private DefaultDNSAdapter adapter;

    public DefaultDNSDialog(@NonNull final Context context, final int theme, @NonNull final OnProviderSelectedListener listener) {
        super(context, theme);
        for(DNSEntry entry: entries)localEntries.add(entry);
        loadEntriesFromDatabase();
        this.listener = listener;
        view = LayoutInflater.from(context).inflate(R.layout.dialog_default_dns, null, false);
        setView(view);
        final ListView list = (ListView) view.findViewById(R.id.defaultDnsDialogList);
        list.setAdapter(adapter = new DefaultDNSAdapter());
        list.setDividerHeight(0);
        setButton(BUTTON_NEGATIVE, context.getString(R.string.cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        setButton(BUTTON_POSITIVE, context.getString(R.string.add), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        setTitle(R.string.default_dns_title);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dismiss();
                DNSEntry entry = (DNSEntry)view.getTag();
                listener.onProviderSelected(entry.getName(), entry.dns1, entry.dns2, entry.dns1V6, entry.dns2V6);
            }
        });

        setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                getButton(BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new DNSCreationDialog(context, new DNSCreationDialog.OnCreationFinishedListener() {
                            @Override
                            public void onCreationFinished(String name, String dns1, String dns2, String dns1V6, String dns2V6) {
                                localEntries.add(saveEntryToDatabase(new DNSEntry(name, dns1, dns2, dns1V6, dns2V6)));
                                adapter.notifyDataSetChanged();
                            }
                        }).show();
                    }
                });
            }
        });
    }

    private DNSEntry saveEntryToDatabase(DNSEntry entry){
        SQLiteDatabase database = API.getDatabase(getContext());
        ContentValues values = new ContentValues(5);
        values.put("Name", entry.getName());
        values.put("dns1", entry.getDns1());
        values.put("dns2", entry.getDns2());
        values.put("dns1v6", entry.getDns1V6());
        values.put("dns2v6", entry.getDns2V6());
        database.insert("DNSEntries", null,values);
        return entry;
    }

    private void loadEntriesFromDatabase(){
        SQLiteDatabase database = API.getDatabase(getContext());
        Cursor cursor = database.rawQuery("SELECT * FROM DNSEntries", new String[]{});
        if(cursor.moveToFirst()){
            do{
                localEntries.add(new DNSEntry(cursor.getString(cursor.getColumnIndex("Name")), cursor.getString(cursor.getColumnIndex("dns1")), cursor.getString(cursor.getColumnIndex("dns2")),
                        cursor.getString(cursor.getColumnIndex("dns1v6")), cursor.getString(cursor.getColumnIndex("dns2v6"))));
            }while(cursor.moveToNext());
        }
        cursor.close();
    }

    public static interface OnProviderSelectedListener{
        public void onProviderSelected(String name, String dns1, String dns2, String dns1V6, String dns2V6);
    }

    private class DefaultDNSAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return localEntries.size();
        }

        @Override
        public Object getItem(int position) {
            return localEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = getLayoutInflater().inflate(R.layout.item_default_dns, parent, false);
            ((TextView) v.findViewById(R.id.text)).setText(localEntries.get(position).getName());
            v.setTag(getItem(position));
            return v;
        }
    }

    private static class DNSEntry implements Comparable<DNSEntry>{
        private String name, dns1,dns2,dns1V6,dns2V6;

        public DNSEntry(String name, String dns1,String dns2, String dns1V6, String dns2V6){
            this.name = name;
            this.dns1 =  dns1;
            this.dns2 = dns2;
            this.dns1V6 = dns1V6;
            this.dns2V6 = dns2V6;
        }

        public String getName() {
            return name;
        }

        public String getDns1() {
            return dns1;
        }

        public String getDns2() {
            return dns2;
        }

        public String getDns1V6() {
            return dns1V6;
        }

        public String getDns2V6() {
            return dns2V6;
        }

        @Override
        public int compareTo(@NonNull DNSEntry o) {
            return name.compareTo(o.name);
        }
    }
}
