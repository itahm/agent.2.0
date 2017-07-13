package com.itahm;


import java.io.File;

import com.itahm.json.JSONObject;

import com.itahm.http.Request;
import com.itahm.http.Response;

public interface ITAhMAgent {
	public Response executeRequest(Request request, JSONObject data);
	public void closeRequest(Request request);
	public boolean start(File root);
	public void stop();
	public Object get(String key);
	public void set(Object value);
}
