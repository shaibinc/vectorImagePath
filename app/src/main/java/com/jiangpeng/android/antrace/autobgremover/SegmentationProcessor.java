package com.jiangpeng.android.antrace.autobgremover;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.util.Pair;

import androidx.lifecycle.LifecycleObserver;

import org.jetbrains.annotations.NotNull;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayDeque;
import java.util.Queue;

import kotlin.jvm.internal.Intrinsics;


public final class SegmentationProcessor implements LifecycleObserver {
    private static final float IMAGE_MEAN = 128.0f;
    private static final String MODEL_FILE = "deeplabv3_mnv2_pascal_trainval.tflite";
    private final Context context;
    private int inputImageHeight;
    private int inputImageWidth;
    private Interpreter interpreter;
    private int modelInputSize;
    private int bitForCluster = 1;
    private int maxBit = 1;
    private int[][] fillArray;


    public SegmentationProcessor(@NotNull Context context2) {
        Intrinsics.checkParameterIsNotNull(context2, "context");
        this.context = context2;
    }

    public final void initializeInterpreter() throws IOException {
        AssetManager assets = this.context.getAssets();
        Intrinsics.checkExpressionValueIsNotNull(assets, "assetManager");
        ByteBuffer loadModelFile = loadModelFile(assets);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2);
        Interpreter interpreter2 = new Interpreter(loadModelFile, options);
        int[] shape = interpreter2.getInputTensor(0).shape();
        this.inputImageWidth = shape[1];
        this.inputImageHeight = shape[2];
        this.modelInputSize = this.inputImageWidth * 4 * this.inputImageHeight * 3;
        this.interpreter = interpreter2;
    }

    private final ByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
        AssetFileDescriptor openFd = assetManager.openFd(MODEL_FILE);
        Intrinsics.checkExpressionValueIsNotNull(openFd, "fileDescriptor");
        MappedByteBuffer map = new FileInputStream(openFd.getFileDescriptor()).getChannel().map(MapMode.READ_ONLY, openFd.getStartOffset(), openFd.getDeclaredLength());
        return map;
    }
    public final Bitmap getMask(Bitmap bitmap){
        Bitmap scale = ImageUtils.tfResizeBilinear(bitmap, this.inputImageWidth, this.inputImageHeight);
        int[][] iArr2 = runSegmentation(scale);
        if(iArr2 == null)
            return  null;
        reFillBits(iArr2);
        Bitmap createOverlayResult = createOverlayResult(scale, fillArray);
        return createOverlayResult;
    }

    @NotNull
    public final int[][] runSegmentation(@NotNull Bitmap scale) {

        ByteBuffer convertBitmapToByteBuffer = convertBitmapToByteBuffer(scale);
        long[][][] jArr = new long[1][][];
        for (int i = 0; i < 1; i++) {
            int i2 = this.inputImageWidth;
            long[][] jArr2 = new long[i2][];
            for (int i3 = 0; i3 < i2; i3++) {
                jArr2[i3] = new long[this.inputImageWidth];
            }
            jArr[i] = jArr2;
        }
        long[][][] jArr3 = jArr;
        Interpreter interpreter2 = this.interpreter;
        if (interpreter2 != null) {
            interpreter2.run(convertBitmapToByteBuffer, jArr3);
        }
        else
            return null;
        int[][] iArr = new int[this.inputImageWidth][this.inputImageHeight];
        for(int i = 0 ; i < iArr.length; i++){
            for(int j = 0 ; j < iArr[i].length; j++)
                iArr[i][j] = (int)jArr3[0][i][j];
        }
        boolean isPerson = false;
        for (int i = 0; i < inputImageWidth; i++) {
            for (int i2 = 0; i2 < inputImageHeight; i2++) {
                if (iArr[i2][i]==15) {
                    iArr[i2][i] =1;
                    isPerson = true;
                }else {
                    iArr[i2][i] =0;
                }
            }}
        if(isPerson)
            return iArr;
        return null;
    }



    public final void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }

    private final ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer allocateDirect = ByteBuffer.allocateDirect(this.modelInputSize);
        allocateDirect.order(ByteOrder.nativeOrder());
        int[] iArr = new int[(this.inputImageWidth * this.inputImageHeight)];
        bitmap.getPixels(iArr, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i : iArr) {
            float f = (((float) ((i >> 8) & 255)) / IMAGE_MEAN) - 1.0f;
            float f2 = (((float) (i & 255)) / IMAGE_MEAN) - 1.0f;
            allocateDirect.putFloat((((float) ((i >> 16) & 255)) / IMAGE_MEAN) - 1.0f);
            allocateDirect.putFloat(f);
            allocateDirect.putFloat(f2);
        }
        Intrinsics.checkExpressionValueIsNotNull(allocateDirect, "byteBuffer");
        return allocateDirect;
    }



    private final Bitmap createOverlayResult( Bitmap bitmap2, int[][] iArr) {
        int width = bitmap2.getWidth();
        int height = bitmap2.getHeight();
        if (width == iArr[0].length && height == ((Object[]) iArr).length) {
            Bitmap createBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
            for (int i = 0; i < height; i++) {
                for (int i2 = 0; i2 < width; i2++) {
                    if (iArr[i2][i]!=maxBit) {
                        createBitmap.setPixel(i, i2,Color.BLACK );
                    } else {
                        createBitmap.setPixel(i, i2, Color.WHITE);
                    }
                }
            }

           return createBitmap;
        }

    return null;
    }

    void reFillBits(int[][] arr) {
        int current_area = 0;
        int max_area = 0;
        fillArray = new int[inputImageHeight][inputImageWidth];
        for (int r = 0; r < inputImageHeight; r++) {
            for (int c = 0; c < inputImageWidth; c++) {
                if (arr[r][c] != 0) {
                    bitForCluster++;
                    current_area = fill(arr, r, c);
                    if (current_area > max_area) {
                        max_area = current_area;
                        maxBit = bitForCluster;
                    }
                }
            }
        }

    }


    int fill( int[][] arr,int r, int c) {
        int count = 0;
        Queue<Pair<Integer, Integer>> queue = new ArrayDeque<>();
        queue.add(new Pair(r,c));
        while (!queue.isEmpty()) {
            Pair<Integer, Integer> xy = queue.remove();
            int x = xy.first;
            int y = xy.second;
            if (x < inputImageHeight && y < inputImageWidth && x >= 0 && y >= 0 && arr[x][y]!=0) {
                count = count + 1;
                arr[x][y] = 0;
                fillArray[x][y] = bitForCluster;
                queue.add(new Pair(x, y - 1));
                queue.add(new Pair(x, y + 1));
                queue.add(new Pair(x - 1, y));
                queue.add(new Pair(x + 1, y));
            }
        }
        return count;
    }


    public int getInputSize() {
        return 513;
    }
}
