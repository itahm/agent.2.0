package com.itahm;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

import com.itahm.gcm.DownStream;
import com.itahm.table.Table;
import com.itahm.util.Util;

public class GCMManager extends DownStream {

	/**
	 * token - id mapping
	 */
	private final Map<String, String> tokenToID = new HashMap<> ();
	private final Table gcmTable = Agent.getTable(Table.GCM);
	
	public GCMManager(String apiKey, String host) throws IOException {
		super(apiKey, host);
		
		JSONObject gcmData = gcmTable.getJSONObject();
		String id;
		
		for (Object key : gcmData.keySet()) {
			id = (String)key;
			
			try {
				register(gcmData.getJSONObject(id).getString("token"), id);
			} catch (JSONException jsone) {
				Agent.log(Util.EToString(jsone));
			}
		}
	}

	public void broadcast(String message) {
		JSONObject gcmData = this.gcmTable.getJSONObject();
		
		for (Object id : gcmData.keySet()) {
			try {
				super.send(gcmData.getJSONObject((String)id).getString("token"), "ITAhM message", message);
			} catch (IOException | JSONException e) {
				Agent.log(Util.EToString(e));
			}
		}
	}

	public void register(String token, String id) {
		this.tokenToID.put(token, id);
	}
	
	public void unregister(String token) {
		this.tokenToID.remove(token);
	}
	
	@Override
	public void onUnRegister(String token) {
		JSONObject gcmData = this.gcmTable.getJSONObject();
		
		gcmData.remove(this.tokenToID.get(token));
		
		try {
			this.gcmTable.save();
		} catch (IOException ioe) {
			Agent.log(Util.EToString(ioe));
		}
	}

	@Override
	public void onRefresh(String oldToken, String token) {
		JSONObject gcmData = this.gcmTable.getJSONObject();
		String id = this.tokenToID.get(oldToken);
		
		if (gcmData.has(id)) {
			JSONObject gcm = gcmData.getJSONObject(id);
			
			gcm.put("token", token);
		}
		
		try {
			this.gcmTable.save();
		} catch (IOException ioe) {
			Agent.log(Util.EToString(ioe));
		}
	}

	@Override
	public void onComplete(int status) {
		if (status != 200) {
			Agent.log(String.format("GCM failed. status %d", status));
		}
	}

	@Override
	public void onStart() {
		System.out.println("GCM manager start.");
	}

	@Override
	public void onStop() {
		System.out.println("GCM manager stop.");
	}
	
}
