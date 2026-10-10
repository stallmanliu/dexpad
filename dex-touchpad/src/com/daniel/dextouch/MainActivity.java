package com.daniel.dextouch;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

public final class MainActivity extends Activity implements TouchpadView.Listener {
    private static final float[] SPEEDS = {0.6f, 1f, 1.6f, 2.4f};
    private static final String[] SPEED_LABELS = {"0.6×", "1×", "1.6×", "2.4×"};
    private static final float SCROLL_PX = 34f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private InjectClient client;
    private TouchpadView pad;
    private KeyboardView keyboard;
    private SetupPanel setup;
    private DaemonStarter starter;
    private String starterNote = "正在启动";
    private boolean starterBusy;
    private IconButton keyboardButton;
    private IconButton awakeButton;
    private IconButton speedButton;
    private DisplayManager displays;
    private DisplayManager.DisplayListener displayListener;
    private PowerManager.WakeLock wakeLock;

    private int speedIndex = 1;
    private boolean keepAwake = true;
    private boolean naturalScroll = true;
    private boolean keyboardOpen;
    private boolean connected;
    private boolean hint = true;
    private String displayName = "外接屏幕";
    private float pendingX;
    private float pendingY;
    private float scrollY;
    private float scrollX;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        prefs = getSharedPreferences("dextouch", MODE_PRIVATE);
        speedIndex = prefs.getInt("speed", 1);
        keepAwake = prefs.getBoolean("awake", true);
        naturalScroll = prefs.getBoolean("natural", true);
        hint = prefs.getBoolean("hint", true);
        if (speedIndex < 0 || speedIndex >= SPEEDS.length) speedIndex = 1;

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attrs);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
        /* Touches stay on this window, but key focus stays on the DeX display.
           Otherwise the virtual keyboard's own key events are delivered back here. */
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

        client = new InjectClient();
        client.setListener(up -> {
            connected = up;
            renderStatus();
        });
        client.start();

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Color.BLACK);

        FrameLayout frame = new FrameLayout(this);
        pad = new TouchpadView(this);
        pad.setListener(this);
        pad.setShowHint(hint);
        frame.addView(pad, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        int gap = dp(8);
        keyboardButton = addButton(buttons, IconButton.ICON_KEYBOARD, gap);
        awakeButton = addButton(buttons, IconButton.ICON_AWAKE, gap);
        speedButton = addButton(buttons, IconButton.ICON_TEXT, gap);
        IconButton close = addButton(buttons, IconButton.ICON_CLOSE, 0);
        speedButton.setLabel(SPEED_LABELS[speedIndex]);
        awakeButton.setActive(keepAwake);

        keyboardButton.setOnClick(b -> toggleKeyboard());
        awakeButton.setOnClick(b -> {
            keepAwake = !keepAwake;
            prefs.edit().putBoolean("awake", keepAwake).apply();
            applyAwake();
            toast(keepAwake ? "使用时保持屏幕常亮" : "允许屏幕自动休眠");
        });
        speedButton.setOnClick(b -> {
            speedIndex = (speedIndex + 1) % SPEEDS.length;
            prefs.edit().putInt("speed", speedIndex).apply();
            speedButton.setLabel(SPEED_LABELS[speedIndex]);
            renderStatus();
            toast("指针速度 " + SPEED_LABELS[speedIndex]);
        });
        speedButton.setOnLongClick(b -> {
            naturalScroll = !naturalScroll;
            prefs.edit().putBoolean("natural", naturalScroll).apply();
            renderStatus();
            toast(naturalScroll ? "双指滚动：内容跟随手指" : "双指滚动：传统方向");
        });
        close.setOnClick(b -> finishAndRemoveTask());

        FrameLayout.LayoutParams buttonLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        buttonLp.topMargin = dp(18);
        buttonLp.rightMargin = dp(14);
        frame.addView(buttons, buttonLp);

        keyboard = new KeyboardView(this);
        keyboard.setClient(client);
        keyboard.setVisibility(LinearLayout.GONE);

        setup = new SetupPanel(this);
        setup.onOpenSettings = this::openWirelessDebugging;
        setup.onRetry = () -> starter.ensure();
        setup.onPair = code -> starter.pairAndStart(code);

        column.addView(frame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        column.addView(setup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(keyboard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(column);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }

        column.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets cut = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) buttons.getLayoutParams();
            lp.topMargin = cut.top + dp(12);
            lp.rightMargin = Math.max(cut.right, dp(12));
            buttons.setLayoutParams(lp);
            LinearLayout.LayoutParams klp = (LinearLayout.LayoutParams) keyboard.getLayoutParams();
            klp.bottomMargin = cut.bottom;
            keyboard.setLayoutParams(klp);
            return insets;
        });

        displays = getSystemService(DisplayManager.class);
        displayListener = new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int displayId) { refreshDisplay(); }
            @Override public void onDisplayRemoved(int displayId) { refreshDisplay(); }
            @Override public void onDisplayChanged(int displayId) { refreshDisplay(); }
        };
        displays.registerDisplayListener(displayListener, main);
        refreshDisplay();
        renderStatus();

        starter = DaemonStarter.get(this);
        starter.setCallback((state, detail) -> main.post(() -> onStarter(state, detail)));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        consumePairExtra(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumePairExtra(intent);
    }

    private void consumePairExtra(Intent intent) {
        if (intent == null) return;
        String code = intent.getStringExtra("pair_code");
        if (code == null || code.isEmpty() || starter == null) return;
        intent.removeExtra("pair_code");
        starter.pairAndStart(code);
    }

    private void onStarter(DaemonStarter.State state, String detail) {
        starterNote = detail;
        if (state == DaemonStarter.State.STARTING) starterBusy = true;
        if (state == DaemonStarter.State.CONNECTED) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            setup.hidePanel();
            cancelPairNotification();
            if (starterBusy) toast("触控服务已在这台手机上启动");
            starterBusy = false;
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            setup.show(state, detail);
            if (state == DaemonStarter.State.NEED_PAIR) showPairNotification();
        }
        renderStatus();
    }

    private void openWirelessDebugging() {
        String[] names = {
                "com.android.settings.development.WirelessDebuggingActivity",
                "com.samsung.android.settings.wifi.WifiDebuggingActivity"
        };
        for (String name : names) {
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings", name));
                startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            toast("请手动打开 设置 → 开发者选项 → 无线调试");
        }
    }

    private void showPairNotification() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    "pair", "无线调试配对", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }
        RemoteInput input = new RemoteInput.Builder(PairCodeReceiver.EXTRA_CODE)
                .setLabel("6 位配对码")
                .build();
        Intent reply = new Intent(this, PairCodeReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
                this, 1, reply, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        Notification.Action action = new Notification.Action.Builder(
                getApplicationInfo().icon, "输入配对码", pending)
                .addRemoteInput(input)
                .build();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "pair")
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(getApplicationInfo().icon)
                .setContentTitle("DeX Pad 配对")
                .setContentText("打开配对窗口后，在这里回复 6 位数字")
                .addAction(action)
                .setOngoing(true)
                .build();
        manager.notify(7, notification);
    }

    private void cancelPairNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.cancel(7);
    }

    private IconButton addButton(LinearLayout row, int icon, int endGap) {
        IconButton button = new IconButton(this, icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
        lp.rightMargin = endGap;
        row.addView(button, lp);
        return button;
    }

    private void toggleKeyboard() {
        keyboardOpen = !keyboardOpen;
        keyboard.setVisibility(keyboardOpen ? LinearLayout.VISIBLE : LinearLayout.GONE);
        keyboardButton.setActive(keyboardOpen);
    }

    private void refreshDisplay() {
        Display found = null;
        Display[] presentation = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        if (presentation != null && presentation.length > 0) found = presentation[0];
        if (found == null) {
            for (Display display : displays.getDisplays()) {
                if (display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    found = display;
                    break;
                }
            }
        }
        displayName = found == null ? "未检测到外接屏幕" : found.getName();
        renderStatus();
    }

    private void renderStatus() {
        if (pad == null) return;
        String link = connected ? "已连接" : starterNote;
        String scroll = naturalScroll ? "自然滚动" : "传统滚动";
        pad.setStatus(displayName + " · " + link + " · " + SPEED_LABELS[speedIndex] + " · " + scroll);
    }

    private void dismissHint() {
        if (!hint) return;
        hint = false;
        prefs.edit().putBoolean("hint", false).apply();
        pad.setShowHint(false);
    }

    @Override
    public void onMove(float dx, float dy) {
        dismissHint();
        float scale = SPEEDS[speedIndex];
        pendingX += dx * scale;
        pendingY += dy * scale;
        int ix = Math.round(pendingX);
        int iy = Math.round(pendingY);
        if (ix == 0 && iy == 0) return;
        pendingX -= ix;
        pendingY -= iy;
        client.move(ix, iy);
    }

    @Override
    public void onLeftClick() {
        dismissHint();
        client.leftClick();
    }

    @Override
    public void onRightClick() {
        dismissHint();
        client.rightClick();
    }

    @Override
    public void onScroll(float dx, float dy) {
        dismissHint();
        float dir = naturalScroll ? -1f : 1f;
        scrollY += dy * dir;
        scrollX += dx * dir;
        int notchesY = (int) (scrollY / SCROLL_PX);
        int notchesX = (int) (scrollX / SCROLL_PX);
        if (notchesY == 0 && notchesX == 0) return;
        scrollY -= notchesY * SCROLL_PX;
        scrollX -= notchesX * SCROLL_PX;
        client.wheel(notchesY, notchesX);
    }

    @Override
    public void onDragStart() {
        dismissHint();
        client.leftDown();
    }

    @Override
    public void onDragEnd() {
        client.leftUp();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyAwake();
        if (starter != null && !connected && setup.getVisibility() != android.view.View.VISIBLE) {
            starter.ensure();
        }
    }

    @Override
    protected void onPause() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        displays.unregisterDisplayListener(displayListener);
        if (starter != null) starter.setCallback(null);
        client.stop();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void applyAwake() {
        awakeButton.setActive(keepAwake);
        if (keepAwake) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (wakeLock == null) {
                PowerManager pm = getSystemService(PowerManager.class);
                wakeLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                        "dextouch:awake");
                wakeLock.setReferenceCounted(false);
            }
            if (!wakeLock.isHeld()) wakeLock.acquire();
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
