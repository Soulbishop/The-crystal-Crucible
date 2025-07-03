package com.screenmirror.samsung.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StreamingService extends Service {
    
    private static final String TAG = "StreamingService";
    private static final int SIGNALING_PORT = 8080;
    
    public static StreamingService instance;
    
    private ServerSocket signalingServer;
    private Socket clientSocket;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    
    // Touch coordinate callback interface
    public interface TouchCallback {
        void onTouchReceived(float x, float y);
    }
    
    private TouchCallback touchCallback;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        startSignalingServer();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();
        instance = null;
    }
    
    private void startSignalingServer() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    signalingServer = new ServerSocket(SIGNALING_PORT);
                    Log.d(TAG, "Signaling server started on port " + SIGNALING_PORT);
                    
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            clientSocket = signalingServer.accept();
                            Log.d(TAG, "iPad client connected");
                            handleClientConnection();
                        } catch (IOException e) {
                            if (!Thread.currentThread().isInterrupted()) {
                                Log.e(TAG, "Error accepting client connection", e);
                            }
                            break;
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error starting signaling server", e);
                }
            }
        });
    }
    
    private void handleClientConnection() {
        setupScreenCapture();
        
        // Handle signaling messages from iPad
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream())
                    );
                    
                    String message;
                    while (!Thread.currentThread().isInterrupted() && 
                           (message = reader.readLine()) != null) {
                        handleSignalingMessage(message);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading signaling message", e);
                }
            }
        });
    }
    
    private void setupScreenCapture() {
        // Get screen capture frames from ScreenCaptureService
        if (ScreenCaptureService.instance != null) {
            ScreenCaptureService.instance.setFrameCallback(new ScreenCaptureService.FrameCallback() {
                @Override
                public void onFrameAvailable(byte[] frameData) {
                    sendVideoFrame(frameData);
                }
            });
        }
    }
    
    private void sendVideoFrame(byte[] frameData) {
        // In a real implementation, you would:
        // 1. Convert the raw frame data to a video format
        // 2. Send it via WebSocket or WebRTC to the iPad
        // 3. Handle video encoding and streaming
        
        // For now, this is a placeholder for the video streaming logic
        Log.d(TAG, "Sending video frame: " + frameData.length + " bytes");
        
        // Simple HTTP streaming approach (basic implementation)
        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                // Send frame data as HTTP response
                String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: " + frameData.length + "\r\n" +
                    "Connection: keep-alive\r\n\r\n";
                
                clientSocket.getOutputStream().write(httpResponse.getBytes());
                clientSocket.getOutputStream().write(frameData);
                clientSocket.getOutputStream().flush();
                
            } catch (IOException e) {
                Log.e(TAG, "Error sending video frame", e);
            }
        }
    }
    
    private void handleSignalingMessage(String message) {
        try {
            Log.d(TAG, "Received message: " + message);
            
            // Simple message parsing for touch coordinates
            if (message.startsWith("TOUCH:")) {
                String[] parts = message.substring(6).split(",");
                if (parts.length == 2) {
                    float x = Float.parseFloat(parts[0]);
                    float y = Float.parseFloat(parts[1]);
                    handleTouchMessage(x, y);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling signaling message", e);
        }
    }
    
    private void handleTouchMessage(float x, float y) {
        Log.d(TAG, "Touch received: " + x + ", " + y);
        if (touchCallback != null) {
            touchCallback.onTouchReceived(x, y);
        }
    }
    
    private void sendMessage(String message) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        clientSocket.getOutputStream().write((message + "\n").getBytes());
                        clientSocket.getOutputStream().flush();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error sending message", e);
                }
            }
        });
    }
    
    private void cleanup() {
        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
            if (signalingServer != null) {
                signalingServer.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing sockets", e);
        }
        
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    public void setTouchCallback(TouchCallback callback) {
        this.touchCallback = callback;
    }
    
    public void broadcastDeviceInfo() {
        if (ScreenCaptureService.instance != null) {
            int[] dimensions = ScreenCaptureService.instance.getScreenDimensions();
            String deviceInfo = "DEVICE_INFO:" + dimensions[0] + "," + dimensions[1];
            sendMessage(deviceInfo);
        }
    }
}

