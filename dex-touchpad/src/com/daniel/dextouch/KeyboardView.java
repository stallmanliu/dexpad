package com.daniel.dextouch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/** On-screen QWERTY. Keys are injected as a virtual keyboard, not the system IME. */
public final class KeyboardView extends View {
    private static final int KEY_ESC = 1;
    private static final int KEY_1 = 2;
    private static final int KEY_MINUS = 12;
    private static final int KEY_EQUAL = 13;
    private static final int KEY_BACKSPACE = 14;
    private static final int KEY_TAB = 15;
    private static final int KEY_Q = 16;
    private static final int KEY_W = 17;
    private static final int KEY_E = 18;
    private static final int KEY_R = 19;
    private static final int KEY_T = 20;
    private static final int KEY_Y = 21;
    private static final int KEY_U = 22;
    private static final int KEY_I = 23;
    private static final int KEY_O = 24;
    private static final int KEY_P = 25;
    private static final int KEY_ENTER = 28;
    private static final int KEY_LEFTCTRL = 29;
    private static final int KEY_A = 30;
    private static final int KEY_S = 31;
    private static final int KEY_D = 32;
    private static final int KEY_F = 33;
    private static final int KEY_G = 34;
    private static final int KEY_H = 35;
    private static final int KEY_J = 36;
    private static final int KEY_K = 37;
    private static final int KEY_L = 38;
    private static final int KEY_GRAVE = 41;
    private static final int KEY_LEFTSHIFT = 42;
    private static final int KEY_Z = 44;
    private static final int KEY_X = 45;
    private static final int KEY_C = 46;
    private static final int KEY_V = 47;
    private static final int KEY_B = 48;
    private static final int KEY_N = 49;
    private static final int KEY_M = 50;
    private static final int KEY_COMMA = 51;
    private static final int KEY_DOT = 52;
    private static final int KEY_SLASH = 53;
    private static final int KEY_LEFTALT = 56;
    private static final int KEY_SPACE = 57;
    private static final int KEY_UP = 103;
    private static final int KEY_LEFT = 105;
    private static final int KEY_RIGHT = 106;
    private static final int KEY_DOWN = 108;

    private static final int KIND_CHAR = 0;
    private static final int KIND_SHIFT = 1;
    private static final int KIND_CTRL = 2;
    private static final int KIND_ALT = 3;

    private static final class Key {
        final String label;
        final String shifted;
        final int code;
        final int kind;
        final float weight;
        final RectF rect = new RectF();

        Key(String label, String shifted, int code, int kind, float weight) {
            this.label = label;
            this.shifted = shifted;
            this.code = code;
            this.kind = kind;
            this.weight = weight;
        }
    }

    private final Key[][] rows = new Key[][]{
            {
                    key("`", "~", KEY_GRAVE, 1f),
                    key("1", "!", KEY_1, 1f),
                    key("2", "@", KEY_1 + 1, 1f),
                    key("3", "#", KEY_1 + 2, 1f),
                    key("4", "$", KEY_1 + 3, 1f),
                    key("5", "%", KEY_1 + 4, 1f),
                    key("6", "^", KEY_1 + 5, 1f),
                    key("7", "&", KEY_1 + 6, 1f),
                    key("8", "*", KEY_1 + 7, 1f),
                    key("9", "(", KEY_1 + 8, 1f),
                    key("0", ")", KEY_1 + 9, 1f),
                    key("-", "_", KEY_MINUS, 1f),
                    key("=", "+", KEY_EQUAL, 1f),
                    key("⌫", "⌫", KEY_BACKSPACE, 1.5f),
            },
            {
                    key("q", "Q", KEY_Q, 1f),
                    key("w", "W", KEY_W, 1f),
                    key("e", "E", KEY_E, 1f),
                    key("r", "R", KEY_R, 1f),
                    key("t", "T", KEY_T, 1f),
                    key("y", "Y", KEY_Y, 1f),
                    key("u", "U", KEY_U, 1f),
                    key("i", "I", KEY_I, 1f),
                    key("o", "O", KEY_O, 1f),
                    key("p", "P", KEY_P, 1f),
            },
            {
                    key("a", "A", KEY_A, 1f),
                    key("s", "S", KEY_S, 1f),
                    key("d", "D", KEY_D, 1f),
                    key("f", "F", KEY_F, 1f),
                    key("g", "G", KEY_G, 1f),
                    key("h", "H", KEY_H, 1f),
                    key("j", "J", KEY_J, 1f),
                    key("k", "K", KEY_K, 1f),
                    key("l", "L", KEY_L, 1f),
                    key("⏎", "⏎", KEY_ENTER, 1.45f),
            },
            {
                    mod("⇧", KIND_SHIFT, 1.35f),
                    key("z", "Z", KEY_Z, 1f),
                    key("x", "X", KEY_X, 1f),
                    key("c", "C", KEY_C, 1f),
                    key("v", "V", KEY_V, 1f),
                    key("b", "B", KEY_B, 1f),
                    key("n", "N", KEY_N, 1f),
                    key("m", "M", KEY_M, 1f),
                    key(",", "<", KEY_COMMA, 1f),
                    key(".", ">", KEY_DOT, 1f),
                    key("/", "?", KEY_SLASH, 1f),
            },
            {
                    key("esc", "esc", KEY_ESC, 1.1f),
                    key("tab", "tab", KEY_TAB, 1.1f),
                    mod("ctrl", KIND_CTRL, 1.15f),
                    mod("alt", KIND_ALT, 1.05f),
                    key("空格", "空格", KEY_SPACE, 4.2f),
                    key("←", "←", KEY_LEFT, 1f),
                    key("↑", "↑", KEY_UP, 1f),
                    key("↓", "↓", KEY_DOWN, 1f),
                    key("→", "→", KEY_RIGHT, 1f),
            },
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private InjectClient client;
    private Key held;
    private int heldPointer = -1;
    private boolean shiftOnce;
    private boolean shiftLock;
    private boolean ctrlOnce;
    private boolean altOnce;
    private long lastShiftTap;

    public KeyboardView(Context context) {
        super(context);
        setClickable(true);
    }

    public void setClient(InjectClient client) {
        this.client = client;
    }

    private static Key key(String label, String shifted, int code, float weight) {
        return new Key(label, shifted, code, KIND_CHAR, weight);
    }

    private static Key mod(String label, int kind, float weight) {
        return new Key(label, label, 0, kind, weight);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = Math.round(dp(46) * rows.length + dp(8) * (rows.length + 1));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float gap = dp(4);
        float pad = dp(6);
        float rowH = (h - pad * 2f - gap * (rows.length - 1)) / rows.length;
        float y = pad;
        for (Key[] row : rows) {
            float weight = 0f;
            for (Key key : row) weight += key.weight;
            float inner = w - pad * 2f - gap * (row.length - 1);
            float x = pad;
            for (Key key : row) {
                float kw = inner * key.weight / weight;
                key.rect.set(x, y, x + kw, y + rowH);
                x += kw + gap;
            }
            y += rowH + gap;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF101010);
        paint.setTextAlign(Paint.Align.CENTER);
        boolean shifted = shiftOnce || shiftLock;
        for (Key[] row : rows) {
            for (Key key : row) {
                boolean armed = key == held
                        || (key.kind == KIND_SHIFT && shifted)
                        || (key.kind == KIND_CTRL && ctrlOnce)
                        || (key.kind == KIND_ALT && altOnce);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(armed ? 0xFF3A6EA5 : (key.kind == KIND_CHAR ? 0xFF2A2A2A : 0xFF222222));
                canvas.drawRoundRect(key.rect, dp(6), dp(6), paint);
                paint.setColor(0xFFF3F3F3);
                paint.setTextSize(labelSize(key));
                String label = shifted && key.kind == KIND_CHAR ? key.shifted : key.label;
                if (key.kind == KIND_SHIFT && shiftLock) label = "⇪";
                float cx = key.rect.centerX();
                float cy = key.rect.centerY() - (paint.descent() + paint.ascent()) / 2f;
                canvas.drawText(label, cx, cy, paint);
            }
        }
    }

    private float labelSize(Key key) {
        if (key.label.length() > 2) return dp(11);
        if ("⌫⏎⇧".contains(key.label) || key.label.length() == 1) return dp(16);
        return dp(13);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int pointer = event.getPointerId(index);
        if (action == MotionEvent.ACTION_DOWN || (action == MotionEvent.ACTION_POINTER_DOWN && heldPointer == -1)) {
            heldPointer = pointer;
            Key hit = hit(event.getX(index), event.getY(index));
            press(hit);
            return true;
        }
        if (pointer != heldPointer && event.getActionMasked() != MotionEvent.ACTION_MOVE) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                releaseHeld(true);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && heldPointer != -1) {
            int pi = event.findPointerIndex(heldPointer);
            if (pi < 0) return true;
            Key hit = hit(event.getX(pi), event.getY(pi));
            if (hit != held) {
                releaseHeld(false);
                press(hit);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_UP) {
            releaseHeld(true);
            return true;
        }
        return true;
    }

    private void press(Key key) {
        held = key;
        if (key == null) {
            invalidate();
            return;
        }
        if (key.kind != KIND_CHAR) {
            invalidate();
            return;
        }
        if (client == null) return;
        if (shiftOnce || shiftLock) client.key(KEY_LEFTSHIFT, true);
        if (ctrlOnce) client.key(KEY_LEFTCTRL, true);
        if (altOnce) client.key(KEY_LEFTALT, true);
        client.key(key.code, true);
        invalidate();
    }

    private void releaseHeld(boolean endPointer) {
        Key key = held;
        held = null;
        if (endPointer) heldPointer = -1;
        if (key == null) {
            invalidate();
            return;
        }
        if (key.kind == KIND_SHIFT) {
            long now = android.os.SystemClock.uptimeMillis();
            if (shiftLock) {
                shiftLock = false;
                shiftOnce = false;
            } else if (now - lastShiftTap < 320) {
                shiftLock = true;
                shiftOnce = false;
            } else {
                shiftOnce = !shiftOnce;
            }
            lastShiftTap = now;
            invalidate();
            return;
        }
        if (key.kind == KIND_CTRL) {
            ctrlOnce = !ctrlOnce;
            invalidate();
            return;
        }
        if (key.kind == KIND_ALT) {
            altOnce = !altOnce;
            invalidate();
            return;
        }
        if (client != null) {
            client.key(key.code, false);
            if (altOnce) {
                client.key(KEY_LEFTALT, false);
                altOnce = false;
            }
            if (ctrlOnce) {
                client.key(KEY_LEFTCTRL, false);
                ctrlOnce = false;
            }
            if (shiftOnce || shiftLock) client.key(KEY_LEFTSHIFT, false);
            if (shiftOnce && !shiftLock) shiftOnce = false;
        }
        invalidate();
    }

    private Key hit(float x, float y) {
        for (Key[] row : rows) {
            for (Key key : row) {
                if (key.rect.contains(x, y)) return key;
            }
        }
        return null;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
