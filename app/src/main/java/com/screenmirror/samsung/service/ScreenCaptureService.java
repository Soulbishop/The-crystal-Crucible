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
        Intent mediaProjectionData = null;
        if (intent != null) {
            mediaProjectionData = intent.getParcelableExtra("mediaProjectionData");
        }
        
        if (mediaProjectionData != null) {
            startForeground(NOTIFICATION_ID, createNotification());
            startScreenCapture(mediaProjectionData);
        } else {
            Log.e(TAG, "No MediaProjection data provided");
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build();
    }
    
    private void getScreenMetrics() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        } else {
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        }
        
        screenWidth = displayMetrics.widthPixels;
        screenHeight = displayMetrics.heightPixels;
        screenDensity = displayMetrics.densityDpi;
        
        Log.d(TAG, "Screen metrics: " + screenWidth + "x" + screenHeight + ", density: " + screenDensity);
    }
    
    private void startScreenCapture(Intent mediaProjectionData) {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mediaProjection = mediaProjectionManager.getMediaProjection(
            android.app.Activity.RESULT_OK,
            mediaProjectionData
        );
        
        // Create ImageReader for capturing frames
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        );
        
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                android.media.Image image = reader.acquireLatestImage();
                if (image != null) {
                    try {
                        android.media.Image.Plane[] planes = image.getPlanes();
                        ByteBuffer buffer = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * screenWidth;
                        
                        // Create bitmap data
                        byte[] bitmapData = new byte[buffer.remaining()];
                        buffer.get(bitmapData);
                        
                        // Notify streaming service of new frame
                        if (frameCallback != null) {
                            frameCallback.onFrameAvailable(bitmapData);
                        }
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing image", e);
                    } finally {
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
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
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
        
        Log.d(TAG, "Screen capture stopped");
    }
    
    public int[] getScreenDimensions() {
        return new int[]{screenWidth, screenHeight};
    }
    
    public void setFrameCallback(FrameCallback callback) {
        this.frameCallback = callback;
    }
}

