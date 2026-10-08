package com.mike.invitehelper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends Activity {
    public static final String ACTION_QUEUE_CHANGED = "com.mike.invitehelper.QUEUE_CHANGED";
    public static final String AMAZON_PACKAGE = "com.amazon.mShop.android.shopping";
    public static final String INVITE_SEARCH_URL = "https://www.amazon.co.uk/s?k=Pokemon+Trading+Card+Game&rh=p_123%3A325733%2Cp_6%3AA3P5ROKL5A1OLE&s=date-desc-rank&dc=";

    private LinearLayout listContainer;
    private TextView serviceState;
    private TextView runState;
    private EditText input;
    private CheckBox autoRequest;
    private CheckBox stopOnAvailable;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        handleIncomingText(getIntent());
        refresh();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingText(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_QUEUE_CHANGED);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
    }

    private View buildUi() {
        ScrollView scroller = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(32));
        scroller.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Amazon Invite Helper", 25, true);
        root.addView(title);
        TextView sub = text("Queues Amazon product pages, requests invitation-only items, and flags products that become available for you to buy. It never presses Add to Basket or Buy Now.", 15, false);
        sub.setPadding(0, dp(6), 0, dp(12));
        root.addView(sub);

        serviceState = text("", 14, true);
        root.addView(serviceState);

        Button enable = button("Enable accessibility service");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(enable);

        Button search = button("Open Pokémon invitation search in Amazon");
        search.setOnClickListener(v -> openAmazon(INVITE_SEARCH_URL));
        root.addView(search);

        TextView addHeader = text("Add products", 19, true);
        addHeader.setPadding(0, dp(16), 0, dp(5));
        root.addView(addHeader);

        TextView hint = text("Paste one Amazon URL or ASIN per line. You can also use: Product name | URL", 13, false);
        root.addView(hint);

        input = new EditText(this);
        input.setHint("B0XXXXXXXX\nhttps://www.amazon.co.uk/dp/...\nProduct name | https://amzn.to/...");
        input.setMinLines(4);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(input, lpMatch());

        LinearLayout addRow = horizontal();
        Button paste = button("Paste");
        paste.setOnClickListener(v -> pasteClipboard());
        Button add = button("Add to queue");
        add.setOnClickListener(v -> {
            QueueStore.addLines(this, input.getText().toString());
            input.setText("");
            refresh();
        });
        addRow.addView(paste, lpWeight());
        addRow.addView(add, lpWeight());
        root.addView(addRow);

        autoRequest = new CheckBox(this);
        autoRequest.setText("Automatically tap “Request invite”");
        autoRequest.setChecked(QueueStore.autoRequest(this));
        autoRequest.setOnCheckedChangeListener((b, checked) -> QueueStore.setAutoRequest(this, checked));
        root.addView(autoRequest);

        stopOnAvailable = new CheckBox(this);
        stopOnAvailable.setText("Stop when an item is available to buy");
        stopOnAvailable.setChecked(QueueStore.stopOnAvailable(this));
        stopOnAvailable.setOnCheckedChangeListener((b, checked) -> QueueStore.setStopOnAvailable(this, checked));
        root.addView(stopOnAvailable);

        runState = text("", 15, true);
        runState.setPadding(0, dp(10), 0, dp(6));
        root.addView(runState);

        LinearLayout runRow = horizontal();
        Button start = button("Start / Resume");
        start.setOnClickListener(v -> startQueue());
        Button pause = button("Pause");
        pause.setOnClickListener(v -> {
            QueueStore.setPaused(this, true);
            broadcastChanged();
        });
        Button stop = button("Stop");
        stop.setOnClickListener(v -> {
            QueueStore.setRunning(this, false);
            QueueStore.setPaused(this, false);
            broadcastChanged();
        });
        runRow.addView(start, lpWeight());
        runRow.addView(pause, lpWeight());
        runRow.addView(stop, lpWeight());
        root.addView(runRow);

        TextView queueHeader = text("Queue", 19, true);
        queueHeader.setPadding(0, dp(18), 0, dp(6));
        root.addView(queueHeader);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer, lpMatch());

        Button reset = button("Reset statuses to Pending");
        reset.setOnClickListener(v -> resetStatuses());
        root.addView(reset);

        Button clear = button("Clear queue");
        clear.setOnClickListener(v -> {
            QueueStore.save(this, new ArrayList<>());
            QueueStore.setCurrentIndex(this, 0);
            refresh();
        });
        root.addView(clear);

        TextView footer = text("V1 works entirely through Android’s Accessibility API on the Amazon Shopping app. Amazon can change its screen text/layout, so the scanner intentionally uses several text checks and a scrolling fallback.", 12, false);
        footer.setPadding(0, dp(16), 0, 0);
        root.addView(footer);
        return scroller;
    }

    private void startQueue() {
        ArrayList<ProductItem> items = QueueStore.load(this);
        if (items.isEmpty()) {
            Toast.makeText(this, "Add at least one product first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isServiceEnabled()) {
            Toast.makeText(this, "Enable Amazon Invite Helper in Accessibility first.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        int index = QueueStore.getCurrentIndex(this);
        if (index < 0 || index >= items.size()) index = 0;
        QueueStore.setCurrentIndex(this, index);
        QueueStore.setPaused(this, false);
        QueueStore.setRunning(this, true);
        openAmazon(items.get(index).url);
        broadcastChanged();
    }

    private void resetStatuses() {
        ArrayList<ProductItem> items = QueueStore.load(this);
        for (ProductItem item : items) {
            item.status = ProductItem.Status.PENDING;
            item.lastChecked = 0L;
        }
        QueueStore.save(this, items);
        QueueStore.setCurrentIndex(this, 0);
        refresh();
    }

    private void refresh() {
        if (serviceState == null) return;
        boolean enabled = isServiceEnabled();
        serviceState.setText(enabled ? "Accessibility: enabled" : "Accessibility: NOT enabled");
        serviceState.setTextColor(enabled ? Color.rgb(0, 120, 70) : Color.rgb(180, 40, 40));

        boolean running = QueueStore.isRunning(this);
        boolean paused = QueueStore.isPaused(this);
        int index = QueueStore.getCurrentIndex(this);
        ArrayList<ProductItem> items = QueueStore.load(this);
        if (running) runState.setText("Run: " + (paused ? "PAUSED" : "RUNNING") + "   Item " + Math.min(index + 1, items.size()) + " / " + items.size());
        else runState.setText("Run: stopped   " + items.size() + " queued");

        listContainer.removeAllViews();
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (int i = 0; i < items.size(); i++) {
            final int position = i;
            ProductItem item = items.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            card.setBackgroundColor(i == index && running ? Color.rgb(255, 247, 210) : Color.rgb(245, 245, 245));

            String name = item.label.isEmpty() ? item.url : item.label;
            TextView nameView = text((i + 1) + ". " + name, 14, true);
            card.addView(nameView);

            String checked = item.lastChecked == 0 ? "Never checked" : df.format(new Date(item.lastChecked));
            TextView state = text(statusText(item.status) + " • " + checked, 12, false);
            state.setTextColor(statusColor(item.status));
            card.addView(state);

            LinearLayout actions = horizontal();
            Button open = smallButton("Open");
            open.setOnClickListener(v -> openAmazon(item.url));
            Button remove = smallButton("Remove");
            remove.setOnClickListener(v -> {
                ArrayList<ProductItem> now = QueueStore.load(this);
                if (position < now.size()) now.remove(position);
                QueueStore.save(this, now);
                int current = QueueStore.getCurrentIndex(this);
                if (current >= now.size()) QueueStore.setCurrentIndex(this, Math.max(0, now.size() - 1));
                refresh();
            });
            actions.addView(open, lpWeight());
            actions.addView(remove, lpWeight());
            card.addView(actions);

            LinearLayout.LayoutParams cp = lpMatch();
            cp.setMargins(0, 0, 0, dp(7));
            listContainer.addView(card, cp);
        }
    }

    private String statusText(ProductItem.Status status) {
        switch (status) {
            case REQUESTED: return "Invitation requested";
            case AVAILABLE: return "AVAILABLE TO BUY";
            case NO_INVITE_CONTROL: return "No invitation control found";
            case ERROR: return "Check failed";
            default: return "Pending";
        }
    }

    private int statusColor(ProductItem.Status status) {
        switch (status) {
            case AVAILABLE: return Color.rgb(0, 125, 70);
            case REQUESTED: return Color.rgb(35, 95, 175);
            case ERROR: return Color.rgb(185, 35, 35);
            case NO_INVITE_CONTROL: return Color.DKGRAY;
            default: return Color.rgb(130, 90, 0);
        }
    }

    private boolean isServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabled) {
            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                String pkg = info.getResolveInfo().serviceInfo.packageName;
                String cls = info.getResolveInfo().serviceInfo.name;
                if (getPackageName().equals(pkg) && InviteAccessibilityService.class.getName().equals(cls)) return true;
            }
        }
        return false;
    }

    private void openAmazon(String url) {
        try {
            Intent in = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            in.setPackage(AMAZON_PACKAGE);
            startActivity(in);
        } catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception ignored) { Toast.makeText(this, "Couldn't open that URL.", Toast.LENGTH_SHORT).show(); }
        }
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData clip = cm.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence s = clip.getItemAt(0).coerceToText(this);
            input.setText(s);
        }
    }

    private void handleIncomingText(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                QueueStore.addLines(this, shared);
                Toast.makeText(this, "Added shared Amazon link to queue.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void broadcastChanged() {
        Intent i = new Intent(ACTION_QUEUE_CHANGED);
        i.setPackage(getPackageName());
        sendBroadcast(i);
        refresh();
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(30, 30, 30));
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private Button smallButton(String s) {
        Button b = button(s);
        b.setTextSize(12);
        b.setMinHeight(0);
        return b;
    }

    private LinearLayout.LayoutParams lpMatch() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams lpWeight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
