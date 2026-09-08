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
import java.util.Set;

public class GamepadMouseService extends AccessibilityService {
    private static volatile GamepadMouseService instance;
    private WindowManager windowManager;
    private CursorOverlayView overlay;
    private boolean mouseMode;
    private boolean comboLatched;
    private boolean scrollGestureRunning;
    private float pendingScrollAxis;
    private long lastScrollStarted;
    private int lastKeyToken = KeyEvent.KEYCODE_UNKNOWN;
    private int lastKeyAction = -1;
    private long lastKeyHandledAt;
    private final Set<Integer> downKeys = new HashSet<>();
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
        if (mouseMode) showOverlay();
        toast("PadCursor 已启动；默认 START + SELECT 切换模式");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // No UI content is inspected.
    }

    @Override public void onInterrupt() {}

    @Override protected boolean onKeyEvent(KeyEvent event) {
        return handleControllerKey(event, false);
    }

    public boolean onOverlayKeyEvent(KeyEvent event) {
        if (!mouseMode || overlay == null) return false;
        return handleControllerKey(event, true);
    }

    private boolean handleControllerKey(KeyEvent event, boolean fromFocusedOverlay) {
        int code = event.getKeyCode();
        int token = Prefs.eventToken(code, event.getScanCode());
        MainActivity.reportKeyEvent(event, fromFocusedOverlay ? "OVERLAY" : "ACCESSIBILITY");

        // A number of Android TV builds deliver the same physical key through
        // both AccessibilityService and the focused accessibility overlay.
        // Dispatching the action twice cancels long-press/scroll gestures.
        long now = SystemClock.uptimeMillis();
        boolean duplicate = token == lastKeyToken
                && event.getAction() == lastKeyAction
                && now - lastKeyHandledAt < 45;
        lastKeyToken = token;
        lastKeyAction = event.getAction();
        lastKeyHandledAt = now;
        if (duplicate) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int duplicateAction = actionForKey(token);
                if (duplicateAction >= 0) {
                    MainActivity.reportAction(Prefs.ACTION_NAMES[duplicateAction], true, true);
                }
            }
            return mouseMode || MainActivity.isCapturingKey();
        }

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
        if (!fromFocusedOverlay && !gamepadSource
                && mappedAction < 0 && !comboKey && !legacyKey) return false;

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

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (mappedAction >= 0) {
                final int actionToRun = mappedAction;
                // Run after the key-filter callback returns. This avoids vendor
                // InputDispatcher implementations rejecting a global action or
                // injected gesture while the original key is still dispatching.
                mainHandler.post(() -> {
                    boolean accepted = performMappedAction(actionToRun);
                    MainActivity.reportAction(Prefs.ACTION_NAMES[actionToRun], accepted, false);
                });
            }
            else performLegacyFallback(code);
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

    private boolean performMappedAction(int action) {
        switch (action) {
            case 0: return tapAtCursor(55);
            case 1: return tapAtCursor(650);
            case 2: return performGlobalAction(GLOBAL_ACTION_BACK);
            case 3: return performGlobalAction(GLOBAL_ACTION_HOME);
            case 4: return scrollAtCursor(false);
            case 5: return scrollAtCursor(true);
            case 6:
                if (overlay == null) return false;
                overlay.centerCursor();
                return true;
            default: return false;
        }
    }

    private void performLegacyFallback(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                tapAtCursor(55);
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
        scrollGestureRunning = false;
        if (enabled) {
            showOverlay();
            toast("鼠标模式：开");
        } else {
            hideOverlay();
            toast("手柄直通：开（适合 Moonlight）");
        }
    }

    public boolean isMouseMode() { return mouseMode; }

    public void applySettings() {
        if (overlay != null) overlay.reloadSettings();
    }

    public void onRightStickScroll(float rawAxis) {
        pendingScrollAxis = rawAxis;
        if (!mouseMode || overlay == null || scrollGestureRunning) return;
        float value = applyScrollCurve(rawAxis);
        if (value == 0f) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastScrollStarted < 50) return;
        float delta = -Prefs.scrollSpeed(this) * value * 0.05f;
        if (Math.abs(delta) < 10f) delta = Math.copySign(10f, delta);
        startScrollGesture(delta);
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

    private void startScrollGesture(float deltaY) {
        if (overlay == null) return;
        float x = overlay.getCursorX();
        float startY = Math.max(100f, Math.min(overlay.getHeight() - 100f, overlay.getCursorY()));
        float endY = Math.max(35f, Math.min(overlay.getHeight() - 35f, startY + deltaY));
        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 45)).build();
        scrollGestureRunning = true;
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                scrollGestureRunning = false;
                onRightStickScroll(pendingScrollAxis);
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                scrollGestureRunning = false;
            }
        }, null);
        if (!accepted) scrollGestureRunning = false;
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

    private boolean tapAtCursor(long durationMs) {
        if (overlay == null) return false;
        Path path = new Path();
        path.moveTo(overlay.getCursorX(), overlay.getCursorY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs)).build();
        return dispatchGesture(gesture, null, null);
    }

    private boolean scrollAtCursor(boolean down) {
        if (overlay == null) return false;
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
        return dispatchGesture(gesture, null, null);
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
