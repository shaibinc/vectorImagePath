package com.jiangpeng.android.antrace;

import static com.jiangpeng.android.antrace.MainActivity.FILTER_TYPE;
import static com.jiangpeng.android.antrace.PreviewActivity.states.STATE_EDITED;
import static com.jiangpeng.android.antrace.PreviewActivity.states.STATE_LOADED;
import static com.jiangpeng.android.antrace.PreviewActivity.states.STATE_SAVE;
import static com.jiangpeng.android.antrace.PreviewActivity.states.STATE_START;
import static com.jiangpeng.android.antrace.autobgremover.SegmentBitmapsLoader.getSegmentationPath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.jiangpeng.android.antrace.Objects.PerspectiveInteraction;
import com.jiangpeng.android.antrace.Objects.RegularInteraction;
import com.jiangpeng.android.antrace.Objects.path;
import com.waynejo.androidndkgif.GifEncoder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

public class PreviewActivity extends Activity {
    private String m_filename = null;
    private ProgressDialog m_progress = null;
    private PreviewImageView m_imageView = null;

    private Bitmap grayBitmap = null;
    private Bitmap monoBitmap = null;
    private Button m_ok = null;
    private Button nextButton = null;
    private BitmapFactory.Options bitmapOption = null;
    private Thread mThread = null;
    private Path[] paths = new Path[50];

    private int m_processedThreshold = -1;
    private int currentThreshold = 127;
    private ProgressBar m_progressBar = null;
    public static int CODE_SHARE_IMAGE = 1115;
    public static String FILENAME = "FILENAME";
    public static String FILE_DIR = "saved";
    private MainActivity.FilterType filterType;

    public enum states {
        STATE_START,
        STATE_LOADED,
        STATE_EDITED,
        STATE_SAVE,
        STATE_SHARE
    }

    private states currentState = STATE_START;
    private boolean m_isCropping = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.edit_image);

        m_imageView = (PreviewImageView) findViewById(R.id.previewImageView);
        m_imageView.setInteraction(new RegularInteraction(m_imageView));
        m_imageView.init();
        m_ok = (Button) findViewById(R.id.okButton);
        nextButton = (Button) findViewById(R.id.cancelButton);
        m_progressBar = (ProgressBar) findViewById(R.id.thresholdProgress);

        OnClickListener okListener = new OkListener();
        m_ok.setOnClickListener(okListener);

        OnClickListener cancelListener = new NextListener();
        nextButton.setOnClickListener(cancelListener);

        m_progressBar.setVisibility(View.INVISIBLE);


        m_filename = this.getIntent().getStringExtra(PreviewActivity.FILENAME);
        filterType = (MainActivity.FilterType) getIntent().getSerializableExtra(FILTER_TYPE);

        m_progress = ProgressDialog.show(this, getResources().getString(R.string.empty), getResources().getString(R.string.loading), true);
        Thread t = new Thread(new LoadImageThread());
        t.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && requestCode == CODE_SHARE_IMAGE) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    class OkListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (currentState == STATE_LOADED) {
                if (m_isCropping) {
                    m_ok.setText(R.string.edit);
                    nextButton.setText(R.string.next);
                    m_imageView.endCrop();
                    m_isCropping = false;
                    return;
                }
                showEditDialog();
                return;
            }
            finish();
        }
    }

    ;

    class NextListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (currentState == STATE_LOADED) {
                m_progress = ProgressDialog.show(PreviewActivity.this, getResources().getString(R.string.empty), getResources().getString(R.string.loading), true);
                if (m_isCropping) {
                    Thread t = new Thread(new CropThread());
                    m_ok.setText(R.string.edit);
                    nextButton.setText(R.string.next);
                    m_imageView.endCrop();
                    t.start();
                    m_isCropping = false;
                } else {
                    m_progressBar.setVisibility(View.VISIBLE);
                    GrayscaleThread();
                    m_ok.setText(R.string.quit);
                }
                return;
            }
            if (currentState == STATE_EDITED) {
                monoBitmap = Bitmap.createBitmap(grayBitmap.getWidth(), grayBitmap.getHeight(), Bitmap.Config.ARGB_8888);
                ThresholdThread();
                return;
            }
            if (currentState == STATE_SAVE) {
               finish();
            }
        }
    }

    ;


    void ThresholdThread() {
        File file = null;
        if (filterType == MainActivity.FilterType.AI_GIF) {
            file = makeGif();
        } else if (filterType == MainActivity.FilterType.AI_DRAW) {
            file = makeAiDraw();
        }
        if (filterType == MainActivity.FilterType.AI_DRAW_GIF) {
            file = makeAiDGif();
        }
        Glide.with(m_imageView).load(file).centerInside().diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true).into(m_imageView);
        m_progressBar.setVisibility(View.INVISIBLE);
        m_progress.dismiss();
        currentState = STATE_SAVE;
    }


    private File makeGif() {
        GifEncoder gifEncoder = new GifEncoder();
        String id = UUID.randomUUID().toString().substring(0, 7);
        File parent = new File(getFilesDir(), FILE_DIR);
        if (!parent.exists())
            parent.mkdirs();
        File file = new File(parent, id + ".gif");
        try {
            gifEncoder.init(monoBitmap.getWidth(), monoBitmap.getHeight(), file.getPath(), GifEncoder.EncodingType.ENCODING_TYPE_STABLE_HIGH_MEMORY);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        Bitmap bitmap = Bitmap.createBitmap(monoBitmap.getWidth(), monoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        float threshold = 2;
        int alpha = 255;
        for (int i = 0; i < 20; i++) {
            Utils.threshold(grayBitmap, (int) threshold, monoBitmap);
            threshold += 12.1;
//			Utils.unsharpMask(monoBitmap, bitmap);
            gifEncoder.encodeFrame(monoBitmap, 200);
        }
        gifEncoder.close();
        return file;
    }


    private File makePencilDraw() {
            Utils.threshold(grayBitmap, 120, monoBitmap);
        String id = UUID.randomUUID().toString().substring(0, 7);
        File parent = new File(getFilesDir(), FILE_DIR);
        if (!parent.exists())
            parent.mkdirs();
        File file = new File(parent, id + ".png");
        try {
            file.createNewFile();
            FileOutputStream out = new FileOutputStream(file);
            monoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }


        private File makeAiDraw() {

        Bitmap bitmap2 = Bitmap.createBitmap(monoBitmap.getWidth(), monoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap2);
        Bitmap bitmap = Bitmap.createBitmap(monoBitmap.getWidth(), monoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        int threshold = 0;
        int alpha = 0;
        for (int i = 0; i < 10; i++) {
            Utils.threshold(grayBitmap, threshold, monoBitmap);
            threshold += 20;
//				Utils.unsharpMask(monoBitmap, monoBitmap);

//			 path trace=Utils.traceImage(monoBitmap);
//
//			 paths[i] = trace.p


// Bitmap is MUST ARGB_8888.


            Paint paint = new Paint();
            paint.setAlpha(alpha);
            ;
            canvas.drawBitmap(monoBitmap, 0, 0, paint);
            alpha = alpha + 10;


            paths[i] = getSegmentationPath(monoBitmap, monoBitmap);
            File file = new File(getFilesDir() + String.valueOf(i) + ".svg");
            Utils.saveSVG(file.getPath(), monoBitmap.getWidth(), monoBitmap.getHeight());
        }


//		Bitmap backingBitmap = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(), Bitmap.Config.ARGB_8888);
//		backingBitmap.eraseColor(Color.WHITE);
        Canvas canvas2 = new Canvas();
        Paint paintWhite = new Paint();
        paintWhite.setAntiAlias(true);
        paintWhite.setStrokeWidth(1);
        paintWhite.setStyle(Paint.Style.STROKE);
        paintWhite.setPathEffect(new CornerPathEffect(0));
        canvas2.setBitmap(bitmap2);


//		 alpha = 0;
        if (paths != null)
            for (int i = 0; i < paths.length; i++) {
                if (paths[i] != null) {
                    paintWhite.setColor(Color.BLACK);
                    canvas2.drawPath(paths[i], paintWhite);

                }
            }


        String id = UUID.randomUUID().toString().substring(0, 7);
        File parent = new File(getFilesDir(), FILE_DIR);
        if (!parent.exists())
            parent.mkdirs();
        File file = new File(parent, id + ".png");
        try {
            file.createNewFile();
            FileOutputStream out = new FileOutputStream(file);
            bitmap2.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }


    private File makeAiDGif() {
        GifEncoder gifEncoder = new GifEncoder();
        String id = UUID.randomUUID().toString().substring(0, 7);
        File parent = new File(getFilesDir(), FILE_DIR);
        if (!parent.exists())
            parent.mkdirs();
        File file = new File(parent, id + ".gif");
        try {
            gifEncoder.init(monoBitmap.getWidth(), monoBitmap.getHeight(), file.getPath(), GifEncoder.EncodingType.ENCODING_TYPE_STABLE_HIGH_MEMORY);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        Bitmap bitmap2 = Bitmap.createBitmap(monoBitmap.getWidth(), monoBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap2);
        int threshold = 0;
        int alpha = 0;
        for (int i = 0; i < 10; i++) {
            Utils.threshold(grayBitmap, threshold, monoBitmap);
            threshold += 20;
//				Utils.unsharpMask(monoBitmap, monoBitmap);

//			 path trace=Utils.traceImage(monoBitmap);
//
//			 paths[i] = trace.p


// Bitmap is MUST ARGB_8888.


            Paint paint = new Paint();
            paint.setAlpha(alpha);
            ;
            canvas.drawBitmap(monoBitmap, 0, 0, paint);
            alpha = alpha + 10;


            gifEncoder.encodeFrame(bitmap2, 200);

            paths[i] = getSegmentationPath(monoBitmap, monoBitmap);
        }


//		Bitmap backingBitmap = Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(), Bitmap.Config.ARGB_8888);
//		backingBitmap.eraseColor(Color.WHITE);
        Canvas canvas2 = new Canvas();
        Paint paintWhite = new Paint();
        paintWhite.setAntiAlias(true);
        paintWhite.setStrokeWidth(1);
        paintWhite.setStyle(Paint.Style.STROKE);
        paintWhite.setPathEffect(new CornerPathEffect(0));
        canvas2.setBitmap(bitmap2);


//		 alpha = 0;
        if (paths != null)
            for (int i = 0; i < paths.length; i++) {
                if (paths[i] != null) {
                    paintWhite.setColor(Color.BLACK);
                    canvas2.drawPath(paths[i], paintWhite);

                    gifEncoder.encodeFrame(bitmap2, 200);

                }
            }

        gifEncoder.close();

        return file;
    }



    private Handler stateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (m_progress != null) {
                m_progress.dismiss();
                m_progress = null;
            }

            Bitmap bmp = (Bitmap) msg.obj;
            if (bmp == null) {
                Toast toast = Toast.makeText(PreviewActivity.this, R.string.check_your_files, Toast.LENGTH_SHORT);
                toast.show();
                PreviewActivity.this.finish();
                return;
            }
            if (bmp.isRecycled()) {
                Toast toast = Toast.makeText(PreviewActivity.this, R.string.check_your_files, Toast.LENGTH_SHORT);
                toast.show();
                PreviewActivity.this.finish();
            } else {
                m_imageView.setImage(bmp);
                if (currentState == STATE_START) {
                    currentState = STATE_LOADED;
                    m_ok.setText(R.string.edit);
                    nextButton.setText(R.string.next);
                    grayBitmap = bmp;
                    return;
                }
                if (currentState == STATE_LOADED) {
                    m_imageView.endCrop();
                    grayBitmap = bmp;
                    nextButton.setText(R.string.next);
//            		m_state = STATE_EDITED;
                    return;
                }
            }
        }
    };

    /*
    private void endCrop()
    {
		m_ok.setText(R.string.next);
		m_cancel.setText(R.string.quit);
		m_state = STATE_CROPPED;
		m_imageView.endCrop();
    }
    */

    void GrayscaleThread() {
        if (grayBitmap != null) {
            Bitmap bm = grayBitmap;
            try {
                grayBitmap = Bitmap.createBitmap(bm);
                Utils.grayScale(bm, grayBitmap);

                saveToFile(grayBitmap, getFilesDir() + FileUtils.sep + "gray.png");
                Utils.unsharpMask(grayBitmap, bm);
                grayBitmap = bm;
                saveToFile(grayBitmap, getFilesDir() + FileUtils.sep + "after.png");

            } catch (OutOfMemoryError err) {
            }
            monoBitmap = Bitmap.createBitmap(grayBitmap.getWidth(), grayBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            ThresholdThread();
        }
    }

    ;

    class saveImage implements Runnable {
        @Override
        public void run() {
            if (grayBitmap != null) {
                path p = Utils.traceImage(monoBitmap);
                String svgFile = FileUtils.tempSvgFile(PreviewActivity.this);
                if (!Utils.saveSVG(svgFile, monoBitmap.getWidth(), monoBitmap.getHeight())) {
                    Message msg = stateHandler.obtainMessage(0, null);
                    stateHandler.sendMessage(msg);
                    return;
                }
                File file = new File(svgFile);
                if (!file.exists()) {
                    Message msg = stateHandler.obtainMessage(0, null);
                    stateHandler.sendMessage(msg);
                    return;
                }
                Message msg = stateHandler.obtainMessage(0, p);
                stateHandler.sendMessage(msg);
            }
        }
    }

    ;

    float distance(PointF p1, PointF p2) {
        return (float) Math.sqrt(((p2.y - p1.y) * (p2.y - p1.y) + (p2.x - p1.x) * (p2.x - p1.x)));
    }

    class CropThread implements Runnable {
        @Override
        public void run() {
            if (grayBitmap != null) {
                Bitmap croppedBitmap = m_imageView.getInteraction().getCroppedBitmap();
                Message msg = stateHandler.obtainMessage(0, croppedBitmap);
                stateHandler.sendMessage(msg);
            }
        }
    }

    ;

    class LoadImageThread implements Runnable {
        @Override
        public void run() {
            if (m_filename == null || m_filename.length() == 0) {
                Message msg = stateHandler.obtainMessage(0, null);
                stateHandler.sendMessage(msg);
                return;
            }
            bitmapOption = ImageUtils.getBmpOptions(m_filename);
//			m_originalSize = new Point(m_ops.outWidth, m_ops.outHeight);
//            Bitmap ret = ImageUtils.getFullScreenImageFromFilename(EditImageActivity.this, m_filename);
            bitmapOption.inSampleSize = ImageUtils.computeInitialSampleSize(bitmapOption, -1, 512 * 1024);
            bitmapOption.inPreferredConfig = Bitmap.Config.ARGB_8888;
            FileInputStream stream;
            try {
                stream = new FileInputStream(m_filename);
            } catch (FileNotFoundException e) {
                Message msg = stateHandler.obtainMessage(0, null);
                stateHandler.sendMessage(msg);
                return;
            }
            Bitmap loadedBitmap = null;
            try {
                bitmapOption.inJustDecodeBounds = false;
                loadedBitmap = BitmapFactory.decodeStream(stream, null, bitmapOption);
            } catch (OutOfMemoryError err) {
            }

            if (loadedBitmap == null || loadedBitmap.getWidth() < 2 || loadedBitmap.getHeight() < 2) {
                Message msg = stateHandler.obtainMessage(0, null);
                stateHandler.sendMessage(msg);
                return;
            }

            float a = FileUtils.getPhotoAngle(m_filename);
            Matrix m = new Matrix();
            m.reset();
            m.postRotate(a);
            try {
                Bitmap ret = Bitmap.createBitmap(loadedBitmap, 0, 0, loadedBitmap.getWidth(), loadedBitmap.getHeight(), m, true);
                if (ret != loadedBitmap) {
                    loadedBitmap.recycle();
                    loadedBitmap = ret;
                }
            } catch (OutOfMemoryError e) {
                loadedBitmap = null;
            }

            Message msg = stateHandler.obtainMessage(0, loadedBitmap);
            stateHandler.sendMessage(msg);
        }
    }

    @Override
    protected void onDestroy() {
        if (m_progress != null) {
            if (m_progress.isShowing()) {
                m_progress.dismiss();
            }
            m_progress = null;
        }
        super.onDestroy();
        Utils.clearState();
    }


    private Uri saveToFile(Bitmap bmp, String tempfile) {
        FileOutputStream out;
        try {
            out = new FileOutputStream(tempfile);
        } catch (FileNotFoundException e) {
            return null;
        }
        if (!bmp.compress(Bitmap.CompressFormat.PNG, 90, out)) {
            return null;
        }
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    class RotateThread implements Runnable {
        private float m_angle = 0.0f;

        public RotateThread(float a) {
            m_angle = a;
        }

        @Override
        public void run() {
            Bitmap bm = null;
            int result = 1;
            Matrix m = new Matrix();
            m.reset();
            m.postRotate((float) (m_angle / Math.PI * 180));
            try {
                Bitmap ret = Bitmap.createBitmap(grayBitmap, 0, 0, grayBitmap.getWidth(), grayBitmap.getHeight(), m, true);
                bm = ret;
            } catch (OutOfMemoryError e) {
                bm = null;
            }

            Message msg = stateHandler.obtainMessage(result, bm);
            stateHandler.sendMessage(msg);
        }
    }

    protected void showEditDialog() {
        final String[] shorts = new String[4];

        shorts[0] = getResources().getString(R.string.rotate_left);
        shorts[1] = getResources().getString(R.string.rotate_right);
        shorts[2] = getResources().getString(R.string.regular_crop);
        shorts[3] = getResources().getString(R.string.perspective_crop);

        final AlertDialog.Builder ad = new AlertDialog.Builder(this);
        ad.setTitle(R.string.please_select);
        // define the alert dialog with the choices and the action to take
        // when one of the choices is selected
        ad.setSingleChoiceItems(shorts, 0, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                float a = 0;
                if (which == 2) {
                    m_imageView.setInteraction(new RegularInteraction(m_imageView));
                    m_imageView.setImage(grayBitmap);
                    m_imageView.startCrop();
                    m_ok.setText(android.R.string.cancel);
                    nextButton.setText(R.string.crop);
                    m_isCropping = true;
                    dialog.dismiss();
                    return;
                }

                if (which == 3) {
                    m_imageView.setInteraction(new PerspectiveInteraction(m_imageView));
                    m_imageView.setImage(grayBitmap);
                    m_imageView.startCrop();
                    m_ok.setText(android.R.string.cancel);
                    nextButton.setText(R.string.crop);
                    m_isCropping = true;
                    dialog.dismiss();
                    return;
                }
                m_progress = ProgressDialog.show(PreviewActivity.this, "", getString(R.string.loading));
                if (which == 0) {
                    a = (float) (Math.PI / 2.0);
                } else if (which == 1) {
                    a = (float) (Math.PI * 3.0 / 2.0);
                }
                Thread t = new Thread(new RotateThread(a));
                t.start();
                dialog.dismiss();
            }
        });
        AlertDialog dlg = ad.create();
        dlg.show();
    }
}
