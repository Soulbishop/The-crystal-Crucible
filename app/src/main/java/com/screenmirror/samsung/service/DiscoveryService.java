package com.screenmirror.samsung.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscoveryService extends Service {
    
    private static final String TAG = "DiscoveryService";
    private static final String SERVICE_TYPE = "_screenmirror._tcp.local.";
    private static final String SERVICE_NAME = "Samsung Screen Mirror";
    private static final int SERVICE_PORT = 8080;
    
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private WifiManager.MulticastLock multicastLock;
    
    @Override
    public void onCreate() {
        super.onCreate();
        startDiscovery();
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
        stopDiscovery();
    }
    
    private void startDiscovery() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    multicastLock = wifiManager.createMulticastLock("screenmirror_discovery");
                    multicastLock.acquire();
                    
                    // Get device IP address
                    String ipAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                    
                    Log.d(TAG, "Discovery service started. Device IP: " + ipAddress + ":" + SERVICE_PORT);
                    Log.d(TAG, "Device available for iPad connection");
                    
                    // Simple discovery mechanism - broadcast device info
                    broadcastDeviceInfo(ipAddress);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error starting discovery service", e);
                }
            }
        });
    }
    
    private void broadcastDeviceInfo(String ipAddress) {
        // Simple implementation - log device info for manual connection
        Log.i(TAG, "=== DEVICE CONNECTION INFO ===");
        Log.i(TAG, "Device: Samsung Galaxy S22 Ultra");
        Log.i(TAG, "IP Address: " + ipAddress);
        Log.i(TAG, "Port: " + SERVICE_PORT);
        Log.i(TAG, "Service: Screen Mirror");
        Log.i(TAG, "Status: Ready for iPad connection");
        Log.i(TAG, "=============================");
        
        // In a real implementation, you might:
        // 1. Use mDNS/Bonjour for automatic discovery
        // 2. Broadcast UDP packets
        // 3. Use a simple HTTP server for device info
        
        // For now, we'll use the streaming service to handle connections
        if (StreamingService.instance != null) {
            StreamingService.instance.broadcastDeviceInfo();
        }
    }
    
    private void stopDiscovery() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (multicastLock != null && multicastLock.isHeld()) {
                        multicastLock.release();
                    }
                    Log.d(TAG, "Discovery service stopped");
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping discovery service", e);
                }
            }
        });
        
        if (executor != null) {
            executor.shutdown();
        }
    }
    
    public String getDeviceInfo() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String ipAddress = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
            
            return "Device: Samsung Galaxy S22 Ultra\n" +
                   "IP: " + ipAddress + "\n" +
                   "Port: " + SERVICE_PORT + "\n" +
                   "Status: Ready";
        } catch (Exception e) {
            Log.e(TAG, "Error getting device info", e);
            return "Device info unavailable";
        }
    }
}

