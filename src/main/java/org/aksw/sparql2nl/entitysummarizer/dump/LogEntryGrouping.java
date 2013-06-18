/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer.dump;

import java.util.Collection;
import java.util.Date;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

/**
 * @author Lorenz Buehmann
 *
 */
public class LogEntryGrouping {

	public static Multimap<String, LogEntry> groupByIPAddress(Collection<LogEntry> entries){
		Multimap<String, LogEntry> ip2Entries = TreeMultimap.create();
		for (LogEntry entry : entries) {
			ip2Entries.put(entry.getIp(), entry);
		}
		return ip2Entries;
	}
	
	public static Multimap<Date, LogEntry> groupByTime(Collection<LogEntry> entries){
		Multimap<Date, LogEntry> time2Entries = TreeMultimap.create();
		for (LogEntry entry : entries) {
			time2Entries.put(entry.getDate(), entry);
		}
		return time2Entries;
	}
	
	public static Multimap<String, LogEntry> groupByUserAgent(Collection<LogEntry> entries){
		Multimap<String, LogEntry> userAgent2Entries = TreeMultimap.create();
		for (LogEntry entry : entries) {
			userAgent2Entries.put(entry.getUserAgent(), entry);
		}
		return userAgent2Entries;
	}

}
