package com.screenmirror.samsung.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

// This service is now a placeholder. MediaProjection and image capture logic
// have been moved directly into StreamingService for a more streamlined architecture.
// Keep this file if it's referenced elsewhere in your project (e.g., AndroidManifest.xml)
// but it will not actively perform screen capture.
public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScreenCaptureService created (placeholder).");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ScreenCaptureService onStartCommand (placeholder).");
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ScreenCaptureService destroyed (placeholder).");
    }
}
