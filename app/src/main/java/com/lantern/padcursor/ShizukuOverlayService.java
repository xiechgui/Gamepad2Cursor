package com.lantern.padcursor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public class ShizukuOverlayService extends Service implements CursorEventHost {
    private static final String CHANNEL_ID = "padcursor_shizuku";
    private static final int NOTIFICATION_ID = 41;
    private static volatile ShizukuOverlayService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Integer> downKeys = new HashSet<>();
    private final Set<Integer> heldActionKeys = new HashSet<>();
    private final Queue<Integer> actionQueue = new ArrayDeque<>();
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private CursorOverlayView overlay;
    private boolean mouseMode;
    private boolean comboLatched;
    private boolean actionInFlight;
    private float pendingScrollAxis;
    private long lastScrollStarted;

    static void start(Context context) {
        Context app = context.getApplicationContext();
        if (!Prefs.usesShizuku(app) || !Settings.canDrawOverlays(app) || !ShizukuBridge.isReady()) return;
        Intent intent = new Intent(app, ShizukuOverlayService.class);
        if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent);
        else app.startService(intent);
    }

    static void stop(Context context) {
        context.getApplicationContext().stopService(new Intent(context, ShizukuOverlayService.class));
    }

    static void stopIfRunning() {
        ShizukuOverlayService service = instance;
        if (service != null) service.stopSelf();
    }

    static ShizukuOverlayService getInstance() { return instance; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        ShizukuBridge.initialize(this);
        startForeground(NOTIFICATION_ID, createNotification());
        mouseMode = Prefs.get(this).getBoolean("mouse_mode", true);
        if (Prefs.usesShizuku(this) && mouseMode && ShizukuBridge.isReady()) showOverlay();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Prefs.usesShizuku(this) || !ShizukuBridge.isReady()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        mouseMode = Prefs.get(this).getBoolean("mouse_mode", true);
        if (mouseMode) showOverlay(); else hideOverlay();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private Notification createNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "PadCursor Shizuku 输入", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(com.lantern.padcursor.R.drawable.ic_padcursor)
                .setContentTitle("PadCursor TV")
                .setContentText("Shizuku 鼠标输入正在运行")
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    void setMouseMode(boolean enabled) {
        mouseMode = enabled;
        Prefs.get(this).edit().putBoolean("mouse_mode", enabled).apply();
        pendingScrollAxis = 0f;
        actionInFlight = false;
        heldActionKeys.clear();
        actionQueue.clear();
        if (enabled) {
            showOverlay();
            toast("鼠标模式：开（Shizuku）");
        } else {
            hideOverlay();
            toast("手柄直通：开（适合 Moonlight）");
        }
        MainActivity.refreshStatus();
    }

    boolean isMouseMode() { return mouseMode; }

    void applySettings() {
        if (overlay != null) overlay.reloadSettings();
    }

    @Override public boolean onOverlayKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        int token = Prefs.eventToken(code, event.getScanCode());
        MainActivity.reportKeyEvent(event, "SHIZUKU_OVERLAY");

        if (MainActivity.isCapturingKey()) {
            if (event.getAction() == KeyEvent.ACTION_UP && MainActivity.isCaptureReady()) {
                MainActivity.deliverCapturedKey(token);
            }
            return true;
        }

        int mappedAction = actionForKey(token);
        int comboFirst = Prefs.comboFirst(this);
        int comboSecond = Prefs.comboSecond(this);
        boolean comboKey = token == comboFirst || token == comboSecond;
        if (!isGamepadEvent(event) && mappedAction < 0 && !comboKey && !isLegacyFallbackKey(code)) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) downKeys.add(token);
        else if (event.getAction() == KeyEvent.ACTION_UP) downKeys.remove(token);

        boolean firstDown = downKeys.contains(comboFirst);
        boolean secondDown = downKeys.contains(comboSecond);
        if (firstDown && secondDown) comboLatched = true;
        if (comboLatched && event.getAction() == KeyEvent.ACTION_UP && !firstDown && !secondDown) {
            comboLatched = false;
            setMouseMode(false);
            return true;
        }
        if (comboLatched || comboKey) return true;

        if (mappedAction >= 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                heldActionKeys.add(token);
                MainActivity.reportAction(Prefs.ACTION_NAMES[mappedAction], "按下，等待松开");
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                heldActionKeys.remove(token);
                actionQueue.offer(mappedAction);
                MainActivity.reportAction(Prefs.ACTION_NAMES[mappedAction], "已排队");
                drainActionQueue();
            }
        } else if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            performLegacyFallback(code);
        }
        return true;
    }

    private int actionForKey(int token) {
        for (int i = 0; i < Prefs.ACTION_IDS.length; i++) {
            int configured = Prefs.actionKey(this, i);
            if (configured != KeyEvent.KEYCODE_UNKNOWN && configured == token) return i;
        }
        return -1;
    }

    private void drainActionQueue() {
        if (!mouseMode || actionInFlight || actionQueue.isEmpty()) return;
        int action = actionQueue.poll();
        if (overlay == null) {
            finishAction(action, false);
            return;
        }
        switch (action) {
            case 0:
                injectTap(action, 55);
                break;
            case 1:
                injectTap(action, 700);
                break;
            case 2:
                injectKeyWithFocusRelease(action, KeyEvent.KEYCODE_BACK);
                break;
            case 3:
                injectKeyWithFocusRelease(action, KeyEvent.KEYCODE_HOME);
                break;
            case 4:
                injectMappedScroll(action, false);
                break;
            case 5:
                injectMappedScroll(action, true);
                break;
            case 6:
                overlay.centerCursor();
                finishAction(action, true);
                break;
            case 7:
                injectKeyWithFocusRelease(action, KeyEvent.KEYCODE_MENU);
                break;
            default:
                finishAction(action, false);
        }
    }

    private void injectTap(int action, long durationMs) {
        actionInFlight = true;
        ShizukuBridge.injectTap(overlay.getCursorX(), overlay.getCursorY(), durationMs,
                success -> finishAction(action, success));
    }

    private void injectMappedScroll(int action, boolean down) {
        float x = overlay.getCursorX();
        float y = overlay.getCursorY();
        float distance = Math.max(180f, overlay.getHeight() * 0.28f);
        float startY = down ? Math.min(overlay.getHeight() - 80f, y + distance / 2f)
                : Math.max(80f, y - distance / 2f);
        float endY = down ? Math.max(80f, y - distance / 2f)
                : Math.min(overlay.getHeight() - 80f, y + distance / 2f);
        actionInFlight = true;
        ShizukuBridge.injectSwipe(x, startY, x, endY, 280,
                success -> finishAction(action, success));
    }

    private void injectKeyWithFocusRelease(int action, int keyCode) {
        actionInFlight = true;
        setOverlayFocusable(false);
        mainHandler.postDelayed(() -> ShizukuBridge.injectKey(keyCode, success ->
                mainHandler.postDelayed(() -> {
                    if (mouseMode) setOverlayFocusable(true);
                    finishAction(action, success);
                }, 100)), 80);
    }

    private void finishAction(int action, boolean success) {
        actionInFlight = false;
        MainActivity.reportAction(Prefs.ACTION_NAMES[action], success ? "执行成功" : "执行失败");
        if (!actionQueue.isEmpty()) mainHandler.post(this::drainActionQueue);
        else if (heldActionKeys.isEmpty()) mainHandler.post(() -> onRightStickScroll(pendingScrollAxis));
    }

    @Override public void onRightStickScroll(float rawAxis) {
        pendingScrollAxis = rawAxis;
        if (!mouseMode || overlay == null || actionInFlight
                || !heldActionKeys.isEmpty() || !actionQueue.isEmpty()) return;
        float value = applyScrollCurve(rawAxis);
        if (value == 0f) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastScrollStarted < 120) return;
        float delta = -Prefs.scrollSpeed(this) * value * 0.12f;
        if (Math.abs(delta) < 16f) delta = Math.copySign(16f, delta);
        float x = overlay.getCursorX();
        float startY = Math.max(100f, Math.min(overlay.getHeight() - 100f, overlay.getCursorY()));
        float endY = Math.max(35f, Math.min(overlay.getHeight() - 35f, startY + delta));
        actionInFlight = true;
        ShizukuBridge.injectSwipe(x, startY, x, endY, 90, success -> {
            actionInFlight = false;
            if (mouseMode) mainHandler.post(() -> onRightStickScroll(pendingScrollAxis));
        });
        lastScrollStarted = now;
    }

    private float applyScrollCurve(float value) {
        float deadzone = Prefs.scrollDeadzone(this);
        float abs = Math.abs(value);
        if (abs <= deadzone) return 0f;
        float normalized = Math.min(1f, (abs - deadzone) / Math.max(0.001f, 1f - deadzone));
        float curved = (float) Math.pow(normalized, 1f / Prefs.scrollSensitivity(this));
        return Math.copySign(curved, value);
    }

    private void performLegacyFallback(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                actionQueue.offer(0);
                drainActionQueue();
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (overlay != null) overlay.nudge(-1, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (overlay != null) overlay.nudge(1, 0);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (overlay != null) overlay.nudge(0, -1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (overlay != null) overlay.nudge(0, 1);
                break;
            default:
                break;
        }
    }

    private boolean isLegacyFallbackKey(int code) {
        return code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private boolean isGamepadEvent(KeyEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private void showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new CursorOverlayView(this, this);
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                baseOverlayFlags(), PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.setTitle("PadCursor TV Shizuku Input Overlay");
        try {
            windowManager.addView(overlay, overlayParams);
            overlay.postDelayed(overlay::requestFocus, 120);
        } catch (Exception e) {
            overlay = null;
            toast("无法创建 Shizuku 光标层：" + e.getClass().getSimpleName());
        }
    }

    private int baseOverlayFlags() {
        return WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    }

    private void setOverlayFocusable(boolean focusable) {
        if (overlay == null || windowManager == null || overlayParams == null) return;
        overlayParams.flags = baseOverlayFlags();
        if (!focusable) overlayParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try {
            windowManager.updateViewLayout(overlay, overlayParams);
            if (focusable) overlay.postDelayed(overlay::requestFocus, 40);
        } catch (Exception ignored) {}
    }

    private void hideOverlay() {
        if (windowManager != null && overlay != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        overlay = null;
        overlayParams = null;
    }

    private void toast(String message) {
        mainHandler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override public void onDestroy() {
        hideOverlay();
        mainHandler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
