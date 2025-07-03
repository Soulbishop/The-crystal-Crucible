/**
 * Device Discovery for Screen Mirror PWA
 * Finds Samsung devices on local network using mDNS/Bonjour
 */

class DeviceDiscovery {
    constructor(options = {}) {
        this.options = {
            onDeviceFound: options.onDeviceFound || (() => {}),
            onDeviceLost: options.onDeviceLost || (() => {}),
            onError: options.onError || (() => {})
        };
        
        this.isScanning = false;
        this.discoveredDevices = new Map();
        this.scanInterval = null;
        this.deviceTimeout = 30000; // 30 seconds
        this.scanFrequency = 5000; // 5 seconds
        
        // Service type for Samsung screen mirror devices
        this.serviceType = '_screenmirror._tcp.local.';
        this.fallbackPorts = [8080, 8081, 8082, 8083, 8084];
        
        console.log('Device discovery initialized');
    }
    
    start() {
        if (this.isScanning) {
            console.log('Discovery already running');
            return;
        }
        
        this.isScanning = true;
        console.log('Starting device discovery...');
        
        // Try mDNS discovery first
        this.startMDNSDiscovery();
        
        // Fallback to network scanning
        setTimeout(() => {
            if (this.discoveredDevices.size === 0) {
                this.startNetworkScan();
            }
        }, 3000);
        
        // Start periodic scanning
        this.scanInterval = setInterval(() => {
            this.performScan();
        }, this.scanFrequency);
        
        // Clean up expired devices
        setInterval(() => {
            this.cleanupExpiredDevices();
        }, 10000);
    }
    
    stop() {
        if (!this.isScanning) return;
        
        this.isScanning = false;
        console.log('Stopping device discovery...');
        
        if (this.scanInterval) {
            clearInterval(this.scanInterval);
            this.scanInterval = null;
        }
        
        // Clear all discovered devices
        this.discoveredDevices.clear();
    }
    
    startMDNSDiscovery() {
        // Note: Web browsers don't have direct mDNS access
        // This is a placeholder for potential future WebRTC mDNS support
        // or service worker based discovery
        
        console.log('mDNS discovery not available in browser, using fallback methods');
    }
    
    startNetworkScan() {
        console.log('Starting network scan...');
        
        // Get local network information
        this.getLocalNetworkInfo().then(networkInfo => {
            if (networkInfo) {
                this.scanNetworkRange(networkInfo);
            }
        }).catch(error => {
            console.error('Failed to get network info:', error);
            this.options.onError(error);
        });
    }
    
    async getLocalNetworkInfo() {
        try {
            // Use WebRTC to get local IP address
            const pc = new RTCPeerConnection({
                iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
            });
            
            pc.createDataChannel('');
            const offer = await pc.createOffer();
            await pc.setLocalDescription(offer);
            
            return new Promise((resolve) => {
                pc.onicecandidate = (event) => {
                    if (event.candidate) {
                        const candidate = event.candidate.candidate;
                        const ipMatch = candidate.match(/(\d+\.\d+\.\d+\.\d+)/);
                        
                        if (ipMatch && !ipMatch[1].startsWith('127.')) {
                            const localIP = ipMatch[1];
                            const networkBase = localIP.substring(0, localIP.lastIndexOf('.'));
                            
                            pc.close();
                            resolve({
                                localIP: localIP,
                                networkBase: networkBase
                            });
                        }
                    }
                };
                
                // Timeout after 5 seconds
                setTimeout(() => {
                    pc.close();
                    resolve(null);
                }, 5000);
            });
        } catch (error) {
            console.error('Error getting local network info:', error);
            return null;
        }
    }
    
    async scanNetworkRange(networkInfo) {
        const { networkBase } = networkInfo;
        const promises = [];
        
        // Scan common IP ranges (last octet 1-254)
        for (let i = 1; i <= 254; i++) {
            const ip = `${networkBase}.${i}`;
            
            // Skip our own IP
            if (ip === networkInfo.localIP) continue;
            
            // Test each fallback port
            for (const port of this.fallbackPorts) {
                promises.push(this.testDevice(ip, port));
            }
        }
        
        // Limit concurrent requests
        const batchSize = 20;
        for (let i = 0; i < promises.length; i += batchSize) {
            const batch = promises.slice(i, i + batchSize);
            await Promise.allSettled(batch);
            
            // Small delay between batches
            await new Promise(resolve => setTimeout(resolve, 100));
        }
    }
    
    async testDevice(ip, port) {
        try {
            // Try to connect to the device's discovery endpoint
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), 2000);
            
            const response = await fetch(`http://${ip}:${port}/discovery`, {
                method: 'GET',
                signal: controller.signal,
                mode: 'cors'
            });
            
            clearTimeout(timeoutId);
            
            if (response.ok) {
                const deviceInfo = await response.json();
                this.handleDeviceFound(ip, port, deviceInfo);
            }
        } catch (error) {
            // Device not found or not responding - this is expected for most IPs
            // Only log actual errors, not connection failures
            if (error.name !== 'AbortError' && !error.message.includes('Failed to fetch')) {
                console.debug(`Device test failed for ${ip}:${port}:`, error.message);
            }
        }
    }
    
    handleDeviceFound(ip, port, deviceInfo = {}) {
        const deviceId = `${ip}:${port}`;
        
        // Create device object
        const device = {
            id: deviceId,
            name: deviceInfo.name || `Samsung Device (${ip})`,
            ipAddress: ip,
            port: port,
            type: deviceInfo.type || 'samsung-galaxy',
            model: deviceInfo.model || 'Galaxy S22 Ultra',
            version: deviceInfo.version || '1.0',
            capabilities: deviceInfo.capabilities || ['screen-mirror', 'touch-input'],
            lastSeen: Date.now(),
            ...deviceInfo
        };
        
        // Check if this is a new device
        const isNewDevice = !this.discoveredDevices.has(deviceId);
        
        // Add or update device
        this.discoveredDevices.set(deviceId, device);
        
        if (isNewDevice) {
            console.log('New device found:', device);
            this.options.onDeviceFound(device);
        } else {
            // Update last seen time
            this.discoveredDevices.get(deviceId).lastSeen = Date.now();
        }
    }
    
    performScan() {
        if (!this.isScanning) return;
        
        // Perform a quick scan of known good IPs
        const knownIPs = Array.from(this.discoveredDevices.values())
            .map(device => ({ ip: device.ipAddress, port: device.port }));
        
        knownIPs.forEach(({ ip, port }) => {
            this.testDevice(ip, port);
        });
        
        // Occasionally do a broader scan
        if (Math.random() < 0.1) { // 10% chance
            this.getLocalNetworkInfo().then(networkInfo => {
                if (networkInfo) {
                    // Scan a smaller range for new devices
                    const { networkBase } = networkInfo;
                    const promises = [];
                    
                    for (let i = 1; i <= 50; i++) {
                        const ip = `${networkBase}.${i}`;
                        for (const port of this.fallbackPorts) {
                            promises.push(this.testDevice(ip, port));
                        }
                    }
                    
                    Promise.allSettled(promises);
                }
            });
        }
    }
    
    cleanupExpiredDevices() {
        const now = Date.now();
        const expiredDevices = [];
        
        for (const [deviceId, device] of this.discoveredDevices) {
            if (now - device.lastSeen > this.deviceTimeout) {
                expiredDevices.push(device);
                this.discoveredDevices.delete(deviceId);
            }
        }
        
        expiredDevices.forEach(device => {
            console.log('Device lost:', device);
            this.options.onDeviceLost(device);
        });
    }
    
    getDevices() {
        return Array.from(this.discoveredDevices.values());
    }
    
    getDevice(deviceId) {
        return this.discoveredDevices.get(deviceId);
    }
    
    getDeviceCount() {
        return this.discoveredDevices.size;
    }
    
    // Manual device addition
    addManualDevice(ip, port = 8080, deviceInfo = {}) {
        const deviceId = `${ip}:${port}`;
        
        const device = {
            id: deviceId,
            name: deviceInfo.name || `Manual Device (${ip})`,
            ipAddress: ip,
            port: port,
            type: 'manual',
            model: deviceInfo.model || 'Unknown',
            version: deviceInfo.version || '1.0',
            capabilities: ['screen-mirror', 'touch-input'],
            lastSeen: Date.now(),
            manual: true,
            ...deviceInfo
        };
        
        this.discoveredDevices.set(deviceId, device);
        this.options.onDeviceFound(device);
        
        return device;
    }
    
    removeDevice(deviceId) {
        const device = this.discoveredDevices.get(deviceId);
        if (device) {
            this.discoveredDevices.delete(deviceId);
            this.options.onDeviceLost(device);
            return true;
        }
        return false;
    }
    
    // Device validation
    async validateDevice(device) {
        try {
            const response = await fetch(`http://${device.ipAddress}:${device.port}/status`, {
                method: 'GET',
                timeout: 3000
            });
            
            if (response.ok) {
                const status = await response.json();
                return {
                    valid: true,
                    status: status
                };
            }
        } catch (error) {
            return {
                valid: false,
                error: error.message
            };
        }
        
        return { valid: false };
    }
    
    // Network utilities
    isValidIP(ip) {
        const ipRegex = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/;
        if (!ipRegex.test(ip)) return false;
        
        const parts = ip.split('.');
        return parts.every(part => {
            const num = parseInt(part, 10);
            return num >= 0 && num <= 255;
        });
    }
    
    isPrivateIP(ip) {
        const parts = ip.split('.').map(part => parseInt(part, 10));
        
        // 10.0.0.0/8
        if (parts[0] === 10) return true;
        
        // 172.16.0.0/12
        if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) return true;
        
        // 192.168.0.0/16
        if (parts[0] === 192 && parts[1] === 168) return true;
        
        return false;
    }
    
    // Debug methods
    getDiscoveryStats() {
        return {
            isScanning: this.isScanning,
            deviceCount: this.discoveredDevices.size,
            devices: this.getDevices(),
            scanFrequency: this.scanFrequency,
            deviceTimeout: this.deviceTimeout
        };
    }
}

