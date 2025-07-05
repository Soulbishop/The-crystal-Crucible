/**
 * 🧪 WebRTC Client for Screen Mirror PWA - ALCHEMICAL EDITION
 * 🔴 Handles connection to Samsung Galaxy S22 Ultra via WebSocket alchemy
 * 🔵 Optimized for iPad Air 2 memory constraints
 */

class WebRTCClient {
    constructor(options = {}) {
        this.options = {
            onConnectionStateChange: options.onConnectionStateChange || (() => {}),
            onVideoFrame: options.onVideoFrame || (() => {}),
            onError: options.onError || (() => {}),
            onLatencyUpdate: options.onLatencyUpdate || (() => {})
        };
        
        // 🔴 CRIMSON VARIABLES - Core Connection State
        this.websocket = null;
        this.isConnected = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 2000;
        
        // 🔵 AZURE VARIABLES - Performance Monitoring
        this.latencyInterval = null;
        this.lastPingTime = 0;
        this.connectionStartTime = 0;
        
        // ⚗️ HERMETIC VARIABLES - iPad Air 2 Optimization
        this.messageQueue = [];
        this.isProcessingQueue = false;
        this.maxQueueSize = 50; // Memory constraint for iPad Air 2
        
        console.log('🧪 WebRTC Client initialized - Alchemical protocol ready');
    }
    
    async connect(ipAddress, port = 8080) {
        try {
            console.log(`🔴 Initiating transmutation to ${ipAddress}:${port}...`);
            this.options.onConnectionStateChange('connecting');
            this.connectionStartTime = Date.now();
            
            // 🧪 Create direct WebSocket connection (no /signaling endpoint)
            await this.createAlchemicalWebSocket(ipAddress, port);
            
        } catch (error) {
            console.error('🔴 Transmutation failed:', error);
            this.options.onConnectionStateChange('failed');
            this.options.onError(error);
            throw error;
        }
    }
    
    async createAlchemicalWebSocket(ipAddress, port) {
        return new Promise((resolve, reject) => {
            // 🔴 DIRECT WEBSOCKET CONNECTION - No signaling endpoint
            const wsUrl = `ws://${ipAddress}:${port}`;
            console.log(`🧪 Establishing alchemical link: ${wsUrl}`);
            
            this.websocket = new WebSocket(wsUrl);
            
            this.websocket.onopen = () => {
                console.log('🔵 Alchemical WebSocket link established');
                this.isConnected = true;
                this.reconnectAttempts = 0;
                this.options.onConnectionStateChange('connected');
                this.startLatencyMonitoring();
                this.sendConnectionHandshake();
                resolve();
            };
            
            this.websocket.onmessage = (event) => {
                this.handleAlchemicalMessage(event.data);
            };
            
            this.websocket.onerror = (error) => {
                console.error('🔴 Alchemical link error:', error);
                reject(new Error('WebSocket transmutation failed'));
            };
            
            this.websocket.onclose = (event) => {
                console.log('⚗️ Alchemical link severed:', event.code, event.reason);
                this.handleDisconnection();
                
                // 🔵 Auto-reconnection alchemy for unstable connections
                if (this.isConnected && this.reconnectAttempts < this.maxReconnectAttempts) {
                    this.attemptReconnection(ipAddress, port);
                }
            };
            
            // 🧪 Timeout protection for iPad Air 2
            setTimeout(() => {
                if (this.websocket.readyState !== WebSocket.OPEN) {
                    this.websocket.close();
                    reject(new Error('Alchemical link timeout - Samsung device not responding'));
                }
            }, 10000);
        });
    }
    
    sendConnectionHandshake() {
        // 🔴 Send initial handshake to Samsung device
        const handshake = {
            type: 'connection_request',
            device: 'iPad Air 2',
            client: 'Crystal Crucible Web Client',
            capabilities: ['touch_input', 'video_display'],
            screen_resolution: {
                width: window.screen.width,
                height: window.screen.height
            },
            timestamp: Date.now()
        };
        
        this.sendAlchemicalMessage(handshake);
        console.log('🧪 Connection handshake transmitted');
    }
    
    handleAlchemicalMessage(data) {
        try {
            const message = JSON.parse(data);
            console.log('🔵 Received alchemical message:', message.type);
            
            // 🧪 Add to processing queue for iPad Air 2 memory management
            if (this.messageQueue.length >= this.maxQueueSize) {
                this.messageQueue.shift(); // Remove oldest message
                console.warn('⚗️ Message queue overflow - discarding old messages');
            }
            
            this.messageQueue.push(message);
            this.processMessageQueue();
            
        } catch (error) {
            console.error('🔴 Failed to parse alchemical message:', error);
        }
    }
    
    async processMessageQueue() {
        if (this.isProcessingQueue) return;
        
        this.isProcessingQueue = true;
        
        while (this.messageQueue.length > 0) {
            const message = this.messageQueue.shift();
            await this.processMessage(message);
            
            // 🔵 Yield control to prevent blocking iPad Air 2
            await new Promise(resolve => setTimeout(resolve, 1));
        }
        
        this.isProcessingQueue = false;
    }
    
    async processMessage(message) {
        switch (message.type) {
            case 'connection_established':
                console.log('🧪 Samsung device confirmed connection');
                this.handleConnectionEstablished(message);
                break;
                
            case 'video_frame':
                // 🔴 Handle video frame data
                this.handleVideoFrame(message);
                break;
                
            case 'resize':
                console.log('🔵 Screen resolution update:', message.width, 'x', message.height);
                this.handleScreenResize(message);
                break;
                
            case 'visibility':
                console.log('⚗️ Visibility change:', message.visible);
                this.handleVisibilityChange(message);
                break;
                
            case 'status':
                console.log('🧪 Status update:', message.status);
                this.handleStatusUpdate(message);
                break;
                
            case 'pong':
                this.handlePongMessage(message);
                break;
                
            case 'error':
                console.error('🔴 Samsung device error:', message.error);
                this.options.onError(new Error(message.error));
                break;
                
            default:
                console.warn('⚗️ Unknown message type:', message.type);
        }
    }
    
    handleConnectionEstablished(message) {
        // 🔵 Update coordinate mapping with Samsung device resolution
        if (message.screen_resolution) {
            const event = new CustomEvent('samsungResolutionUpdate', {
                detail: message.screen_resolution
            });
            window.dispatchEvent(event);
        }
        
        // 🧪 Connection fully established
        console.log('🔴 Alchemical transmutation complete - Ready for screen mirroring');
    }
    
    handleVideoFrame(message) {
        // 🔵 For now, we'll use WebRTC for actual video streaming
        // This handles video metadata and control messages
        if (message.metadata) {
            this.options.onVideoFrame({
                metadata: message.metadata,
                timestamp: message.timestamp
            });
        }
    }
    
    handleScreenResize(message) {
        // 🧪 Notify coordinate mapper of Samsung screen changes
        const event = new CustomEvent('samsungResolutionUpdate', {
            detail: {
                width: message.width,
                height: message.height
            }
        });
        window.dispatchEvent(event);
    }
    
    handleVisibilityChange(message) {
        // ⚗️ Handle Samsung app visibility changes
        if (!message.visible) {
            console.log('🔴 Samsung app hidden - pausing touch input');
        } else {
            console.log('🔵 Samsung app visible - resuming touch input');
        }
    }
    
    handleStatusUpdate(message) {
        // 🧪 Handle various status updates from Samsung device
        switch (message.status) {
            case 'transmutation_started':
                console.log('🔴 Samsung transmutation activated');
                break;
            case 'transmutation_stopped':
                console.log('🔵 Samsung transmutation halted');
                break;
        }
    }
    
    sendTouchData(touchData) {
        if (!this.isConnected || !this.websocket || this.websocket.readyState !== WebSocket.OPEN) {
            console.warn('⚗️ Cannot send touch data - alchemical link not established');
            return;
        }
        
        try {
            // 🔴 Enhanced touch message for Samsung processing
            const touchMessage = {
                type: 'touch',
                action: touchData.action,
                x: touchData.x,
                y: touchData.y,
                pressure: touchData.pressure || 1.0,
                timestamp: Date.now(),
                device_info: {
                    screen_width: window.screen.width,
                    screen_height: window.screen.height,
                    pixel_ratio: window.devicePixelRatio || 1
                }
            };
            
            // 🧪 Add gesture-specific data
            if (touchData.gesture) {
                touchMessage.gesture = touchData.gesture;
            }
            
            this.sendAlchemicalMessage(touchMessage);
            
        } catch (error) {
            console.error('🔴 Failed to transmit touch data:', error);
        }
    }
    
    sendAlchemicalMessage(message) {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            try {
                const jsonMessage = JSON.stringify(message);
                this.websocket.send(jsonMessage);
            } catch (error) {
                console.error('🔴 Failed to send alchemical message:', error);
            }
        } else {
            console.warn('⚗️ Cannot send message - WebSocket not connected');
        }
    }
    
    startLatencyMonitoring() {
        // 🔵 Start ping monitoring for connection quality
        this.latencyInterval = setInterval(() => {
            this.sendPing();
        }, 2000); // Reduced frequency for iPad Air 2
    }
    
    sendPing() {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            this.lastPingTime = Date.now();
            const pingMessage = {
                type: 'ping',
                timestamp: this.lastPingTime
            };
            this.sendAlchemicalMessage(pingMessage);
        }
    }
    
    handlePongMessage(message) {
        const latency = Date.now() - message.timestamp;
        this.options.onLatencyUpdate(latency);
    }
    
    attemptReconnection(ipAddress, port) {
        this.reconnectAttempts++;
        console.log(`🧪 Attempting alchemical reconnection ${this.reconnectAttempts}/${this.maxReconnectAttempts}`);
        
        setTimeout(() => {
            this.connect(ipAddress, port).catch(error => {
                console.error('🔴 Reconnection failed:', error);
                
                if (this.reconnectAttempts >= this.maxReconnectAttempts) {
                    console.error('⚗️ Maximum reconnection attempts reached');
                    this.options.onConnectionStateChange('failed');
                    this.options.onError(new Error('Connection lost - maximum reconnection attempts reached'));
                }
            });
        }, this.reconnectDelay * this.reconnectAttempts);
    }
    
    updateVideoQuality(quality) {
        // 🔵 Send quality change request to Samsung device
        const qualityMessage = {
            type: 'quality_change',
            quality: quality,
            timestamp: Date.now()
        };
        
        this.sendAlchemicalMessage(qualityMessage);
        console.log('🧪 Video quality change requested:', quality);
    }
    
    async disconnect() {
        console.log('🔴 Initiating disconnection ritual...');
        
        this.isConnected = false;
        this.reconnectAttempts = this.maxReconnectAttempts; // Prevent reconnection
        
        // 🧪 Stop latency monitoring
        if (this.latencyInterval) {
            clearInterval(this.latencyInterval);
            this.latencyInterval = null;
        }
        
        // 🔵 Send disconnection message
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            const disconnectMessage = {
                type: 'disconnect',
                reason: 'user_initiated',
                timestamp: Date.now()
            };
            this.sendAlchemicalMessage(disconnectMessage);
        }
        
        // ⚗️ Close WebSocket connection
        if (this.websocket) {
            this.websocket.close(1000, 'Normal closure');
            this.websocket = null;
        }
        
        // 🧪 Clear message queue for memory cleanup
        this.messageQueue = [];
        this.isProcessingQueue = false;
        
        this.options.onConnectionStateChange('disconnected');
        console.log('🔴 Alchemical disconnection complete');
    }
    
    handleDisconnection() {
        if (this.isConnected) {
            this.isConnected = false;
            this.options.onConnectionStateChange('disconnected');
            
            // 🔵 Stop latency monitoring
            if (this.latencyInterval) {
                clearInterval(this.latencyInterval);
                this.latencyInterval = null;
            }
            
            console.log('⚗️ Alchemical link severed');
        }
    }
    
    getConnectionStats() {
        // 🧪 Return connection statistics for debugging
        return {
            isConnected: this.isConnected,
            reconnectAttempts: this.reconnectAttempts,
            messageQueueLength: this.messageQueue.length,
            connectionDuration: this.isConnected ? Date.now() - this.connectionStartTime : 0,
            websocketState: this.websocket ? this.websocket.readyState : 'null'
        };
    }
}
