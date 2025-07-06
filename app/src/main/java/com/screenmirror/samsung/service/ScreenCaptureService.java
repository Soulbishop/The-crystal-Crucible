package com.screenmirror.samsung.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

// This service might be redundant if StreamingService handles MediaProjection directly.
// Keeping it as a placeholder if it's referenced elsewhere.
public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";

    // You might define an interface here if other components need to listen for capture events.
    // public interface ScreenCaptureListener {
    //     void onScreenCaptureStarted();
    //     void onScreenCaptureStopped();
    //     void onFrameCaptured(byte[] jpegData);
    // }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ScreenCaptureService created (placeholder).");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ScreenCaptureService onStartCommand (placeholder).");
        // This service typically would start MediaProjection and image capture,
        // but that logic is now in StreamingService for simplicity.
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
