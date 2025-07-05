package com.screenmirror.samsung.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.GestureDescription;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class TouchInputService extends AccessibilityService implements StreamingService.TouchCallback {

    private static final String TAG = "TouchInputService";
    private WindowManager windowManager;
    private View overlayView; // For visual feedback if needed
    private int screenWidth;
    private int screenHeight;

    // Scaling factors
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TouchInputService onCreate");
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        // Initialize screen dimensions (will be updated when service connects to StreamingService)
        Display display = windowManager.getDefaultDisplay();
        screenWidth = display.getWidth();
        screenHeight = display.getHeight();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "TouchInputService connected");

        // Configure AccessibilityServiceInfo for dispatchGesture
        AccessibilityServiceInfo info = getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS; // For key events if needed
        info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE; // For touch events
        setServiceInfo(info);

        // Register this service as a TouchCallback with StreamingService
        if (StreamingService.instance != null) {
            StreamingService.instance.setTouchCallback(this);
            Log.d(TAG, "TouchInputService registered with StreamingService.");
        } else {
            Log.e(TAG, "StreamingService.instance is null, cannot register TouchCallback.");
            Toast.makeText(this, "Touch Service: StreamingService not ready.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // This method is called when an accessibility event occurs.
        // We generally don't process these for screen mirroring, but it's required.
        // Log.d(TAG, "Accessibility Event: " + event.getEventType());
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        // This method can be used to intercept key events if needed for control.
        return super.onKeyEvent(event);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "TouchInputService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "TouchInputService onDestroy");
        if (StreamingService.instance != null) {
            StreamingService.instance.setTouchCallback(null); // Unregister
        }
    }

    // --- Implementation of StreamingService.TouchCallback ---

    @Override
    public void onTouchReceived(float x, float y) {
        Log.d(TAG, "onTouchReceived: x=" + x + ", y=" + y);
        // Simulate a tap at the received coordinates
        dispatchGesture(x, y, x, y, 1, 1); // Simple tap (down and up immediately)
    }

    @Override
    public void onLongPressReceived(float x, float y) {
        Log.d(TAG, "onLongPressReceived: x=" + x + ", y=" + y);
        dispatchGesture(x, y, x, y, 0, 500); // Long press (hold for 500ms)
    }

    @Override
    public void onSwipeReceived(float startX, float startY, float endX, float endY) {
        Log.d(TAG, "onSwipeReceived: startX=" + startX + ", startY=" + startY + ", endX=" + endX + ", endY=" + endY);
        dispatchGesture(startX, startY, endX, endY, 0, 200); // Swipe (duration 200ms)
    }

    @Override
    public void onPinchReceived(float scale) {
        // Pinch gesture - for simplicity, we'll map this to a zoom in/out action
        // This is a complex gesture for AccessibilityService to simulate accurately,
        // but we can try a simple two-finger touch/move
        Log.d(TAG, "onPinchReceived: scale=" + scale);

        // For simplicity, we'll just log this for now.
        // A true pinch gesture simulation requires complex multi-pointer GestureDescription.
        // If zoom is needed, we might consider sending specific key events (e.g. for browser zoom)
        // or a simpler single-touch gesture that implies zoom for some apps.
        // For now, focusing on basic touch.
    }

    // Helper method to dispatch gestures
    private void dispatchGesture(float startX, float startY, float endX, float endY, long downTime, long duration) {
        // Scale coordinates to device screen dimensions if necessary
        // Assuming incoming coordinates are normalized (0-1) or absolute pixels matching capture
        // If they are absolute pixels from capture, they should map directly.
        // For normalized: x = x * screenWidth, y = y * screenHeight
        // For now, assume coordinates directly map.

        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();

        // Start stroke (finger down)
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, downTime, duration));

        // Execute the gesture
        boolean dispatched = dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "Gesture completed: " + gestureDescription.toString());
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.e(TAG, "Gesture cancelled: " + gestureDescription.toString());
            }
        }, null);

        if (!dispatched) {
            Log.e(TAG, "Failed to dispatch gesture.");
        }
    }

    /**
     * Updates the internal screen dimensions based on the latest capture dimensions
     * received from ScreenCaptureService. This is critical for accurate coordinate mapping.
     * @param width The width of the captured screen.
     * @param height The height of the captured screen.
     */
    public void updateScreenDimensions(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
        Log.d(TAG, "Updated TouchInputService screen dimensions to: " + width + "x" + height);
        // Recalculate scaling factors if needed based on host (iPad) vs target (Samsung)
    }
}
