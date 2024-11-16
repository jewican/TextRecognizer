package com.capstone.textrecognizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;



public class ControlHandler extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.START_RECOGNITION".equals(intent.getAction())) {
            Log.d("ControlHandler", "Method invocation received! Starting text recognition");
            context.sendBroadcast(new Intent("START_RECOGNITION"));
        }
    }

}
