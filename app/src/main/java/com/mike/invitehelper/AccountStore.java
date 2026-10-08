package com.mike.invitehelper;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class AccountStore {
    private static final String PREF = "invite_helper_accounts";
    private static final String KEY_ACCOUNTS = "accounts_v1";

    private AccountStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized ArrayList<AmazonAccount> load(Context c) {
        ArrayList<AmazonAccount> out = new ArrayList<>();
        String raw = prefs(c).getString(KEY_ACCOUNTS, "");
        if (raw == null || raw.trim().isEmpty()) return out;
        for (String line : raw.split("\\n")) {
            AmazonAccount a = AmazonAccount.deserialize(line);
            if (a != null && !a.email.isEmpty()) out.add(a);
        }
        return out;
    }

    public static synchronized void save(Context c, List<AmazonAccount> accounts) {
        StringBuilder b = new StringBuilder();
        for (AmazonAccount a : accounts) {
            if (a == null || a.email == null || a.email.trim().isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(a.serialize());
        }
        prefs(c).edit().putString(KEY_ACCOUNTS, b.toString()).apply();
    }

    public static synchronized int mergeDiscovered(Context c, Collection<String> emails) {
        ArrayList<AmazonAccount> accounts = load(c);
        int added = 0;
        for (String email : emails) {
            String clean = canonicalEmail(email);
            if (clean.isEmpty()) continue;
            boolean exists = false;
            for (AmazonAccount a : accounts) {
                if (canonicalEmail(a.email).equals(clean)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                accounts.add(new AmazonAccount(clean, "", true));
                added++;
            }
        }
        save(c, accounts);
        return added;
    }

    public static synchronized void setSelected(Context c, String email, boolean selected) {
        String clean = canonicalEmail(email);
        ArrayList<AmazonAccount> accounts = load(c);
        for (AmazonAccount a : accounts) {
            if (canonicalEmail(a.email).equals(clean)) a.selected = selected;
        }
        save(c, accounts);
    }

    public static synchronized ArrayList<AmazonAccount> selected(Context c) {
        ArrayList<AmazonAccount> out = new ArrayList<>();
        for (AmazonAccount a : load(c)) if (a.selected) out.add(a);
        return out;
    }

    public static String canonicalEmail(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
    }

    public static void startDiscovery(Context c) {
        prefs(c).edit()
                .putBoolean("discovering", true)
                .putInt("discovery_pass", 0)
                .putLong("discovery_started", System.currentTimeMillis())
                .apply();
    }

    public static void finishDiscovery(Context c) {
        prefs(c).edit().putBoolean("discovering", false).apply();
    }

    public static boolean isDiscovering(Context c) {
        return prefs(c).getBoolean("discovering", false);
    }

    public static void setDiscoveryPass(Context c, int pass) {
        prefs(c).edit().putInt("discovery_pass", pass).apply();
    }

    public static int getDiscoveryPass(Context c) {
        return prefs(c).getInt("discovery_pass", 0);
    }

    public static void resetRun(Context c) {
        prefs(c).edit()
                .putInt("run_account_index", 0)
                .putBoolean("run_account_ready", false)
                .putString("run_account_email", "")
                .apply();
    }

    public static void setRunAccountIndex(Context c, int index) {
        prefs(c).edit().putInt("run_account_index", Math.max(0, index)).apply();
    }

    public static int getRunAccountIndex(Context c) {
        return Math.max(0, prefs(c).getInt("run_account_index", 0));
    }

    public static void setRunAccountReady(Context c, boolean ready) {
        prefs(c).edit().putBoolean("run_account_ready", ready).apply();
    }

    public static boolean isRunAccountReady(Context c) {
        return prefs(c).getBoolean("run_account_ready", false);
    }

    public static void setRunAccountEmail(Context c, String email) {
        prefs(c).edit().putString("run_account_email", canonicalEmail(email)).apply();
    }

    public static String getRunAccountEmail(Context c) {
        return prefs(c).getString("run_account_email", "");
    }

    public static String currentSelectedEmail(Context c) {
        ArrayList<AmazonAccount> selected = selected(c);
        int i = getRunAccountIndex(c);
        if (i < 0 || i >= selected.size()) return "";
        return canonicalEmail(selected.get(i).email);
    }
}
