/**
 * Screen Mirror PWA - Main Application
 * Coordinates all functionality for Samsung to iPad screen mirroring
 */

class ScreenMirrorApp {
    constructor() {
        this.isConnected = false;
        this.selectedDevice = null;
        this.webrtcClient = null;
        this.deviceDiscovery = null;
        this.touchHandler = null;
        this.videoDisplay = null;
        this.coordinateMapper = null;
        
        this.settings = {
            touchSensitivity: 1.0,
            gestureDelay: 100,
            showTouchIndicator: true,
            hapticFeedback: true,
            videoQuality: 'medium'
        };
        
        this.init();
    }
    
    async init() {
        console.log('Initializing Screen Mirror PWA...');
        
        // Load settings from localStorage
        this.loadSettings();
        
        // Initialize components
        this.initializeComponents();
        
        // Setup event listeners
        this.setupEventListeners();
        
        // Setup UI
        this.setupUI();
        
        // Start device discovery
        this.startDeviceDiscovery();
        
        console.log('Screen Mirror PWA initialized successfully');
    }
    
    initializeComponents() {
        // Initialize coordinate mapper for iPad Air 2 to Samsung Galaxy S22 Ultra
        // These are initial default values, will be updated by device discovery
        this.coordinateMapper = new CoordinateMapper({
            sourceWidth: 2048,  // iPad Air 2 width
            sourceHeight: 1536, // iPad Air 2 height
            targetWidth: 1080,  // Default assumed Samsung width (will be updated)
            targetHeight: 2316  // Default assumed Samsung height (will be updated)
        });
        
        // Initialize video display
        this.videoDisplay = new VideoDisplay('videoCanvas', {
            onFrameReceived: (frame) => this.handleVideoFrame(frame),
            onError: (error) => this.handleVideoError(error)
        });
        
        // Initialize touch handler
        this.touchHandler = new TouchHandler('videoCanvas', {
            sensitivity: this.settings.touchSensitivity,
            gestureDelay: this.settings.gestureDelay,
            showIndicator: this.settings.showTouchIndicator,
            hapticFeedback: this.settings.hapticFeedback,
            onTouch: (touchData) => this.handleTouchEvent(touchData),
            coordinateMapper: this.coordinateMapper
        });
        
        // Initialize device discovery
        this.deviceDiscovery = new DeviceDiscovery({
            onDeviceFound: (device) => this.handleDeviceFound(device),
            onDeviceLost: (device) => this.handleDeviceLost(device),
            onError: (error) => this.handleDiscoveryError(error)
        });
        
        // Initialize WebRTC client
        this.webrtcClient = new WebRTCClient({
            onConnectionStateChange: (state) => this.handleConnectionStateChange(state),
            onVideoFrame: (frame) => this.videoDisplay.displayFrame(frame),
            onError: (error) => this.handleWebRTCError(error),
            onLatencyUpdate: (latency) => this.updateLatencyDisplay(latency)
        });
    }
    
    setupEventListeners() {
        // Connection controls
        document.getElementById('connectBtn').addEventListener('click', () => this.connect());
        document.getElementById('disconnectBtn').addEventListener('click', () => this.disconnect());
        document.getElementById('manualConnectBtn').addEventListener('click', () => this.manualConnect());
        
        // Navigation
        document.getElementById('homeBtn').addEventListener('click', () => this.showHome());
        document.getElementById('settingsBtn').addEventListener('click', () => this.showSettings());
        document.getElementById('helpBtn').addEventListener('click', () => this.showHelp());
        
        // Settings
        document.getElementById('closeSettingsBtn').addEventListener('click', () => this.hideSettings());
        document.getElementById('touchSensitivity').addEventListener('input', (e) => this.updateTouchSensitivity(e.target.value));
        document.getElementById('gestureDelay').addEventListener('input', (e) => this.updateGestureDelay(e.target.value));
        document.getElementById('showTouchIndicator').addEventListener('change', (e) => this.updateShowTouchIndicator(e.target.checked));
        document.getElementById('hapticFeedback').addEventListener('change', (e) => this.updateHapticFeedback(e.target.checked));
        document.getElementById('qualitySelect').addEventListener('change', (e) => this.updateVideoQuality(e.target.value));
        
        // Help modal
        document.getElementById('closeHelpBtn').addEventListener('click', () => this.hideHelp());
        
        // Manual IP input
        document.getElementById('manualIP').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.manualConnect();
            }
        });
        
        // Prevent context menu on long press
        document.addEventListener('contextmenu', (e) => e.preventDefault());
        
        // Handle orientation changes
        window.addEventListener('orientationchange', () => {
            setTimeout(() => this.handleOrientationChange(), 100);
        });
        
        // Handle visibility changes
        document.addEventListener('visibilitychange', () => this.handleVisibilityChange());
        
        // Handle beforeunload
        window.addEventListener('beforeunload', () => this.cleanup());
    }
    
    setupUI() {
        // Update settings UI with current values
        document.getElementById('touchSensitivity').value = this.settings.touchSensitivity;
        document.getElementById('touchSensitivityValue').textContent = this.settings.touchSensitivity;
        document.getElementById('gestureDelay').value = this.settings.gestureDelay;
        document.getElementById('gestureDelayValue').textContent = this.settings.gestureDelay;
        document.getElementById('showTouchIndicator').checked = this.settings.showTouchIndicator;
        document.getElementById('hapticFeedback').checked = this.settings.hapticFeedback;
        document.getElementById('qualitySelect').value = this.settings.videoQuality;
        
        // Set initial navigation state
        this.setActiveNavButton('homeBtn');
    }
    
    startDeviceDiscovery() {
        console.log('Starting device discovery...');
        this.deviceDiscovery.start();
        this.updateDiscoveryStatus('Searching for Samsung devices...');
    }
    
    handleDeviceFound(device) {
        console.log('Device found:', device);
        this.addDeviceToList(device);
        this.updateDiscoveryStatus(`Found ${this.deviceDiscovery.getDeviceCount()} device(s)`);
    }
    
    handleDeviceLost(device) {
        console.log('Device lost:', device);
        this.removeDeviceFromList(device);
        this.updateDiscoveryStatus(`Found ${this.deviceDiscovery.getDeviceCount()} device(s)`);
    }
    
    handleDiscoveryError(error) {
        console.error('Discovery error:', error);
        this.showToast('Device discovery failed', 'error');
        this.updateDiscoveryStatus('Discovery failed - try manual connection');
    }
    
    addDeviceToList(device) {
        const deviceList = document.getElementById('deviceList');
        const discoveryStatus = document.getElementById('discoveryStatus');
        
        // Hide discovery status if this is the first device
        if (this.deviceDiscovery.getDeviceCount() === 1) {
            discoveryStatus.style.display = 'none';
        }
        
        const deviceItem = document.createElement('div');
        deviceItem.className = 'device-item';
        deviceItem.dataset.deviceId = device.id;
        deviceItem.innerHTML = `
            <div class="device-info">
                <h4>${device.name}</h4>
                <p>${device.ipAddress}:${device.port}</p>
            </div>
            <div class="device-status">Available</div>
        `;
        
        deviceItem.addEventListener('click', () => this.selectDevice(device));
        deviceList.appendChild(deviceItem);
    }
    
    removeDeviceFromList(device) {
        const deviceItem = document.querySelector(`[data-device-id="${device.id}"]`);
        if (deviceItem) {
            deviceItem.remove();
        }
        
        // Show discovery status if no devices remain
        if (this.deviceDiscovery.getDeviceCount() === 0) {
            document.getElementById('discoveryStatus').style.display = 'flex';
            this.updateDiscoveryStatus('No devices found - try manual connection');
        }
        
        // Clear selection if this was the selected device
        if (this.selectedDevice && this.selectedDevice.id === device.id) {
            this.selectedDevice = null;
            document.getElementById('connectBtn').disabled = true;
        }
    }
    
    selectDevice(device) {
        // Remove previous selection
        document.querySelectorAll('.device-item').forEach(item => {
            item.classList.remove('selected');
        });
        
        // Select new device
        const deviceItem = document.querySelector(`[data-device-id="${device.id}"]`);
        if (deviceItem) {
            deviceItem.classList.add('selected');
        }
        
        this.selectedDevice = device;
        document.getElementById('connectBtn').disabled = false;
        
        console.log('Selected device:', device);
    }
    
    updateDiscoveryStatus(message) {
        const statusElement = document.querySelector('#discoveryStatus span');
        if (statusElement) {
            statusElement.textContent = message;
        }
    }
    
    async connect() {
        if (!this.selectedDevice) {
            this.showToast('Please select a device first', 'warning');
            return;
        }
        
        try {
            this.showLoading('Connecting to ' + this.selectedDevice.name + '...');
            await this.webrtcClient.connect(this.selectedDevice.ipAddress, this.selectedDevice.port);
        } catch (error) {
            console.error('Connection failed:', error);
            this.hideLoading();
            this.showToast('Connection failed: ' + error.message, 'error');
        }
    }
    
    async manualConnect() {
        const ipInput = document.getElementById('manualIP');
        let fullAddress = ipInput.value.trim(); // Get the full string from input
        let ipAddress;
        let port = 8080; // Default port

        // Regex to parse IP and optional port
        const ipPortRegex = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}(?::(\d+))?$/;
        const match = fullAddress.match(ipPortRegex);

        if (!match) {
            this.showToast('Please enter a valid IP address (e.g., 192.168.1.100 or 192.168.1.100:8080)', 'error');
            return;
        }

        ipAddress = match[0]; // Full matched string, without port if not specified
        if (match[1]) { // If port group was captured
            port = parseInt(match[1], 10);
            ipAddress = fullAddress.substring(0, fullAddress.indexOf(':')); // Extract just the IP part
        }

        // Basic IP range validation for 0-255 in each octet
        const octets = ipAddress.split('.').map(Number);
        if (octets.length !== 4 || octets.some(octet => octet < 0 || octet > 255)) {
            this.showToast('Invalid IP address range. Each part must be 0-255.', 'error');
            return;
        }

        // Port validation
        if (port < 1 || port > 65535) {
            this.showToast('Invalid port number. Must be between 1 and 65535.', 'error');
            return;
        }
        
        try {
            this.showLoading('Connecting to ' + ipAddress + ':' + port + '...');
            await this.webrtcClient.connect(ipAddress, port);
        } catch (error) {
            console.error('Manual connection failed:', error);
            this.hideLoading();
            this.showToast('Connection failed: ' + error.message, 'error');
        }
    }
    
    async disconnect() {
        try {
            await this.webrtcClient.disconnect();
            this.showToast('Disconnected successfully', 'success');
        } catch (error) {
            console.error('Disconnect failed:', error);
            this.showToast('Disconnect failed: ' + error.message, 'error');
        }
    }
    
    handleConnectionStateChange(state) {
        console.log('Connection state changed:', state);
        
        const statusIndicator = document.getElementById('connectionStatus');
        const statusDot = statusIndicator.querySelector('.status-dot');
        const statusText = statusIndicator.querySelector('.status-text');
        const connectBtn = document.getElementById('connectBtn');
        const disconnectBtn = document.getElementById('disconnectBtn');
        const connectionPanel = document.getElementById('connectionPanel');
        const videoContainer = document.getElementById('videoContainer');
        
        switch (state) {
            case 'connecting':
                statusDot.className = 'status-dot connecting';
                statusText.textContent = 'Connecting...';
                this.isConnected = false;
                break;
                
            case 'connected':
                statusDot.className = 'status-dot connected';
                statusText.textContent = 'Connected';
                connectBtn.style.display = 'none';
                disconnectBtn.style.display = 'block';
                connectionPanel.style.display = 'none';
                videoContainer.style.display = 'block';
                this.isConnected = true;
                this.hideLoading();
                this.showToast('Connected successfully!', 'success');
                break;
                
            case 'disconnected':
                statusDot.className = 'status-dot disconnected';
                statusText.textContent = 'Disconnected';
                connectBtn.style.display = 'block';
                disconnectBtn.style.display = 'none';
                connectionPanel.style.display = 'block';
                videoContainer.style.display = 'none';
                this.isConnected = false;
                this.hideLoading();
                this.showToast('Disconnected successfully', 'success');
                break;
                
            case 'failed':
                statusDot.className = 'status-dot disconnected';
                statusText.textContent = 'Connection Failed';
                connectBtn.style.display = 'block';
                disconnectBtn.style.display = 'none';
                connectionPanel.style.display = 'block';
                videoContainer.style.display = 'none';
                this.isConnected = false;
                this.hideLoading();
                this.showToast('Connection failed', 'error');
                break;
        }
    }
    
    handleTouchEvent(touchData) {
        if (!this.isConnected) return;
        
        // Send touch data to Samsung device via WebRTC
        this.webrtcClient.sendTouchData(touchData);
        
        // Show touch indicator if enabled
        if (this.settings.showTouchIndicator) {
            this.showTouchIndicator(touchData.x, touchData.y);
        }
        
        // Provide haptic feedback if enabled
        if (this.settings.hapticFeedback && navigator.vibrate) {
            navigator.vibrate(10);
        }
    }
    
    handleVideoFrame(frame) {
        // Video frame handling is done by VideoDisplay component
        // This is called for any additional processing needed
    }
    
    handleVideoError(error) {
        console.error('Video error:', error);
        this.showToast('Video error: ' + error.message, 'error');
    }
    
    handleWebRTCError(error) {
        console.error('WebRTC error:', error);
        this.showToast('Connection error: ' + error.message, 'error');
    }
    
    showTouchIndicator(x, y) {
        const indicator = document.getElementById('touchIndicator');
        indicator.style.left = x + 'px';
        indicator.style.top = y + 'px';
        indicator.classList.add('active');
        
        setTimeout(() => {
            indicator.classList.remove('active');
        }, 200);
    }
    
    updateLatencyDisplay(latency) {
        const latencyElement = document.getElementById('latencyValue');
        if (latencyElement) {
            latencyElement.textContent = Math.round(latency);
        }
    }
    
    // Navigation methods
    showHome() {
        this.hideAllPanels();
        document.getElementById('connectionPanel').style.display = 'block';
        this.setActiveNavButton('homeBtn');
    }
    
    showSettings() {
        this.hideAllPanels();
        document.getElementById('settingsPanel').style.display = 'block';
        this.setActiveNavButton('settingsBtn');
    }
    
    hideSettings() {
        document.getElementById('settingsPanel').style.display = 'none';
        this.showHome();
    }
    
    showHelp() {
        document.getElementById('helpModal').style.display = 'flex';
        this.setActiveNavButton('helpBtn');
    }
    
    hideHelp() {
        document.getElementById('helpModal').style.display = 'none';
        this.setActiveNavButton('homeBtn');
    }
    
    hideAllPanels() {
        document.getElementById('connectionPanel').style.display = 'none';
        document.getElementById('settingsPanel').style.display = 'none';
    }
    
    setActiveNavButton(buttonId) {
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.classList.remove('active');
        });
        document.getElementById(buttonId).classList.add('active');
    }
    
    // Settings methods
    updateTouchSensitivity(value) {
        this.settings.touchSensitivity = parseFloat(value);
        document.getElementById('touchSensitivityValue').textContent = value;
        this.touchHandler.updateSensitivity(this.settings.touchSensitivity);
        this.saveSettings();
    }
    
    updateGestureDelay(value) {
        this.settings.gestureDelay = parseInt(value);
        document.getElementById('gestureDelayValue').textContent = value;
        this.touchHandler.updateGestureDelay(this.settings.gestureDelay);
        this.saveSettings();
    }
    
    updateShowTouchIndicator(checked) {
        this.settings.showTouchIndicator = checked;
        this.touchHandler.updateShowIndicator(checked);
        this.saveSettings();
    }
    
    updateHapticFeedback(checked) {
        this.settings.hapticFeedback = checked;
        this.touchHandler.updateHapticFeedback(checked);
        this.saveSettings();
    }
    
    updateVideoQuality(quality) {
        this.settings.videoQuality = quality;
        if (this.webrtcClient) {
            this.webrtcClient.updateVideoQuality(quality);
        }
        this.saveSettings();
    }
    
    // Utility methods
    showLoading(message) {
        const overlay = document.getElementById('loadingOverlay');
        const text = document.getElementById('loadingText');
        text.textContent = message;
        overlay.style.display = 'flex';
    }
    
    hideLoading() {
        document.getElementById('loadingOverlay').style.display = 'none';
    }
    
    showToast(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;
        
        container.appendChild(toast);
        
        setTimeout(() => {
            toast.remove();
        }, 4000);
    }
    
    handleOrientationChange() {
        // Recalculate coordinate mapping for new orientation
        if (this.coordinateMapper) {
            this.coordinateMapper.updateOrientation();
        }
        
        // Resize video display
        if (this.videoDisplay) {
            this.videoDisplay.resize();
        }
    }
    
    handleVisibilityChange() {
        if (document.hidden) {
            // App is hidden, pause video if needed
            if (this.videoDisplay) {
                this.videoDisplay.pause();
            }
        } else {
            // App is visible, resume video
            if (this.videoDisplay) {
                this.videoDisplay.resume();
            }
        }
    }
    
    loadSettings() {
        try {
            const saved = localStorage.getItem('screenMirrorSettings');
            if (saved) {
                const settings = JSON.parse(saved);
                this.settings = { ...this.settings, ...settings };
            }
        } catch (error) {
            console.error('Failed to load settings:', error);
        }
    }
    
    saveSettings() {
        try {
            localStorage.setItem('screenMirrorSettings', JSON.stringify(this.settings));
        } catch (error) {
            console.error('Failed to save settings:', error);
        }
    }
    
    cleanup() {
        if (this.webrtcClient) {
            this.webrtcClient.disconnect();
        }
        if (this.deviceDiscovery) {
            this.deviceDiscovery.stop();
        }
    }
}

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.screenMirrorApp = new ScreenMirrorApp();
});

// Handle app installation
let deferredPrompt;
window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    
    // Show install button or prompt
    console.log('App can be installed');
});

// Handle successful installation
window.addEventListener('appinstalled', (evt) => {
    console.log('App was installed successfully');
});
