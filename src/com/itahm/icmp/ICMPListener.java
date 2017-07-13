package com.itahm.icmp;

public interface ICMPListener {
	public void onSuccess(String host, long time);
	public void onFailure(String host);
}
