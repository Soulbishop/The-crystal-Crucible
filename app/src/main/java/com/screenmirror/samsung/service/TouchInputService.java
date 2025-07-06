package com.screenmirror.samsung.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONException;
import org.json.JSONObject;

public class TouchInputService extends AccessibilityService implements StreamingService.TouchCallback {

    private static final String TAG = "TouchInputService";
    private Handler mainHandler;

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
    }

    @Override
    public void onTouchEvent(float x, float y, String action) {
        Log.d(TAG, "Received touch event via callback: x=" + x + ", y=" + y + ", action=" + action);

        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            // GestureDescription.Builder does not have getStrokeCount().
            // We just add the stroke if the action is "down", "move", or "up" for basic events.
            // For complex gestures (multi-finger, swipes), more advanced logic to build strokes is needed.
            // For now, we'll ensure at least one stroke is added if any action is triggered.
            if ("down".equals(action) || "move".equals(action) || "up".equals(action)) {
                 builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
            }


            // We check getStrokeCount() implicitly by checking if a stroke was added
            // (or if builder.build() would succeed without error)
            // If no stroke was added, dispatchGesture will throw an error or do nothing.
            // A more robust check might be `if (!builder.getStrokes().isEmpty())` but getStrokes is not public.
            // Just checking if builder.build() can be called:
            try {
                GestureDescription gesture = builder.build(); // Will throw if no strokes
                dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                    @Override // Corrected: This is the correct way to override
                    public void onGestureCompleted(GestureDescription gestureDescription) {
                        super.onGestureCompleted(gestureDescription); // Corrected: This super call is now correct
                        Log.d(TAG, "Gesture completed for action: " + action);
                    }

                    @Override // Corrected: This is the correct way to override
                    public void onGestureCancelled(GestureDescription gestureDescription) {
                        super.onGestureCancelled(gestureDescription); // Corrected: This super call is now correct
                        Log.w(TAG, "Gesture cancelled for action: " + action);
                    }
                }, null);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error dispatching gesture: No strokes added or invalid gesture. " + e.getMessage());
            }
        });
    }
}
