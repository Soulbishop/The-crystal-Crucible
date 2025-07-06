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

// This service implements the TouchCallback interface from StreamingService to receive touch events.
public class TouchInputService extends AccessibilityService implements StreamingService.TouchCallback {

    private static final String TAG = "TouchInputService";
    private Handler mainHandler; // Handler for dispatching gestures on the main thread

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
        // Register this service as the TouchCallback for StreamingService
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
        // Unregister this service as the TouchCallback for StreamingService
        StreamingService streamingService = StreamingService.getInstance();
        if (streamingService != null) {
            streamingService.setTouchCallback(null); // Unregister
            Log.d(TAG, "TouchInputService unregistered from StreamingService.");
        }
    }

    // Implementation of StreamingService.TouchCallback
    @Override
    public void onTouchEvent(float x, float y, String action) {
        Log.d(TAG, "Received touch event via callback: x=" + x + ", y=" + y + ", action=" + action);

        // Dispatch gestures on the main thread
        mainHandler.post(() -> {
            Path path = new Path();
            path.moveTo(x, y);

            GestureDescription.Builder builder = new GestureDescription.Builder();
            // Action "down" corresponds to a new stroke
            if ("down".equals(action)) {
                builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1)); // Duration 1ms for tap
            } else if ("move".equals(action)) {
                // For move, typically you'd need to continue an existing stroke.
                // This requires more complex state management of ongoing gestures.
                // For simplicity, we'll treat moves as single points for now, or you'd track stroke IDs.
                builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
            } else if ("up".equals(action)) {
                // For "up", you'd end an existing stroke.
                // Similar to "move", requires state management.
                // For now, it will be treated as a release point.
                builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 1));
            }

            if (builder.getStrokeCount() > 0) {
                dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onGestureCompleted(GestureDescription gestureDescription) {
                        super.onGestureCompleted(gestureDescription);
                        Log.d(TAG, "Gesture completed for action: " + action);
                    }

                    @Override
                    public void onGestureCancelled(GestureDescription gestureDescription) {
                        super.onGestureCancelled(gestureDescription);
                        Log.w(TAG, "Gesture cancelled for action: " + action);
                    }
                }, null);
            }
        });
    }
}
