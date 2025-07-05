/**
 * 🧪 Device Discovery - ALCHEMICAL EDITION
 * 🔴 Simplified discovery for direct IP connection
 * 🔵 Optimized for iPad Air 2 performance constraints
 */

class DeviceDiscovery {
    constructor(options = {}) {
        this.options = {
            onDeviceFound: options.onDeviceFound || (() => {}),
            onDeviceLost: options.onDeviceLost || (() => {}),
            onError: options.onError || (() => {})
        };
        
        // 🔴 CRIMSON VARIABLES - Discovery State
        this.isDiscovering = false;
        this.discoveredDevices = new Map();
        this.discoveryInterval = null;
        
        // 🔵 AZURE VARIABLES - Network Configuration
        this.commonPorts = [8080, 8081, 8082, 8888, 9090];
        this.networkTimeout = 3000;
        this.discoveryFrequency = 10000; // 10 seconds
        
        // ⚗️ HERMETIC VARIABLES - iPad Air 2 Optimization
        this.maxConcurrentChecks = 3; // Limit concurrent requests
        this.activeChecks = 0;
        
        console.log('🧪 Device Discovery initialized - Alchemical scanning ready');
    }
    
    start() {
        if (this.isDiscovering) {
            console.log('⚗️ Discovery already active');
            return;
        }
        
        console.log('🔴 Starting alchemical device discovery...');
        this.isDiscovering = true;
        
        // 🧪 Start immediate discovery
        this.performDiscovery();
        
        // 🔵 Set up periodic discovery
        this.discoveryInterval = setInterval(() => {
            this.performDiscovery();
        }, this.discoveryFrequency);
    }
    
    stop() {
        console.log('🔴 Stopping alchemical device discovery...');
        this.isDiscovering = false;
        
        if (this.discoveryInterval) {
            clearInterval(this.discoveryInterval);
            this.discoveryInterval = null;
        }
        
        // 🧪 Clear discovered devices
        this.discoveredDevices.clear();
    }
    
    async performDiscovery() {
        if (!this.isDiscovering) return;
        
        try {
            // 🔴 Get local network range
            const networkRange = await this.getLocalNetworkRange();
            
            if (networkRange) {
                console.log('🧪 Scanning network range:', networkRange);
                await this.scanNetworkRange(networkRange);
            } else {
                console.log('🔵 Using common device discovery methods');
                await this.performCommonDiscovery();
            }
            
        } catch (error) {
            console.error('🔴 Discovery error:', error);
            this.options.onError(error);
        }
    }
    
    async getLocalNetworkRange() {
        // 🧪 Simplified network detection for web environment
        // In a real implementation, this would use more sophisticated methods
        
        try {
            // 🔴 Try to detect local IP via WebRTC
            const localIP = await this.getLocalIPAddress();
            if (localIP) {
                const parts = localIP.split('.');
                if (parts.length === 4) {
                    const baseIP = `${parts[0]}.${parts[1]}.${parts[2]}`;
                    return { baseIP, start: 1, end: 254 };
                }
            }
        } catch (error) {
            console.warn('⚗️ Could not detect local network range:', error);
        }
        
        return null;
    }
    
    async getLocalIPAddress() {
        return new Promise((resolve) => {
            // 🔵 Use WebRTC to get local IP
            const pc = new RTCPeerConnection({
                iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
            });
            
            pc.createDataChannel('');
            pc.createOffer().then(offer => pc.setLocalDescription(offer));
            
            pc.onicecandidate = (event) => {
                if (event.candidate) {
                    const candidate = event.candidate.candidate;
                    const ipMatch = candidate.match(/(\d+\.\d+\.\d+\.\d+)/);
                    if (ipMatch) {
                        pc.close();
                        resolve(ipMatch[1]);
                        return;
                    }
                }
            };
            
            // 🧪 Timeout after 5 seconds
            setTimeout(() => {
                pc.close();
                resolve(null);
            }, 5000);
        });
    }
    
    async scanNetworkRange(range) {
        const { baseIP, start, end } = range;
        const promises = [];
        
        // 🔴 Limit concurrent scans for iPad Air 2
        for (let i = start; i <= end; i += this.maxConcurrentChecks) {
            const batch = [];
            
            for (let j = 0; j < this.maxConcurrentChecks && (i + j) <= end; j++) {
                const ip = `${baseIP}.${i + j}`;
                batch.push(this.checkDevice(ip));
            }
            
            // 🧪 Process batch and wait before next
            const results = await Promise.allSettled(batch);
            results.forEach(result => {
                if (result.status === 'fulfilled' && result.value) {
                    this.handleDeviceFound(result.value);
                }
            });
            
            // ⚗️ Small delay to prevent overwhelming iPad Air 2
            await new Promise(resolve => setTimeout(resolve, 100));
        }
    }
    
    async performCommonDiscovery() {
        // 🔵 Check common local addresses
        const commonIPs = [
            '192.168.1.1', '192.168.1.100', '192.168.1.101', '192.168.1.102',
            '192.168.0.1', '192.168.0.100', '192.168.0.101', '192.168.0.102',
            '10.0.0.1', '10.0.0.100', '10.0.0.101', '10.0.0.102'
        ];
        
        for (const ip of commonIPs) {
            try {
                const device = await this.checkDevice(ip);
                if (device) {
                    this.handleDeviceFound(device);
                }
            } catch (error) {
                // 🧪 Ignore individual check failures
            }
            
            // ⚗️ Small delay between checks
            await new Promise(resolve => setTimeout(resolve, 50));
        }
    }
    
    async checkDevice(ipAddress) {
        // 🔴 Check each common port for Samsung device
        for (const port of this.commonPorts) {
            try {
                const device = await this.testConnection(ipAddress, port);
                if (device) {
                    return device;
                }
            } catch (error) {
                // 🧪 Continue to next port
            }
        }
        
        return null;
    }
    
    async testConnection(ipAddress, port) {
        return new Promise((resolve) => {
            const wsUrl = `ws://${ipAddress}:${port}`;
            const ws = new WebSocket(wsUrl);
            
            const timeout = setTimeout(() => {
                ws.close();
                resolve(null);
            }, this.networkTimeout);
            
            ws.onopen = () => {
                clearTimeout(timeout);
                
                // 🔵 Send discovery message
                const discoveryMessage = {
                    type: 'discovery_request',
                    client: 'Crystal Crucible iPad',
                    timestamp: Date.now()
                };
                
                ws.send(JSON.stringify(discoveryMessage));
                
                // 🧪 Wait for response
                const responseTimeout = setTimeout(() => {
                    ws.close();
                    resolve(null);
                }, 2000);
                
                ws.onmessage = (event) => {
                    clearTimeout(responseTimeout);
                    
                    try {
                        const response = JSON.parse(event.data);
                        if (response.type === 'discovery_response') {
                            const device = {
                                id: `${ipAddress}:${port}`,
                                name: response.device_name || 'Samsung Galaxy S22 Ultra',
                                ipAddress: ipAddress,
                                port: port,
                                capabilities: response.capabilities || [],
                                lastSeen: Date.now()
                            };
                            
                            ws.close();
                            resolve(device);
                        } else {
                            ws.close();
                            resolve(null);
                        }
                    } catch (error) {
                        ws.close();
                        resolve(null);
                    }
                };
            };
            
            ws.onerror = () => {
                clearTimeout(timeout);
                resolve(null);
            };
        });
    }
    
    handleDeviceFound(device) {
        const existingDevice = this.discoveredDevices.get(device.id);
        
        if (!existingDevice) {
            // 🔴 New device discovered
            this.discoveredDevices.set(device.id, device);
            this.options.onDeviceFound(device);
            console.log('🧪 New Samsung device discovered:', device.name);
        } else {
            // 🔵 Update existing device timestamp
            existingDevice.lastSeen = Date.now();
        }
    }
    
    getDeviceCount() {
        return this.discoveredDevices.size;
    }
    
    getDevices() {
        return Array.from(this.discoveredDevices.values());
    }
    
    removeStaleDevices() {
        const now = Date.now();
        const staleThreshold = 30000; // 30 seconds
        
        for (const [id, device] of this.discoveredDevices) {
            if (now - device.lastSeen > staleThreshold) {
                this.discoveredDevices.delete(id);
                this.options.onDeviceLost(device);
                console.log('⚗️ Samsung device lost:', device.name);
            }
        }
    }
}
