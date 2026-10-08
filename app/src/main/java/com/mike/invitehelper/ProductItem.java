package com.mike.invitehelper;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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

    public static class AccountResult {
        public String email;
        public Status status;
        public long lastChecked;
        public String note;

        public AccountResult(String email, Status status, long lastChecked, String note) {
            this.email = email == null ? "" : email.trim();
            this.status = status == null ? Status.PENDING : status;
            this.lastChecked = lastChecked;
            this.note = note == null ? "" : note.trim();
        }
    }

    public String label;
    public String url;
    public Status status;
    public long lastChecked;
    public String note;
    public final ArrayList<AccountResult> accountResults = new ArrayList<>();

    public ProductItem(String label, String url) {
        this.label = label == null ? "" : label.trim();
        this.url = url == null ? "" : url.trim();
        this.status = Status.PENDING;
        this.lastChecked = 0L;
        this.note = "";
    }

    public AccountResult getAccountResult(String email) {
        String wanted = AccountStore.canonicalEmail(email);
        if (wanted.isEmpty()) return null;
        for (AccountResult r : accountResults) {
            if (AccountStore.canonicalEmail(r.email).equals(wanted)) return r;
        }
        return null;
    }

    public void setAccountResult(String email, Status newStatus, long checked, String resultNote) {
        String clean = AccountStore.canonicalEmail(email);
        if (clean.isEmpty()) return;
        AccountResult r = getAccountResult(clean);
        if (r == null) {
            r = new AccountResult(clean, newStatus, checked, resultNote);
            accountResults.add(r);
        } else {
            r.status = newStatus == null ? Status.PENDING : newStatus;
            r.lastChecked = checked;
            r.note = resultNote == null ? "" : resultNote.trim();
        }
    }

    public void clearResults() {
        accountResults.clear();
        status = Status.PENDING;
        lastChecked = 0L;
        note = "";
    }

    public String serialize() {
        return enc(label) + "\t" + enc(url) + "\t" + status.name() + "\t" + lastChecked + "\t" +
                enc(note == null ? "" : note) + "\t" + enc(serializeAccountResults());
    }

    public static ProductItem deserialize(String line) {
        try {
            String[] p = line.split("\\t", -1);
            if (p.length < 4) return null;
            ProductItem item = new ProductItem(dec(p[0]), dec(p[1]));
            item.status = parseStatus(p[2]);
            item.lastChecked = Long.parseLong(p[3]);
            if (p.length >= 5) item.note = dec(p[4]);
            if (p.length >= 6) item.deserializeAccountResults(dec(p[5]));
            return item;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String serializeAccountResults() {
        StringBuilder b = new StringBuilder();
        for (AccountResult r : accountResults) {
            if (r == null || AccountStore.canonicalEmail(r.email).isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(enc(AccountStore.canonicalEmail(r.email))).append('|')
                    .append(r.status == null ? Status.PENDING.name() : r.status.name()).append('|')
                    .append(r.lastChecked).append('|')
                    .append(enc(r.note == null ? "" : r.note));
        }
        return b.toString();
    }

    private void deserializeAccountResults(String raw) {
        accountResults.clear();
        if (raw == null || raw.trim().isEmpty()) return;
        for (String row : raw.split("\\n")) {
            try {
                String[] p = row.split("\\|", -1);
                if (p.length < 4) continue;
                String email = dec(p[0]);
                Status s = parseStatus(p[1]);
                long checked = Long.parseLong(p[2]);
                String n = dec(p[3]);
                if (!AccountStore.canonicalEmail(email).isEmpty()) {
                    accountResults.add(new AccountResult(email, s, checked, n));
                }
            } catch (Exception ignored) {}
        }
    }

    private static Status parseStatus(String value) {
        try { return Status.valueOf(value); }
        catch (Exception ignored) { return Status.PENDING; }
    }

    private static String enc(String s) {
        return Base64.encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String dec(String s) {
        return new String(Base64.decode(s, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }
}
