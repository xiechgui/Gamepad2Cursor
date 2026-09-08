package com.lantern.padcursor;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static volatile MainActivity instance;
    private static volatile int captureAction = -1;
    private static volatile int captureCombo;
    private static volatile long captureReadyAt;

    private SharedPreferences prefs;
    private TextView statusView;
    private TextView speedLabel;
    private TextView deadzoneLabel;
    private TextView sizeLabel;
    private SeekBar speedSeek;
    private SeekBar deadzoneSeek;
    private SeekBar sizeSeek;
    private CheckBox rightStickCheck;
    private SeekBar scrollDeadzoneSeek;
    private SeekBar scrollSensitivitySeek;
    private SeekBar scrollSpeedSeek;
    private TextView scrollDeadzoneLabel;
    private TextView scrollSensitivityLabel;
    private TextView scrollSpeedLabel;
    private TextView inputMonitor;
    private Button[] mappingButtons;
    private Button[] comboButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        prefs = Prefs.get(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private View buildUi() {
        mappingButtons = new Button[Prefs.ACTION_IDS.length];
        comboButtons = new Button[2];
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), dp(24), dp(36), dp(36));
        root.setBackgroundColor(Color.rgb(245, 245, 245));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("PadCursor TV v0.3.3", 30, true);
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
                "• 右摇杆 Y 轴：连续滚动\n" +
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

        TextView scrollHeading = text("右摇杆连续滚动", 22, true);
        scrollHeading.setPadding(0, dp(22), 0, dp(8));
        root.addView(scrollHeading);

        scrollDeadzoneSeek = new SeekBar(this);
        scrollDeadzoneSeek.setMax(55);
        scrollDeadzoneSeek.setProgress(Math.max(5, prefs.getInt("scroll_deadzone", 22)) - 5);
        scrollDeadzoneLabel = text("", 18, false);
        root.addView(scrollDeadzoneLabel);
        root.addView(scrollDeadzoneSeek);

        scrollSensitivitySeek = new SeekBar(this);
        scrollSensitivitySeek.setMax(150);
        scrollSensitivitySeek.setProgress(Math.max(50, prefs.getInt("scroll_sensitivity", 100)) - 50);
        scrollSensitivityLabel = text("", 18, false);
        root.addView(scrollSensitivityLabel);
        root.addView(scrollSensitivitySeek);

        scrollSpeedSeek = new SeekBar(this);
        scrollSpeedSeek.setMax(2200);
        scrollSpeedSeek.setProgress(Math.max(200, prefs.getInt("scroll_speed", 900)) - 200);
        scrollSpeedLabel = text("", 18, false);
        root.addView(scrollSpeedLabel);
        root.addView(scrollSpeedSeek);

        TextView scrollNote = text(
                "支持常见的 RZ / RY 轴。若勾选“使用右摇杆控制光标”，为避免冲突，右摇杆滚动会自动暂停。",
                16, false);
        root.addView(scrollNote);

        SeekBar.OnSeekBarChangeListener labels = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateLabels(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
        speedSeek.setOnSeekBarChangeListener(labels);
        deadzoneSeek.setOnSeekBarChangeListener(labels);
        sizeSeek.setOnSeekBarChangeListener(labels);
        scrollDeadzoneSeek.setOnSeekBarChangeListener(labels);
        scrollSensitivitySeek.setOnSeekBarChangeListener(labels);
        scrollSpeedSeek.setOnSeekBarChangeListener(labels);
        updateLabels();

        Button save = button("保存并立即应用");
        save.setOnClickListener(v -> {
            int speed = Math.max(200, speedSeek.getProgress());
            int deadzone = Math.max(5, deadzoneSeek.getProgress());
            int cursorSize = Math.max(8, sizeSeek.getProgress());
            int scrollDeadzone = scrollDeadzoneSeek.getProgress() + 5;
            int scrollSensitivity = scrollSensitivitySeek.getProgress() + 50;
            int scrollSpeed = scrollSpeedSeek.getProgress() + 200;
            prefs.edit()
                    .putInt("speed", speed)
                    .putInt("deadzone", deadzone)
                    .putInt("cursor_size", cursorSize)
                    .putBoolean("right_stick", rightStickCheck.isChecked())
                    .putInt("scroll_deadzone", scrollDeadzone)
                    .putInt("scroll_sensitivity", scrollSensitivity)
                    .putInt("scroll_speed", scrollSpeed)
                    .apply();
            GamepadMouseService s = GamepadMouseService.getInstance();
            if (s != null) s.applySettings();
            Toast.makeText(this, "设置已应用", Toast.LENGTH_SHORT).show();
        });
        root.addView(save, lp());

        TextView mappingHeading = text("自定义按键映射", 22, true);
        mappingHeading.setPadding(0, dp(22), 0, dp(8));
        root.addView(mappingHeading);
        inputMonitor = text("最近按键：等待输入", 16, false);
        inputMonitor.setPadding(0, 0, 0, dp(8));
        root.addView(inputMonitor);
        for (int i = 0; i < Prefs.ACTION_IDS.length; i++) addMappingRow(root, i);

        TextView comboHeading = text("模式切换组合键", 22, true);
        comboHeading.setPadding(0, dp(20), 0, dp(8));
        root.addView(comboHeading);
        addComboRow(root, 1, "组合键 1", Prefs.comboFirst(this));
        addComboRow(root, 2, "组合键 2", Prefs.comboSecond(this));
        TextView comboNote = text("两个组合键全部释放后才切换模式，避免 Moonlight 出现按键卡住。", 16, false);
        root.addView(comboNote);

        Button resetMappings = button("恢复默认按键映射");
        resetMappings.setOnClickListener(v -> {
            Prefs.resetMappings(this);
            Toast.makeText(this, "已恢复默认按键映射", Toast.LENGTH_SHORT).show();
            for (int i = 0; i < mappingButtons.length; i++) {
                mappingButtons[i].setText(keyName(Prefs.actionKey(this, i)));
            }
            comboButtons[0].setText(keyName(Prefs.comboFirst(this)));
            comboButtons[1].setText(keyName(Prefs.comboSecond(this)));
        });
        root.addView(resetMappings, lp());

        TextView note = text(
                "兼容性说明：该版本专门以 Android 9 / API 28 为最低版本。" +
                "它使用“可聚焦的辅助功能透明层”接收手柄摇杆，再通过 Accessibility dispatchGesture 模拟触摸。" +
                "少数电视固件可能不把摇杆 MotionEvent 发送给这种覆盖层。按键识别同时兼容 GAMEPAD、JOYSTICK 和电视固件以 KEYBOARD 来源上报的已配置键码。",
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
        if (scrollDeadzoneLabel != null) {
            scrollDeadzoneLabel.setText("滚动死区：" + (scrollDeadzoneSeek.getProgress() + 5) + "%");
        }
        if (scrollSensitivityLabel != null) {
            scrollSensitivityLabel.setText("滚动灵敏度：" + (scrollSensitivitySeek.getProgress() + 50) + "%");
        }
        if (scrollSpeedLabel != null) {
            scrollSpeedLabel.setText("最大滚动速度：" + (scrollSpeedSeek.getProgress() + 200) + " px/s");
        }
    }

    private void addMappingRow(LinearLayout root, int index) {
        LinearLayout row = mappingRow();
        row.addView(rowLabel(Prefs.ACTION_NAMES[index]), new LinearLayout.LayoutParams(0, dp(54), 1));
        Button set = button(keyName(Prefs.actionKey(this, index)));
        mappingButtons[index] = set;
        set.setOnClickListener(v -> beginCapture(index, 0));
        row.addView(set, new LinearLayout.LayoutParams(dp(250), dp(50)));
        Button clear = button("清除");
        clear.setOnClickListener(v -> {
            prefs.edit().putInt("key_" + Prefs.ACTION_IDS[index], KeyEvent.KEYCODE_UNKNOWN).apply();
            mappingButtons[index].setText("未绑定");
        });
        row.addView(clear, new LinearLayout.LayoutParams(dp(100), dp(50)));
        root.addView(row);
    }

    private void addComboRow(LinearLayout root, int which, String label, int keyCode) {
        LinearLayout row = mappingRow();
        row.addView(rowLabel(label), new LinearLayout.LayoutParams(0, dp(54), 1));
        Button set = button(keyName(keyCode));
        comboButtons[which - 1] = set;
        set.setOnClickListener(v -> beginCapture(-1, which));
        row.addView(set, new LinearLayout.LayoutParams(dp(250), dp(50)));
        root.addView(row);
    }

    private LinearLayout mappingRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView rowLabel(String value) {
        TextView label = text(value, 17, false);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private String keyName(int code) {
        if (code == KeyEvent.KEYCODE_UNKNOWN) return "未绑定";
        return code < 0 ? "SCAN_" + (-code) : KeyEvent.keyCodeToString(code);
    }

    private void beginCapture(int action, int combo) {
        captureAction = action;
        captureCombo = combo;
        captureReadyAt = SystemClock.uptimeMillis() + 250;
        Toast.makeText(this, "请按一下要绑定的手柄实体按键", Toast.LENGTH_LONG).show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (isCapturingKey()) {
            reportKeyEvent(event, "ACTIVITY");
            if (event.getAction() == KeyEvent.ACTION_UP && isCaptureReady()) {
                finishCapture(Prefs.eventToken(event.getKeyCode(), event.getScanCode()));
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    static boolean isCapturingKey() {
        return captureAction >= 0 || captureCombo != 0;
    }

    static boolean isCaptureReady() {
        return SystemClock.uptimeMillis() >= captureReadyAt;
    }

    static void deliverCapturedKey(int keyToken) {
        MainActivity activity = instance;
        if (activity != null) activity.runOnUiThread(() -> activity.finishCapture(keyToken));
    }

    static void reportKeyEvent(KeyEvent event, String route) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return;
        MainActivity activity = instance;
        if (activity == null) return;
        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        int source = event.getSource();
        int deviceId = event.getDeviceId();
        activity.runOnUiThread(() -> {
            if (activity.inputMonitor != null) {
                activity.inputMonitor.setText("最近按键：" + KeyEvent.keyCodeToString(keyCode)
                        + "  keyCode=" + keyCode
                        + "  scanCode=" + scanCode
                        + "  source=0x" + Integer.toHexString(source)
                        + "  device=" + deviceId
                        + "  path=" + route);
            }
        });
    }

    private void finishCapture(int keyCode) {
        int action = captureAction;
        int combo = captureCombo;
        if (action < 0 && combo == 0) return;
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            Toast.makeText(this, "该输入没有可用的 keyCode 或 scanCode，请换一个按键", Toast.LENGTH_LONG).show();
            return;
        }
        if (hasConflict(keyCode, action, combo)) {
            Toast.makeText(this, "该按键已被其它动作或模式组合占用，请换一个键", Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences.Editor edit = prefs.edit();
        if (action >= 0) edit.putInt("key_" + Prefs.ACTION_IDS[action], keyCode);
        else edit.putInt(combo == 1 ? "combo_first" : "combo_second", keyCode);
        edit.apply();
        if (action >= 0 && mappingButtons != null) {
            mappingButtons[action].setText(keyName(keyCode));
        } else if (combo > 0 && comboButtons != null) {
            comboButtons[combo - 1].setText(keyName(keyCode));
        }
        captureAction = -1;
        captureCombo = 0;
        captureReadyAt = 0;
    }

    private boolean hasConflict(int keyCode, int ignoredAction, int ignoredCombo) {
        for (int i = 0; i < Prefs.ACTION_IDS.length; i++) {
            if (i != ignoredAction && Prefs.actionKey(this, i) == keyCode) return true;
        }
        if (ignoredCombo != 1 && Prefs.comboFirst(this) == keyCode) return true;
        return ignoredCombo != 2 && Prefs.comboSecond(this) == keyCode;
    }

    @Override protected void onDestroy() {
        if (instance == this) instance = null;
        captureAction = -1;
        captureCombo = 0;
        captureReadyAt = 0;
        super.onDestroy();
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
