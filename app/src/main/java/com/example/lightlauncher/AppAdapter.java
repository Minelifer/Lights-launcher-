package com.example.lightlauncher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private static final int FOCUS_COLOR = 0xFF333333;

    private final List<AppInfo> apps = new ArrayList<>();

    public AppAdapter(List<AppInfo> initial) {
        apps.addAll(initial);
    }

    /** Заменяет список приложений. Вызывать только из UI-потока. */
    public void setApps(List<AppInfo> newApps) {
        apps.clear();
        apps.addAll(newApps);
        notifyDataSetChanged();
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

        // Один общий «слот» под иконку (64x64) и баннер (96x54), FIT_CENTER сохраняет пропорции
        ImageView icon = new ImageView(context);
        icon.setLayoutParams(new LinearLayout.LayoutParams(
                dpToPx(context, 96), dpToPx(context, 64)));
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

            final Context context = itemView.getContext();

            final GradientDrawable focusBackground = new GradientDrawable();
            focusBackground.setColor(FOCUS_COLOR);
            focusBackground.setCornerRadius(dpToPx(context, 8));
            final float focusLift = dpToPx(context, 8);

            itemView.setOnClickListener(v -> launch(v.getContext(), boundApp));

            itemView.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    v.setBackground(focusBackground);
                    v.setScaleX(1.06f);
                    v.setScaleY(1.06f);
                    // Поднимаем выбранную плитку над соседними, чтобы они её не перекрывали
                    v.setTranslationZ(focusLift);
                } else {
                    v.setBackground(null);
                    v.setScaleX(1.0f);
                    v.setScaleY(1.0f);
                    v.setTranslationZ(0f);
                }
            });
        }

        private static void launch(Context context, AppInfo app) {
            if (app == null) return;

            try {
                Intent intent;
                if (app.settings) {
                    intent = new Intent(Settings.ACTION_SETTINGS);
                } else {
                    PackageManager pm = context.getPackageManager();
                    intent = pm.getLeanbackLaunchIntentForPackage(app.packageName);
                    if (intent == null) {
                        intent = pm.getLaunchIntentForPackage(app.packageName);
                    }
                }

                if (intent == null) {
                    Toast.makeText(context, "Не удалось открыть: " + app.label, Toast.LENGTH_SHORT).show();
                    return;
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(context, "Не удалось открыть: " + app.label, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
