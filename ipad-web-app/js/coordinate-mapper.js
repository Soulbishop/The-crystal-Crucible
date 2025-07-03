/**
 * Coordinate Mapper for Screen Mirror PWA
 * Maps touch coordinates between iPad Air 2 and Samsung Galaxy S22 Ultra
 */

class CoordinateMapper {
    constructor(options = {}) {
        // iPad Air 2 specifications
        this.sourceWidth = options.sourceWidth || 2048;
        this.sourceHeight = options.sourceHeight || 1536;
        
        // Samsung Galaxy S22 Ultra specifications
        this.targetWidth = options.targetWidth || 3088;
        this.targetHeight = options.targetHeight || 1440;
        
        // Calculate aspect ratios
        this.sourceAspectRatio = this.sourceWidth / this.sourceHeight;
        this.targetAspectRatio = this.targetWidth / this.targetHeight;
        
        // Mapping mode: 'contain', 'cover', 'stretch'
        this.mappingMode = options.mappingMode || 'contain';
        
        // Orientation tracking
        this.sourceOrientation = 'landscape'; // iPad orientation
        this.targetOrientation = 'landscape'; // Samsung orientation
        
        // Calibration offsets
        this.calibrationOffset = {
            x: options.offsetX || 0,
            y: options.offsetY || 0
        };
        
        // Dead zones (areas where touches should be ignored)
        this.deadZones = options.deadZones || [];
        
        this.calculateMappingParameters();
        console.log('Coordinate mapper initialized:', this.getMappingInfo());
    }
    
    calculateMappingParameters() {
        // Calculate scaling factors based on mapping mode
        switch (this.mappingMode) {
            case 'contain':
                // Scale to fit within target while maintaining aspect ratio
                if (this.sourceAspectRatio > this.targetAspectRatio) {
                    // Source is wider, scale based on width
                    this.scaleX = this.targetWidth / this.sourceWidth;
                    this.scaleY = this.scaleX;
                } else {
                    // Source is taller, scale based on height
                    this.scaleY = this.targetHeight / this.sourceHeight;
                    this.scaleX = this.scaleY;
                }
                break;
                
            case 'cover':
                // Scale to cover entire target while maintaining aspect ratio
                if (this.sourceAspectRatio > this.targetAspectRatio) {
                    // Source is wider, scale based on height
                    this.scaleY = this.targetHeight / this.sourceHeight;
                    this.scaleX = this.scaleY;
                } else {
                    // Source is taller, scale based on width
                    this.scaleX = this.targetWidth / this.sourceWidth;
                    this.scaleY = this.scaleX;
                }
                break;
                
            case 'stretch':
                // Stretch to fill entire target (may distort aspect ratio)
                this.scaleX = this.targetWidth / this.sourceWidth;
                this.scaleY = this.targetHeight / this.sourceHeight;
                break;
                
            default:
                this.scaleX = 1.0;
                this.scaleY = 1.0;
        }
        
        // Calculate the actual mapped dimensions
        this.mappedWidth = this.sourceWidth * this.scaleX;
        this.mappedHeight = this.sourceHeight * this.scaleY;
        
        // Calculate centering offsets
        this.offsetX = (this.targetWidth - this.mappedWidth) / 2;
        this.offsetY = (this.targetHeight - this.mappedHeight) / 2;
        
        console.log('Mapping parameters calculated:', {
            scaleX: this.scaleX,
            scaleY: this.scaleY,
            offsetX: this.offsetX,
            offsetY: this.offsetY,
            mappedSize: `${this.mappedWidth}x${this.mappedHeight}`
        });
    }
    
    mapCoordinates(sourceX, sourceY) {
        // Apply orientation transformations if needed
        let transformedX = sourceX;
        let transformedY = sourceY;
        
        // Handle orientation differences
        if (this.sourceOrientation !== this.targetOrientation) {
            [transformedX, transformedY] = this.transformOrientation(transformedX, transformedY);
        }
        
        // Apply scaling and offset
        let targetX = (transformedX * this.scaleX) + this.offsetX;
        let targetY = (transformedY * this.scaleY) + this.offsetY;
        
        // Apply calibration offset
        targetX += this.calibrationOffset.x;
        targetY += this.calibrationOffset.y;
        
        // Clamp to target bounds
        targetX = Math.max(0, Math.min(this.targetWidth - 1, targetX));
        targetY = Math.max(0, Math.min(this.targetHeight - 1, targetY));
        
        // Check dead zones
        if (this.isInDeadZone(targetX, targetY)) {
            return null; // Ignore touches in dead zones
        }
        
        return {
            x: Math.round(targetX),
            y: Math.round(targetY)
        };
    }
    
    transformOrientation(x, y) {
        // Transform coordinates based on orientation differences
        // This handles cases where iPad and Samsung have different orientations
        
        if (this.sourceOrientation === 'portrait' && this.targetOrientation === 'landscape') {
            // Rotate 90 degrees clockwise
            return [this.sourceHeight - y, x];
        } else if (this.sourceOrientation === 'landscape' && this.targetOrientation === 'portrait') {
            // Rotate 90 degrees counter-clockwise
            return [y, this.sourceWidth - x];
        }
        
        return [x, y];
    }
    
    reverseMapCoordinates(targetX, targetY) {
        // Map coordinates from target back to source (for debugging/calibration)
        
        // Remove calibration offset
        let x = targetX - this.calibrationOffset.x;
        let y = targetY - this.calibrationOffset.y;
        
        // Remove centering offset and scaling
        x = (x - this.offsetX) / this.scaleX;
        y = (y - this.offsetY) / this.scaleY;
        
        // Handle orientation transformation (reverse)
        if (this.sourceOrientation !== this.targetOrientation) {
            [x, y] = this.reverseTransformOrientation(x, y);
        }
        
        // Clamp to source bounds
        x = Math.max(0, Math.min(this.sourceWidth - 1, x));
        y = Math.max(0, Math.min(this.sourceHeight - 1, y));
        
        return {
            x: Math.round(x),
            y: Math.round(y)
        };
    }
    
    reverseTransformOrientation(x, y) {
        if (this.sourceOrientation === 'portrait' && this.targetOrientation === 'landscape') {
            // Reverse of 90 degrees clockwise = 90 degrees counter-clockwise
            return [y, this.targetHeight - x];
        } else if (this.sourceOrientation === 'landscape' && this.targetOrientation === 'portrait') {
            // Reverse of 90 degrees counter-clockwise = 90 degrees clockwise
            return [this.targetWidth - y, x];
        }
        
        return [x, y];
    }
    
    isInDeadZone(x, y) {
        return this.deadZones.some(zone => {
            return x >= zone.x && x <= zone.x + zone.width &&
                   y >= zone.y && y <= zone.y + zone.height;
        });
    }
    
    updateOrientation() {
        // Update orientation based on current device orientation
        const orientation = screen.orientation || {};
        const angle = orientation.angle || 0;
        
        // Determine iPad orientation
        if (angle === 0 || angle === 180) {
            this.sourceOrientation = 'portrait';
        } else {
            this.sourceOrientation = 'landscape';
        }
        
        // Samsung Galaxy S22 Ultra is typically used in landscape for screen mirroring
        this.targetOrientation = 'landscape';
        
        // Recalculate mapping parameters
        this.calculateMappingParameters();
        
        console.log('Orientation updated:', {
            source: this.sourceOrientation,
            target: this.targetOrientation,
            angle: angle
        });
    }
    
    setMappingMode(mode) {
        if (['contain', 'cover', 'stretch'].includes(mode)) {
            this.mappingMode = mode;
            this.calculateMappingParameters();
            console.log('Mapping mode changed to:', mode);
        }
    }
    
    setCalibrationOffset(offsetX, offsetY) {
        this.calibrationOffset.x = offsetX;
        this.calibrationOffset.y = offsetY;
        console.log('Calibration offset updated:', this.calibrationOffset);
    }
    
    addDeadZone(x, y, width, height) {
        this.deadZones.push({ x, y, width, height });
        console.log('Dead zone added:', { x, y, width, height });
    }
    
    removeDeadZone(index) {
        if (index >= 0 && index < this.deadZones.length) {
            const removed = this.deadZones.splice(index, 1)[0];
            console.log('Dead zone removed:', removed);
            return removed;
        }
        return null;
    }
    
    clearDeadZones() {
        this.deadZones = [];
        console.log('All dead zones cleared');
    }
    
    // Calibration helpers
    startCalibration() {
        this.calibrationPoints = [];
        this.isCalibrating = true;
        console.log('Calibration started');
    }
    
    addCalibrationPoint(sourceX, sourceY, targetX, targetY) {
        if (!this.isCalibrating) return;
        
        this.calibrationPoints.push({
            source: { x: sourceX, y: sourceY },
            target: { x: targetX, y: targetY }
        });
        
        console.log('Calibration point added:', {
            source: { x: sourceX, y: sourceY },
            target: { x: targetX, y: targetY }
        });
    }
    
    finishCalibration() {
        if (!this.isCalibrating || this.calibrationPoints.length < 2) {
            console.warn('Insufficient calibration points');
            return false;
        }
        
        // Calculate average offset from calibration points
        let totalOffsetX = 0;
        let totalOffsetY = 0;
        
        this.calibrationPoints.forEach(point => {
            const mapped = this.mapCoordinates(point.source.x, point.source.y);
            totalOffsetX += point.target.x - mapped.x;
            totalOffsetY += point.target.y - mapped.y;
        });
        
        const avgOffsetX = totalOffsetX / this.calibrationPoints.length;
        const avgOffsetY = totalOffsetY / this.calibrationPoints.length;
        
        this.setCalibrationOffset(avgOffsetX, avgOffsetY);
        
        this.isCalibrating = false;
        console.log('Calibration finished with offset:', {
            x: avgOffsetX,
            y: avgOffsetY
        });
        
        return true;
    }
    
    // Information getters
    getMappingInfo() {
        return {
            source: {
                width: this.sourceWidth,
                height: this.sourceHeight,
                aspectRatio: this.sourceAspectRatio,
                orientation: this.sourceOrientation
            },
            target: {
                width: this.targetWidth,
                height: this.targetHeight,
                aspectRatio: this.targetAspectRatio,
                orientation: this.targetOrientation
            },
            mapping: {
                mode: this.mappingMode,
                scaleX: this.scaleX,
                scaleY: this.scaleY,
                offsetX: this.offsetX,
                offsetY: this.offsetY,
                mappedWidth: this.mappedWidth,
                mappedHeight: this.mappedHeight
            },
            calibration: {
                offsetX: this.calibrationOffset.x,
                offsetY: this.calibrationOffset.y
            },
            deadZones: this.deadZones.length
        };
    }
    
    getEffectiveArea() {
        // Returns the area on the target device that corresponds to the source
        return {
            x: this.offsetX + this.calibrationOffset.x,
            y: this.offsetY + this.calibrationOffset.y,
            width: this.mappedWidth,
            height: this.mappedHeight
        };
    }
    
    // Debug and testing methods
    testMapping(sourceX, sourceY) {
        const mapped = this.mapCoordinates(sourceX, sourceY);
        const reversed = mapped ? this.reverseMapCoordinates(mapped.x, mapped.y) : null;
        
        return {
            source: { x: sourceX, y: sourceY },
            mapped: mapped,
            reversed: reversed,
            error: reversed ? {
                x: Math.abs(sourceX - reversed.x),
                y: Math.abs(sourceY - reversed.y)
            } : null
        };
    }
    
    generateTestPattern() {
        const testPoints = [];
        const steps = 5;
        
        for (let i = 0; i <= steps; i++) {
            for (let j = 0; j <= steps; j++) {
                const x = (this.sourceWidth * i) / steps;
                const y = (this.sourceHeight * j) / steps;
                testPoints.push(this.testMapping(x, y));
            }
        }
        
        return testPoints;
    }
}

