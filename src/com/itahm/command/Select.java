package com.itahm.command;

import java.io.IOException;

import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

import com.itahm.Agent;
import com.itahm.http.Request;
import com.itahm.http.Response;

public class Select implements Command {
	
	@Override
	public Response execute(Request request, JSONObject data) throws IOException {
		try {
			JSONObject nodeData = Agent.snmp.getNodeData(data.getString("ip"), data.has("offline"));
			
			if (nodeData != null) {
				return Response.getInstance(Response.Status.OK, nodeData.toString());
			}
			else {
				return Response.getInstance(Response.Status.BADREQUEST,
					new JSONObject().put("error", "node not found").toString());
			}
		}
		catch(NullPointerException npe) {npe.printStackTrace();
			return Response.getInstance(Response.Status.UNAVAILABLE);
		}
		catch (JSONException jsone) {
			return Response.getInstance(Response.Status.BADREQUEST,
					new JSONObject().put("error", "invalid json request").toString());
		}
	}
	
}
