package com.lantern.padcursor;

interface IPrivilegedInputService {
    boolean injectKey(int keyCode);
    boolean injectTap(float x, float y, long durationMs);
    boolean injectSwipe(float startX, float startY, float endX, float endY, long durationMs);
    void destroy();
}
