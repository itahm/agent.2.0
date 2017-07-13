package com.itahm.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.itahm.json.JSONObject;

public class TopTable <E extends Enum<E>> implements Comparator<String> {
	
	private final Class<E> e;
	private final Map<E, HashMap<String, Long>> map;
	private Map<String, Long> sortTop;
	
	public TopTable(Class<E> e) {
		this.e = e;
		
		map = new HashMap<>();
		
		for (E key : e.getEnumConstants()) {
			map.put(key, new HashMap<String, Long>());
		}
	}
	
	public synchronized void submit(String ip, E resource, long value) {
		this.map.get(resource).put(ip, value);
	}
	
	public synchronized JSONObject getTop(final int count) {
		JSONObject top = new JSONObject();
		
		for (E key : e.getEnumConstants()) {
			top.put(key.toString(), getTop(this.map.get(key), count));
		}
		
		return top;
	}
	
	private Map<String, Long> getTop(HashMap<String, Long> sortTop, int count) {
		Map<String, Long > top = new HashMap<String, Long>();
		List<String> list = new ArrayList<String>();
		String ip;
		
		this.sortTop = sortTop;
		
        list.addAll(sortTop.keySet());
         
        Collections.sort(list, this);
        
        count = Math.min(list.size(), count);
        for (int i=0; i< count; i++) {
        	ip = list.get(i);
        	
        	top.put(ip, this.sortTop.get(ip));
        }
        
        return top;
	}

	public void remove(String ip) {
		for (E key : e.getEnumConstants()) {
			this.map.get(key).remove(ip);
		}
	}
	
	@Override
	public int compare(String ip1, String ip2) {
		Long value1 = this.sortTop.get(ip1);
        Long value2 = this.sortTop.get(ip2);
         
        return value2.compareTo(value1);
	}
	
}