package com.lantern.padcursor;

import android.view.KeyEvent;

interface CursorEventHost {
    void onRightStickScroll(float rawAxis);
    boolean onOverlayKeyEvent(KeyEvent event);
}
