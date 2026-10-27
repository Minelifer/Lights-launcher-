package com.example.lightlauncher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Показывает приложения в двух видах: плитки формата 16:9 (избранное на главном экране)
 * и обычный список (все приложения). Баннер ТВ-приложения заполняет плитку целиком,
 * иконка ложится по центру цветной плитки.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    public interface Listener {
        /** Долгое нажатие (удержание OK на пульте): добавить в избранное или убрать. */
        void onLongClick(AppInfo app, int position);
    }

    private static final int TYPE_PLAIN = 0;
    private static final int TYPE_TILE_ICON = 1;
    private static final int TYPE_TILE_BANNER = 2;

    private static final int FOCUS_COLOR = 0xFF333333;
    private static final int FAVORITE_LABEL_COLOR = 0xFFFFD54A;

    /** Цвета плиток: цвет выбирается по имени пакета, поэтому у приложения он всегда один и тот же. */
    private static final int[] TILE_COLORS = {
            0xFF5E35B1, 0xFF1E88E5, 0xFFD32F2F, 0xFFC2185B,
            0xFF2E7D32, 0xFFEF6C00, 0xFF00838F, 0xFF546E7A
    };

    private final List<AppInfo> apps = new ArrayList<>();
    private final Favorites favorites;
    private final Listener listener;
    private boolean tiles;

    public AppAdapter(Favorites favorites, Listener listener) {
        this.favorites = favorites;
        this.listener = listener;
    }

    /** Заменяет список приложений. Вызывать только из UI-потока. */
    public void setApps(List<AppInfo> newApps, boolean tiles) {
        this.tiles = tiles;
        apps.clear();
        apps.addAll(newApps);
        notifyDataSetChanged();
    }

    private static int dpToPx(Context context, int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    private static int tileColor(String packageName) {
        int index = (packageName.hashCode() & 0x7fffffff) % TILE_COLORS.length;
        return TILE_COLORS[index];
    }

    private static int lighten(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return Color.rgb(r + (255 - r) / 5, g + (255 - g) / 5, b + (255 - b) / 5);
    }

    /** Высота плитки: формат 16:9 от ширины ячейки, но не меньше, чем нужно для иконки и подписи. */
    private static int tileHeight(Context context, ViewGroup parent) {
        int fallback = dpToPx(context, 124);
        if (!(parent instanceof RecyclerView)) return fallback;

        RecyclerView.LayoutManager manager = ((RecyclerView) parent).getLayoutManager();
        int span = manager instanceof GridLayoutManager
                ? ((GridLayoutManager) manager).getSpanCount() : 1;
        int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (width <= 0 || span <= 0) return fallback;

        return Math.max(dpToPx(context, 110), width / span * 9 / 16);
    }

    @Override
    public int getItemViewType(int position) {
        if (!tiles) return TYPE_PLAIN;
        return apps.get(position).banner ? TYPE_TILE_BANNER : TYPE_TILE_ICON;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        boolean tile = viewType != TYPE_PLAIN;
        boolean banner = viewType == TYPE_TILE_BANNER;

        int itemHeight = tile ? tileHeight(context, parent) : dpToPx(context, 130);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, itemHeight));
        layout.setFocusable(true);
        layout.setFocusableInTouchMode(false);
        layout.setClickable(true);

        ImageView icon = new ImageView(context);
        TextView label = new TextView(context);

        if (banner) {
            // Баннер на всю плитку со скруглёнными углами, подпись не нужна: название есть на самом баннере
            int gap = dpToPx(context, 7);
            layout.setPadding(gap, gap, gap, gap);

            icon.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            final float radius = dpToPx(context, 8);
            icon.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            icon.setClipToOutline(true);

            layout.addView(icon);
        } else {
            int padH = dpToPx(context, 12);
            int padV = dpToPx(context, tile ? 8 : 12);
            layout.setPadding(padH, padV, padH, padV);

            // Слот под иконку 96x64, FIT_CENTER сохраняет пропорции
            icon.setLayoutParams(new LinearLayout.LayoutParams(
                    dpToPx(context, 96), dpToPx(context, 64)));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

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
        }

        return new ViewHolder(layout, icon, label, tile, banner, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = apps.get(position);
        holder.boundApp = app;
        holder.label.setText(app.label);
        holder.icon.setImageDrawable(app.icon);
        holder.itemView.setContentDescription(app.label);

        Context context = holder.itemView.getContext();
        if (holder.banner) {
            // У баннерной плитки без фокуса фона нет, на фокусе вокруг баннера белая рамка
            GradientDrawable frame = new GradientDrawable();
            frame.setColor(Color.TRANSPARENT);
            frame.setCornerRadius(dpToPx(context, 12));
            frame.setStroke(dpToPx(context, 3), Color.WHITE);

            holder.normalBackground = null;
            holder.focusBackground = new InsetDrawable(frame, dpToPx(context, 3));
        } else if (holder.tile) {
            int color = tileColor(app.packageName);
            int gap = dpToPx(context, 5);

            GradientDrawable normal = new GradientDrawable();
            normal.setColor(color);
            normal.setCornerRadius(dpToPx(context, 10));

            GradientDrawable focused = new GradientDrawable();
            focused.setColor(lighten(color));
            focused.setCornerRadius(dpToPx(context, 10));
            focused.setStroke(dpToPx(context, 3), Color.WHITE);

            holder.normalBackground = new InsetDrawable(normal, gap);
            holder.focusBackground = new InsetDrawable(focused, gap);
            holder.label.setTextColor(Color.WHITE);
        } else {
            GradientDrawable focused = new GradientDrawable();
            focused.setColor(FOCUS_COLOR);
            focused.setCornerRadius(dpToPx(context, 8));

            holder.normalBackground = null;
            holder.focusBackground = focused;
            // В списке всех приложений избранные подсвечены цветом названия
            holder.label.setTextColor(
                    favorites.contains(app.packageName) ? FAVORITE_LABEL_COLOR : Color.WHITE);
        }
        holder.applyFocusState(holder.itemView.hasFocus());
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;
        final boolean tile;
        final boolean banner;
        AppInfo boundApp;
        Drawable normalBackground;
        Drawable focusBackground;

        ViewHolder(@NonNull View itemView, ImageView icon, TextView label,
                   boolean tile, boolean banner, Listener listener) {
            super(itemView);
            this.icon = icon;
            this.label = label;
            this.tile = tile;
            this.banner = banner;

            itemView.setOnClickListener(v -> launch(v.getContext(), boundApp));

            itemView.setOnLongClickListener(v -> {
                if (boundApp == null || listener == null) return false;
                listener.onLongClick(boundApp, getBindingAdapterPosition());
                return true;
            });

            itemView.setOnFocusChangeListener((v, hasFocus) -> applyFocusState(hasFocus));
        }

        void applyFocusState(boolean hasFocus) {
            View v = itemView;
            float lift = dpToPx(v.getContext(), 8);
            if (hasFocus) {
                v.setBackground(focusBackground);
                v.setScaleX(1.06f);
                v.setScaleY(1.06f);
                // Поднимаем выбранную плитку над соседними, чтобы они её не перекрывали
                v.setTranslationZ(lift);
            } else {
                v.setBackground(normalBackground);
                v.setScaleX(1.0f);
                v.setScaleY(1.0f);
                v.setTranslationZ(0f);
            }
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
