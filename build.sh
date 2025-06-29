#!/bin/bash

# Screen Mirror Android APK Build Script
# This script helps build the APK using online compilation services

echo "=== Screen Mirror Android APK Builder ==="
echo ""

# Check if we're in the right directory
if [ ! -f "AndroidManifest.xml" ]; then
    echo "Error: AndroidManifest.xml not found. Please run this script from the project directory."
    exit 1
fi

echo "Project files found. Preparing for compilation..."

# Create necessary directories
mkdir -p res/layout
mkdir -p res/values
mkdir -p res/drawable
mkdir -p res/mipmap-hdpi
mkdir -p res/mipmap-mdpi
mkdir -p res/mipmap-xhdpi
mkdir -p res/mipmap-xxhdpi
mkdir -p res/mipmap-xxxhdpi
mkdir -p res/xml
mkdir -p src/main/java/com/screenmirror/samsung
mkdir -p src/main/java/com/screenmirror/samsung/service

# Move files to correct locations
echo "Organizing project structure..."

# Move layout file
if [ -f "activity_main.xml" ]; then
    mv activity_main.xml res/layout/
fi

# Move Java files
if [ -f "MainActivity.java" ]; then
    mv MainActivity.java src/main/java/com/screenmirror/samsung/
fi

# Create strings.xml
cat > res/values/strings.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Screen Mirror</string>
    <string name="start_mirroring">Start Screen Mirroring</string>
    <string name="stop_mirroring">Stop Screen Mirroring</string>
    <string name="accessibility_settings">Accessibility Settings</string>
    <string name="status_running">Screen mirroring is active</string>
    <string name="status_stopped">Screen mirroring is stopped</string>
    <string name="permission_required">Permissions Required</string>
    <string name="accessibility_service_description">Allows Screen Mirror to simulate touch events on your device when controlled from iPad</string>
</resources>
EOF

# Create styles.xml
cat > res/values/styles.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="android:Theme.Material.Light.DarkActionBar">
        <item name="android:colorPrimary">#667EEA</item>
        <item name="android:colorPrimaryDark">#5A6FD8</item>
        <item name="android:colorAccent">#764BA2</item>
        <item name="android:windowBackground">@drawable/gradient_background</item>
    </style>
</resources>
EOF

# Create colors.xml
cat > res/values/colors.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#667EEA</color>
    <color name="primary_dark">#5A6FD8</color>
    <color name="accent">#764BA2</color>
    <color name="white">#FFFFFF</color>
    <color name="black">#000000</color>
    <color name="gray">#808080</color>
    <color name="light_gray">#E0E0E0</color>
    <color name="success">#27AE60</color>
    <color name="danger">#E74C3C</color>
</resources>
EOF

# Create drawable resources
cat > res/drawable/gradient_background.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:startColor="#667EEA"
        android:endColor="#764BA2"
        android:angle="135" />
</shape>
EOF

cat > res/drawable/button_primary.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <gradient
        android:startColor="#667EEA"
        android:endColor="#764BA2"
        android:angle="0" />
    <corners android:radius="12dp" />
</shape>
EOF

cat > res/drawable/button_danger.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#E74C3C" />
    <corners android:radius="12dp" />
</shape>
EOF

cat > res/drawable/button_secondary.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <stroke android:width="2dp" android:color="#667EEA" />
    <solid android:color="#FFFFFF" />
    <corners android:radius="12dp" />
</shape>
EOF

# Create accessibility service config
cat > res/xml/accessibility_service_config.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:packageNames="com.screenmirror.samsung"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFlags="flagDefault"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true" />
EOF

# Create file paths config
cat > res/xml/file_paths.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-path name="external_files" path="."/>
</paths>
EOF

# Create a simple build.gradle
cat > build.gradle << 'EOF'
apply plugin: 'com.android.application'

android {
    compileSdkVersion 33
    buildToolsVersion "33.0.0"
    
    defaultConfig {
        applicationId "com.screenmirror.samsung"
        minSdkVersion 24
        targetSdkVersion 33
        versionCode 1
        versionName "1.0"
    }
    
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.cardview:cardview:1.0.0'
    implementation 'androidx.core:core:1.9.0'
    implementation 'com.google.android.material:material:1.8.0'
}
EOF

echo ""
echo "Project structure created successfully!"
echo ""
echo "=== BUILD OPTIONS ==="
echo ""
echo "Option 1: Online APK Builder (Recommended)"
echo "1. Zip this entire folder"
echo "2. Go to https://www.apkbuilder.online/"
echo "3. Upload the zip file"
echo "4. Click 'Build APK'"
echo "5. Download the generated APK"
echo ""
echo "Option 2: Android Studio"
echo "1. Open Android Studio"
echo "2. Import this project"
echo "3. Build > Generate Signed Bundle/APK"
echo ""
echo "Option 3: Command Line (if Android SDK installed)"
echo "1. Run: ./gradlew assembleRelease"
echo "2. APK will be in app/build/outputs/apk/release/"
echo ""
echo "=== INSTALLATION ==="
echo "1. Transfer APK to Samsung Galaxy S22 Ultra"
echo "2. Enable 'Unknown Sources' in Settings > Security"
echo "3. Install the APK"
echo "4. Grant all required permissions"
echo ""

# Create a zip file for easy upload
if command -v zip &> /dev/null; then
    echo "Creating zip file for online builders..."
    zip -r screen_mirror_android.zip . -x "*.sh" "*.zip"
    echo "Created: screen_mirror_android.zip"
    echo ""
fi

echo "Build preparation complete!"
echo "Choose one of the build options above to create your APK."

