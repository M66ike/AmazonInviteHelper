package com.mike.invitehelper;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QueueStore {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final String PREF = "invite_helper";
    private static final String KEY_ITEMS = "items_v1";

    private QueueStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized ArrayList<ProductItem> load(Context c) {
        ArrayList<ProductItem> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY_ITEMS, "");
        if (raw == null || raw.isEmpty()) return out;
        String[] lines = raw.split("\\n");
        for (String line : lines) {
            ProductItem item = ProductItem.deserialize(line);
            if (item != null && !item.url.isEmpty()) out.add(item);
        }
        return out;
    }

    public static synchronized void save(Context c, List<ProductItem> items) {
        StringBuilder b = new StringBuilder();
        for (ProductItem item : items) {
            if (b.length() > 0) b.append('\n');
            b.append(item.serialize());
        }
        prefs(c).edit().putString(KEY_ITEMS, b.toString()).apply();
    }

    public static synchronized void addLines(Context c, String raw) {
        ArrayList<ProductItem> items = load(c);
        String[] lines = raw.replace("\r", "\n").split("\n+");
        for (String original : lines) {
            String line = original.trim();
            if (line.isEmpty()) continue;

            String label = "";
            String value = line;
            int pipe = line.indexOf('|');
            if (pipe > 0 && pipe < line.length() - 1) {
                label = line.substring(0, pipe).trim();
                value = line.substring(pipe + 1).trim();
            }

            String url = normalizeToAmazonUrl(value);
            if (url == null) continue;

            boolean duplicate = false;
            for (ProductItem existing : items) {
                if (existing.url.equalsIgnoreCase(url)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) items.add(new ProductItem(label, url));
        }
        save(c, items);
    }

    public static String normalizeToAmazonUrl(String value) {
        String v = value.trim();

        Matcher urlMatcher = URL_PATTERN.matcher(v);
        if (urlMatcher.find()) {
            String url = urlMatcher.group();
            while (url.endsWith(")") || url.endsWith("]") || url.endsWith("}") || url.endsWith(",") || url.endsWith(".")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }

        if (v.matches("(?i)^[A-Z0-9]{10}$")) {
            return "https://www.amazon.co.uk/dp/" + v.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    public static void setCurrentIndex(Context c, int index) {
        prefs(c).edit().putInt("current_index", index).apply();
    }

    public static int getCurrentIndex(Context c) {
        return prefs(c).getInt("current_index", 0);
    }

    public static void setRunning(Context c, boolean value) {
        prefs(c).edit().putBoolean("running", value).apply();
    }

    public static boolean isRunning(Context c) {
        return prefs(c).getBoolean("running", false);
    }

    public static void setPaused(Context c, boolean value) {
        prefs(c).edit().putBoolean("paused", value).apply();
    }

    public static boolean isPaused(Context c) {
        return prefs(c).getBoolean("paused", false);
    }

    public static void setStopOnAvailable(Context c, boolean value) {
        prefs(c).edit().putBoolean("stop_on_available", value).apply();
    }

    public static boolean stopOnAvailable(Context c) {
        return prefs(c).getBoolean("stop_on_available", true);
    }

    public static void setAutoRequest(Context c, boolean value) {
        prefs(c).edit().putBoolean("auto_request", value).apply();
    }

    public static boolean autoRequest(Context c) {
        return prefs(c).getBoolean("auto_request", true);
    }
}
