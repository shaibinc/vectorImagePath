package com.jiangpeng.android.antrace.autobgremover;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.util.Stack;

public class Magnifier extends ImageView {
    private PointF zoomPos;
    private boolean zooming = false;
    private Paint paint;
    private Bitmap originBitmap;
    private int bitmapHeight = 5;
    private int bitmapWidth = 5;
    public Stack<MaskEraser.LinePath> DrawnPaths = new Stack<>() ;
    public Path newPath = new Path();
    public Paint newDrawPaint = new Paint();
    //RADIUS_TO_MAGNIFY have a relation to brush size
    private static float RADIUS_TO_MAGNIFY = 100;
    Canvas  maskCanvas;
    Canvas canvas;
    Bitmap maskBitmap;
    Bitmap circleBitmap;
    int magnify = 5;
    int size = (int) (RADIUS_TO_MAGNIFY*2*magnify);
    Path path;
    RectF rect;


    public void setPointSize(float mBrushSize) {
        this.mBrushSize = mBrushSize;
    }

    public void setDisplayWidth(int displayWidth) {
        this.displayWidth = displayWidth;
    }

    public int displayWidth;
    public void onChangeBrushSize(float mBrushSize){
        this.mBrushSize = mBrushSize;
        zoomPos.y = bitmapHeight/2;
        zoomPos.x = bitmapWidth/2;
        zooming = true;
        if (circleBitmap != null)
            draw();
    }
    public void setBitmap(Bitmap bitmap) {
        originBitmap = bitmap;
        bitmapWidth = bitmap.getWidth();
        bitmapHeight = bitmap.getHeight();
        circleBitmap = Bitmap.createBitmap( size ,size, Bitmap.Config.ARGB_8888);
        maskBitmap = Bitmap.createBitmap( bitmapWidth ,bitmapHeight, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(circleBitmap);
        maskCanvas = new Canvas(maskBitmap);
        path = new Path();
        rect = new RectF(0, 0, size, size);
        float radius = size/2;
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    private float mBrushSize = 25;
    public Magnifier(Context context) {
        super(context);
        init();
    }

    public Magnifier(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public Magnifier(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        zoomPos = new PointF(0, 0);
        paint = new Paint();
    }
    public boolean touchEvent(MotionEvent event) {
        int action = event.getAction();
        zoomPos.x = event.getX();
        zoomPos.y = event.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                zooming = true;
                this.invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                zooming = false;
                this.invalidate();
                break;

            default:
                break;
        }

        return true;
    }


    public void draw() {


        circleBitmap.eraseColor(Color.TRANSPARENT);
        maskBitmap.eraseColor(Color.TRANSPARENT);

        if (!zooming) {
            setVisibility(GONE);
        } else {

            setVisibility(VISIBLE);


            for (MaskEraser.LinePath linePath : DrawnPaths) {
                maskCanvas.drawPath(linePath.getDrawPath(), linePath.getDrawPaint());
            }
            maskCanvas.drawPath(newPath, newDrawPaint);


            canvas.clipPath(path);
            RectF areaToCut = new RectF(zoomPos.x-RADIUS_TO_MAGNIFY,zoomPos.y-RADIUS_TO_MAGNIFY,zoomPos.x+RADIUS_TO_MAGNIFY,zoomPos.y+RADIUS_TO_MAGNIFY);
            Rect rounded = new Rect();
            areaToCut.round(rounded);
            canvas.drawBitmap(originBitmap, rounded,rect,null);
            canvas.drawBitmap(maskBitmap,rounded,rect,null);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setShadowLayer(7*magnify ,0,0,Color.BLACK );
            paint.setStrokeWidth(6*magnify);
            canvas.drawCircle(size/2, size/2, mBrushSize*magnify, paint);
            if(zoomPos.x>(originBitmap.getWidth()/2))
                setTranslationX(0);
            else {
                View parent = (View) getParent();

                setTranslationX(parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight()-getWidth());
            }
            setImageBitmap(circleBitmap);
        }
    }


}
