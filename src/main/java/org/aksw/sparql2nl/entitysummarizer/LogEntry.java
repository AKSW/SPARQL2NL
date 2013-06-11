/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import java.util.Date;

import org.apache.commons.lang.builder.CompareToBuilder;

/**
 *
 * @author ngonga
 */
public class LogEntry implements Comparable<LogEntry>{
    String query;
    Date date;
    String ip;
    Query sparqlQuery;
    
    public LogEntry(String q)
    {
        query = q;
        this.sparqlQuery = QueryFactory.create(q);
    }
    
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
        this.sparqlQuery = QueryFactory.create(query);
    }

    public Query getSparqlQuery() {
        return sparqlQuery;
    }

    public void setSparqlQuery(Query sparqlQuery) {
        this.sparqlQuery = sparqlQuery;
    }

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(LogEntry other) {
		return new CompareToBuilder()
		.append(ip, other.ip)
		.append(query, other.query)
		.append(date, other.date)
		.toComparison();
	}
    
   
}
