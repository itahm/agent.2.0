package com.itahm;

import java.net.UnknownHostException;

import com.itahm.icmp.AbstractNode;
import com.itahm.icmp.ICMPListener;

public class ICMPNode extends AbstractNode {

	//private static final int [] TIMEOUT = new int [] {Agent.DEF_TIMEOUT, Agent.MID_TIMEOUT, Agent.MAX_TIMEOUT};
	private static final int [] TIMEOUT = new int [] {Agent.DEF_TIMEOUT, Agent.MID_TIMEOUT};
	private static final int MAX_RETRY = TIMEOUT.length -1; 
	
	private final ICMPListener listener;
	private final String host;
	private int failure = 0;
	
	public ICMPNode(ICMPListener listener, String host) throws UnknownHostException {
		super(host, Agent.DEF_TIMEOUT, Agent.ICMP_INTV);
		
		this.host = host;
		this.listener = listener;
	}
	
	@Override
	public void onSuccess(long time) {
		if (failure > 0) {
			failure = 0;
			
			super.setTimeout(TIMEOUT[failure]);
		}
		
		listener.onSuccess(this.host, time);
	}
	
	@Override
	public void onFailure() {
		if (failure < MAX_RETRY) {
			failure++;
			
			super.setTimeout(TIMEOUT[failure]);
		}
		else {
			this.listener.onFailure(host);
		}
	}
	
	public static void main(String[] args) throws UnknownHostException, InterruptedException {
		ICMPNode node = new ICMPNode(new ICMPListener() {

			@Override
			public void onSuccess(String host, long time) {
			}

			@Override
			public void onFailure(String host) {
			}
			
		}, args[0]);
		
		node.start();
		
		try {
			Thread.sleep(100000);
		} catch (InterruptedException e) {
		}
		
		node.stop();
	}
	
}
