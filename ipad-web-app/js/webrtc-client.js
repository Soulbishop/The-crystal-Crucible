/**
 * WebRTC Client for Screen Mirror PWA
 * Handles connection to Samsung device and media streaming
 */

class WebRTCClient {
    constructor(options = {}) {
        this.options = {
            onConnectionStateChange: options.onConnectionStateChange || (() => {}),
            onVideoFrame: options.onVideoFrame || (() => {}),
            onError: options.onError || (() => {}),
            onLatencyUpdate: options.onLatencyUpdate || (() => {})
        };
        
        this.peerConnection = null;
        this.dataChannel = null;
        this.websocket = null;
        this.isConnected = false;
        this.latencyInterval = null;
        this.lastPingTime = 0;
        
        this.videoQualitySettings = {
            high: { width: 1920, height: 1080, framerate: 60, bitrate: 8000000 },
            medium: { width: 1280, height: 720, framerate: 30, bitrate: 4000000 },
            low: { width: 854, height: 480, framerate: 20, bitrate: 2000000 }
        };
        
        this.currentQuality = 'medium';
    }
    
    async connect(ipAddress, port = 8080) {
        try {
            console.log(`Connecting to ${ipAddress}:${port}...`);
            this.options.onConnectionStateChange('connecting');
            
            // Create WebSocket connection for signaling
            await this.createWebSocketConnection(ipAddress, port);
            
            // Create peer connection
            await this.createPeerConnection();
            
            // Create data channel for touch input
            this.createDataChannel();
            
            // Start connection process
            await this.startConnection();
            
        } catch (error) {
            console.error('Connection failed:', error);
            this.options.onConnectionStateChange('failed');
            this.options.onError(error);
            throw error;
        }
    }
    
    async createWebSocketConnection(ipAddress, port) {
        return new Promise((resolve, reject) => {
            const wsUrl = `ws://${ipAddress}:${port}/signaling`;
            this.websocket = new WebSocket(wsUrl);
            
            this.websocket.onopen = () => {
                console.log('WebSocket connected');
                resolve();
            };
            
            this.websocket.onmessage = (event) => {
                this.handleSignalingMessage(JSON.parse(event.data));
            };
            
            this.websocket.onerror = (error) => {
                console.error('WebSocket error:', error);
                reject(new Error('WebSocket connection failed'));
            };
            
            this.websocket.onclose = () => {
                console.log('WebSocket disconnected');
                if (this.isConnected) {
                    this.handleDisconnection();
                }
            };
            
            // Timeout after 10 seconds
            setTimeout(() => {
                if (this.websocket.readyState !== WebSocket.OPEN) {
                    this.websocket.close();
                    reject(new Error('WebSocket connection timeout'));
                }
            }, 10000);
        });
    }
    
    async createPeerConnection() {
        const configuration = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' }
            ],
            iceCandidatePoolSize: 10
        };
        
        this.peerConnection = new RTCPeerConnection(configuration);
        
        // Handle ICE candidates
        this.peerConnection.onicecandidate = (event) => {
            if (event.candidate) {
                this.sendSignalingMessage({
                    type: 'ice-candidate',
                    candidate: event.candidate
                });
            }
        };
        
        // Handle connection state changes
        this.peerConnection.onconnectionstatechange = () => {
            console.log('Peer connection state:', this.peerConnection.connectionState);
            
            switch (this.peerConnection.connectionState) {
                case 'connected':
                    this.isConnected = true;
                    this.options.onConnectionStateChange('connected');
                    this.startLatencyMonitoring();
                    break;
                case 'disconnected':
                case 'failed':
                case 'closed':
                    this.handleDisconnection();
                    break;
            }
        };
        
        // Handle incoming video stream
        this.peerConnection.ontrack = (event) => {
            console.log('Received video track');
            const [stream] = event.streams;
            this.handleVideoStream(stream);
        };
        
        // Handle data channel from remote peer
        this.peerConnection.ondatachannel = (event) => {
            const channel = event.channel;
            console.log('Received data channel:', channel.label);
            
            if (channel.label === 'touch-response') {
                this.setupTouchResponseChannel(channel);
            }
        };
    }
    
    createDataChannel() {
        this.dataChannel = this.peerConnection.createDataChannel('touch-input', {
            ordered: false,
            maxRetransmits: 0
        });
        
        this.dataChannel.onopen = () => {
            console.log('Touch input data channel opened');
        };
        
        this.dataChannel.onclose = () => {
            console.log('Touch input data channel closed');
        };
        
        this.dataChannel.onerror = (error) => {
            console.error('Data channel error:', error);
        };
    }
    
    setupTouchResponseChannel(channel) {
        channel.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === 'pong') {
                    this.handlePongMessage(data);
                }
            } catch (error) {
                console.error('Error parsing touch response:', error);
            }
        };
    }
    
    async startConnection() {
        // Create offer
        const offer = await this.peerConnection.createOffer({
            offerToReceiveVideo: true,
            offerToReceiveAudio: false
        });
        
        await this.peerConnection.setLocalDescription(offer);
        
        // Send offer to remote peer
        this.sendSignalingMessage({
            type: 'offer',
            offer: offer,
            quality: this.currentQuality
        });
    }
    
    async handleSignalingMessage(message) {
        try {
            switch (message.type) {
                case 'answer':
                    await this.peerConnection.setRemoteDescription(message.answer);
                    break;
                    
                case 'ice-candidate':
                    await this.peerConnection.addIceCandidate(message.candidate);
                    break;
                    
                case 'error':
                    throw new Error(message.error);
                    
                default:
                    console.warn('Unknown signaling message type:', message.type);
            }
        } catch (error) {
            console.error('Error handling signaling message:', error);
            this.options.onError(error);
        }
    }
    
    sendSignalingMessage(message) {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            this.websocket.send(JSON.stringify(message));
        } else {
            console.error('Cannot send signaling message: WebSocket not connected');
        }
    }
    
    handleVideoStream(stream) {
        console.log('Setting up video stream');
        const videoTrack = stream.getVideoTracks()[0];
        
        if (videoTrack) {
            // Create video element for processing
            const video = document.createElement('video');
            video.srcObject = stream;
            video.autoplay = true;
            video.muted = true;
            video.playsInline = true;
            
            video.onloadedmetadata = () => {
                console.log('Video metadata loaded:', {
                    width: video.videoWidth,
                    height: video.videoHeight
                });
                
                // Start frame processing
                this.startFrameProcessing(video);
            };
        }
    }
    
    startFrameProcessing(video) {
        const canvas = document.createElement('canvas');
        const ctx = canvas.getContext('2d');
        
        const processFrame = () => {
            if (video.readyState >= 2) {
                canvas.width = video.videoWidth;
                canvas.height = video.videoHeight;
                ctx.drawImage(video, 0, 0);
                
                // Send frame to video display
                this.options.onVideoFrame({
                    canvas: canvas,
                    width: video.videoWidth,
                    height: video.videoHeight,
                    timestamp: Date.now()
                });
            }
            
            if (this.isConnected) {
                requestAnimationFrame(processFrame);
            }
        };
        
        processFrame();
    }
    
    sendTouchData(touchData) {
        if (this.dataChannel && this.dataChannel.readyState === 'open') {
            try {
                const message = JSON.stringify({
                    type: 'touch',
                    ...touchData,
                    timestamp: Date.now()
                });
                
                this.dataChannel.send(message);
            } catch (error) {
                console.error('Error sending touch data:', error);
            }
        }
    }
    
    startLatencyMonitoring() {
        this.latencyInterval = setInterval(() => {
            this.sendPing();
        }, 1000);
    }
    
    sendPing() {
        if (this.dataChannel && this.dataChannel.readyState === 'open') {
            this.lastPingTime = Date.now();
            const message = JSON.stringify({
                type: 'ping',
                timestamp: this.lastPingTime
            });
            this.dataChannel.send(message);
        }
    }
    
    handlePongMessage(data) {
        const latency = Date.now() - data.timestamp;
        this.options.onLatencyUpdate(latency);
    }
    
    updateVideoQuality(quality) {
        this.currentQuality = quality;
        
        if (this.isConnected) {
            // Send quality change request
            this.sendSignalingMessage({
                type: 'quality-change',
                quality: quality
            });
        }
    }
    
    async disconnect() {
        console.log('Disconnecting...');
        
        this.isConnected = false;
        
        // Stop latency monitoring
        if (this.latencyInterval) {
            clearInterval(this.latencyInterval);
            this.latencyInterval = null;
        }
        
        // Close data channel
        if (this.dataChannel) {
            this.dataChannel.close();
            this.dataChannel = null;
        }
        
        // Close peer connection
        if (this.peerConnection) {
            this.peerConnection.close();
            this.peerConnection = null;
        }
        
        // Close WebSocket
        if (this.websocket) {
            this.websocket.close();
            this.websocket = null;
        }
        
        this.options.onConnectionStateChange('disconnected');
    }
    
    handleDisconnection() {
        if (this.isConnected) {
            this.isConnected = false;
            this.options.onConnectionStateChange('disconnected');
            
            // Stop latency monitoring
            if (this.latencyInterval) {
                clearInterval(this.latencyInterval);
                this.latencyInterval = null;
            }
        }
    }
    
    getConnectionStats() {
        if (!this.peerConnection) return null;
        
        return this.peerConnection.getStats().then(stats => {
            const result = {};
            stats.forEach(report => {
                if (report.type === 'inbound-rtp' && report.mediaType === 'video') {
                    result.video = {
                        bytesReceived: report.bytesReceived,
                        packetsReceived: report.packetsReceived,
                        packetsLost: report.packetsLost,
                        framesDecoded: report.framesDecoded,
                        frameWidth: report.frameWidth,
                        frameHeight: report.frameHeight
                    };
                }
            });
            return result;
        });
    }
}

