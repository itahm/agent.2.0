package com.itahm;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.itahm.ITAhMAgent;
import com.itahm.http.Listener;
import com.itahm.http.Request;
import com.itahm.http.Response;
import com.itahm.json.JSONException;
import com.itahm.json.JSONObject;

public class ITAhM extends Listener implements Closeable {
	
	private final static String DATA = "data";
	
	enum Options {
		PATH, TCP;
	}
	
	private final File root;
	private ITAhMAgent agent;
	
	public ITAhM(int tcp, File root, boolean clean) throws Exception {
		super("0.0.0.0", tcp);
		
		System.out.format("ITAhM communicator started with TCP %d.\n", tcp);
		
		this.root = root == null? new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile(): root;
		
		System.out.format("Directory : %s\n", this.root.getAbsoluteFile());
		
		System.out.format("Agent loading...\n");
		
		this.agent = new Agent();
		
		File dataRoot = new File(root, DATA);
		dataRoot.mkdir();
		
		this.agent.start(dataRoot);
	}
	
	@Override
	protected void onStart() {
		System.out.println("HTTP Server start.");
	}

	@Override
	protected void onRequest(Request request) throws IOException {
		processRequest(request);
	}
	
	@Override
	protected void onClose(Request request) {	
		this.agent.closeRequest(request);
	}
	
	@Override
	protected void onException(Exception e) {
		Agent.log.sysLog(e.getMessage());
	}
	
	public static void sendResponse(Request request, Response response) throws IOException {
		String origin = request.getRequestHeader(Request.Header.ORIGIN);
		
		if (origin == null) {
			origin = "http://itahm.com";
		}
	
		response.setResponseHeader("Access-Control-Allow-Origin", origin);
		response.setResponseHeader("Access-Control-Allow-Credentials", "true");
		
		request.sendResponse(response);
	}
	
	private void processRequest(Request request) throws IOException{
		Response response = parseRequest(request);
		
		if (response != null) { /* listen인 경우 null*/
			sendResponse(request, response);
		}
	}
	
	private Response parseRequest(Request request) throws IOException{
		if (!"HTTP/1.1".equals(request.getRequestVersion())) {
			return Response.getInstance(Response.Status.VERSIONNOTSUP);
		}
		
		switch(request.getRequestMethod()) {
		case "OPTIONS":
			return Response.getInstance(Response.Status.OK).setResponseHeader("Allow", "GET, POST");
		
		case"POST":
			JSONObject data;
			
			try {
				data = new JSONObject(new String(request.getRequestBody(), StandardCharsets.UTF_8.name()));
				
				if (!data.has("command")) {
					return Response.getInstance(Response.Status.BADREQUEST, new JSONObject().put("error", "command not found").toString());
				}
			} catch (JSONException e) {
				return Response.getInstance(Response.Status.BADREQUEST, new JSONObject().put("error", "invalid json request").toString());
			} catch (UnsupportedEncodingException e) {
				return Response.getInstance(Response.Status.BADREQUEST, new JSONObject().put("error", "UTF-8 encoding required").toString());
			}
			
			return this.agent.executeRequest(request, data);
			
		case "GET":
			String uri = request.getRequestURI();
			File file = new File(this.root, uri);
			
			if (!Pattern.compile("^/data/.*").matcher(uri).matches() && file.isFile()) {
				return Response.getInstance(file);
			}
			
			return Response.getInstance(Response.Status.NOTFOUND);
		}
		
		return Response.getInstance(Response.Status.NOTALLOWED).setResponseHeader("Allow", "OPTIONS, POST, GET");
	}
	
	@Override
	public void close() throws IOException {
		super.close();
		
		this.agent.stop();
	}
	
	public static void download(URL url, File output) throws IOException {
		HttpURLConnection hurlc = (HttpURLConnection)url.openConnection();
		
		hurlc.setConnectTimeout(3000);
		
		try {
			hurlc.setRequestMethod("GET");
			hurlc.connect();
		
			if (hurlc.getResponseCode() == 200) {
				try (InputStream is = hurlc.getInputStream()) {
					try (FileOutputStream fos = new FileOutputStream(output)) {
						int length;
						
						byte [] buffer = new byte [1024];
						
						while((length = is.read(buffer)) != -1) {
							fos.write(buffer, 0, length);
						}
					}
				}	
			}
			else {
				throw new IOException("HTTP status "+ hurlc.getResponseCode());
			}
		}
		finally {
			hurlc.disconnect();
		}
	}
	
	public static void main(String[] args) {
		int tcp = 2014;
		File path = null;
		boolean clean = true;
		
		System.out.format("ITAhM Agent, since 2014.\n");
		
		for (int i=0, _i=args.length; i<_i; i++) {
			if (args[i].indexOf("-") != 0) {
				System.out.println("잘못된 옵션 형식이 입력되어 실행을 중단합니다.");
				
				return;
			}
			
			try {
				switch(Options.valueOf(args[i].substring(1).toUpperCase())) {
				case PATH:
					path = new File(args[++i]);
					
					if (!path.isDirectory()) {
						System.out.println("PATH 옵션에 존재하지 않는 경로가 입력되어 실행을 중단합니다.");
						
						return;
					}
					
					break;
				case TCP:
					try {
						tcp = Integer.parseInt(args[++i]);
					}
					catch(NumberFormatException nfe) {
						System.out.println("TCP 옵션에 숫자가 아닌 값이 입력되어 실행을 중단합니다.");
						
						return;
					}
					
					break;
				}
			}
			catch(IllegalArgumentException iae) {
				break;
			}
		}
		
		try {
			final ITAhM itahm = new ITAhM(tcp, path, clean);
			
			Runtime.getRuntime().addShutdownHook(
				new Thread() {
					public void run() {
						try {
							itahm.close();
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}
				});
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
