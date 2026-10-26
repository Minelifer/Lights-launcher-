package com.example.lightlauncher;

import android.graphics.drawable.Drawable;

public class AppInfo {
    public final String label;
    public final String packageName;
    public final Drawable icon;
    /** true, если вместо иконки используется широкий баннер (16:9). */
    public final boolean banner;
    /** true для служебной плитки «Настройки». */
    public final boolean settings;

    public AppInfo(String label, String packageName, Drawable icon, boolean banner, boolean settings) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
        this.banner = banner;
        this.settings = settings;
    }
}
