package com.screenmirror.samsung.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.WebSocket;
import fi.iki.elonen.WebSocketFrame;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StreamingService extends Service {

    private static final String TAG = "StreamingService";
    private ScreenMirrorWebServer webServer;
    private StreamingServiceListener listener; // To communicate with other services/components

    // For storing signaling messages until client connects
    private final List<String> pendingIceCandidates = Collections.synchronizedList(new ArrayList<>());
    private String pendingSdp; // Stores the last received SDP offer

    // Interface for callbacks
    public interface StreamingServiceListener {
        void onClientConnected(String ipAddress);
        void onClientDisconnected();
        void onSignalingMessage(String message);
        void onOfferReceived(String sdp);
        void onAnswerReceived(String sdp);
        void onIceCandidateReceived(String candidate);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "StreamingService onCreate");
        startWebServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "StreamingService onStartCommand");
        return START_STICKY; // Service will restart if killed by system
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "StreamingService onDestroy");
        stopWebServer();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't use binding for this service, but must return null
        return null;
    }

    public void setListener(StreamingServiceListener listener) {
        this.listener = listener;
    }

    private void startWebServer() {
        if (webServer == null) {
            try {
                // Use port 8080 for signaling
                webServer = new ScreenMirrorWebServer(8080, this);
                webServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Log.d(TAG, "Signaling server started on port 8080");
            } catch (IOException e) {
                Log.e(TAG, "Error starting web server: " + e.getMessage(), e);
            }
        }
    }

    private void stopWebServer() {
        if (webServer != null) {
            webServer.stop();
            webServer = null;
            Log.d(TAG, "Signaling server stopped.");
        }
    }

    // --- Signaling methods for other services (e.g., ScreenCaptureService) ---

    public void sendSdpOffer(String sdp) {
        this.pendingSdp = sdp; // Store it in case client connects later
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
            Log.e(TAG, "No WebSocket client connected to send SDP answer.");
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
            pendingIceCandidates.add(candidate); // Store in case client connects later
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
                default:
                    Log.w(TAG, "Unknown signaling message type: " + type);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing signaling message JSON: " + e.getMessage(), e);
        }
    }


    // --- Inner Class for NanoHTTPD Web Server ---
    public class ScreenMirrorWebServer extends NanoWSD { // Extends NanoWSD for WebSocket support

        private StreamingService service;
        private WebSocket currentWebSocket; // To keep track of the active WebSocket connection

        public ScreenMirrorWebServer(int port, StreamingService service) {
            super(port);
            this.service = service;
            Log.d(TAG, "NanoWSD server initialized on port: " + port);
        }

        public WebSocket getCurrentWebSocket() {
            return currentWebSocket;
        }

        @Override
        protected WebSocket openWebSocket(IHTTPSession handshake) {
            // This method is called by NanoWSD when a WebSocket handshake is successful
            return new ScreenMirrorWebSocket(handshake, service);
        }

        // Handle regular HTTP requests (e.g., for discovery)
        @Override
        public Response serve(IHTTPSession session) {
            // Check if it's a WebSocket handshake
            Map<String, String> headers = session.getHeaders();
            String upgrade = headers.get("upgrade");
            if (upgrade != null && upgrade.equalsIgnoreCase("websocket")) {
                // If it's a WebSocket handshake, NanoWSD will handle it via openWebSocket
                // We just return null here for NanoWSD to take over.
                // It's important NOT to try to handle it as a regular HTTP request.
                return super.serve(session); // Let NanoWSD handle the WebSocket upgrade
            } else {
                // Handle standard HTTP requests (e.g., for discovery)
                // For now, let's just respond with a simple message
                String uri = session.getUri();
                Log.d(TAG, "HTTP Request received for URI: " + uri);
                String msg = "Screen Mirror Web Server is running. URI: " + uri;
                return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, msg);
            }
        }

        // Inner class for custom WebSocket handling
        private class ScreenMirrorWebSocket extends WebSocket {

            private StreamingService service;

            public ScreenMirrorWebSocket(IHTTPSession handshake, StreamingService service) {
                super(handshake);
                this.service = service;
            }

            @Override
            protected void onOpen() {
                Log.d(TAG, "WebSocket Opened: " + getRemoteIpAddress());
                currentWebSocket = this; // Set this as the active WebSocket
                if (service.listener != null) {
                    service.listener.onClientConnected(getRemoteIpAddress());
                }

                // Send initial configuration or ICE candidates/SDP if available
                if (service.getPendingSdp() != null) {
                    try {
                        JSONObject json = new JSONObject();
                        json.put("type", "offer"); // Assuming it's an offer that was pending
                        json.put("sdp", service.getPendingSdp());
                        send(json.toString());
                        Log.d(TAG, "Sent pending SDP offer on WebSocket open.");
                    } catch (IOException | JSONException e) {
                        Log.e(TAG, "Error sending pending SDP on open: " + e.getMessage());
                    } finally {
                        service.clearPendingSdp();
                    }
                }

                if (service.getPendingIceCandidates() != null) {
                    for (String candidate : service.getPendingIceCandidates()) {
                        try {
                            JSONObject json = new JSONObject();
                            json.put("type", "candidate");
                            json.put("candidate", candidate);
                            send(json.toString());
                        } catch (IOException | JSONException e) {
                            Log.e(TAG, "Error sending pending ICE candidate on open: " + e.getMessage());
                        }
                    }
                    service.clearPendingIceCandidates(); // Clear after sending
                    Log.d(TAG, "Sent pending ICE candidates on WebSocket open.");
                }
            }

            @Override
            protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
                Log.d(TAG, "WebSocket Closed: " + getRemoteIpAddress() + " Code: " + code + " Reason: " + reason + " Remote: " + initiatedByRemote);
                if (currentWebSocket == this) { // Only clear if this was the active connection
                    currentWebSocket = null;
                }
                if (service.listener != null) {
                    service.listener.onClientDisconnected();
                }
            }

            @Override
            protected void onMessage(WebSocketFrame message) {
                if (message.isText()) {
                    String text = message.getTextPayload();
                    Log.d(TAG, "WebSocket Message: " + text);
                    // Handle signaling messages (SDP, ICE candidates)
                    service.handleSignalingMessage(text);
                }
            }

            @Override
            protected void onPong(WebSocketFrame pong) {
                // Log.d(TAG, "WebSocket Pong from: " + getRemoteIpAddress()); // Too verbose
            }

            @Override
            protected void onException(IOException exception) {
                Log.e(TAG, "WebSocket Exception: " + exception.getMessage(), exception);
            }
        }
    }
}
