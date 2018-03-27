package com.frostnerd.dnschanger.adapters;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.database.DatabaseHelper;
import com.frostnerd.dnschanger.database.entities.DNSRule;
import com.frostnerd.dnschanger.dialogs.NewRuleDialog;
import com.frostnerd.dnschanger.util.RuleImport;
import com.frostnerd.dnschanger.util.Util;
import com.frostnerd.utils.adapters.DatabaseAdapter;
import com.frostnerd.utils.database.orm.parser.columns.Column;
import com.frostnerd.utils.database.orm.statementoptions.queryoptions.WhereCondition;


/**
 * Copyright Daniel Wolf 2017
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */
public class RuleAdapter extends DatabaseAdapter<DNSRule, RuleAdapter.ViewHolder>{
    private LayoutInflater layoutInflater;
    private static Column<DNSRule> ipv6Column;
    private static Column<DNSRule> hostColumn;
    private static Column<DNSRule> targetColumn;
    private static Column<DNSRule> wildcardColumn;

    public <T extends Activity &RuleImport.ImportStartedListener> RuleAdapter(final T context, DatabaseHelper databaseHelper, final TextView rowCount, ProgressBar updateProgress){
        super(databaseHelper, 10000);
        this.layoutInflater = LayoutInflater.from(context);
        setOnRowLoaded(new OnRowLoaded<DNSRule, ViewHolder>() {
            @Override
            public void bindRow(ViewHolder view, final DNSRule entity) {
                view.host.setText(entity.getHost());
                view.target.setText(entity.getTarget());
                view.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        new NewRuleDialog(context, new NewRuleDialog.CreationListener() {
                            @Override
                            public void creationFinished(@NonNull String host, @Nullable String target, @Nullable String targetV6, boolean ipv6, boolean wildcard, boolean editingMode) {
                                if (target != null)
                                    DatabaseHelper.getInstance(context).editDNSRule(host, ipv6, target);
                                else {
                                    DatabaseHelper.getInstance(context).deleteDNSRule(host, ipv6);
                                }
                                reloadData();
                            }
                        }, entity.getHost(), entity.getTarget(), entity.isWildcard(), entity.isIpv6()).show();
                        return true;
                    }
                });
            }
        });
        setReloadCallback(new Runnable() {
            @Override
            public void run() {
                rowCount.setText(context.getString(R.string.x_entries).replace("[x]", getItemCount() + ""));
            }
        });
        setProgressView(updateProgress);
        setUpdateDataOnConfigChange(false);
        filter(ArgumentLessFilter.SHOW_NORMAL);
        filter(ArgumentLessFilter.SHOW_WILDCARD);
        filter(ArgumentLessFilter.SHOW_IPV6);
        filter(ArgumentLessFilter.SHOW_IPV4);
        ipv6Column = databaseHelper.findColumn(DNSRule.class, "ipv6");
        hostColumn = databaseHelper.findColumn(DNSRule.class, "host");
        targetColumn = databaseHelper.findColumn(DNSRule.class, "target");
        wildcardColumn = databaseHelper.findColumn(DNSRule.class, "wildcard");
        setUpdateDataOnConfigChange(true);
        reloadData();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(layoutInflater.inflate(R.layout.row_rule, parent, false));
    }

    class ViewHolder extends RecyclerView.ViewHolder{
        TextView host, target;

        public ViewHolder(View itemView) {
            super(itemView);
            host = itemView.findViewById(R.id.text);
            target = itemView.findViewById(R.id.text3);
        }
    }

    public enum ArgumentLessFilter implements DatabaseAdapter.ArgumentLessFilter{
        SHOW_IPV6 {
            @Override
            public WhereCondition getCondition() {
                return WhereCondition.equal(ipv6Column, "1");
            }

            @Override
            public boolean isResourceIntensive() {
                return false;
            }

            @Override
            public DatabaseAdapter.Filter[] exclusiveWith() {
                return new DatabaseAdapter.Filter[]{SHOW_IPV4};
            }
        }, HIDE_LOCAL {
            @Override
            public WhereCondition getCondition() {
                return WhereCondition.equal(targetColumn, "127.0.0.1").not()
                        .and(WhereCondition.equal(targetColumn, "::1").not());
            }

            @Override
            public boolean isResourceIntensive() {
                return false;
            }

            @Override
            public DatabaseAdapter.Filter[] exclusiveWith() {
                return new DatabaseAdapter.Filter[0];
            }
        }, SHOW_WILDCARD {
            @Override
            public WhereCondition getCondition() {
                return WhereCondition.equal(wildcardColumn, "1");
            }

            @Override
            public boolean isResourceIntensive() {
                return false;
            }

            @Override
            public DatabaseAdapter.Filter[] exclusiveWith() {
                return new DatabaseAdapter.Filter[]{SHOW_NORMAL};
            }
        }, SHOW_IPV4 {
            @Override
            public WhereCondition getCondition() {
                return WhereCondition.equal(ipv6Column, "0");
            }

            @Override
            public boolean isResourceIntensive() {
                return false;
            }

            @Override
            public DatabaseAdapter.Filter[] exclusiveWith() {
                return new DatabaseAdapter.Filter[]{SHOW_IPV4};
            }
        }, SHOW_NORMAL{
            @Override
            public WhereCondition getCondition() {
                return WhereCondition.equal(wildcardColumn, "1").not();
            }

            @Override
            public boolean isResourceIntensive() {
                return false;
            }

            @Override
            public DatabaseAdapter.Filter[] exclusiveWith() {
                return new DatabaseAdapter.Filter[]{SHOW_WILDCARD};
            }
        }
    }

    public enum ArgumentBasedFilter implements ArgumentFilter{
        TARGET {
            @Override
            public WhereCondition getCondition(String argument) {
                return WhereCondition.like(targetColumn, "%" + argument + "%");
            }

            @Override
            public boolean isResourceIntensive() {
                return true;
            }

            @Override
            public DatabaseAdapter.Filter[] exclusiveWith() {
                return new DatabaseAdapter.Filter[0];
            }
        }, HOST_SEARCH{
            @Override
            public WhereCondition getCondition(String argument) {
                return WhereCondition.like(hostColumn, "%" + argument + "%");
            }

            @Override
            public boolean isResourceIntensive() {
                return true;
            }

            @Override
            public DatabaseAdapter.Filter[] exclusiveWith() {
                return new DatabaseAdapter.Filter[0];
            }
        }
    }
}
