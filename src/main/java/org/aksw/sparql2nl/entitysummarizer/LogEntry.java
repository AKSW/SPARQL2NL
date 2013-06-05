/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.util.Date;

/**
 *
 * @author ngonga
 */
public class LogEntry {
    String query;
    Date date;
    String ip;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
    
    public LogEntry(String q)
    {
        query = q;
    }
}
