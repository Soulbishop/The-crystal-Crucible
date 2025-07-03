/**
 * Touch Handler for Screen Mirror PWA
 * Manages touch events on iPad and converts them for Samsung device
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
        
        this.isEnabled = false;
        this.activeTouches = new Map();
        this.gestureState = {
            type: 'none',
            startTime: 0,
            startDistance: 0,
            lastDistance: 0,
            center: { x: 0, y: 0 }
        };
        
        this.longPressTimer = null;
        this.longPressThreshold = 500; // ms
        this.tapThreshold = 10; // pixels
        this.doubleTapThreshold = 300; // ms
        this.lastTapTime = 0;
        this.lastTapPosition = { x: 0, y: 0 };
        
        this.init();
    }
    
    init() {
        this.setupEventListeners();
        this.enable();
        console.log('Touch handler initialized');
    }
    
    setupEventListeners() {
        // Touch events
        this.canvas.addEventListener('touchstart', (e) => this.handleTouchStart(e), { passive: false });
        this.canvas.addEventListener('touchmove', (e) => this.handleTouchMove(e), { passive: false });
        this.canvas.addEventListener('touchend', (e) => this.handleTouchEnd(e), { passive: false });
        this.canvas.addEventListener('touchcancel', (e) => this.handleTouchCancel(e), { passive: false });
        
        // Mouse events for desktop testing
        this.canvas.addEventListener('mousedown', (e) => this.handleMouseDown(e));
        this.canvas.addEventListener('mousemove', (e) => this.handleMouseMove(e));
        this.canvas.addEventListener('mouseup', (e) => this.handleMouseUp(e));
        this.canvas.addEventListener('mouseleave', (e) => this.handleMouseLeave(e));
        
        // Prevent context menu
        this.canvas.addEventListener('contextmenu', (e) => e.preventDefault());
        
        // Prevent default touch behaviors
        this.canvas.addEventListener('touchstart', (e) => e.preventDefault(), { passive: false });
        this.canvas.addEventListener('touchmove', (e) => e.preventDefault(), { passive: false });
    }
    
    enable() {
        this.isEnabled = true;
        this.canvas.style.pointerEvents = 'auto';
    }
    
    disable() {
        this.isEnabled = false;
        this.canvas.style.pointerEvents = 'none';
        this.clearAllTouches();
    }
    
    handleTouchStart(event) {
        if (!this.isEnabled) return;
        
        const touches = Array.from(event.changedTouches);
        
        touches.forEach(touch => {
            const coords = this.getTouchCoordinates(touch);
            const touchData = {
                id: touch.identifier,
                x: coords.x,
                y: coords.y,
                pressure: touch.force || 1.0,
                timestamp: Date.now()
            };
            
            this.activeTouches.set(touch.identifier, touchData);
        });
        
        this.updateGestureState();
        this.handleGestureStart();
    }
    
    handleTouchMove(event) {
        if (!this.isEnabled) return;
        
        const touches = Array.from(event.changedTouches);
        
        touches.forEach(touch => {
            if (this.activeTouches.has(touch.identifier)) {
                const coords = this.getTouchCoordinates(touch);
                const touchData = this.activeTouches.get(touch.identifier);
                
                touchData.x = coords.x;
                touchData.y = coords.y;
                touchData.pressure = touch.force || 1.0;
                touchData.timestamp = Date.now();
            }
        });
        
        this.updateGestureState();
        this.handleGestureMove();
    }
    
    handleTouchEnd(event) {
        if (!this.isEnabled) return;
        
        const touches = Array.from(event.changedTouches);
        
        touches.forEach(touch => {
            this.activeTouches.delete(touch.identifier);
        });
        
        this.updateGestureState();
        this.handleGestureEnd();
    }
    
    handleTouchCancel(event) {
        this.handleTouchEnd(event);
    }
    
    // Mouse events for desktop testing
    handleMouseDown(event) {
        if (!this.isEnabled) return;
        
        const coords = this.getMouseCoordinates(event);
        const touchData = {
            id: 'mouse',
            x: coords.x,
            y: coords.y,
            pressure: 1.0,
            timestamp: Date.now()
        };
        
        this.activeTouches.set('mouse', touchData);
        this.updateGestureState();
        this.handleGestureStart();
    }
    
    handleMouseMove(event) {
        if (!this.isEnabled || !this.activeTouches.has('mouse')) return;
        
        const coords = this.getMouseCoordinates(event);
        const touchData = this.activeTouches.get('mouse');
        
        touchData.x = coords.x;
        touchData.y = coords.y;
        touchData.timestamp = Date.now();
        
        this.updateGestureState();
        this.handleGestureMove();
    }
    
    handleMouseUp(event) {
        if (!this.isEnabled) return;
        
        this.activeTouches.delete('mouse');
        this.updateGestureState();
        this.handleGestureEnd();
    }
    
    handleMouseLeave(event) {
        this.handleMouseUp(event);
    }
    
    getTouchCoordinates(touch) {
        const rect = this.canvas.getBoundingClientRect();
        const x = (touch.clientX - rect.left) * this.options.sensitivity;
        const y = (touch.clientY - rect.top) * this.options.sensitivity;
        
        return this.options.coordinateMapper ? 
            this.options.coordinateMapper.mapCoordinates(x, y) : 
            { x, y };
    }
    
    getMouseCoordinates(event) {
        const rect = this.canvas.getBoundingClientRect();
        const x = (event.clientX - rect.left) * this.options.sensitivity;
        const y = (event.clientY - rect.top) * this.options.sensitivity;
        
        return this.options.coordinateMapper ? 
            this.options.coordinateMapper.mapCoordinates(x, y) : 
            { x, y };
    }
    
    updateGestureState() {
        const touchCount = this.activeTouches.size;
        const now = Date.now();
        
        if (touchCount === 0) {
            this.gestureState.type = 'none';
        } else if (touchCount === 1) {
            const touch = Array.from(this.activeTouches.values())[0];
            
            if (this.gestureState.type === 'none') {
                this.gestureState.type = 'tap';
                this.gestureState.startTime = now;
                this.gestureState.center = { x: touch.x, y: touch.y };
            } else if (this.gestureState.type === 'tap') {
                const distance = this.calculateDistance(
                    this.gestureState.center,
                    { x: touch.x, y: touch.y }
                );
                
                if (distance > this.tapThreshold) {
                    this.gestureState.type = 'drag';
                } else if (now - this.gestureState.startTime > this.longPressThreshold) {
                    this.gestureState.type = 'long-press';
                }
            }
        } else if (touchCount === 2) {
            const touches = Array.from(this.activeTouches.values());
            const distance = this.calculateDistance(touches[0], touches[1]);
            const center = this.calculateCenter(touches[0], touches[1]);
            
            if (this.gestureState.type !== 'pinch') {
                this.gestureState.type = 'pinch';
                this.gestureState.startTime = now;
                this.gestureState.startDistance = distance;
                this.gestureState.center = center;
            }
            
            this.gestureState.lastDistance = distance;
            this.gestureState.center = center;
        } else {
            this.gestureState.type = 'multi-touch';
        }
    }
    
    handleGestureStart() {
        const touchCount = this.activeTouches.size;
        
        if (touchCount === 1) {
            const touch = Array.from(this.activeTouches.values())[0];
            
            // Start long press timer
            this.longPressTimer = setTimeout(() => {
                if (this.gestureState.type === 'tap') {
                    this.sendTouchEvent('long-press', touch.x, touch.y);
                    this.provideFeedback();
                }
            }, this.longPressThreshold);
            
            // Send touch down event
            this.sendTouchEvent('down', touch.x, touch.y);
        } else if (touchCount === 2) {
            // Clear long press timer for multi-touch
            this.clearLongPressTimer();
            
            const touches = Array.from(this.activeTouches.values());
            this.sendPinchEvent('start', touches[0], touches[1]);
        }
    }
    
    handleGestureMove() {
        const touchCount = this.activeTouches.size;
        
        if (touchCount === 1) {
            const touch = Array.from(this.activeTouches.values())[0];
            
            if (this.gestureState.type === 'drag') {
                this.sendTouchEvent('move', touch.x, touch.y);
            }
        } else if (touchCount === 2 && this.gestureState.type === 'pinch') {
            const touches = Array.from(this.activeTouches.values());
            this.sendPinchEvent('move', touches[0], touches[1]);
        }
    }
    
    handleGestureEnd() {
        const touchCount = this.activeTouches.size;
        
        this.clearLongPressTimer();
        
        if (touchCount === 0) {
            if (this.gestureState.type === 'tap') {
                const touch = this.gestureState.center;
                const now = Date.now();
                
                // Check for double tap
                if (now - this.lastTapTime < this.doubleTapThreshold &&
                    this.calculateDistance(touch, this.lastTapPosition) < this.tapThreshold) {
                    this.sendTouchEvent('double-tap', touch.x, touch.y);
                } else {
                    this.sendTouchEvent('tap', touch.x, touch.y);
                }
                
                this.lastTapTime = now;
                this.lastTapPosition = { x: touch.x, y: touch.y };
                this.provideFeedback();
                
            } else if (this.gestureState.type === 'drag') {
                const touch = this.gestureState.center;
                this.sendTouchEvent('up', touch.x, touch.y);
                
            } else if (this.gestureState.type === 'pinch') {
                const touches = Array.from(this.activeTouches.values());
                if (touches.length >= 2) {
                    this.sendPinchEvent('end', touches[0], touches[1]);
                }
            }
        }
    }
    
    sendTouchEvent(type, x, y, pressure = 1.0) {
        const touchData = {
            type: type,
            x: Math.round(x),
            y: Math.round(y),
            pressure: pressure,
            timestamp: Date.now()
        };
        
        this.options.onTouch(touchData);
        
        // Show visual feedback
        if (this.options.showIndicator && (type === 'tap' || type === 'down')) {
            this.showTouchIndicator(x, y);
        }
    }
    
    sendPinchEvent(phase, touch1, touch2) {
        const center = this.calculateCenter(touch1, touch2);
        const distance = this.calculateDistance(touch1, touch2);
        const scale = this.gestureState.startDistance > 0 ? 
            distance / this.gestureState.startDistance : 1.0;
        
        const pinchData = {
            type: 'pinch',
            phase: phase,
            x: Math.round(center.x),
            y: Math.round(center.y),
            scale: scale,
            distance: distance,
            timestamp: Date.now()
        };
        
        this.options.onTouch(pinchData);
    }
    
    calculateDistance(point1, point2) {
        const dx = point1.x - point2.x;
        const dy = point1.y - point2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
    
    calculateCenter(point1, point2) {
        return {
            x: (point1.x + point2.x) / 2,
            y: (point1.y + point2.y) / 2
        };
    }
    
    clearLongPressTimer() {
        if (this.longPressTimer) {
            clearTimeout(this.longPressTimer);
            this.longPressTimer = null;
        }
    }
    
    clearAllTouches() {
        this.activeTouches.clear();
        this.gestureState.type = 'none';
        this.clearLongPressTimer();
    }
    
    showTouchIndicator(x, y) {
        // This will be handled by the main app
        // Just trigger the visual feedback
        const event = new CustomEvent('touchIndicator', {
            detail: { x, y }
        });
        this.canvas.dispatchEvent(event);
    }
    
    provideFeedback() {
        if (this.options.hapticFeedback && navigator.vibrate) {
            navigator.vibrate(10);
        }
    }
    
    // Settings update methods
    updateSensitivity(sensitivity) {
        this.options.sensitivity = sensitivity;
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
    
    // Debug methods
    getActiveTouches() {
        return Array.from(this.activeTouches.values());
    }
    
    getGestureState() {
        return { ...this.gestureState };
    }
}

