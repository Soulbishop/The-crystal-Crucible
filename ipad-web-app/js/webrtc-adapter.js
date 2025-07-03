/**
 * WebRTC Adapter for Screen Mirror PWA
 * Provides cross-browser compatibility for WebRTC APIs
 */

(function() {
    'use strict';
    
    // Check if WebRTC is supported
    if (typeof window === 'undefined' || !window.navigator) {
        console.error('WebRTC Adapter: Window or navigator not available');
        return;
    }
    
    const navigator = window.navigator;
    
    // Detect browser
    const isChrome = navigator.userAgent.indexOf('Chrome') !== -1;
    const isFirefox = navigator.userAgent.indexOf('Firefox') !== -1;
    const isSafari = navigator.userAgent.indexOf('Safari') !== -1 && !isChrome;
    const isEdge = navigator.userAgent.indexOf('Edge') !== -1;
    
    console.log('WebRTC Adapter: Browser detection:', {
        chrome: isChrome,
        firefox: isFirefox,
        safari: isSafari,
        edge: isEdge
    });
    
    // Polyfill RTCPeerConnection
    if (!window.RTCPeerConnection) {
        window.RTCPeerConnection = window.webkitRTCPeerConnection || 
                                   window.mozRTCPeerConnection || 
                                   window.msRTCPeerConnection;
    }
    
    // Polyfill RTCSessionDescription
    if (!window.RTCSessionDescription) {
        window.RTCSessionDescription = window.webkitRTCSessionDescription || 
                                       window.mozRTCSessionDescription || 
                                       window.msRTCSessionDescription;
    }
    
    // Polyfill RTCIceCandidate
    if (!window.RTCIceCandidate) {
        window.RTCIceCandidate = window.webkitRTCIceCandidate || 
                                 window.mozRTCIceCandidate || 
                                 window.msRTCIceCandidate;
    }
    
    // Polyfill getUserMedia
    if (!navigator.mediaDevices) {
        navigator.mediaDevices = {};
    }
    
    if (!navigator.mediaDevices.getUserMedia) {
        navigator.mediaDevices.getUserMedia = function(constraints) {
            const getUserMedia = navigator.webkitGetUserMedia || 
                               navigator.mozGetUserMedia || 
                               navigator.msGetUserMedia;
            
            if (!getUserMedia) {
                return Promise.reject(new Error('getUserMedia is not implemented'));
            }
            
            return new Promise((resolve, reject) => {
                getUserMedia.call(navigator, constraints, resolve, reject);
            });
        };
    }
    
    // Polyfill getDisplayMedia for screen capture
    if (!navigator.mediaDevices.getDisplayMedia) {
        navigator.mediaDevices.getDisplayMedia = function(constraints) {
            // Fallback for browsers that don't support getDisplayMedia
            console.warn('getDisplayMedia not supported, falling back to getUserMedia');
            return navigator.mediaDevices.getUserMedia({
                video: { mediaSource: 'screen' },
                ...constraints
            });
        };
    }
    
    // Enhanced RTCPeerConnection with additional methods
    if (window.RTCPeerConnection) {
        const OriginalRTCPeerConnection = window.RTCPeerConnection;
        
        window.RTCPeerConnection = function(configuration, constraints) {
            const pc = new OriginalRTCPeerConnection(configuration, constraints);
            
            // Add legacy callback support for createOffer/createAnswer
            const originalCreateOffer = pc.createOffer;
            const originalCreateAnswer = pc.createAnswer;
            
            pc.createOffer = function(options, successCallback, failureCallback) {
                if (typeof options === 'function') {
                    // Legacy callback style
                    failureCallback = successCallback;
                    successCallback = options;
                    options = {};
                }
                
                const promise = originalCreateOffer.call(this, options);
                
                if (successCallback) {
                    promise.then(successCallback, failureCallback);
                }
                
                return promise;
            };
            
            pc.createAnswer = function(options, successCallback, failureCallback) {
                if (typeof options === 'function') {
                    // Legacy callback style
                    failureCallback = successCallback;
                    successCallback = options;
                    options = {};
                }
                
                const promise = originalCreateAnswer.call(this, options);
                
                if (successCallback) {
                    promise.then(successCallback, failureCallback);
                }
                
                return promise;
            };
            
            // Add legacy callback support for setLocalDescription/setRemoteDescription
            const originalSetLocalDescription = pc.setLocalDescription;
            const originalSetRemoteDescription = pc.setRemoteDescription;
            
            pc.setLocalDescription = function(description, successCallback, failureCallback) {
                const promise = originalSetLocalDescription.call(this, description);
                
                if (successCallback) {
                    promise.then(successCallback, failureCallback);
                }
                
                return promise;
            };
            
            pc.setRemoteDescription = function(description, successCallback, failureCallback) {
                const promise = originalSetRemoteDescription.call(this, description);
                
                if (successCallback) {
                    promise.then(successCallback, failureCallback);
                }
                
                return promise;
            };
            
            // Add legacy callback support for addIceCandidate
            const originalAddIceCandidate = pc.addIceCandidate;
            
            pc.addIceCandidate = function(candidate, successCallback, failureCallback) {
                const promise = originalAddIceCandidate.call(this, candidate);
                
                if (successCallback) {
                    promise.then(successCallback, failureCallback);
                }
                
                return promise;
            };
            
            return pc;
        };
        
        // Copy static methods
        Object.setPrototypeOf(window.RTCPeerConnection, OriginalRTCPeerConnection);
        window.RTCPeerConnection.prototype = OriginalRTCPeerConnection.prototype;
    }
    
    // Browser-specific fixes
    if (isSafari) {
        // Safari-specific WebRTC fixes
        console.log('WebRTC Adapter: Applying Safari-specific fixes');
        
        // Safari requires different STUN server configuration
        window.webrtcAdapterConfig = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' },
                { urls: 'stun:stun.services.mozilla.com' }
            ]
        };
    } else if (isFirefox) {
        // Firefox-specific WebRTC fixes
        console.log('WebRTC Adapter: Applying Firefox-specific fixes');
        
        window.webrtcAdapterConfig = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' }
            ]
        };
    } else if (isChrome) {
        // Chrome-specific WebRTC fixes
        console.log('WebRTC Adapter: Applying Chrome-specific fixes');
        
        window.webrtcAdapterConfig = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' }
            ]
        };
    }
    
    // Add utility functions
    window.webrtcAdapter = {
        browserDetails: {
            browser: isChrome ? 'chrome' : isFirefox ? 'firefox' : isSafari ? 'safari' : isEdge ? 'edge' : 'unknown',
            version: navigator.userAgent
        },
        
        // Check WebRTC support
        isWebRTCSupported: function() {
            return !!(window.RTCPeerConnection && 
                     window.RTCSessionDescription && 
                     window.RTCIceCandidate);
        },
        
        // Check specific feature support
        isGetUserMediaSupported: function() {
            return !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
        },
        
        isGetDisplayMediaSupported: function() {
            return !!(navigator.mediaDevices && navigator.mediaDevices.getDisplayMedia);
        },
        
        // Get recommended configuration for current browser
        getRecommendedConfig: function() {
            return window.webrtcAdapterConfig || {
                iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
            };
        },
        
        // Log browser capabilities
        logCapabilities: function() {
            console.log('WebRTC Capabilities:', {
                webrtc: this.isWebRTCSupported(),
                getUserMedia: this.isGetUserMediaSupported(),
                getDisplayMedia: this.isGetDisplayMediaSupported(),
                browser: this.browserDetails.browser
            });
        }
    };
    
    // Check for required APIs and warn if missing
    if (!window.webrtcAdapter.isWebRTCSupported()) {
        console.error('WebRTC Adapter: WebRTC is not supported in this browser');
        
        // Show user-friendly error
        window.addEventListener('load', function() {
            const errorDiv = document.createElement('div');
            errorDiv.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                background: #e74c3c;
                color: white;
                padding: 10px;
                text-align: center;
                z-index: 10000;
                font-family: Arial, sans-serif;
            `;
            errorDiv.innerHTML = `
                <strong>WebRTC Not Supported</strong><br>
                Your browser does not support WebRTC. Please use a modern browser like Chrome, Firefox, or Safari.
            `;
            document.body.insertBefore(errorDiv, document.body.firstChild);
        });
    } else {
        console.log('WebRTC Adapter: WebRTC is supported');
        window.webrtcAdapter.logCapabilities();
    }
    
    // Add event listener for unhandled promise rejections
    window.addEventListener('unhandledrejection', function(event) {
        if (event.reason && event.reason.name === 'NotAllowedError') {
            console.warn('WebRTC permission denied:', event.reason);
        }
    });
    
})();

