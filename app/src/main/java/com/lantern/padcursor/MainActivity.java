package com.lantern.padcursor;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS = "padcursor";

    private SharedPreferences prefs;
    private TextView statusView;
    private TextView speedLabel;
    private TextView deadzoneLabel;
    private TextView sizeLabel;
    private SeekBar speedSeek;
    private SeekBar deadzoneSeek;
    private SeekBar sizeSeek;
    private CheckBox rightStickCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), dp(24), dp(36), dp(36));
        root.setBackgroundColor(Color.rgb(245, 245, 245));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("PadCursor TV", 30, true);
        root.addView(title);

        statusView = text("", 20, true);
        statusView.setPadding(0, dp(12), 0, dp(16));
        root.addView(statusView);

        TextView desc = text(
                "Android 9 电视用手柄鼠标。\n\n" +
                "默认映射：\n" +
                "• 左摇杆：移动光标（可改右摇杆）\n" +
                "• A：单击    X：长按\n" +
                "• B：返回    Y：主页\n" +
                "• LB / RB：向上 / 向下滚动\n" +
                "• L3 / R3：光标回到屏幕中心\n" +
                "• START + SELECT：鼠标模式 ↔ 手柄直通模式\n\n" +
                "进入 Moonlight 前按 START+SELECT 切到“手柄直通”，游戏即可直接收到手柄。",
                18, false);
        desc.setPadding(0, 0, 0, dp(18));
        root.addView(desc);

        Button accessibility = button("打开电视辅助功能设置");
        accessibility.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开辅助功能设置", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(accessibility, lp());

        Button enableMouse = button("开启鼠标模式");
        enableMouse.setOnClickListener(v -> {
            GamepadMouseService s = GamepadMouseService.getInstance();
            if (s == null) {
                Toast.makeText(this, "请先启用 PadCursor 辅助功能服务", Toast.LENGTH_SHORT).show();
            } else {
                s.setMouseMode(true);
                updateStatus();
            }
        });
        root.addView(enableMouse, lp());

        Button passthrough = button("切换到手柄直通（Moonlight）");
        passthrough.setOnClickListener(v -> {
            GamepadMouseService s = GamepadMouseService.getInstance();
            if (s == null) {
                Toast.makeText(this, "辅助功能服务未运行", Toast.LENGTH_SHORT).show();
            } else {
                s.setMouseMode(false);
                updateStatus();
            }
        });
        root.addView(passthrough, lp());

        rightStickCheck = new CheckBox(this);
        rightStickCheck.setText("使用右摇杆控制光标（不勾选=左摇杆）");
        rightStickCheck.setTextSize(18);
        rightStickCheck.setChecked(prefs.getBoolean("right_stick", false));
        rightStickCheck.setPadding(0, dp(14), 0, dp(10));
        root.addView(rightStickCheck);

        speedSeek = new SeekBar(this);
        speedSeek.setMax(1800);
        speedSeek.setProgress(Math.max(200, prefs.getInt("speed", 1050)));
        speedLabel = text("", 18, false);
        root.addView(speedLabel);
        root.addView(speedSeek);

        deadzoneSeek = new SeekBar(this);
        deadzoneSeek.setMax(35);
        deadzoneSeek.setProgress(prefs.getInt("deadzone", 16));
        deadzoneLabel = text("", 18, false);
        root.addView(deadzoneLabel);
        root.addView(deadzoneSeek);

        sizeSeek = new SeekBar(this);
        sizeSeek.setMax(48);
        sizeSeek.setProgress(Math.max(8, prefs.getInt("cursor_size", 18)));
        sizeLabel = text("", 18, false);
        root.addView(sizeLabel);
        root.addView(sizeSeek);

        SeekBar.OnSeekBarChangeListener labels = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateLabels(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
        speedSeek.setOnSeekBarChangeListener(labels);
        deadzoneSeek.setOnSeekBarChangeListener(labels);
        sizeSeek.setOnSeekBarChangeListener(labels);
        updateLabels();

        Button save = button("保存并立即应用");
        save.setOnClickListener(v -> {
            int speed = Math.max(200, speedSeek.getProgress());
            int deadzone = Math.max(5, deadzoneSeek.getProgress());
            int cursorSize = Math.max(8, sizeSeek.getProgress());
            prefs.edit()
                    .putInt("speed", speed)
                    .putInt("deadzone", deadzone)
                    .putInt("cursor_size", cursorSize)
                    .putBoolean("right_stick", rightStickCheck.isChecked())
                    .apply();
            GamepadMouseService s = GamepadMouseService.getInstance();
            if (s != null) s.applySettings();
            Toast.makeText(this, "设置已应用", Toast.LENGTH_SHORT).show();
        });
        root.addView(save, lp());

        TextView note = text(
                "兼容性说明：该版本专门以 Android 9 / API 28 为最低版本。" +
                "它使用“可聚焦的辅助功能透明层”接收手柄摇杆，再通过 Accessibility dispatchGesture 模拟触摸。" +
                "少数电视固件可能不把摇杆 MotionEvent 发送给这种覆盖层；若出现“按键有效但摇杆完全没反应”，需要针对该电视机型继续做输入层兼容。",
                16, false);
        note.setPadding(0, dp(22), 0, dp(12));
        root.addView(note);

        return scroll;
    }

    private void updateLabels() {
        int speed = speedSeek == null ? 1050 : Math.max(200, speedSeek.getProgress());
        int dz = deadzoneSeek == null ? 16 : Math.max(5, deadzoneSeek.getProgress());
        int size = sizeSeek == null ? 18 : Math.max(8, sizeSeek.getProgress());
        if (speedLabel != null) speedLabel.setText("光标最大速度：" + speed + " dp/s");
        if (deadzoneLabel != null) deadzoneLabel.setText("摇杆死区：" + dz + "%");
        if (sizeLabel != null) sizeLabel.setText("光标大小：" + size + " dp");
    }

    private void updateStatus() {
        boolean enabled = isAccessibilityServiceEnabled(this, GamepadMouseService.class);
        GamepadMouseService s = GamepadMouseService.getInstance();
        String state;
        if (!enabled) {
            state = "状态：辅助功能服务未启用";
        } else if (s == null) {
            state = "状态：辅助功能已启用，等待服务启动";
        } else if (s.isMouseMode()) {
            state = "状态：鼠标模式 ON";
        } else {
            state = "状态：手柄直通 ON（Moonlight 模式）";
        }
        if (statusView != null) statusView.setText(state);
    }

    private static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        int enabled = 0;
        try {
            enabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException ignored) {
        }
        if (enabled != 1) return false;

        String settingValue = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(settingValue)) return false;

        ComponentName wanted = new ComponentName(context, serviceClass);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(settingValue);
        while (splitter.hasNext()) {
            ComponentName component = ComponentName.unflattenFromString(splitter.next());
            if (wanted.equals(component)) return true;
        }
        return false;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(25, 25, 25));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        return b;
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(5));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
