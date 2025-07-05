/**
 * 🧪 Touch Handler - ALCHEMICAL EDITION
 * 🔴 Optimized for iPad Air 2 touch processing
 * 🔵 Enhanced gesture recognition for Samsung Galaxy S22 Ultra
 */

class TouchHandler {
    constructor(canvasId, options = {}) {
        this.canvas = document.getElementById(canvasId);
        this.options = {
            sensitivity: options.sensitivity || 1.0,
            gestureDelay: options.gestureDelay || 100,
            showIndicator: options.showIndicator !== false,
            hapticFeedback: options.hapticFeedback !== false,
            onTouch: options.onTouch || (() => {}),
            coordinateMapper: options.coordinateMapper
        };
        
        // 🔴 CRIMSON VARIABLES - Touch State Management
        this.isTracking = false;
        this.touchStartTime = 0;
        this.lastTouchTime = 0;
        this.touchSequence = [];
        
        // 🔵 AZURE VARIABLES - Gesture Recognition
        this.gestureThresholds = {
            tap: { maxDuration: 200, maxMovement: 10 },
            longPress: { minDuration: 800, maxMovement: 15 },
            swipe: { minDistance: 50, maxDuration: 500 },
            pinch: { minDistance: 20 }
        };
        
        // ⚗️ HERMETIC VARIABLES - iPad Air 2 Optimization
        this.touchBuffer = [];
        this.maxBufferSize = 10;
        this.processingTimeout = null;
        
        this.initializeAlchemicalTouch();
        console.log('🧪 Touch Handler initialized - Alchemical gestures ready');
    }
    
    initializeAlchemicalTouch() {
        if (!this.canvas) {
            console.error('🔴 Canvas not found - touch alchemy failed');
            return;
        }
        
        // 🧪 Prevent default touch behaviors
        this.canvas.style.touchAction = 'none';
        this.canvas.style.userSelect = 'none';
        
        // 🔴 Single touch events
        this.canvas.addEventListener('touchstart', (e) => this.handleTouchStart(e), { passive: false });
        this.canvas.addEventListener('touchmove', (e) => this.handleTouchMove(e), { passive: false });
        this.canvas.addEventListener('touchend', (e) => this.handleTouchEnd(e), { passive: false });
        this.canvas.addEventListener('touchcancel', (e) => this.handleTouchCancel(e), { passive: false });
        
        // 🔵 Mouse events for testing on desktop
        this.canvas.addEventListener('mousedown', (e) => this.handleMouseDown(e));
        this.canvas.addEventListener('mousemove', (e) => this.handleMouseMove(e));
        this.canvas.addEventListener('mouseup', (e) => this.handleMouseUp(e));
        
        // ⚗️ Prevent context menu
        this.canvas.addEventListener('contextmenu', (e) => e.preventDefault());
    }
    
    handleTouchStart(event) {
        event.preventDefault();
        
        const touches = Array.from(event.touches);
        const touch = touches[0];
        
        if (!touch) return;
        
        // 🔴 Initialize touch tracking
        this.isTracking = true;
        this.touchStartTime = Date.now();
        this.lastTouchTime = this.touchStartTime;
        
        const coords = this.getCanvasCoordinates(touch);
        
        // 🧪 Start new touch sequence
        this.touchSequence = [{
            x: coords.x,
            y: coords.y,
            timestamp: this.touchStartTime,
            type: 'start'
        }];
        
        // 🔵 Handle multi-touch for gestures
        if (touches.length > 1) {
            this.handleMultiTouch(touches, 'start');
        } else {
            this.processSingleTouch(coords, 'start');
        }
        
        console.log('🧪 Touch sequence initiated:', coords);
    }
    
    handleTouchMove(event) {
        event.preventDefault();
        
        if (!this.isTracking) return;
        
        const touches = Array.from(event.touches);
        const touch = touches[0];
        
        if (!touch) return;
        
        const coords = this.getCanvasCoordinates(touch);
        const currentTime = Date.now();
        
        // 🔴 Add to touch sequence
        this.touchSequence.push({
            x: coords.x,
            y: coords.y,
            timestamp: currentTime,
            type: 'move'
        });
        
        // ⚗️ Limit sequence length for iPad Air 2 memory
        if (this.touchSequence.length > 20) {
            this.touchSequence.shift();
        }
        
        // 🔵 Handle multi-touch gestures
        if (touches.length > 1) {
            this.handleMultiTouch(touches, 'move');
        } else {
            this.processSingleTouch(coords, 'move');
        }
        
        this.lastTouchTime = currentTime;
    }
    
    handleTouchEnd(event) {
        event.preventDefault();
        
        if (!this.isTracking) return;
        
        const endTime = Date.now();
        const duration = endTime - this.touchStartTime;
        
        // 🔴 Finalize touch sequence
        if (this.touchSequence.length > 0) {
            const lastTouch = this.touchSequence[this.touchSequence.length - 1];
            this.touchSequence.push({
                x: lastTouch.x,
                y: lastTouch.y,
                timestamp: endTime,
                type: 'end'
            });
        }
        
        // 🧪 Analyze gesture
        const gesture = this.analyzeGesture(this.touchSequence, duration);
        
        // 🔵 Send final touch event
        if (gesture) {
            this.sendTouchEvent(gesture);
        }
        
        // ⚗️ Reset tracking state
        this.isTracking = false;
        this.touchSequence = [];
        
        console.log('🧪 Touch sequence completed:', gesture?.type || 'unknown');
    }
    
    handleTouchCancel(event) {
        event.preventDefault();
        this.isTracking = false;
        this.touchSequence = [];
        console.log('⚗️ Touch sequence cancelled');
    }
    
    handleMultiTouch(touches, phase) {
        if (touches.length === 2) {
            // 🔴 Handle pinch/zoom gesture
            const touch1 = this.getCanvasCoordinates(touches[0]);
            const touch2 = this.getCanvasCoordinates(touches[1]);
            
            const distance = this.calculateDistance(touch1, touch2);
            const center = {
                x: (touch1.x + touch2.x) / 2,
                y: (touch1.y + touch2.y) / 2
            };
            
            const gesture = {
                type: 'pinch',
                action: phase,
                x: center.x,
                y: center.y,
                distance: distance,
                touches: [touch1, touch2]
            };
            
            this.sendTouchEvent(gesture);
        }
    }
    
    processSingleTouch(coords, action) {
        // 🔵 Add to processing buffer
        this.touchBuffer.push({
            x: coords.x,
            y: coords.y,
            action: action,
            timestamp: Date.now()
        });
        
        // ⚗️ Process buffer with delay for gesture recognition
        if (this.processingTimeout) {
            clearTimeout(this.processingTimeout);
        }
        
        this.processingTimeout = setTimeout(() => {
            this.processBufferedTouches();
        }, 16); // ~60fps processing rate
    }
    
    processBufferedTouches() {
        if (this.touchBuffer.length === 0) return;
        
        // 🧪 Process most recent touch from buffer
        const touch = this.touchBuffer[this.touchBuffer.length - 1];
        this.touchBuffer = []; // Clear buffer
        
        const mappedCoords = this.mapCoordinates(touch.x, touch.y);
        if (mappedCoords) {
            const touchEvent = {
                type: 'touch',
                action: touch.action,
                x: mappedCoords.x,
                y: mappedCoords.y,
                pressure: 1.0,
                timestamp: touch.timestamp
            };
            
            this.options.onTouch(touchEvent);
        }
    }
    
    analyzeGesture(sequence, duration) {
        if (sequence.length < 2) return null;
        
        const start = sequence[0];
        const end = sequence[sequence.length - 1];
        const movement = this.calculateDistance(start, end);
        
        // 🔴 Determine gesture type
        if (movement <= this.gestureThresholds.tap.maxMovement) {
            if (duration <= this.gestureThresholds.tap.maxDuration) {
                return this.createGesture('tap', end, { duration });
            } else if (duration >= this.gestureThresholds.longPress.minDuration) {
                return this.createGesture('long_press', end, { duration });
            }
        } else if (movement >= this.gestureThresholds.swipe.minDistance && 
                   duration <= this.gestureThresholds.swipe.maxDuration) {
            const direction = this.calculateSwipeDirection(start, end);
            return this.createGesture('swipe', end, { direction, distance: movement });
        }
        
        // 🔵 Default to drag gesture
        return this.createGesture('drag', end, { distance: movement, duration });
    }
    
    createGesture(type, coords, properties = {}) {
        const mappedCoords = this.mapCoordinates(coords.x, coords.y);
        if (!mappedCoords) return null;
        
        return {
            type: type,
            action: 'gesture',
            x: mappedCoords.x,
            y: mappedCoords.y,
            gesture: {
                type: type,
                ...properties
            },
            timestamp: Date.now()
        };
    }
    
    calculateSwipeDirection(start, end) {
        const deltaX = end.x - start.x;
        const deltaY = end.y - start.y;
        const angle = Math.atan2(deltaY, deltaX) * 180 / Math.PI;
        
        // 🧪 Determine primary direction
        if (angle >= -45 && angle <= 45) return 'right';
        if (angle >= 45 && angle <= 135) return 'down';
        if (angle >= 135 || angle <= -135) return 'left';
        if (angle >= -135 && angle <= -45) return 'up';
        
        return 'unknown';
    }
    
    calculateDistance(point1, point2) {
        const deltaX = point2.x - point1.x;
        const deltaY = point2.y - point1.y;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }
    
    getCanvasCoordinates(touch) {
        const rect = this.canvas.getBoundingClientRect();
        return {
            x: touch.clientX - rect.left,
            y: touch.clientY - rect.top
        };
    }
    
    mapCoordinates(x, y) {
        if (this.options.coordinateMapper) {
            return this.options.coordinateMapper.mapCoordinates(x, y);
        }
        return { x, y };
    }
    
    sendTouchEvent(touchData) {
        // 🔴 Apply haptic feedback
        if (this.options.hapticFeedback && navigator.vibrate) {
            const vibrationPattern = this.getVibrationPattern(touchData.type);
            navigator.vibrate(vibrationPattern);
        }
        
        // 🔵 Show visual indicator
        if (this.options.showIndicator) {
            this.showTouchIndicator(touchData.x, touchData.y, touchData.type);
        }
        
        // ⚗️ Send to callback
        this.options.onTouch(touchData);
    }
    
    getVibrationPattern(gestureType) {
        // 🧪 Different vibration patterns for different gestures
        switch (gestureType) {
            case 'tap': return [10];
            case 'long_press': return [20, 10, 20];
            case 'swipe': return [15];
            case 'pinch': return [5, 5, 5];
            default: return [10];
        }
    }
    
    showTouchIndicator(x, y, type) {
        // 🔴 Visual feedback implementation would go here
        // For now, just log the touch
        console.log(`🧪 Touch indicator: ${type} at (${x}, ${y})`);
    }
    
    // 🔵 Mouse event handlers for desktop testing
    handleMouseDown(event) {
        const coords = this.getCanvasCoordinates(event);
        this.handleTouchStart({ 
            touches: [{ clientX: event.clientX, clientY: event.clientY }],
            preventDefault: () => event.preventDefault()
        });
    }
    
    handleMouseMove(event) {
        if (!this.isTracking) return;
        this.handleTouchMove({
            touches: [{ clientX: event.clientX, clientY: event.clientY }],
            preventDefault: () => event.preventDefault()
        });
    }
    
    handleMouseUp(event) {
        if (!this.isTracking) return;
        this.handleTouchEnd({
            preventDefault: () => event.preventDefault()
        });
    }
    
    // 🧪 Configuration methods
    updateSensitivity(sensitivity) {
        this.options.sensitivity = sensitivity;
        if (this.options.coordinateMapper) {
            this.options.coordinateMapper.updateSensitivity(sensitivity);
        }
    }
    
    updateGestureDelay(delay) {
        this.options.gestureDelay = delay;
    }
    
    updateShowIndicator(show) {
        this.options.showIndicator = show;
    }
    
    updateHapticFeedback(enabled) {
        this.options.hapticFeedback = enabled;
    }
}
