/**
 * 🧪 Coordinate Mapper - ALCHEMICAL EDITION
 * 🔴 Maps iPad Air 2 touch coordinates to Samsung Galaxy S22 Ultra
 * 🔵 Handles resolution differences and aspect ratio corrections
 */

class CoordinateMapper {
    constructor(options = {}) {
        // 🔴 CRIMSON DEFAULTS - iPad Air 2 specifications
        this.sourceWidth = options.sourceWidth || 2048;
        this.sourceHeight = options.sourceHeight || 1536;
        
        // 🔵 AZURE DEFAULTS - Samsung Galaxy S22 Ultra specifications  
        this.targetWidth = options.targetWidth || 3088;
        this.targetHeight = options.targetHeight || 1440;
        
        // ⚗️ HERMETIC VARIABLES - Transformation matrix
        this.scaleX = 1;
        this.scaleY = 1;
        this.offsetX = 0;
        this.offsetY = 0;
        
        // 🧪 ALCHEMICAL SETTINGS
        this.aspectRatioCorrection = true;
        this.touchSensitivity = 1.0;
        this.deadZoneThreshold = 5; // pixels
        
        this.calculateTransformation();
        this.setupEventListeners();
        
        console.log('🧪 Coordinate Mapper initialized - Alchemical transformation ready');
    }
    
    setupEventListeners() {
        // 🔴 Listen for Samsung resolution updates
        window.addEventListener('samsungResolutionUpdate', (event) => {
            this.updateTargetResolution(event.detail.width, event.detail.height);
        });
        
        // 🔵 Listen for orientation changes
        window.addEventListener('orientationchange', () => {
            setTimeout(() => this.handleOrientationChange(), 100);
        });
    }
    
    updateTargetResolution(width, height) {
        console.log(`🧪 Updating Samsung resolution: ${width}x${height}`);
        this.targetWidth = width;
        this.targetHeight = height;
        this.calculateTransformation();
    }
    
    calculateTransformation() {
        // 🔴 Calculate scaling factors
        this.scaleX = this.targetWidth / this.sourceWidth;
        this.scaleY = this.targetHeight / this.sourceHeight;
        
        if (this.aspectRatioCorrection) {
            // 🔵 Maintain aspect ratio - use uniform scaling
            const uniformScale = Math.min(this.scaleX, this.scaleY);
            this.scaleX = uniformScale;
            this.scaleY = uniformScale;
            
            // ⚗️ Calculate centering offsets
            const scaledWidth = this.sourceWidth * this.scaleX;
            const scaledHeight = this.sourceHeight * this.scaleY;
            
            this.offsetX = (this.targetWidth - scaledWidth) / 2;
            this.offsetY = (this.targetHeight - scaledHeight) / 2;
        } else {
            this.offsetX = 0;
            this.offsetY = 0;
        }
        
        console.log('🧪 Transformation matrix updated:', {
            scaleX: this.scaleX.toFixed(3),
            scaleY: this.scaleY.toFixed(3),
            offsetX: this.offsetX.toFixed(1),
            offsetY: this.offsetY.toFixed(1)
        });
    }
    
    mapCoordinates(x, y) {
        // 🔴 Apply dead zone filtering
        if (this.isInDeadZone(x, y)) {
            return null;
        }
        
        // 🔵 Apply transformation matrix
        let mappedX = (x * this.scaleX) + this.offsetX;
        let mappedY = (y * this.scaleY) + this.offsetY;
        
        // ⚗️ Apply touch sensitivity
        if (this.touchSensitivity !== 1.0) {
            const centerX = this.targetWidth / 2;
            const centerY = this.targetHeight / 2;
            
            mappedX = centerX + ((mappedX - centerX) * this.touchSensitivity);
            mappedY = centerY + ((mappedY - centerY) * this.touchSensitivity);
        }
        
        // 🧪 Clamp to target bounds
        mappedX = Math.max(0, Math.min(this.targetWidth - 1, mappedX));
        mappedY = Math.max(0, Math.min(this.targetHeight - 1, mappedY));
        
        return {
            x: Math.round(mappedX),
            y: Math.round(mappedY),
            originalX: x,
            originalY: y
        };
    }
    
    isInDeadZone(x, y) {
        // 🔴 Check if coordinates are in dead zone (too close to edges)
        return (
            x < this.deadZoneThreshold ||
            y < this.deadZoneThreshold ||
            x > (this.sourceWidth - this.deadZoneThreshold) ||
            y > (this.sourceHeight - this.deadZoneThreshold)
        );
    }
    
    handleOrientationChange() {
        // 🔵 Recalculate for new iPad orientation
        this.sourceWidth = window.screen.width;
        this.sourceHeight = window.screen.height;
        this.calculateTransformation();
        
        console.log('🧪 Orientation change - transformation recalculated');
    }
    
    updateSensitivity(sensitivity) {
        this.touchSensitivity = Math.max(0.1, Math.min(3.0, sensitivity));
        console.log('⚗️ Touch sensitivity updated:', this.touchSensitivity);
    }
    
    getTransformationInfo() {
        return {
            source: { width: this.sourceWidth, height: this.sourceHeight },
            target: { width: this.targetWidth, height: this.targetHeight },
            scale: { x: this.scaleX, y: this.scaleY },
            offset: { x: this.offsetX, y: this.offsetY },
            sensitivity: this.touchSensitivity
        };
    }
}
