package com.frostnerd.dnschanger.util;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;

import com.frostnerd.dnschanger.R;

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

public class ThemeHandler {

    public static void updateAppTheme(Context context, int theme) {
        Preferences.getInstance(context).put( "apptheme", theme);
    }

    public static void updateDialogTheme(Context context, int theme) {
        Preferences.getInstance(context).put( "dialogtheme", theme);
    }

    public static int getDialogTheme(Context context) {
        switch(Preferences.getInstance(context).getInteger("dialogtheme", 2)) {
            case 1: return R.style.DialogTheme;
            case 2: return R.style.DialogTheme_Mono;
            case 3: return R.style.DialogTheme_Dark;
            case 4: return R.style.DialogTheme_True_Black;
        }
        return Preferences.getInstance(context).getInteger("dialogtheme", R.style.DialogTheme_Mono);
    }

    public static int getAppTheme(Context context) {
        switch(Preferences.getInstance(context).getInteger("apptheme", 2)) {
            case 1: return R.style.AppTheme;
            case 2: return R.style.AppTheme_Mono;
            case 3: return R.style.AppTheme_Dark;
            case 4: return R.style.AppTheme_True_Black;
        }
        return Preferences.getInstance(context).getInteger("apptheme", R.style.AppTheme_Mono);
    }

    public static int getPreferenceTheme(Context context){
        switch(getAppTheme(context)){
            case 2: return R.style.PreferenceTheme_Mono;
            case 3: return R.style.PreferenceTheme_Dark;
            case 4: return R.style.PreferenceTheme_True_Black;
            default: return R.style.PreferenceTheme;
        }
    }

    @ColorInt
    public static int getColor(Context context, @AttrRes int attribute, @ColorInt int defaultValue) {
        TypedArray ta = context.obtainStyledAttributes(getAppTheme(context), new int[]{attribute});
        @ColorInt int color = ta.getColor(0, defaultValue);
        ta.recycle();
        return color;
    }

    public static int resolveThemeAttribute(Resources.Theme theme, @AttrRes int attribute){
        TypedValue value = new TypedValue();
        theme.resolveAttribute(attribute, value, true);
        return value.data;
    }

    @ColorInt
    public static int getSelectedItemColor(Context context){
        return getColor(context, R.attr.inputElementColor, -1);
    }

    public static Drawable getDrawableFromTheme(Context context, @AttrRes int attribute){
        TypedArray ta = context.obtainStyledAttributes(getAppTheme(context), new int[]{attribute});
        Drawable drawable = ta.getDrawable(0);
        ta.recycle();
        return drawable;
    }
}
