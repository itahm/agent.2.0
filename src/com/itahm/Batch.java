package com.itahm;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class Batch {
	private final static long DISK_MON_INTV = 10000;
	
	private Timer timer;
	
	public static long lastDiskUsage = 0;
	
	public boolean start(final File root) {
		if (this.timer != null) {
			return false;
		}
		
		this.timer = new Timer();
		
		scheduleDiskMonitor(root);
		scheduleUsageMonitor(new File(root, "node"));
		
		return true;
	}
	
	public boolean stop() {
		if (this.timer == null) {
			return false;
		}
		
		this.timer.cancel();
			
		this.timer = null;
		
		return true;
	}
	
	private final void scheduleUsageMonitor(final File nodeRoot) {
		Calendar c = Calendar.getInstance();
		
		c.set(Calendar.DATE, c.get(Calendar.DATE) +1);
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		
		this.timer.schedule(new TimerTask() {

			@Override
			public void run() {
				Calendar c = Calendar.getInstance();
				File dir;
				long size = 0;
				
				c.set(Calendar.DATE, c.get(Calendar.DATE) -1);
				c.set(Calendar.HOUR_OF_DAY, 0);
				c.set(Calendar.MINUTE, 0);
				c.set(Calendar.SECOND, 0);
				c.set(Calendar.MILLISECOND, 0);
				
				if (nodeRoot.isDirectory()) {
					for (File node: nodeRoot.listFiles()) {
						try {
							InetAddress.getByName(node.getName());
							
							if (node.isDirectory()) {
								for (File rsc : node.listFiles()) {
									if (rsc.isDirectory()) {
										for (File index : rsc.listFiles()) {
											if (index.isDirectory()) {
												dir = new File(index, Long.toString(c.getTimeInMillis()));
												
												if (dir.isDirectory()) {
													
													for (File file : dir.listFiles()) {
														size += file.length();
													}
												}
											}
										}
									}
								}
							}
						} catch (UnknownHostException uhe) {
						}
					}
				}
				
				lastDiskUsage = size;
			}
		}, c.getTime(), 24 * 60 * 60 * 1000);
	}
	
	private final void scheduleDiskMonitor(final File root) {
		this.timer.schedule(new TimerTask() {
			private final static long MAX = 100;
			private final static long CRITICAL = 10;
			
			private long lastFreeSpace = MAX;
			private long freeSpace;
			
			@Override
			public void run() {
					freeSpace = MAX * root.getUsableSpace() / root.getTotalSpace();
					
					if (freeSpace < lastFreeSpace && freeSpace < CRITICAL) {
						Agent.log.write("ITAhM", String.format("저장 여유공간 %d%%", freeSpace), "system", false, true);
					}
					
					lastFreeSpace = freeSpace;
					
			}
		}, 0, DISK_MON_INTV);
	}

}
