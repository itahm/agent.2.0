package com.itahm.json;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Calendar;

import com.itahm.json.JSONObject;
import com.itahm.util.Util;

/**
 * The Class RollingFile.
 */
public class RollingFile {
	
	private long load;
	
	/** The lastHour. */
	private long lastHour = -1;
	private long lastDay = -1;
	
	/** rollingRoot, itahm/snmp/ip address/resource/index */
	private final File root;
	
	private File summaryFile;
	private JSONObject summaryData;
	private JSONObject summary;
	private String summaryHour;
	
	private File dayDirectory;
	private File hourFile;
	private JSONObject hourData;
	private long max;
	private long min;
	private BigInteger hourSum = BigInteger.valueOf(0);
	private int hourCnt = 0;
	private BigInteger minuteSum = BigInteger.valueOf(0);
	private long minuteSumCnt = 0;
	
	/**
	 * Instantiates a new rolling file.
	 *
	 * @param root the root (itahm\snmp\ip\resource)
	 * @param index the index of host, interfaces, etc.
	 * @param type gauge or counter
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public RollingFile(File rscRoot, String index) throws IOException {
		Calendar c = Calendar.getInstance();
		
		c.set(Calendar.MILLISECOND, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MINUTE, 0);
		
		root = new File(rscRoot, index);
		root.mkdir();
		
		this.lastHour = c.getTimeInMillis();
		this.summaryHour = Long.toString(this.lastHour);
				
		c.set(Calendar.HOUR_OF_DAY, 0);
		
		this.lastDay = c.getTimeInMillis();
		
		this.dayDirectory = new File(this.root, Long.toString(this.lastDay));
		this.dayDirectory.mkdir();
		
		// summary file 생성
		this.summaryFile = new File(this.dayDirectory, "summary");
		if (this.summaryFile.isFile()) {
			this.summaryData = Util.getJSONFromFile(this.summaryFile);
		}
		
		// 최초 생성되거나, 파일이 깨졌을때
		if (this.summaryData == null) {
			Util.putJSONtoFile(this.summaryFile, this.summaryData = new JSONObject());
		}
		
		if (this.summaryData.has(this.summaryHour)) {
			summary = this.summaryData.getJSONObject(this.summaryHour);
		}
		else {
			this.summaryData.put(this.summaryHour, summary = new JSONObject());
		}
		
		// hourly file 생성
		this.hourFile = new File(this.dayDirectory, this.summaryHour);
		if (this.hourFile.isFile()) {
			this.hourData = Util.getJSONFromFile(this.hourFile);
		}
		
		// 최초 생성되거나, 파일이 깨졌을때
		if (this.hourData == null) {
			Util.putJSONtoFile(this.hourFile, this.hourData = new JSONObject());
		}
	}
	
	/**
	 * Roll.
	 *
	 * @param value the value
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void roll(long value) throws IOException {
		Calendar c = Calendar.getInstance();
		String minString;
		long hourMills, dayMills, elapse;

		c.set(Calendar.MILLISECOND, 0);
		c.set(Calendar.SECOND, 0);
		
		minString = Long.toString(c.getTimeInMillis());
		
		c.set(Calendar.MINUTE, 0);
		hourMills = c.getTimeInMillis();
		
		if (this.lastHour != hourMills) {
			// 시간마다 summary 파일과 시간파일 저장
			elapse = System.currentTimeMillis();
			
			Util.putJSONtoFile(this.summaryFile, this.summaryData);
			Util.putJSONtoFile(this.hourFile, this.hourData);
			
			this.load = System.currentTimeMillis() - elapse;
			
			c.set(Calendar.HOUR_OF_DAY, 0);
			dayMills = c.getTimeInMillis();
			
			if (this.lastDay != dayMills) {
				this.lastDay = dayMills;
				
				// day directory 생성
				this.dayDirectory = new File(this.root, Long.toString(dayMills));
				this.dayDirectory.mkdir();
				
				// summary file 생성
				this.summaryFile = new File(this.dayDirectory, "summary");
				this.summaryData = new JSONObject();
			}
			
			// hourly file 생성
			this.lastHour = hourMills;
			this.summaryHour = Long.toString(hourMills);
			this.hourFile = new File(this.dayDirectory, this.summaryHour);
			this.hourData = new JSONObject();
			this.hourCnt = 0;
			this.summaryData.put(this.summaryHour, this.summary = new JSONObject());
		}
		
		roll(minString, value);
	}
	
	private void roll(String minuteString, long value) throws IOException {
		if (this.hourData.has(minuteString)) {
			this.minuteSum = this.minuteSum.add(BigInteger.valueOf(value));
		}
		else {
			this.minuteSum = BigInteger.valueOf(value);
			this.minuteSumCnt = 0;
		}
		
		this.minuteSumCnt++;
		
		this.hourData.put(minuteString, this.minuteSum.divide(BigInteger.valueOf(this.minuteSumCnt)));
		
		if (this.hourCnt == 0) {
			this.hourSum = BigInteger.valueOf(value);
			this.max = value;
			this.min = value;
		}
		else {
			this.hourSum = this.hourSum.add(BigInteger.valueOf(value));
			this.max = Math.max(this.max, value);
			this.min = Math.min(this.min, value);
		}
		
		this.hourCnt++;
		
		summarize(this.hourSum.divide(BigInteger.valueOf(this.hourCnt)).longValue());
	}
	
	private void summarize(long avg) throws IOException {
		this.summary
			.put("avg", avg)
			.put("max", Math.max(avg, this.max))
			.put("min", Math.min(avg, this.min));
	}
	
	public JSONObject getData(long start, long end, boolean summary) throws IOException {
		JSONObject data;
		long now = Calendar.getInstance().getTimeInMillis();
		
		if (summary) {
			data = new JSONSummary(this.root).getJSON(start, end);

			if (start < now && now < end) {
				for (Object key : this.summaryData.keySet()) {
					data.put((String)key, this.summaryData.getJSONObject((String)key));
				}
			}
		}
		else {
			data = new JSONData(this.root).getJSON(start, end);
			
			if (start < now && now < end) {
				for (Object key : this.hourData.keySet()) {
					data.put((String)key, this.hourData.getLong((String)key));
				}
			}
		}
			
		return data;
	}
	
	public long getLoad() {
		return this.load;
	}
	
}

