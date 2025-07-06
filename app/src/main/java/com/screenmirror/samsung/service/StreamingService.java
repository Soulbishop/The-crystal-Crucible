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
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.screenmirror.samsung.MainActivity;
import com.screenmirror.samsung.R;
import com.screenmirror.samsung.util.ImageUtils; // This import should now be resolved

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.WebSocket; // Correct import for WebSocket (from NanoHTTPD or java-websocket)
import fi.iki.elonen.WebSocketFrame; // Correct import for WebSocketFrame

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
    private VirtualDisplay virtualDisplay;
    private Surface surface;
    private int screenWidth, screenHeight, screenDensity;

    private static WebSocket currentClientWebSocket;

    // Singleton pattern for easy access from TouchInputService
    private static StreamingService instance;

    public static StreamingService getInstance() {
        return instance;
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
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Screen Mirroring Active")
                .setContentText("Streaming your screen to the iPad.")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this resource exists
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mediaProjection = intent.getParcelableExtra("mediaProjection");
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

            // Using ImageUtils now
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

        @Override
        protected void onOpen() {
            Log.d(TAG, "WebSocket opened. Client connected.");
            currentClientWebSocket = this;
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
        public void onClose(int code, String reason, boolean initiatedByRemote) {
            Log.d(TAG, "WebSocket closed: " + code + ", reason: " + reason + ", initiatedByRemote: " + initiatedByRemote);
            if (this == currentClientWebSocket) {
                currentClientWebSocket = null;
            }
        }

        // Corrected onMessage signature and payload access
        @Override
        public void onMessage(WebSocketFrame message) { // Changed protected to public
            // WebSocketFrame.getTextPayload() and getBinaryPayload() should work with NanoWSD's WebSocketFrame
            if (message.isText()) { // isText() should be available
                String textMessage = message.getTextPayload();
                Log.d(TAG, "Received message: " + textMessage);
                try {
                    JSONObject json = new JSONObject(textMessage);
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
            } else if (message.isBinary()) { // isBinary() should be available
                Log.d(TAG, "Received binary message of length: " + message.getBinaryPayload().length);
            }
        }

        // Corrected onPong signature
        @Override
        public void onPong(WebSocketFrame pongFrame) { // Changed protected to public and parameter name
            Log.d(TAG, "Pong received.");
        }

        // Corrected onPing signature
        @Override
        public void onPing(WebSocketFrame pingFrame) { // Changed protected to public and parameter name
            Log.d(TAG, "Ping received.");
        }

        // Corrected method name from onError to onException as required by WebSocket interface
        @Override
        public void onException(IOException e) { // Changed protected to public and onError to onException
            Log.e(TAG, "WebSocket error: " + e.getMessage(), e);
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
