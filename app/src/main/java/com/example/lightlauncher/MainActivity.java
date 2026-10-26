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
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int ICON_DP = 64;
    private static final int BANNER_W_DP = 96;
    private static final int BANNER_H_DP = 54;
    /** Примерная ширина плитки: по ней считаем число колонок. */
    private static final int CELL_HOME_DP = 180;
    private static final int CELL_ALL_DP = 160;
    private static final int RELOAD_DELAY_MS = 400;

    private static final int MUTED_TEXT = 0xFF9AA4B5;

    private RecyclerView recyclerView;
    private GridLayoutManager layoutManager;
    private AppAdapter adapter;
    private Favorites favorites;

    private LinearLayout menuButton;
    private TextView menuLabel;
    private TextView sectionView;
    private TextView hintView;
    private TextView clockView;
    private TextView dateView;

    private int homeColumns;
    private int allColumns;

    /** false: главный экран с избранным, true: список всех приложений. */
    private boolean showAll = false;
    private boolean firstLoad = true;
    /** Подсказку про пустое избранное показываем только после первой загрузки списка. */
    private boolean loaded = false;
    private List<AppInfo> allApps = new ArrayList<>();

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

    /** Обновляет часы раз в минуту и при смене времени или часового пояса. */
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        homeColumns = clamp((int) (widthDp / CELL_HOME_DP), 3, 6);
        allColumns = clamp((int) (widthDp / CELL_ALL_DP), 3, 8);

        favorites = new Favorites(this);
        adapter = new AppAdapter(favorites, (app, position) -> toggleFavorite(app, position));

        int side = dp(24);

        layoutManager = new GridLayoutManager(this, homeColumns);
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setClipToPadding(false);
        recyclerView.setClipChildren(false);
        recyclerView.setPadding(side, dp(8), side, side);
        recyclerView.setAdapter(adapter);

        setContentView(buildDesktop(side));
        applyTexts();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);

        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clockReceiver, filter);
        updateClock();
    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(clockReceiver);
        } catch (IllegalArgumentException ignored) {
            // уже отписаны
        }
        super.onStop();
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

    /** Кнопка Home (или повторный запуск лаунчера) возвращает на главный экран. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (showAll) {
            applyMode(false);
        }
    }

    @Override
    public void onBackPressed() {
        // Из списка всех приложений Назад ведёт на главный экран, на главном экране лаунчер не закрывается
        if (showAll) {
            applyMode(false);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    // ---------------------------------------------------------------- интерфейс

    /** Рабочий стол: сверху кнопка меню и часы, ниже избранное (или все приложения), фон с градиентом. */
    private LinearLayout buildDesktop(int side) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF141C2E, Color.BLACK}));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(side, side, side, 0);

        menuButton = buildMenuButton();
        header.addView(menuButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(this);
        header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        dateView = new TextView(this);
        dateView.setTextColor(MUTED_TEXT);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        dateView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateParams.rightMargin = dp(16);
        header.addView(dateView, dateParams);

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 36);
        clockView.setTypeface(Typeface.DEFAULT_BOLD);
        clockView.setIncludeFontPadding(false);
        header.addView(clockView);

        sectionView = new TextView(this);
        sectionView.setTextColor(MUTED_TEXT);
        sectionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        sectionView.setPadding(side, dp(20), side, 0);

        FrameLayout content = new FrameLayout(this);
        content.setClipChildren(false);
        content.addView(recyclerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hintView = new TextView(this);
        hintView.setTextColor(MUTED_TEXT);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hintView.setGravity(Gravity.CENTER);
        hintView.setPadding(dp(48), 0, dp(48), 0);
        hintView.setText("Здесь будут ваши избранные приложения.\n\n"
                + "Откройте «Все приложения», выберите нужное и удерживайте OK, "
                + "чтобы добавить его на главный экран.");
        hintView.setVisibility(View.GONE);
        content.addView(hintView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sectionView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        updateClock();
        return root;
    }

    /** Кнопка меню: переключает главный экран и список всех приложений. */
    private LinearLayout buildMenuButton() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setFocusable(true);
        button.setFocusableInTouchMode(false);
        button.setClickable(true);
        button.setPadding(dp(18), dp(10), dp(22), dp(10));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_menu);
        button.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));

        menuLabel = new TextView(this);
        menuLabel.setTextColor(Color.WHITE);
        menuLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp(10);
        button.addView(menuLabel, labelParams);

        setMenuFocus(button, false);
        button.setOnFocusChangeListener((v, hasFocus) -> setMenuFocus(v, hasFocus));
        button.setOnClickListener(v -> applyMode(!showAll));
        return button;
    }

    private void setMenuFocus(View button, boolean hasFocus) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(28));
        if (hasFocus) {
            background.setColor(0xFF3D4A66);
            background.setStroke(dp(2), Color.WHITE);
        } else {
            background.setColor(0x33FFFFFF);
        }
        button.setBackground(background);
        button.setScaleX(hasFocus ? 1.04f : 1.0f);
        button.setScaleY(hasFocus ? 1.04f : 1.0f);
    }

    private void applyTexts() {
        menuLabel.setText(showAll ? "На главный" : "Все приложения");
        sectionView.setText(showAll ? "Все приложения" : "Избранное");
    }

    private void updateClock() {
        if (clockView == null || dateView == null) return;

        Date now = new Date();
        clockView.setText(DateFormat.getTimeFormat(this).format(now));

        Locale locale = Locale.getDefault();
        String pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM");
        String date = new SimpleDateFormat(pattern, locale).format(now);
        if (!date.isEmpty()) {
            date = date.substring(0, 1).toUpperCase(locale) + date.substring(1);
        }
        dateView.setText(date);
    }

    // ------------------------------------------------------- режимы и избранное

    /** Переключает главный экран и список всех приложений. */
    private void applyMode(boolean all) {
        showAll = all;
        layoutManager.setSpanCount(all ? allColumns : homeColumns);
        applyTexts();
        refreshList();
        focusFirstWhenReady();
    }

    /** Заполняет список по текущему режиму: либо избранное плитками, либо все приложения. */
    private void refreshList() {
        List<AppInfo> list;
        if (showAll) {
            list = allApps;
        } else {
            Map<String, AppInfo> byPackage = new HashMap<>();
            for (AppInfo app : allApps) {
                byPackage.put(app.packageName, app);
            }
            list = new ArrayList<>();
            for (String pkg : favorites.list()) {
                AppInfo app = byPackage.get(pkg);
                if (app != null) {
                    list.add(app);
                }
            }
        }
        adapter.setApps(list, !showAll);
        hintView.setVisibility(loaded && !showAll && list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toggleFavorite(AppInfo app, int position) {
        boolean added = favorites.toggle(app.packageName);
        Toast.makeText(this,
                (added ? "Добавлено в избранное: " : "Убрано из избранного: ") + app.label,
                Toast.LENGTH_SHORT).show();

        if (showAll) {
            if (position >= 0) {
                adapter.notifyItemChanged(position);
            }
        } else {
            refreshList();
            focusFirstWhenReady();
        }
    }

    /** После перерисовки списка отдаёт фокус первой плитке, а если список пуст, кнопке меню. */
    private void focusFirstWhenReady() {
        recyclerView.requestLayout();
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        ViewTreeObserver observer = recyclerView.getViewTreeObserver();
                        if (observer.isAlive()) {
                            observer.removeOnGlobalLayoutListener(this);
                        }
                        View first = recyclerView.getChildCount() > 0
                                ? recyclerView.getChildAt(0) : null;
                        if (first != null) {
                            first.requestFocus();
                        } else {
                            menuButton.requestFocus();
                        }
                    }
                });
    }

    // -------------------------------------------------------- загрузка списка

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
                allApps = apps;
                loaded = true;
                refreshList();
                if (firstLoad) {
                    firstLoad = false;
                    focusFirstWhenReady();
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
