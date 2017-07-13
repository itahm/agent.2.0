package com.itahm;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

import com.itahm.icmp.ICMPListener;
import com.itahm.table.Table;
import com.itahm.util.Util;

public class ICMPAgent implements ICMPListener, Closeable {
	
	private final Map<String, ICMPNode> nodeList = new HashMap<>();
	private final Table monitorTable = Agent.getTable(Table.MONITOR);
	
	public ICMPAgent() throws IOException {
		JSONObject snmpData = monitorTable.getJSONObject();
		
		for (Object ip : snmpData.keySet()) {
			try {
				if ("icmp".equals(snmpData.getJSONObject((String)ip).getString("protocol"))) {
					addNode((String)ip);
				}
			} catch (JSONException jsone) {
				Agent.log(Util.EToString(jsone));
			}
		}
		
		System.out.println("ICMP manager start.");
	}
	
	private void addNode(String ip) {
		try {
			ICMPNode node = new ICMPNode(this, ip);
			
			synchronized (this.nodeList) {
				this.nodeList.put(ip, node);
			}
			
			node.start();
		} catch (UnknownHostException uhe) {
			Agent.log(Util.EToString(uhe));
		}		
	}
	
	public boolean removeNode(String ip) {
		ICMPNode node;
		
		synchronized (this.nodeList) {
			node = this.nodeList.remove(ip);
		}
		
		if (node == null) {
			return false;
		}
		
		try {
			node.stop();
		} catch (InterruptedException ie) {
			Agent.log(Util.EToString(ie));
		}
		
		return true;
	}
	
	public ICMPNode getNode(String ip) {
		synchronized(this.nodeList) {
			return this.nodeList.get(ip);
		}
	}
	
	public void testNode(final String ip) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				boolean isReachable = false;
				
				try {
					isReachable = InetAddress.getByName(ip).isReachable(Agent.DEF_TIMEOUT);
				} catch (IOException e) {
					Agent.log(Util.EToString(e));
				}
				
				if (!isReachable) {
					Agent.log.write(ip, String.format("%s ICMP 등록 실패.", ip), "shutdown", false, false);
				}
				else {
					monitorTable.getJSONObject().put(ip, new JSONObject()
						.put("protocol", "icmp")
						.put("ip", ip)
						.put("shutdown", false));
					
					try {
						monitorTable.save();
					} catch (IOException ioe) {
						Agent.log(Util.EToString(ioe));
					}
					
					addNode(ip);
					
					Agent.log.write(ip, String.format("%s ICMP 등록 성공.", ip), "shutdown", true, false);
				}
			}
			
		}).start();
	}
	
	public void onSuccess(String ip, long time) {
		ICMPNode node;
		
		synchronized (this.nodeList) {
			node = this.nodeList.get(ip);
		}
		
		if (node == null) {
			return;
		}
	
		JSONObject monitor = this.monitorTable.getJSONObject(ip);
		
		if (monitor == null) {
			return;
		}
		
		if (monitor.getBoolean("shutdown")) {
			monitor.put("shutdown", false);
			
			try {
				this.monitorTable.save();
			} catch (IOException ioe) {
				Agent.log(Util.EToString(ioe));
			}
			
			Agent.log.write(ip, String.format("%s ICMP 정상.", ip), "shutdown", true, true);
		}
	}
	
	public void onFailure(String ip) {
		ICMPNode node;
		
		synchronized (this.nodeList) {
			node = this.nodeList.get(ip);
		}
		
		if (node == null) {
			return;
		}
		
		JSONObject monitor = this.monitorTable.getJSONObject(ip);
		
		if (monitor == null) {
			return;
		}
		
		if (!monitor.getBoolean("shutdown")) {
			monitor.put("shutdown", true);
			
			try {
				this.monitorTable.save();
			} catch (IOException ioe) {
				Agent.log(Util.EToString(ioe));
			}
			
			Agent.log.write(ip, String.format("%s ICMP 응답 없음.", ip), "shutdown", false, true);
		}
	}
	
	/**
	 * ovverride
	 */
	@Override
	public void close() {
		Exception e = null;
		
		synchronized (this.nodeList) {
			for (ICMPNode node : this.nodeList.values()) {
				try {
					node.stop();
				} catch (InterruptedException ie) {
					e = ie;
				}
			}
		}
		
		this.nodeList.clear();
		
		System.out.format("ICMP manager stop.\n");
		
		if (e != null) {
			Agent.log(Util.EToString(e));
		}
	}
	
}
