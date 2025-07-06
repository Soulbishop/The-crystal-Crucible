package com.screenmirror.samsung.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.screenmirror.samsung.MainActivity;
import com.screenmirror.samsung.R;
import com.screenmirror.samsung.util.ImageUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.WebSocket;
import fi.iki.elonen.WebSocketFrame;

public class StreamingService extends Service {

    private static final String TAG = "StreamingService";
    private static final String CHANNEL_ID = "ScreenMirroringChannel";
    private static final int NOTIFICATION_ID = 1001;

    private MediaProjection mediaProjection;
    private NanoWSD wsServer;
    private HandlerThread imageProcessingThread;
    private Handler imageProcessingHandler;
    private ImageReader imageReader;
    private Surface surface;
    private int screenWidth, screenHeight, screenDensity;

    private static WebSocket currentClientWebSocket; // Store the current client WebSocket

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Mirroring Active")
                .setContentText("Streaming your screen to the iPad.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        imageProcessingThread = new HandlerThread("ImageProcessingThread");
        imageProcessingThread.start();
        imageProcessingHandler = new Handler(imageProcessingThread.getLooper());

        startWebSocketServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getAction() != null && intent.getAction().equals(MainActivity.ACTION_START_STREAMING)) {
                mediaProjection = intent.getParcelableExtra("mediaProjection");
                screenWidth = intent.getIntExtra("width", 1920);
                screenHeight = intent.getIntExtra("height", 1080);
                screenDensity = intent.getIntExtra("density", 1);
                startScreenCapture();
                Log.d(TAG, "Streaming service started with MediaProjection.");
            } else if (intent.getAction() != null && intent.getAction().equals(MainActivity.ACTION_STOP_STREAMING)) {
                stopSelf();
                Log.d(TAG, "Streaming service stopped via intent.");
            }
        }
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Screen Mirroring";
            String description = "Notification for active screen mirroring service.";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startWebSocketServer() {
        wsServer = new NanoWSD(8080) {
            @Override
            protected WebSocket openWebSocket(IHTTPSession handshake) {
                Log.d(TAG, "New WebSocket connection attempt.");
                return new ScreenMirrorWebSocket(handshake);
            }
        };
        try {
            wsServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.d(TAG, "WebSocket server started on port 8080.");
        } catch (IOException e) {
            Log.e(TAG, "Error starting WebSocket server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startScreenCapture() {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null, cannot start screen capture.");
            return;
        }

        imageReader = ImageReader.newInstance(screenWidth, screenHeight,
                android.graphics.PixelFormat.RGBA_8888, 2); // Capture 2 frames at a time
        surface = imageReader.getSurface();

        mediaProjection.createVirtualDisplay("ScreenMirrorDisplay",
                screenWidth, screenHeight, screenDensity,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, imageProcessingHandler); // Use imageProcessingHandler

        imageReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    processImage(image);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error acquiring or processing image: " + e.getMessage());
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, imageProcessingHandler); // Use imageProcessingHandler for image processing
        Log.d(TAG, "Screen capture started.");
    }

    private void processImage(Image image) {
        if (currentClientWebSocket != null && currentClientWebSocket.isOpen()) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            // Convert RGBA to JPEG
            Bitmap bitmap = ImageUtils.imageToBitmap(image);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos); // Adjust quality as needed
            byte[] jpegBytes = bos.toByteArray();

            try {
                // Send JPEG bytes over WebSocket
                currentClientWebSocket.send(jpegBytes);
                Log.d(TAG, "Sent JPEG frame of size: " + jpegBytes.length);
            } catch (Exception e) {
                Log.e(TAG, "Error sending image over WebSocket: " + e.getMessage());
            } finally {
                bitmap.recycle();
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private class ScreenMirrorWebSocket extends WebSocket {
        public ScreenMirrorWebSocket(IHTTPSession handshake) {
            super(handshake);
            Log.d(TAG, "ScreenMirrorWebSocket created.");
        }

        @Override
        protected void onOpen() {
            Log.d(TAG, "WebSocket opened.");
            currentClientWebSocket = this; // Set this as the current active client
            // Optionally send some initial metadata like screen resolution
            try {
                JSONObject welcomeMessage = new JSONObject();
                welcomeMessage.put("type", "welcome");
                welcomeMessage.put("screenWidth", screenWidth);
                welcomeMessage.put("screenHeight", screenHeight);
                send(welcomeMessage.toString());
            } catch (JSONException e) {
                Log.e(TAG, "Error sending welcome message: " + e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean initiatedByRemote) { // MODIFIED LINE
            Log.d(TAG, "WebSocket closed: " + code + ", reason: " + reason + ", initiatedByRemote: " + initiatedByRemote);
            if (this == currentClientWebSocket) {
                currentClientWebSocket = null; // Clear the active client if this one closes
            }
            // Handle WebSocket closure, e.g., attempt reconnection or update UI
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            if (message.isText()) {
                String textMessage = message.getTextPayload();
                Log.d(TAG, "Received message: " + textMessage);
                try {
                    JSONObject json = new JSONObject(textMessage);
                    String type = json.optString("type");
                    if ("touchEvent".equals(type)) {
                        handleTouchEvent(json);
                    }
                    // Add other message types as needed
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing JSON message: " + e.getMessage());
                }
            } else if (message.isBinary()) {
                // Not expecting binary messages from iPad for touch, but good to log
                Log.d(TAG, "Received binary message of length: " + message.getBinaryPayload().length);
            }
        }

        private void handleTouchEvent(JSONObject touchEvent) {
            // This is where you would integrate with TouchInputService or directly
            // simulate touch input on the Android device based on the received coordinates.
            // For now, just logging:
            Log.d(TAG, "Touch Event Received: " + touchEvent.toString());
            // Example of extracting data:
            // int x = touchEvent.optInt("x");
            // int y = touchEvent.optInt("y");
            // String action = touchEvent.optString("action"); // e.g., "down", "up", "move"
            // More sophisticated touch injection would be needed here, e.g., using AccessibilityService or adb commands.
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            Log.d(TAG, "Pong received.");
        }

        @Override
        protected void onPing(WebSocketFrame ping) {
            Log.d(TAG, "Ping received.");
        }

        @Override
        protected void onError(IOException e) {
            Log.e(TAG, "WebSocket error: " + e.getMessage());
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "StreamingService onDestroy called.");
        if (wsServer != null) {
            try {
                wsServer.stop();
                Log.d(TAG, "WebSocket server stopped.");
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error stopping WebSocket server: " + e.getMessage());
            }
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            Log.d(TAG, "MediaProjection stopped.");
        }
        if (imageReader != null) {
            imageReader.close();
            Log.d(TAG, "ImageReader closed.");
        }
        if (surface != null) {
            surface.release();
            Log.d(TAG, "Surface released.");
        }
        if (imageProcessingThread != null) {
            imageProcessingThread.quitSafely();
            try {
                imageProcessingThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "Image processing thread stopped.");
        }
        stopForeground(true);
        Log.d(TAG, "Streaming Service destroyed.");
    }
}
