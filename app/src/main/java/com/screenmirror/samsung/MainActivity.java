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
 * 🔴 ALCHEMICAL MAIN ACTIVITY - THE PHILOSOPHER'S INTERFACE
 * 🔵 Transmutes user interactions into screen mirroring gold
 * ⚗️ Handles the sacred ritual of permission acquisition
 */
public class MainActivity extends AppCompatActivity {
    
    // 🔴 CRIMSON CONSTANTS - The Sacred Numbers
    private static final String TAG = "🧪AlchemicalMain";
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_OVERLAY_PERMISSION = 1002;
    private static final int REQUEST_ACCESSIBILITY_PERMISSION = 1003;
    private static final int REQUEST_PERMISSIONS = 1004;
    
    // 🔵 AZURE COMPONENTS - The Transmutation Elements
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private Button startButton, stopButton;
    private TextView statusText;
    private boolean isStreaming = false;
    
    // ⚗️ HERMETIC CALLBACK - The Philosopher's Stone Response
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.d(TAG, "🔴 MediaProjection stopped - Transmutation halted");
            runOnUiThread(() -> {
                isStreaming = false;
                updateUI();
                stopAllServices();
            });
        }
        
        @Override
        public void onCapturedContentResize(int width, int height) {
            Log.d(TAG, "🔵 Content resized: " + width + "x" + height + " - Adjusting alchemical matrix");
            // Handle dynamic resize for Samsung Galaxy S22 Ultra
            Intent resizeIntent = new Intent(MainActivity.this, StreamingService.class);
            resizeIntent.setAction("RESIZE_CAPTURE");
            resizeIntent.putExtra("width", width);
            resizeIntent.putExtra("height", height);
            startService(resizeIntent);
        }
        
        @Override
        public void onCapturedContentVisibilityChanged(boolean isVisible) {
            Log.d(TAG, "⚗️ Content visibility: " + isVisible + " - Adjusting transmutation flow");
            // Optimize for Samsung power management
            Intent visibilityIntent = new Intent(MainActivity.this, StreamingService.class);
            visibilityIntent.setAction("VISIBILITY_CHANGED");
            visibilityIntent.putExtra("isVisible", isVisible);
            startService(visibilityIntent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "🔴 Initializing Alchemical Interface - The Great Work begins");
        
        // 🧪 Initialize the Transmutation Circle
        initializeAlchemicalComponents();
        
        // ⚗️ Prepare the Sacred Permissions Ritual
        checkAndRequestPermissions();
    }
    
    /**
     * 🔵 AZURE INITIALIZATION - Preparing the Philosopher's Tools
     */
    private void initializeAlchemicalComponents() {
        // Initialize UI elements
        startButton = findViewById(R.id.btn_start);
        stopButton = findViewById(R.id.btn_stop);
        statusText = findViewById(R.id.tv_status);
        
        // 🔴 Initialize MediaProjection Manager - The Crimson Gateway
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        // Set up button listeners
        startButton.setOnClickListener(v -> initiateTransmutation());
        stopButton.setOnClickListener(v -> haltTransmutation());
        
        updateUI();
    }
    
    /**
     * ⚗️ PERMISSION RITUAL - The Sacred Invocation of System Powers
     */
    private void checkAndRequestPermissions() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.FOREGROUND_SERVICE
        };
        
        // 🔵 Android 14+ Specific Permissions - The Azure Codex
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            String[] android14Permissions = {
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
                Manifest.permission.POST_NOTIFICATIONS
            };
            
            // Combine permission arrays
            String[] allPermissions = new String[permissions.length + android14Permissions.length];
            System.arraycopy(permissions, 0, allPermissions, 0, permissions.length);
            System.arraycopy(android14Permissions, 0, allPermissions, permissions.length, android14Permissions.length);
            permissions = allPermissions;
        }
        
        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        
        if (!allGranted) {
            Log.d(TAG, "🔴 Requesting sacred permissions for the Great Work");
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        } else {
            checkOverlayPermission();
        }
    }
    
    /**
     * 🧪 OVERLAY PERMISSION CHECK - The Ethereal Layer Access
     */
    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "🔵 Requesting overlay permission - Azure layer access");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            checkAccessibilityPermission();
        }
    }
    
    /**
     * ⚗️ ACCESSIBILITY PERMISSION - The Touch Transmutation Gateway
     */
    private void checkAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivityForResult(intent, REQUEST_ACCESSIBILITY_PERMISSION);
        
        Toast.makeText(this, "🔴 Please enable Crystal Crucible Accessibility Service", 
                      Toast.LENGTH_LONG).show();
    }
    
    /**
     * 🔴 INITIATE TRANSMUTATION - Begin the Great Work
     */
    private void initiateTransmutation() {
        Log.d(TAG, "🧪 Initiating screen transmutation ritual");
        
        if (mediaProjectionManager != null) {
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
        }
    }
    
    /**
     * 🔵 HALT TRANSMUTATION - End the Alchemical Process
     */
    private void haltTransmutation() {
        Log.d(TAG, "⚗️ Halting transmutation - Sealing the circle");
        
        isStreaming = false;
        stopAllServices();
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        updateUI();
    }
    
    /**
     * 🔴 STOP ALL SERVICES - Dissolve All Transmutation Processes
     */
    private void stopAllServices() {
        stopService(new Intent(this, ScreenCaptureService.class));
        stopService(new Intent(this, StreamingService.class));
        stopService(new Intent(this, DiscoveryService.class));
    }
    
    /**
     * 🔵 UPDATE UI - Reflect the Current Alchemical State
     */
    private void updateUI() {
        runOnUiThread(() -> {
            if (isStreaming) {
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                statusText.setText("🔴 Transmutation Active - Screen Mirroring to iPad");
            } else {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                statusText.setText("⚗️ Awaiting Transmutation - Ready to Mirror");
            }
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        switch (requestCode) {
            case REQUEST_MEDIA_PROJECTION:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Log.d(TAG, "🧪 MediaProjection granted - Philosopher's Stone activated");
                    
                    // 🔴 CRITICAL: Register callback BEFORE creating MediaProjection
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                    mediaProjection.registerCallback(projectionCallback, null);
                    
                    // Start the alchemical services
                    startTransmutationServices(resultCode, data);
                    
                    isStreaming = true;
                    updateUI();
                } else {
                    Log.e(TAG, "🔴 MediaProjection denied - Transmutation failed");
                    Toast.makeText(this, "⚗️ Screen capture permission required for transmutation", 
                                  Toast.LENGTH_SHORT).show();
                }
                break;
                
            case REQUEST_OVERLAY_PERMISSION:
                if (Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "🔵 Overlay permission granted - Azure layer accessible");
                    checkAccessibilityPermission();
                } else {
                    Log.w(TAG, "🔴 Overlay permission denied - Ethereal layer blocked");
                }
                break;
                
            case REQUEST_ACCESSIBILITY_PERMISSION:
                Log.d(TAG, "⚗️ Returned from accessibility settings");
                break;
        }
    }
    
    /**
     * 🧪 START TRANSMUTATION SERVICES - Activate All Alchemical Processes
     */
    private void startTransmutationServices(int resultCode, Intent data) {
        // Start Screen Capture Service
        Intent captureIntent = new Intent(this, ScreenCaptureService.class);
        captureIntent.putExtra("resultCode", resultCode);
        captureIntent.putExtra("data", data);
        startForegroundService(captureIntent);
        
        // Start Streaming Service
        Intent streamIntent = new Intent(this, StreamingService.class);
        startService(streamIntent);
        
        // Start Discovery Service
        Intent discoveryIntent = new Intent(this, DiscoveryService.class);
        startService(discoveryIntent);
        
        Log.d(TAG, "🔴 All transmutation services activated - The Great Work proceeds");
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
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
                Log.d(TAG, "🧪 All permissions granted - Sacred ritual complete");
                checkOverlayPermission();
            } else {
                Log.e(TAG, "🔴 Permissions denied - Transmutation blocked");
                Toast.makeText(this, "⚗️ All permissions required for the Great Work", 
                              Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🔵 Destroying alchemical interface - Sealing the circle");
        
        if (mediaProjection != null) {
            mediaProjection.unregisterCallback(projectionCallback);
            mediaProjection.stop();
        }
        
        stopAllServices();
    }
}
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
 * 🔴 ALCHEMICAL MAIN ACTIVITY - THE PHILOSOPHER'S INTERFACE
 * 🔵 Transmutes user interactions into screen mirroring gold
 * ⚗️ Handles the sacred ritual of permission acquisition
 */
public class MainActivity extends AppCompatActivity {
    
    // 🔴 CRIMSON CONSTANTS - The Sacred Numbers
    private static final String TAG = "🧪AlchemicalMain";
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_OVERLAY_PERMISSION = 1002;
    private static final int REQUEST_ACCESSIBILITY_PERMISSION = 1003;
    private static final int REQUEST_PERMISSIONS = 1004;
    
    // 🔵 AZURE COMPONENTS - The Transmutation Elements
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private Button startButton, stopButton;
    private TextView statusText;
    private boolean isStreaming = false;
    
    // ⚗️ HERMETIC CALLBACK - The Philosopher's Stone Response
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            Log.d(TAG, "🔴 MediaProjection stopped - Transmutation halted");
            runOnUiThread(() -> {
                isStreaming = false;
                updateUI();
                stopAllServices();
            });
        }
        
        @Override
        public void onCapturedContentResize(int width, int height) {
            Log.d(TAG, "🔵 Content resized: " + width + "x" + height + " - Adjusting alchemical matrix");
            // Handle dynamic resize for Samsung Galaxy S22 Ultra
            Intent resizeIntent = new Intent(MainActivity.this, StreamingService.class);
            resizeIntent.setAction("RESIZE_CAPTURE");
            resizeIntent.putExtra("width", width);
            resizeIntent.putExtra("height", height);
            startService(resizeIntent);
        }
        
        @Override
        public void onCapturedContentVisibilityChanged(boolean isVisible) {
            Log.d(TAG, "⚗️ Content visibility: " + isVisible + " - Adjusting transmutation flow");
            // Optimize for Samsung power management
            Intent visibilityIntent = new Intent(MainActivity.this, StreamingService.class);
            visibilityIntent.setAction("VISIBILITY_CHANGED");
            visibilityIntent.putExtra("isVisible", isVisible);
            startService(visibilityIntent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Log.d(TAG, "🔴 Initializing Alchemical Interface - The Great Work begins");
        
        // 🧪 Initialize the Transmutation Circle
        initializeAlchemicalComponents();
        
        // ⚗️ Prepare the Sacred Permissions Ritual
        checkAndRequestPermissions();
    }
    
    /**
     * 🔵 AZURE INITIALIZATION - Preparing the Philosopher's Tools
     */
    private void initializeAlchemicalComponents() {
        // Initialize UI elements
        startButton = findViewById(R.id.btn_start);
        stopButton = findViewById(R.id.btn_stop);
        statusText = findViewById(R.id.tv_status);
        
        // 🔴 Initialize MediaProjection Manager - The Crimson Gateway
        mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        // Set up button listeners
        startButton.setOnClickListener(v -> initiateTransmutation());
        stopButton.setOnClickListener(v -> haltTransmutation());
        
        updateUI();
    }
    
    /**
     * ⚗️ PERMISSION RITUAL - The Sacred Invocation of System Powers
     */
    private void checkAndRequestPermissions() {
        String[] permissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.FOREGROUND_SERVICE
        };
        
        // 🔵 Android 14+ Specific Permissions - The Azure Codex
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            String[] android14Permissions = {
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
                Manifest.permission.POST_NOTIFICATIONS
            };
            
            // Combine permission arrays
            String[] allPermissions = new String[permissions.length + android14Permissions.length];
            System.arraycopy(permissions, 0, allPermissions, 0, permissions.length);
            System.arraycopy(android14Permissions, 0, allPermissions, permissions.length, android14Permissions.length);
            permissions = allPermissions;
        }
        
        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        
        if (!allGranted) {
            Log.d(TAG, "🔴 Requesting sacred permissions for the Great Work");
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        } else {
            checkOverlayPermission();
        }
    }
    
    /**
     * 🧪 OVERLAY PERMISSION CHECK - The Ethereal Layer Access
     */
    private void checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "🔵 Requesting overlay permission - Azure layer access");
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            checkAccessibilityPermission();
        }
    }
    
    /**
     * ⚗️ ACCESSIBILITY PERMISSION - The Touch Transmutation Gateway
     */
    private void checkAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivityForResult(intent, REQUEST_ACCESSIBILITY_PERMISSION);
        
        Toast.makeText(this, "🔴 Please enable Crystal Crucible Accessibility Service", 
                      Toast.LENGTH_LONG).show();
    }
    
    /**
     * 🔴 INITIATE TRANSMUTATION - Begin the Great Work
     */
    private void initiateTransmutation() {
        Log.d(TAG, "🧪 Initiating screen transmutation ritual");
        
        if (mediaProjectionManager != null) {
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
        }
    }
    
    /**
     * 🔵 HALT TRANSMUTATION - End the Alchemical Process
     */
    private void haltTransmutation() {
        Log.d(TAG, "⚗️ Halting transmutation - Sealing the circle");
        
        isStreaming = false;
        stopAllServices();
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        updateUI();
    }
    
    /**
     * 🔴 STOP ALL SERVICES - Dissolve All Transmutation Processes
     */
    private void stopAllServices() {
        stopService(new Intent(this, ScreenCaptureService.class));
        stopService(new Intent(this, StreamingService.class));
        stopService(new Intent(this, DiscoveryService.class));
    }
    
    /**
     * 🔵 UPDATE UI - Reflect the Current Alchemical State
     */
    private void updateUI() {
        runOnUiThread(() -> {
            if (isStreaming) {
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                statusText.setText("🔴 Transmutation Active - Screen Mirroring to iPad");
            } else {
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                statusText.setText("⚗️ Awaiting Transmutation - Ready to Mirror");
            }
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        switch (requestCode) {
            case REQUEST_MEDIA_PROJECTION:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    Log.d(TAG, "🧪 MediaProjection granted - Philosopher's Stone activated");
                    
                    // 🔴 CRITICAL: Register callback BEFORE creating MediaProjection
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                    mediaProjection.registerCallback(projectionCallback, null);
                    
                    // Start the alchemical services
                    startTransmutationServices(resultCode, data);
                    
                    isStreaming = true;
                    updateUI();
                } else {
                    Log.e(TAG, "🔴 MediaProjection denied - Transmutation failed");
                    Toast.makeText(this, "⚗️ Screen capture permission required for transmutation", 
                                  Toast.LENGTH_SHORT).show();
                }
                break;
                
            case REQUEST_OVERLAY_PERMISSION:
                if (Settings.canDrawOverlays(this)) {
                    Log.d(TAG, "🔵 Overlay permission granted - Azure layer accessible");
                    checkAccessibilityPermission();
                } else {
                    Log.w(TAG, "🔴 Overlay permission denied - Ethereal layer blocked");
                }
                break;
                
            case REQUEST_ACCESSIBILITY_PERMISSION:
                Log.d(TAG, "⚗️ Returned from accessibility settings");
                break;
        }
    }
    
    /**
     * 🧪 START TRANSMUTATION SERVICES - Activate All Alchemical Processes
     */
    private void startTransmutationServices(int resultCode, Intent data) {
        // Start Screen Capture Service
        Intent captureIntent = new Intent(this, ScreenCaptureService.class);
        captureIntent.putExtra("resultCode", resultCode);
        captureIntent.putExtra("data", data);
        startForegroundService(captureIntent);
        
        // Start Streaming Service
        Intent streamIntent = new Intent(this, StreamingService.class);
        startService(streamIntent);
        
        // Start Discovery Service
        Intent discoveryIntent = new Intent(this, DiscoveryService.class);
        startService(discoveryIntent);
        
        Log.d(TAG, "🔴 All transmutation services activated - The Great Work proceeds");
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
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
                Log.d(TAG, "🧪 All permissions granted - Sacred ritual complete");
                checkOverlayPermission();
            } else {
                Log.e(TAG, "🔴 Permissions denied - Transmutation blocked");
                Toast.makeText(this, "⚗️ All permissions required for the Great Work", 
                              Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🔵 Destroying alchemical interface - Sealing the circle");
        
        if (mediaProjection != null) {
            mediaProjection.unregisterCallback(projectionCallback);
            mediaProjection.stop();
        }
        
        stopAllServices();
    }
}
