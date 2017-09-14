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
	
	@Override
	public Response execute(Request request, JSONObject data) throws IOException {
		try {
			switch(data.getString("key")) {
			case "clean":
				int clean = data.getInt("value");
				
				Agent.config.put("clean", clean);
				
				Agent.snmp.clean();
				
				break;
			
			case "dashboard":
				Agent.config.put("dashboard", data.getJSONObject("value"));
				
				break;
			case "display":
				Agent.config.put("display", data.getString("value"));
				
				break;
			case "sms":
				Agent.config.put("sms", data.getBoolean("value"));
				
				break;
			case "interval":
				Agent.config.put("interval", data.getInt("value"));
				
				break;
			case "top":
				Agent.config.put("top", data.getInt("value"));
				
				break;
			case "gcm":
				if (data.isNull("value")) {
					if (Agent.gcmm != null) {
						Agent.gcmm.close();
						
						Agent.gcmm = null;
					}
					
					Agent.config.put("gcm", JSONObject.NULL);
				}
				else {
					String host = data.getString("value");
					
					if (Agent.gcmm == null) {
						Agent.gcmm = new GCMManager(Agent.API_KEY, data.getString("value"));
					}
					
					Agent.config.put("gcm", host);
				}
				
				break;
			default:
				return Response.getInstance(Response.Status.BADREQUEST,
					new JSONObject().put("error", "invalid config parameter").toString());
			}
			
			Agent.getTable(Table.CONFIG).save();
			
			return Response.getInstance(Response.Status.OK);
		}
		catch (JSONException jsone) {
			return Response.getInstance(Response.Status.BADREQUEST,
				new JSONObject().put("error", "invalid json request").toString());
		}
	}
	
}
