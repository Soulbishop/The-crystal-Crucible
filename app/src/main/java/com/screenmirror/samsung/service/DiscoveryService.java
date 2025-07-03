package com.screenmirror.samsung.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscoveryService extends Service {

    private static final String TAG = "DiscoveryService";
    private static final int DISCOVERY_SERVER_PORT = 8081; // New port for discovery
    private static final int STREAMING_SERVICE_PORT = 8080; // Your existing streaming port

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private ServerSocket discoveryServerSocket; // Socket for discovery server
    private boolean isServerRunning = false;
    private WifiManager.MulticastLock multicastLock; // Still useful if you wanted mDNS later

    @Override
    public void onCreate() {
        super.onCreate();
        // Acquire multicast lock early if you need it for any future broadcast;
        // currently, it's not strictly necessary for a simple HTTP server,
        // but good practice if any form of local network discovery/broadcast is intended.
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("screenmirror_discovery_lock");
            multicastLock.setReferenceCounted(true); // Allow multiple acquires/releases
            multicastLock.acquire();
        }
        startDiscoveryServer(); // Start the HTTP discovery server
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Service will restart if killed by system
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not providing a bound service interface
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopDiscoveryServer(); // Stop the server when service is destroyed
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
        if (executor != null) {
            executor.shutdownNow(); // Attempt to stop all running tasks immediately
        }
        Log.d(TAG, "DiscoveryService destroyed");
    }

    private void startDiscoveryServer() {
        if (isServerRunning) {
            Log.d(TAG, "Discovery server already running.");
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    discoveryServerSocket = new ServerSocket(DISCOVERY_SERVER_PORT);
                    isServerRunning = true;
                    Log.i(TAG, "Discovery server started on port " + DISCOVERY_SERVER_PORT);
                    Log.i(TAG, "Device IP for discovery: " + getDeviceIpAddress()); // Log current IP

                    while (isServerRunning && !Thread.currentThread().isInterrupted()) {
                        Socket clientSocket = null;
                        try {
                            clientSocket = discoveryServerSocket.accept(); // Blocks until a client connects
                            handleClientRequest(clientSocket);
                        } catch (IOException e) {
                            if (isServerRunning) { // Log error only if server is expected to be running
                                Log.e(TAG, "Error accepting client connection: " + e.getMessage(), e);
                            } else {
                                Log.d(TAG, "Discovery server socket closed."); // Expected during shutdown
                            }
                        } finally {
                            if (clientSocket != null) {
                                try {
                                    clientSocket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Error closing client socket", e);
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Could not start discovery server on port " + DISCOVERY_SERVER_PORT + ": " + e.getMessage(), e);
                } finally {
                    isServerRunning = false;
                    if (discoveryServerSocket != null && !discoveryServerSocket.isClosed()) {
                        try {
                            discoveryServerSocket.close();
                        } catch (IOException e) {
                            Log.e(TAG, "Error closing discovery server socket", e);
                        }
                    }
                }
            }
        });
    }

    private void stopDiscoveryServer() {
        isServerRunning = false;
        if (discoveryServerSocket != null && !discoveryServerSocket.isClosed()) {
            try {
                discoveryServerSocket.close(); // This will unblock accept() and cause IOException
                Log.d(TAG, "Discovery server attempting to stop.");
            } catch (IOException e) {
                Log.e(TAG, "Error stopping discovery server: " + e.getMessage(), e);
            }
        }
    }

    private void handleClientRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            // Read the first line of the HTTP request (e.g., GET / HTTP/1.1)
            String requestLine = in.readLine();
            if (requestLine == null || !requestLine.startsWith("GET /")) {
                // Not a valid GET request for our simple server
                return;
            }

            // Construct the JSON response with device info
            String deviceIp = getDeviceIpAddress();
            String jsonResponse = "{\"ipAddress\":\"" + deviceIp + "\", \"port\":" + STREAMING_SERVICE_PORT + "}";

            // Build HTTP response headers
            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                                  "Content-Type: application/json\r\n" +
                                  "Access-Control-Allow-Origin: *\r\n" + // IMPORTANT for CORS in web browsers
                                  "Content-Length: " + jsonResponse.length() + "\r\n" +
                                  "Connection: close\r\n" +
                                  "\r\n" +
                                  jsonResponse;

            out.write(httpResponse.getBytes("UTF-8"));
            out.flush();
            Log.d(TAG, "Responded to discovery request from: " + clientSocket.getInetAddress().getHostAddress());

        } catch (IOException e) {
            Log.e(TAG, "Error handling client request: " + e.getMessage(), e);
        }
    }

    // Helper to get device IP address (already in MainActivity, but needed here too)
    private String getDeviceIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            int ipAddressInt = wifiManager.getConnectionInfo().getIpAddress();
            return Formatter.formatIpAddress(ipAddressInt);
        }
        return "0.0.0.0"; // Fallback
    }

    // This method is now primarily for debugging or if MainActivity still needs to pull info
    // The main discovery mechanism is the HTTP server
    public String getDeviceInfo() {
        return "Device: Samsung Galaxy S22 Ultra\n" +
               "IP: " + getDeviceIpAddress() + "\n" +
               "Port: " + STREAMING_SERVICE_PORT + "\n" +
               "Status: Ready (via HTTP server on port " + DISCOVERY_SERVER_PORT + ")";
    }
}
