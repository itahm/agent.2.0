package com.itahm;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.itahm.ITAhMAgent;
import com.itahm.GCMManager;
import com.itahm.Log;
import com.itahm.SNMPAgent;
import com.itahm.ICMPAgent;
import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;
import com.itahm.command.Command;
import com.itahm.command.Commander;
import com.itahm.http.Request;
import com.itahm.http.Response;
import com.itahm.http.Session;
import com.itahm.table.Account;
import com.itahm.table.Config;
import com.itahm.table.Critical;
import com.itahm.table.Device;
import com.itahm.table.GCM;
import com.itahm.table.Monitor;
import com.itahm.table.Position;
import com.itahm.table.Profile;
import com.itahm.table.Table;
import com.itahm.enterprise.Enterprise;

public class Agent implements ITAhMAgent {

	public final static String VERSION = "2.0.3.0";
	public final static String API_KEY = "AIzaSyBg6u1cj9pPfggp-rzQwvdsTGKPgna0RrA";
	
	public final static int MAX_TIMEOUT = 10000;
	public final static int ICMP_INTV = 1000;
	public final static int MID_TIMEOUT = 5000;
	public final static int DEF_TIMEOUT = 3000;
	
	private static Map<Table.Name, Table> tables = new HashMap<>();
	
	public static boolean isDemo = false;
	private static Log log;
	private static GCMManager gcmm = null;
	private static SNMPAgent snmp;
	private static ICMPAgent icmp;
	public final static Enterprise enterprise = new Enterprise();
	private static JSONObject config;
	private final static Batch batch = new Batch();
	private static File root;
	private boolean isClosed = true;
	
	public Agent() {
		System.out.format("ITAhM Agent version %s ready.\n", VERSION);
	}
	
	public static void initialize() throws IOException {
		try {
			log = new Log(root);
			snmp = new SNMPAgent(root);
			icmp = new ICMPAgent();
			
			if (config.has("gcm")) {
				if (!config.isNull("gcm")) {
					gcmm = new GCMManager(API_KEY, config.getString("gcm"));
				}
			}
			
		} catch (IOException ioe) {
			close();
			
			throw ioe;
		}
	}
	
	public void start(File dataRoot) throws Exception {
		if (!this.isClosed) {
			return;
		}
		
		this.isClosed = false;
		
		root = dataRoot;
	
		tables.put(Table.Name.CONFIG, new Config(root));
		tables.put(Table.Name.ACCOUNT, new Account(root));
		tables.put(Table.Name.PROFILE, new Profile(root));
		tables.put(Table.Name.DEVICE, new Device(root));
		tables.put(Table.Name.POSITION, new Position(root));
		tables.put(Table.Name.MONITOR, new Monitor(root));
		tables.put(Table.Name.ICON, new Table(root, Table.Name.ICON));
		tables.put(Table.Name.CRITICAL, new Critical(root));
		tables.put(Table.Name.GCM, new GCM(root));
		tables.put(Table.Name.SMS, new Table(root, Table.Name.SMS));
		
		config = getTable(Table.Name.CONFIG).getJSONObject();
		
		initialize();
		
		batch.start(root);
	}
	
	public File getRoot() {
		return root;
	}
	
	private Session signIn(JSONObject data) {
		String username = data.getString("username");
		String password = data.getString("password");
		JSONObject accountData = getTable(Table.Name.ACCOUNT).getJSONObject();
		
		if (accountData.has(username)) {
			 JSONObject account = accountData.getJSONObject(username);
			 
			 if (account.getString("password").equals(password)) {
				// signin 성공, cookie 발행
				return Session.getInstance(account.getInt("level"));
			 }
		}
		
		return null;
	}

	private static Session getSession(Request request) {
		String cookie = request.getRequestHeader(Request.Header.COOKIE);
		
		if (cookie == null) {
			return null;
		}
		
		String [] cookies = cookie.split("; ");
		String [] token;
		Session session = null;
		
		for(int i=0, length=cookies.length; i<length; i++) {
			token = cookies[i].split("=");
			
			if (token.length == 2 && "SESSION".equals(token[0])) {
				session = Session.find(token[1]);
				
				if (session != null) {
					session.update();
				}
			}
		}
		
		return session;
	}
	
	public static Table getTable(Table.Name name) {
		return tables.get(name);
	}
	
	public static Table getTable(String name) {
		try {
			return tables.get(Table.Name.getName(name));
		}
		catch (IllegalArgumentException iae) {
			return null;
		}
	}
	
	public static JSONObject backup() {
		JSONObject backup = new JSONObject();
		
		for (Table.Name name : Table.Name.values()) {
			backup.put(name.toString(), getTable(name).getJSONObject());
		}
		
		return backup;
	}
	
	public static boolean clean() {
		if (snmp == null) {
			return false;
		}
		
		int day = config.getInt("clean");
		
		if (day > 0) {
			snmp.clean(day);
		}
		
		return true;
	}
	
	public static int getRollingInterval() {
		return config.getInt("interval");
	}
	
	public static void config(String key, Object value) throws IOException {
		config.put(key, value);
		
		getTable(Table.Name.CONFIG).save();
	}
	
	public static void log(String ip, String message, String type, boolean status, boolean broadcast) {
		if (log != null) {
			log.write(ip, message, type, status, broadcast);
		}
	}
	
	public static void sendEvent(String message) {
		if (gcmm != null) {
			gcmm.broadcast(message);
		}
	
		if (config.has("sms") && config.getBoolean("sms")) {
			enterprise.sendEvent(message);
		}
	}
	
	public static void syslog(String msg) {
		if (log != null) {
			Calendar c = Calendar.getInstance();
		
			log.sysLog(String.format("%04d-%02d-%02d %02d:%02d:%02d %s"
				, c.get(Calendar.YEAR)
				, c.get(Calendar.MONTH) +1
				, c.get(Calendar.DAY_OF_MONTH)
				, c.get(Calendar.HOUR_OF_DAY)
				, c.get(Calendar.MINUTE)
				, c.get(Calendar.SECOND), msg));
		}
	}
	
	public static void restore(JSONObject backup) throws Exception {
		Table.Name name;
		
		close();
		
		for (Object key : backup.keySet()) {
			name = Table.Name.getName((String)key);
			
			if (name != null) {
				Agent.getTable(name).save(backup.getJSONObject(name.toString()));
			}
		}
		
		initialize();
	}
	
	public static long calcLoad() {
		if (snmp == null) {
			return -1;
		}
		
		return snmp.calcLoad();
	}
	
	public static boolean removeSNMPNode(String ip) {
		if (snmp == null) {
			return false;
		}
		
		return snmp.removeNode(ip);
	}
	
	public static void testSNMPNode(String ip, boolean onFailure) {
		if (snmp != null) {
			snmp.testNode(ip, onFailure);
		}
	}
	
	public static boolean removeICMPNode(String ip) {
		if (icmp == null) {
			return false;
		}
		
		return icmp.removeNode(ip);
	}
	
	public static void testICMPNode(String ip) {
		if (icmp != null) {
			icmp.testNode(ip);
		}
	}
	
	public static void resetCritical(String ip, JSONObject critical) {
		if (snmp != null) {
			snmp.resetCritical(ip, critical);
		}
	}
	
	public static boolean addUSM(JSONObject usm) {
		if (snmp == null) {
			return false;
		}
		
		return snmp.addUSM(usm);
	}
	
	public static void removeUSM(String usm) {
		if (snmp != null) {
			snmp.removeUSM(usm);
		}
	}
	
	public static boolean isIdleProfile(String name) {
		if (snmp == null) {
			return false;
		}
		
		return snmp.isIdleProfile(name);
	}
	
	public static SNMPNode getNode(String ip) {
		if (snmp == null) {
			return null;
		}
		
		return snmp.getNode(ip); 
	}
	
	public static JSONObject getNodeData(String ip, boolean offline) {
		return snmp == null? null: snmp.getNodeData(ip, offline);
	}
	
	public static Response executeEnterprise(Request request, JSONObject data) {
		return snmp == null? Response.getInstance(Response.Status.UNAVAILABLE):
			snmp.executeEnterprise(request, data);
	}
	
	public static JSONObject snmpTest() {
		return snmp == null? new JSONObject(): snmp.test();
	}
	
	public static JSONObject getTop(int count) {
		return snmp == null? new JSONObject(): snmp.getTop(count);
	}
	
	public static String report(long start, long end) throws IOException {
		return log == null? new JSONObject().toString(): log.read(start, end);
	}
	
	public static void resetResponse(String ip) {
		if (snmp != null) {
			snmp.resetResponse(ip);
		}
	}
	
	public static JSONObject getFailureRate(String ip) {
		return snmp == null? null: snmp.getFailureRate(ip);
	}
	
	public static String getLog(long date) throws IOException {
		return log == null? new JSONObject().toString(): log.read(date);
	}
	
	public static String getSyslog(long date) throws IOException {
		return log == null? new JSONObject().toString(): log.getSysLog(date);
	}
	
	public static void listen(Request request, long index) throws IOException {
		if (log == null) {
			throw new IOException("Service unavailable.");
		}
		
		log.listen(request, index);
	}
	
	public static void setGCM(String host) throws IOException {
		if (host == null) {
			if (gcmm != null) {
				gcmm.close();
				
				gcmm = null;
			}
		}
		else {
			if (gcmm == null) {
				gcmm = new GCMManager(API_KEY, host);
			}
		}
	}
	
	public static void register(String token, String id) {
		if (gcmm != null) {
			if (id == null) {
				gcmm.unregister(token);
			}
			else {
				gcmm.register(token, id);
			}
		}
	}
	
	public static void close() {
		if (snmp != null) {
			snmp.close();
		}
		
		if (icmp != null) {
			icmp.close();
		}
		
		if (gcmm != null) {
			gcmm.close();
		}
	}
	
	@Override
	public Response executeRequest(Request request, JSONObject data) {
		if (this.isClosed) {
			return Response.getInstance(Response.Status.SERVERERROR);
		}
		
		String cmd = data.getString("command");
		Session session = getSession(request);
		
		if ("signin".equals(cmd)) {
			if (session == null) {
				try {
					session = signIn(data);
				} catch (JSONException jsone) {
					return Response.getInstance(Response.Status.BADREQUEST
						, new JSONObject().put("error", "invalid json request").toString());
				}
			}
			
			if (session == null) {
				return Response.getInstance(Response.Status.UNAUTHORIZED);
			}
			
			return Response.getInstance(Response.Status.OK, new JSONObject()
				.put("level", (int)session.getExtras())
				.put("version", VERSION).toString())
					.setResponseHeader("Set-Cookie", String.format("SESSION=%s; HttpOnly", session.getCookie()));
		}
		else if ("signout".equals(cmd)) {
			if (session != null) {
				session.close();
			}
			
			return Response.getInstance(Response.Status.OK);
		}
		
		Command command = Commander.getCommand(cmd);
		
		if (command == null) {
			return Response.getInstance(Response.Status.BADREQUEST
				, new JSONObject().put("error", "invalid command").toString());
		}
		
		try {
			if ("put".equals(cmd) && "gcm".equals(data.getString("database"))) {
				return command.execute(request, data);
			}
			
			if (session != null) {
				return command.execute(request, data);
			}
		}
		catch (IOException ioe) {
			return Response.getInstance(Response.Status.UNAVAILABLE
				, new JSONObject().put("error", ioe).toString());
		}
			
		return Response.getInstance(Response.Status.UNAUTHORIZED);
	}

	@Override
	public void closeRequest(Request request) {
		log.cancel(request);
	}

	@Override
	public void stop() {
		if (this.isClosed) {
			return;
		}
		
		this.isClosed = true;
		
		close();
		
		batch.stop();
		
		enterprise.close();
		
		System.out.println("ITAhM agent down.");
	}

	public static void getInformation(JSONObject jsono) {
		jsono.put("space", root == null? 0: root.getUsableSpace())
		.put("version", VERSION)
		.put("load", batch.load)
		.put("resource", snmp.getResourceCount())
		.put("usage", batch.lastDiskUsage)
		.put("java", System.getProperty("java.version"))
		.put("expire", ITAhM.expire)
		.put("demo", isDemo);
	}
	
	@Override
	public Object get(String key) {
		switch(key) {

		default:
			return null;
		}
	}

	@Override
	public void set(String key, Object value) {
		switch(key) {
		default:
		}
	}

}