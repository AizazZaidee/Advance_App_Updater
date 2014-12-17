package com.az.advance.app.updater;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.thin.downloadmanager.DownloadManager;
import com.thin.downloadmanager.DownloadRequest;
import com.thin.downloadmanager.DownloadStatusListener;
import com.thin.downloadmanager.ThinDownloadManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Created by Aizaz on 2014-12-16.
 */
public class UpdateCheck implements IUpdateCheck {
    public static final String DEBUG_TAG = "AdvanceAppUpdater";
    public static final String ACTION_DOWNLOAD_CANCELLED = "com.az.advance.app.updater.DOWNLOAD_CANCELLED";
    public static final String ACTION_NOTIFICATION_REMOVED = "com.az.advance.app.updater.NOTIFICATION_REMOVED";
    public static final String ACTION_DOWNLOAD_UPDATE = "com.az.advance.app.updater.INSTALL_UPDATE";
    public static final String ACTION_UPDATE_DOWNLOADED = "com.az.advance.app.updater.UPDATE_DOWNLOADED";
    private static final String KEY_VERSION_CODE = "versionCode";
    private static final String KEY_UPDATED_URL = "updateURL";

    private final int NOTIFICATION_ID = 1;
    private NotificationCompat.Builder builder;
    private NotificationManager notificationManager;
    private RemoteViews notificationView;
    private int downloadId = -1;
    private DownloadManager downloadManager;
    private String updateDownloadPath;

    private Context context;
    private UpdateCheckCallback updateCheckResult;
    private Handler mHandler = null;
    private String downloadURL = null;

    private static volatile UpdateCheck instance;


    private UpdateCheck(){}

    public static UpdateCheck getInstance(){
        if(instance == null)
            instance = new UpdateCheck();
        return instance;

    }

    @Override
    public void checkForUpdate(final Context context, final String updateURL, final boolean onlyWifi, final UpdateCheckCallback updateCheckResult) {
        if (context == null || updateURL == null) {
            throw new NullPointerException("context or UpdateURL is null");
        }
        this.context = context;
        this.updateCheckResult = updateCheckResult;
        mHandler =  new Handler(context.getMainLooper());

        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = null;

        if (onlyWifi) {
            networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        } else {
            networkInfo = connectivityManager.getActiveNetworkInfo();
        }

        if (networkInfo != null && networkInfo.isConnected()) {
            runAsyncTask(updateURL);
        } else {
            throw new RuntimeException("Network not connected");
        }

    }

    private void runAsyncTask(String updateURL) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            new UpdateCheckTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, updateURL);
        } else {
            new UpdateCheckTask().execute(updateURL);
        }
    }

    @Override
    public int getAppVersionCode(Context context) throws PackageManager.NameNotFoundException {
        if (context != null)
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        else
            throw new NullPointerException("Context is null");
    }

    public interface UpdateCheckCallback {
        void noUpdateAvailable();

        void onUpdateAvailable();
    }

    private class UpdateCheckTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            try {
                JSONObject jsonObject = downloadUrl(params[0]);
                if (jsonObject != null) {
                    int updateVersionCode = jsonObject.getInt(KEY_VERSION_CODE);
                    if (getAppVersionCode(context) < updateVersionCode) {
                        if (updateCheckResult != null) {
                            updateCheckResult.onUpdateAvailable();
                        }
                        //update is available, now create notification
                        downloadURL = jsonObject.getString(KEY_UPDATED_URL);
                        createUpdateAvailableNotification(downloadURL);

                    } else {
                        if (updateCheckResult != null) {
                            updateCheckResult.noUpdateAvailable();
                        }
                        Log.i(DEBUG_TAG, "NO_UPDATE_FOUND_ON_SERVER");
                    }

                }
            } catch (IOException | JSONException | PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        private JSONObject downloadUrl(String updateURL) throws IOException, JSONException {
            InputStream is = null;

            try {
                disableConnectionReuseIfNecessary();
                URL url = new URL(updateURL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                // Starts the query
                conn.connect();
                int response = conn.getResponseCode();
                Log.d(DEBUG_TAG, "The response is: " + response);
                is = conn.getInputStream();

                // Convert the InputStream into a string
                String contentAsString = readIt(is);
                return new JSONObject(contentAsString);

                // Makes sure that the InputStream is closed after the app is
                // finished using it.
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        private void disableConnectionReuseIfNecessary() {
            // HTTP connection reuse which was buggy pre-froyo
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.FROYO) {
                System.setProperty("http.keepAlive", "false");
            }
        }

        private String readIt(InputStream stream) throws IOException {
            StringBuilder textBuilder = new StringBuilder(stream.available());//weak guarantee
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")));
            String line = reader.readLine();

            while (line != null) {
                textBuilder.append(line);
                line = reader.readLine();
            }

            return textBuilder.toString();
        }
    }

    private UpdateNotificationRunnable progRunnable = new UpdateNotificationRunnable();
    public void createUpdateAvailableNotification(String updateURL) {
        if (downloadManager != null) {
            createDownloadingUpdateNotification();
            try {
                mHandler.removeCallbacks(progRunnable);
                mHandler.post(progRunnable);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return;// download is already in progress
        }

        this.downloadURL = updateURL;

        Intent installUpdate = new Intent(ACTION_DOWNLOAD_UPDATE);
        installUpdate.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingInstallUpdateIntent = PendingIntent.getBroadcast(context, 0, installUpdate, 0);

        notificationView = new RemoteViews(context.getPackageName(), R.layout.update_notification_available);

        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat.Builder(context).setSmallIcon(R.drawable.ic_launcher).setTicker(context.getString(R.string.install_update)).setContent(notificationView);
        notificationView.setTextViewText(R.id.tv_2, context.getString(R.string.download_update));
        notificationView.setOnClickPendingIntent(R.id.rl_notify_root, pendingInstallUpdateIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());

    }

    public void createDownloadingUpdateNotification() {

        Intent closeButton = new Intent(ACTION_DOWNLOAD_CANCELLED);
        closeButton.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingCancelDownloadIntent = PendingIntent.getBroadcast(context, 0, closeButton, 0);

        Intent notiRemoved = new Intent(ACTION_NOTIFICATION_REMOVED);
        closeButton.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingNotiRemovedIntent = PendingIntent.getBroadcast(context, 0, notiRemoved, 0);

        notificationView = new RemoteViews(context.getPackageName(), R.layout.update_notification);

        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat.Builder(context).setSmallIcon(R.drawable.ic_launcher).setTicker(context.getString(R.string.download_update)).setContent(notificationView)
                .setDeleteIntent(pendingNotiRemovedIntent);
        notificationView.setProgressBar(R.id.pb_notification, 100, 0, true);
        notificationView.setTextViewText(R.id.tv_1, context.getString(R.string.downloading_update));
        notificationView.setTextViewText(R.id.tv_2, "");
        notificationView.setOnClickPendingIntent(R.id.bt_cancel, pendingCancelDownloadIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());

    }

    private void createNotifyUpdateDownloaded() {

        Intent installUpdate = new Intent(ACTION_UPDATE_DOWNLOADED);
        installUpdate.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingInstallUpdateIntent = PendingIntent.getBroadcast(context, 0, installUpdate, 0);

        notificationView = new RemoteViews(context.getPackageName(), R.layout.update_notification_available);

        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        builder = new NotificationCompat.Builder(context).setSmallIcon(R.drawable.ic_launcher).setTicker(context.getString(R.string.install_update)).setContent(notificationView);
        notificationView.setTextViewText(R.id.tv_2, context.getString(R.string.install_update));
        notificationView.setOnClickPendingIntent(R.id.rl_notify_root, pendingInstallUpdateIntent);

        notificationManager.notify(NOTIFICATION_ID, builder.build());

    }

    public void installUpdate() {
        Intent i;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            i.putExtra(Intent.EXTRA_ALLOW_REPLACE, true);
        } else {
            i = new Intent(Intent.ACTION_VIEW);
        }

        i.setDataAndType(Uri.parse("file://" + updateDownloadPath), "application/vnd.android.package-archive");
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(i);
    }

    private int prog;
    private long total;
    private long done;
    class UpdateNotificationRunnable implements Runnable {

        @Override
        public void run() {
            notificationView.setProgressBar(R.id.pb_notification, 100, prog, false);
            notificationView.setTextViewText(R.id.tv_2,
                    "(" + Formatter.formatShortFileSize(context, done) + "/" + Formatter.formatShortFileSize(context, total) + ") " + String.valueOf(prog)
                            + "%");
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            mHandler.postDelayed(this, 1000 * 2);
        }

    }

    public void downloadUpdate() {
        if (downloadURL != null) {

            cancelDownload();

            Uri downloadUri = Uri.parse(downloadURL);
            Uri destinationUri = Uri.parse(updateDownloadPath = context.getExternalCacheDir().toString() + "/az_update.apk");

            DownloadRequest downloadRequest = new DownloadRequest(downloadUri).setDestinationURI(destinationUri).setPriority(DownloadRequest.Priority.HIGH)
                    .setDownloadListener(new DownloadStatusListener() {

                        private boolean posted = false;

                        @Override
                        public void onDownloadComplete(int id) {
                            cancelNotificationAndUpdateRunnable();
                            createNotifyUpdateDownloaded();
                        }

                        @Override
                        public void onDownloadFailed(int id, int errorCode, String errorMessage) {
                            cancelNotificationAndUpdateRunnable();
                            if (errorCode != DownloadManager.ERROR_DOWNLOAD_CANCELLED) {
                                try {
                                    notificationView.setProgressBar(R.id.pb_notification, 100, 0, true);
                                    notificationView.setTextViewText(R.id.tv_1, context.getString(R.string.error_network));
                                    notificationView.setTextViewText(R.id.tv_2, context.getString(R.string.error));
                                    notificationManager.notify(NOTIFICATION_ID, builder.build());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            downloadManager = null;
                        }

                        @Override
                        public void onProgress(int id, long totalBytes, long downlaodedBytes, int progress) {
                            // update notification
                            prog = progress;
                            total = totalBytes;
                            done = downlaodedBytes;
                            if (!posted) {
                                posted = true;
                                mHandler.post(progRunnable);
                            }
                            // }
                        }

                    });

            downloadManager = new ThinDownloadManager();
            downloadId = downloadManager.add(downloadRequest);
        } else {
            Toast.makeText(context.getApplicationContext(), R.string.update_failure, Toast.LENGTH_LONG).show();
            instance.cancelNotification();
        }
    }

    public void cancelNotificationAndUpdateRunnable() {
        try {
            mHandler.removeCallbacks(progRunnable);

        } catch (Exception e) {

            e.printStackTrace();
        }
        cancelNotification();
    }

    public void cancelNotification() {
        try {
            notificationManager.cancel(NOTIFICATION_ID);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cancelDownload() {
        cancelNotificationAndUpdateRunnable();

        try {
            downloadManager.cancel(downloadId);
            downloadManager.release();

        } catch (Exception e) {

            e.printStackTrace();
        }
    }
}
