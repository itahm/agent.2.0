package com.itahm.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Calendar;

import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

public class Util {

	public static void download(URL url, File file) throws IOException {
		try (BufferedInputStream bi = new BufferedInputStream(url.openStream());
			FileOutputStream fos = new FileOutputStream(file);
		) {
			final byte buffer[] = new byte[1024];
			int length;
			
			while ((length = bi.read(buffer, 0, 1024)) != -1) {
				fos.write(buffer,  0, length);
			}
		}
	}
	
	public static Object loadClass(URL url, String name) throws InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
		try (URLClassLoader urlcl = new URLClassLoader(new URL [] {
				url
			})) {
			
			return urlcl.loadClass(name).newInstance();
		}
	}
	
	public static String EToString(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);

		e.printStackTrace(pw);
		
		return sw.toString();
	}
	
	public static Calendar trimDate(Calendar c) {
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		
		return c;
	}

	/**
	 * 
	 * @param file
	 * @return null if not json file
	 * @throws IOException
	 */
	public static JSONObject getJSONFromFile(File file) throws IOException {
		try {
			return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		}
		catch (JSONException jsone) {
			return null;
		}
	}
	
	public static JSONObject putJSONtoFile(File file, JSONObject json) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(json.toString().getBytes(StandardCharsets.UTF_8));
		}
		
		return json;
	}
	
}
