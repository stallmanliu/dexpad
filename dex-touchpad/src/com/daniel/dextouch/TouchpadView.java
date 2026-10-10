package com.daniel.dextouch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Portrait touch surface.
 * One finger moves the cursor, a short tap left-clicks, a hold-then-drag
 * holds the left button. Two fingers tap for a right click, or slide to scroll.
 */
public final class TouchpadView extends View {
    public interface Listener {
        void onMove(float dx, float dy);

        void onLeftClick();

        void onRightClick();

        void onScroll(float dx, float dy);

        void onDragStart();

        void onDragEnd();
    }

    private static final int MODE_IDLE = 0;
    private static final int MODE_ONE = 1;
    private static final int MODE_MOVE = 2;
    private static final int MODE_DRAG = 3;
    private static final int MODE_TWO = 4;
    private static final int MODE_SCROLL = 5;

    private static final class Finger {
        float x;
        float y;
        float downX;
        float downY;
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();
    private final SparseArray<Finger> fingers = new SparseArray<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dragStarter = this::startDrag;

    private Listener listener;
    private String status = "";
    private boolean showHint = true;
    private int mode = MODE_IDLE;
    private boolean sawTwo;
    private float lastX;
    private float lastY;
    private float centroidX;
    private float centroidY;
    private boolean centroidReady;
    private float travel;
    private long gestureStart;
    private int slop;
    private int tapTimeout;
    private int dragTimeout;

    public TouchpadView(Context context) {
        super(context);
        text.setColor(0xFF9A9A9A);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(dp(15));
        setClickable(true);
        setHapticFeedbackEnabled(true);
        slop = Math.round(dp(8));
        tapTimeout = 280;
        dragTimeout = 360;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
        invalidate();
    }

    public void setShowHint(boolean showHint) {
        this.showHint = showHint;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF000000);
        float inset = dp(10);
        frame.set(inset, inset, getWidth() - inset, getHeight() - inset);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.2f));
        paint.setColor(0xFF3A3A3A);
        canvas.drawRoundRect(frame, dp(28), dp(28), paint);

        if (showHint && mode == MODE_IDLE && fingers.size() == 0) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(15));
            text.setColor(0xFFB0B0B0);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f - dp(28);
            drawCentered(canvas, "竖屏触控板", cx, cy);
            text.setTextSize(dp(13));
            text.setColor(0xFF8E8E8E);
            drawCentered(canvas, "单指移动 · 轻点左键 · 按住拖动", cx, cy + dp(28));
            drawCentered(canvas, "双指轻点右键 · 双指上下滚动", cx, cy + dp(50));
        }

        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(dp(12));
        text.setColor(0xFF7A7A7A);
        canvas.drawText(status, frame.left + dp(16), frame.bottom - dp(16), text);
    }

    private void drawCentered(Canvas canvas, String line, float cx, float y) {
        canvas.drawText(line, cx, y, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_MOVE) {
            if (showHint) {
                showHint = false;
                invalidate();
            }
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                sawTwo = false;
                beginGesture(event.getEventTime());
                addFinger(event, 0);
                armOneFinger(event.getEventTime());
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                addFinger(event, event.getActionIndex());
                if (fingers.size() >= 2) {
                    sawTwo = true;
                    if (mode == MODE_DRAG && listener != null) listener.onDragEnd();
                    cancelDragTimer();
                    boolean fromMove = mode == MODE_MOVE || mode == MODE_DRAG;
                    mode = fromMove ? MODE_SCROLL : MODE_TWO;
                    if (!fromMove) {
                        travel = 0f;
                        gestureStart = event.getEventTime();
                    }
                    centroidReady = false;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                updateFingers(event);
                onFingerMove(event);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                noteTravel();
                fingers.remove(event.getPointerId(event.getActionIndex()));
                centroidReady = false;
                if (fingers.size() == 1 && mode != MODE_TWO) {
                    Finger left = fingers.valueAt(0);
                    lastX = left.x;
                    lastY = left.y;
                }
                break;
            case MotionEvent.ACTION_UP:
                noteTravel();
                finishGesture(event.getEventTime());
                fingers.clear();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mode == MODE_DRAG && listener != null) listener.onDragEnd();
                cancelDragTimer();
                fingers.clear();
                mode = MODE_IDLE;
                break;
            default:
                break;
        }
        return true;
    }

    private void beginGesture(long when) {
        travel = 0f;
        gestureStart = when;
        mode = MODE_IDLE;
    }

    private void addFinger(MotionEvent event, int index) {
        Finger finger = new Finger();
        finger.x = finger.downX = event.getX(index);
        finger.y = finger.downY = event.getY(index);
        fingers.put(event.getPointerId(index), finger);
    }

    private void armOneFinger(long when) {
        mode = MODE_ONE;
        Finger finger = fingers.valueAt(0);
        lastX = finger.x;
        lastY = finger.y;
        gestureStart = when;
        handler.postDelayed(dragStarter, dragTimeout);
    }

    private void updateFingers(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            Finger finger = fingers.get(event.getPointerId(i));
            if (finger == null) continue;
            finger.x = event.getX(i);
            finger.y = event.getY(i);
        }
        noteTravel();
    }

    private void onFingerMove(MotionEvent event) {
        if (fingers.size() == 1 && (mode == MODE_ONE || mode == MODE_MOVE || mode == MODE_DRAG)) {
            Finger finger = fingers.valueAt(0);
            if (mode == MODE_ONE && distance(finger.x, finger.y, finger.downX, finger.downY) > slop) {
                mode = MODE_MOVE;
                cancelDragTimer();
                lastX = finger.downX;
                lastY = finger.downY;
            }
            if (mode == MODE_MOVE || mode == MODE_DRAG) {
                int index = event.findPointerIndex(fingers.keyAt(0));
                if (index >= 0) {
                    int history = event.getHistorySize();
                    for (int h = 0; h < history; h++) {
                        emitMove(event.getHistoricalX(index, h), event.getHistoricalY(index, h));
                    }
                }
                emitMove(finger.x, finger.y);
            }
            return;
        }
        if (fingers.size() == 1 && mode == MODE_TWO) {
            Finger finger = fingers.valueAt(0);
            if (distance(finger.x, finger.y, finger.downX, finger.downY) > slop) {
                mode = MODE_MOVE;
                lastX = finger.x;
                lastY = finger.y;
            }
            return;
        }
        if (fingers.size() >= 2 && (mode == MODE_TWO || mode == MODE_SCROLL)) {
            float x = 0f;
            float y = 0f;
            for (int i = 0; i < fingers.size(); i++) {
                x += fingers.valueAt(i).x;
                y += fingers.valueAt(i).y;
            }
            x /= fingers.size();
            y /= fingers.size();
            if (!centroidReady) {
                centroidX = x;
                centroidY = y;
                centroidReady = true;
                return;
            }
            float dx = x - centroidX;
            float dy = y - centroidY;
            centroidX = x;
            centroidY = y;
            if (mode == MODE_TWO && travel > slop) mode = MODE_SCROLL;
            if (mode == MODE_SCROLL && listener != null && (dx != 0f || dy != 0f)) {
                listener.onScroll(dx, dy);
            }
        }
    }

    private void emitMove(float x, float y) {
        float dx = x - lastX;
        float dy = y - lastY;
        lastX = x;
        lastY = y;
        if (listener != null && (dx != 0f || dy != 0f)) listener.onMove(dx, dy);
    }

    private void finishGesture(long when) {
        cancelDragTimer();
        boolean tap = when - gestureStart <= tapTimeout && travel <= slop;
        if (mode == MODE_DRAG) {
            if (listener != null) listener.onDragEnd();
        } else if (mode == MODE_TWO && tap) {
            haptic(HapticFeedbackConstants.CONTEXT_CLICK);
            if (listener != null) listener.onRightClick();
        } else if (!sawTwo && (mode == MODE_ONE || mode == MODE_MOVE) && tap && mode != MODE_MOVE) {
            haptic(HapticFeedbackConstants.VIRTUAL_KEY);
            if (listener != null) listener.onLeftClick();
        }
        mode = MODE_IDLE;
    }

    private void startDrag() {
        if (mode != MODE_ONE || fingers.size() != 1) return;
        Finger finger = fingers.valueAt(0);
        if (distance(finger.x, finger.y, finger.downX, finger.downY) > slop) return;
        mode = MODE_DRAG;
        lastX = finger.x;
        lastY = finger.y;
        haptic(HapticFeedbackConstants.LONG_PRESS);
        if (listener != null) listener.onDragStart();
    }

    private void noteTravel() {
        for (int i = 0; i < fingers.size(); i++) {
            Finger finger = fingers.valueAt(i);
            travel = Math.max(travel, distance(finger.x, finger.y, finger.downX, finger.downY));
        }
    }

    private void cancelDragTimer() {
        handler.removeCallbacks(dragStarter);
    }

    private void haptic(int kind) {
        performHapticFeedback(kind);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.hypot(dx, dy);
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
