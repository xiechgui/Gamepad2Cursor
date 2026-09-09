package com.lantern.padcursor;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

public class CursorOverlayView extends View {
    private static final String PREFS = "padcursor";

    private final CursorEventHost host;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cursorX;
    private float cursorY;
    private float axisX;
    private float axisY;
    private float scrollAxisY;
    private float maxSpeedPxPerSecond;
    private float deadzone;
    private float radiusPx;
    private boolean useRightStick;
    private long lastFrameNanos;

    private final Runnable frameTicker = new Runnable() {
        @Override
        public void run() {
            long now = System.nanoTime();
            if (lastFrameNanos == 0L) {
                lastFrameNanos = now;
            }
            float dt = Math.min(0.05f, (now - lastFrameNanos) / 1_000_000_000f);
            lastFrameNanos = now;

            float nx = curve(axisX);
            float ny = curve(axisY);
            if (Math.abs(nx) > 0f || Math.abs(ny) > 0f) {
                cursorX += nx * maxSpeedPxPerSecond * dt;
                cursorY += ny * maxSpeedPxPerSecond * dt;
                clampCursor();
                invalidate();
            }
            host.onRightStickScroll(useRightStick ? 0f : scrollAxisY);
            postOnAnimation(this);
        }
    };

    public CursorOverlayView(Context context, CursorEventHost host) {
        super(context);
        this.host = host;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(false);

        fillPaint.setColor(0xE6FFFFFF);
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setColor(0xFF111111);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2.2f));

        reloadSettings();
        post(frameTicker);
    }

    public void reloadSettings() {
        SharedPreferences p = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        maxSpeedPxPerSecond = p.getInt("speed", 1050) * getResources().getDisplayMetrics().density;
        deadzone = p.getInt("deadzone", 16) / 100f;
        radiusPx = p.getInt("cursor_size", 18) * getResources().getDisplayMetrics().density;
        useRightStick = p.getBoolean("right_stick", false);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(this::requestFocus, 80);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(frameTicker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (oldw == 0 || oldh == 0) {
            cursorX = w / 2f;
            cursorY = h / 2f;
        } else {
            cursorX = Math.min(w - radiusPx, Math.max(radiusPx, cursorX));
            cursorY = Math.min(h - radiusPx, Math.max(radiusPx, cursorY));
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int source = event.getSource();
        boolean joystick = (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
        boolean gamepad = (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
        if (!joystick && !gamepad) {
            return super.onGenericMotionEvent(event);
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            scrollAxisY = readRightStickY(event, source);
            if (useRightStick) {
                InputDevice device = event.getDevice();
                boolean hasRxRy = device != null
                        && device.getMotionRange(MotionEvent.AXIS_RX, source) != null
                        && device.getMotionRange(MotionEvent.AXIS_RY, source) != null;
                if (hasRxRy) {
                    axisX = event.getAxisValue(MotionEvent.AXIS_RX);
                    axisY = event.getAxisValue(MotionEvent.AXIS_RY);
                } else {
                    // Most Android/XInput controllers expose the right stick as Z/RZ.
                    axisX = event.getAxisValue(MotionEvent.AXIS_Z);
                    axisY = event.getAxisValue(MotionEvent.AXIS_RZ);
                }
            } else {
                axisX = event.getAxisValue(MotionEvent.AXIS_X);
                axisY = event.getAxisValue(MotionEvent.AXIS_Y);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return host.onOverlayKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    private float readRightStickY(MotionEvent event, int source) {
        InputDevice device = event.getDevice();
        float rz = 0f;
        float ry = 0f;
        if (device == null || device.getMotionRange(MotionEvent.AXIS_RZ, source) != null) {
            rz = event.getAxisValue(MotionEvent.AXIS_RZ);
        }
        if (device == null || device.getMotionRange(MotionEvent.AXIS_RY, source) != null) {
            ry = event.getAxisValue(MotionEvent.AXIS_RY);
        }
        return Math.abs(rz) >= Math.abs(ry) ? rz : ry;
    }

    private float curve(float value) {
        float abs = Math.abs(value);
        if (abs <= deadzone) return 0f;
        float normalized = (abs - deadzone) / Math.max(0.001f, 1f - deadzone);
        normalized = Math.min(1f, normalized);
        float curved = (float) Math.pow(normalized, 1.55);
        return Math.copySign(curved, value);
    }

    public void nudge(float dx, float dy) {
        cursorX += dx * dp(48);
        cursorY += dy * dp(48);
        clampCursor();
        invalidate();
    }

    public void centerCursor() {
        cursorX = getWidth() / 2f;
        cursorY = getHeight() / 2f;
        invalidate();
    }

    private void clampCursor() {
        float r = Math.max(radiusPx, dp(6));
        cursorX = Math.max(r, Math.min(Math.max(r, getWidth() - r), cursorX));
        cursorY = Math.max(r, Math.min(Math.max(r, getHeight() - r), cursorY));
    }

    public float getCursorX() {
        return cursorX;
    }

    public float getCursorY() {
        return cursorY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float r = radiusPx;
        canvas.drawCircle(cursorX, cursorY, r, fillPaint);
        canvas.drawCircle(cursorX, cursorY, r, strokePaint);
        canvas.drawLine(cursorX - r * 0.55f, cursorY, cursorX + r * 0.55f, cursorY, strokePaint);
        canvas.drawLine(cursorX, cursorY - r * 0.55f, cursorX, cursorY + r * 0.55f, strokePaint);
        canvas.drawCircle(cursorX, cursorY, dp(2.2f), strokePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
