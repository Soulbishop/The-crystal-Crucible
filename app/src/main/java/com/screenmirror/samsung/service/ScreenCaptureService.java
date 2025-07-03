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

public class ScreenCaptureService extends Service {

    private static final String TAG = "ScreenCaptureService";
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
        createNotificationChannel();
        getScreenMetrics();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // --- MODIFIED SECTION START ---
        int resultCode = intent.getIntExtra("resultCode", 0); // Get the result code
        Intent data = intent.getParcelableExtra("data");     // Get the data intent

        if (resultCode != 0 && data != null) { // Check if data is properly received
            startForeground(NOTIFICATION_ID, createNotification());
            // Pass resultCode and data directly to startScreenCapture
            startScreenCapture(resultCode, data);
        } else {
            Log.e(TAG, "No MediaProjection resultCode or data provided. Stopping service.");
            stopSelf();
        }
        // --- MODIFIED SECTION END ---

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopScreenCapture();
        instance = null;
        Log.d(TAG, "ScreenCaptureService destroyed"); // Added log for clarity
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Screen mirroring service notification");

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Mirroring Active")
            .setContentText("Your screen is being mirrored to iPad")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Make sure this icon exists or provide your own
            .setOngoing(true)
            .build();
    }

    private void getScreenMetrics() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();

        // Using modern getRealMetrics for API 30+ but keeping older path for safety, though only one is strictly needed for API 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        } else { // Older APIs
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics); // getRealMetrics is available before R too
        }

        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
        screenDensity = displayMetrics.densityDpi;

        Log.d(TAG, "Screen metrics: " + screenWidth + "x" + screenHeight + ", density: " + screenDensity);
    }

    // --- MODIFIED METHOD SIGNATURE ---
    private void startScreenCapture(int resultCode, Intent data) {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = mediaProjectionManager.getMediaProjection(
            resultCode, // Use the resultCode from MainActivity
            data        // Use the data Intent from MainActivity
        );

        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection object after receiving data.");
            stopSelf(); // Stop if MediaProjection cannot be obtained
            return;
        }

        // Create ImageReader for capturing frames
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888, // RGBA_8888 is a good format for most uses
            2 // Max images
        );

        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                android.media.Image image = null; // Declare image outside try-finally
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        android.media.Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        // int rowPadding = rowStride - pixelStride * screenWidth; // Not directly used in byte[] creation

                        // Create bitmap data - Note: This simple buffer.get() only gets the raw bytes.
                        // If you need a proper bitmap for streaming, more complex processing is needed
                        // to handle rowStride and pixelStride for different image formats.
                        byte[] bitmapData = new byte[buffer.remaining()];
                        buffer.get(bitmapData);

                        // Notify streaming service of new frame
                        if (frameCallback != null) {
                            frameCallback.onFrameAvailable(bitmapData);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing image", e);
                } finally {
                    if (image != null) {
                        image.close();
                    }
                }
            }
        }, null);

        // Create virtual display
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenMirror",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, // AUTO_MIRROR for mirroring
            imageReader.getSurface(),
            null,
            null
        );

        Log.d(TAG, "Screen capture started");
    }

    private void stopScreenCapture() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        // Ensure notification is removed when service stops
        stopForeground(true); // Call this to remove the persistent notification
        Log.d(TAG, "Screen capture stopped");
    }

    public int[] getScreenDimensions() {
        return new int[]{screenWidth, screenHeight};
    }

    public void setFrameCallback(FrameCallback callback) {
        this.frameCallback = callback;
    }
}
