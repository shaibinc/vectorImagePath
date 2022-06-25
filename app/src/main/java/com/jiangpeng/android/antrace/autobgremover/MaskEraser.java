package com.jiangpeng.android.antrace.autobgremover;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Stack;


public class MaskEraser extends ImageView {

    private float mBrushSize ;
    private Stack<LinePath> mDrawnPaths = new Stack<>();
    private Stack<LinePath> mRedoPaths = new Stack<>();
    private Paint mDrawPaint;
    private boolean mBrushDrawMode;
    Paint resultPaint;
    Magnifier magnifier;
    private Bitmap backgroundBitmap;
    private Path resultPath;
    private Path mPath;
    private float mTouchX, mTouchY;
    private static final float TOUCH_TOLERANCE = 4;
    Context context;
    private MaskEraserActivity.UndoRedoButtonStateChangeListener undoRedoButtonStateChangeListener;
    Bitmap fullScreenBitmap;
    Canvas maskCanvas;

    public void setMagnifier(Magnifier magnifier) {
        this.magnifier = magnifier;
    }

    public void setBitmap(Bitmap bitmap) {
        this.backgroundBitmap = bitmap;
        fullScreenBitmap = Bitmap.createBitmap(backgroundBitmap.getWidth(), backgroundBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        maskCanvas = new Canvas(fullScreenBitmap);
        magnifier.setBitmap(bitmap);
    }

    public MaskEraser(Context context) {
        this(context, null);
    }

    public MaskEraser(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        setupBrushDrawing();
    }

    public MaskEraser(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;

        setupBrushDrawing();
    }

    private void setupBrushDrawing() {
        resultPaint = new Paint();
        resultPaint.setAntiAlias(true);
        resultPaint.setDither(true);
        resultPaint.setStrokeJoin(Paint.Join.ROUND);
        resultPaint.setStrokeCap(Paint.Cap.ROUND);
        resultPaint.setStyle(Paint.Style.FILL);
        resultPaint.setColor(Color.BLUE);

        //Caution: This line is to disable hardware acceleration to make eraser feature work properly
        setLayerType(LAYER_TYPE_HARDWARE, null);
        mDrawPaint = new Paint();
        mPath = new Path();
        mDrawPaint.setAntiAlias(true);
        mDrawPaint.setDither(true);
        mDrawPaint.setColor(Color.BLUE);
        mDrawPaint.setStyle(Paint.Style.STROKE);
        mDrawPaint.setStrokeJoin(Paint.Join.ROUND);
        mDrawPaint.setStrokeCap(Paint.Cap.ROUND);
        mDrawPaint.setStrokeWidth(mBrushSize);
        mDrawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        this.setVisibility(View.VISIBLE);
        mBrushDrawMode = true;
    }

    private void refreshBrushDrawing() {
        mBrushDrawMode = true;
        mPath = new Path();
        mDrawPaint.setAntiAlias(true);
        mDrawPaint.setDither(true);
        mDrawPaint.setStyle(Paint.Style.STROKE);
        mDrawPaint.setStrokeJoin(Paint.Join.ROUND);
        mDrawPaint.setStrokeCap(Paint.Cap.ROUND);
        mDrawPaint.setStrokeWidth(mBrushSize);
        mDrawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
    }

    void brushEraser() {
        mBrushDrawMode = true;
        mDrawPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    void setBrushDrawingMode(boolean brushDrawMode) {
        this.mBrushDrawMode = brushDrawMode;
        if (brushDrawMode) {
            this.setVisibility(View.VISIBLE);
            refreshBrushDrawing();
        }
    }


    void clearAll() {
        resultPath =null;
        mDrawnPaths.clear();
        mRedoPaths.clear();
        invalidateDependentViews();
    }
    void setBrushSize(float size) {
        mBrushSize = size;
        mDrawPaint.setStrokeWidth(mBrushSize);
        magnifier.setPointSize((size)/2);
    }

    boolean undo() {
        if (mDrawnPaths.size()>1) {
            mRedoPaths.push(mDrawnPaths.pop());
            invalidateDependentViews();
            undoRedoButtonStateChangeListener.setRedoButtonState(true);
        }
            undoRedoButtonStateChangeListener.setUndoButtonState(mDrawnPaths.size()!=1);

        return !mDrawnPaths.empty();
    }

    boolean redo() {
        if (!mRedoPaths.empty()) {
            mDrawnPaths.push(mRedoPaths.pop());
            invalidateDependentViews();
        }
        undoRedoButtonStateChangeListener.setRedoButtonState(mRedoPaths.size()>0);
        undoRedoButtonStateChangeListener.setUndoButtonState(mDrawnPaths.size()>1);
        return !mRedoPaths.empty();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);


    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(backgroundBitmap!=null) {
            fullScreenBitmap.eraseColor(Color.TRANSPARENT);
            for (LinePath linePath : mDrawnPaths) {
                maskCanvas.drawPath(linePath.getDrawPath(), linePath.getDrawPaint());
            }
            maskCanvas.drawPath(mPath, mDrawPaint);
            canvas.drawBitmap(fullScreenBitmap, 0, 0, null);
        }

    }
    public void setResultPath(Path path){
        resultPath = path;
        mDrawnPaths.push(new LinePath(resultPath, resultPaint));

    }
    /**
     * Handle touch event to draw paint on canvas i.e brush drawing
     *
     * @param event points having touch info
     * @return true if handling touch events
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        super.onTouchEvent(event);
        if (mBrushDrawMode) {
            float touchX = event.getX() ;
            float touchY = event.getY() ;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStart(touchX, touchY);
                    break;
                case MotionEvent.ACTION_MOVE:
                    touchMove(touchX, touchY);
                    break;
                case MotionEvent.ACTION_UP:
                    touchUp();
                    break;
            }
            setMagnifierDrawPath();
            magnifier.touchEvent(event);
            magnifier.draw();
            invalidateDependentViews();
            return true;
        } else {
            return false;
        }
    }


    public Path getPath() {
        return mPath;
    }


    public class LinePath {
        private Paint mDrawPaint;
        private Path mDrawPath;

        LinePath(Path drawPath, Paint drawPaints) {
            mDrawPaint = new Paint(drawPaints);
            mDrawPath = new Path(drawPath);
        }

        Paint getDrawPaint() {
            return mDrawPaint;
        }

        Path getDrawPath() {
            return mDrawPath;
        }
    }



    private void invalidateDependentViews() {
        invalidate();

    }


    private void touchStart(float x, float y) {
        mRedoPaths.clear();
        undoRedoButtonStateChangeListener.setRedoButtonState(false);
        mPath.reset();
        mPath.moveTo(x, y);
        mTouchX = x;
        mTouchY = y;

    }

    private void touchMove(float x, float y) {
        float dx = Math.abs(x - mTouchX);
        float dy = Math.abs(y - mTouchY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mTouchX, mTouchY, (x + mTouchX) / 2, (y + mTouchY) / 2);
            mTouchX = x;
            mTouchY = y;
        }
    }

    private void touchUp() {
        mPath.lineTo(mTouchX, mTouchY);
        // Commit the path to our offscreen
        // kill this so we don't double draw
        mDrawnPaths.push(new LinePath(mPath, mDrawPaint));
        undoRedoButtonStateChangeListener.setUndoButtonState(true);
        mPath = new Path();
    }
   public Uri getImageUri(){
       Bitmap bitmap  = cropImage();
       clearAll();
       try {
           File resultFile = createMaskEraserResultFile();
           FileOutputStream out = new FileOutputStream(resultFile);
           bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
           out.flush();
           out.close();
           return Uri.fromFile(resultFile);
       } catch (IOException e) {
           e.printStackTrace();
           return null;
       }
    }
    public Bitmap cropImage() {
        fullScreenBitmap.eraseColor(Color.TRANSPARENT);
        Paint transPainter = new Paint();
        transPainter.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        maskCanvas.drawRect(0, 0, fullScreenBitmap.getWidth(), fullScreenBitmap.getHeight(), transPainter);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setFilterBitmap(true);
        for (LinePath linePath : mDrawnPaths) {
            linePath.getDrawPaint().setColor(Color.BLACK);
            maskCanvas.drawPath(linePath.getDrawPath(), linePath.getDrawPaint());
        }
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        maskCanvas.drawBitmap(backgroundBitmap, 0, 0, paint);
        Bitmap croppedBitmap =
                BitmapUtil.removeTransparency(fullScreenBitmap);
        return croppedBitmap;
    }
    private File createMaskEraserResultFile() throws IOException {
        File bgFile = new File(context.getCacheDir(), "MaskEraser_image.png");
        if (!bgFile.exists())
            bgFile.createNewFile();
        return bgFile;
    }
    void setMagnifierDrawPath(){
        magnifier.DrawnPaths = mDrawnPaths;
        magnifier.newDrawPaint = mDrawPaint;
        magnifier.newPath = mPath;
    }
    public void setUndoRedoButtonStateChangeListener(MaskEraserActivity.UndoRedoButtonStateChangeListener undoRedoButtonStateChangeListener) {
        this.undoRedoButtonStateChangeListener = undoRedoButtonStateChangeListener;
    }


}