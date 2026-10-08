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
    private boolean advanceScheduled = false;
    private long settleUntil = 0L;
    private int itemGeneration = 0;

    // Quick Add mode (v1.4): deliberately triggered by an Amazon long-press preview.
    private static final int QA_IDLE = 0;
    private static final int QA_WAIT_AMAZON_SHARE_PANEL = 1;
    private static final int QA_WAIT_SYSTEM_SHARE = 2;
    private static final int QA_WAIT_RETURN = 3;
    private int quickAddStage = QA_IDLE;
    private boolean quickAddScheduled = false;
    private long quickAddStartedAt = 0L;
    private long quickAddCooldownUntil = 0L;
    private String quickAddTitle = "";

    private static final int MAX_SCROLLS = 18;

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
        if (event == null || event.getPackageName() == null) return;
        boolean amazonEvent = MainActivity.AMAZON_PACKAGE.contentEquals(event.getPackageName());

        if (!QueueStore.isRunning(this)) {
            hideOverlay();
            if (QueueStore.quickAdd(this) && (amazonEvent || quickAddStage != QA_IDLE)) {
                scheduleQuickAdd(260);
            }
            return;
        }

        showOverlay();
        updateOverlay();

        if (QueueStore.isPaused(this)) return;
        if (!amazonEvent) return;

        scheduleProcess(450);
    }

    @Override public void onInterrupt() {}

    @Override
    public void onDestroy() {
        hideOverlay();
        super.onDestroy();
    }

    private void scheduleQuickAdd(long delay) {
        if (quickAddScheduled || QueueStore.isRunning(this) || !QueueStore.quickAdd(this)) return;
        quickAddScheduled = true;
        handler.postDelayed(() -> {
            quickAddScheduled = false;
            processQuickAdd();
        }, delay);
    }

    private void processQuickAdd() {
        if (QueueStore.isRunning(this) || !QueueStore.quickAdd(this)) return;
        long now = System.currentTimeMillis();
        if (now < quickAddCooldownUntil) return;
        if (quickAddStage != QA_IDLE && now - quickAddStartedAt > 11000L) {
            resetQuickAdd("Quick Add timed out — try the long-press again.", false);
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        if (quickAddStage == QA_IDLE) {
            QuickAddPreview preview = scanQuickAddPreview(root);
            root.recycle();
            if (!preview.isPreview || preview.productTitle.isEmpty()) {
                recycleQuickAddPreview(preview);
                return;
            }

            quickAddTitle = preview.productTitle.trim();
            if (QueueStore.containsLabel(this, quickAddTitle)) {
                recycleQuickAddPreview(preview);
                quickAddCooldownUntil = now + 1400L;
                Toast.makeText(this, "Already in queue: " + shortTitle(quickAddTitle), Toast.LENGTH_SHORT).show();
                // Close the long-press preview so the user can immediately pick another item.
                handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 180);
                return;
            }

            QueueStore.setPendingCaptureTitle(this, quickAddTitle);
            quickAddStartedAt = now;
            quickAddStage = QA_WAIT_AMAZON_SHARE_PANEL;
            boolean clicked = preview.shareNode != null && (clickNode(preview.shareNode) || tapNode(preview.shareNode));
            recycleQuickAddPreview(preview);
            if (!clicked) clicked = tapAtFraction(0.50f, 0.78f);
            if (!clicked) {
                resetQuickAdd("Couldn't press Share on the Amazon preview.", false);
                return;
            }
            handler.postDelayed(() -> scheduleQuickAdd(0), 500);
            return;
        }

        if (quickAddStage == QA_WAIT_AMAZON_SHARE_PANEL) {
            boolean sharePanel = treeContains(root, "share this product with friends");
            AccessibilityNodeInfo more = findNodeByText(root, "more", true);
            root.recycle();
            if (!sharePanel) {
                handler.postDelayed(() -> scheduleQuickAdd(0), 350);
                return;
            }
            boolean clicked = more != null && (clickNode(more) || tapNode(more));
            if (more != null) try { more.recycle(); } catch (Exception ignored) {}
            if (!clicked) clicked = tapAtFraction(0.82f, 0.89f);
            if (clicked) {
                quickAddStage = QA_WAIT_SYSTEM_SHARE;
                handler.postDelayed(() -> scheduleQuickAdd(0), 550);
            } else {
                resetQuickAdd("Couldn't press More on Amazon's share panel.", false);
            }
            return;
        }

        if (quickAddStage == QA_WAIT_SYSTEM_SHARE) {
            AccessibilityNodeInfo target = findInviteHelperShareTarget(root);
            root.recycle();
            if (target == null) {
                handler.postDelayed(() -> scheduleQuickAdd(0), 350);
                return;
            }
            boolean clicked = clickNode(target) || tapNode(target);
            try { target.recycle(); } catch (Exception ignored) {}
            if (!clicked) {
                resetQuickAdd("Couldn't select Add to Invite Helper.", false);
                return;
            }
            quickAddStage = QA_WAIT_RETURN;
            handler.postDelayed(this::finishQuickAddReturn, 1250);
            return;
        }

        // WAIT_RETURN is completed by the delayed check above.
        root.recycle();
    }

    private QuickAddPreview scanQuickAddPreview(AccessibilityNodeInfo root) {
        QuickAddPreview result = new QuickAddPreview();
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int bestTitleScore = Integer.MIN_VALUE;

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (isVisibleOnScreen(n)) {
                String combined = combinedText(n).replace('\n', ' ').replaceAll("\\s+", " ").trim();
                String lower = combined.toLowerCase(Locale.ROOT);
                if (lower.contains("see all details")) result.hasDetailsButton = true;
                if (lower.contains("customers say")) result.hasPreviewMarker = true;
                if (result.shareNode == null && isShareControl(n, lower)) {
                    result.shareNode = AccessibilityNodeInfo.obtain(n);
                }
                if (n.getText() != null) {
                    String raw = n.getText().toString().replace('\n', ' ').replaceAll("\\s+", " ").trim();
                    if (looksLikeProductTitle(raw)) {
                        Rect bounds = new Rect();
                        n.getBoundsInScreen(bounds);
                        int score = titleScore(raw, bounds, screenHeight);
                        if (bounds.centerY() < screenHeight * 0.48f) score += 5;
                        if (score > bestTitleScore) {
                            bestTitleScore = score;
                            result.productTitle = raw.length() > 180 ? raw.substring(0, 180).trim() : raw;
                        }
                    }
                }
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
            n.recycle();
        }
        result.isPreview = result.hasDetailsButton && result.shareNode != null;
        return result;
    }

    private boolean isShareControl(AccessibilityNodeInfo node, String lowerCombined) {
        if (node == null || lowerCombined == null) return false;
        String t = lowerCombined.trim();
        if (!(t.equals("share") || t.equals("share item") || t.startsWith("share "))) return false;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.isEmpty()) return false;
        int w = getResources().getDisplayMetrics().widthPixels;
        return r.centerX() > w * 0.30f && r.centerX() < w * 0.70f;
    }

    private AccessibilityNodeInfo findInviteHelperShareTarget(AccessibilityNodeInfo root) {
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            String lower = combinedText(n).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (containsAny(lower, "add to invite helper", "amazon invite helper", "add to invi")) {
                AccessibilityNodeInfo out = AccessibilityNodeInfo.obtain(n);
                n.recycle();
                while (!q.isEmpty()) q.removeFirst().recycle();
                return out;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
            n.recycle();
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo root, String text, boolean exact) {
        String wanted = text.toLowerCase(Locale.ROOT);
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            String lower = combinedText(n).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            boolean match = exact ? lower.equals(wanted) : lower.contains(wanted);
            if (isVisibleOnScreen(n) && match) {
                AccessibilityNodeInfo out = AccessibilityNodeInfo.obtain(n);
                n.recycle();
                while (!q.isEmpty()) q.removeFirst().recycle();
                return out;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
            n.recycle();
        }
        return null;
    }

    private boolean treeContains(AccessibilityNodeInfo root, String needle) {
        String wanted = needle.toLowerCase(Locale.ROOT);
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));
        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            if (isVisibleOnScreen(n) && combinedText(n).toLowerCase(Locale.ROOT).contains(wanted)) {
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

    private boolean tapAtFraction(float fx, float fy) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        Path p = new Path();
        p.moveTo(dm.widthPixels * fx, dm.heightPixels * fy);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 90))
                .build();
        return dispatchGesture(gesture, null, null);
    }

    private void finishQuickAddReturn() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean amazonShareStillOpen = root != null && treeContains(root, "share this product with friends");
        if (root != null) root.recycle();
        if (amazonShareStillOpen) performGlobalAction(GLOBAL_ACTION_BACK);
        quickAddCooldownUntil = System.currentTimeMillis() + 1000L;
        quickAddStage = QA_IDLE;
        quickAddTitle = "";
        QueueStore.clearPendingCaptureTitle(this);
    }

    private void resetQuickAdd(String message, boolean closeOneScreen) {
        if (closeOneScreen) performGlobalAction(GLOBAL_ACTION_BACK);
        QueueStore.clearPendingCaptureTitle(this);
        quickAddStage = QA_IDLE;
        quickAddTitle = "";
        quickAddCooldownUntil = System.currentTimeMillis() + 900L;
        if (message != null && !message.isEmpty()) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void recycleQuickAddPreview(QuickAddPreview preview) {
        if (preview != null && preview.shareNode != null) {
            try { preview.shareNode.recycle(); } catch (Exception ignored) {}
            preview.shareNode = null;
        }
    }

    private String shortTitle(String title) {
        if (title == null) return "Amazon item";
        String t = title.trim();
        return t.length() <= 45 ? t : t.substring(0, 42).trim() + "…";
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
        itemGeneration++;
        final int generation = itemGeneration;
        workingIndex = index;
        scrollAttempts = 0;
        clickedRequest = false;
        requestClickedAt = 0L;
        advanceScheduled = false;
        openedAt = System.currentTimeMillis();
        // Amazon can briefly leave the previous product's accessibility tree in place
        // while a new deep link is loading. Do not inspect it until the new page settles.
        settleUntil = openedAt + 2300L;
        QueueStore.setCurrentIndex(this, index);
        updateOverlay("Opening item " + (index + 1) + "…");
        openAmazon(item.url);
        handler.postDelayed(() -> {
            if (generation == itemGeneration && QueueStore.isRunning(this)) scheduleProcess(0);
        }, 2400);
    }

    private void processCurrentScreen() {
        if (!QueueStore.isRunning(this) || QueueStore.isPaused(this)) return;
        if (advanceScheduled) return;
        long now = System.currentTimeMillis();
        if (now < settleUntil) {
            scheduleProcess(Math.max(200L, settleUntil - now + 80L));
            return;
        }
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

        if (scan.productTitle != null && !scan.productTitle.isEmpty()) {
            ProductItem current = items.get(index);
            if (isPlaceholderLabel(current.label)) {
                current.label = scan.productTitle;
                QueueStore.save(this, items);
                notifyChanged();
            }
        }

        // Strong terminal states are checked before availability. This prevents an
        // already-purchased/requested page from being misread because Amazon keeps
        // other hidden or stale accessibility text around during navigation.
        if (scan.purchased) {
            markAndAdvance(ProductItem.Status.PURCHASED, "Already purchased");
            recycleScan(scan);
            root.recycle();
            return;
        }

        // A requested page contains the sentence "If invited to purchase...".
        // Always give the explicit requested state priority over any availability wording.
        if (scan.requested) {
            markAndAdvance(ProductItem.Status.REQUESTED, "Invitation already requested");
            recycleScan(scan);
            root.recycle();
            return;
        }

        if (scan.thirdParty) {
            String seller = scan.sellerName == null || scan.sellerName.isEmpty() ? "Third-party seller" : scan.sellerName;
            markAndAdvance(ProductItem.Status.OTHER_SELLER, "Other seller — skipping", seller);
            recycleScan(scan);
            root.recycle();
            return;
        }

        // A normal Add to basket / Buy Now page is not an invitation state. If Amazon
        // does not expose the seller name, record it after a couple of passes rather than
        // scrolling indefinitely.
        if (scan.normalPurchase && scrollAttempts >= 2) {
            markAndAdvance(ProductItem.Status.NO_INVITE_CONTROL, "Normal sale — no invitation control", "Normal sale");
            recycleScan(scan);
            root.recycle();
            return;
        }

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
            recycleScan(scan);
            root.recycle();
            return;
        }

        if (scan.requestNode != null) {
            if (!QueueStore.autoRequest(this)) {
                updateOverlay("Request invite found — auto request is off");
                recycleScan(scan);
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
                recycleScan(scan);
                root.recycle();
                return;
            }

            if (requestClickedAt > 0 && now - requestClickedAt > 5500) {
                markAndAdvance(ProductItem.Status.ERROR, "Request button did not confirm");
            } else {
                scheduleProcess(800);
            }
            recycleScan(scan);
            root.recycle();
            return;
        }

        recycleScan(scan);
        if (scrollAttempts < MAX_SCROLLS) {
            scrollAttempts++;
            updateOverlay("Scanning… scroll " + scrollAttempts + "/" + MAX_SCROLLS);
            boolean scrolled = scrollForward(root);
            if (!scrolled) performSwipeUp();
            handler.postDelayed(() -> scheduleProcess(0), 800);
        } else {
            markAndAdvance(ProductItem.Status.NO_INVITE_CONTROL, "No invitation control found");
        }
        root.recycle();
    }

    private ScanResult scanTree(AccessibilityNodeInfo root) {
        ScanResult result = new ScanResult();
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(root));

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int bestTitleScore = Integer.MIN_VALUE;

        while (!q.isEmpty()) {
            AccessibilityNodeInfo n = q.removeFirst();
            String text = combinedText(n).toLowerCase(Locale.ROOT);

            boolean visible = isVisibleOnScreen(n);
            if (visible) {
                if (containsAny(text, "add to basket", "buy now")) {
                    result.normalPurchase = true;
                }
                if (text.contains("shipper / seller") || text.contains("shipper/seller")) {
                    String seller = extractSellerFromNode(n);
                    if (seller != null && !seller.isEmpty()) result.sellerName = seller;
                }

                if (containsAny(text,
                        "thanks for shopping with us",
                        "you purchased this item",
                        "you last purchased this item",
                        "you bought this item")) {
                    result.purchased = true;
                }

                // Only strong, account-specific wording counts as AVAILABLE.
                // Do not match generic "invited to purchase" because the requested state
                // itself says "If invited to purchase...".
                if (containsAny(text,
                        "available for you to buy",
                        "available for you to purchase",
                        "you have been invited to purchase",
                        "your invitation is ready",
                        "congratulations, you're invited",
                        "congratulations, you’re invited")) {
                    result.available = true;
                }
                if (containsAny(text,
                        "invitation requested",
                        "invitation requested, thanks",
                        "you'll get an email with a link that's valid for 72 hours",
                        "you’ll get an email with a link that’s valid for 72 hours")) {
                    result.requested = true;
                }
                if (result.requestNode == null && containsAny(text, "request invite", "request invitation")) {
                    result.requestNode = AccessibilityNodeInfo.obtain(n);
                }
            }

            if (visible && scrollAttempts <= 2 && n.getText() != null) {
                String raw = n.getText().toString().replace('\n', ' ').replaceAll("\\s+", " " ).trim();
                if (looksLikeProductTitle(raw)) {
                    Rect bounds = new Rect();
                    n.getBoundsInScreen(bounds);
                    int score = titleScore(raw, bounds, screenHeight);
                    if (score > bestTitleScore) {
                        bestTitleScore = score;
                        result.productTitle = raw.length() > 180 ? raw.substring(0, 180).trim() : raw;
                    }
                }
            }

            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
            n.recycle();
        }
        if (result.normalPurchase && result.sellerName != null && !result.sellerName.isEmpty() &&
                !result.sellerName.equalsIgnoreCase("Amazon") && !result.sellerName.equalsIgnoreCase("Amazon.co.uk")) {
            result.thirdParty = true;
        }
        return result;
    }

    private String extractSellerFromNode(AccessibilityNodeInfo node) {
        String raw = subtreeText(node, 24);
        String seller = parseSeller(raw);
        if (!seller.isEmpty()) return seller;
        AccessibilityNodeInfo parent = node == null ? null : node.getParent();
        if (parent != null) {
            seller = parseSeller(subtreeText(parent, 40));
            parent.recycle();
        }
        return seller;
    }

    private String subtreeText(AccessibilityNodeInfo node, int maxNodes) {
        if (node == null) return "";
        StringBuilder b = new StringBuilder();
        Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(AccessibilityNodeInfo.obtain(node));
        int count = 0;
        while (!q.isEmpty() && count++ < maxNodes) {
            AccessibilityNodeInfo n = q.removeFirst();
            String t = combinedText(n).replace('\n', ' ').replaceAll("\\s+", " ").trim();
            if (!t.isEmpty()) b.append(t).append(' ');
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo child = n.getChild(i);
                if (child != null) q.addLast(child);
            }
            n.recycle();
        }
        while (!q.isEmpty()) q.removeFirst().recycle();
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    private String parseSeller(String raw) {
        if (raw == null) return "";
        String lower = raw.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("shipper / seller");
        int markerLen = "shipper / seller".length();
        if (idx < 0) {
            idx = lower.indexOf("shipper/seller");
            markerLen = "shipper/seller".length();
        }
        if (idx < 0) return "";
        String tail = raw.substring(Math.min(raw.length(), idx + markerLen)).trim();
        tail = tail.replaceFirst("^[\\s:–—-]+", "");
        String tailLower = tail.toLowerCase(Locale.ROOT);
        int cut = tail.length();
        for (String stop : new String[]{" returns ", " payment ", " secure transaction", " product safety", " save this item"}) {
            int at = tailLower.indexOf(stop);
            if (at >= 0 && at < cut) cut = at;
        }
        tail = tail.substring(0, cut).replaceAll("\\s+", " ").trim();
        if (tail.length() > 80) tail = tail.substring(0, 80).trim();
        return tail;
    }

    private boolean looksLikeProductTitle(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        if (s.length() < 14 || s.length() > 240) return false;
        String l = s.toLowerCase(Locale.ROOT);
        if (containsAny(l,
                "search or ask", "visit the store", "sponsored", "bought in past",
                "free returns", "available by invitation", "request invite",
                "invitation requested", "save this item", "add to list",
                "product safety", "secure transaction", "returnable within",
                "shipper / seller", "qualifying items", "customers say", "see all details",
                "share this product with friends", "terms", "prime")) return false;
        if (s.contains("£") || s.matches("^[0-9.,%+\\- ]+$")) return false;
        return true;
    }

    private int titleScore(String raw, Rect bounds, int screenHeight) {
        int score = 0;
        int top = Math.max(0, bounds.top);
        float y = screenHeight <= 0 ? 0.5f : (float) top / (float) screenHeight;
        if (y >= 0.14f && y <= 0.48f) score += 8;
        else if (y >= 0.08f && y <= 0.60f) score += 3;
        if (raw.length() >= 24 && raw.length() <= 170) score += 4;
        if (raw.toLowerCase(Locale.ROOT).contains("pokémon") || raw.toLowerCase(Locale.ROOT).contains("pokemon")) score += 2;
        if (raw.contains(":") || raw.contains("—") || raw.contains("-")) score += 1;
        return score;
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

    private boolean isVisibleOnScreen(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) return false;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        if (r.isEmpty()) return false;
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        return r.right > 0 && r.left < w && r.bottom > 0 && r.top < h;
    }

    private boolean isPlaceholderLabel(String label) {
        if (label == null) return true;
        String s = label.trim();
        return s.isEmpty() || s.equalsIgnoreCase("Share Item") || s.equalsIgnoreCase("Share") ||
                s.equalsIgnoreCase("Amazon item") || s.startsWith("Amazon item —");
    }

    private void recycleScan(ScanResult scan) {
        if (scan != null && scan.requestNode != null) {
            try { scan.requestNode.recycle(); } catch (Exception ignored) {}
            scan.requestNode = null;
        }
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
        markAndAdvance(status, overlayMessage, "");
    }

    private void markAndAdvance(ProductItem.Status status, String overlayMessage, String note) {
        if (advanceScheduled) return;
        ArrayList<ProductItem> items = QueueStore.load(this);
        int index = QueueStore.getCurrentIndex(this);
        if (index >= 0 && index < items.size()) mark(items, index, status, note);
        updateOverlay(overlayMessage);
        notifyChanged();
        advanceAfter(1000);
    }

    private void mark(ArrayList<ProductItem> items, int index, ProductItem.Status status) {
        mark(items, index, status, "");
    }

    private void mark(ArrayList<ProductItem> items, int index, ProductItem.Status status, String note) {
        ProductItem item = items.get(index);
        item.status = status;
        item.lastChecked = System.currentTimeMillis();
        item.note = note == null ? "" : note.trim();
        QueueStore.save(this, items);
    }

    private void advanceAfter(long delay) {
        if (advanceScheduled) return;
        advanceScheduled = true;
        final int expectedIndex = QueueStore.getCurrentIndex(this);
        final int generation = itemGeneration;
        handler.postDelayed(() -> {
            if (!QueueStore.isRunning(this) || QueueStore.isPaused(this)) {
                advanceScheduled = false;
                return;
            }
            // Ignore duplicate/stale callbacks from the previous product. These were
            // the main cause of every-other-item style queue skipping.
            if (generation != itemGeneration || QueueStore.getCurrentIndex(this) != expectedIndex) {
                advanceScheduled = false;
                return;
            }
            ArrayList<ProductItem> items = QueueStore.load(this);
            int next = expectedIndex + 1;
            if (next >= items.size()) {
                advanceScheduled = false;
                finishRun("Queue complete");
                return;
            }
            QueueStore.setCurrentIndex(this, next);
            notifyChanged();
            performGlobalAction(GLOBAL_ACTION_BACK);
            handler.postDelayed(() -> {
                ArrayList<ProductItem> latest = QueueStore.load(this);
                if (QueueStore.isRunning(this) && next < latest.size()) beginItem(next, latest.get(next));
            }, 700);
        }, delay);
    }

    private void finishRun(String message) {
        QueueStore.setRunning(this, false);
        QueueStore.setPaused(this, false);
        advanceScheduled = false;
        updateOverlay(message);
        notifyChanged();
        handler.postDelayed(this::returnToHelper, 450);
        handler.postDelayed(this::hideOverlay, 1800);
    }

    private void returnToHelper() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        } catch (Exception ignored) {}
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

    private static final class QuickAddPreview {
        boolean isPreview = false;
        boolean hasDetailsButton = false;
        boolean hasPreviewMarker = false;
        String productTitle = "";
        AccessibilityNodeInfo shareNode = null;
    }

    private static final class ScanResult {
        boolean available = false;
        boolean requested = false;
        boolean purchased = false;
        boolean normalPurchase = false;
        boolean thirdParty = false;
        String sellerName = "";
        String productTitle = "";
        AccessibilityNodeInfo requestNode = null;
    }
}
