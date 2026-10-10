package com.daniel.dextouch;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public final class PairCodeReceiver extends BroadcastReceiver {
    public static final String EXTRA_CODE = "code";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle results = RemoteInput.getResultsFromIntent(intent);
        if (results == null) return;
        CharSequence raw = results.getCharSequence(EXTRA_CODE);
        if (raw == null) return;
        DaemonStarter starter = DaemonStarter.peek();
        if (starter != null) {
            starter.pairAndStart(raw.toString());
            return;
        }
        Intent open = new Intent(context, MainActivity.class);
        open.putExtra("pair_code", raw.toString());
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(open);
    }
}
