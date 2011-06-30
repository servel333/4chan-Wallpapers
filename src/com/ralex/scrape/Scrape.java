package com.ralex.scrape;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.util.ByteArrayBuffer;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

public class Scrape extends Activity {
	// options ?c=1&rt=0&rs=0&eq=0&cnt=40&srt=7&nw=2&da=1&b=0&pn=
	/*
	 * d : url list, rt : , eq : opperand, nw : NSFW (0:safe,1:mixed,2:nsfw
	 * only), da : days, b : board, cnt : number of images, rs : ,pn : [page
	 * number?], srt : sort mode (7:random)
	 */
	private final boolean NSFW = false;
	
	private final int SAFE_MODE = 0;
	private final int NSFW_MODE = 2;
	private int MODE = SAFE_MODE;//default mode
	
	private final int LIMIT = 99;
	
	private final int MENU_REFRESH = 1;
	private final int MENU_MODE = 2;
	private final int MENU_VIEW = 3;
	private final int MENU_SAVE = 4;
	
	private final String SRC = "http://nik.bot.nu/browse.fu?cnt="
			+ String.valueOf(LIMIT) + "&da=6&srt=7&nw=";
	private final String PATH = "/data/data/com.ralex.scrape/files/";
	private final String regex = "img/thumb/([/a-zA-Z0-9\\.]*)";// "http://nik.bot.nu/img/orig/([/a-zA-Z0-9\\.]*)";
	private final String thumbUrl = "http://nik.bot.nu/img/thumb/";
	private final String bigUrl = "http://nik.bot.nu/img/orig/";
	private final String PACKAGE_NAME = "com.ralex.scrape";
	public static final String PREFS_NAME = "PrefsFile";
	
	private SnapGallery gallery;
	private GridView gridview;
	private ProgressDialog pd;
	private View view;
	
	private ImageAdapter im;
	
	private String SDCARD_PATH;
	private String deletePath = null;
	private int cache;
	private BuildCacheTask bct = null;
	private GetImageTask git = null;
	public UpdateCacheTask uct = new UpdateCacheTask();
	private Menu menu;
	
	private int orientation;
	
	private int startingPosition;
	
	/** Called when the activity is first created. */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

		this.orientation = getResources().getConfiguration().orientation;
		if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
			Log.i("MAGIC", "loaded in landscape");
			this.setContentView(R.layout.horizontal);
			this.gallery = (SnapGallery) findViewById(R.id.gallery);
			this.gallery.setOnItemClickListener(clickListener);
			this.gallery.setLongClickable(true);
			this.gallery.setOnItemSelectedListener(selectedListener);
			this.registerForContextMenu(gallery);
			this.view = gallery;
			Log.i("MAGIC", "done. drawing gallery view");
		} else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
			Log.i("MAGIC", "loaded in portrait");
			this.setContentView(R.layout.vertical);
			this.gridview = (GridView) findViewById(R.id.gridview);
			this.gridview.setOnItemClickListener(gridClickListener);
			this.gridview.setLongClickable(true);
			this.registerForContextMenu(gridview);
			this.view = gridview;
			Log.i("MAGIC", "done. drawing grid view");
		}

		// setup
		File files = new File(PATH);
		if (!files.exists()) {
			files.mkdirs();
		}

		this.SDCARD_PATH = Environment.getExternalStorageDirectory() + "/"
				+ this.PACKAGE_NAME + "/";
		File sdcard = new File(SDCARD_PATH);
		if (Environment.getExternalStorageDirectory().canWrite()
				&& !sdcard.exists()) {
			sdcard.mkdirs();
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		startingPosition = settings.getInt("startingPosition", 0);

		if (this.deletePath != null) {
			(new File(deletePath)).delete();
			this.deletePath = null;
		}
		// already have thumbnails?
		checkCache();
		this.cache = cacheSize();
		Log.i("MAGIC", "looking for cache. size: " + String.valueOf(cache));
		if (cache > 0 && cache <= LIMIT) {
			this.im = new ImageAdapter(this, cache, orientation);
			if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
				this.gallery.setAdapter(im);
				rebuildCache();
				Log.i("MAGIC", "rebuilt cache");
				this.im.notifyDataSetChanged();
				this.gallery.setSelection(startingPosition, true);
				Log.i("MAGIC", "done. redrawing view");
			} else {
				this.gridview.setAdapter(im);
				rebuildCache();
				uct = new UpdateCacheTask();
				uct.execute(this, 0, true);
				setProgressBarIndeterminateVisibility(true);
				this.im.notifyDataSetChanged();
			}
		} else {
			Log.i("MAGIC", "refreshing");
			refresh();
		}
		Log.i("MAGIC", "closed on pos: " + startingPosition);
	}

	@Override
	protected void onPause() {
		super.onPause();
		SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		if(startingPosition >= 0)
			editor.putInt("startingPosition", startingPosition);
		else
			editor.putInt("startingPosition", ((AdapterView) this.view)
				.getSelectedItemPosition());
		editor.commit();

		Log.i("MAGIC", "stopping any downloads");
		if (this.bct != null)
			this.bct.cancel(true);
		if (this.git != null)
			this.git.cancel(true);
		Log.i("MAGIC", "going down");
		menuEnabled(true);
	}

	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		Log.i("MAGIC", "context menu build started");
		if (v.getId() == R.id.gallery || v.getId() == R.id.gridview) {
			menu.setHeaderTitle("Image Actions");
			menu.add(1, MENU_VIEW, 0, "View Image");
			menu.add(1, MENU_SAVE, 1, "Save Image to SD card");
		}
	}

	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case MENU_VIEW:
			saveImage(info.position, true);
			return true;
		case MENU_SAVE:
			saveImage(info.position, false);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	/* Creates the menu items */
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i("MAGIC", "creating menu");
		this.menu = menu;
		this.menu.add(0, MENU_REFRESH, 0, "Refresh")
				.setIcon(R.drawable.refresh);
		if (this.NSFW)
			this.menu.add(0, MENU_MODE, 1, "NSFW Mode");
		return true;
	}

	/* Handles item selections */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_REFRESH:
			refresh();
			Log.i("MAGIC", "REFRESH");
			return true;
		case MENU_MODE:
			if (MODE == NSFW_MODE) {
				MODE = SAFE_MODE;
				item.setTitle("NSFW Mode");
			} else {
				MODE = NSFW_MODE;
				item.setTitle("Safe Mode");
			}
			Log.i("MAGIC", "MODE");
			refresh();
			return true;
		}
		return false;
	}

	// touch listener
	OnItemClickListener clickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> g, View view, int position,
				long arg3) {
			saveImage(position, true);
		}
	};
	
	// touch listener
	OnItemClickListener gridClickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> g, View view, int position,
				long arg3) {
			startingPosition = position;
			setToast("Rotate your device to view larger image",Toast.LENGTH_SHORT);
		}
	};

	//gallery select
	OnItemSelectedListener selectedListener = new OnItemSelectedListener() {

		public void onItemSelected(AdapterView<?> arg0, View arg1, int current,
				long arg3) {
			try {
				if ((current > 0 && !im.isCached(current - 1))
						|| (current + 1 < LIMIT && !im.isCached(current + 1))) {
					if (uct.getStatus().compareTo(AsyncTask.Status.FINISHED) == 0
							|| uct.getStatus().compareTo(
									AsyncTask.Status.PENDING) == 0) {
						uct = new UpdateCacheTask();
						uct.execute(this, current, false);
					}
				}
			} catch (IndexOutOfBoundsException e) {
				Log.i("MAGIC", "index out of bounds");
			}
		}

		public void onNothingSelected(AdapterView<?> arg0) {
			// nothing
		}
	};

	// cancel listener
	OnCancelListener cancelListener = new OnCancelListener() {
		public void onCancel(DialogInterface dialog) {
			git.cancel(true);
			dialog.dismiss();
		}
	};

	private void setToast(String msg, int length) {
		Toast.makeText(Scrape.this, msg, length).show();
	}

	private boolean isOnSdcard(String filename) {
		return new File(SDCARD_PATH + filename).exists();
	}

	private void saveImage(int position, boolean display) {
		pd = ProgressDialog.show(Scrape.this, "Please wait...",
				"Downloading HQ image.", true);
		pd.setCancelable(true);
		pd.setOnCancelListener(cancelListener);
		Log.i("MAGIC", "item clicked, position: " + String.valueOf(position));
		String file = im.getFilename(position);
		if (file != null
				&& Environment.getExternalStorageDirectory().canWrite()) {
			git = new GetImageTask();
			git.execute(file, display);
		}
	}

	private void menuEnabled(boolean state) {
		if (menu != null)
			menu.setGroupEnabled(0, state);
	}

	// delete any 0 sized files in cache
	private void checkCache() {
		File dir = new File(PATH);
		String[] filelist = dir.list();
		if (filelist != null) {
			for (String file : filelist) {
				File f = new File(PATH + file);
				if (f.length() <= 0)
					f.delete();
			}
		}
	}

	// returns number of images in cache
	private int cacheSize() {
		File dir = new File(PATH);
		if (dir.list() != null)
			return dir.list().length;
		else
			return 0;
	}

	//
	private void rebuildCache() {
		// rebuild cache
		this.setProgressBarIndeterminateVisibility(true);
		Log.i("MAGIC", "rebuilding cache");
		File dir = new File(PATH);
		ArrayList<String> filelist = new ArrayList<String>(Arrays.asList(dir
				.list()));
		Collections.sort(filelist);

		int count = 0;
		for (String file : filelist) {
			Log.i("MAGIC", "set name. file: " + file);
			this.im.setItem(count, file);
			count++;
		}
		this.setProgressBarIndeterminateVisibility(false);
		this.im.notifyDataSetChanged();	
	}

	// repopulate the gallery view and clean up files
	@SuppressWarnings("unchecked")
	private void refresh() {
		// empty gallery
		menuEnabled(false);
		Log.i("MAGIC", "clearing image adapter cache");
		this.im = new ImageAdapter(this, LIMIT, orientation);
		((AdapterView) this.view).setAdapter(im);
		this.im.notifyDataSetChanged();

		// clears cache
		if (cacheSize() > 0)
			new ClearCacheThread().run();

		// scrape
		// this.setProgressBarVisibility(true);
		Log.i("MAGIC", "build cache from scrape");
		this.bct = new BuildCacheTask();
		this.bct.execute(doScrape());
		if (orientation == Configuration.ORIENTATION_PORTRAIT) {
			uct = new UpdateCacheTask();
			uct.execute(this, 0, true);
		}
		menuEnabled(true);
	}

	// filename from url
	private String getFilename(String url) {
		String[] parts = url.split("/");
		String filename = parts[parts.length - 1];
		return filename;
	}

	// url from filename and size
	private String getUrl(String filename, boolean big) {
		String url = null;
		if (big)
			url = bigUrl;
		else
			url = thumbUrl;
		String[] parts = filename.split("");
		url = url + parts[1] + "/" + parts[2] + "/" + filename;
		// Log.i("MAGIC","url: "+url);
		return url;
	}

	// scrape website
	public String getPageContent() throws Exception {
		String page = "";
		URL scrapeSite = new URL(SRC + MODE);
		URLConnection sS = scrapeSite.openConnection();
		BufferedReader in = new BufferedReader(new InputStreamReader(sS
				.getInputStream()));
		String inputLine;
		while ((inputLine = in.readLine()) != null)
			page = page.concat(inputLine);
		in.close();
		return page;
	}

	// find image src
	private ArrayList<String> doScrape() {
		ArrayList<String> list = new ArrayList<String>();
		Pattern p = Pattern.compile(regex);
		try {
			Matcher m = p.matcher(getPageContent());
			int count = 0;
			while (m.find() && count < this.LIMIT) {
				count++;
				// Log.i("MAGIC", thumbUrl + m.group(1));
				list.add(thumbUrl + m.group(1));
			}
		} catch (Exception e) {
			Log.e("MAGIC", "failed to scrape. e: " + e);
		}
		Collections.sort(list);
		return list;
	}

	public class UpdateCacheTask extends AsyncTask<Object, Integer, Void> {

		private Integer[] getBoundaries(int current, boolean all) {
			int l, r;
			if (!all) {
				if (current - im.boundaryL <= 0)
					l = 0;
				else
					l = current - im.boundaryL;

				cache = cacheSize();
				if (cache == LIMIT) {
					if (current + im.boundaryR >= LIMIT)
						r = LIMIT;
					else
						r = current + im.boundaryR;
				} else if (current + im.boundaryR >= cache)
					r = cache;
				else
					r = current + im.boundaryR;
			} else {
				l = 0;
				if (cache < LIMIT)
					r = cache;
				else
					r = LIMIT;
			}
			Log.i("MAGIC", "l: " + l + ", r: " + r);

			Integer[] boundaries = { l, r };
			return boundaries;
		}

		protected Void doInBackground(Object... args) {
			Log.i("MAGIC", "update cache thread started");
			int current = (Integer) args[1];
			Log.i("MAGIC", "current: " + current);
			im.fresh();

			Integer[] boundaries = getBoundaries(current, (Boolean) args[2]);

			for (int i = boundaries[0]; i < boundaries[1]; i++) {
				if (!im.isCached(i) && im.getFilename(i) != null) {
					Log.i("MAGIC", "add position: " + i);
					if (orientation == Configuration.ORIENTATION_LANDSCAPE)
						im.setBitmap(i, ImageAdapter.makeBitmap(im
								.getFilename(i), 480));
					else {
						im.setBitmap(i, ImageAdapter.makeBitmap(im
								.getFilename(i), 100));
						publishProgress(i);
					}
				}
			}
			return null;
		}

		protected void onProgressUpdate(Integer... i) {
			if (i[0] % 12 == 0)
				im.notifyDataSetChanged();
		}

		protected void onPostExecute(Void v) {
			if (pd != null)
				pd.dismiss();
			setProgressBarIndeterminateVisibility(false);
			im.notifyDataSetChanged();
			Log.i("MAGIC", "done cache update");
		}
	}

	private class BuildCacheTask extends
			AsyncTask<ArrayList<String>, Integer, Void> {

		protected Void doInBackground(ArrayList<String>... args) {
			int count = 0;
			for (String url : args[0]) {
				if (this.isCancelled())
					break;
				String filename = getFilename(url);
				boolean success = downloadImage(PATH, filename, url);
				if (!success)
					Log.e("MAGIC", "Download unsuccessful");
				else {
					Log.i("MAGIC", "setting image");
					if (this.isCancelled())
						break;
					im.setItem(count, filename);
					if (orientation == Configuration.ORIENTATION_PORTRAIT)
						im.setBitmap(count, ImageAdapter.makeBitmap(im
								.getFilename(count), 100));
				}
				count++;
				publishProgress(count);
			}
			
			return null;
		}

		protected void onProgressUpdate(Integer... i) {
			menuEnabled(false);
			if (orientation == Configuration.ORIENTATION_PORTRAIT && i[0] % 3 == 0)
				im.notifyDataSetChanged();
			else if (orientation == Configuration.ORIENTATION_LANDSCAPE && i[0] == 1) {
				if (uct.getStatus().compareTo(AsyncTask.Status.FINISHED) == 0
						|| uct.getStatus().compareTo(AsyncTask.Status.PENDING) == 0) {
					uct = new UpdateCacheTask();
					uct.execute(this, 0, false);
					setProgressBarIndeterminateVisibility(true);
				}
			}
			
		}

		protected void onPostExecute(Void v) {
			menuEnabled(true);
		}

		protected void onCancelled() {
			Log.i("MAGIC", "bct cancelling");
		}
	}

	private boolean downloadImage(String path, String filename, String url) {
		Log.i("MAGIC", "Download started");
		if ((new File(path + filename).exists())) {
			Log.i("MAGIC", "file already exists");
			return true;
		}
		// download image
		try {
			URL fileurl = new URL(url);// make url
			URLConnection ucon = fileurl.openConnection();// open connection
			BufferedInputStream bis = new BufferedInputStream(ucon
					.getInputStream(), 8192);// get buffered input stream

			ByteArrayBuffer baf = new ByteArrayBuffer(50);
			int current = 0;
			while ((current = bis.read()) != -1) {
				baf.append((byte) current);
			}
			FileOutputStream fos = new FileOutputStream(path + filename);
			// Log.i("MAGIC", "opened out stream. bytes: " + baf.length());
			fos.write(baf.toByteArray());
			// Log.i("MAGIC", "wrote to stream");
			fos.flush();
			fos.close();
			baf.clear();
			Log.i("MAGIC", "Download successful");
			return true;
		} catch (Exception e) {
			Log.e("MAGIC", "file not found exception");
			return false;
		}
	}

	private class GetImageTask extends AsyncTask<Object, Integer, String> {
		private boolean view;
		private boolean delete = false;

		protected String doInBackground(Object... args) {
			boolean success;
			this.view = (Boolean) args[1];
			String filename = (String) args[0];
			if (!isOnSdcard(filename) && view) {
				delete = true;
			} else if (isOnSdcard(filename)) {
				return SDCARD_PATH + filename;
			}
			success = downloadImage(SDCARD_PATH, filename, getUrl(filename,
					true));
			if (success) {
				return SDCARD_PATH + filename;
			}
			return null;
		}

		protected void onPostExecute(String path) {
			pd.dismiss();
			if (this.view) {
				Intent intent = new Intent();
				intent.setAction(android.content.Intent.ACTION_VIEW);
				intent.setDataAndType(Uri.fromFile(new File(path)), "image/*");
				startActivity(intent);
				if (delete)
					deletePath = path;
				Log.i("MAGIC", "view + done download");
			} else {
				setToast("Image saved to: " + path, Toast.LENGTH_LONG);
				Log.i("MAGIC", "save + done download");
			}
			git = null;
		}
	}
}