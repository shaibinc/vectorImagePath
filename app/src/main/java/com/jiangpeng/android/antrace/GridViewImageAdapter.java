package com.jiangpeng.android.antrace;



import static com.jiangpeng.android.antrace.PreviewActivity.FILE_DIR;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class GridViewImageAdapter extends  RecyclerView.Adapter<GridViewImageAdapter.GridItemViewHolder>{

	private Context context;
	private ArrayList<File> filePaths = new ArrayList<File>();

	public GridViewImageAdapter(Context context, ArrayList<File> filePaths) {
		this.context = context;
		this.filePaths = filePaths;
	}

	@NonNull
	@Override
	public GridItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(context).inflate(R.layout.imageitem, parent, false);
		GridItemViewHolder holder = new GridItemViewHolder(view);
		return holder;
	}

	@Override
	public void onBindViewHolder(@NonNull GridItemViewHolder holder, int position) {
		if(filePaths.get(position).equals("add")){
			holder.imageView.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_add));
			holder.share.setVisibility(View.GONE);
			holder.delete.setVisibility(View.GONE);
		}
		else {

//				InputStream istr =new FileInputStream( new File(context.getFilesDir()+"/"+FILE_DIR+"/" + filePaths.get(position)));

				Glide.with(holder.imageView).load(filePaths.get(position))
						.centerInside().diskCacheStrategy(DiskCacheStrategy.NONE)
						.skipMemoryCache(true).into(	holder.imageView);
//				holder.imageView.setImageDrawable(Drawable.createFromStream(istr, null));

		}


		holder.share.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent i =  new Intent(Intent.ACTION_SEND_MULTIPLE);
				i.setType("*/*");
				ArrayList<Uri> files = new ArrayList<>();
				files.add( FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID, new File(context.getFilesDir()+"/"+FILE_DIR+"/" + filePaths.get(position))));
				i.putParcelableArrayListExtra(Intent.EXTRA_STREAM,  files);
				i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
				try {
					context.startActivity(i);

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

		holder.delete.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				new AlertDialog.Builder(context)
						.setTitle("Do want you to delete?")
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setCancelable(false)
						.setPositiveButton("YES", new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								File imgfile = new File(context.getFilesDir()+"/"+FILE_DIR+"/" + filePaths.get(position));
								filePaths.remove(position);
								imgfile.delete();
								notifyDataSetChanged();
							}})
						.setNegativeButton("NO", null).show();

			}
		});

	}


	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public int getItemCount() {
		return this.filePaths.size();
	}



	public class GridItemViewHolder extends RecyclerView.ViewHolder{

		View container;
		ImageView imageView;
		ImageView delete;
		ImageView share;

		public GridItemViewHolder(final View itemView) {
			super(itemView);

			 imageView = itemView.findViewById(R.id.imageView);
			 delete = itemView.findViewById(R.id.delete);
			 share = itemView.findViewById(R.id.share);
		}
	}


}
