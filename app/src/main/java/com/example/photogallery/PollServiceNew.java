package com.example.photogallery;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;

public class PollServiceNew extends JobService {

    private static final String TAG = "PollServiceNew";

    private static final int JOB_ID = 1;

    private PollTask mCurrentTask;
    private Context mContext;

    @Override
    public boolean onStartJob(JobParameters params) {
        mCurrentTask = new PollTask();
        mCurrentTask.execute(params);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mCurrentTask != null) {
            mCurrentTask.cancel(true);
        }
        return true;
    }

    @SuppressLint("StaticFieldLeak")
    private class PollTask extends AsyncTask<JobParameters, Void, Void> {

        @Override
        protected Void doInBackground(JobParameters... jobParameters) {
            JobParameters jobParams = jobParameters[0];
            String query = QueryPreferences.getStoredQuery(PollServiceNew.this);
            String lastResultId = QueryPreferences.getLastResultId(PollServiceNew.this);
            List<GalleryItem> items;

            if (query == null) {
                items = new FlickrFetchr().fetchRecentPhotos(1);
            } else {
                items = new FlickrFetchr().searchPhotos(query, 1);
            }

            if (items.size() == 0) {
                jobFinished(jobParams, false);
                return null;
            }

            String resultId = items.get(0).getId();
            if (resultId.equals(lastResultId)) {
                Log.i(TAG, "Got an old result: " + resultId);
            } else {
                Log.i(TAG, "Got a new result: " + resultId);
            }

            Resources resources = getResources();
            Intent i = PhotoGalleryActivity.newIntent(PollServiceNew.this);
            PendingIntent pi = PendingIntent.getActivity(PollServiceNew.this, 0, i, 0);

            Notification notification = new NotificationCompat.Builder(PollServiceNew.this)
                    .setTicker(resources.getString(R.string.new_pictures_title))
                    .setSmallIcon(android.R.drawable.ic_menu_report_image)
                    .setContentTitle(resources.getString(R.string.new_pictures_title))
                    .setContentText(resources.getString(R.string.new_pictures_text))
                    .setContentIntent(pi)
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setAutoCancel(true)
                    .build();

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(PollServiceNew.this);
            notificationManager.notify(0, notification);

            QueryPreferences.setLastResultId(PollServiceNew.this, resultId);

            jobFinished(jobParams, false);
            return null;
        }
    }

    public static boolean hasScheduled(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        boolean hasBeenScheduled = false;
        assert scheduler != null;
        for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
            if (jobInfo.getId() == JOB_ID) {
                hasBeenScheduled = true;
                break;
            }
        }
        return hasBeenScheduled;
    }

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        JobInfo jobInfo = new JobInfo.Builder(JOB_ID, new ComponentName(context, PollServiceNew.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(1000*60*15)
                .setPersisted(true)
                .build();
        assert scheduler != null;
        scheduler.schedule(jobInfo);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        assert scheduler != null;
        scheduler.cancel(JOB_ID);
    }
}
