package com.screenmirror.samsung.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import android.util.Log;

import java.nio.ByteBuffer;

public class ImageUtils {

    private static final String TAG = "ImageUtils";

    /**
     * Converts an Android Image object (typically from ImageReader) to a Bitmap.
     * This method handles RGBA_8888 format.
     * @param image The Image object to convert.
     * @return A Bitmap representation of the image, or null if conversion fails.
     */
    public static Bitmap imageToBitmap(Image image) {
        if (image == null) {
            Log.e(TAG, "Input image is null.");
            return null;
        }

        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            Log.e(TAG, "Image has no planes.");
            image.close(); // Ensure image is closed even if no planes
            return null;
        }

        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();

        Bitmap bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);

        // If there's padding, create a new bitmap to remove it
        if (rowPadding > 0) {
            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
            bitmap.recycle(); // Recycle the padded bitmap
            return croppedBitmap;
        }

        // Rotate if necessary (e.g., for some devices or orientations)
        // This part might need device-specific tuning.
        // For now, assuming portrait or consistent orientation.
        // Example for rotation:
        // Matrix matrix = new Matrix();
        // matrix.postRotate(90); // Rotate 90 degrees clockwise
        // Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        // bitmap.recycle();
        // return rotatedBitmap;

        return bitmap;
    }
}
