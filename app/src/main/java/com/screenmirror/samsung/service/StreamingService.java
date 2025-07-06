package com.screenmirror.samsung.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.NanoWSD.WebSocket;
import fi.iki.elonen.NanoWSD.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 🧪 StreamingService - ALCHEMICAL EDITION
 * 🔴 Samsung Galaxy S22 Ultra WebSocket Streaming Service
 */
public class StreamingService extends Service {

    private static final String TAG = "StreamingService";
    private static final int WEBSOCKET_PORT = 8080;

    // Singleton instance for easy access
    public static StreamingService instance;

    private WebSocketServer webSocketServer;
    private MediaProjectionManager mediaProjectionManager;
    private ScreenCaptureService.ScreenCaptureListener screenCaptureListener;
    private ExecutorService executorService;

    // Listener interface for communication with other components
    public interface StreamingServiceListener {
        void onClientConnected();
        void onClientDisconnected();
        void onTouchInputReceived(JSONObject touchData);
    }

    // Interface for TouchInputService to register callbacks
    public interface TouchCallback {
        void onTouchEvent(JSONObject touchData);
    }

    private StreamingServiceListener listener;
    private TouchCallback touchCallback;

    public void setListener(StreamingServiceListener listener) {
        this.listener = listener;
    }

    public void setTouchCallback(TouchCallback touchCallback) {
        this.touchCallback = touchCallback;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "StreamingService onCreate");
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "StreamingService onStartCommand");
        if (webSocketServer == null) {
            startWebSocketServer();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "StreamingService onDestroy");
        stopWebSocketServer();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startWebSocketServer() {
        try {
            webSocketServer = new WebSocketServer(WEBSOCKET_PORT, this);
            webSocketServer.start();
            Log.d(TAG, "WebSocket server started on port " + WEBSOCKET_PORT);
            displayIpAddress();
        } catch (IOException e) {
            Log.e(TAG, "Error starting WebSocket server", e);
        }
    }

    private void stopWebSocketServer() {
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
                Log.d(TAG, "WebSocket server stopped");
            } catch (IOException e) {
                Log.e(TAG, "Error stopping WebSocket server", e);
            } catch (InterruptedException e) {
                Log.e(TAG, "WebSocket server stop interrupted", e);
            }
            webSocketServer = null;
        }
    }

    private void displayIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        String ipAddressString = Formatter.formatIpAddress(ipAddress);
        Log.d(TAG, "Device IP Address: " + ipAddressString + ":" + WEBSOCKET_PORT);
    }

    private class WebSocketServer extends NanoWSD {

        private final StreamingService serviceContext;

        public WebSocketServer(int port, StreamingService context) {
            super(port);
            this.serviceContext = context;
        }

        @Override
        protected WebSocket openWebSocket(IHTTPSession handshake) {
            return new AlchemicalWebSocket(handshake, serviceContext);
        }
    }

    private class AlchemicalWebSocket extends WebSocket {

        private final StreamingService serviceContext;

        public AlchemicalWebSocket(IHTTPSession handshake, StreamingService serviceContext) {
            super(handshake);
            this.serviceContext = serviceContext;
        }

        @Override
        protected void onOpen() {
            Log.d(TAG, "🧪 Alchemical WebSocket connection opened");
            if (serviceContext.listener != null) {
                serviceContext.listener.onClientConnected();
            }
        }

        @Override
        protected void onClose(int code, String reason, boolean initiatedByRemote) {
            Log.d(TAG, "🔴 WebSocket connection closed. Code: " + code + ", Reason: " + reason + ", Remote: " + initiatedByRemote);
            if (serviceContext.listener != null) {
                serviceContext.listener.onClientDisconnected();
            }
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            String msg = message.getTextPayload();
            Log.d(TAG, "Received WebSocket message: " + msg);
            try {
                JSONObject json = new JSONObject(msg);
                String type = json.optString("type");

                if ("touch".equals(type)) {
                    if (serviceContext.touchCallback != null) {
                        serviceContext.touchCallback.onTouchEvent(json);
                    }
                } else if ("video_stream_start".equals(type)) {
                    // Handle video stream start command
                    Log.d(TAG, "Video stream start command received");
                    // Potentially start screen capture or adjust settings
                } else if ("video_stream_stop".equals(type)) {
                    // Handle video stream stop command
                    Log.d(TAG, "Video stream stop command received");
                    // Potentially stop screen capture
                } else if ("ping".equals(type)) {
                    // Respond to ping to keep connection alive
                    try {
                        send(new JSONObject().put("type", "pong").toString());
                    } catch (IOException e) {
                        Log.e(TAG, "Error sending pong", e);
                    }
                }

            } catch (JSONException e) {
                Log.e(TAG, "Error parsing WebSocket message JSON", e);
            }
        }

        @Override
        protected void onException(IOException e) {
            Log.e(TAG, "WebSocket error: " + e.getMessage(), e);
        }

        @Override
        protected void onPong(WebSocketFrame pongFrame) {
            Log.d(TAG, "Received pong");
        }
    }
}

