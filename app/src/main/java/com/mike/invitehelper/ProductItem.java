package com.mike.invitehelper;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

public class ProductItem {
    public enum Status {
        PENDING,
        REQUESTED,
        REQUESTED_NOW,
        ALREADY_REQUESTED,
        AVAILABLE,
        NO_INVITE_CONTROL,
        PURCHASED,
        OTHER_SELLER,
        ERROR
    }

    public String label;
    public String url;
    public Status status;
    public long lastChecked;
    public String note;

    public ProductItem(String label, String url) {
        this.label = label == null ? "" : label.trim();
        this.url = url == null ? "" : url.trim();
        this.status = Status.PENDING;
        this.lastChecked = 0L;
        this.note = "";
    }

    public String serialize() {
        return enc(label) + "\t" + enc(url) + "\t" + status.name() + "\t" + lastChecked + "\t" + enc(note == null ? "" : note);
    }

    public static ProductItem deserialize(String line) {
        try {
            String[] p = line.split("\\t", -1);
            if (p.length < 4) return null;
            ProductItem item = new ProductItem(dec(p[0]), dec(p[1]));
            item.status = Status.valueOf(p[2]);
            item.lastChecked = Long.parseLong(p[3]);
            if (p.length >= 5) item.note = dec(p[4]);
            return item;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String enc(String s) {
        return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String dec(String s) {
        return new String(Base64.decode(s, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
