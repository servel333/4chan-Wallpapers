package com.ralex.scrape;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Gallery.LayoutParams;

public class ImageAdapter extends BaseAdapter {
	private static String PATH = "/data/data/com.ralex.scrape/files/";
	public final int boundaryR = 15;
	public final int boundaryL = 5;
	/* The parent context */
	private Context myContext;
	public int LIMIT;
	private Bitmap[] cache;
	public volatile int current;
	private String[] names;
	private int orientation;
		
	private Bitmap blankThumb;
	private Bitmap blank;

	/** Simple Constructor saving the 'parent' context. */
	public ImageAdapter(Context c, int limit, int orientation) {
		this.myContext = c;
		this.LIMIT = limit;
		this.names = new String[LIMIT];
		this.cache = new Bitmap[LIMIT];
		this.current = 0;
		this.orientation = orientation;
		makeBlankThumb();
		makeBlank();
	}
	
	private void makeBlankThumb(){
		blankThumb = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(blankThumb);
		c.drawARGB(255, 200, 200, 200);
	}
	
	private void makeBlank(){
		blank = Bitmap.createBitmap(350, 225, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(blank);
		c.drawARGB(255, 200, 200, 200);
		Paint p = new Paint();
		p.setAntiAlias(true);
		p.setFakeBoldText(true);
		c.drawText("Image loading...", (float) 100, (float) 100, p);
	}
	

	/** Returns the amount of images we have defined. */
	public int getCount() {
		return this.names.length;
	}

	/* Use the array-Positions as unique IDs */
	public Object getItem(int position) {
		return position;
	}

	public long getItemId(int position) {
		return position;
	}

	public void fresh() {
		this.cache = new Bitmap[LIMIT];
	}

	/* RJ */
	public String getFilename(int position) {
		return this.names[position];
	}

	public void setBitmap(int position, Bitmap bm) {
		this.cache[position] = bm;
	}

	public void removeBitmap(int position) {
		this.cache[position] = null;
	}

	/* RJ */
	public Bitmap getBitmap(final int position) {
		if (cache[position] != null) {
			Log.i("MAGIC", "cache exists");
			return cache[position];
		}
		return null;
	}

	/* RJ */
	public void setItem(int position, String item) {
		this.names[position] = item;
	}

	public boolean isCached(int position) throws IndexOutOfBoundsException {
		if (position < -1 || position >= LIMIT)
			throw new IndexOutOfBoundsException();
		return cache[position] != null;
	}

	public static Bitmap makeBitmap(String filename, int w) {
		Bitmap bmo;
		Bitmap bm = null;
		try {
			// Log.i("MAGIC", "opening file");
			FileInputStream fis = new FileInputStream(PATH + filename);
			/* Buffered is always good for a performance plus. */
			BufferedInputStream bis = new BufferedInputStream(fis, 8192);
			/* Decode url-data to a bitmap. */
			// Log.i("MAGIC", "decoding stream");
			bmo = BitmapFactory.decodeStream(bis);
			if (bmo == null)
				throw new IOException("file empty");
			bis.close();
			fis.close();
			int width = bmo.getWidth();
			int height = bmo.getHeight();
			int newWidth = w;
			int newHeight = height * newWidth / width;

			// Log.i("MAGIC", "calculating scale");
			// calculate the scale
			float scaleWidth = ((float) newWidth) / width;
			float scaleHeight = ((float) newHeight) / height;

			// create a matrix for the manipulation
			Matrix matrix = new Matrix();
			// resize the bit map
			matrix.postScale(scaleWidth, scaleHeight);

			// Log.i("MAGIC", "creating bitmap");
			// recreate the new Bitmap
			bm = Bitmap.createBitmap(bmo, 0, 0, width, height, matrix, true);
			bmo.recycle();
		} catch (IOException e) {
			Log.e("MAGIC", "file not found. e: " + e);
		}
		return bm;
	}

	/**
	 * Returns a new ImageView to be displayed, depending on the position
	 * passed.
	 */
	public View getView(int position, View convertView, ViewGroup parent) {
		ImageView i;
		if(convertView == null)
			i = new ImageView(this.myContext);
		else
			i = (ImageView) convertView;
		// if(position > 0 && position < LIMIT){
		Bitmap bm;
		String filename = null;
		Log.i("MAGIC", "setting current position");
		this.current = position;
		filename = this.names[position];
		// if null, draw filled bitmap
		if (filename == null || !isCached(position)) {
			Log.i("MAGIC", "blank image");
			if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
				bm = blank;
			} else{
				bm = blankThumb;
			}
		} else {
			Log.i("MAGIC", "get bm pos: " + String.valueOf(position));
			bm = getBitmap(position);

		}
		/* Apply the Bitmap to the ImageView that will be returned. */
		i.setImageBitmap(bm);
		// }

		/* Image should be scaled as width/height are set. */
		i.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
		if (orientation == Configuration.ORIENTATION_LANDSCAPE){
			i.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT,
					LayoutParams.FILL_PARENT));
		}
		else{
			i.setLayoutParams(new GridView.LayoutParams(100, 100));
            i.setScaleType(ImageView.ScaleType.CENTER_CROP);
		}

		return i;
	}
}