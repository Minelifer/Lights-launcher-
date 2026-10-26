package com.example.lightlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics());

        recyclerView = new RecyclerView(this);
        recyclerView.setBackgroundColor(Color.BLACK);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 5));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setClipChildren(false);
        recyclerView.setPadding(padding, padding, padding, padding);

        setContentView(recyclerView);

        loadApps();
    }

    @Override
    public void onBackPressed() {
        // Игнорируем кнопку Назад, чтобы лаунчер не закрывался
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            String myPackage = getPackageName();

            List<ResolveInfo> allResolved = new ArrayList<>();

            Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
            leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            allResolved.addAll(pm.queryIntentActivities(leanbackIntent, 0));

            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            allResolved.addAll(pm.queryIntentActivities(mainIntent, 0));

            List<AppInfo> apps = new ArrayList<>();
            Set<String> addedPackages = new HashSet<>();

            int maxIconPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 64, getResources().getDisplayMetrics());

            for (ResolveInfo info : allResolved) {
                String pkg = info.activityInfo.packageName;

                if (pkg.equals(myPackage) || addedPackages.contains(pkg)) {
                    continue;
                }
                addedPackages.add(pkg);

                String label = info.loadLabel(pm).toString();
                Drawable rawIcon = info.loadIcon(pm);
                Drawable compactIcon = scaleDownIcon(rawIcon, maxIconPx);

                apps.add(new AppInfo(label, pkg, compactIcon));
            }

            Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));

            runOnUiThread(() -> {
                recyclerView.setAdapter(new AppAdapter(apps));
                recyclerView.post(() -> {
                    if (recyclerView.getChildCount() > 0) {
                        recyclerView.getChildAt(0).requestFocus();
                    }
                });
            });
        }).start();
    }

    private Drawable scaleDownIcon(Drawable icon, int sizePx) {
        if (icon == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, sizePx, sizePx);
        icon.draw(canvas);
        return new BitmapDrawable(getResources(), bitmap);
    }
}
