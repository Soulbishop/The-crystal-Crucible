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
import android.media.projection.MediaProjectionManager; // Added import
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

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

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoWSD;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata;

public class StreamingService extends Service {

    private static final String TAG = "StreamingService";
    private static final String CHANNEL_ID = "ScreenMirroringChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int WEBSOCKET_PORT = 8080;

    private MediaProjectionManager mediaProjectionManager; // Added
    private MediaProjection mediaProjection;
    private NanoWSD wsServer;
    private HandlerThread imageProcessingThread;
    private Handler imageProcessingHandler;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private int screenWidth, screenHeight, screenDensity;

    private static WebSocket currentClientWebSocket;

    // Singleton pattern for easy access from TouchInputService
    private static StreamingService instance;
    private static boolean isServiceRunning = false; // Added flag

    public static StreamingService getInstance() {
        return instance;
    }

    public static boolean isRunning() { // Added isRunning method
        return isServiceRunning;
    }

    // Interface for TouchInputService to send touch events
    public interface TouchCallback {
        void onTouchEvent(float x, float y, String action);
    }

    private TouchCallback touchCallback;

    public void setTouchCallback(TouchCallback callback) {
        this.touchCallback = callback;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        isServiceRunning = true; // Set flag to true
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title)) // Use string resource
                .setContentText(getString(R.string.notification_text))   // Use string resource
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(NOTIFICATION_ID, notification);

        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE); // Initialize MediaProjectionManager

        imageProcessingThread = new HandlerThread("ImageProcessingThread");
        imageProcessingThread.start();
        imageProcessingHandler = new Handler(imageProcessingThread.getLooper());

        startWebSocketServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getAction() != null && intent.getAction().equals(MainActivity.ACTION_START_STREAMING)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaProjectionManager != null) {
                    int resultCode = intent.getIntExtra("resultCode", 0);
                    Intent resultData = intent.getParcelableExtra("resultData"); // resultData is Intent, which is Parcelable
                    if (resultData != null && resultCode != 0) {
                        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData);
                        if (mediaProjection == null) {
                            Log.e(TAG, "Failed to get MediaProjection from result data.");
                            stopSelf(); // Stop service if MediaProjection fails
                            return START_NOT_STICKY;
                        }
                    } else {
                        Log.e(TAG, "Missing resultCode or resultData for MediaProjection. Stopping service.");
                        stopSelf(); // Stop service if data is missing
                        return START_NOT_STICKY;
                    }
                } else {
                    Log.e(TAG, "MediaProjectionManager is null or Android version too low.");
                    stopSelf(); // Stop service if conditions are not met
                    return START_NOT_STICKY;
                }
                screenWidth = intent.getIntExtra("width", 1920);
                screenHeight = intent.getIntExtra("height", 1080);
                screenDensity = intent.getIntExtra("density", 1);
                startScreenCapture();
                Log.d(TAG, "Streaming service started with MediaProjection. Resolution: " + screenWidth + "x" + screenHeight);
            } else if (intent.getAction() != null && intent.getAction().equals(MainActivity.ACTION_STOP_STREAMING)) {
                stopSelf();
                Log.d(TAG, "Streaming service stopped via intent.");
            }
        }
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.notification_channel_name); // Use string resource
            String description = getString(R.string.notification_channel_description); // Use string resource
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void startWebSocketServer() {
        wsServer = new NanoWSD(WEBSOCKET_PORT) {
            @Override
            protected WebSocket openWebSocket(IHTTPSession handshake) {
                Log.d(TAG, "New WebSocket connection attempt.");
                return new ScreenMirrorWebSocket(handshake);
            }
        };
        try {
            wsServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.d(TAG, "WebSocket server started on port " + WEBSOCKET_PORT);
            displayIpAddress();
        } catch (IOException e) {
            Log.e(TAG, "Error starting WebSocket server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void displayIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        String ipAddressString = Formatter.formatIpAddress(ipAddress);
        Log.d(TAG, "Device IP Address: " + ipAddressString + ":" + WEBSOCKET_PORT);
    }

    private void startScreenCapture() {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null, cannot start screen capture.");
            return;
        }

        imageReader = ImageReader.newInstance(screenWidth, screenHeight,
                android.graphics.PixelFormat.RGBA_8888, 2);
        surface = imageReader.getSurface();

        // Release existing virtual display if it somehow wasn't released
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }

        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenMirrorDisplay",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, imageProcessingHandler);

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
        }, imageProcessingHandler);
        Log.d(TAG, "Screen capture started.");
    }

    private void processImage(Image image) {
        if (currentClientWebSocket != null && currentClientWebSocket.isOpen()) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            Bitmap bitmap = ImageUtils.imageToBitmap(image);
            if (bitmap == null) {
                Log.e(TAG, "Failed to convert Image to Bitmap.");
                return;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos);
            byte[] jpegBytes = bos.toByteArray();

            try {
                currentClientWebSocket.send(jpegBytes);
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

        // Changed onOpen, onClose, onMessage, onPing, onPong, onError to match org.java_websocket.WebSocket overrides
        @Override
        public void onOpen(org.java_websocket.handshake.Handshakedata handshakedata) { // Corrected parameter type
            Log.d(TAG, "WebSocket opened. Client connected.");
            currentClientWebSocket = this;
            try {
                JSONObject welcomeMessage = new JSONObject();
                welcomeMessage.put("type", "welcome");
                welcomeMessage.put("screenWidth", screenWidth);
                welcomeMessage.put("screenHeight", screenHeight);
                send(welcomeMessage.toString()); // This send method is from WebSocket
            } catch (JSONException e) {
                Log.e(TAG, "Error sending welcome message: " + e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean initiatedByRemote) {
            Log.d(TAG, "WebSocket closed: " + code + ", reason: " + reason + ", initiatedByRemote: " + initiatedByRemote);
            if (this == currentClientWebSocket) {
                currentClientWebSocket = null;
            }
        }

        @Override
        public void onMessage(String message) {
            Log.d(TAG, "Received message: " + message);
            try {
                JSONObject json = new JSONObject(message);
                String type = json.optString("type");
                if ("touchEvent".equals(type)) {
                    if (touchCallback != null) {
                        float x = (float) json.optDouble("x");
                        float y = (float) json.optDouble("y");
                        String action = json.optString("action");
                        touchCallback.onTouchEvent(x, y, action);
                    } else {
                        Log.w(TAG, "TouchCallback not set. Touch event not processed.");
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON message: " + e.getMessage());
            }
        }

        @Override
        public void onMessage(ByteBuffer message) {
            Log.d(TAG, "Received binary message of length: " + message.remaining());
            // Process binary data here if needed
        }

        @Override
        public void onPing(WebSocket conn, Framedata f) { // This is for NanoWSD's WebSocket, not org.java_websocket.
            // NanoWSD's WebSocket doesn't have onPing/onPong.
            // If you intend to use org.java_websocket for both client/server, these would be valid.
            // Since you are using NanoWSD (the outer class), its WebSocket implementation might not expose these directly in this inner class.
            // For org.java_websocket, onPing/onPong are directly in the WebSocket base class, but NanoWSD wraps it differently.
            // Let's remove these as they conflict with NanoWSD's internal WebSocket handling.
        }

        @Override
        public void onPong(WebSocket conn, Framedata f) { // Removing for same reason as onPing
            // Removing as per above
        }

        @Override
        public void onError(WebSocket conn, Exception ex) { // Corrected parameter type for conn
            Log.e(TAG, "WebSocket error: " + ex.getMessage(), ex);
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
        instance = null;
        isServiceRunning = false; // Set flag to false

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
        if (virtualDisplay != null) {
            virtualDisplay.release();
            Log.d(TAG, "VirtualDisplay released.");
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
        }
        stopForeground(true);
        Log.d(TAG, "Streaming Service destroyed.");
    }
}
