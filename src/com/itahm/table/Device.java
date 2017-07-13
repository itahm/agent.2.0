package com.itahm.table;

import java.io.File;
import java.io.IOException;

import com.itahm.json.JSONObject;

import com.itahm.Agent;

public class Device extends Table {
	
	public Device(File dataRoot) throws IOException {
		super(dataRoot, DEVICE);
	}
	
	/**
	 * 추가인 경우 position 정보를 생성해 주어야 하고
	 * 삭제인 경우 link 데이터, position 정보, monior 정보를 함께 삭제해 주어야 한다.
	 * @throws IOException 
	 */
	public JSONObject put(String ip, JSONObject device) throws IOException {
		if (device == null) {
			if (super.table.has(ip)) {
				Table posTable = Agent.getTable(Table.POSITION);
				JSONObject linkData = posTable.getJSONObject(ip).getJSONObject("ifEntry");
				
				if (linkData.length() > 0) {
					for (Object peerIP : linkData.keySet()) {
						posTable.getJSONObject((String)peerIP).getJSONObject("ifEntry").remove(ip);
					}
				}
				
				posTable.put(ip, null);
				
				Agent.getTable(Table.MONITOR).put(ip, null);
			}
		}
		else {
			Table posTable = Agent.getTable(Table.POSITION);
			
			if (posTable.getJSONObject(ip) == null) {
				posTable.put(ip, new JSONObject().put("x", 0).put("y", 0).put("ifEntry", new JSONObject()));
			}
			/*
			if (device.has("ip")) {
				if (!device.has("name")) {
					device.put("name", "");
				}
			
				if (!device.has("type")) {
					device.put("type", "unknown");
				}
				
				if (!device.has("ifSpeed")) {
					device.put("ifSpeed", new JSONObject());
				}
			}
			// ip를 가지지 않은 것은 group
			else {
				if (!device.has("name")) {
					device.put("name", ip);
				}
			}*/
		}
		
		return super.put(ip, device);
	}
	
}
