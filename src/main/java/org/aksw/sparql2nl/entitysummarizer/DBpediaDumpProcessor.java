/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.ExtractionDBCache;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.kb.sparql.SparqlQuery;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.Syntax;
import com.hp.hpl.jena.sparql.engine.http.QueryEngineHTTP;

/**
 *
 * @author ngonga
 */
public class DBpediaDumpProcessor implements DumpProcessor {

    public static String BEGIN = "query=";
    private static SparqlEndpoint ENDPOINT = SparqlEndpoint.getEndpointDBpediaLiveAKSW();
    private static final Logger logger = Logger.getLogger(DBpediaDumpProcessor.class);
    private static int maxCount = 10000;
    private ExtractionDBCache cache = new ExtractionDBCache("cache");

    public DBpediaDumpProcessor() {
    }
    
	public void filterOutInvalidQueries(String file) {
		OutputStream os = null;
		InputStream is = null;
		try {
			String ending = file.substring(file.lastIndexOf('.') + 1);
			os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(new File(
					file.substring(0, file.lastIndexOf('.')) + "-valid." + ending), true)));
			is = new FileInputStream(new File(file));
			if (file.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			LogEntry entry = null;
			while ((line = reader.readLine()) != null) {
				entry = processDumpLine(line);
				if (entry != null) {
					line += "\n";
					os.write(line.getBytes());
					os.flush();
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
    
	public void filterOutNonSelectQueries(String file) {
		OutputStream os = null;
		InputStream is = null;
		try {
			String ending = file.substring(file.lastIndexOf('.') + 1);
			os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(new File(file.substring(0,
					file.lastIndexOf('.'))
					+ "-select." + ending), true)));
			is = new FileInputStream(new File(file));
			if (file.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			LogEntry entry = null;
			while ((line = reader.readLine()) != null) {
				entry = processDumpLine(line);
				if (entry != null && entry.sparqlQuery.isSelectType()) {
					line += "\n";
					os.write(line.getBytes());
					os.flush();
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
    
	public void filterOutEmptyQueries(String file) {
		OutputStream os = null;
		InputStream is = null;
		try {
			String ending = file.substring(file.lastIndexOf('.') + 1);
			os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(new File(file.substring(0,
					file.lastIndexOf('.'))
					+ "-nonempty." + ending), true)));
			is = new FileInputStream(new File(file));
			if (file.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			LogEntry entry = null;
			while ((line = reader.readLine()) != null) {
				entry = processDumpLine(line);
				if (entry != null) {
					int r = checkForResults(entry.query);
					if (r >= 1) {
						line += "\n";
						os.write(line.getBytes());
						os.flush();
					} 
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				is.close();
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

    public List<LogEntry> processDump(String file, boolean omitQueriesWithEmptyResults, int limit) {
    	List<LogEntry> results = new ArrayList<LogEntry>();
        int queryScore;
        int count = 0;
        String s = "";
        try {
            // set query score
            if (omitQueriesWithEmptyResults) {
                queryScore = 0;
            } else {
                queryScore = 1;
            }
            //read file
            InputStream is = new FileInputStream(new File(file));
            if(file.endsWith(".gz")){
            	is = new GZIPInputStream(is);
            }
            BufferedReader bufRdr = new BufferedReader(new InputStreamReader(is));
            while ((s = bufRdr.readLine()) != null && count++ < limit) {
                if (s.contains(BEGIN)) {
                    LogEntry l = processDumpLine(s);
                    if (l != null) {
                        if (omitQueriesWithEmptyResults) {
                            int r = checkForResults(l.query);
                            if (r >= queryScore) {
                                results.add(l);
                            }
                        } else {
                            try {
                                QueryFactory.create(l.query);
                                results.add(l);
                            } catch (Exception e) {
//                                logger.warn("Query parse error for " + q);
                            }
                        }
                    }
                }
                
                if ((count + 1) % 1000 == 0) {
                    System.out.println("Reading line " + (count + 1));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warn("Query parse error for " + s);
        }
        return results;
    }
    
    public List<LogEntry> processDump(String file, boolean selectQueriesWithEmptyResults) {
        return processDump(file, selectQueriesWithEmptyResults, maxCount);
    }

    /**
     * Reads a line of a DBpedia dump and returns the query contained therein
     *
     * @param line Line of DBpedia dump
     * @return Query contained therein
     */
    private LogEntry processDumpLine(String line) {
//        System.out.println(line);

        String query = line.substring(line.indexOf(BEGIN) + BEGIN.length());
        DateFormat df = new SimpleDateFormat("dd/MMM/yyyy hh:mm:ss", Locale.ENGLISH);

        try {
            query = query.substring(0, query.indexOf(" ") - 1);
            query = URLDecoder.decode(query, "UTF-8");
            if (query.contains("&")) {
                query = query.substring(0, query.indexOf("&") - 1);
                query = query + "}";
            }
            QueryFactory.create(query, Syntax.syntaxARQ);
            LogEntry l = new LogEntry(query);
            String[] split = line.split(" ");
            l.ip = split[0];
            l.date = df.parse(split[3].substring(1).toLowerCase() + " " + split[4].toLowerCase());

            return l;
        } catch (Exception e) {
//            logger.warn("Query parse error for " + query + " from "+line);
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
        	Query q = QueryFactory.create(query, Syntax.syntaxARQ);
        	q.setLimit(1);
            ResultSet results = SparqlQuery.convertJSONtoResultSet(cache.executeSelectQuery(ENDPOINT, query));
            if (results.hasNext()) {
                return 1;//results.getRowNumber();
            } //no results
            else {
                return 0;
            }
        } catch (Exception e) {e.printStackTrace();
            logger.error("Query parse error for " + query);
        }
        //query parse error
        return -1;
    }

    public static void main(String args[]) {
        DBpediaDumpProcessor dp = new DBpediaDumpProcessor();
        dp.filterOutInvalidQueries("resources/dbpediaLog/dbpedia.log.gz");
        dp.filterOutNonSelectQueries("resources/dbpediaLog/dbpedia.log-valid.gz");
//        dp.filterOutEmptyQueries("resources/dbpediaLog/dbpedia.log-valid-select.gz");
//        List<LogEntry> entries = dp.processDump("resources/dbpediaLog/access.log-20120805.gz", false);
//        System.out.println("#Entries: " + entries.size());
//        
//        //group by IP address
//        Multimap<String, LogEntry> ip2Entries = LogEntryGrouping.groupByIPAddress(entries);
//        System.out.println("#IP addresses: " + ip2Entries.keySet().size());
//        
//		for (Entry<String, Collection<LogEntry>> entry : ip2Entries.asMap().entrySet()) {
//			String ip = entry.getKey();
//			Collection<LogEntry> entriesForIP = entry.getValue();
//			System.out.println(ip + ": " + entriesForIP.size());
//			//print top n 
//			int n = 5;
//			for (LogEntry e : new ArrayList<LogEntry>(entriesForIP).subList(0, Math.min(entriesForIP.size(), n))) {
//				System.out.println(e.getSparqlQuery());
//			}
//		}
//        
//        List<String> filteredResults = new ArrayList<String>();
//        DateFormat df = new SimpleDateFormat("dd/mm/yyyy hh:mm:ss", Locale.ENGLISH);
//
//        for (LogEntry e : entries) {
//            if (dp.checkForResults(e.getQuery()) >= 0) {
//                filteredResults.add(df.format(e.date) + "" + e.getQuery());
//            }
//        }
//        System.out.println(filteredResults);
    }

    public List<LogEntry> processDump(String file) {
        return processDump(file, false);
    }
}
