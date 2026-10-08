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
                label = cleanLabel(line.substring(0, pipe));
                value = line.substring(pipe + 1).trim();
            } else {
                label = labelFromSharedText(line);
            }

            String url = normalizeToAmazonUrl(value);
            if (url == null) continue;
            addOrUpdate(items, label, url);
        }
        save(c, items);
    }

    public static synchronized boolean addShared(Context c, String sharedText, String sharedSubject) {
        if (sharedText == null) return false;
        String url = normalizeToAmazonUrl(sharedText);
        if (url == null) return false;

        String label = cleanLabel(sharedSubject);
        if (label.isEmpty()) label = labelFromSharedText(sharedText);
        if (label.isEmpty()) label = consumePendingCaptureTitle(c);
        else clearPendingCaptureTitle(c);

        ArrayList<ProductItem> items = load(c);
        addOrUpdate(items, label, url);
        save(c, items);
        return true;
    }

    private static void addOrUpdate(ArrayList<ProductItem> items, String label, String url) {
        String clean = cleanLabel(label);
        for (ProductItem existing : items) {
            boolean sameUrl = existing.url.equalsIgnoreCase(url);
            boolean sameNamedProduct = !clean.isEmpty() && existing.label != null &&
                    !existing.label.trim().isEmpty() && existing.label.trim().equalsIgnoreCase(clean);
            if (sameUrl || sameNamedProduct) {
                if ((existing.label == null || existing.label.trim().isEmpty()) && !clean.isEmpty()) {
                    existing.label = clean;
                }
                return;
            }
        }
        items.add(new ProductItem(clean, url));
    }

    public static synchronized boolean containsLabel(Context c, String label) {
        String clean = cleanLabel(label);
        if (clean.isEmpty()) return false;
        for (ProductItem item : load(c)) {
            if (item.label != null && item.label.trim().equalsIgnoreCase(clean)) return true;
        }
        return false;
    }

    public static synchronized void updateLabel(Context c, int index, String label) {
        String clean = cleanLabel(label);
        if (clean.isEmpty()) return;
        ArrayList<ProductItem> items = load(c);
        if (index < 0 || index >= items.size()) return;
        ProductItem item = items.get(index);
        if (item.label == null || item.label.trim().isEmpty() || item.label.startsWith("Amazon item")) {
            item.label = clean;
            save(c, items);
        }
    }

    public static String normalizeToAmazonUrl(String value) {
        String v = value == null ? "" : value.trim();

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

    private static String labelFromSharedText(String raw) {
        if (raw == null) return "";
        String text = raw.replace('\r', ' ').replace('\n', ' ').trim();
        Matcher m = URL_PATTERN.matcher(text);
        if (m.find()) {
            String before = text.substring(0, m.start()).trim();
            String after = text.substring(m.end()).trim();
            String candidate = before.length() >= 8 ? before : after;
            candidate = candidate
                    .replaceAll("(?i)^check out this product on amazon[:\\s-]*", "")
                    .replaceAll("(?i)^check this out on amazon[:\\s-]*", "")
                    .replaceAll("(?i)^amazon\\s*[-–—:]?\\s*", "")
                    .trim();
            return cleanLabel(candidate);
        }
        return "";
    }

    private static String cleanLabel(String value) {
        if (value == null) return "";
        String s = value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (s.length() > 180) s = s.substring(0, 180).trim();
        if (s.equalsIgnoreCase("Amazon") || s.equalsIgnoreCase("Amazon.co.uk") ||
                s.equalsIgnoreCase("Share Item") || s.equalsIgnoreCase("Share") ||
                s.equalsIgnoreCase("Amazon Shopping")) return "";
        if (s.startsWith("http://") || s.startsWith("https://")) return "";
        return s;
    }


    public static void applyV15Defaults(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getInt("defaults_version", 0) < 15) {
            p.edit()
                    .putBoolean("stop_on_available", false)
                    .putBoolean("quick_add", true)
                    .putBoolean("auto_collect_opened", false)
                    .putInt("defaults_version", 15)
                    .apply();
        }
    }

    public static void setQuickAdd(Context c, boolean value) {
        prefs(c).edit().putBoolean("quick_add", value).apply();
    }

    public static boolean quickAdd(Context c) {
        return prefs(c).getBoolean("quick_add", true);
    }

    // Kept for compatibility with settings from v1.3. v1.5 no longer auto-shares
    // full product pages; Quick Add is intentionally triggered by a long-press preview.
    public static void setAutoCollectOpened(Context c, boolean value) {
        prefs(c).edit().putBoolean("auto_collect_opened", value).apply();
    }

    public static boolean autoCollectOpened(Context c) {
        return prefs(c).getBoolean("auto_collect_opened", false);
    }

    public static void setPendingCaptureTitle(Context c, String title) {
        prefs(c).edit().putString("pending_capture_title", cleanLabel(title)).apply();
    }

    public static String consumePendingCaptureTitle(Context c) {
        SharedPreferences p = prefs(c);
        String title = p.getString("pending_capture_title", "");
        p.edit().remove("pending_capture_title").apply();
        return cleanLabel(title);
    }

    public static void clearPendingCaptureTitle(Context c) {
        prefs(c).edit().remove("pending_capture_title").apply();
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
        return prefs(c).getBoolean("stop_on_available", false);
    }

    public static void setAutoRequest(Context c, boolean value) {
        prefs(c).edit().putBoolean("auto_request", value).apply();
    }

    public static boolean autoRequest(Context c) {
        return prefs(c).getBoolean("auto_request", true);
    }
}
