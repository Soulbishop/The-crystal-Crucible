package com.screenmirror.samsung.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context; // Added for isAccessibilityServiceEnabled
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.os.Build;
import android.provider.Settings; // Added for isAccessibilityServiceEnabled
import android.text.TextUtils; // Added for isAccessibilityServiceEnabled
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.WindowManager;
// Corrected import for GestureDescription
import android.accessibilityservice.GestureDescription;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
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
        // FLAG_REQUEST_TOUCH_EXPLORATION_MODE is for talkback like features, not typically for dispatchGesture
        // info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
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
        dispatchSinglePointGesture(x, y, 0, 100); // Duration 100ms for a tap
    }

    @Override
    public void onLongPressReceived(float x, float y) {
        Log.d(TAG, "onLongPressReceived: x=" + x + ", y=" + y);
        dispatchSinglePointGesture(x, y, 0, 500); // Long press (hold for 500ms)
    }

    @Override
    public void onSwipeReceived(float startX, float startY, float endX, float endY) {
        Log.d(TAG, "onSwipeReceived: startX=" + startX + ", startY=" + startY + ", endX=" + endX + ", endY=" + endY);
        dispatchSinglePointGesture(startX, startY, endX, endY, 0, 200); // Swipe (duration 200ms)
    }

    @Override
    public void onPinchReceived(float scale) {
        // Pinch gesture - for simplicity, we'll map this to a zoom in/out action
        // This is a complex gesture for AccessibilityService to simulate accurately,
        // but we can try a simple two-finger touch/move
        Log.d(TAG, "onPinchReceived: scale=" + scale);

        // For now, focusing on basic touch.
        // Implementing pinch accurately requires managing two pointers with GestureDescription.
        // Example (conceptual):
        // Path path1 = new Path(); path1.moveTo(x1_start, y1_start); path1.lineTo(x1_end, y1_end);
        // Path path2 = new Path(); path2.moveTo(x2_start, y2_start); path2.lineTo(x2_end, y2_end);
        // gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path1, 0, duration));
        // gestureBuilder.addStroke(new GestureDescription.StrokeDescription(path2, 0, duration));
        // dispatchGesture(gestureBuilder.build(), null, null);
    }

    // Helper method to dispatch single-point gestures (tap, long press, swipe)
    private void dispatchSinglePointGesture(float startX, float startY, float endX, float endY, long startTime, long duration) {
        // Scale coordinates to device screen dimensions if necessary
        // Assuming incoming coordinates are normalized (0-1) or absolute pixels matching capture
        // If they are absolute pixels from capture, they should map directly.
        // For normalized: x = x * screenWidth, y = y * screenHeight
        // For now, assume coordinates directly map.

        Path gesturePath = new Path();
        gesturePath.moveTo(startX, startY);
        if (startX != endX || startY != endY) { // If it's a swipe
            gesturePath.lineTo(endX, endY);
        }

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(gesturePath, startTime, duration));

        // Execute the gesture
        boolean dispatched = dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d(TAG, "Gesture completed.");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.e(TAG, "Gesture cancelled.");
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

    /**
     * Helper method to check if the Accessibility Service is enabled.
     * This is commonly used in MainActivity to guide the user.
     * @param context The application context.
     * @return true if the service is enabled, false otherwise.
     */
    public static boolean isAccessibilityServiceEnabled(Context context) {
        String service = context.getPackageName() + "/" + TouchInputService.class.getCanonicalName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error finding setting, default accessibility to not enabled: " + e.getMessage());
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                splitter.setString(settingValue);
                while (splitter.hasNext()) {
                    String accessibilityService = splitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        Log.v(TAG, "Accessibility service " + service + " is enabled");
                        return true;
                    }
                }
            }
        } else {
            Log.v(TAG, "Accessibility is disabled");
        }
        return false;
    }
}
