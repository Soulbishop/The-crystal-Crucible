package com.screenmirror.samsung.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

// No need for JSONException or JSONObject if input is already parsed
// import org.json.JSONException;
// import org.json.JSONObject;

public class TouchInputService extends AccessibilityService implements StreamingService.TouchCallback {

    private static final String TAG = "TouchInputService";
    private Handler mainHandler;

    // State variables to track the current touch position for "move" events
    private float lastTouchX = -1;
    private float lastTouchY = -1;
    private boolean isTouching = false; // To know if a "down" event has occurred

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "TouchInputService onCreate.");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "TouchInputService connected.");
        StreamingService streamingService = StreamingService.getInstance();
        if (streamingService != null) {
            streamingService.setTouchCallback(this);
            Log.d(TAG, "TouchInputService registered with StreamingService.");
        } else {
            Log.e(TAG, "StreamingService instance is null. Cannot register TouchCallback.");
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not directly used for receiving touch input from iPad, but required by AccessibilityService
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "TouchInputService interrupted.");
        // Reset touch state if interrupted unexpectedly
        isTouching = false;
        lastTouchX = -1;
        lastTouchY = -1;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "TouchInputService disconnected.");
        StreamingService streamingService = StreamingService.getInstance();
        if (streamingService != null) {
            streamingService.setTouchCallback(null);
            Log.d(TAG, "TouchInputService unregistered from StreamingService.");
        }
        // Ensure state is reset on destroy
        isTouching = false;
        lastTouchX = -1;
        lastTouchY = -1;
    }

    @Override
    public void onTouchEvent(float x, float y, String action) {
        Log.d(TAG, "Received touch event via callback: x=" + x + ", y=" + y + ", action=" + action);

        // Post to main handler to ensure UI operations are on the main thread
        mainHandler.post(() -> {
            Path path = new Path();
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription stroke = null;

            long startTime = 0; // When the stroke starts
            long duration = 1;  // Duration of the stroke in milliseconds. Default to 1ms for quick actions.

            switch (action) {
                case "down":
                    path.moveTo(x, y);
                    stroke = new GestureDescription.StrokeDescription(path, startTime, duration);
                    isTouching = true;
                    lastTouchX = x;
                    lastTouch
