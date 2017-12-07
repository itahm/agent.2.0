package com.itahm.table;

import java.io.File;
import java.io.IOException;

import com.itahm.json.JSONObject;

import com.itahm.Agent;
import com.itahm.table.Table;

public class GCM extends Table {

	public GCM(File dataRoot) throws IOException {
		super(dataRoot, Name.GCM);
	}
	
	@Override
	public JSONObject put(String id, JSONObject gcm) throws IOException {
		if (gcm == null) {
			if (super.table.has(id)) {
				Agent.register(super.getJSONObject(id).getString("token"), null);
			}
		}
		else {
			Agent.register(gcm.getString("token"), id);
		}
		
		return super.put(id,  gcm);
	}
}
