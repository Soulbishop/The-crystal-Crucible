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
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.nio.ByteBuffer;

import com.screenmirror.samsung.R;

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
        Toast.makeText(this, "Service: onCreate Started!", Toast.LENGTH_SHORT).show(); // FIRST SERVICE TOAST
        Log.d(TAG, "ScreenCaptureService: onCreate called.");

        // --- MOVED getScreenMetrics() here ---
        try {
            getScreenMetrics();
            Toast.makeText(this, "Service: Metrics Obtained!", Toast.LENGTH_SHORT).show(); // New Toast
            Log.d(TAG, "ScreenCaptureService: Screen metrics obtained in onCreate.");
        } catch (Exception e) {
            Log.e(TAG, "ScreenCaptureService: Error getting screen metrics in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Service: Metrics Error!", Toast.LENGTH_LONG).show(); // Critical error toast
            // Consider stopping service here if this is fatal
        }
        // --- End moved getScreenMetrics() ---
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "Service: onStartCommand entry", Toast.LENGTH_SHORT).show(); // SECOND SERVICE TOAST
        Log.d(TAG, "ScreenCaptureService: onStartCommand called, attempting foreground.");

        createNotificationChannel(); // Channel must exist before notification
        startForeground(NOTIFICATION_ID, createNotification());
        Toast.makeText(this, "Service: Foreground Initiated!", Toast.LENGTH_SHORT).show(); // THIRD SERVICE TOAST
        Log.d(TAG, "ScreenCaptureService: Foreground service started.");

        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");

        if (resultCode != 0 && data != null) {
            Toast.makeText(this, "Service: Received MediaProjection data.", Toast.LENGTH_SHORT).show(); // FOURTH SERVICE TOAST
            Log.d(TAG, "ScreenCaptureService: MediaProjection data received.");

            startScreenCapture(resultCode, data);
        } else {
            Toast.makeText(this, "Service: No MediaProjection data! Stopping.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "ScreenCaptureService: No MediaProjection resultCode or data provided. Stopping service.");
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
        stopScreenCapture();
        instance = null;
        Log.d(TAG, "ScreenCaptureService: onDestroy called. Service destroyed.");
        Toast.makeText(this, "Service: onDestroy", Toast.LENGTH_SHORT).show(); // Debug Toast
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null && notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Capture Service",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                channel.setDescription("Screen mirroring service notification");
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "ScreenCaptureService: Notification channel created.");
            } else if (notificationManager != null) {
                Log.d(TAG, "ScreenCaptureService: Notification channel already exists.");
            }
        }
    }

    private Notification createNotification() {
        Log.d(TAG, "ScreenCaptureService: Creating notification.");
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Mirroring Active")
            .setContentText("Your screen is being mirrored to iPad")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build();
    }

    private void getScreenMetrics() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();

        // Using modern getRealMetrics for API 30+ but keeping older path for safety, though only one is strictly needed for API 33
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        } else {
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        }

        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
        screenDensity = displayMetrics.densityDpi;

        // Moved log to onCreate.
    }

    private void startScreenCapture(int resultCode, Intent data) {
        Toast.makeText(this, "Service: In startScreenCapture", Toast.LENGTH_SHORT).show(); // FIFTH SERVICE TOAST
        Log.d(TAG, "ScreenCaptureService: startScreenCapture called.");

        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = mediaProjectionManager.getMediaProjection(
            resultCode,
            data
        );

        if (mediaProjection == null) {
            Toast.makeText(this, "Service: MediaProjection NULL! Stopping.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "ScreenCaptureService: Failed to get MediaProjection object after receiving data.");
            stopSelf();
            return;
        }
        Toast.makeText(this, "Service: MediaProjection obtained!", Toast.LENGTH_SHORT).show(); // SIXTH SERVICE TOAST
        Log.d(TAG, "ScreenCaptureService: MediaProjection obtained.");

        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2 // Max images
        );

        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                android.media.Image image = null;
                try {
                    // Toast.makeText(ScreenCaptureService.this, "Service: Image Available!", Toast.LENGTH_SHORT).show(); // DEBUG TOAST
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        // Toast.makeText(ScreenCaptureService.this, "Service: Image Acquired!", Toast.LENGTH_SHORT).show(); // DEBUG TOAST
                        android.media.Image.Plane[] planes = image.getPlanes();
                        
                        if (planes != null && planes.length > 0 && planes[0].getBuffer() != null) {
                            ByteBuffer buffer = planes[0].getBuffer();
                            int pixelStride = planes[0].getPixelStride();
                            int rowStride = planes[0].getRowStride();

                            byte[] bitmapData = new byte[buffer.remaining()];
                            buffer.get(bitmapData);

                            if (frameCallback != null) {
                                frameCallback.onFrameAvailable(bitmapData);
                            }
                            // Toast.makeText(ScreenCaptureService.this, "Service: Frame Sent!", Toast.LENGTH_SHORT).show(); // DEBUG TOAST
                        } else {
                            Log.e(TAG, "ScreenCaptureService: Image planes or buffer are null.");
                            Toast.makeText(ScreenCaptureService.this, "Service: Image planes/buffer null!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w(TAG, "ScreenCaptureService: acquireLatestImage returned null.");
                        Toast.makeText(ScreenCaptureService.this, "Service: Acquired null image!", Toast.LENGTH_SHORT).show();
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "ScreenCaptureService: ImageReader acquire error (IllegalState): " + e.getMessage());
                    Toast.makeText(ScreenCaptureService.this, "Service: ImageReader state error!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "ScreenCaptureService: Error processing image: " + e.getMessage(), e);
                    Toast.makeText(ScreenCaptureService.this, "Service: Image processing error!", Toast.LENGTH_LONG).show();
                    // Consider stopping the service here if errors are persistent and critical.
                } finally {
                    if (image != null) {
                        try {
                            image.close();
                            Log.d(TAG, "ScreenCaptureService: Image closed.");
                        } catch (Exception e) {
                            Log.e(TAG, "ScreenCaptureService: Error closing image: " + e.getMessage(), e);
                        }
                    }
                }
            }
        }, null);

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenMirror",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(),
            null,
            null
        );

        Log.d(TAG, "ScreenCaptureService: Screen capture started.");
        Toast.makeText(this, "Service: Screen capture started!", Toast.LENGTH_SHORT).show(); // SEVENTH SERVICE TOAST
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

        stopForeground(true);
        Log.d(TAG, "ScreenCaptureService: Screen capture stopped.");
    }

    public int[] getScreenDimensions() {
        return new int[]{screenWidth, screenHeight};
    }

    public void setFrameCallback(FrameCallback callback) {
        this.frameCallback = callback;
    }
}
