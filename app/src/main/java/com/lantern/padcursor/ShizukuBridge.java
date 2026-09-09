package com.lantern.padcursor;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

final class ShizukuBridge {
    static final int PERMISSION_REQUEST = 4107;

    interface ResultCallback { void onResult(boolean success); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static Context appContext;
    private static IPrivilegedInputService inputService;
    private static boolean initialized;
    private static boolean binding;

    private static final Shizuku.OnBinderReceivedListener BINDER_RECEIVED = () -> {
        if (hasPermission()) ensureBound();
        MainActivity.refreshStatus();
    };
    private static final Shizuku.OnBinderDeadListener BINDER_DEAD = () -> {
        inputService = null;
        binding = false;
        ShizukuOverlayService.stopIfRunning();
        MainActivity.refreshStatus();
    };
    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            inputService = IPrivilegedInputService.Stub.asInterface(binder);
            binding = false;
            MainActivity.refreshStatus();
            if (appContext != null && Prefs.usesShizuku(appContext)
                    && Prefs.get(appContext).getBoolean("mouse_mode", true)) {
                ShizukuOverlayService.start(appContext);
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            inputService = null;
            binding = false;
            MainActivity.refreshStatus();
        }
    };

    private ShizukuBridge() {}

    static synchronized void initialize(Context context) {
        appContext = context.getApplicationContext();
        if (initialized) return;
        initialized = true;
        Shizuku.addBinderReceivedListenerSticky(BINDER_RECEIVED);
        Shizuku.addBinderDeadListener(BINDER_DEAD);
        if (isRunning() && hasPermission()) ensureBound();
    }

    static boolean isRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean hasPermission() {
        try {
            return !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isReady() {
        IPrivilegedInputService service = inputService;
        return isRunning() && hasPermission() && service != null && service.asBinder().isBinderAlive();
    }

    static void requestPermission() {
        if (!isRunning()) return;
        try {
            Shizuku.requestPermission(PERMISSION_REQUEST);
        } catch (Throwable ignored) {
            MainActivity.refreshStatus();
        }
    }

    static synchronized void ensureBound() {
        if (appContext == null || binding || isReady() || !isRunning() || !hasPermission()) return;
        binding = true;
        ComponentName component = new ComponentName(appContext, PrivilegedInputService.class);
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(component)
                .processNameSuffix("input")
                .tag("padcursor-input")
                .version(1)
                .daemon(false);
        try {
            Shizuku.bindUserService(args, CONNECTION);
        } catch (Throwable ignored) {
            binding = false;
            MainActivity.refreshStatus();
        }
    }

    static void injectKey(int keyCode, ResultCallback callback) {
        run(service -> service.injectKey(keyCode), callback);
    }

    static void injectTap(float x, float y, long durationMs, ResultCallback callback) {
        run(service -> service.injectTap(x, y, durationMs), callback);
    }

    static void injectSwipe(float startX, float startY, float endX, float endY,
                            long durationMs, ResultCallback callback) {
        run(service -> service.injectSwipe(startX, startY, endX, endY, durationMs), callback);
    }

    private interface RemoteCall { boolean run(IPrivilegedInputService service) throws Exception; }

    private static void run(RemoteCall call, ResultCallback callback) {
        IPrivilegedInputService service = inputService;
        if (service == null || !service.asBinder().isBinderAlive()) {
            if (callback != null) MAIN.post(() -> callback.onResult(false));
            ensureBound();
            return;
        }
        WORKER.execute(() -> {
            boolean success;
            try {
                success = call.run(service);
            } catch (Exception ignored) {
                success = false;
            }
            boolean result = success;
            if (callback != null) MAIN.post(() -> callback.onResult(result));
        });
    }
}
