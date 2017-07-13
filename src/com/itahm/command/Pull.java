package com.itahm.command;

import java.io.IOException;

import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

import com.itahm.table.Table;
import com.itahm.table.Config;
import com.itahm.Agent;
import com.itahm.http.Request;
import com.itahm.http.Response;

public class Pull implements Command {
	
	@Override
	public Response execute(Request request, JSONObject data) throws IOException {
		try {
			Table table = Agent.getTable(data.getString("database"));
			
			if (table == null) {
				return Response.getInstance(Response.Status.BADREQUEST,
					new JSONObject().put("error", "database not found").toString());
			}
			else {
				if (table instanceof Config) {
					data = table.getJSONObject()
						.put("space", Agent.getUsableSpace())
						.put("version", Agent.VERSION)
						.put("load", Agent.snmp.getLoad())
						.put("resource", Agent.snmp.getResourceCount())
						.put("java", System.getProperty("java.version"));
				}
				else {
					data = table.getJSONObject();
				}
				
				return Response.getInstance(Response.Status.OK, data.toString());
			}
		}
		catch (JSONException jsone) {
			return Response.getInstance(Response.Status.BADREQUEST,
				new JSONObject().put("error", "invalid json request").toString());
		}
	}
	
}
