package com.example.photogallery;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.util.Objects;

public abstract class VisibleFragment extends Fragment {
    private static final String TAG = "VisibleFragment";

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            filter = new IntentFilter(PollServiceNew.ACTION_SHOW_NOTIFICATION);
            Objects.requireNonNull(getActivity()).registerReceiver(mOnShowNotification, filter,
                    PollServiceNew.PERM_PRIVATE, null);
        } else {
            filter = new IntentFilter(PollService.ACTION_SHOW_NOTIFICATION);
            Objects.requireNonNull(getActivity()).registerReceiver(mOnShowNotification, filter,
                    PollService.PERM_PRIVATE, null);
        }

    }

    @Override
    public void onStop() {
        super.onStop();
        Objects.requireNonNull(getActivity()).unregisterReceiver(mOnShowNotification);
    }

    private BroadcastReceiver mOnShowNotification = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "cancelling notification");
            setResultCode(Activity.RESULT_CANCELED);
        }
    };
}
