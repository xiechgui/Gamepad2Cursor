package com.lantern.padcursor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class GamepadMouseService extends AccessibilityService {
    private static final String PREFS = "padcursor";
    private static volatile GamepadMouseService instance;

    private WindowManager windowManager;
    private CursorOverlayView overlay;
    private boolean mouseMode;

    private boolean startDown;
    private boolean selectDown;
    private boolean comboLatched;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static GamepadMouseService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;

        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        setServiceInfo(info);

        SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        mouseMode = p.getBoolean("mouse_mode", true);
        if (mouseMode) {
            showOverlay();
        }
        toast("PadCursor 已启动；START + SELECT 切换鼠标/手柄直通模式");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No UI inspection is required. We only use AccessibilityService for
        // global key filtering, overlay display, global actions and gestures.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!isGamepadEvent(event)) {
            return false;
        }

        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;

        if (code == KeyEvent.KEYCODE_BUTTON_START) {
            startDown = down;
        }
        if (code == KeyEvent.KEYCODE_BUTTON_SELECT || code == KeyEvent.KEYCODE_BACK) {
            selectDown = down;
        }

        if (startDown && selectDown && !comboLatched) {
            comboLatched = true;
            setMouseMode(!mouseMode);
            return true;
        }
        if (!startDown || !selectDown) {
            comboLatched = false;
        }

        if (!mouseMode) {
            // Pass all ordinary controller events to Moonlight/games.
            return false;
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) {
            return true;
        }

        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                tapAtCursor(55);
                return true;

            case KeyEvent.KEYCODE_BUTTON_X:
                tapAtCursor(650);
                return true;

            case KeyEvent.KEYCODE_BUTTON_B:
                performGlobalAction(GLOBAL_ACTION_BACK);
                return true;

            case KeyEvent.KEYCODE_BUTTON_Y:
                performGlobalAction(GLOBAL_ACTION_HOME);
                return true;

            case KeyEvent.KEYCODE_BUTTON_L1:
                scrollAtCursor(false);
                return true;

            case KeyEvent.KEYCODE_BUTTON_R1:
                scrollAtCursor(true);
                return true;

            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                if (overlay != null) overlay.centerCursor();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (overlay != null) overlay.nudge(-1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (overlay != null) overlay.nudge(1, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (overlay != null) overlay.nudge(0, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (overlay != null) overlay.nudge(0, 1);
                return true;
        }

        return true;
    }

    private boolean isGamepadEvent(KeyEvent event) {
        int source = event.getSource();
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    public void setMouseMode(boolean enabled) {
        mouseMode = enabled;
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean("mouse_mode", enabled).apply();

        if (enabled) {
            showOverlay();
            toast("鼠标模式：开");
        } else {
            hideOverlay();
            toast("手柄直通：开（适合 Moonlight）");
        }
    }

    public boolean isMouseMode() {
        return mouseMode;
    }

    public void applySettings() {
        if (overlay != null) {
            overlay.reloadSettings();
        }
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
            try {
                windowManager.removeView(overlay);
            } catch (Exception ignored) {
            }
        }
        overlay = null;
    }

    private void tapAtCursor(long durationMs) {
        if (overlay == null) return;
        float x = overlay.getCursorX();
        float y = overlay.getCursorY();

        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, durationMs);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();
        dispatchGesture(gesture, null, null);
    }

    private void scrollAtCursor(boolean down) {
        if (overlay == null) return;
        float x = overlay.getCursorX();
        float y = overlay.getCursorY();
        float distance = Math.max(180f, overlay.getHeight() * 0.28f);

        float startY;
        float endY;
        if (down) {
            // Finger swipe upward -> content scrolls down.
            startY = Math.min(overlay.getHeight() - 80f, y + distance / 2f);
            endY = Math.max(80f, y - distance / 2f);
        } else {
            startY = Math.max(80f, y - distance / 2f);
            endY = Math.min(overlay.getHeight() - 80f, y + distance / 2f);
        }

        Path path = new Path();
        path.moveTo(x, startY);
        path.lineTo(x, endY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 280))
                .build();
        dispatchGesture(gesture, null, null);
    }

    private void toast(String text) {
        mainHandler.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroy() {
        hideOverlay();
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
