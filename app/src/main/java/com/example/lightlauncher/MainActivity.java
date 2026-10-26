package com.example.lightlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int ICON_DP = 64;
    private static final int BANNER_W_DP = 96;
    private static final int BANNER_H_DP = 54;
    /** Примерная ширина одной плитки, по ней считаем число колонок. */
    private static final int CELL_DP = 160;
    private static final int RELOAD_DELAY_MS = 400;

    private RecyclerView recyclerView;
    private AppAdapter adapter;
    private boolean firstLoad = true;

    /** Номер последнего запроса: результаты устаревших загрузок отбрасываются. */
    private volatile int loadGeneration = 0;

    private final Runnable reloadRunnable = this::loadApps;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // При установке и обновлении приходит пачка событий, поэтому откладываем перезагрузку
            recyclerView.removeCallbacks(reloadRunnable);
            recyclerView.postDelayed(reloadRunnable, RELOAD_DELAY_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int padding = dp(24);
        int columns = (int) (dm.widthPixels / dm.density / CELL_DP);
        columns = Math.max(3, Math.min(8, columns));

        adapter = new AppAdapter(new ArrayList<>());

        recyclerView = new RecyclerView(this);
        recyclerView.setBackgroundColor(Color.BLACK);
        recyclerView.setLayoutManager(new GridLayoutManager(this, columns));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setClipChildren(false);
        recyclerView.setPadding(padding, padding, padding, padding);
        recyclerView.setAdapter(adapter);

        setContentView(recyclerView);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        loadApps();
    }

    @Override
    protected void onDestroy() {
        recyclerView.removeCallbacks(reloadRunnable);
        try {
            unregisterReceiver(packageReceiver);
        } catch (IllegalArgumentException ignored) {
            // уже отписаны
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Игнорируем кнопку Назад, чтобы лаунчер не закрывался
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void loadApps() {
        final int generation = ++loadGeneration;

        new Thread(() -> {
            PackageManager pm = getPackageManager();
            String myPackage = getPackageName();

            List<ResolveInfo> leanback = pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
            List<ResolveInfo> regular = pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);

            List<AppInfo> apps = new ArrayList<>();
            Set<String> addedPackages = new HashSet<>();

            // Сначала ТВ-активности, чтобы для приложений с баннером взять именно его
            addAll(apps, addedPackages, leanback, pm, myPackage, true);
            addAll(apps, addedPackages, regular, pm, myPackage, false);

            Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));

            AppInfo settingsTile = buildSettingsTile(pm, addedPackages);
            if (settingsTile != null) {
                apps.add(settingsTile);
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != loadGeneration) {
                    return;
                }
                adapter.setApps(apps);
                if (firstLoad) {
                    firstLoad = false;
                    recyclerView.post(() -> {
                        if (recyclerView.getChildCount() > 0) {
                            recyclerView.getChildAt(0).requestFocus();
                        }
                    });
                }
            });
        }, "load-apps").start();
    }

    private void addAll(List<AppInfo> apps, Set<String> addedPackages, List<ResolveInfo> resolved,
                        PackageManager pm, String myPackage, boolean tv) {
        for (ResolveInfo info : resolved) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(myPackage) || !addedPackages.add(pkg)) {
                continue;
            }

            String label = String.valueOf(info.loadLabel(pm));

            Drawable banner = null;
            if (tv) {
                banner = info.activityInfo.loadBanner(pm);
                if (banner == null && info.activityInfo.applicationInfo != null) {
                    banner = info.activityInfo.applicationInfo.loadBanner(pm);
                }
            }
            AppInfo app;
            if (banner != null) {
                app = new AppInfo(label, pkg,
                        scaleDown(banner, dp(BANNER_W_DP), dp(BANNER_H_DP)), true, false);
            } else {
                int size = dp(ICON_DP);
                app = new AppInfo(label, pkg, scaleDown(info.loadIcon(pm), size, size), false, false);
            }
            apps.add(app);
        }
    }

    /** Плитка «Настройки», если системные настройки не попали в список сами (на ряде ТВ их там нет). */
    private AppInfo buildSettingsTile(PackageManager pm, Set<String> addedPackages) {
        ResolveInfo info = pm.resolveActivity(new Intent(Settings.ACTION_SETTINGS), 0);
        if (info == null || info.activityInfo == null) {
            return null;
        }
        if (addedPackages.contains(info.activityInfo.packageName)) {
            return null;
        }
        int size = dp(ICON_DP);
        return new AppInfo(String.valueOf(info.loadLabel(pm)), info.activityInfo.packageName,
                scaleDown(info.loadIcon(pm), size, size), false, true);
    }

    /** Рисует drawable в bitmap нужного размера, сохраняя пропорции, чтобы не тянуть память и не искажать картинку. */
    private Drawable scaleDown(Drawable drawable, int boxW, int boxH) {
        if (drawable == null) return null;

        int srcW = drawable.getIntrinsicWidth();
        int srcH = drawable.getIntrinsicHeight();
        int w = boxW;
        int h = boxH;
        if (srcW > 0 && srcH > 0) {
            float scale = Math.min((float) boxW / srcW, (float) boxH / srcH);
            w = Math.max(1, Math.round(srcW * scale));
            h = Math.max(1, Math.round(srcH * scale));
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, w, h);
        drawable.draw(canvas);
        return new BitmapDrawable(getResources(), bitmap);
    }
}
