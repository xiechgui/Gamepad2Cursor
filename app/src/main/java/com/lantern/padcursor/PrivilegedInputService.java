package com.lantern.padcursor;

import java.io.InputStream;

public class PrivilegedInputService extends IPrivilegedInputService.Stub {
    public PrivilegedInputService() {}

    @Override public boolean injectKey(int keyCode) {
        return runInput("keyevent", Integer.toString(keyCode));
    }

    @Override public boolean injectTap(float x, float y, long durationMs) {
        String sx = Integer.toString(Math.round(x));
        String sy = Integer.toString(Math.round(y));
        if (durationMs <= 100) return runInput("tap", sx, sy);
        return runInput("swipe", sx, sy, sx, sy, Long.toString(durationMs));
    }

    @Override public boolean injectSwipe(float startX, float startY,
                                         float endX, float endY, long durationMs) {
        return runInput("swipe",
                Integer.toString(Math.round(startX)),
                Integer.toString(Math.round(startY)),
                Integer.toString(Math.round(endX)),
                Integer.toString(Math.round(endY)),
                Long.toString(durationMs));
    }

    private boolean runInput(String... inputArgs) {
        String[] command = new String[inputArgs.length + 1];
        command[0] = "/system/bin/input";
        System.arraycopy(inputArgs, 0, command, 1, inputArgs.length);
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (InputStream output = process.getInputStream()) {
                byte[] buffer = new byte[256];
                while (output.read(buffer) >= 0) { /* drain command output */ }
            }
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    @Override public void destroy() {
        System.exit(0);
    }
}
