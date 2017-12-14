package com.frostnerd.dnschanger.dialogs;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import com.frostnerd.dnschanger.R;
import com.frostnerd.dnschanger.services.RuleImportService;
import com.frostnerd.dnschanger.util.ThemeHandler;
import com.frostnerd.utils.networking.NetworkUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copyright Daniel Wolf 2017
 * All rights reserved.
 * Code may NOT be used without proper permission, neither in binary nor in source form.
 * All redistributions of this software in source code must retain this copyright header
 * All redistributions of this software in binary form must visibly inform users about usage of this software
 * <p>
 * development@frostnerd.com
 */
public class RuleImportProgressDialog extends AlertDialog {
    private static final Pattern DNSMASQ_PATTERN = Pattern.compile("^address=/([^/]+)/(?:([0-9.]+)|([0-9a-fA-F:]+))");
    private static final Matcher DNSMASQ_MATCHER = DNSMASQ_PATTERN.matcher(""),
    DNSMASQ_VALIDATION_MATCHER = DNSMASQ_PATTERN.matcher("");
    private static final Pattern HOSTS_PATTERN = Pattern.compile("^(?:([^#\\s]+)\\s+(((?:[0-9.[^#\\s]])+$)|(?:[0-9a-fA-F:[^#\\s]]+)))|(?:^(?:([0-9.]+)|([0-9a-fA-F:]+))\\s+([^#\\s]+))");
    private static final Matcher HOSTS_MATCHER = HOSTS_PATTERN.matcher(""),
    HOSTS_VALIDATION_MATCHER = HOSTS_PATTERN.matcher("");
    private static final Pattern DOMAINS_PATTERN = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9\\-.]+)");
    private static final Matcher DOMAINS_MATCHER = DOMAINS_PATTERN.matcher(""),
    DOMAINS_VALIDATION_MATCHER = DOMAINS_PATTERN.matcher("");
    private static final Pattern ADBLOCK_PATTERN = Pattern.compile("^\\|\\|([A-Za-z0-9][A-Za-z0-9\\-.]+)\\^");
    private static final Matcher ADBLOCK_MATCHER = ADBLOCK_PATTERN.matcher(""),
    ADBLOCK_VALIDATION_MATCHER = ADBLOCK_PATTERN.matcher("");
    private int linesCombined;

    public RuleImportProgressDialog(@NonNull Activity context, List<ImportableFile> files, int databaseConflictHandling) {
        super(context, ThemeHandler.getDialogTheme(context));
        for(ImportableFile file: files)linesCombined += file.getLines();
        setTitle(getContext().getString(R.string.importing_x_rules).replace("[x]", "" + linesCombined));
        setCancelable(false);
        setCanceledOnTouchOutside(false);
        setButton(BUTTON_NEUTRAL, context.getString(R.string.cancel), new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //asyncImport.cancel(false);
                //asyncImport = null;
                dialog.dismiss();
            }
        });
        setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                //if(asyncImport != null)asyncImport.cancel(false);
                //asyncImport = null;
            }
        });
        View content;
        setView(content = getLayoutInflater().inflate(R.layout.dialog_rule_import_progress, null, false));
        context.startService(RuleImportService.createIntent(context, linesCombined, databaseConflictHandling,
                files.toArray(new ImportableFile[files.size()])));
    }

    @Override
    public void dismiss() {
        super.dismiss();
    }

    public static int getFileLines(File f) {
        int lines = 0;
        try {
            LineNumberReader lnr = new LineNumberReader(new FileReader(f));
            lnr.skip(Long.MAX_VALUE);
            lines = lnr.getLineNumber() + 1;
            lnr.close();
        } catch (IOException ignored) {

        }
        return lines;
    }

    public static FileType tryFindFileType(File f, boolean failFast){
        try{
            HashMap<FileType, Integer> validLines = new LinkedHashMap<>();
            for(FileType type: FileType.values())validLines.put(type, 0);
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String line;
            int lines = 0, fileLines = failFast ? 0 : getFileLines(f), validLinesBuffer;
            FileType won = null, focus = null;
            List<String> lineBuffer = failFast ? new ArrayList<String>(1000) : null;
            while((line = reader.readLine()) != null && ((failFast && lines++ <= 300) || (!failFast && lines++ <= fileLines))){
                if(lines % 10000 == 0){ //We are in non-fail fast, try to focus on the best type yet
                    if(lineBuffer.size() != 0){ //We had a focus before which wasn't successful in finding the Type
                        HashMap<FileType, Integer> validLinesTemp = new HashMap<>(validLines);
                        validLinesTemp.remove(focus); //Remove the current focus from the temporary map (Otherwise it would increase its line count twice)
                        for(FileType type: validLinesTemp.keySet()){ //Update the other types
                            if(type.validateLine(line)){
                                validLinesTemp.put(type, validLinesBuffer=(validLinesTemp.get(type)+1));
                                if(validLinesBuffer >= 50){
                                    won = type;
                                    break;
                                }
                            }
                        }
                        validLines.putAll(validLinesTemp); //Add the result from the other types
                        lineBuffer.clear();
                    }
                    Map.Entry<FileType, Integer> max = null;
                    for(Map.Entry<FileType, Integer> entry: validLines.entrySet()){ //Determine the type which had the most matches this far
                        if(max == null || entry.getValue().compareTo(max.getValue()) > 0)max = entry;
                    }
                    focus = max.getKey();
                }else if(focus != null){ //Use the determined focus
                    if(focus.validateLine(line)){
                        validLines.put(focus, validLinesBuffer=(validLines.get(focus)+1));
                        if(validLinesBuffer >= 50){
                            won = focus;
                            break;
                        }
                    }
                    lineBuffer.add(line); //Safe the lines for the other types (will be queried if focus has no result)
                }else{ //Either we are in fail-fast or the focus isn't net
                    for(FileType type: validLines.keySet()){
                        if(type.validateLine(line)){
                            validLines.put(type, validLinesBuffer=(validLines.get(type)+1));
                            if(validLinesBuffer >= 50){
                                won = type;
                                break;
                            }
                        }
                    }
                }
            }
            reader.close();
            if(won == null){
                Map.Entry<FileType, Integer> max = null;
                for(Map.Entry<FileType, Integer> entry: validLines.entrySet()){
                    if(max == null || entry.getValue().compareTo(max.getValue()) > 0)max = entry;
                }
                if(max != null && ((double)max.getValue()/lines) >= 0.66)won = max.getKey();
            }
            return won;
        }catch (IOException ignored){

        }
        return null;
    }

    public enum FileType implements LineParser, Serializable {
        DNSMASQ {
            @Override
            public TemporaryDNSRule parseLine(String line) {
                if(DNSMASQ_MATCHER.reset(line).matches()){
                    String host = DNSMASQ_MATCHER.group(1);
                    String target = DNSMASQ_MATCHER.group(2);
                    if(target != null && NetworkUtil.isIP(target, false)){
                        if(target.equals("0.0.0.0"))return new TemporaryDNSRule(host);
                        else return new TemporaryDNSRule(host, target, false);
                    }else if((target = DNSMASQ_MATCHER.group(3)) != null && NetworkUtil.isIP(target, true)){
                        return new TemporaryDNSRule(host, target, true);
                    }
                }
                return null;
            }

            @Override
            public boolean validateLine(String line) {
                if(DNSMASQ_VALIDATION_MATCHER.reset(line).matches()){
                    String target = DNSMASQ_VALIDATION_MATCHER.group(2);
                    if(target != null && NetworkUtil.isIP(target, false)){
                        return true;
                    }else if((target = DNSMASQ_VALIDATION_MATCHER.group(3)) != null && NetworkUtil.isIP(target, true)){
                        return true;
                    }
                }
                return false;
            }
        }, HOST {
            @Override
            public TemporaryDNSRule parseLine(String line) {
                if(HOSTS_MATCHER.reset(line).matches()){
                    String host = HOSTS_MATCHER.group(1), target;
                    boolean ipv6 = false;
                    if(NetworkUtil.isIPv4(host) || (ipv6 = NetworkUtil.isIP(host, true))){
                        target = host;
                        host = HOSTS_MATCHER.group(2);
                    }else{
                        target = HOSTS_MATCHER.group(2);
                        ipv6 = NetworkUtil.isIP(target, true);
                    }
                    if(!ipv6 && target.equals("0.0.0.0"))return new TemporaryDNSRule(host);
                    else if(NetworkUtil.isIP(target, ipv6))return new TemporaryDNSRule(host, target, ipv6);
                }
                return null;
            }

            @Override
            public boolean validateLine(String line) {
                if(HOSTS_VALIDATION_MATCHER.reset(line).matches()){
                    String host = HOSTS_VALIDATION_MATCHER.group(1), target;
                    boolean ipv6 = false;
                    if(NetworkUtil.isIPv4(host) || (ipv6 = NetworkUtil.isIP(host, true))){
                        target = host;
                    }else{
                        target = HOSTS_VALIDATION_MATCHER.group(2);
                        ipv6 = NetworkUtil.isIP(target, true);
                    }
                    return (!ipv6 && target.equals("0.0.0.0")) || NetworkUtil.isIP(target, ipv6);
                }
                return false;
            }
        }, ADBLOCK_FILE{
            @Override
            public TemporaryDNSRule parseLine(String line) {
                if(ADBLOCK_MATCHER.reset(line).matches()){
                    String host = ADBLOCK_MATCHER.group(1);
                    return new TemporaryDNSRule(host);
                }
                return null;
            }

            @Override
            public boolean validateLine(String line) {
                return ADBLOCK_VALIDATION_MATCHER.reset(line).matches();
            }
        }, DOMAIN_LIST {
            @Override
            public TemporaryDNSRule parseLine(String line) {
                if(DOMAINS_MATCHER.reset(line).matches()){
                    String host = DOMAINS_MATCHER.group(1);
                    return new TemporaryDNSRule(host);
                }
                return null;
            }

            @Override
            public boolean validateLine(String line) {
                return DOMAINS_VALIDATION_MATCHER.reset(line).matches();
            }
        }

    }

    public interface LineParser {
        TemporaryDNSRule parseLine(String line);
        boolean validateLine(String line);
    }

    public static final class TemporaryDNSRule {
        final String host;
        String target;
        boolean ipv6, both = false;

        public TemporaryDNSRule(String host){
            this.host = host;
            both = true;
        }

        public TemporaryDNSRule(String host, String target, boolean IPv6) {
            this.host = host;
            this.target = target;
            this.ipv6 = IPv6;
        }

        public String getHost() {
            return host;
        }

        public String getTarget() {
            return target;
        }

        public boolean isBoth() {
            return both;
        }

        public boolean isIpv6() {
            return ipv6;
        }
    }

    public static class ImportableFile implements Serializable{
        private final File file;
        private final FileType fileType;
        private final int lines;

        public ImportableFile(File file, FileType fileType, int lines) {
            this.file = file;
            this.fileType = fileType;
            this.lines = lines;
        }

        public File getFile() {
            return file;
        }

        public FileType getFileType() {
            return fileType;
        }

        public int getLines() {
            return lines;
        }
    }
}
