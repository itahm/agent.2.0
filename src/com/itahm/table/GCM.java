package com.itahm.table;

import java.io.File;
import java.io.IOException;

import com.itahm.json.JSONObject;

import com.itahm.Agent;
import com.itahm.table.Table;

public class GCM extends Table {

	public GCM(File dataRoot) throws IOException {
		super(dataRoot, GCM);
	}
	
	@Override
	public JSONObject put(String id, JSONObject gcm) throws IOException {
		if (Agent.gcmm != null) {
			if (gcm == null) {
				if (super.table.has(id)) {
					Agent.gcmm.unregister(super.getJSONObject(id).getString("token"));
				}
			}
			else {
				Agent.gcmm.register(gcm.getString("token"), id);
			}
		}
		
		return super.put(id,  gcm);
	}
}
