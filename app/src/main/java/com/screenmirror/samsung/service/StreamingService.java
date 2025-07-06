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
import android.hardware.display.DisplayManager; // Added import for DisplayManager
import android.hardware.display.VirtualDisplay; // Added import for VirtualDisplay
import android.net.wifi.WifiManager; // Added for IP address display
import android.text.format.Formatter; // Added for IP address display

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
import fi.iki.elonen.NanoHTTPD.IHTTPSession; // Explicit import
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.NanoWSD.WebSocket; // Explicit import
import fi.iki.elonen.NanoWSD.WebSocketFrame; // Explicit import

public class StreamingService extends Service {

    private static final String TAG = "StreamingService";
    private static final String CHANNEL_ID = "ScreenMirroringChannel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int WEBSOCKET_PORT = 8080;

    private MediaProjection mediaProjection;
    private NanoWSD wsServer;
    private HandlerThread imageProcessingThread;
    private Handler imageProcessingHandler;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay; // Added VirtualDisplay
    private Surface surface;
    private int screenWidth, screenHeight, screenDensity;

    private static WebSocket currentClientWebSocket; [span_9](start_span)// Store the current client WebSocket[span_9](end_span)

    // Singleton pattern for easy access from TouchInputService
    private static StreamingService instance;

    public static StreamingService getInstance() {
        return instance;
    }

    // Interface for TouchInputService to send touch events
    public interface TouchCallback {
        void onTouchEvent(float x, float y, String action);
        // You can add more granular touch events if needed, e.g., onTouchDown, onTouchMove, onTouchUp
    }

    private TouchCallback touchCallback;

    public void setTouchCallback(TouchCallback callback) {
        this.touchCallback = callback;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        instance = this; // Set singleton instance
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

        startWebSocketServer(); [span_10](start_span)//[span_10](end_span)
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getAction() != null && intent.getAction().equals(MainActivity.ACTION_START_STREAMING)) {
                [span_11](start_span)// Ensure MediaProjection is correctly passed and handled[span_11](end_span)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaProjection = intent.getParcelableExtra("mediaProjection");
                }
                screenWidth = intent.getIntExtra("width", 1920);
                screenHeight = intent.getIntExtra("height", 1080);
                screenDensity = intent.getIntExtra("density", 1);
                startScreenCapture(); [span_12](start_span)//[span_12](end_span)
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
            displayIpAddress(); // Display IP when server starts
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
                android.graphics.PixelFormat.RGBA_8888, 2); // Capture 2 frames at a time
        surface = imageReader.getSurface();

        [span_13](start_span)// Ensure VirtualDisplay is properly created and managed by this service[span_13](end_span)
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenMirrorDisplay",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, // Use DisplayManager constant
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
                // Log.d(TAG, "Sent JPEG frame of size: " + jpegBytes.length); // Too verbose, uncomment for debug
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
            Log.d(TAG, "WebSocket opened. Client connected.");
            currentClientWebSocket = this; [span_14](start_span)// Set this as the current active client[span_14](end_span)
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
        [span_15](start_span)public void onClose(int code, String reason, boolean initiatedByRemote) { // CORRECTED LINE: Changed CloseCode to int[span_15](end_span)
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
                        // Pass touch event to registered callback (TouchInputService)
                        if (touchCallback != null) {
                            float x = (float) json.optDouble("x");
                            float y = (float) json.optDouble("y");
                            String action = json.optString("action");
                            touchCallback.onTouchEvent(x, y, action);
                        } else {
                            Log.w(TAG, "TouchCallback not set. Touch event not processed.");
                        }
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
        instance = null; // Clear singleton instance

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
            Log.d(TAG, "Image processing thread stopped.");
        }
        stopForeground(true);
        Log.d(TAG, "Streaming Service destroyed.");
    }
}
