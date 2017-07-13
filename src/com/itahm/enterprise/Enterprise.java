package com.itahm.enterprise;

import java.io.Closeable;

public class Enterprise implements Closeable {

	private static final int LICENSE = 0;
	
	public static Enterprise getInstance() {
		//return new KIER();
		
		return new Enterprise();
	}
	
	public Enterprise() {
	}
	
	public void sendEvent(String event) {
		System.out.println(event);
	}

	public int getLicense() {
		return LICENSE;
	}
	
	@Override
	public void close() {
	}

}
