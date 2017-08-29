package com.itahm.icmp;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ICMPNode extends Thread implements Closeable {

	private final ICMPListener listener;
	private final InetAddress target;
	private final int [] timeouts;
	private final int retry;
	private final BlockingQueue<Long> bq = new LinkedBlockingQueue<>();
	
	public final String ip;
	
	public ICMPNode(ICMPListener listener, String ip, int [] timeouts) throws UnknownHostException {
		this.listener = listener;
		this.ip = ip;
		this.timeouts = timeouts;
		
		target = InetAddress.getByName(ip);
		retry = timeouts.length;
		
		setDaemon(true);
		start();
	}
	
	@Override
	public void run() {
		long sent, delay;
		
		init: while (!Thread.interrupted()) {
			try {
				try {
					delay = bq.take();
					
					if (delay > 0) {
						Thread.sleep(delay);
					}
					
					sent = System.currentTimeMillis();
					
					for (int i=0; i < retry; i++) {
						if (this.target.isReachable(timeouts[i])) {
							this.listener.onSuccess(this, System.currentTimeMillis() - sent);
							
							continue init;
						}
					}
				} catch (IOException e) {}
				
				this.listener.onFailure(this);
				
			} catch (InterruptedException e) {
				break;
			}
		}
	}
	
	public void ping(long delay) {
		try {
			bq.put(delay);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void close() throws IOException {
		interrupt();
		
		try {
			join();
		} catch (InterruptedException e) {
		}
	}
	
}
