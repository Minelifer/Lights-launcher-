package com.example.lightlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private final List<AppInfo> apps;

    public AppAdapter(List<AppInfo> apps) {
        this.apps = apps;
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        int pad = dpToPx(context, 12);
        int iconSize = dpToPx(context, 64);
        int itemHeight = dpToPx(context, 130);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, itemHeight));
        layout.setFocusable(true);
        layout.setFocusableInTouchMode(false);
        layout.setClickable(true);
        layout.setPadding(pad, pad, pad, pad);

        ImageView icon = new ImageView(context);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

        TextView label = new TextView(context);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dpToPx(context, 8);
        label.setLayoutParams(labelParams);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setGravity(Gravity.CENTER);

        layout.addView(icon);
        layout.addView(label);

        return new ViewHolder(layout, icon, label);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = apps.get(position);
        holder.label.setText(app.label);
        holder.icon.setImageDrawable(app.icon);
        holder.boundApp = app;
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        AppInfo boundApp;

        ViewHolder(@NonNull View itemView, ImageView icon, TextView label) {
            super(itemView);
            this.icon = icon;
            this.label = label;

            itemView.setOnClickListener(v -> {
                if (boundApp == null) return;
                Context context = v.getContext();
                PackageManager pm = context.getPackageManager();

                Intent intent = pm.getLeanbackLaunchIntentForPackage(boundApp.packageName);
                if (intent == null) {
                    intent = pm.getLaunchIntentForPackage(boundApp.packageName);
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            });

            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.setBackgroundColor(0xFF333333);
                    v.setScaleX(1.06f);
                    v.setScaleY(1.06f);
                } else {
                    v.setBackgroundColor(Color.TRANSPARENT);
                    v.setScaleX(1.0f);
                    v.setScaleY(1.0f);
                }
            });
        }
    }
}
