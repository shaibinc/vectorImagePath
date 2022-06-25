package com.jiangpeng.android.antrace.autobgremover;


import static com.jiangpeng.android.antrace.autobgremover.SegmentBitmapsLoader.getBitmapFromUri;
import static com.jiangpeng.android.antrace.autobgremover.SegmentBitmapsLoader.getSegmentationPath;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import androidx.appcompat.app.AppCompatActivity;

import com.jiangpeng.android.antrace.R;

import java.io.IOException;

public class MaskEraserActivity extends AppCompatActivity {
    public static final String EXTRA_AUTO_CROP_IMAGE_URI = "auto_crop_image_uri";
    public static final String EXTRA_AUTO_CROP_NO_FACE = "auto_crop_no_face_found";
    public static final String EXTRA_MASK_ERASER_IMAGE_MASK_URI = "mask_eraser_path";
    public static final String EXTRA_MASK_ERASER_IMAGE_URI = "mask_eraser_bitmap";
    private SeekBar sbBrushSize;
    MaskEraser maskEraser;
    private static int BRUSHSIZE_MIN = 20;
    private LinearLayout undoButton, redoButton;

    private Bitmap maskBitmap = null;
    private Bitmap originBitmap = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mask_eraser);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        undoButton = findViewById(R.id.imgUndo);
        redoButton = findViewById(R.id.imgRedo);
        undoButton.setOnClickListener(v -> maskEraser.undo());
        undoButton.setAlpha((float) 0.5);
        redoButton.setOnClickListener(v -> maskEraser.redo());
        redoButton.setAlpha((float) 0.5);
        findViewById(R.id.restore_active).setBackgroundColor(Color.GRAY);
        findViewById(R.id.erase_active).setBackgroundColor(Color.TRANSPARENT);
        Magnifier magnifier = findViewById(R.id.mask_magnifier);
        maskEraser = findViewById(R.id.mask_eraser);
        maskEraser.setMagnifier(magnifier);
        sbBrushSize = findViewById(R.id.brushSizeSeekBar);
        maskEraser.setBrushSize((BRUSHSIZE_MIN+sbBrushSize.getProgress()));
        UndoRedoButtonStateChangeListener undoRedoListener = new UndoRedoButtonStateChangeListener() {
            @Override
            public void setUndoButtonState(boolean show) {
                setButtonState(undoButton,show);

            }

            @Override
            public void setRedoButtonState(boolean show) {
                setButtonState(redoButton,show);

            }
        };
        maskEraser.setUndoRedoButtonStateChangeListener(undoRedoListener);


        sbBrushSize.setMax(60);
        sbBrushSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progress+= BRUSHSIZE_MIN;
                maskEraser.setBrushSize(progress);
                magnifier.onChangeBrushSize(progress/2);
            }


            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                magnifier.setVisibility(View.VISIBLE);
                magnifier.onChangeBrushSize((BRUSHSIZE_MIN+seekBar.getProgress())/2);

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                magnifier.setVisibility(View.GONE);

            }
        });

        findViewById(R.id.crop_restore).setOnClickListener(v -> {
            maskEraser.setBrushDrawingMode(true);
            findViewById(R.id.restore_active).setBackgroundColor(Color
            .GRAY);
            findViewById(R.id.erase_active).setBackgroundColor(Color.TRANSPARENT);
        });
        findViewById(R.id.crop_erase).setOnClickListener(v -> {
            maskEraser.brushEraser();
            findViewById(R.id.restore_active).setBackgroundColor(Color.TRANSPARENT);
            findViewById(R.id.erase_active).setBackgroundColor(Color.GRAY);
        });
        findViewById(R.id.cancel_crop).setOnClickListener(v -> {
            onBackPressed();
        });
        magnifier.setDisplayWidth( getWindowManager().getDefaultDisplay().getWidth());
        findViewById(R.id.crop_ok_tick).setOnClickListener(v -> {
            Uri imageUri = maskEraser. getImageUri();
            Intent intent = new Intent();
            intent.putExtra(EXTRA_MASK_ERASER_IMAGE_URI ,imageUri );
            setResult(RESULT_OK,intent);
            finish();
        });

        try {
             maskBitmap= getBitmapFromUri(this,getIntent().getParcelableExtra(EXTRA_MASK_ERASER_IMAGE_MASK_URI));
             originBitmap= getBitmapFromUri(this,getIntent().getParcelableExtra(EXTRA_MASK_ERASER_IMAGE_URI));
        } catch (IOException e) {
            e.printStackTrace();
        }


        maskEraser.setImageBitmap(originBitmap);
        SegmentationPathAsync segmentationPathAsync = new SegmentationPathAsync();
        segmentationPathAsync.init();
        maskEraser.getViewTreeObserver().addOnGlobalLayoutListener (new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    maskEraser.getViewTreeObserver()
                            .removeOnGlobalLayoutListener(this);
                } else {
                    maskEraser.getViewTreeObserver()
                            .removeGlobalOnLayoutListener(this);
                }
                segmentationPathAsync.execute();
            }
        });

    }
    private class SegmentationPathAsync extends AsyncTask<Void,Void,Path>{
        ProgressDialog mDialog;

        public void init() {
            mDialog = ProgressDialog.show(MaskEraserActivity.this, "","processing", true, false);

        }
        @Override
        protected Path doInBackground(Void... voids) {
            int [] i = getBitmapPositionInsideImageView(maskEraser);
           maskBitmap = ImageUtils.tfResizeBilinear(maskBitmap, i[2] , i[3]);
           originBitmap = ImageUtils.tfResizeBilinear(originBitmap, i[2] , i[3]);
           return getSegmentationPath(originBitmap, maskBitmap);
        }

        @Override
        protected void onPostExecute(Path path) {
            super.onPostExecute(path);

                maskEraser.setBitmap(originBitmap);
                maskEraser.getLayoutParams().width = originBitmap.getWidth();
                maskEraser.getLayoutParams().height = originBitmap.getHeight();
                maskEraser.setResultPath(path);
                maskEraser.requestLayout();
            if (mDialog.isShowing())
                mDialog.dismiss();
        }
    }
    public static int[] getBitmapPositionInsideImageView(ImageView imageView) {
        int[] ret = new int[4];

        if (imageView == null || imageView.getDrawable() == null)
            return ret;

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int actW = Math.round(origW * scaleX);
        final int actH = Math.round(origH * scaleY);

        ret[2] = actW;
        ret[3] = actH;

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (int) (imgViewH - actH)/2;
        int left = (int) (imgViewW - actW)/2;

        ret[0] = left;
        ret[1] = top;

        return ret;
    }
    void setButtonState(LinearLayout button,boolean enabled){
        button.setClickable(true);
        button.setAlpha(enabled?1:.5f);
    }


    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
    public interface UndoRedoButtonStateChangeListener {
        void setUndoButtonState(boolean show);
        void setRedoButtonState(boolean show);
    }
}
