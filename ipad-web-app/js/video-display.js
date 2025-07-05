/**
 * 🧪 Video Display - ALCHEMICAL EDITION
 * 🔴 Optimized for iPad Air 2 memory constraints
 * 🔵 Enhanced for Samsung Galaxy S22 Ultra video streaming
 */

class VideoDisplay {
    constructor(canvasId, options = {}) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas ? this.canvas.getContext('2d') : null;
        
        this.options = {
            onFrameReceived: options.onFrameReceived || (() => {}),
            onError: options.onError || (() => {})
        };
        
        // 🔴 CRIMSON VARIABLES - Display State
        this.isActive = false;
        this.isPaused = false;
        this.frameCount = 0;
        this.lastFrameTime = 0;
        
        // 🔵 AZURE VARIABLES - Performance Monitoring
        this.fps = 0;
        this.frameInterval = null;
        this.performanceStats = {
            framesProcessed: 0,
            framesDropped: 0,
            averageProcessingTime: 0
        };
        
        // ⚗️ HERMETIC VARIABLES - iPad Air 2 Memory Management
        this.frameBuffer = null;
        this.maxFrameSize = 1920 * 1080; // Limit for iPad Air 2
        this.compressionLevel = 0.8;
        
        this.initializeAlchemicalDisplay();
        console.log('🧪 Video Display initialized - Alchemical rendering ready');
    }
    
    initializeAlchemicalDisplay() {
        if (!this.canvas || !this.ctx) {
            console.error('🔴 Canvas initialization failed');
            return;
        }
        
        // 🧪 Set canvas properties for optimal performance
        this.canvas.style.imageRendering = 'pixelated';
        this.canvas.style.imageRendering = '-moz-crisp-edges';
        this.canvas.style.imageRendering = 'crisp-edges';
        
        // 🔵 Initialize frame buffer
        this.frameBuffer = document.createElement('canvas');
        this.frameBufferCtx = this.frameBuffer.getContext('2d');
        
        // ⚗️ Setup resize observer for responsive display
        if (window.ResizeObserver) {
            const resizeObserver = new ResizeObserver(() => {
                this.handleResize();
            });
            resizeObserver.observe(this.canvas.parentElement);
        }
        
        console.log('🔴 Alchemical display matrix initialized');
    }
    
    displayFrame(frameData) {
        if (!this.isActive || this.isPaused) return;
        
        const startTime = performance.now();
        
        try {
            // 🧪 Process frame based on type
            if (frameData.canvas) {
                this.displayCanvasFrame(frameData);
            } else if (frameData.imageData) {
                this.displayImageDataFrame(frameData);
            } else if (frameData.blob) {
                this.displayBlobFrame(frameData);
            } else {
                console.warn('⚗️ Unknown frame data type');
                return;
            }
            
            // 🔴 Update performance statistics
            this.updatePerformanceStats(startTime);
            this.frameCount++;
            this.lastFrameTime = Date.now();
            
            // 🔵 Notify callback
            this.options.onFrameReceived(frameData);
            
        } catch (error) {
            console.error('🔴 Frame display error:', error);
            this.performanceStats.framesDropped++;
            this.options.onError(error);
        }
    }
    
    displayCanvasFrame(frameData) {
        const { canvas, width, height } = frameData;
        
        // 🧪 Resize display canvas if needed
        if (this.canvas.width !== width || this.canvas.height !== height) {
            this.resizeCanvas(width, height);
        }
        
        // 🔴 Draw frame to display canvas
        this.ctx.drawImage(canvas, 0, 0, width, height, 0, 0, this.canvas.width, this.canvas.height);
    }
    
    displayImageDataFrame(frameData) {
        const { imageData, width, height } = frameData;
        
        // 🔵 Create temporary canvas for ImageData
        if (!this.frameBuffer || this.frameBuffer.width !== width || this.frameBuffer.height !== height) {
            this.frameBuffer.width = width;
            this.frameBuffer.height = height;
        }
        
        // ⚗️ Put image data and draw to display
        this.frameBufferCtx.putImageData(imageData, 0, 0);
        this.ctx.drawImage(this.frameBuffer, 0, 0, this.canvas.width, this.canvas.height);
    }
    
    async displayBlobFrame(frameData) {
        const { blob } = frameData;
        
        // 🧪 Create image from blob
        const img = new Image();
        
        return new Promise((resolve, reject) => {
            img.onload = () => {
                try {
                    // 🔴 Draw image to canvas
                    this.ctx.drawImage(img, 0, 0, this.canvas.width, this.canvas.height);
                    URL.revokeObjectURL(img.src); // Clean up memory
                    resolve();
                } catch (error) {
                    reject(error);
                }
            };
            
            img.onerror = () => {
                URL.revokeObjectURL(img.src);
                reject(new Error('Failed to load image from blob'));
            };
            
            img.src = URL.createObjectURL(blob);
        });
    }
    
    resizeCanvas(width, height) {
        // 🔵 Calculate optimal display size for iPad Air 2
        const maxWidth = Math.min(width, this.maxFrameSize / height);
        const maxHeight = Math.min(height, this.maxFrameSize / width);
        
        // ⚗️ Maintain aspect ratio
        const aspectRatio = width / height;
        const containerWidth = this.canvas.parentElement.clientWidth;
        const containerHeight = this.canvas.parentElement.clientHeight;
        
        let displayWidth, displayHeight;
        
        if (containerWidth / containerHeight > aspectRatio) {
            displayHeight = Math.min(containerHeight, maxHeight);
            displayWidth = displayHeight * aspectRatio;
        } else {
            displayWidth = Math.min(containerWidth, maxWidth);
            displayHeight = displayWidth / aspectRatio;
        }
        
        // 🧪 Apply new dimensions
        this.canvas.width = Math.floor(displayWidth);
        this.canvas.height = Math.floor(displayHeight);
        
        console.log(`🔴 Canvas resized: ${this.canvas.width}x${this.canvas.height}`);
    }
    
    handleResize() {
        // 🔵 Recalculate canvas size on container resize
        if (this.lastFrameTime > 0) {
            // Use last known frame dimensions
            this.resizeCanvas(this.canvas.width, this.canvas.height);
        }
    }
    
    updatePerformanceStats(startTime) {
        const processingTime = performance.now() - startTime;
        
        // 🧪 Update rolling average
        this.performanceStats.framesProcessed++;
        const alpha = 0.1; // Smoothing factor
        this.performanceStats.averageProcessingTime = 
            (this.performanceStats.averageProcessingTime * (1 - alpha)) + 
            (processingTime * alpha);
        
        // 🔴 Calculate FPS every second
        const now = Date.now();
        if (!this.lastFpsUpdate) {
            this.lastFpsUpdate = now;
            this.framesSinceLastUpdate = 0;
        }
        
        this.framesSinceLastUpdate++;
        
        if (now - this.lastFpsUpdate >= 1000) {
            this.fps = this.framesSinceLastUpdate;
            this.framesSinceLastUpdate = 0;
            this.lastFpsUpdate = now;
            
            console.log(`🔵 Performance: ${this.fps} FPS, ${this.performanceStats.averageProcessingTime.toFixed(2)}ms avg processing`);
        }
    }
    
    start() {
        this.isActive = true;
        this.isPaused = false;
        console.log('🧪 Alchemical video display activated');
    }
    
    pause() {
        this.isPaused = true;
        console.log('⚗️ Video display paused');
    }
    
    resume() {
        this.isPaused = false;
        console.log('🔴 Video display resumed');
    }
    
    stop() {
        this.isActive = false;
        this.isPaused = false;
        
        // 🔵 Clear canvas
        if (this.ctx) {
            this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        }
        
        console.log('🧪 Alchemical video display deactivated');
    }
    
    resize() {
        this.handleResize();
    }
    
    getPerformanceStats() {
        return {
            ...this.performanceStats,
            fps: this.fps,
            isActive: this.isActive,
            isPaused: this.isPaused,
            frameCount: this.frameCount
        };
    }
    
    // 🧪 Memory cleanup for iPad Air 2
    cleanup() {
        this.stop();
        
        if (this.frameBuffer) {
            this.frameBuffer.width = 1;
            this.frameBuffer.height = 1;
        }
        
        this.performanceStats = {
            framesProcessed: 0,
            framesDropped: 0,
            averageProcessingTime: 0
        };
        
        console.log('⚗️ Video display memory cleaned');
    }
}
