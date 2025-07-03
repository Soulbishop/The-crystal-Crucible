package com.screenmirror.samsung.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.content.ComponentName; // ADD THIS IMPORT

public class TouchInputService extends AccessibilityService {

    private static final String TAG = "TouchInputService";

    public static TouchInputService instance;

    // RENAMED METHOD: isEnabled -> isAccessibilityServiceEnabled
    public static boolean isAccessibilityServiceEnabled(Context context) {
        // This part of the code is also slightly improved for robustness
        // It uses ComponentName which is generally safer for checking accessibility service status
        ComponentName cn = new ComponentName(context, TouchInputService.class);
        String flat = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (flat == null || flat.isEmpty()) { // Check for null or empty string
            return false;
        }
        return flat.contains(cn.flattenToString());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "TouchInputService connected");

        // Set up touch coordinate handling from streaming service
        if (StreamingService.instance != null) {
            StreamingService.instance.setTouchCallback(new StreamingService.TouchCallback() {
                @Override
                public void onTouchReceived(float x, float y) {
                    simulateTouch(x, y);
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "TouchInputService destroyed");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We don't need to handle accessibility events for this use case
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "TouchInputService interrupted");
    }

    public void simulateTouch(float iPadX, float iPadY) {
        // Get screen dimensions from ScreenCaptureService
        if (ScreenCaptureService.instance == null) {
            Log.e(TAG, "ScreenCaptureService not available");
            return;
        }

        int[] screenDimensions = ScreenCaptureService.instance.getScreenDimensions();
        if (screenDimensions == null || screenDimensions.length < 2) {
            Log.e(TAG, "Cannot get screen dimensions");
            return;
        }

        int screenWidth = screenDimensions[0];
        int screenHeight = screenDimensions[1];

        // iPad Air 2 dimensions: 2048 x 1536
        float iPadWidth = 2048f;
        float iPadHeight = 1536f;

        // Map iPad coordinates to Android screen coordinates
        float androidX = (iPadX / iPadWidth) * screenWidth;
        float androidY = (iPadY / iPadHeight) * screenHeight;

        // Ensure coordinates are within screen bounds
        float clampedX = Math.max(0f, Math.min(androidX, screenWidth));
        float clampedY = Math.max(0f, Math.min(androidY, screenHeight));

        Log.d(TAG, "Touch mapping: iPad(" + iPadX + ", " + iPadY + ") -> Android(" + clampedX + ", " + clampedY + ")");

        // Create gesture description for touch
        Path path = new Path();
        path.moveTo(clampedX, clampedY);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        GestureDescription.StrokeDescription strokeDescription =
            new GestureDescription.StrokeDescription(path, 0, 100);
        gestureBuilder.addStroke(strokeDescription);

        GestureDescription gesture = gestureBuilder.build();

        // Dispatch the gesture
        boolean result = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "Touch gesture completed at (" + clampedX + ", " + clampedY + ")");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, "Touch gesture cancelled");
            }
        }, null);

        if (!result) {
            Log.e(TAG, "Failed to dispatch touch gesture");
        }
    }

    public void simulateLongPress(float iPadX, float iPadY) {
        if (ScreenCaptureService.instance == null) {
            Log.e(TAG, "ScreenCaptureService not available");
            return;
        }

        int[] screenDimensions = ScreenCaptureService.instance.getScreenDimensions();
        if (screenDimensions == null || screenDimensions.length < 2) {
            Log.e(TAG, "Cannot get screen dimensions");
            return;
        }

        int screenWidth = screenDimensions[0];
        int screenHeight = screenDimensions[1];
        float iPadWidth = 2048f;
        float iPadHeight = 1536f;

        // Map coordinates
        float androidX = (iPadX / iPadWidth) * screenWidth;
        float androidY = (iPadY / iPadHeight) * screenHeight;

        // Clamp coordinates
        float clampedX = Math.max(0f, Math.min(androidX, screenWidth));
        float clampedY = Math.max(0f, Math.min(androidY, screenHeight));

        // Create long press gesture (longer duration)
        Path path = new Path();
        path.moveTo(clampedX, clampedY);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        GestureDescription.StrokeDescription strokeDescription =
            new GestureDescription.StrokeDescription(path, 0, 1000); // 1 second
        gestureBuilder.addStroke(strokeDescription);

        GestureDescription gesture = gestureBuilder.build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "Long press gesture completed at (" + clampedX + ", " + clampedY + ")");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, "Long press gesture cancelled");
            }
        }, null);
    }

    public void simulateSwipe(float startX, float startY, float endX, float endY) {
        if (ScreenCaptureService.instance == null) {
            Log.e(TAG, "ScreenCaptureService not available");
            return;
        }

        int[] screenDimensions = ScreenCaptureService.instance.getScreenDimensions();
        if (screenDimensions == null || screenDimensions.length < 2) {
            Log.e(TAG, "Cannot get screen dimensions");
            return;
        }

        int screenWidth = screenDimensions[0];
        int screenHeight = screenDimensions[1];
        float iPadWidth = 2048f;
        float iPadHeight = 1536f;

        // Map coordinates
        float androidStartX = (startX / iPadWidth) * screenWidth;
        float androidStartY = (startY / iPadHeight) * screenHeight;
        float androidEndX = (endX / iPadWidth) * screenWidth;
        float androidEndY = (endY / iPadHeight) * screenHeight;

        // Clamp coordinates
        float clampedStartX = Math.max(0f, Math.min(androidStartX, screenWidth));
        float clampedStartY = Math.max(0f, Math.min(androidStartY, screenHeight));
        float clampedEndX = Math.max(0f, Math.min(androidEndX, screenWidth));
        float clampedEndY = Math.max(0f, Math.min(androidEndY, screenHeight));

        // Create swipe gesture
        Path path = new Path();
        path.moveTo(clampedStartX, clampedStartY);
        path.lineTo(clampedEndX, clampedEndY);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        GestureDescription.StrokeDescription strokeDescription =
            new GestureDescription.StrokeDescription(path, 0, 300); // 300ms swipe
        gestureBuilder.addStroke(strokeDescription);

        GestureDescription gesture = gestureBuilder.build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "Swipe gesture completed from (" + clampedStartX + ", " + clampedStartY +
                            ") to (" + clampedEndX + ", " + clampedEndY + ")");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w(TAG, "Swipe gesture cancelled");
            }
        }, null);
    }
}
