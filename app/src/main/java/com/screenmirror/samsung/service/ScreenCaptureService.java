package com.screenmirror.samsung.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import java.nio.ByteBuffer;

import com.screenmirror.samsung.R;

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService"; // Keep this tag
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "screen_capture_channel";

    public static ScreenCaptureService instance;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private int screenDensity = 0;

    // Callback interface for frame data
    public interface FrameCallback {
        void onFrameAvailable(byte[] frameData);
    }

    private FrameCallback frameCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "Service: onCreate() called."); // Debug Log
        createNotificationChannel();
        getScreenMetrics();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service: onStartCommand() entry."); // Debug Log
        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");

        Log.d(TAG, "Service: onStartCommand - resultCode: " + resultCode + ", data is null: " + (data == null)); // Debug Log

        if (resultCode != 0 && data != null) {
            Log.d(TAG, "Service: MediaProjection data received. Attempting to start foreground."); // Debug Log
            try {
                startForeground(NOTIFICATION_ID, createNotification());
                Log.d(TAG, "Service: startForeground() successful. Notification should be visible."); // Debug Log
            } catch (Exception e) {
                // This catch block is highly unlikely to hit if the notification briefly appears.
                Log.e(TAG, "Service: EXCEPTION during startForeground!", e);
                stopSelf();
                return START_NOT_STICKY; // Or START_REDELIVER_INTENT if you want the intent resent
            }

            // Immediately after successful startForeground, attempt screen capture
            try {
                startScreenCapture(resultCode, data);
                Log.d(TAG, "Service: startScreenCapture() called successfully."); // Debug Log
            } catch (Exception e) {
                Log.e(TAG, "Service: CRITICAL EXCEPTION caught in startScreenCapture! Stopping service.", e);
                stopSelf();
                return START_NOT_STICKY;
            }
        } else {
            Log.e(TAG, "Service: No MediaProjection resultCode or data provided. Stopping service.");
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service: onDestroy() called. Stopping screen capture."); // Debug Log
        stopScreenCapture();
        instance = null;
        Log.d(TAG, "Service: ScreenCaptureService destroyed."); // Debug Log
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Screen mirroring service notification");

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "Service: Notification Channel created."); // Debug Log
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Mirroring Active")
            .setContentText("Your screen is being mirrored to iPad")
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure this resource exists and is correct
            .setOngoing(true)
            .build();
    }

    private void getScreenMetrics() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();

        // The if/else block below is functionally redundant as both branches do the same.
        // It's harmless, but can be simplified if desired.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        } else {
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        }

        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
        screenDensity = displayMetrics.densityDpi;

        // Crucial Check: Ensure metrics are not zero
        if (screenWidth == 0 || screenHeight == 0 || screenDensity == 0) {
            Log.e(TAG, "Service: ERROR! Screen metrics are zero or invalid! " +
                       "Width: " + screenWidth + ", Height: " + screenHeight + ", Density: " + screenDensity);
            // Consider throwing a RuntimeException here to crash earlier if this state is truly fatal.
            // throw new RuntimeException("Invalid screen metrics detected.");
        } else {
            Log.d(TAG, "Service: Screen metrics obtained - " + screenWidth + "x" + screenHeight + ", density: " + screenDensity); // Debug Log
        }
    }

    private void startScreenCapture(int resultCode, Intent data) {
        Log.d(TAG, "Service: Entering startScreenCapture method."); // Debug Log

        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mediaProjectionManager == null) {
            Log.e(TAG, "Service: MediaProjectionManager is null! Cannot proceed.");
            stopSelf();
            return;
        }

        try {
            Log.d(TAG, "Service: Attempting to get MediaProjection object..."); // Debug Log
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection == null) {
                Log.e(TAG, "Service: Failed to get MediaProjection object (returned null). Stopping service.");
                stopSelf();
                return;
            }
            Log.d(TAG, "Service: MediaProjection object obtained successfully."); // Debug Log
        } catch (SecurityException e) {
            Log.e(TAG, "Service: SecurityException when getting MediaProjection! Permission denied or data invalid.", e);
            stopSelf();
            return;
        } catch (Exception e) {
            Log.e(TAG, "Service: Generic EXCEPTION when getting MediaProjection!", e);
            stopSelf();
            return;
        }

        try {
            Log.d(TAG, "Service: Attempting to create ImageReader. Dims: " +
                       screenWidth + "x" + screenHeight + ", Format: RGBA_8888, Max Images: 2"); // Debug Log
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            );
            Log.d(TAG, "Service: ImageReader created successfully."); // Debug Log
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Service: IllegalArgumentException creating ImageReader! Check screen dimensions (" +
                       screenWidth + "x" + screenHeight + ") or pixel format.", e);
            stopSelf();
            return;
        } catch (Exception e) {
            Log.e(TAG, "Service: Generic EXCEPTION creating ImageReader!", e);
            stopSelf();
            return;
        }

        // Setting the listener doesn't immediately cause a crash, but errors within it would appear later.
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                // Log.v(TAG, "Service: onImageAvailable triggered."); // Use 'v' or 'd' sparingly, this can be noisy.
                android.media.Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        android.media.Image.Plane[] planes = image.getPlanes();
                        if (planes != null && planes.length > 0 && planes[0] != null) {
                            ByteBuffer buffer = planes[0].getBuffer();
                            // int pixelStride = planes[0].getPixelStride(); // Not used currently
                            // int rowStride = planes[0].getRowStride();     // Not used currently

                            if (buffer.hasRemaining()) { // Prevent crash if buffer is unexpectedly empty
                                byte[] bitmapData = new byte[buffer.remaining()];
                                buffer.get(bitmapData);

                                if (frameCallback != null) {
                                    frameCallback.onFrameAvailable(bitmapData);
                                }
                            } else {
                                Log.w(TAG, "Service: Image buffer has no remaining bytes in onImageAvailable.");
                            }
                        } else {
                            Log.w(TAG, "Service: Image planes are null or empty in onImageAvailable.");
                        }
                    } else {
                        Log.w(TAG, "Service: Acquired null image in onImageAvailable.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Service: Error processing image in onImageAvailable!", e);
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }
        }, null); // Handler is null, so it runs on the main looper thread.

        try {
            Log.d(TAG, "Service: Attempting to create VirtualDisplay. Dims: " +
                       screenWidth + "x" + screenHeight + ", Density: " + screenDensity); // Debug Log
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenMirror",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), // Ensure imageReader.getSurface() is not null here
                null,
                null
            );
            Log.d(TAG, "Service: VirtualDisplay created successfully."); // Debug Log

        } catch (Exception e) { // Catch all exceptions for comprehensive debugging
            Log.e(TAG, "Service: CRITICAL EXCEPTION creating VirtualDisplay! " +
                       "Check screen dimensions, density, MediaProjection state, or ImageReader surface.", e);
            stopSelf();
            return;
        }

        Log.d(TAG, "Service: Screen capture fully started and initialized."); // Debug Log - You should see this if everything above succeeds.
    }

    private void stopScreenCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
            Log.d(TAG, "Service: VirtualDisplay released.");
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
            Log.d(TAG, "Service: ImageReader closed.");
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
            Log.d(TAG, "Service: MediaProjection stopped.");
        }

        stopForeground(true);
        Log.d(TAG, "Service: Screen capture stopped. stopForeground called.");
    }

    public int[] getScreenDimensions() {
        return new int[]{screenWidth, screenHeight};
    }

    public void setFrameCallback(FrameCallback callback) {
        this.frameCallback = callback;
    }
}
