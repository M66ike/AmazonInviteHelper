package com.mike.invitehelper;

import android.util.Base64;

import java.nio.charset.StandardCharsets;

public class AmazonAccount {
    public String email;
    public String name;
    public boolean selected;

    public AmazonAccount(String email, String name, boolean selected) {
        this.email = email == null ? "" : email.trim();
        this.name = name == null ? "" : name.trim();
        this.selected = selected;
    }

    public String serialize() {
        return enc(email) + "\t" + enc(name) + "\t" + (selected ? "1" : "0");
    }

    public static AmazonAccount deserialize(String line) {
        try {
            String[] p = line.split("\\t", -1);
            if (p.length < 3) return null;
            return new AmazonAccount(dec(p[0]), dec(p[1]), "1".equals(p[2]));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String enc(String s) {
        return Base64.encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String dec(String s) {
        return new String(Base64.decode(s, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
