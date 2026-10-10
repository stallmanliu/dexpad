package com.daniel.dextouch;

import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** On-phone pairing instructions. No computer is involved. */
public final class SetupPanel extends LinearLayout {
    private final TextView message;
    private final EditText code;
    private final Button pair;

    public SetupPanel(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(0xFF141414);
        int pad = dp(16);
        setPadding(pad, pad, pad, pad);

        TextView title = new TextView(context);
        title.setText("在这台手机上启动");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        addView(title);

        message = new TextView(context);
        message.setTextColor(0xFFBDBDBD);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        message.setPadding(0, dp(8), 0, dp(8));
        addView(message);

        code = new EditText(context);
        code.setHint("6 位配对码");
        code.setHintTextColor(0xFF777777);
        code.setTextColor(Color.WHITE);
        code.setBackgroundColor(0xFF222222);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        code.setPadding(dp(12), dp(10), dp(12), dp(10));
        addView(code, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        pair = button(context, "配对并启动");
        Button open = button(context, "打开无线调试");
        Button retry = button(context, "重试");
        row.addView(pair);
        row.addView(gap(context, open));
        row.addView(gap(context, retry));
        addView(row);

        pair.setOnClickListener(v -> {
            if (onPair != null) onPair.run(code.getText().toString());
        });
        open.setOnClickListener(v -> {
            if (onOpenSettings != null) onOpenSettings.run();
        });
        retry.setOnClickListener(v -> {
            if (onRetry != null) onRetry.run();
        });
        setVisibility(GONE);
    }

    public interface CodeAction {
        void run(String code);
    }

    public Runnable onOpenSettings;
    public Runnable onRetry;
    public CodeAction onPair;

    public void show(DaemonStarter.State state, String detail) {
        setVisibility(VISIBLE);
        boolean pairing = state == DaemonStarter.State.NEED_PAIR;
        code.setVisibility(pairing ? VISIBLE : GONE);
        pair.setVisibility(pairing ? VISIBLE : GONE);
        if (state == DaemonStarter.State.NEED_PAIR) {
            message.setText("不需要电脑。点「打开无线调试」，再点「使用配对码配对设备」，窗口先别关，把 6 位数字填在这里。");
        } else if (state == DaemonStarter.State.NEED_WIFI) {
            message.setText(detail + "。打开「开发者选项 → 无线调试」，然后点重试。");
        } else if (state == DaemonStarter.State.STARTING) {
            message.setText(detail + "…");
        } else {
            message.setText(detail);
        }
    }

    public void hidePanel() {
        setVisibility(GONE);
    }

    private Button button(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(0xFF2C2C2C);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return button;
    }

    private Button gap(Context context, Button button) {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        button.setLayoutParams(lp);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
