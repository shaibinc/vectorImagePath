package com.jiangpeng.android.antrace;
import java.io.File;
import java.util.ArrayList;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.Menu;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
	private static int CAMERA_STATUS_CODE = 111;
	private static int EDIT_IMAGE_CODE = 122;
	private static int SELECT_PHOTO = 100;
	private static int REQUEST_PERMISSION = 133;
	public static String FILTER_TYPE= "filter_type";
	public static String PHOTO_FILE_TEMP_ = "__antrace.jpg";
	public enum FilterType{
		AI_DRAW,
		AI_GIF,
		AI_DRAW_GIF
	}


	private FilterType filterType;

	private	GridViewImageAdapter adapter;



	private LinearLayout createGif ;
	private LinearLayout createAiGif ;
	private LinearLayout createAiDraw ;
	private RecyclerView savedProject;
	private TextView noSavedText;

	protected String m_photoFile = "";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		noSavedText = findViewById(R.id.no_saved);

		createAiDraw = (LinearLayout)findViewById(R.id.createAiDraw);
		createGif = (LinearLayout)findViewById(R.id.createGif);
		createAiGif = (LinearLayout)findViewById(R.id.createAiGif);
		savedProject = (RecyclerView) findViewById(R.id.grid_recycle_view);

		createAiDraw.setOnClickListener(v -> {
			filterType = FilterType.AI_DRAW;
			openGallery();
		});
		createGif.setOnClickListener(v -> {
			filterType = FilterType.AI_GIF;
			openGallery();

		});

		createAiGif.setOnClickListener(v -> {
			filterType = FilterType.AI_DRAW_GIF;
			openGallery();

		});


		savedProject.setLayoutManager(new GridLayoutManager(this,3));
		savedProject.setItemAnimator(new DefaultItemAnimator());
		ArrayList<File> files =    FileUtils.getFilePaths(this);
		if (files.size() != 0) {
		 adapter = new GridViewImageAdapter(this, files);
			savedProject.setAdapter(adapter);
			savedProject.setVisibility(View.VISIBLE);
			noSavedText.setVisibility(View.GONE);
		} else {
			savedProject.setVisibility(View.GONE);
			noSavedText.setVisibility(View.VISIBLE);
		}


		m_photoFile = FileUtils.getCacheDir(this) + FileUtils.sep + PHOTO_FILE_TEMP_;
		FileUtils.checkAndCreateFolder(FileUtils.getCacheDir(this));

		String svgFile = FileUtils.tempSvgFile(this);
		File file = new File(svgFile);
		file.delete();


		if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
		{
			if (!ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE))
			{
				showMessageDialog(R.string.permission_warning,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								ActivityCompat.requestPermissions(MainActivity.this,
										new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
										REQUEST_PERMISSION);
							}
						});
				return;
			}
			ActivityCompat.requestPermissions(MainActivity.this,
					new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
					REQUEST_PERMISSION);
			return;
		}
	}
	private void showMessageDialog(int str, DialogInterface.OnClickListener okListener) {
		new AlertDialog.Builder(MainActivity.this)
				.setMessage(str)
				.setPositiveButton("OK", okListener)
				.create()
				.show();
	}

	private void openGallery(){
		Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
		photoPickerIntent.setType("image/*");
		startActivityForResult(photoPickerIntent, SELECT_PHOTO);
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == REQUEST_PERMISSION)
		{
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			} else {
				showMessageDialog(R.string.permission_warning_quit,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								MainActivity.this.finish();
							}
						});
				return;
			}
		}
	}
	@Override
	protected void onResume() {
		if(adapter != null) {
			adapter.updateStickers();
		}
		super.onResume();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}


	static {
		System.loadLibrary("antrace");
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if(requestCode == CAMERA_STATUS_CODE && resultCode == RESULT_OK)
		{
			launchPreviewActivity(m_photoFile);
			super.onActivityResult(requestCode, resultCode, intent);
			return;
		}
		if(requestCode == SELECT_PHOTO && resultCode == RESULT_OK)
		{
			Uri selectedImage = intent.getData();
			String[] filePathColumn = {MediaStore.Images.Media.DATA};

			Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
			if(cursor == null)
			{
				super.onActivityResult(requestCode, resultCode, intent);
				return;
			}
			cursor.moveToFirst();

			int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
			String filePath = cursor.getString(columnIndex);
			cursor.close();

			launchPreviewActivity(filePath);
		}
		super.onActivityResult(requestCode, resultCode, intent);
	}

	protected void launchPreviewActivity(String filename) {
		Intent i = new Intent();
		i.setClass(MainActivity.this, PreviewActivity.class);
		i.putExtra(FILTER_TYPE, filterType);
		i.putExtra(PreviewActivity.FILENAME, filename);
		startActivityForResult(i, EDIT_IMAGE_CODE);

	}

}
