package com.lantern.padcursor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
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

import rikka.shizuku.Shizuku;

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
    private TextView actionMonitor;
    private Button[] mappingButtons;
    private Button[] comboButtons;
    private TextView backendLabel;
    private Button accessibilityBackendButton;
    private Button shizukuBackendButton;
    private Button configureBackendButton;
    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != ShizukuBridge.PERMISSION_REQUEST) return;
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    ShizukuBridge.ensureBound();
                    Toast.makeText(this, "Shizuku 已授权，请继续配置悬浮层权限", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Shizuku 授权被拒绝", Toast.LENGTH_SHORT).show();
                }
                updateStatus();
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        prefs = Prefs.get(this);
        ShizukuBridge.initialize(this);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Prefs.usesShizuku(this) && ShizukuBridge.isReady()
                && Settings.canDrawOverlays(this)
                && prefs.getBoolean("mouse_mode", true)) {
            ShizukuOverlayService.start(this);
        }
        updateStatus();
    }

    private View buildUi() {
        mappingButtons = new Button[Prefs.ACTION_IDS.length];
        comboButtons = new Button[2];
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setBackgroundColor(Color.rgb(245, 245, 245));

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.setFillViewport(true);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setPadding(dp(18), dp(14), dp(18), dp(18));
        leftScroll.addView(left, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        ScrollView rightScroll = new ScrollView(this);
        rightScroll.setFillViewport(true);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(18), dp(14), dp(18), dp(18));
        rightScroll.addView(right, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        page.addView(leftScroll, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 9f));
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(205, 205, 205));
        page.addView(divider, new LinearLayout.LayoutParams(dp(1),
                LinearLayout.LayoutParams.MATCH_PARENT));
        page.addView(rightScroll, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 11f));

        LinearLayout statusRow = mappingRow();
        statusView = text("", 18, true);
        statusView.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.addView(statusView, new LinearLayout.LayoutParams(0, dp(46), 1f));
        Button about = button("关于");
        about.setTextSize(16);
        about.setOnClickListener(v -> showAbout());
        statusRow.addView(about, new LinearLayout.LayoutParams(dp(100), dp(44)));
        left.addView(statusRow);

        LinearLayout backendRow = mappingRow();
        backendLabel = text("输入方案", 16, true);
        backendLabel.setGravity(Gravity.CENTER_VERTICAL);
        backendRow.addView(backendLabel, new LinearLayout.LayoutParams(0, dp(44), 0.72f));
        accessibilityBackendButton = button("辅助功能");
        accessibilityBackendButton.setTextSize(15);
        accessibilityBackendButton.setOnClickListener(v -> selectBackend(Prefs.BACKEND_ACCESSIBILITY));
        backendRow.addView(accessibilityBackendButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        shizukuBackendButton = button("Shizuku");
        shizukuBackendButton.setTextSize(15);
        shizukuBackendButton.setOnClickListener(v -> selectBackend(Prefs.BACKEND_SHIZUKU));
        backendRow.addView(shizukuBackendButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        left.addView(backendRow);

        configureBackendButton = button("配置当前输入方案");
        configureBackendButton.setOnClickListener(v -> configureCurrentBackend());
        configureBackendButton.setTextSize(16);
        left.addView(configureBackendButton, compactLp());

        Button enableMouse = button("开启鼠标模式");
        enableMouse.setOnClickListener(v -> {
            if (Prefs.usesShizuku(this)) {
                if (!ShizukuBridge.isReady() || !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "请先配置并授权 Shizuku 输入方案", Toast.LENGTH_SHORT).show();
                    return;
                }
                prefs.edit().putBoolean("mouse_mode", true).apply();
                ShizukuOverlayService service = ShizukuOverlayService.getInstance();
                if (service != null) service.setMouseMode(true);
                else ShizukuOverlayService.start(this);
            } else {
                GamepadMouseService s = GamepadMouseService.getInstance();
                if (s == null) {
                    Toast.makeText(this, "请先启用 PadCursor 辅助功能服务", Toast.LENGTH_SHORT).show();
                } else {
                    s.setMouseMode(true);
                }
            }
            updateStatus();
        });
        enableMouse.setTextSize(16);

        Button passthrough = button("切换到手柄直通（Moonlight）");
        passthrough.setOnClickListener(v -> {
            if (Prefs.usesShizuku(this)) {
                prefs.edit().putBoolean("mouse_mode", false).apply();
                ShizukuOverlayService service = ShizukuOverlayService.getInstance();
                if (service != null) service.setMouseMode(false);
            } else {
                GamepadMouseService s = GamepadMouseService.getInstance();
                if (s == null) {
                    Toast.makeText(this, "辅助功能服务未运行", Toast.LENGTH_SHORT).show();
                } else {
                    s.setMouseMode(false);
                }
            }
            updateStatus();
        });
        passthrough.setTextSize(16);
        LinearLayout modeRow = mappingRow();
        modeRow.addView(enableMouse, new LinearLayout.LayoutParams(0, dp(46), 1f));
        modeRow.addView(passthrough, new LinearLayout.LayoutParams(0, dp(46), 1.35f));
        left.addView(modeRow);

        rightStickCheck = new CheckBox(this);
        rightStickCheck.setText("使用右摇杆控制光标（不勾选=左摇杆）");
        rightStickCheck.setTextSize(16);
        rightStickCheck.setChecked(prefs.getBoolean("right_stick", false));
        rightStickCheck.setPadding(0, dp(3), 0, dp(3));
        left.addView(rightStickCheck);

        speedSeek = new SeekBar(this);
        speedSeek.setMax(1800);
        speedSeek.setProgress(Math.max(200, prefs.getInt("speed", 1050)));
        speedLabel = text("", 16, false);
        addSliderRow(left, speedLabel, speedSeek);

        deadzoneSeek = new SeekBar(this);
        deadzoneSeek.setMax(35);
        deadzoneSeek.setProgress(prefs.getInt("deadzone", 16));
        deadzoneLabel = text("", 16, false);
        addSliderRow(left, deadzoneLabel, deadzoneSeek);

        sizeSeek = new SeekBar(this);
        sizeSeek.setMax(48);
        sizeSeek.setProgress(Math.max(8, prefs.getInt("cursor_size", 18)));
        sizeLabel = text("", 16, false);
        addSliderRow(left, sizeLabel, sizeSeek);

        TextView scrollHeading = text("右摇杆连续滚动", 19, true);
        scrollHeading.setPadding(0, dp(6), 0, dp(2));
        left.addView(scrollHeading);

        scrollDeadzoneSeek = new SeekBar(this);
        scrollDeadzoneSeek.setMax(55);
        scrollDeadzoneSeek.setProgress(Math.max(5, prefs.getInt("scroll_deadzone", 22)) - 5);
        scrollDeadzoneLabel = text("", 16, false);
        addSliderRow(left, scrollDeadzoneLabel, scrollDeadzoneSeek);

        scrollSensitivitySeek = new SeekBar(this);
        scrollSensitivitySeek.setMax(150);
        scrollSensitivitySeek.setProgress(Math.max(50, prefs.getInt("scroll_sensitivity", 100)) - 50);
        scrollSensitivityLabel = text("", 16, false);
        addSliderRow(left, scrollSensitivityLabel, scrollSensitivitySeek);

        scrollSpeedSeek = new SeekBar(this);
        scrollSpeedSeek.setMax(2200);
        scrollSpeedSeek.setProgress(Math.max(200, prefs.getInt("scroll_speed", 900)) - 200);
        scrollSpeedLabel = text("", 16, false);
        addSliderRow(left, scrollSpeedLabel, scrollSpeedSeek);

        TextView scrollNote = text("使用右摇杆控制光标时，右摇杆滚动自动暂停。", 14, false);
        left.addView(scrollNote);

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
            ShizukuOverlayService shizukuService = ShizukuOverlayService.getInstance();
            if (shizukuService != null) shizukuService.applySettings();
            Toast.makeText(this, "设置已应用", Toast.LENGTH_SHORT).show();
        });
        save.setTextSize(17);
        left.addView(save, compactLp());

        TextView mappingHeading = text("自定义按键映射", 20, true);
        mappingHeading.setPadding(0, 0, 0, dp(3));
        right.addView(mappingHeading);
        inputMonitor = text("最近按键：等待输入", 14, false);
        inputMonitor.setPadding(0, 0, 0, dp(3));
        right.addView(inputMonitor);
        actionMonitor = text("最近动作：等待输入", 14, false);
        actionMonitor.setPadding(0, 0, 0, dp(3));
        right.addView(actionMonitor);
        for (int i = 0; i < Prefs.ACTION_IDS.length; i++) addMappingRow(right, i);

        TextView comboHeading = text("模式切换组合键", 19, true);
        comboHeading.setPadding(0, dp(5), 0, dp(2));
        right.addView(comboHeading);
        addComboRow(right, 1, "组合键 1", Prefs.comboFirst(this));
        addComboRow(right, 2, "组合键 2", Prefs.comboSecond(this));
        TextView comboNote = text("辅助功能可双向组合键切换；Shizuku 直通后需回本页开启。", 14, false);
        right.addView(comboNote);

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
        resetMappings.setTextSize(16);
        right.addView(resetMappings, compactLp());

        return page;
    }

    private void showAbout() {
        String message = "Android 9 / API 28 电视手柄鼠标\n\n"
                + "默认映射\n"
                + "左摇杆：移动光标（可切换右摇杆）\n"
                + "A：单击    X：长按\n"
                + "B：返回    Y：主页\n"
                + "LB / RB：向上 / 向下滚动\n"
                + "L3：光标回到中心\n"
                + "菜单：默认未绑定（Shizuku 方案可用）\n"
                + "右摇杆 Y 轴：连续滚动\n"
                + "START + SELECT：鼠标模式 / 手柄直通\n\n"
                + "进入 Moonlight 前请切换到手柄直通模式。\n\n"
                + "输入方案可在辅助功能与 Shizuku 之间切换。\n"
                + "辅助功能方案安装简单；Shizuku 方案可注入真正的菜单键，"
                + "但 Android 9 每次重启后需要通过 ADB 重新启动 Shizuku。";
        new AlertDialog.Builder(this)
                .setTitle("PadCursor TV v" + BuildConfig.VERSION_NAME)
                .setMessage(message)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void selectBackend(String backend) {
        if (backend.equals(Prefs.inputBackend(this))) {
            updateStatus();
            return;
        }
        prefs.edit().putString("input_backend", backend).apply();
        if (Prefs.BACKEND_SHIZUKU.equals(backend)) {
            GamepadMouseService accessibility = GamepadMouseService.getInstance();
            if (accessibility != null) accessibility.applyBackendSelection();
            ShizukuBridge.ensureBound();
            if (ShizukuBridge.isReady() && Settings.canDrawOverlays(this)
                    && prefs.getBoolean("mouse_mode", true)) {
                ShizukuOverlayService.start(this);
            }
        } else {
            ShizukuOverlayService.stop(this);
            GamepadMouseService accessibility = GamepadMouseService.getInstance();
            if (accessibility != null) accessibility.applyBackendSelection();
        }
        updateStatus();
    }

    private void configureCurrentBackend() {
        if (!Prefs.usesShizuku(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开辅助功能设置", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (!ShizukuBridge.isRunning()) {
            Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if (launch != null) {
                startActivity(launch);
                Toast.makeText(this, "请先在 Shizuku 中启动服务，然后返回 PadCursor", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "未检测到 Shizuku，请先安装并通过 ADB 启动", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (!ShizukuBridge.hasPermission()) {
            ShizukuBridge.requestPermission();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
            return;
        }
        ShizukuBridge.ensureBound();
        if (ShizukuBridge.isReady()) {
            if (prefs.getBoolean("mouse_mode", true)) ShizukuOverlayService.start(this);
            Toast.makeText(this, "Shizuku 输入方案已就绪", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "正在连接 Shizuku 输入服务，请稍候", Toast.LENGTH_SHORT).show();
        }
        updateStatus();
    }

    private void addSliderRow(LinearLayout root, TextView label, SeekBar seekBar) {
        LinearLayout row = mappingRow();
        label.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(44), 1.2f));
        row.addView(seekBar, new LinearLayout.LayoutParams(0, dp(44), 1f));
        root.addView(row);
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
        row.addView(rowLabel(Prefs.ACTION_NAMES[index]), new LinearLayout.LayoutParams(0, dp(44), 1));
        Button set = button(keyName(Prefs.actionKey(this, index)));
        set.setTextSize(14);
        mappingButtons[index] = set;
        set.setOnClickListener(v -> beginCapture(index, 0));
        row.addView(set, new LinearLayout.LayoutParams(0, dp(42), 1.45f));
        Button clear = button("清除");
        clear.setTextSize(15);
        clear.setOnClickListener(v -> {
            prefs.edit().putInt("key_" + Prefs.ACTION_IDS[index], KeyEvent.KEYCODE_UNKNOWN).apply();
            mappingButtons[index].setText("未绑定");
        });
        row.addView(clear, new LinearLayout.LayoutParams(dp(72), dp(42)));
        root.addView(row);
    }

    private void addComboRow(LinearLayout root, int which, String label, int keyCode) {
        LinearLayout row = mappingRow();
        row.addView(rowLabel(label), new LinearLayout.LayoutParams(0, dp(44), 1));
        Button set = button(keyName(keyCode));
        set.setTextSize(14);
        comboButtons[which - 1] = set;
        set.setOnClickListener(v -> beginCapture(-1, which));
        row.addView(set, new LinearLayout.LayoutParams(0, dp(42), 1.45f));
        root.addView(row);
    }

    private LinearLayout mappingRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView rowLabel(String value) {
        TextView label = text(value, 16, false);
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

    static void reportAction(String action, String result) {
        MainActivity activity = instance;
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (activity.actionMonitor != null) {
                activity.actionMonitor.setText("最近动作：" + action + "（" + result + "）");
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
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        if (instance == this) instance = null;
        captureAction = -1;
        captureCombo = 0;
        captureReadyAt = 0;
        super.onDestroy();
    }

    static void refreshStatus() {
        MainActivity activity = instance;
        if (activity != null) activity.runOnUiThread(activity::updateStatus);
    }

    private void updateStatus() {
        boolean shizuku = Prefs.usesShizuku(this);
        if (backendLabel != null) {
            backendLabel.setText("输入方案：" + (shizuku ? "Shizuku" : "辅助功能"));
        }
        if (accessibilityBackendButton != null) accessibilityBackendButton.setAlpha(shizuku ? 0.58f : 1f);
        if (shizukuBackendButton != null) shizukuBackendButton.setAlpha(shizuku ? 1f : 0.58f);
        if (configureBackendButton != null) {
            configureBackendButton.setText(shizuku ? "配置 Shizuku 与悬浮层权限" : "打开电视辅助功能设置");
        }

        if (shizuku) {
            String state;
            if (!ShizukuBridge.isRunning()) {
                state = "状态：Shizuku 未启动";
            } else if (!ShizukuBridge.hasPermission()) {
                state = "状态：Shizuku 等待授权";
            } else if (!Settings.canDrawOverlays(this)) {
                state = "状态：等待悬浮层权限";
            } else if (!ShizukuBridge.isReady()) {
                state = "状态：正在连接 Shizuku";
                ShizukuBridge.ensureBound();
            } else {
                ShizukuOverlayService service = ShizukuOverlayService.getInstance();
                boolean mouse = service != null ? service.isMouseMode()
                        : prefs.getBoolean("mouse_mode", true);
                state = mouse ? "状态：鼠标模式 ON（Shizuku）"
                        : "状态：手柄直通 ON（Shizuku）";
            }
            if (statusView != null) statusView.setText(state);
            return;
        }

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

    private LinearLayout.LayoutParams compactLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        p.setMargins(0, dp(2), 0, dp(2));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
