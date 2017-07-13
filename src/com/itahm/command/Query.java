package com.itahm.command;

import java.io.IOException;

import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

import com.itahm.Agent;
import com.itahm.SNMPNode;
import com.itahm.http.Request;
import com.itahm.http.Response;

public class Query implements Command {
	
	@Override
	public Response execute(Request request, JSONObject data) throws IOException {
		
		try {
			SNMPNode node = Agent.snmp.getNode(data.getString("ip"));
			
			if (node != null) {
				data = node.getData(data.getString("database")
					, String.valueOf(data.getInt("index"))
					, data.getLong("start")
					, data.getLong("end")
					, data.has("summary")? data.getBoolean("summary"): false);
				
				if (data != null) {
					return Response.getInstance(Response.Status.OK, data.toString());
				}
				else {
					return Response.getInstance(Response.Status.BADREQUEST,
						new JSONObject().put("error", "database not found").toString());
				}
			}
			else {
				return Response.getInstance(Response.Status.BADREQUEST,
					new JSONObject().put("error", "node not found").toString());
			}
		}
		catch(NullPointerException npe) {
			return Response.getInstance(Response.Status.UNAVAILABLE);
		}
		catch (JSONException jsone) {
			return Response.getInstance(Response.Status.BADREQUEST,
				new JSONObject().put("error", "invalid json request").toString());
		}
		
	}

}
