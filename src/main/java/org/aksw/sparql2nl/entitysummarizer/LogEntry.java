/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import java.util.Date;

/**
 *
 * @author ngonga
 */
public class LogEntry {
    String query;
    Date date;
    String ip;
    Query sparqlQuery;
    
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
    
    public LogEntry(String q)
    {
        query = q;
        this.sparqlQuery = QueryFactory.create(q);
    }
}
