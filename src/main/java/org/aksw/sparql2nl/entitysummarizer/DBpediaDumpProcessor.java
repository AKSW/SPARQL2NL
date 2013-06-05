/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URLDecoder;
import java.util.*;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;

/**
 *
 * @author ngonga
 */
public class DBpediaDumpProcessor implements DumpProcessor {

    public static String BEGIN = "query=";
    private static SparqlEndpoint ENDPOINT = SparqlEndpoint.getEndpointDBpedia();
    private static final Logger logger = Logger.getLogger(DBpediaDumpProcessor.class);
    private static int maxCount = 100000;

    public DBpediaDumpProcessor() {
    }

    public List<LogEntry> processDump(String file, boolean selectQueriesWithEmptyResults) {
        List<LogEntry> results = new ArrayList<LogEntry>();
        int queryScore;
        int count = 0;
        try {
            // set query score
            if (selectQueriesWithEmptyResults) {
                queryScore = 0;
            } else {
                queryScore = 1;
            }
            //read file
            BufferedReader bufRdr = new BufferedReader(new FileReader(new File(file)));
            String s = bufRdr.readLine();
            while (s != null) {
                count++;
                if (s.contains(BEGIN)) {
                    String q = processDumpLine(s);
                    if (q != null) {
                        if (selectQueriesWithEmptyResults) {
                            int r = checkForResults(q);
                            if (r >= queryScore) {
                                results.add(new LogEntry(q));
                            }
                        } else {
                            try {
                                QueryFactory.create(q);
                                results.add(new LogEntry(q));
                                //logger.warn("Query parse error for " + query);
                            }
                            catch(Exception e)
                            {
                                
                            }
                        }
                    }
                }
                s = bufRdr.readLine();
                if (count == maxCount) {
                    break;
                }
                if ((count + 1) % 1000 == 0) {
                    System.out.println("Reading line " + (count + 1));
                }
            }
        } catch (Exception e) {
        }
        return results;
    }

    /**
     * Reads a line of a DBpedia dump and returns the query contained therein
     *
     * @param line Line of DBpedia dump
     * @return Query contained therein
     */
    private String processDumpLine(String line) {
//        System.out.println(line);
        String query = line.substring(line.indexOf(BEGIN) + BEGIN.length());
        query = query.substring(0, query.indexOf(" ") - 1);
        try {
            return URLDecoder.decode(query, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the number of results for a given query.
     *
     * @param query
     * @return Size of result set for the input query
     */
    private int checkForResults(String query) {
        try {
            QueryEngineHTTP qexec = new QueryEngineHTTP(ENDPOINT.getURL().toString(), query);
            ResultSet results = qexec.execSelect();
            if (results.hasNext()) {
                return results.getRowNumber();
            } //no results
            else {
                return 0;
            }
        } catch (Exception e) {
            //logger.warn("Query parse error for " + query);
        }
        //query parse error
        return -1;
    }

    public static void main(String args[]) {
        DBpediaDumpProcessor dp = new DBpediaDumpProcessor();
        List<LogEntry> query = dp.processDump("E:/Work/Data/DBpediaQueryLog/log_small.txt", true);
        List<String> filteredResults = new ArrayList<String>();
        for (LogEntry e : query) {
            if (dp.checkForResults(e.getQuery()) >= 0) {
                filteredResults.add(e.getQuery());
            }
        }
        System.out.println(filteredResults);
    }

    public List<LogEntry> processDump(String file) {
        return processDump(file, true);
    }
}
