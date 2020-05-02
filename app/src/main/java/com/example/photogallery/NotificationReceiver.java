package com.example.photogallery;

import android.app.Activity;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "received result: " + getResultCode());

        if (getResultCode() != Activity.RESULT_OK) {
            return;
        }

        int requestCode;
        Notification notification;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            requestCode = intent.getIntExtra(PollServiceNew.REQUEST_CODE, 0);
            notification = (Notification) intent.getParcelableExtra(PollServiceNew.NOTIFICATION);
        } else {
            requestCode = intent.getIntExtra(PollService.REQUEST_CODE, 0);
            notification = (Notification) intent.getParcelableExtra(PollService.NOTIFICATION);
        }

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        assert notification != null;
        notificationManager.notify(requestCode, notification);
    }
}
