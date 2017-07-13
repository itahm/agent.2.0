package com.itahm;

import java.util.HashMap;
import java.util.Map;

import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;
import com.itahm.util.Util;

abstract public class Critical {

	public enum Resource {
		PROCESSOR("Processor load"),
		MEMORY("Physical memory"),
		STORAGE("Storage usage"),
		THROUGHPUT("interface throughput");
		
		private final String alias;
		
		private Resource(String alias) {
			this.alias = alias;
		}
		
		public String toString() {
			return this.alias;
		}
	}
	
	public static byte NONE = 0x00;
	public static byte CRITIC = 0x01;
	public static byte DIFF = 0x10;
	
	private final Map<Resource, HashMap<String, Data>> mapping = new HashMap<>();
	
	public Critical(JSONObject criticalCondition) {
		JSONObject list;
		
		for (Resource resource: Resource.values()) {
			mapping.put(resource, new HashMap<String, Data>());
		}
		
		Resource resource;
		
		for (Object key : criticalCondition.keySet()) {
			list = criticalCondition.getJSONObject((String)key);
			
			try {
				resource = Resource.valueOf(((String)key).toUpperCase());
			}
			catch (IllegalArgumentException iae) {
				continue;
			}
			
			for (Object index: list.keySet()) {
				try {
					mapping.get(resource).put((String)index, new Data(list.getJSONObject((String)index).getInt("limit")));
				}
				catch(JSONException jsone) {
					Agent.log(Util.EToString(jsone));
				}
			}
		}
	}
	
	public void analyze(Resource resource, String index, long max, long current) {
		
		Data critical = this.mapping.get(resource).get(index);
		
		if (critical == null) {
			return;
		}
		
		long rate = current *100 / max;
		byte flag = critical.test(rate);
		
		if (isDiff(flag)) {
			onCritical(isCritical(flag), resource, index, rate);
		}
	}
	
	public static boolean isCritical(byte flag) {
		return (flag & CRITIC) == CRITIC;
	}
	
	public static boolean isDiff(byte flag) {
		return (flag & DIFF) == DIFF;
	}
	
	class Data {

		private final int limit;
		private Boolean status = null;
		
		public Data(int limit) {
			this.limit = limit;
		}
		
		public byte test(long current) {
			boolean isCritic = this.limit <= current;
			
			if (this.status == null) {
				this.status = isCritic;
			}
			
			if (this.status == isCritic) {
				return NONE;
			}
			
			this.status = isCritic;
			
			return (byte)(DIFF | (isCritic? CRITIC : NONE));
		}
	}
	
	abstract public void onCritical(boolean isCritical, Resource resource, String index, long rate);
}
