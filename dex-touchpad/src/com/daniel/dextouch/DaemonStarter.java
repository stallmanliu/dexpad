package com.daniel.dextouch;

import android.content.Context;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.adb.AdbAuthenticationFailedException;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;

/**
 * Starts the on-phone cursor service. The privileged process is spawned by the
 * phone's own wireless debugging, not by a computer.
 */
public final class DaemonStarter {
    public enum State {
        CONNECTED, STARTING, NEED_PAIR, NEED_WIFI, FAILED
    }

    public interface Callback {
        void onState(State state, String detail);
    }

    private static final String TAG = "DexTouch";
    private static volatile DaemonStarter instance;

    private final Context app;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile Callback callback;

    private DaemonStarter(Context app) {
        this.app = app.getApplicationContext();
    }

    public static DaemonStarter get(Context context) {
        if (instance == null) {
            synchronized (DaemonStarter.class) {
                if (instance == null) instance = new DaemonStarter(context);
            }
        }
        return instance;
    }

    public static DaemonStarter peek() {
        return instance;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void ensure() {
        exec.execute(this::ensureOnThread);
    }

    public void pairAndStart(String code) {
        exec.execute(() -> pairOnThread(code));
    }

    private void ensureOnThread() {
        if (daemonUp()) {
            report(State.CONNECTED, "已连接");
            return;
        }
        report(State.STARTING, "正在启动");
        enableWirelessDebugging();
        LocalAdb adb = null;
        try {
            adb = LocalAdb.create(app.getFilesDir());
            boolean ok = adb.connectTls(app, 12_000);
            if (!ok) {
                report(State.NEED_WIFI, "请打开无线调试");
                return;
            }
            launchBinary(adb);
            if (waitForDaemon()) {
                report(State.CONNECTED, "已连接");
            } else {
                report(State.FAILED, "服务没有起来，请再试一次");
            }
        } catch (AdbPairingRequiredException | AdbAuthenticationFailedException e) {
            Log.i(TAG, "needs pair", e);
            report(State.NEED_PAIR, "需要在这台手机上配对一次");
        } catch (Exception e) {
            Log.w(TAG, "ensure failed", e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String lower = message.toLowerCase();
            if (e instanceof AdbPairingRequiredException
                    || e instanceof AdbAuthenticationFailedException
                    || lower.contains("pair")
                    || lower.contains("unauthor")
                    || lower.contains("certificate")
                    || lower.contains("connection failed")) {
                report(State.NEED_PAIR, "需要在这台手机上配对一次");
            } else if (lower.contains("port") || lower.contains("time")) {
                report(State.NEED_WIFI, "请打开无线调试后再试");
            } else {
                report(State.FAILED, message);
            }
        } finally {
            close(adb);
        }
    }

    private void pairOnThread(String code) {
        String digits = code == null ? "" : code.trim();
        if (digits.length() < 6) {
            report(State.NEED_PAIR, "请输入 6 位配对码");
            return;
        }
        report(State.STARTING, "正在配对");
        enableWirelessDebugging();
        AtomicInteger port = new AtomicInteger(-1);
        AtomicReference<InetAddress> host = new AtomicReference<>();
        CountDownLatch found = new CountDownLatch(1);
        AdbMdns mdns = new AdbMdns(app, AdbMdns.SERVICE_TYPE_TLS_PAIRING, (address, discovered) -> {
            if (discovered > 0 && port.get() < 0) {
                host.set(address);
                port.set(discovered);
                found.countDown();
            }
        });
        mdns.start();
        LocalAdb adb = null;
        try {
            if (!found.await(20, TimeUnit.SECONDS) || port.get() < 0) {
                report(State.NEED_PAIR, "没找到配对窗口。请先点「使用配对码配对设备」，窗口不要关");
                return;
            }
            adb = LocalAdb.create(app.getFilesDir());
            boolean paired = adb.pair("127.0.0.1", port.get(), digits);
            if (!paired && host.get() != null) {
                paired = adb.pair(host.get().getHostAddress(), port.get(), digits);
            }
            if (!paired) {
                report(State.NEED_PAIR, "配对失败，请重新生成配对码再试");
                return;
            }
            Log.i(TAG, "paired");
            close(adb);
            adb = LocalAdb.create(app.getFilesDir());
            if (!adb.connectTls(app, 12_000)) {
                report(State.FAILED, "配对成功，但没有连上无线调试");
                return;
            }
            launchBinary(adb);
            if (waitForDaemon()) report(State.CONNECTED, "已连接");
            else report(State.FAILED, "配对成功，但触控服务没有起来");
        } catch (Exception e) {
            Log.w(TAG, "pair failed", e);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            report(State.NEED_PAIR, "配对失败：" + message);
        } finally {
            try {
                mdns.stop();
            } catch (Exception ignored) {
            }
            close(adb);
        }
    }

    private void launchBinary(LocalAdb adb) throws Exception {
        byte[] binary;
        try (InputStream in = app.getAssets().open("dextouchd")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) > 0) buf.write(tmp, 0, n);
            binary = buf.toByteArray();
        }
        String b64 = Base64.encodeToString(binary, Base64.NO_WRAP);
        String cmd = "if pidof dextouchd >/dev/null 2>&1; then echo ALREADY; exit 0; fi; "
                + "printf '%s' '" + b64 + "' | toybox base64 -d > /data/local/tmp/dextouchd "
                + "&& chmod 755 /data/local/tmp/dextouchd "
                + "&& /data/local/tmp/dextouchd; echo STARTED";
        Log.i(TAG, "shell command bytes " + cmd.length());
        AdbStream stream = adb.openStream("shell:" + cmd);
        String output = readFor(stream, 4000);
        Log.i(TAG, "shell output: " + output);
        try {
            stream.close();
        } catch (Exception ignored) {
        }
    }

    private static String readFor(AdbStream stream, long ms) {
        StringBuilder out = new StringBuilder();
        Thread reader = new Thread(() -> {
            byte[] buf = new byte[1024];
            try {
                while (true) {
                    int n = stream.read(buf, 0, buf.length);
                    if (n < 0) break;
                    out.append(new String(buf, 0, n));
                }
            } catch (Exception ignored) {
            }
        }, "dextouch-adb-read");
        reader.start();
        try {
            reader.join(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out.toString().trim();
    }

    private boolean waitForDaemon() {
        for (int i = 0; i < 20; i++) {
            if (daemonUp()) return true;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public static boolean daemonUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", InjectClient.PORT), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void enableWirelessDebugging() {
        try {
            Settings.Global.putInt(app.getContentResolver(), "adb_wifi_enabled", 1);
        } catch (SecurityException e) {
            Log.i(TAG, "cannot toggle wireless debugging");
        }
    }

    private static void close(LocalAdb adb) {
        if (adb == null) return;
        try {
            adb.close();
        } catch (Exception ignored) {
        }
    }

    private void report(State state, String detail) {
        Callback current = callback;
        if (current != null) current.onState(state, detail);
    }
}
