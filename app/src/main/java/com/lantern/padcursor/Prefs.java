package com.lantern.padcursor;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

final class Prefs {
    static final String FILE = "padcursor";

    static final String[] ACTION_IDS = {
            "click", "long_click", "back", "home", "scroll_up", "scroll_down", "center"
    };
    static final String[] ACTION_NAMES = {
            "单击", "长按", "返回", "主页", "向上滚动", "向下滚动", "光标回到中心"
    };
    static final int[] DEFAULT_KEYS = {
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_THUMBL
    };

    private Prefs() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int actionKey(Context context, int index) {
        return get(context).getInt("key_" + ACTION_IDS[index], DEFAULT_KEYS[index]);
    }

    static int comboFirst(Context context) {
        return get(context).getInt("combo_first", KeyEvent.KEYCODE_BUTTON_START);
    }

    static int comboSecond(Context context) {
        return get(context).getInt("combo_second", KeyEvent.KEYCODE_BUTTON_SELECT);
    }

    static float scrollDeadzone(Context context) {
        return get(context).getInt("scroll_deadzone", 22) / 100f;
    }

    static float scrollSensitivity(Context context) {
        return get(context).getInt("scroll_sensitivity", 100) / 100f;
    }

    static int scrollSpeed(Context context) {
        return get(context).getInt("scroll_speed", 900);
    }

    static void resetMappings(Context context) {
        SharedPreferences.Editor edit = get(context).edit();
        for (String id : ACTION_IDS) edit.remove("key_" + id);
        edit.remove("combo_first");
        edit.remove("combo_second");
        edit.apply();
    }
}
