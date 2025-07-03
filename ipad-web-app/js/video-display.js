/**
 * Video Display for Screen Mirror PWA
 * Handles video rendering and scaling for iPad display
 */

class VideoDisplay {
    constructor(canvasId, options = {}) {
        this.canvas = document.getElementById(canvasId);
        this.ctx = this.canvas.getContext('2d');
        this.options = {
            onFrameReceived: options.onFrameReceived || (() => {}),
            onError: options.onError || (() => {})
        };
        
        this.isActive = false;
        this.currentFrame = null;
        this.frameCount = 0;
        this.lastFrameTime = 0;
        this.fps = 0;
        this.fpsInterval = null;
        
        // Video scaling settings
        this.scaleMode = 'contain'; // 'contain', 'cover', 'stretch'
        this.sourceAspectRatio = 16/9; // Default Samsung Galaxy S22 Ultra aspect ratio
        this.targetAspectRatio = 4/3;  // iPad Air 2 aspect ratio
        
        this.init();
    }
    
    init() {
        this.setupCanvas();
        this.startFPSMonitoring();
        console.log('Video display initialized');
    }
    
    setupCanvas() {
        // Set canvas size to match container
        this.resize();
        
        // Set up canvas properties for smooth rendering
        this.ctx.imageSmoothingEnabled = true;
        this.ctx.imageSmoothingQuality = 'high';
        
        // Handle resize events
        window.addEventListener('resize', () => this.resize());
        window.addEventListener('orientationchange', () => {
            setTimeout(() => this.resize(), 100);
        });
    }
    
    resize() {
        const container = this.canvas.parentElement;
        const rect = container.getBoundingClientRect();
        
        // Set canvas size to match container
        this.canvas.width = rect.width;
        this.canvas.height = rect.height;
        
        // Update canvas style size
        this.canvas.style.width = rect.width + 'px';
        this.canvas.style.height = rect.height + 'px';
        
        // Recalculate scaling if we have a current frame
        if (this.currentFrame) {
            this.displayFrame(this.currentFrame);
        }
        
        console.log('Canvas resized to:', rect.width, 'x', rect.height);
    }
    
    displayFrame(frameData) {
        if (!this.isActive) return;
        
        try {
            this.currentFrame = frameData;
            this.frameCount++;
            
            // Clear canvas
            this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
            
            if (frameData.canvas) {
                this.drawScaledFrame(frameData.canvas, frameData.width, frameData.height);
            }
            
            // Update frame timing
            const now = Date.now();
            this.lastFrameTime = now;
            
            // Notify frame received
            this.options.onFrameReceived(frameData);
            
        } catch (error) {
            console.error('Error displaying frame:', error);
            this.options.onError(error);
        }
    }
    
    drawScaledFrame(sourceCanvas, sourceWidth, sourceHeight) {
        const canvasWidth = this.canvas.width;
        const canvasHeight = this.canvas.height;
        
        // Calculate source aspect ratio
        this.sourceAspectRatio = sourceWidth / sourceHeight;
        this.targetAspectRatio = canvasWidth / canvasHeight;
        
        let drawWidth, drawHeight, drawX, drawY;
        
        switch (this.scaleMode) {
            case 'contain':
                // Scale to fit within canvas while maintaining aspect ratio
                if (this.sourceAspectRatio > this.targetAspectRatio) {
                    // Source is wider, fit to width
                    drawWidth = canvasWidth;
                    drawHeight = canvasWidth / this.sourceAspectRatio;
                    drawX = 0;
                    drawY = (canvasHeight - drawHeight) / 2;
                } else {
                    // Source is taller, fit to height
                    drawWidth = canvasHeight * this.sourceAspectRatio;
                    drawHeight = canvasHeight;
                    drawX = (canvasWidth - drawWidth) / 2;
                    drawY = 0;
                }
                break;
                
            case 'cover':
                // Scale to cover entire canvas while maintaining aspect ratio
                if (this.sourceAspectRatio > this.targetAspectRatio) {
                    // Source is wider, fit to height
                    drawWidth = canvasHeight * this.sourceAspectRatio;
                    drawHeight = canvasHeight;
                    drawX = (canvasWidth - drawWidth) / 2;
                    drawY = 0;
                } else {
                    // Source is taller, fit to width
                    drawWidth = canvasWidth;
                    drawHeight = canvasWidth / this.sourceAspectRatio;
                    drawX = 0;
                    drawY = (canvasHeight - drawHeight) / 2;
                }
                break;
                
            case 'stretch':
                // Stretch to fill entire canvas
                drawWidth = canvasWidth;
                drawHeight = canvasHeight;
                drawX = 0;
                drawY = 0;
                break;
                
            default:
                drawWidth = canvasWidth;
                drawHeight = canvasHeight;
                drawX = 0;
                drawY = 0;
        }
        
        // Draw the scaled frame
        this.ctx.drawImage(
            sourceCanvas,
            0, 0, sourceWidth, sourceHeight,
            drawX, drawY, drawWidth, drawHeight
        );
        
        // Store scaling information for coordinate mapping
        this.scalingInfo = {
            drawX, drawY, drawWidth, drawHeight,
            scaleX: drawWidth / sourceWidth,
            scaleY: drawHeight / sourceHeight,
            sourceWidth, sourceHeight,
            canvasWidth, canvasHeight
        };
    }
    
    getScalingInfo() {
        return this.scalingInfo;
    }
    
    setScaleMode(mode) {
        if (['contain', 'cover', 'stretch'].includes(mode)) {
            this.scaleMode = mode;
            if (this.currentFrame) {
                this.displayFrame(this.currentFrame);
            }
        }
    }
    
    start() {
        this.isActive = true;
        console.log('Video display started');
    }
    
    pause() {
        this.isActive = false;
        console.log('Video display paused');
    }
    
    resume() {
        this.isActive = true;
        console.log('Video display resumed');
    }
    
    stop() {
        this.isActive = false;
        this.currentFrame = null;
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        console.log('Video display stopped');
    }
    
    startFPSMonitoring() {
        let lastTime = Date.now();
        let frameCounter = 0;
        
        this.fpsInterval = setInterval(() => {
            const now = Date.now();
            const deltaTime = now - lastTime;
            
            if (deltaTime >= 1000) {
                this.fps = Math.round((frameCounter * 1000) / deltaTime);
                frameCounter = 0;
                lastTime = now;
                
                // Update FPS display if element exists
                const fpsElement = document.getElementById('fpsValue');
                if (fpsElement) {
                    fpsElement.textContent = this.fps;
                }
            }
            
            frameCounter = this.frameCount;
        }, 100);
    }
    
    stopFPSMonitoring() {
        if (this.fpsInterval) {
            clearInterval(this.fpsInterval);
            this.fpsInterval = null;
        }
    }
    
    getFPS() {
        return this.fps;
    }
    
    getFrameCount() {
        return this.frameCount;
    }
    
    getLastFrameTime() {
        return this.lastFrameTime;
    }
    
    // Screenshot functionality
    takeScreenshot() {
        if (!this.currentFrame) {
            throw new Error('No frame available for screenshot');
        }
        
        // Create a new canvas with the current frame
        const screenshotCanvas = document.createElement('canvas');
        const screenshotCtx = screenshotCanvas.getContext('2d');
        
        screenshotCanvas.width = this.canvas.width;
        screenshotCanvas.height = this.canvas.height;
        
        // Copy current canvas content
        screenshotCtx.drawImage(this.canvas, 0, 0);
        
        // Convert to blob
        return new Promise((resolve) => {
            screenshotCanvas.toBlob((blob) => {
                resolve(blob);
            }, 'image/png');
        });
    }
    
    // Performance monitoring
    getPerformanceStats() {
        return {
            fps: this.fps,
            frameCount: this.frameCount,
            lastFrameTime: this.lastFrameTime,
            isActive: this.isActive,
            canvasSize: {
                width: this.canvas.width,
                height: this.canvas.height
            },
            scalingInfo: this.scalingInfo
        };
    }
    
    // Debug methods
    drawDebugInfo() {
        if (!this.isActive) return;
        
        const ctx = this.ctx;
        const info = this.getPerformanceStats();
        
        // Save current context
        ctx.save();
        
        // Set debug text style
        ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
        ctx.fillRect(10, 10, 200, 100);
        
        ctx.fillStyle = 'white';
        ctx.font = '12px monospace';
        ctx.textAlign = 'left';
        
        // Draw debug information
        ctx.fillText(`FPS: ${info.fps}`, 15, 25);
        ctx.fillText(`Frames: ${info.frameCount}`, 15, 40);
        ctx.fillText(`Canvas: ${info.canvasSize.width}x${info.canvasSize.height}`, 15, 55);
        
        if (info.scalingInfo) {
            ctx.fillText(`Scale: ${info.scalingInfo.scaleX.toFixed(2)}x${info.scalingInfo.scaleY.toFixed(2)}`, 15, 70);
            ctx.fillText(`Source: ${info.scalingInfo.sourceWidth}x${info.scalingInfo.sourceHeight}`, 15, 85);
        }
        
        // Restore context
        ctx.restore();
    }
    
    enableDebugMode() {
        this.debugMode = true;
        this.debugInterval = setInterval(() => {
            if (this.currentFrame) {
                this.drawDebugInfo();
            }
        }, 100);
    }
    
    disableDebugMode() {
        this.debugMode = false;
        if (this.debugInterval) {
            clearInterval(this.debugInterval);
            this.debugInterval = null;
        }
    }
    
    cleanup() {
        this.stop();
        this.stopFPSMonitoring();
        this.disableDebugMode();
        
        // Remove event listeners
        window.removeEventListener('resize', this.resize);
        window.removeEventListener('orientationchange', this.resize);
    }
}

