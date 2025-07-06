package com.screenmirror.samsung.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
 * StreamingService - Crystal Crucible Edition
 * Samsung Galaxy S22 Ultra WebSocket Streaming Service
 * Optimized for iPad Air 2 communication
 * Clean implementation for NanoHTTPD 2.3.1
 */
public class StreamingService extends Service {

    private static final String TAG = "StreamingService";
    private static final int SIGNALING_PORT = 8080;

    public static StreamingService instance;

    private ScreenMirrorWebServer webServer;
    private StreamingServiceListener listener;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    // For storing signaling messages until client connects
    private final List<String> pendingIceCandidates = Collections.synchronizedList(new ArrayList<>());
    private String pendingSdp;

    // Interface for callbacks
    public interface StreamingServiceListener {
        void onClientConnected(String ipAddress);
        void onClientDisconnected();
        void onSignalingMessage(String message);
        void onOfferReceived(String sdp);
        void onAnswerReceived(String sdp);
        void onIceCandidateReceived(String candidate);
    }

    // Touch coordinate callback interface (for TouchInputService)
    public interface TouchCallback {
        void onTouchReceived(float x, float y);
        void onLongPressReceived(float x, float y);
        void onSwipeReceived(float startX, float startY, float endX, float endY);
        void onPinchReceived(float scale);
    }
    private TouchCallback touchCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "StreamingService onCreate - Crystal Crucible WebSocket server initializing");
        startWebServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "StreamingService onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "StreamingService onDestroy - Cleaning up Crystal Crucible resources");
        stopWebServer();
        instance = null;
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void setListener(StreamingServiceListener listener) {
        this.listener = listener;
    }

    public void setTouchCallback(TouchCallback callback) {
        this.touchCallback = callback;
    }

    private void startWebServer() {
        if (webServer == null) {
            try {
                webServer = new ScreenMirrorWebServer(SIGNALING_PORT, this);
                webServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Log.d(TAG, "Crystal Crucible signaling server started on port " + SIGNALING_PORT);
            } catch (IOException e) {
                Log.e(TAG, "Error starting web server: " + e.getMessage(), e);
            }
        }
    }

    private void stopWebServer() {
        if (webServer != null) {
            webServer.stop();
            webServer = null;
            Log.d(TAG, "Crystal Crucible signaling server stopped");
        }
    }

    // --- Signaling methods for other services (e.g., ScreenCaptureService) ---

    public void sendSdpOffer(String sdp) {
        this.pendingSdp = sdp;
        if (webServer != null && webServer.getCurrentWebSocket() != null) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "offer");
                json.put("sdp", sdp);
                webServer.getCurrentWebSocket().send(json.toString());
                Log.d(TAG, "Sent SDP Offer: " + sdp);
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error sending SDP offer: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "No WebSocket client connected to send SDP offer immediately. Stored.");
        }
    }

    public void sendSdpAnswer(String sdp) {
        if (webServer != null && webServer.getCurrentWebSocket() != null) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "answer");
                json.put("sdp", sdp);
                webServer.getCurrentWebSocket().send(json.toString());
                Log.d(TAG, "Sent SDP Answer: " + sdp);
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error sending SDP answer: " + e.getMessage(), e);
            }
        } else {
            Log.e(TAG, "No WebSocket client connected to send SDP answer");
        }
    }

    public void sendIceCandidate(String candidate) {
        if (webServer != null && webServer.getCurrentWebSocket() != null) {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "candidate");
                json.put("candidate", candidate);
                webServer.getCurrentWebSocket().send(json.toString());
                Log.d(TAG, "Sent ICE Candidate: " + candidate);
            } catch (IOException | JSONException e) {
                Log.e(TAG, "Error sending ICE candidate: " + e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "No WebSocket client connected to send ICE candidate immediately. Stored.");
            pendingIceCandidates.add(candidate);
        }
    }

    // Accessors for pending messages (used by WebSocket onOpen)
    public List<String> getPendingIceCandidates() {
        return pendingIceCandidates;
    }

    public void clearPendingIceCandidates() {
        pendingIceCandidates.clear();
    }

    public String getPendingSdp() {
        return pendingSdp;
    }

    public void clearPendingSdp() {
        pendingSdp = null;
    }

    // --- Handle incoming signaling messages from WebSocket client ---
    public void handleSignalingMessage(String message) {
        if (listener != null) {
            listener.onSignalingMessage(message);
        }

        try {
            JSONObject json = new JSONObject(message);
            String type = json.getString("type");

            switch (type) {
                case "offer":
                    String sdpOffer = json.getString("sdp");
                    if (listener != null) listener.onOfferReceived(sdpOffer);
                    Log.d(TAG, "Received SDP Offer");
                    break;
                case "answer":
                    String sdpAnswer = json.getString("sdp");
                    if (listener != null) listener.onAnswerReceived(sdpAnswer);
                    Log.d(TAG, "Received SDP Answer");
                    break;
                case "candidate":
                    String candidate = json.getString("candidate");
                    if (listener != null) listener.onIceCandidateReceived(candidate);
                    Log.d(TAG, "Received ICE Candidate");
                    break;
                case "touch":
                    handleTouchMessage(json);
                    break; 
                default:
                    Log.w(TAG, "Unknown signaling message type: " + type);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing signaling message JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Handle touch input messages from iPad
     */
    private void handleTouchMessage(JSONObject json) {
        try {
            if (touchCallback != null) {
                String touchType = json.optString("touchType", "tap");
                float x = (float) json.getDouble("x");
                float y = (float) json.getDouble("y");

                switch (touchType) {
                    case "tap":
                        touchCallback.onTouchReceived(x, y);
                        break;
                    case "longPress":
                        touchCallback.onLongPressReceived(x, y);
                        break;
                    case "swipe":
                        float endX = (float) json.getDouble("endX");
                        float endY = (float) json.getDouble("endY");
                        touchCallback.onSwipeReceived(x, y, endX, endY);
                        break;
                    case "pinch":
                        float scale = (float) json.getDouble("scale");
                        touchCallback.onPinchReceived(scale);
                        break;
                    default:
                        Log.w(TAG, "Unknown touch type: " + touchType);
                        break;
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing touch message: " + e.getMessage(), e);
        }
    }

    public String getLocalIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
            return Formatter.formatIpAddress(ipAddress);
        }
        return null;
    }

    // Inner class for the WebSocket server
    private static class ScreenMirrorWebServer extends NanoWSD {
        private StreamingService serviceContext;
        private SignalingWebSocket currentWebSocket; 

        public ScreenMirrorWebServer(int port, StreamingService serviceContext) {
            super(port);
            this.serviceContext = serviceContext;
        }

        public SignalingWebSocket getCurrentWebSocket() { 
            return currentWebSocket;
        }

        @Override
        protected WebSocket openWebSocket(IHTTPSession handshake) { 
            Log.d(TAG, "WebSocket opened from " + handshake.getRemoteIpAddress());
            
            // Simply replace the current WebSocket
            this.currentWebSocket = new SignalingWebSocket(handshake, serviceContext);

            if (serviceContext.listener != null) {
                serviceContext.listener.onClientConnected(handshake.getRemoteIpAddress());
            }

            // Send pending messages to new client
            serviceContext.executor.execute(() -> {
                try {
                    String pendingSdp = serviceContext.getPendingSdp();
                    if (pendingSdp != null) {
                        JSONObject json = new JSONObject();
                        json.put("type", "offer"); 
                        json.put("sdp", pendingSdp);
                        currentWebSocket.send(json.toString());
                        serviceContext.clearPendingSdp(); 
                        Log.d(TAG, "Sent pending SDP to new client");
                    }

                    List<String> candidatesToSend = new ArrayList<>(serviceContext.getPendingIceCandidates());
                    for (String candidate : candidatesToSend) {
                        JSONObject json = new JSONObject();
                        json.put("type", "candidate");
                        json.put("candidate", candidate);
                        currentWebSocket.send(json.toString());
                        Log.d(TAG, "Sent pending ICE Candidate to new client: " + candidate);
                    }
                    serviceContext.clearPendingIceCandidates(); 
                } catch (IOException | JSONException e) {
                    Log.e(TAG, "Error sending pending messages to new client: " + e.getMessage(), e);
                }
            });

            return currentWebSocket;
        }
    }

    /**
     * Working WebSocket implementation for NanoHTTPD 2.3.1
     */
    private static class SignalingWebSocket extends WebSocket {
        private StreamingService serviceContext;

        public SignalingWebSocket(IHTTPSession handshake, StreamingService serviceContext) {
            super(handshake);
            this.serviceContext = serviceContext;
        }

        @Override
        protected void onOpen() {
            Log.d(TAG, "Crystal Crucible WebSocket connection opened");
        }

        @Override
        protected void onClose(WebSocket.CloseCode code, String reason, boolean initiatedByRemote) {
            Log.d(TAG, "WebSocket connection closed. Code: " + code + ", Reason: " + reason + ", Remote: " + initiatedByRemote);
            if (serviceContext.listener != null) {
                serviceContext.listener.onClientDisconnected();
            }
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            String msg = message.getTextPayload();
            Log.d(TAG, "Received WebSocket message: " + msg);
            serviceContext.handleSignalingMessage(msg);
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            Log.d(TAG, "Received Pong");
        }

        @Override
        protected void onException(IOException exception) {
            Log.e(TAG, "WebSocket error: " + exception.getMessage(), exception);
        }
    }
}
