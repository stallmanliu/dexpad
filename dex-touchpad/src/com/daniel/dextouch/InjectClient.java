package com.daniel.dextouch;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Talks to the shell-side virtual mouse/keyboard on 127.0.0.1:27184. */
public final class InjectClient {
    public static final int PORT = 27184;

    public interface Listener {
        void onConnectionChanged(boolean connected);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("dextouch-io");
    private Handler io;
    private Listener listener;
    private Socket socket;
    private BufferedWriter out;
    private boolean connected;
    private boolean started;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        if (started) return;
        started = true;
        thread.start();
        io = new Handler(thread.getLooper());
        io.post(this::pump);
    }

    public void stop() {
        if (io == null) return;
        io.post(() -> {
            closeSocket();
            thread.quitSafely();
        });
    }

    public boolean isConnected() {
        return connected;
    }

    public void move(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        send(String.format(Locale.US, "M %d %d", dx, dy));
    }

    public void leftClick() {
        send("L");
    }

    public void rightClick() {
        send("R");
    }

    public void leftDown() {
        send("PD L");
    }

    public void leftUp() {
        send("PU L");
    }

    public void wheel(int vertical, int horizontal) {
        if (vertical == 0 && horizontal == 0) return;
        send(String.format(Locale.US, "W %d %d", vertical, horizontal));
    }

    public void key(int linuxCode, boolean down) {
        send(String.format(Locale.US, "K %d %d", linuxCode, down ? 1 : 0));
    }

    private void send(String line) {
        if (io == null) return;
        io.post(() -> writeLine(line));
    }

    private void pump() {
        if (!connected) tryConnect();
        if (io != null) io.postDelayed(this::pump, connected ? 1500 : 700);
    }

    private void writeLine(String line) {
        if (!connected && !tryConnect()) return;
        try {
            out.write(line);
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            closeSocket();
        }
    }

    private boolean tryConnect() {
        if (connected && socket != null && socket.isConnected() && !socket.isClosed()) return true;
        closeSocket();
        Socket next = new Socket();
        try {
            next.connect(new InetSocketAddress("127.0.0.1", PORT), 400);
            next.setTcpNoDelay(true);
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(next.getOutputStream(), StandardCharsets.UTF_8));
            socket = next;
            out = writer;
            setConnected(true);
            return true;
        } catch (IOException e) {
            try {
                next.close();
            } catch (IOException ignored) {
            }
            setConnected(false);
            return false;
        }
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        socket = null;
        out = null;
        setConnected(false);
    }

    private void setConnected(boolean value) {
        if (connected == value) return;
        connected = value;
        Listener current = listener;
        if (current != null) main.post(() -> current.onConnectionChanged(value));
    }
}
