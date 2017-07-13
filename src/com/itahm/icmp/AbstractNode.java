package com.itahm.icmp; 

import java.io.IOException;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Calendar;

public abstract class AbstractNode implements Runnable {

	private long interval;
	private int timeout;
	private final InetAddress target;
	private Thread thread;

	public AbstractNode(String host, int timeout, long interval) throws UnknownHostException {
		target = Inet4Address.getByName(host);
		this.timeout = timeout;
		this.interval = interval;
	}
	
	public void setInterval(long interval) {
		this.interval = interval;
	}
	
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	
	public synchronized boolean start() {
		if (this.thread != null) {
			return false;
		}
		
		this.thread = new Thread(this);
		this.thread.start();
		
		return true;
	}
	
	public synchronized void stop() throws InterruptedException {
		this.thread.interrupt();
		
		try {
			this.thread.join();
		} finally {
			this.thread = null;
		}
	}
	
	@Override
	public void run() {
		long sent;
		
		while(!Thread.interrupted()) {
			sent = Calendar.getInstance().getTimeInMillis();
			
			try {
				if (this.target.isReachable(this.timeout)) {
					onSuccess(Calendar.getInstance().getTimeInMillis() - sent);
				}
				else {
					onFailure();
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
			
			try {
				Thread.sleep(this.interval);
			} catch (InterruptedException ie) {
				break;
			}
		}
	}

	abstract protected void onSuccess(long time);
	abstract protected void onFailure();
	
	public static void main(String[] args) throws UnknownHostException, InterruptedException {
		AbstractNode node = new AbstractNode("192.168.0.1", 3000, 1000) {

			@Override
			public void onSuccess(long time) {
			}

			@Override
			public void onFailure() {
			}
			
		};
		
		node.start();
		
		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
		}
		
		node.stop();
		
	}
	
}
