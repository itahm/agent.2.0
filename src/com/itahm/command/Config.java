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
		SMS
	}
	
	@Override
	public Response execute(Request request, JSONObject data) throws IOException {
		try {
			Table table = Agent.getTable(Table.CONFIG);
			
			switch(Key.valueOf(data.getString("key").toUpperCase())) {
			case CLEAN:
				int clean = data.getInt("value");
				
				table.getJSONObject().put("clean", clean);
				table.save();
				
				Agent.snmp.clean(clean);
				
				break;
			
			case DASHBOARD:
				table.getJSONObject().put("dashboard", data.getJSONObject("value"));
				table.save();
				
				break;
			case DISPLAY:
				table.getJSONObject().put("display", data.getString("value"));
				table.save();
				
				break;
			case SMS:
				table.getJSONObject().put("sms", data.getBoolean("value"));
				table.save();
				
				break;
			case GCM:
				if (data.isNull("value")) {
					if (Agent.gcmm != null) {
						Agent.gcmm.close();
						
						Agent.gcmm = null;
					}
					
					table.getJSONObject().put("gcm", JSONObject.NULL);
				}
				else {
					String host = data.getString("value");
					
					if (Agent.gcmm == null) {
						Agent.gcmm = new GCMManager(Agent.API_KEY, data.getString("value"));
					}
					
					table.getJSONObject().put("gcm", host);
				}
				
				table.save();
				
				return Response.getInstance(Response.Status.OK);
			default:
				return Response.getInstance(Response.Status.BADREQUEST,
					new JSONObject().put("error", "invalid config parameter").toString());
			}
			
			return Response.getInstance(Response.Status.OK);
		}
		catch (JSONException jsone) {
			return Response.getInstance(Response.Status.BADREQUEST,
				new JSONObject().put("error", "invalid json request").toString());
		}
	}
	
}
