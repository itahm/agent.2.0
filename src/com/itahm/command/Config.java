package com.itahm.command;

import java.io.IOException;

import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

import com.itahm.table.Table;
import com.itahm.Agent;
import com.itahm.GCMManager;
import com.itahm.http.Request;
import com.itahm.http.Response;

public class Config implements Command {
	
	public enum Key {
		CLEAN,
		DASHBOARD,
		DISPLAY,
		GCM,
		SMS,
		INTERVAL
	}
	
	@Override
	public Response execute(Request request, JSONObject data) throws IOException {
		try {
			Table table = Agent.getTable(Table.CONFIG);
			JSONObject config = table.getJSONObject();
			
			switch(Key.valueOf(data.getString("key").toUpperCase())) {
			case CLEAN:
				int clean = data.getInt("value");
				
				config.put("clean", clean);
				
				Agent.snmp.clean(clean);
				
				break;
			
			case DASHBOARD:
				config.put("dashboard", data.getJSONObject("value"));
				
				break;
			case DISPLAY:
				config.put("display", data.getString("value"));
				
				break;
			case SMS:
				config.put("sms", data.getBoolean("value"));
				
				break;
			case INTERVAL:
				config.put("interval", data.getInt("value"));
				
				break;
			case GCM:
				if (data.isNull("value")) {
					if (Agent.gcmm != null) {
						Agent.gcmm.close();
						
						Agent.gcmm = null;
					}
					
					config.put("gcm", JSONObject.NULL);
				}
				else {
					String host = data.getString("value");
					
					if (Agent.gcmm == null) {
						Agent.gcmm = new GCMManager(Agent.API_KEY, data.getString("value"));
					}
					
					config.put("gcm", host);
				}
				
				break;
			default:
				return Response.getInstance(Response.Status.BADREQUEST,
					new JSONObject().put("error", "invalid config parameter").toString());
			}
			
			Agent.config = config;
			
			table.save();
			
			return Response.getInstance(Response.Status.OK);
		}
		catch (JSONException jsone) {
			return Response.getInstance(Response.Status.BADREQUEST,
				new JSONObject().put("error", "invalid json request").toString());
		}
	}
	
}
