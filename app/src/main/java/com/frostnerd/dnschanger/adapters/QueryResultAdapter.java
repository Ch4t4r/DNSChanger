package com.frostnerd.dnschanger.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostnerd.dnschanger.R;
import com.frostnerd.utils.general.DesignUtil;

import org.xbill.DNS.DClass;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Copyright Daniel Wolf 2017
 * All rights reserved.
 * <p>
 * development@frostnerd.com
 */
public class QueryResultAdapter extends RecyclerView.Adapter<QueryResultAdapter.ViewHolder> {
    private RRset[] answer;
    private Context context;
    private LayoutInflater layoutInflater;
    private final List<Entry> entryList = new ArrayList<>();

    public QueryResultAdapter(Context context, RRset[] answer, RRset[] authority, RRset[] additional){
        this.context = context;
        this.layoutInflater = LayoutInflater.from(context);
        this.answer = answer;
        populateEntryList();
    }

    private void populateEntryList(){
        for(RRset rset: answer){
            for(Iterator<Record> it = rset.rrs(); it.hasNext();){
                entryList.add(new Entry(rset, it.next()));
            }
        }
    }

    public void cleanup(){
        context = null;
        layoutInflater = null;
        entryList.clear();
        answer = null;
    }

    @NonNull
    @Override
    public QueryResultAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(entryList, context, (LinearLayout)layoutInflater.inflate(R.layout.row_dns_query, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull QueryResultAdapter.ViewHolder holder, int position) {
        LinearLayout layout = (LinearLayout) holder.itemView;
        int titleText;
        if(position == 0)titleText = R.string.query_title_name;
        else if(position == 1)titleText = R.string.query_title_ttl;
        else if(position == 2)titleText = R.string.query_title_dclass;
        else if(position == 3)titleText = R.string.query_title_type;
        else titleText = R.string.query_title_answer;
        int padding = (int)DesignUtil.dpToPixels(8, context), paddingLeft = position == 0 ? 0 : padding,
                paddingRight = position == getItemCount()-1 ? 0 : padding;
        TextView textView = (TextView)layout.getChildAt(0);

        textView.setText(titleText);
        textView.setTextColor(Color.parseColor("#FFFFFF"));
        textView.setPadding(paddingLeft, 0, paddingRight, 0);
        textView.setTypeface(null, Typeface.BOLD);

        for(int i = 1; i < layout.getChildCount(); i++){
            textView =  ((TextView)layout.getChildAt(i));
            textView.setText(getText(position, i));
            textView.setPadding(paddingLeft, padding, paddingRight, 0);
        }
    }

    private String getText(int position, int index){
        if(position == 0)return entryList.get(index-1).rset.getName().toString();
        else if(position == 1)return entryList.get(index-1).rset.getTTL() + "";
        else if(position == 2)return DClass.string(entryList.get(index-1).rset.getDClass());
        else if(position == 3)return Type.string(entryList.get(index-1).rset.getType());
        else return entryList.get(index-1).record.rdataToString();
    }

    @Override
    public int getItemCount() {
        return 5;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private ViewHolder(List<Entry> entryList, Context context, LinearLayout itemView) {
            super(itemView);
            TextView text;
            for(int i = 0; i <= entryList.size(); i++){
                text = new TextView(context);
                itemView.addView(text);
            }
        }
    }

    private static class Entry{
        private final RRset rset;
        private final Record record;

        private Entry(RRset rset, Record record) {
            this.rset = rset;
            this.record = record;
        }
    }
}
