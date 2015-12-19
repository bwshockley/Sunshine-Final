package com.example.android.sunshine.app;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by benjaminshockley on 12/17/15.
 */
public class SunshineListenerService extends WearableListenerService {

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        if (messageEvent.getPath().equals("/forecast")) {
            final String message = new String(messageEvent.getData());
            Log.v("SunshineListener", "Message path received on watch is: " + messageEvent.getPath());
            Log.v("SunshineListener", "Message received on watch is: " + message);

            String[] messageParts = message.split(",");

            int weatherId = Integer.parseInt(messageParts[0]);
            String high = messageParts[1];
            String low = messageParts[2];

            // Broadcast message to wearable activity for display
            Intent messageIntent = new Intent();
            messageIntent.setAction(Intent.ACTION_SEND);
            messageIntent.putExtra("weatherId", weatherId);
            messageIntent.putExtra("high", high);
            messageIntent.putExtra("low", low);
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
        }
        else {
            super.onMessageReceived(messageEvent);
        }
    }
}