package com.jiangpeng.android.antrace.autobgremover;


import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;

import com.jiangpeng.android.antrace.Objects.curve;
import com.jiangpeng.android.antrace.Objects.dpoint;
import com.jiangpeng.android.antrace.Objects.path;
import com.jiangpeng.android.antrace.Utils;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import kotlin.collections.ArraysKt;


public class SegmentBitmapsLoader extends  AsyncTask<Void, Void, Boolean> {
    private Context context;
    private SegmentationProcessor segmentation ;
    public boolean isInitialized = false;

    static {
        System.loadLibrary("antrace");
    }

    public SegmentBitmapsLoader(Context context) {
        this.context = context.getApplicationContext();
        segmentation = new SegmentationProcessor(context);
    }
    Bitmap bitmap;
    public Uri loadInBackgroundForMask(Uri mImageUri) {

        if (context == null) {
            return null;
        }

        final Resources res = context.getResources();
        if (res == null) {
            return null;
        }

        if (mImageUri == null) {
            return null;
        }



        try {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            double densityAdj = metrics.density > 1 ? 1 / metrics.density : 1;
            int mWidth = (int) (metrics.widthPixels * densityAdj);
            int mHeight = (int) (metrics.heightPixels * densityAdj);
            ContentResolver resolver = context.getContentResolver();
            InputStream stream = null;
            stream = resolver.openInputStream(mImageUri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, new Rect(), options);
            options.inJustDecodeBounds = false;
            closeSafe(stream);

            if (options.outWidth == -1 && options.outHeight == -1)
                throw new RuntimeException("File is not a picture");
            options.inSampleSize =
                    Math.max(
                            calculateInSampleSizeByReqestedSize(
                                    options.outWidth, options.outHeight, mWidth, mHeight),
                            calculateInSampleSizeByMaxTextureSize(options.outWidth, options.outHeight));

            // Decode bitmap with inSampleSize set
            bitmap = decodeImage(resolver, mImageUri, options);

            bitmap = modifyOrientation(bitmap, mImageUri, context);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        if (bitmap == null) {
            return null;
        }

        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();

        Bitmap mask = segmentation.getMask(bitmap);
        if (mask == null)
            return null;
        mask = ImageUtils.tfResizeBilinear(mask,w,h);
        try {
            File resultFile = createAutoBgMaskFile();
            FileOutputStream out = new FileOutputStream(resultFile);
            mask.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return Uri.fromFile(resultFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Uri loadInBackground(Uri mImageUri) {

        if (context == null) {
            return null;
        }

        final Resources res = context.getResources();
        if (res == null) {
            return null;
        }

        if (mImageUri == null) {
            return null;
        }

        Bitmap bitmap;

        try {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            double densityAdj = metrics.density > 1 ? 1 / metrics.density : 1;
            int mWidth = (int) (metrics.widthPixels * densityAdj);
            int mHeight = (int) (metrics.heightPixels * densityAdj);
            ContentResolver resolver = context.getContentResolver();
            InputStream stream = null;
            stream = resolver.openInputStream(mImageUri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, new Rect(), options);
            options.inJustDecodeBounds = false;
            closeSafe(stream);

            if (options.outWidth == -1 && options.outHeight == -1)
                throw new RuntimeException("File is not a picture");
            options.inSampleSize =
                    Math.max(
                            calculateInSampleSizeByReqestedSize(
                                    options.outWidth, options.outHeight, mWidth, mHeight),
                            calculateInSampleSizeByMaxTextureSize(options.outWidth, options.outHeight));

            // Decode bitmap with inSampleSize set
            bitmap = decodeImage(resolver, mImageUri, options);

            bitmap = modifyOrientation(bitmap, mImageUri, context);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        if (bitmap == null) {
            return null;
        }

        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();

        Bitmap mask = segmentation.getMask(bitmap);
        if (mask == null)
            return null;
        mask = ImageUtils.tfResizeBilinear(mask, w , h);
        Path path = getSegmentationPath(bitmap, mask);
        if (path == null)
            return null;
        final Bitmap cropped = cropImage(bitmap, path);
        try {
            File resultFile = createAutoBgResultFile();
            FileOutputStream out = new FileOutputStream(resultFile);
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return Uri.fromFile(resultFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Path getSegmentationPath(Bitmap bitmap, Bitmap mask) {
        path traceImage = Utils.traceImage(mask);
        if(traceImage == null)
            return null;
        Path path = new Path();
        dpoint[][] dpointArr = traceImage.curve.c;
        dpoint[] dpointArr2 = (dpoint[]) ArraysKt.last((Object[]) dpointArr);
        path.moveTo((float) dpointArr2[2].x, (float) dpointArr2[2].y);
        int i = traceImage.curve.n - 1;
        if (i >= 0) {
            int i2 = 0;
            while (true) {
                dpoint[] dpointArr3 = traceImage.curve.c[i2];
                curve curve = traceImage.curve;
                if (curve.tag[i2] == curve.POTRACE_CURVETO) {
                    dpoint dpoint = dpointArr3[0];
                    dpoint dpoint2 = dpointArr3[1];
                    dpoint dpoint3 = dpointArr3[2];
                    path.cubicTo((float) dpoint.x, (float) dpoint.y, (float) dpoint2.x, (float) dpoint2.y, (float) dpoint3.x, (float) dpoint3.y);
                } else if (curve.tag[i2] == curve.POTRACE_CORNER) {
                    dpoint dpoint4 = dpointArr3[1];
                    dpoint dpoint5 = dpointArr3[2];
                    path.lineTo((float) dpoint4.x, (float) dpoint4.y);
                    path.lineTo((float) dpoint5.x, (float) dpoint5.y);
                }
                if (i2 == i) {
                    break;
                }
                i2++;
            }
        }
        path.close();
        Matrix matrix = new Matrix();
        float max = Math.max(((float) bitmap.getHeight()) / ((float) mask.getHeight()), ((float) bitmap.getWidth()) / ((float) mask.getWidth()));
        matrix.postScale(max, max);
        if (bitmap.getHeight() < bitmap.getWidth()) {
            matrix.postTranslate(0.0f, (-((((float) mask.getHeight()) * max) - ((float) bitmap.getHeight()))) / ((float) 2));
        }
        if (bitmap.getHeight() > bitmap.getWidth()) {
            matrix.postTranslate((-((((float) mask.getWidth()) * max) - ((float) bitmap.getWidth()))) / ((float) 2), 0.0f);
        }
        matrix.postScale(1.0f, -1.0f, ((float) bitmap.getWidth()) / 2.0f, ((float) bitmap.getHeight()) / 2.0f);
        path.transform(matrix);
        return path;
    }

    public Bitmap cropImage(Bitmap bitmap, Path path) {
        Bitmap fullScreenBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullScreenBitmap);
        Paint transPainter = new Paint();
        transPainter.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRect(0, 0, fullScreenBitmap.getWidth(), fullScreenBitmap.getHeight(), transPainter);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setFilterBitmap(true);
        canvas.drawPath(path, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, 0, 0, paint);



        // Create a bitmap with just the cropped area.
        Region region = new Region();
        Region clip = new Region(0, 0, fullScreenBitmap.getWidth(), fullScreenBitmap.getHeight());
        region.setPath(path, clip);
        Rect bounds = region.getBounds();
        if (bounds.width() == 0 && bounds.height() == 0) {
            bounds.set(0, 0, 100, 100);

        }
        Bitmap croppedBitmap =
                Bitmap.createBitmap(fullScreenBitmap, bounds.left, bounds.top,
                        bounds.width(), bounds.height());
        return croppedBitmap;
    }
    public Bitmap cropImageWithoutClip(Bitmap bitmap, Path path) {
        Bitmap fullScreenBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fullScreenBitmap);
        Paint transPainter = new Paint();
        transPainter.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRect(0, 0, fullScreenBitmap.getWidth(), fullScreenBitmap.getHeight(), transPainter);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setFilterBitmap(true);
        canvas.drawPath(path, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return fullScreenBitmap;
    }

    private File createAutoBgResultFile() throws IOException {
        File bgFile = new File(context.getCacheDir(), "AutoBg_image.png");
        if (!bgFile.exists())
            bgFile.createNewFile();
        return bgFile;
    }
    private File createAutoBgMaskFile() throws IOException {
        File bgFile = new File(context.getCacheDir(), "AutoBg_Mask.png");
        if (!bgFile.exists())
            bgFile.createNewFile();
        return bgFile;
    }


    @Override
    protected Boolean doInBackground(Void... voids) {
        try {
            segmentation.initializeInterpreter();
            isInitialized = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return isInitialized;
    }

    @Override
    protected void onCancelled(Boolean aBoolean) {
        super.onCancelled(aBoolean);
        closeTF();
    }

    public void closeTF() {
        if (isInitialized) {
            segmentation.close();
            isInitialized = false;
            segmentation = null;
        }
    }
    public Bitmap getResizedBitmap(){
        return bitmap;
    }


    public static void closeSafe(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static Bitmap decodeImage(
            ContentResolver resolver, Uri uri, BitmapFactory.Options options)
            throws FileNotFoundException {
        do {
            InputStream stream = null;
            try {
                stream = resolver.openInputStream(uri);
                return BitmapFactory.decodeStream(stream, new Rect(), options);
            } catch (OutOfMemoryError e) {
                options.inSampleSize *= 2;
            } finally {
                closeSafe(stream);
            }
        } while (options.inSampleSize <= 512);
        throw new RuntimeException("Failed to decode image: " + uri);
    }

    public static int calculateInSampleSizeByMaxTextureSize(int width, int height) {
        int mMaxTextureSize = getMaxTextureSize();
        int inSampleSize = 1;
        if (mMaxTextureSize > 0) {
            while ((height / inSampleSize) > mMaxTextureSize
                    || (width / inSampleSize) > mMaxTextureSize) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
    private static int getMaxTextureSize() {
        // Safe minimum default size
        final int IMAGE_MAX_BITMAP_DIMENSION = 2048;
        try {
            // Get EGL Display
            EGL10 egl = (EGL10) EGLContext.getEGL();
            EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

            // Initialise
            int[] version = new int[2];
            egl.eglInitialize(display, version);

            // Query total number of configurations
            int[] totalConfigurations = new int[1];
            egl.eglGetConfigs(display, null, 0, totalConfigurations);

            // Query actual list configurations
            EGLConfig[] configurationsList = new EGLConfig[totalConfigurations[0]];
            egl.eglGetConfigs(display, configurationsList, totalConfigurations[0], totalConfigurations);

            int[] textureSize = new int[1];
            int maximumTextureSize = 0;

            // Iterate through all the configurations to located the maximum texture size
            for (int i = 0; i < totalConfigurations[0]; i++) {
                // Only need to check for width since opengl textures are always squared
                egl.eglGetConfigAttrib(
                        display, configurationsList[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize);

                // Keep track of the maximum texture size
                if (maximumTextureSize < textureSize[0]) {
                    maximumTextureSize = textureSize[0];
                }
            }

            // Release
            egl.eglTerminate(display);

            // Return largest texture size found, or default
            return Math.max(maximumTextureSize, IMAGE_MAX_BITMAP_DIMENSION);
        } catch (Exception e) {
            return IMAGE_MAX_BITMAP_DIMENSION;
        }
    }

    public static int calculateInSampleSizeByReqestedSize(
            int width, int height, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            while ((height / 2 / inSampleSize) > reqHeight && (width / 2 / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static Bitmap modifyOrientation(Bitmap bitmap, Uri imageUri,Context context) throws IOException {
        InputStream in = context.getContentResolver().openInputStream(imageUri);
        ExifInterface ei = null;
        if (in != null) {
            ei = new ExifInterface(in);
        }
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return rotate(bitmap, 90);

            case ExifInterface.ORIENTATION_ROTATE_180:
                return rotate(bitmap, 180);

            case ExifInterface.ORIENTATION_ROTATE_270:
                return rotate(bitmap, 270);

            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                return flip(bitmap, true, false);

            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return flip(bitmap, false, true);

            default:
                return bitmap;
        }
    }
    public static Bitmap rotate(Bitmap bitmap, float degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap flip(Bitmap bitmap, boolean horizontal, boolean vertical) {
        Matrix matrix = new Matrix();
        matrix.preScale(horizontal ? -1 : 1, vertical ? -1 : 1);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap getBitmapFromUri(Context context,Uri imageUri)throws IOException{
        Bitmap mBitmap;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        double densityAdj = metrics.density > 1 ? 1 / metrics.density : 1;
        int mWidth = (int) (metrics.widthPixels * densityAdj);
        int mHeight = (int) (metrics.heightPixels * densityAdj);
        ContentResolver resolver = context.getContentResolver();
        InputStream stream = null;
        stream = resolver.openInputStream(imageUri);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(stream, new Rect(), options);
        options.inJustDecodeBounds = false;
        closeSafe(stream);
        if(options.outWidth  == -1 && options.outHeight == -1)
            throw new RuntimeException("File is not a picture");
        options.inSampleSize =
                Math.max(
                        calculateInSampleSizeByReqestedSize(
                                options.outWidth, options.outHeight, mWidth, mHeight),
                        calculateInSampleSizeByMaxTextureSize(options.outWidth, options.outHeight));

        // Decode bitmap with inSampleSize set
        mBitmap = decodeImage(resolver, imageUri, options);

        mBitmap = modifyOrientation(mBitmap,imageUri,context);
        return mBitmap;
    }
}
