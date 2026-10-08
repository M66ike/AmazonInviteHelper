package com.mike.invitehelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Locale;

public class InviteAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private LinearLayout overlay;
    private TextView overlayText;

    private int workingIndex = -1;
    private int scrollAttempts = 0;
    private boolean clickedRequest = false;
    private long openedAt = 0L;
    private long requestClickedAt = 0L;
    private long lastProcessAt = 0L;
    private boolean processingScheduled = false;
    private static final int MAX_SCROLLS = 14;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (QueueStore.isRunning(this)) {
            showOverlay();
            handler.postDelayed(this::ensureCurrentItemOpen, 600);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!QueueStore.isRunning(this)) {
            hideOverlay();
            return;
        }
        showOverlay();
        updateOverlay();

        if (QueueStore.isPaused(this)) return;
        if (event == null || event.getPackageName() == null) return;
        if (!MainActivity.AMAZON_PACKAGE.contentEquals(event.getPackageName())) return;

        scheduleProcess(450);
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }

    private void scheduleProcess(long delay) {
        if (processingScheduled) return;
        processingScheduled = true;
        handler.postDelayed(() -> {
            processingScheduled = false;
            processCurrentScreen();
        }, delay);
    }

    private void ensureCurrentItemOpen() {
        if (!QueueStore.isRunning(this) || QueueStore.isPaused(this)) return;
        ArrayList<ProductItem> items = QueueStore.load(this);
        if (items.isEmpty()) {
            finishRun("Queue is empty");
            return;
        }
        int index = QueueStore.getCurrentIndex(this);
        if (index < 0 || index >= items.size()) index = 0;
        beginItem(index, items.get(index));
    }

    private void beginItem(int index, ProductItem item) {
        workingIndex = index;
        scrollAttempts = 0;
        clickedRequest = false;
        requestClickedAt = 0L;
        openedAt = System.currentTimeMillis();
        QueueStore.setCurrentIndex(this, index);
        updateOverlay();
        openAmazon(item.url);
        handler.postDelayed(() -> scheduleProcess(0), 1350);
    }

    private void processCurrentScreen() {
        if (!QueueStore.isRunning(this) || QueueStore.isPaused(this)) return;
        long now = System.currentTimeMillis();
        if (now - lastProcessAt < 300) return;
        lastProcessAt = now;

        ArrayList<ProductItem> items = QueueStore.load(this);
        if (items.isEmpty()) {
            finishRun("Queue complete");
            return;
        }
        int index = QueueStore.getCurrentIndex(this);
        if (index < 0 || index >= items.size()) {
            finishRun("Queue complete");
            return;
        }
        if (workingIndex != index) {
            beginItem(index, items.get(index));
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            if (now - openedAt > 7000) markAndAdvance(ProductItem.Status.ERROR, "Couldn't read Amazon screen");
            else scheduleProcess(700);
            return;
        }

        ScanResult scan = scanTree(root);

        if (scan.available) {
            mark(items, index, ProductItem.Status.AVAILABLE);
            notifyChanged();
            alertAvailable(items.get(index));
            if (QueueStore.stopOnAvailable(this)) {
                QueueStore.setPaused(this, true);
                updateOverlay("AVAILABLE TO BUY — paused");
            } else {
                advanceAfter(1200);
            }
            root.recycle();
            return;
        }

        if (scan.requested) {
            markAndAdvance(ProductItem.Status.REQUESTED, "Invitation already requested");
            root.recycle();
            return;
        }

        if (scan.requestNode != null) {
            if (!QueueStore.autoRequest(this)) {
                updateOverlay("Request invite found — auto request is off");
                root.recycle();
                return;
            }
            if (!clickedRequest) {
                clickedRequest = true;
                requestClickedAt = now;
                updateOverlay("Requesting invitation…");
                boolean clicked = clickNode(scan.requestNode);
                if (!clicked) clicked = tapNode(scan.requestNode);
                if (!clicked) {
                    clickedRequest = false;
                    scheduleProcess(800);
                } else {
                    handler.postDelayed(() -> scheduleProcess(0), 1200);
                }
                root.recycle();
                return;
            }

            if (requestClickedAt > 0 && now - requestClickedAt > 5500) {
                markAndAdvance(ProductItem.Status.ERROR, "Request button did not confirm");
            } else {
                scheduleProcess(800);
            }
            root.recycle();
            return;
        }

        if (scrollAttempts < MAX_SCROLLS) {
            scrollAttempts++;
            updateOverlay("Scanning… scroll " + scrollAttempts + "/" + MAX_SCROLLS);
            boolean scrolled = scrollForward(root);
            if (!scrolled) performSwipeUp();
            handler.postDelayed(() -> scheduleProcess(0), 650);
        } else {
            markAndAdvance(ProductItem.Status.NO_INVITE_CONTROL, "No invitation control found");
        }
        root.recycle();
    }

    private ScanResult scanTree(AccessibilityNodeInfo root) {
        ScanResult result = new ScanResult();
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            String text = combinedText(n).toLowerCase(Locale.ROOT);

            if (containsAny(text,
                    "available for you to buy",
                    "available to buy",
                    "available for you to purchase",
                    "you have been invited to purchase",
                    "invited to purchase")) {
                result.available = true;
            }
            if (containsAny(text,
                    "invitation requested",
                    "invitation requested, thanks",
                    "you'll get an email with a link that's valid for 72 hours")) {
                result.requested = true;
            }
            if (result.requestNode == null && containsAny(text, "request invite", "request invitation")) {
                result.requestNode = AccessibilityNodeInfo.obtain(n);
            }

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
            n.recycle();
        }
        return result;
    }

    private String combinedText(AccessibilityNodeInfo n) {
        StringBuilder b = new StringBuilder();
        if (n.getText() != null) b.append(n.getText()).append(' ');
        if (n.getContentDescription() != null) b.append(n.getContentDescription()).append(' ');
        if (n.getHintText() != null) b.append(n.getHintText());
        return b.toString();
    }

    private boolean containsAny(String text, String... needles) {
        for (String n : needles) if (text.contains(n)) return true;
        return false;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < 7 && current != null; i++) {
            if (current.isClickable() && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                current.recycle();
                return true;
            }
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        if (current != null) current.recycle();
        return false;
    }

    private boolean tapNode(AccessibilityNodeInfo node) {
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.isEmpty()) return false;
        Path p = new Path();
        p.moveTo(r.exactCenterX(), r.exactCenterY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 90))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    private boolean scrollForward(AccessibilityNodeInfo root) {
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (n.isScrollable() && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                n.recycle();
                while (!q.isEmpty()) q.removeFirst().recycle();
                return true;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
            n.recycle();
        }
        return false;
    }

    private void performSwipeUp() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float x = dm.widthPixels * 0.5f;
        float startY = dm.heightPixels * 0.78f;
        float endY = dm.heightPixels * 0.34f;
        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 360))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private void markAndAdvance(ProductItem.Status status, String overlayMessage) {
        ArrayList<ProductItem> items = QueueStore.load(this);
        int index = QueueStore.getCurrentIndex(this);
        if (index >= 0 && index < items.size()) mark(items, index, status);
        updateOverlay(overlayMessage);
        notifyChanged();
        advanceAfter(850);
    }

    private void mark(ArrayList<ProductItem> items, int index, ProductItem.Status status) {
        ProductItem item = items.get(index);
        item.status = status;
        item.lastChecked = System.currentTimeMillis();
        QueueStore.save(this, items);
    }

    private void advanceAfter(long delay) {
        handler.postDelayed(() -> {
            if (!QueueStore.isRunning(this) || QueueStore.isPaused(this)) return;
            ArrayList<ProductItem> items = QueueStore.load(this);
            int next = QueueStore.getCurrentIndex(this) + 1;
            if (next >= items.size()) {
                finishRun("Queue complete");
                return;
            }
            QueueStore.setCurrentIndex(this, next);
            notifyChanged();
            // Close the current product page before opening the next queued item.
            performGlobalAction(GLOBAL_ACTION_BACK);
            handler.postDelayed(() -> beginItem(next, items.get(next)), 420);
        }, delay);
    }

    private void finishRun(String message) {
        QueueStore.setRunning(this, false);
        QueueStore.setPaused(this, false);
        updateOverlay(message);
        notifyChanged();
        handler.postDelayed(this::hideOverlay, 2200);
    }

    private void openAmazon(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage(MainActivity.AMAZON_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ignored) {
                markAndAdvance(ProductItem.Status.ERROR, "Couldn't open product");
            }
        }
    }

    private void alertAvailable(ProductItem item) {
        try {
            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib != null && android.os.Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 180, 100, 180, 100, 400}, -1));
            }
        } catch (Exception ignored) {}
        Toast.makeText(this, "AVAILABLE TO BUY: " + (item.label.isEmpty() ? "Amazon item" : item.label), Toast.LENGTH_LONG).show();
    }

    private void notifyChanged() {
        Intent i = new Intent(MainActivity.ACTION_QUEUE_CHANGED);
        i.setPackage(getPackageName());
        sendBroadcast(i);
        updateOverlay();
    }

    private void showOverlay() {
        if (overlay != null || windowManager == null) return;
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.HORIZONTAL);
        overlay.setGravity(Gravity.CENTER_VERTICAL);
        overlay.setPadding(dp(10), dp(6), dp(6), dp(6));
        overlay.setBackgroundColor(Color.argb(232, 35, 35, 35));

        overlayText = new TextView(this);
        overlayText.setTextColor(Color.WHITE);
        overlayText.setTextSize(13);
        overlay.addView(overlayText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button pause = new Button(this);
        pause.setText("Pause");
        pause.setTextSize(11);
        pause.setAllCaps(false);
        pause.setOnClickListener(v -> {
            boolean paused = !QueueStore.isPaused(this);
            QueueStore.setPaused(this, paused);
            pause.setText(paused ? "Resume" : "Pause");
            notifyChanged();
            if (!paused) scheduleProcess(250);
        });
        overlay.addView(pause, new LinearLayout.LayoutParams(dp(82), dp(46)));

        Button stop = new Button(this);
        stop.setText("Stop");
        stop.setTextSize(11);
        stop.setAllCaps(false);
        stop.setOnClickListener(v -> finishRun("Stopped"));
        overlay.addView(stop, new LinearLayout.LayoutParams(dp(68), dp(46)));

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP;
        try { windowManager.addView(overlay, p); } catch (Exception ignored) { overlay = null; }
        updateOverlay();
    }

    private void hideOverlay() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        overlay = null;
        overlayText = null;
    }

    private void updateOverlay() {
        if (overlayText == null) return;
        ArrayList<ProductItem> items = QueueStore.load(this);
        int i = QueueStore.getCurrentIndex(this);
        String state = QueueStore.isPaused(this) ? "PAUSED" : "Scanning";
        overlayText.setText("Invite Helper • " + state + " • " + (items.isEmpty() ? "0/0" : (Math.min(i + 1, items.size()) + "/" + items.size())));
    }

    private void updateOverlay(String message) {
        if (overlayText != null) overlayText.setText("Invite Helper • " + message);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class ScanResult {
        boolean available = false;
        boolean requested = false;
        AccessibilityNodeInfo requestNode = null;
    }
}
