package env;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Environment;
import android.util.Log;

//import org.tensorflow.lite.examples.detection.MainActivity;
//import org.tensorflow.lite.examples.detection.tflite.Classifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class Utils {

    /**
     * Memory-map the model file in Assets.
     */
    public static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public static void softmax(final float[] vals) {
        float max = Float.NEGATIVE_INFINITY;
        for (final float val : vals) {
            max = Math.max(max, val);
        }
        float sum = 0.0f;
        for (int i = 0; i < vals.length; ++i) {
            vals[i] = (float) Math.exp(vals[i] - max);
            sum += vals[i];
        }
        for (int i = 0; i < vals.length; ++i) {
            vals[i] = vals[i] / sum;
        }
    }

    public static float expit(final float x) {
        return (float) (1. / (1. + Math.exp(-x)));
    }

//    public static Bitmap scale(Context context, String filePath) {
//        AssetManager assetManager = context.getAssets();
//
//        InputStream istr;
//        Bitmap bitmap = null;
//        try {
//            istr = assetManager.open(filePath);
//            bitmap = BitmapFactory.decodeStream(istr);
//            bitmap = Bitmap.createScaledBitmap(bitmap, MainActivity.TF_OD_API_INPUT_SIZE, MainActivity.TF_OD_API_INPUT_SIZE, false);
//        } catch (IOException e) {
//            // handle exception
//            Log.e("getBitmapFromAsset", "getBitmapFromAsset: " + e.getMessage());
//        }
//
//        return bitmap;
//    }

    public static Bitmap getBitmapFromAsset(Context context, String filePath) {
        AssetManager assetManager = context.getAssets();

        InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = assetManager.open(filePath);
            bitmap = BitmapFactory.decodeStream(istr);
//            return bitmap.copy(Bitmap.Config.ARGB_8888,true);
        } catch (IOException e) {
            // handle exception
            Log.e("getBitmapFromAsset", "getBitmapFromAsset: " + e.getMessage());
        }

        return bitmap;
    }

    /**
     * Returns a transformation matrix from one reference frame into another.
     * Handles cropping (if maintaining aspect ratio is desired) and rotation.
     *
     * @param srcWidth Width of source frame.
     * @param srcHeight Height of source frame.
     * @param dstWidth Width of destination frame.
     * @param dstHeight Height of destination frame.
     * @param applyRotation Amount of rotation to apply from one frame to another.
     *  Must be a multiple of 90.
     * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
     * cropping the image if necessary.
     * @return The transformation fulfilling the desired requirements.
     */
    public static Matrix getTransformationMatrix(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation,
            final boolean maintainAspectRatio) {
        final Matrix matrix = new Matrix();

        if (applyRotation != 0) {
            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        // Account for the already applied rotation, if any, and then determine how
        // much scaling is needed for each axis.
        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

        final int inWidth = transpose ? srcHeight : srcWidth;
        final int inHeight = transpose ? srcWidth : srcHeight;

        // Apply scaling if necessary.
        if (inWidth != dstWidth || inHeight != dstHeight) {
            final float scaleFactorX = dstWidth / (float) inWidth;
            final float scaleFactorY = dstHeight / (float) inHeight;

            if (maintainAspectRatio) {
                // Scale by minimum factor so that dst is filled completely while
                // maintaining the aspect ratio. Some image may fall off the edge.
                final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
                matrix.postScale(scaleFactor, scaleFactor);
            } else {
                // Scale exactly to fill dst from src.
                matrix.postScale(scaleFactorX, scaleFactorY);
            }
        }

        if (applyRotation != 0) {
            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;
    }

    public static Bitmap processBitmap(Bitmap source, int size){

        int image_height = source.getHeight();
        int image_width = source.getWidth();

        Bitmap croppedBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

        Matrix frameToCropTransformations = getTransformationMatrix(image_width,image_height,size,size,0,false);
        Matrix cropToFrameTransformations = new Matrix();
        frameToCropTransformations.invert(cropToFrameTransformations);

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(source, frameToCropTransformations, new Paint(6));

        return croppedBitmap;
    }

    public static void writeToFile(String data, Context context) {
        try {
            String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
            String fileName = "myFile.txt";

            File file = new File(baseDir + File.separator + fileName);

            FileOutputStream stream = new FileOutputStream(file);
            try {
                stream.write(data.getBytes());
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    public static Bitmap cropImage(Bitmap originalImage, Bitmap resizedImage, RectF location) {
        int originalHeight = originalImage.getHeight();
        int originalWidth = originalImage.getWidth();
        Log.d("debug","original: "+String.valueOf(originalHeight) + " " + String.valueOf(originalWidth));
        int resizedHeight = resizedImage.getHeight();
        int resizedWidth = resizedImage.getWidth();
        Log.d("debug","resized: "+String.valueOf(resizedHeight) + " " + String.valueOf(resizedWidth));
        float scaledHeight = ((float)originalHeight / (float)resizedHeight);
        float scaledWidth = ((float)originalWidth / (float)resizedWidth);
        Log.d("debug","scaled: "+String.valueOf(scaledHeight) + " " + String.valueOf(scaledWidth));
        float xMin = location.left;
        float yMin = location.top;
        float xMax = location.right;
        float yMax = location.bottom;
        xMin = Math.max(1, xMin*scaledWidth);
        yMin = Math.max(1, yMin*scaledHeight);
        xMax = Math.min(originalWidth, xMax*scaledWidth);
        yMax = Math.min(originalHeight, yMax*scaledHeight);
        if (xMin - 20 >= 0) xMin -= 20;
        if (yMin - 5 >= 0) yMin -= 5;
        if (xMax + 20 <= originalWidth) xMax += 20;
        if (yMax + 5 <= originalHeight) yMax += 5;
        String testStr = "xMin: " + String.valueOf(xMin) + " yMin: " + String.valueOf(yMin) + " xMax: " + String.valueOf(xMax) + " yMax: " + String.valueOf(yMax);
        Log.d("debug",testStr); //debug
        Bitmap croppedBitmap = Bitmap.createBitmap(originalImage, (int)xMin, (int)yMin, (int)xMax-(int)xMin, (int)yMax-(int)yMin);

        return croppedBitmap;
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public static boolean checkRotate(Bitmap image) {
        int width = image.getWidth();
        int height = image.getHeight();
//        Log.d("debug","height: " + String.valueOf(height) + " width: "+String.valueOf(width));
        return height < width; //images with height < width are rotated 90 degrees by default?
    }
}
