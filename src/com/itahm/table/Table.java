package com.itahm.table;

import java.io.File;
import java.io.IOException;

import com.itahm.json.JSONObject;
import com.itahm.util.Util;

public class Table {
	public final static String ACCOUNT = "account";
	public final static String CRITICAL = "critical";
	public final static String DEVICE = "device";
	public final static String MONITOR = "monitor";
	public final static String ICON = "icon";
	public final static String POSITION = "position";
	public final static String PROFILE = "profile";
	public final static String CONFIG = "config";
	public final static String GCM = "gcm";
	public final static String SMS = "sms";
	
	protected JSONObject table;
	private File file;
	private File backup;
	
	public Table(File dataRoot, String tableName) throws IOException {	
		file = new File(dataRoot, tableName);
		backup = new File(dataRoot, tableName +".backup");
		
		if (file.isFile()) {
			table = Util.getJSONFromFile(file);
		}
		else {
			table = new JSONObject();
			
			Util.putJSONtoFile(file, table);
			Util.putJSONtoFile(backup, table);
		}
		
		// file 깨졌을때 복구
		if (table == null && backup.isFile()) {
			table = Util.getJSONFromFile(backup);
			
			if (table != null) {
				Util.putJSONtoFile(file, table);
			}
		}
		
		if (table == null) {
			throw new IOException("Table ("+ tableName +") loading failure");
		}
	}
	
	protected boolean isEmpty() {
		return this.table.length() == 0;
	}
	
	public JSONObject getJSONObject() {
		return this.table;
	}
	
	public JSONObject getJSONObject(String key) {
		if (this.table.has(key)) {
			return this.table.getJSONObject(key);
		}
		
		return null;
	}
	
	public JSONObject put(String key, JSONObject value) throws IOException {
		if (value == null) {
			this.table.remove(key);
		}
		else {
			this.table.put(key, value);
		}
		
		return save();
	}
	
	public JSONObject save() throws IOException {
		Util.putJSONtoFile(this.file, this.table);
		
		return Util.putJSONtoFile(this.backup, this.table);
	}

	public JSONObject save(JSONObject table) throws IOException{
		this.table = table;
		
		return save();
	}
	
}
