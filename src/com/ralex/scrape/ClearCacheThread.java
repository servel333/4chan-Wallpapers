package com.ralex.scrape;

import java.io.File;

import android.util.Log;

public class ClearCacheThread extends Thread {
	private final String PATH = "/data/data/com.ralex.scrape/files/";
	
    public void run() {
    	//clear cache
        Log.i("MAGIC","clear cache thread started");
        File dir = new File(PATH);

		String[] filelist = dir.list();
		for(String file : filelist){
			if((new File(PATH+file)).delete())
				Log.i("CLEAN","file: "+file+" deleted");
			else
				Log.w("CLEAN","file: "+file+" not deleted");
		}
    }

    public static void main() {
        (new ClearCacheThread()).start();
    }
}