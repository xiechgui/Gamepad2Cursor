package com.lantern.padcursor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;

public class GamepadMouseService extends AccessibilityService implements CursorEventHost {
    private static volatile GamepadMouseService instance;
    private WindowManager windowManager;
    private CursorOverlayView overlay;
    private boolean mouseMode;
    private boolean comboLatched;
    private boolean gestureInFlight;
    private float pendingScrollAxis;
    private long lastScrollStarted;
    private final Set<Integer> downKeys = new HashSet<>();
    private final Set<Integer> heldActionKeys = new HashSet<>();
    private final Queue<Integer> actionQueue = new ArrayDeque<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static GamepadMouseService getInstance() { return instance; }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);
        SharedPreferences p = Prefs.get(this);
        mouseMode = p.getBoolean("mouse_mode", true);
        if (mouseMode && !Prefs.usesShizuku(this)) showOverlay();
        toast("PadCursor 已启动；默认 START + SELECT 切换模式");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // No UI content is inspected.
    }

    @Override public void onInterrupt() {}

    @Override protected boolean onKeyEvent(KeyEvent event) {
        return handleControllerKey(event);
    }

    private boolean handleControllerKey(KeyEvent event) {
        if (Prefs.usesShizuku(this)) return false;
        int code = event.getKeyCode();
        int token = Prefs.eventToken(code, event.getScanCode());
        MainActivity.reportKeyEvent(event, "ACCESSIBILITY");

        // Some Android TV firmwares report part of a controller as SOURCE_KEYBOARD.
        // Key capture therefore deliberately runs before source classification.
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
        boolean legacyKey = isLegacyFallbackKey(code);
        boolean gamepadSource = isGamepadEvent(event);

        // Accept configured/known controller key codes even when JUUI labels
        // their source as a keyboard. Leave unrelated remote/keyboard keys alone.
        if (!gamepadSource && mappedAction < 0 && !comboKey && !legacyKey) return false;

        if (event.getAction() == KeyEvent.ACTION_DOWN) downKeys.add(token);
        else if (event.getAction() == KeyEvent.ACTION_UP) downKeys.remove(token);

        boolean firstDown = downKeys.contains(comboFirst);
        boolean secondDown = downKeys.contains(comboSecond);
        if (firstDown && secondDown) comboLatched = true;

        boolean wasMouseMode = mouseMode;
        if (comboLatched && event.getAction() == KeyEvent.ACTION_UP
                && !firstDown && !secondDown) {
            comboLatched = false;
            setMouseMode(!mouseMode);
            return wasMouseMode;
        }

        if (!mouseMode) return false;

        if (comboLatched || comboKey) {
            return true;
        }

        if (mappedAction >= 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                heldActionKeys.add(token);
                MainActivity.reportAction(Prefs.ACTION_NAMES[mappedAction], "按下，等待松开");
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                heldActionKeys.remove(token);
                queueMappedAction(mappedAction);
            }
        } else if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            performLegacyFallback(code);
        }
        return true;
    }

    private int actionForKey(int code) {
        for (int i = 0; i < Prefs.ACTION_IDS.length; i++) {
            int configured = Prefs.actionKey(this, i);
            if (configured != KeyEvent.KEYCODE_UNKNOWN && configured == code) return i;
        }
        return -1;
    }

    private void queueMappedAction(int action) {
        actionQueue.offer(action);
        MainActivity.reportAction(Prefs.ACTION_NAMES[action], "已排队");
        mainHandler.post(this::drainActionQueue);
    }

    private void drainActionQueue() {
        if (!mouseMode || gestureInFlight || actionQueue.isEmpty()) return;
        int action = actionQueue.poll();
        switch (action) {
            case 0:
                dispatchTap(action, 55);
                return;
            case 1:
                dispatchTap(action, 700);
                return;
            case 2:
                dispatchGlobalAction(action, GLOBAL_ACTION_BACK);
                return;
            case 3:
                dispatchGlobalAction(action, GLOBAL_ACTION_HOME);
                return;
            case 4:
                dispatchScroll(action, false);
                return;
            case 5:
                dispatchScroll(action, true);
                return;
            case 6:
                boolean centered = overlay != null;
                if (centered) overlay.centerCursor();
                finishImmediateAction(action, centered);
                return;
            case 7:
                MainActivity.reportAction(Prefs.ACTION_NAMES[action], "仅 Shizuku 方案支持");
                mainHandler.post(this::drainActionQueue);
                return;
            default:
                finishImmediateAction(action, false);
        }
    }

    private void finishImmediateAction(int action, boolean accepted) {
        MainActivity.reportAction(Prefs.ACTION_NAMES[action], accepted ? "执行成功" : "执行失败");
        mainHandler.post(this::drainActionQueue);
    }

    private void dispatchGlobalAction(int mappedAction, int globalAction) {
        boolean accepted = performGlobalAction(globalAction);
        if (accepted) {
            finishImmediateAction(mappedAction, true);
        } else {
            mainHandler.postDelayed(() -> finishImmediateAction(
                    mappedAction, performGlobalAction(globalAction)), 32);
        }
    }

    private void performLegacyFallback(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                queueMappedAction(0);
                break;
            case KeyEvent.KEYCODE_BACK:
                performGlobalAction(GLOBAL_ACTION_BACK);
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                if (overlay != null && Prefs.actionKey(this, 6) != code) overlay.centerCursor();
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
        switch (code) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return false;
        }
    }

    private boolean isGamepadEvent(KeyEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    public void setMouseMode(boolean enabled) {
        mouseMode = enabled;
        Prefs.get(this).edit().putBoolean("mouse_mode", enabled).apply();
        pendingScrollAxis = 0f;
        gestureInFlight = false;
        heldActionKeys.clear();
        actionQueue.clear();
        if (enabled) {
            if (!Prefs.usesShizuku(this)) showOverlay();
            else hideOverlay();
            toast("鼠标模式：开");
        } else {
            hideOverlay();
            toast("手柄直通：开（适合 Moonlight）");
        }
    }

    public boolean isMouseMode() { return mouseMode; }

    public void applyBackendSelection() {
        if (Prefs.usesShizuku(this) || !mouseMode) hideOverlay();
        else showOverlay();
    }

    @Override public boolean onOverlayKeyEvent(KeyEvent event) {
        // Accessibility mode filters keys in onKeyEvent(); do not process them twice.
        return false;
    }

    public void applySettings() {
        if (overlay != null) overlay.reloadSettings();
    }

    public void onRightStickScroll(float rawAxis) {
        pendingScrollAxis = rawAxis;
        if (!mouseMode || overlay == null || gestureInFlight
                || !heldActionKeys.isEmpty() || !actionQueue.isEmpty()) return;
        float value = applyScrollCurve(rawAxis);
        if (value == 0f) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastScrollStarted < 50) return;
        float delta = -Prefs.scrollSpeed(this) * value * 0.05f;
        if (Math.abs(delta) < 10f) delta = Math.copySign(10f, delta);
        dispatchContinuousScroll(delta);
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

    private void dispatchContinuousScroll(float deltaY) {
        if (overlay == null) return;
        float x = overlay.getCursorX();
        float startY = Math.max(100f, Math.min(overlay.getHeight() - 100f, overlay.getCursorY()));
        float endY = Math.max(35f, Math.min(overlay.getHeight() - 35f, startY + deltaY));
        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 45)).build();
        dispatchExclusiveGesture(-1, gesture);
    }

    private void showOverlay() {
        if (overlay != null) {
            overlay.requestFocus();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new CursorOverlayView(this, this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("PadCursor TV Input Overlay");
        try {
            windowManager.addView(overlay, lp);
            overlay.postDelayed(overlay::requestFocus, 120);
        } catch (Exception e) {
            overlay = null;
            toast("无法创建光标层：" + e.getClass().getSimpleName());
        }
    }

    private void hideOverlay() {
        if (windowManager != null && overlay != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        overlay = null;
    }

    private void dispatchTap(int action, long durationMs) {
        if (overlay == null) {
            finishImmediateAction(action, false);
            return;
        }
        Path path = new Path();
        path.moveTo(overlay.getCursorX(), overlay.getCursorY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs)).build();
        dispatchExclusiveGesture(action, gesture);
    }

    private void dispatchScroll(int action, boolean down) {
        if (overlay == null) {
            finishImmediateAction(action, false);
            return;
        }
        float x = overlay.getCursorX();
        float y = overlay.getCursorY();
        float distance = Math.max(180f, overlay.getHeight() * 0.28f);
        float startY = down ? Math.min(overlay.getHeight() - 80f, y + distance / 2f)
                : Math.max(80f, y - distance / 2f);
        float endY = down ? Math.max(80f, y - distance / 2f)
                : Math.min(overlay.getHeight() - 80f, y + distance / 2f);
        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 280)).build();
        dispatchExclusiveGesture(action, gesture);
    }

    private void dispatchExclusiveGesture(int action, GestureDescription gesture) {
        if (gestureInFlight) {
            if (action >= 0) actionQueue.offer(action);
            return;
        }
        gestureInFlight = true;
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                finishGesture(action, true);
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                finishGesture(action, false);
            }
        }, null);
        if (!accepted) finishGesture(action, false);
    }

    private void finishGesture(int action, boolean completed) {
        gestureInFlight = false;
        if (action >= 0) {
            MainActivity.reportAction(Prefs.ACTION_NAMES[action],
                    completed ? "手势完成" : "手势被取消");
        }
        if (!actionQueue.isEmpty()) {
            mainHandler.post(this::drainActionQueue);
        } else if (heldActionKeys.isEmpty()) {
            mainHandler.post(() -> onRightStickScroll(pendingScrollAxis));
        }
    }

    private void toast(String text) {
        mainHandler.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    @Override public void onDestroy() {
        hideOverlay();
        mainHandler.removeCallbacksAndMessages(null);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
