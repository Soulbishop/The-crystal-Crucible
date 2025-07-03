package com.screenmirror.samsung.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
        Log.d(TAG, "StreamingService destroyed");
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
                } finally {
                    if (signalingServer != null && !signalingServer.isClosed()) {
                        try {
                            signalingServer.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing signaling server socket", e);
                        }
                    }
                }
            }
        });
    }

    private void handleClientConnection() {
        setupScreenCapture();

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
                    Log.e(TAG, "Error reading signaling message: " + e.getMessage(), e);
                } finally {
                    try {
                        if (clientSocket != null && !clientSocket.isClosed()) {
                            clientSocket.close();
                            Log.d(TAG, "Client socket closed after connection handling.");
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing client socket in handler: " + e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void setupScreenCapture() {
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
        Log.d(TAG, "Sending video frame: " + frameData.length + " bytes");

        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: " + frameData.length + "\r\n" + // <--- FIXED THIS LINE
                    "Connection: keep-alive\r\n\r\n";

                OutputStream os = clientSocket.getOutputStream();
                os.write(httpResponse.getBytes("UTF-8"));
                os.write(frameData);
                os.flush();

            } catch (IOException e) {
                Log.e(TAG, "Error sending video frame: " + e.getMessage(), e);
                try {
                    clientSocket.close();
                } catch (IOException ex) { /* ignore */ }
            }
        }
    }

    private void handleSignalingMessage(String message) {
        try {
            Log.d(TAG, "Received message: " + message);

            if (message.startsWith("TOUCH:")) {
                String[] parts = message.substring(6).split(",");
                if (parts.length == 2) {
                    float x = Float.parseFloat(parts[0]);
                    float y = Float.parseFloat(parts[1]);
                    handleTouchMessage(x, y);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling signaling message: " + e.getMessage(), e);
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
                        clientSocket.getOutputStream().write((message + "\n").getBytes("UTF-8"));
                        clientSocket.getOutputStream().flush();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error sending message: " + e.getMessage(), e);
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
            Log.e(TAG, "Error closing sockets during cleanup: " + e.getMessage(), e);
        }

        if (executor != null) {
            executor.shutdownNow();
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
