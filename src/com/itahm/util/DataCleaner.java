package com.itahm.util;

import java.io.File;
import java.util.Calendar;

abstract public class DataCleaner {

	private final long minDateMills;
	private long count;
	
	public DataCleaner(File dataRoot, long minDateMills) {
		this(dataRoot, minDateMills, 0);
	}
	
	public DataCleaner(File dataRoot, long minDateMills, int depth) {
		this.minDateMills = minDateMills;
		
		count = 0;
		
		if (dataRoot.isDirectory()) {
			emptyLastData(dataRoot, depth);
		}
		
		onComplete(this.count);
	}

	private void emptyLastData(File directory, int depth) {
		File [] files = directory.listFiles();
		
		for (File file: files) {
			if (file.isDirectory()) {
				if (depth > 0) {
					emptyLastData(file, depth -1);
				}
				else {
					try {
						if (minDateMills > Long.parseLong(file.getName())) {
							if (deleteDirectory(file)) {
								count++;
								
								onDelete(file);
							}
						}
					}
					catch (NumberFormatException nfe) {
					}
				}
			}
		}
	}
	
	public static boolean deleteDirectory(File directory) {
        if(!directory.exists() || !directory.isDirectory()) {
            return false;
        }
        
        File[] files = directory.listFiles();
        
        for (File file : files) {
            if (file.isDirectory()) {
                deleteDirectory(file);
            } else {
                file.delete();
            }
        }
         
        return directory.delete();
    }
	
	abstract public void onDelete(File file);
	abstract public void onComplete(long count);
	
	public static void main(String[] args) {
		Calendar date = Calendar.getInstance();
		
		date.set(Calendar.MONTH, date.get(Calendar.MONTH) -1);
		
		new DataCleaner(new File("."), date.getTimeInMillis(), 1) {
			
			@Override
			public void onDelete(File file) {
			}
			
			@Override
			public void onComplete(long count) {
			}
		};
	}

}
