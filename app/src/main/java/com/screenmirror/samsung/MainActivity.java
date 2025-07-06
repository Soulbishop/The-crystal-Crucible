package com.screenmirror.samsung;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.screenmirror.samsung.service.ScreenCaptureService;
import com.screenmirror.samsung.service.StreamingService;
import com.screenmirror.samsung.service.DiscoveryService;

/**
 * 🧪 MainActivity - ALCHEMICAL EDITION
 * 🔴 Samsung Galaxy S22 Ultra Screen Mirroring Controller
 * 🔵 Optimized for iPad Air 2 communication
 * ⚗️ Clean, corruption-free implementation
 */
public class MainActivity extends AppCompatActivity implements StreamingService.StreamingServiceListener {

    private static final String TAG = "🔴MainActivity";
    private static final int REQUEST_MEDIA_PROJECTION = 1000;
    private static final int REQUEST_PERMISSIONS = 1001;

    // UI Components
    private Button startButton;
    private Button stopButton;
    private TextView statusText;
    private TextView ipAddressText;

    // Services and managers
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private StreamingService streamingService;

    // State tracking
    private boolean isStreaming = false;
    private boolean servicesStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "🧪 MainActivity onCreate - Alchemical initialization");

        initializeViews();
        initializeServices();
        checkPermissions();
        updateUI();
    }

    /**
     * 🔴 Initialize UI components with alchemical styling
     */
    private void initializeViews() {
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        statusText = findViewById(R.id.statusText);
        ipAddressText = findViewById(R.id.ipAddressText);

        startButton.setOnClickListener(v -> startScreenMirroring());
        stopButton.setOnClickListener(v -> stopScreenMirroring());

        // Set initial alchemical theme
        statusText.setText("🧪 Alchemical Matrix Ready");
        ipAddressText.setText("⚗️ Awaiting Network Transmutation");
    }

    /**
     * 🔵 Initialize core services
     */
    private void initializeServices() {
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        // Start StreamingService
        Intent streamingIntent = new Intent(this, StreamingService.class);
        startService(streamingIntent);
        
        // Start DiscoveryService
        Intent discoveryIntent = new Intent(this, DiscoveryService.class);
        startService(discoveryIntent);
        
        servicesStarted = true;
        Log.d(TAG, "⚗️ Alchemical services initialized");
    }

    /**
     * 🧪 Check and request necessary permissions
     */
    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
        };

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }

        // Check accessibility service for TouchInputService
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilityServiceDialog();
        }
    }

    /**
     * 🔴 Start screen mirroring process
     */
    private void startScreenMirroring() {
        Log.d(TAG, "🧪 Starting alchemical screen transmutation");
        
        if (mediaProjectionManager != null) {
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
        } else {
            showError("⚗️ MediaProjection not available");
        }
    }

    /**
     * 🔵 Stop screen mirroring process
     */
    private void stopScreenMirroring() {
        Log.d(TAG, "🔴 Stopping alchemical transmutation");
        
        isStreaming = false;
        
        // Stop ScreenCaptureService
        Intent captureIntent = new Intent(this, ScreenCaptureService.class);
        stopService(captureIntent);
        
        // Release MediaProjection
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        updateUI();
        statusText.setText("🧪 Transmutation Halted");
        Log.d(TAG, "⚗️ Screen mirroring stopped");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "🔴 MediaProjection permission granted");
                
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                
                // Start ScreenCaptureService with MediaProjection
                Intent captureIntent = new Intent(this, ScreenCaptureService.class);
                captureIntent.putExtra("resultCode", resultCode);
                captureIntent.putExtra("data", data);
                startForegroundService(captureIntent);
                
                isStreaming = true;
                updateUI();
                statusText.setText("🧪 Alchemical Transmutation Active");
                
            } else {
                Log.e(TAG, "⚗️ MediaProjection permission denied");
                showError("🔴 Screen capture permission required for transmutation");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                Log.d(TAG, "🔵 All permissions granted");
                updateUI();
            } else {
                Log.e(TAG, "🔴 Some permissions denied");
                showError("⚗️ Permissions required for alchemical transmutation");
            }
        }
    }

    /**
     * 🧪 Update UI based on current state
     */
    private void updateUI() {
        runOnUiThread(() -> {
            startButton.setEnabled(!isStreaming && servicesStarted);
            stopButton.setEnabled(isStreaming);
            
            // Update IP address display
            if (StreamingService.instance != null) {
                String ipAddress = StreamingService.instance.getLocalIpAddress(this);
                if (ipAddress != null) {
                    ipAddressText.setText("🔴 Alchemical Portal: " + ipAddress + ":8080");
                } else {
                    ipAddressText.setText("⚗️ Network Transmutation Pending");
                }
            }
        });
    }

    /**
     * 🔴 Check if accessibility service is enabled for TouchInputService
     */
    private boolean isAccessibilityServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
            getContentResolver(),
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        
        if (enabledServices != null) {
            return enabledServices.contains(getPackageName() + "/com.screenmirror.samsung.service.TouchInputService");
        }
        return false;
    }

    /**
     * 🔵 Show accessibility service setup dialog
     */
    private void showAccessibilityServiceDialog() {
        Toast.makeText(this, 
            "🧪 Enable TouchInput Service in Accessibility Settings for full alchemical control", 
            Toast.LENGTH_LONG).show();
        
        // Optional: Open accessibility settings
        // Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        // startActivity(intent);
    }

    /**
     * ⚗️ Show error message with alchemical styling
     */
    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            statusText.setText(message);
        });
    }

    // StreamingService.StreamingServiceListener implementation
    @Override
    public void onClientConnected(String ipAddress) {
        Log.d(TAG, "🧪 iPad client connected: " + ipAddress);
        runOnUiThread(() -> {
            statusText.setText("🔴 iPad Connected - Transmutation Link Established");
            Toast.makeText(this, "🧪 Alchemical connection established with iPad", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onClientDisconnected() {
        Log.d(TAG, "🔵 iPad client disconnected");
        runOnUiThread(() -> {
            statusText.setText("⚗️ iPad Disconnected - Awaiting Reconnection");
        });
    }

    @Override
    public void onSignalingMessage(String message) {
        Log.d(TAG, "🧪 Signaling message received: " + message);
    }

    @Override
    public void onOfferReceived(String sdp) {
        Log.d(TAG, "🔴 WebRTC Offer received");
    }

    @Override
    public void onAnswerReceived(String sdp) {
        Log.d(TAG, "🔵 WebRTC Answer received");
    }

    @Override
    public void onIceCandidateReceived(String candidate) {
        Log.d(TAG, "⚗️ ICE Candidate received");
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Set StreamingService listener
        if (StreamingService.instance != null) {
            StreamingService.instance.setListener(this);
        }
        
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        
        // Remove StreamingService listener
        if (StreamingService.instance != null) {
            StreamingService.instance.setListener(null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🧪 MainActivity onDestroy - Cleaning up alchemical resources");
        
        if (isStreaming) {
            stopScreenMirroring();
        }
        
        // Stop services
        if (servicesStarted) {
            stopService(new Intent(this, StreamingService.class));
            stopService(new Intent(this, DiscoveryService.class));
        }
    }
}

