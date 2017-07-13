package com.itahm.snmp;

import java.io.IOException;

import java.net.InetAddress;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.itahm.Agent;
import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.Integer32;
//import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.Null;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;

public abstract class Node extends Thread {
	
	private final static int MAX_REQUEST = 100;
	private final static int CISCO = 9;
	private final static int DASAN = 6296;
	
	private final static int [] TIMEOUTS = new int [] {2000, 3000, 5000};
	private final static int TIMEOUT_COUNT = TIMEOUTS.length;
	
	public PDU pdu;
	private PDU nextPDU;
	private final Snmp snmp;
	private final InetAddress ip;
	
	private Target target;
	private Integer enterprise;
	private long failureCount = 0;
	private boolean isInitialized = false;
	private final BlockingQueue<PDU> bq = new LinkedBlockingQueue<>();
	private boolean processing = false;
	
	protected long lastResponse;
	protected long responseTime;
	/**
	 * 이전 데이터 보관소
	 */
	protected final JSONObject data = new JSONObject();
	
	/**
	 * 최신 데이터 보관소
	 */
	protected final Map<String, Integer> hrProcessorEntry = new HashMap<>();
	protected final Map<String, JSONObject> hrStorageEntry = new HashMap<>();
	protected final Map<String, JSONObject> ifEntry = new HashMap<>();
	/*protected final Map<String, String> arpTable = new HashMap<>(); // mac - ip
	protected final Map<String, Integer> macTable = new HashMap<>(); // mac - index
	protected final Map<String, Integer> ipTable = new HashMap<>(); // ip - index
	protected final Map<String, Integer> remoteIPTable = new HashMap<>(); //ip - index
	protected final Map<String, String> networkTable = new HashMap<>(); //ip - mask
	protected final Map<Integer, String> maskTable = new HashMap<>(); //index - mask
	*/
	public Node(Snmp snmp, String ip, int timeout) throws IOException {
		this.snmp = snmp;
		this.ip = InetAddress.getByName(ip);
		
		start();
	}
	
	private static long ping(InetAddress ip) throws IOException {
		long sent = System.currentTimeMillis();
		
		for (int i=0; i < TIMEOUT_COUNT; i++) {
			if (ip.isReachable(TIMEOUTS[i])) {
				return System.currentTimeMillis() - sent;
			}
		}
		
		return -1;
	}
	
	@Override
	public void run() {
		PDU pdu;
		long rt;
		
		while (!Thread.interrupted()) {
			try {
				pdu = this.bq.take();
				
				if (!this.processing) {
					this.processing = true;
					
					rt = ping(this.ip);
					
					if (rt < 0) {
						onTimeout(true);
						
						continue;
					}
					
					data.put("responseTime", this.responseTime = rt);
					
					onTimeout(false);
				}
				
				parseResponse(this.snmp.send(pdu, this.target));
			} catch (InterruptedException ie) {
				ie.printStackTrace();
				
				break;
			} catch (IOException ioe) {
				onException(ioe);
			}
		}
	}
	
	public Node(Snmp snmp, String ip, int udp, OctetString user, int level, int timeout) throws IOException {
		this(snmp, ip, timeout);
		
		pdu = new ScopedPDU();
		pdu.setType(PDU.GETNEXT);
		
		nextPDU = new ScopedPDU();
		nextPDU.setType(PDU.GETNEXT);
		
		// target 설정
		target = new UserTarget();
		target.setAddress(new UdpAddress(InetAddress.getByName(ip), udp));
		target.setVersion(SnmpConstants.version3);
		target.setSecurityLevel(level);
		target.setSecurityName(user);
		target.setTimeout(timeout);
	}
	
	public Node(Snmp snmp, String ip, int udp, OctetString community, int timeout) throws IOException {
		this(snmp, ip, timeout);
		
		pdu = new PDU();
		pdu.setType(PDU.GETNEXT);
		
		nextPDU = new PDU();
		nextPDU.setType(PDU.GETNEXT);
		
		
		
		// target 설정
		try {
			target = new CommunityTarget(new UdpAddress(InetAddress.getByName(ip), udp), community);
		}
		catch (IOException ioe) {
			throw ioe;
		}
		
		target.setVersion(SnmpConstants.version2c);
		target.setTimeout(timeout);
	}
	
	private void setEnterprise(int enterprise) {
		switch(enterprise) {
		case CISCO:
			this.pdu.add(new VariableBinding(RequestOID.busyPer));
			this.pdu.add(new VariableBinding(RequestOID.cpmCPUTotal5sec));
			this.pdu.add(new VariableBinding(RequestOID.cpmCPUTotal5secRev));
			
			break;
			
		case DASAN:
			this.pdu.add(new VariableBinding(RequestOID.dsCpuLoad5s));
			this.pdu.add(new VariableBinding(RequestOID.dsTotalMem));
			this.pdu.add(new VariableBinding(RequestOID.dsUsedMem));
			break;
		}
	}
	
	public void request() throws IOException {
		// 존재하지 않는 index 지워주기 위해 초기화
		hrProcessorEntry.clear();
		hrStorageEntry.clear();
		ifEntry.clear();
		/*
		arpTable.clear();
		remoteIPTable.clear();
		macTable.clear();
		ipTable.clear();
		networkTable.clear();
		maskTable.clear();
		*/
		this.pdu.setRequestID(new Integer32(0));
		
		this.processing = false;
		
		this.bq.add(this.pdu);
	}
	
	public long getFailureRate() {		
		return this.failureCount;
	}
	
	public void resetResponse() {
		this.failureCount = 0;
	}

	public JSONObject getData() {
		if (!this.isInitialized) {
			return null;
		}
		
		this.data.put("failure", getFailureRate());
		
		return this.data;
	}
	
	private final boolean parseSystem(OID response, Variable variable, OID request) {
		if (request.startsWith(RequestOID.sysDescr) && response.startsWith(RequestOID.sysDescr)) {
			this.data.put("sysDescr", new String(((OctetString)variable).getValue()));
		}
		else if (request.startsWith(RequestOID.sysObjectID) && response.startsWith(RequestOID.sysObjectID)) {
			this.data.put("sysObjectID", ((OID)variable).toDottedString());
			
			if (this.enterprise == null) {
				this.enterprise = ((OID)variable).size() > 6? ((OID)variable).get(6): -1;
				
				setEnterprise(this.enterprise);
			}
		}
		else if (request.startsWith(RequestOID.sysName) && response.startsWith(RequestOID.sysName)) {
			this.data.put("sysName", new String(((OctetString)variable).getValue()));
		}
		
		return false;
	}
	
	private final boolean parseIFEntry(OID response, Variable variable, OID request) throws IOException {
		String index = Integer.toString(response.last());
		JSONObject ifData = this.ifEntry.get(index);
		
		if(ifData == null) {
			ifData = new JSONObject();
					
			this.ifEntry.put(index, ifData);
			
			ifData.put("ifInBPS", 0);
			ifData.put("ifOutBPS", 0);
		}
		
		if (request.startsWith(RequestOID.ifDescr) && response.startsWith(RequestOID.ifDescr)) {
			ifData.put("ifDescr", new String(((OctetString)variable).getValue()));
		}
		else if (request.startsWith(RequestOID.ifType) && response.startsWith(RequestOID.ifType)) {			
			ifData.put("ifType", ((Integer32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.ifSpeed) && response.startsWith(RequestOID.ifSpeed)) {			
			ifData.put("ifSpeed", ((Gauge32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.ifPhysAddress) && response.startsWith(RequestOID.ifPhysAddress)) {
			byte [] mac = ((OctetString)variable).getValue();
			
			String macString = "";
			
			if (mac.length > 0) {
				macString = String.format("%02X", 0L |mac[0] & 0xff);
				
				for (int i=1; i<mac.length; i++) {
					macString += String.format("-%02X", 0L |mac[i] & 0xff);
				}
			}
			
			ifData.put("ifPhysAddress", macString);
		}
		else if (request.startsWith(RequestOID.ifAdminStatus) && response.startsWith(RequestOID.ifAdminStatus)) {
			ifData.put("ifAdminStatus", ((Integer32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.ifOperStatus) && response.startsWith(RequestOID.ifOperStatus)) {			
			ifData.put("ifOperStatus", ((Integer32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.ifInOctets) && response.startsWith(RequestOID.ifInOctets)) {
			ifData.put("ifInOctets", ((Counter32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.ifOutOctets) && response.startsWith(RequestOID.ifOutOctets)) {
			ifData.put("ifOutOctets", ((Counter32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.ifInErrors) && response.startsWith(RequestOID.ifInErrors)) {
			ifData.put("ifInErrors", ((Counter32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.ifOutErrors) && response.startsWith(RequestOID.ifOutErrors)) {
			ifData.put("ifOutErrors", ((Counter32)variable).getValue());
		}
		else {
			return false;
		}
		
		return true;
	}
	
	private final boolean parseIFXEntry(OID response, Variable variable, OID request) throws IOException {
		String index = Integer.toString(response.last());
		JSONObject ifData = this.ifEntry.get(index);
		
		if(ifData == null) {
			ifData = new JSONObject();
			
			this.ifEntry.put(index, ifData);
		}
		
		if (request.startsWith(RequestOID.ifName) && response.startsWith(RequestOID.ifName)) {
			ifData.put("ifName", new String(((OctetString)variable).getValue()));
		}
		else if (request.startsWith(RequestOID.ifAlias) && response.startsWith(RequestOID.ifAlias)) {
			ifData.put("ifAlias", new String(((OctetString)variable).getValue()));
		}
		else if (request.startsWith(RequestOID.ifHCInOctets) && response.startsWith(RequestOID.ifHCInOctets)) {
			ifData.put("ifHCInOctets", ((Counter64)variable).getValue());
		}
		else if (request.startsWith(RequestOID.ifHCOutOctets) && response.startsWith(RequestOID.ifHCOutOctets)) {
			ifData.put("ifHCOutOctets", ((Counter64)variable).getValue());
		}
		else if (request.startsWith(RequestOID.ifHighSpeed) && response.startsWith(RequestOID.ifHighSpeed)) {
			ifData.put("ifHighSpeed", ((Gauge32)variable).getValue() * 1000000L);
		}
		else {
			return false;
		}
		
		return true;
	}
	
	private final boolean parseHost(OID response, Variable variable, OID request) throws JSONException, IOException {
		if (request.startsWith(RequestOID.hrSystemUptime) && response.startsWith(RequestOID.hrSystemUptime)) {
			this.data.put("hrSystemUptime", ((TimeTicks)variable).toMilliseconds());
			
			return false;
		}
		
		String index = Integer.toString(response.last());
		
		if (request.startsWith(RequestOID.hrProcessorLoad) && response.startsWith(RequestOID.hrProcessorLoad)) {
			this.hrProcessorEntry.put(index, ((Integer32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.hrStorageEntry) && response.startsWith(RequestOID.hrStorageEntry)) {
			JSONObject storageData = this.hrStorageEntry.get(index);
			
			if (storageData == null) {
				storageData = new JSONObject();
				
				this.hrStorageEntry.put(index, storageData = new JSONObject());
			}
			
			if (request.startsWith(RequestOID.hrStorageType) && response.startsWith(RequestOID.hrStorageType)) {
				storageData.put("hrStorageType", ((OID)variable).last());
			}
			else if (request.startsWith(RequestOID.hrStorageDescr) && response.startsWith(RequestOID.hrStorageDescr)) {
				storageData.put("hrStorageDescr", new String(((OctetString)variable).getValue()));
			}
			else if (request.startsWith(RequestOID.hrStorageAllocationUnits) && response.startsWith(RequestOID.hrStorageAllocationUnits)) {
				storageData.put("hrStorageAllocationUnits", ((Integer32)variable).getValue());
			}
			else if (request.startsWith(RequestOID.hrStorageSize) && response.startsWith(RequestOID.hrStorageSize)) {
				storageData.put("hrStorageSize", ((Integer32)variable).getValue());
			}
			else if (request.startsWith(RequestOID.hrStorageUsed) && response.startsWith(RequestOID.hrStorageUsed)) {
				storageData.put("hrStorageUsed", ((Integer32)variable).getValue());
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
		
		return true;
	}
	/*
	private final boolean parseIP(OID response, Variable variable, OID request) {
		byte [] array = response.toByteArray();
		String ip = new IpAddress(new byte [] {array[array.length -4], array[array.length -3], array[array.length -2], array[array.length -1]}).toString();
		
		if (request.startsWith(RequestOID.ipAddrTable)) {
			if (request.startsWith(RequestOID.ipAdEntIfIndex) && response.startsWith(RequestOID.ipAdEntIfIndex)) {
				if (this.data.has("ifEntry")) {
					JSONObject ifEntry = this.data.getJSONObject("ifEntry");
					Integer index = ((Integer32)variable).getValue();
					
					if (ifEntry.has(index.toString())) {
						String mac = ifEntry.getJSONObject(index.toString()).getString("ifPhysAddress");
						
						this.arpTable.put(mac, ip);
						this.macTable.put(mac, index);
						this.ipTable.put(ip, index);
					}
				}
			}
			else if (request.startsWith(RequestOID.ipAdEntNetMask) && response.startsWith(RequestOID.ipAdEntNetMask)) {
				String mask = ((IpAddress)variable).toString();
				
				this.networkTable.put(ip, mask);
				
				this.maskTable.put(this.ipTable.get(ip), mask);
			}
			else {
				return false;
			}
		} else if (request.startsWith(RequestOID.ipNetToMediaTable)) {
			int index = array[array.length -5];
			
			if (request.startsWith(RequestOID.ipNetToMediaType) && response.startsWith(RequestOID.ipNetToMediaType)) {
				if (((Integer32)variable).getValue() == 3) {
					this.remoteIPTable.put(ip, index);
				}
			}
			else if (request.startsWith(RequestOID.ipNetToMediaPhysAddress) && response.startsWith(RequestOID.ipNetToMediaPhysAddress)) {
				if (this.remoteIPTable.containsKey(ip) && this.remoteIPTable.get(ip) == index) {
					byte [] mac = ((OctetString)variable).getValue();
					String macString = String.format("%02X", 0L |mac[0] & 0xff);
					
					for (int i=1; i<mac.length; i++) {
						macString += String.format("-%02X", 0L |mac[i] & 0xff);
					}
					
					this.macTable.put(macString, index);
					this.arpTable.put(macString, ip);
				}
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
		
		return true;
	}
	*/
	private final boolean parseCisco(OID response, Variable variable, OID request) {
		String index = Integer.toString(response.last());
		
		if (request.startsWith(RequestOID.busyPer) && response.startsWith(RequestOID.busyPer)) {
			this.hrProcessorEntry.put(index, (int)((Gauge32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.cpmCPUTotal5sec) && response.startsWith(RequestOID.cpmCPUTotal5sec)) {
			this.hrProcessorEntry.put(index, (int)((Gauge32)variable).getValue());
			
		}
		else if (request.startsWith(RequestOID.cpmCPUTotal5secRev) && response.startsWith(RequestOID.cpmCPUTotal5secRev)) {
			this.hrProcessorEntry.put(index, (int)((Gauge32)variable).getValue());
		}
		else {
			return false;
		}
		
		return true;
	}
	
	private final boolean parseDasan(OID response, Variable variable, OID request) {
		String index = Integer.toString(response.last());
		JSONObject storageData = this.hrStorageEntry.get(index);
		
		if (storageData == null) {
			storageData = new JSONObject();
			
			this.hrStorageEntry.put("0", storageData = new JSONObject());
			
			storageData.put("hrStorageType", 2);
			storageData.put("hrStorageAllocationUnits", 1);
		}
		
		if (request.startsWith(RequestOID.dsCpuLoad5s) && response.startsWith(RequestOID.dsCpuLoad5s)) {
			this.hrProcessorEntry.put(index, (int)((Integer32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.dsTotalMem) && response.startsWith(RequestOID.dsTotalMem)) {
			storageData.put("hrStorageSize", (int)((Integer32)variable).getValue());
		}
		else if (request.startsWith(RequestOID.dsUsedMem) && response.startsWith(RequestOID.dsUsedMem)) {
			storageData.put("hrStorageUsed", (int)((Integer32)variable).getValue());
		}
		else {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Parse.
	 * 
	 * @param response
	 * @param variable
	 * @param reqest
	 * @return true get-next가 계속 진행되는 경우
	 * @throws IOException 
	 */
	private final boolean parseResponse (OID response, Variable variable, OID request) throws IOException {
		// 1,3,6,1,2,1,1,5
		if (request.startsWith(RequestOID.system)) {
			return parseSystem(response, variable, request);
		}
		// 1,3,6,1,2,1,2,2,1
		else if (request.startsWith(RequestOID.ifEntry)) {
			return parseIFEntry(response, variable, request);
		}
		// 1,3,6,1,2,1,31,1,1,1
		else if (request.startsWith(RequestOID.ifXEntry)) {
			return parseIFXEntry(response, variable, request);
		}
		// 1,3,6,1,2,1,25
		else if (request.startsWith(RequestOID.host)) {
			return parseHost(response, variable, request);
		}/*
		// 1,3,6,1,2,1,4
		else if (request.startsWith(RequestOID.ip)) {
			return parseIP(response, variable, request);
		}*/
		else if (request.startsWith(RequestOID.enterprises)) {
			if (request.startsWith(RequestOID.cisco)) {
				return parseCisco(response, variable, request);
			}
			else if (request.startsWith(RequestOID.dasan)) {
				return parseDasan(response, variable, request);
			}
			else {
				return parseEnterprise(response, variable, request);
			}
		}
		
		return false;
	}
	
	public final boolean getNextRequest(PDU request, PDU response) throws IOException {
		Vector<? extends VariableBinding> requestVBs = request.getVariableBindings();
		Vector<? extends VariableBinding> responseVBs = response.getVariableBindings();
		Vector<VariableBinding> nextRequests = new Vector<VariableBinding>();
		VariableBinding requestVB, responseVB;
		Variable value;
		
		for (int i=0, length = responseVBs.size(); i<length; i++) {
			requestVB = (VariableBinding)requestVBs.get(i);
			responseVB = (VariableBinding)responseVBs.get(i);
			value = responseVB.getVariable();
			
			if (value == Null.endOfMibView) {
				continue;
			}
			
			try {
				if (parseResponse(responseVB.getOid(), value, requestVB.getOid())) {
					nextRequests.add(responseVB);
				}
			} catch(ClassCastException | JSONException e) { 
				Agent.log.sysLog(e.getMessage());
			}
		}
		
		this.nextPDU.clear();
		this.nextPDU.setRequestID(new Integer32(0));
		this.nextPDU.setVariableBindings(nextRequests);
		
		return nextRequests.size() > 0;
	}
		
	public void parseResponse(ResponseEvent event) throws IOException {
		PDU response = event.getResponse();
		
		if (response == null || event.getSource() instanceof Snmp.ReportHandler) {
			this.failureCount = Math.min(MAX_REQUEST, this.failureCount +1);
			
			onResponse(false);
			
			return;
		}
		
		PDU request = event.getRequest();
		int status = response.getErrorStatus();
		
		if (status != PDU.noError) {
			throw new IOException(String.format("Node %s reports error status %d", this.target.getAddress(), status));
		}
		
		if (getNextRequest(request, response)) {
			this.bq.add(this.nextPDU);
		}
		else {
			this.lastResponse = Calendar.getInstance().getTimeInMillis();
			this.data.put("lastResponse", this.lastResponse);
			
			this.failureCount = Math.max(0, this.failureCount -1);
			
			this.isInitialized = true;
			
			onResponse(true);
			
			this.data.put("hrProcessorEntry", this.hrProcessorEntry);
			this.data.put("hrStorageEntry", this.hrStorageEntry);
			this.data.put("ifEntry", this.ifEntry);
		}
	}
	
	abstract protected void onResponse(boolean success);
	abstract protected void onTimeout(boolean timeout);
	abstract protected void onException(Exception e);
	
	abstract protected boolean parseEnterprise(OID response, Variable variable, OID request);
	
	public static void main(String [] args) throws IOException {
	}
	
}
