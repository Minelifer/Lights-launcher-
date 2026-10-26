package com.example.lightlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Избранные приложения: хранит пакеты в порядке добавления в SharedPreferences. */
public class Favorites {

    private static final String PREFS = "favorites";
    private static final String KEY = "packages";

    private final SharedPreferences prefs;
    private final List<String> items = new ArrayList<>();

    public Favorites(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, "");
        for (String pkg : raw.split("\n")) {
            if (!pkg.isEmpty() && !items.contains(pkg)) {
                items.add(pkg);
            }
        }
    }

    public List<String> list() {
        return Collections.unmodifiableList(items);
    }

    public boolean contains(String packageName) {
        return items.contains(packageName);
    }

    /** Добавляет пакет в избранное или убирает его. Возвращает true, если пакет теперь в избранном. */
    public boolean toggle(String packageName) {
        boolean added;
        if (items.remove(packageName)) {
            added = false;
        } else {
            items.add(packageName);
            added = true;
        }
        prefs.edit().putString(KEY, TextUtils.join("\n", items)).apply();
        return added;
    }
}
