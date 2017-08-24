package com.itahm.command;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Iterator;

import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;
import com.itahm.Agent;
import com.itahm.http.Request;
import com.itahm.http.Response;
import com.itahm.util.Network;

public class Extra implements Command {
	
	private static int TOP_MAX = 10;
	
	public enum Key {
		RESET,
		FAILURE,
		SEARCH,
		MESSAGE,
		TOP,
		LOG,
		SYSLOG,
		ENTERPRISE,
		REPORT;
	};
	
	@Override
	public Response execute(Request request, JSONObject data) throws IOException {
		
		try {
			switch(Key.valueOf(data.getString("extra").toUpperCase())) {
			case RESET:
				Agent.snmp.resetResponse(data.getString("ip"));
				
				return Response.getInstance(Response.Status.OK);
			case FAILURE:
				JSONObject json = Agent.snmp.getFailureRate(data.getString("ip"));
				
				if (json == null) {
					return Response.getInstance(Response.Status.BADREQUEST,
						new JSONObject().put("error", "node not found").toString());
				}
				
				return Response.getInstance(Response.Status.OK, json.toString());
			case SEARCH:
				Network network = new Network(InetAddress.getByName(data.getString("network")).getAddress(), data.getInt("mask"));
				Iterator<String> it = network.iterator();
				
				while(it.hasNext()) {
					Agent.snmp.testNode(it.next(), false);
				}
				
				return Response.getInstance(Response.Status.OK);
			case MESSAGE:
				Agent.log.broadcast(data.getString("message"));
				
				return Response.getInstance(Response.Status.OK);
			case TOP:
				int count = TOP_MAX;
				if (data.has("count")) {
					count = Math.min(data.getInt("count"), TOP_MAX);
				}
				
				return Response.getInstance(Response.Status.OK, Agent.snmp.getTop(count).toString());
			case LOG:
				return Response.getInstance(Response.Status.OK, Agent.log.read(data.getLong("date")));
			case ENTERPRISE:
				return Agent.snmp.executeEnterprise(request, data);
			case SYSLOG:
				return Response.getInstance(Response.Status.OK, new JSONObject().put("log", Agent.log.getSysLog(data.getLong("date"))).toString());
			case REPORT:
				return Response.getInstance(Response.Status.OK, Agent.log.read(data.getLong("start"), data.getLong("end")));
			}
		}
		catch (NullPointerException npe) {
			return Response.getInstance(Response.Status.UNAVAILABLE);
		}
		catch (JSONException jsone) {
			return Response.getInstance(Response.Status.BADREQUEST,
					new JSONObject().put("error", "invalid json request").toString());
		}
		catch(IllegalArgumentException iae) {
		}
		
		return Response.getInstance(Response.Status.BADREQUEST,
			new JSONObject().put("error", "invalid extra").toString());
	}

}
