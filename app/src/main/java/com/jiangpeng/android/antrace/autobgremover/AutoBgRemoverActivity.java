package com.jiangpeng.android.antrace.autobgremover;


import static com.jiangpeng.android.antrace.autobgremover.MaskEraserActivity.EXTRA_AUTO_CROP_IMAGE_URI;
import static com.jiangpeng.android.antrace.autobgremover.MaskEraserActivity.EXTRA_AUTO_CROP_NO_FACE;
import static com.jiangpeng.android.antrace.autobgremover.MaskEraserActivity.EXTRA_MASK_ERASER_IMAGE_MASK_URI;
import static com.jiangpeng.android.antrace.autobgremover.MaskEraserActivity.EXTRA_MASK_ERASER_IMAGE_URI;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.jiangpeng.android.antrace.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class AutoBgRemoverActivity extends AppCompatActivity {

    public static final int MASK_ERASER_REQUEST_CODE = 76;
    SegmentBitmapsLoader segmentBitmapsLoader;
    Uri imageUri;
    ImageView imageView;
    Uri previewUri;
    AutoBgRemoverAsync bgRemoverAsync;
    AutoBgMaskAsync autoBgMaskAsync;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_bg_remover);
        imageUri = getIntent().getParcelableExtra(EXTRA_AUTO_CROP_IMAGE_URI);
        imageView = findViewById(R.id.cropImageView);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        segmentBitmapsLoader = new SegmentBitmapsLoader(AutoBgRemoverActivity.this);
        segmentBitmapsLoader.execute();
        bgRemoverAsync = new AutoBgRemoverAsync();
        bgRemoverAsync.init();
        bgRemoverAsync.execute();
        findViewById(R.id.crop_ok_tick).setOnClickListener(v -> {
            Uri autoBgResultUri = null;
            try {
                autoBgResultUri = Uri.fromFile( createAutoBgMaskFile());
                copyFile(previewUri,autoBgResultUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Intent intent = new Intent();
            intent.putExtra(EXTRA_AUTO_CROP_IMAGE_URI ,autoBgResultUri);
            setResult(RESULT_OK,intent);
            finish();
        });
        findViewById(R.id.cancel_crop).setOnClickListener(v -> {
            finish();
        });
        findViewById(R.id.adjust_button).setOnClickListener(v ->{
            autoBgMaskAsync = new AutoBgMaskAsync();
            autoBgMaskAsync.init();
            autoBgMaskAsync.execute();
        } );

    }
    private class AutoBgRemoverAsync extends AsyncTask<Void, Void, Uri> {

        ProgressDialog mDialog;

        public void init() {
            mDialog = ProgressDialog.show(AutoBgRemoverActivity.this, "",
                   "please wait", true, false);

        }

        @Override
        protected Uri doInBackground(Void... v) {
            Log.d("TF", "Auto Bg button clicked");
            if (segmentBitmapsLoader.isInitialized) {
                return segmentBitmapsLoader.loadInBackground(imageUri);
            } else {
                return null;
            }
        }

        @Override
        protected void onCancelled() {
            segmentBitmapsLoader.closeTF();
            super.onCancelled();
        }


        @Override
        protected void onPostExecute(Uri uri) {
            super.onPostExecute(uri);
            if(uri!=null){
                previewUri = uri;
                imageView.setImageURI(uri);
                Log.d("TF", "Auto Bg result ");
            } else {
                setResultCancel();
            }

            if (mDialog.isShowing())
                mDialog.dismiss();
        }
    }
    private class AutoBgMaskAsync extends AsyncTask<Void, Void, Uri> {

        ProgressDialog mDialog;

        public void init() {
            mDialog = ProgressDialog.show(AutoBgRemoverActivity.this, "","progressing..", true, false);

        }

        @Override
        protected Uri doInBackground(Void... v) {
            Log.d("TF", "Auto Bg button clicked");
            if (segmentBitmapsLoader.isInitialized) {
                return segmentBitmapsLoader.loadInBackgroundForMask(imageUri);
            }
            else {
                return null;
            }
        }


        @Override
        protected void onCancelled() {
            segmentBitmapsLoader.closeTF();
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(Uri uri) {
            super.onPostExecute(uri);
            if (uri != null) {
                Intent intent1 = new Intent(AutoBgRemoverActivity.this, MaskEraserActivity.class);
                intent1.putExtra(EXTRA_MASK_ERASER_IMAGE_MASK_URI,uri);
                intent1.putExtra(EXTRA_MASK_ERASER_IMAGE_URI, imageUri);

                startActivityForResult(intent1, MASK_ERASER_REQUEST_CODE);
            }
            if (mDialog.isShowing())
                mDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        boolean segmentBitmapsLoaderCancelled = segmentBitmapsLoader.cancel(true);
        boolean bgRemoverAsyncCancelled = bgRemoverAsync.cancel(true);
        boolean autoBgMaskAsyncCancelled = autoBgMaskAsync != null && autoBgMaskAsync.cancel(true);
        if (!segmentBitmapsLoaderCancelled
                && !bgRemoverAsyncCancelled
                && !autoBgMaskAsyncCancelled) {
            segmentBitmapsLoader.closeTF();
        }
        super.onDestroy();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MASK_ERASER_REQUEST_CODE) {
            switch (resultCode) {
                case RESULT_OK:
                    Intent intent = new Intent();
                    intent.putExtra(EXTRA_AUTO_CROP_IMAGE_URI, (Uri) data.getParcelableExtra(EXTRA_MASK_ERASER_IMAGE_URI));
                    setResult(RESULT_OK, intent);
                    finish();
                    break;
            }
        }
    }
    protected void setResultCancel() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_AUTO_CROP_NO_FACE ,true);
        setResult(RESULT_CANCELED,intent);
        finish();
    }
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
    private void copyFile(Uri pathFrom, Uri pathTo) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(pathFrom)) {
            if(in == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(pathTo)) {
                if(out == null) return;
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }
    private File createAutoBgMaskFile() throws IOException {
        File bgFile = new File(getCacheDir(), "AutoBg_Result.png");
        if (!bgFile.exists())
            bgFile.createNewFile();
        return bgFile;
    }
}
