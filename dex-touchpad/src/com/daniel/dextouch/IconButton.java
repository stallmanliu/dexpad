package com.daniel.dextouch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

public final class IconButton extends View {
    public static final int ICON_KEYBOARD = 1;
    public static final int ICON_AWAKE = 2;
    public static final int ICON_CLOSE = 3;
    public static final int ICON_TEXT = 4;

    public interface Click {
        void onClick(IconButton button);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int icon;
    private String label = "";
    private boolean active;
    private boolean pressed;
    private boolean longFired;
    private Click click;
    private Click longClick;
    private final Runnable longRunnable = () -> {
        longFired = true;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (longClick != null) longClick.onClick(IconButton.this);
    };

    public IconButton(Context context, int icon) {
        super(context);
        this.icon = icon;
        setClickable(true);
        setHapticFeedbackEnabled(true);
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
        invalidate();
    }

    public void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    public void setOnClick(Click click) {
        this.click = click;
    }

    public void setOnLongClick(Click longClick) {
        this.longClick = longClick;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(dp(46));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - dp(1.5f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed || active ? 0xFF1C1C1C : 0xFF000000);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.4f));
        paint.setColor(active ? 0xFFECECEC : 0xFF8A8A8A);
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(1.6f));
        paint.setColor(active ? 0xFFF2F2F2 : 0xFFC8C8C8);
        if (icon == ICON_TEXT) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(12));
            paint.setFakeBoldText(true);
            canvas.drawText(label, cx, cy - (paint.descent() + paint.ascent()) / 2f, paint);
            return;
        }
        if (icon == ICON_CLOSE) {
            float d = dp(7);
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
            canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
        } else if (icon == ICON_KEYBOARD) {
            float w = dp(11);
            float h = dp(8);
            canvas.drawRoundRect(cx - w, cy - h, cx + w, cy + h, dp(2), dp(2), paint);
            paint.setStrokeWidth(dp(1.2f));
            canvas.drawLine(cx - dp(7), cy + dp(3), cx + dp(7), cy + dp(3), paint);
        } else if (icon == ICON_AWAKE) {
            canvas.drawCircle(cx, cy, dp(4.2f), paint);
            paint.setStrokeWidth(dp(1.3f));
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * i / 4.0;
                float x1 = cx + (float) Math.cos(a) * dp(7);
                float y1 = cy + (float) Math.sin(a) * dp(7);
                float x2 = cx + (float) Math.cos(a) * dp(10);
                float y2 = cy + (float) Math.sin(a) * dp(10);
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                longFired = false;
                pressed = true;
                invalidate();
                handler.postDelayed(longRunnable, ViewConfigurationTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed && !inside(event)) {
                    handler.removeCallbacks(longRunnable);
                    pressed = false;
                    longFired = true;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longRunnable);
                if (pressed && !longFired && click != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    click.onClick(this);
                }
                pressed = false;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longRunnable);
                pressed = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private boolean inside(MotionEvent event) {
        return event.getX() >= 0 && event.getY() >= 0
                && event.getX() < getWidth() && event.getY() < getHeight();
    }

    private static int ViewConfigurationTimeout() {
        return 480;
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
