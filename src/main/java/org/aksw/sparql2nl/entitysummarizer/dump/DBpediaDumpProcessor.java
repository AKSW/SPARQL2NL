/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer.dump;

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

import org.aksw.sparql2nl.queryprocessing.TriplePatternExtractor;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.ExtractionDBCache;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.kb.sparql.SparqlQuery;

import com.google.common.base.Charsets;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.Syntax;

/**
 *
 * @author ngonga
 */
public class DBpediaDumpProcessor implements DumpProcessor {

    public static String BEGIN = "query=";
    private static SparqlEndpoint ENDPOINT = SparqlEndpoint.getEndpointDBpedia();
    private static final Logger logger = Logger.getLogger(DBpediaDumpProcessor.class);
    private ExtractionDBCache cache = new ExtractionDBCache("cache");

    public DBpediaDumpProcessor() {
    }
    
	public void filterOutInvalidQueries(String file) {
		OutputStream os = null;
		InputStream is = null;
		try {
			String ending = file.substring(file.lastIndexOf('.') + 1);
			os = new BufferedOutputStream(new FileOutputStream(new File(
					file.substring(0, file.lastIndexOf('.')) + "-valid." + ending), true));
			is = new FileInputStream(new File(file));
			if (file.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			LogEntry entry = null;
			int i = 0;
			while ((line = reader.readLine()) != null) {
				entry = processDumpLine(line);
				if (entry != null) {
					line += "\n";
					os.write(line.getBytes());
					os.flush();i++;
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
	
	public void filterOutNonConjunctiveQueries(String file) {
		OutputStream os = null;
		InputStream is = null;
		try {TriplePatternExtractor triplePatternExtractor = new TriplePatternExtractor();
			String ending = file.substring(file.lastIndexOf('.') + 1);
			os = new BufferedOutputStream(new FileOutputStream(new File(
					file.substring(0, file.lastIndexOf('.')) + "-conjunctive." + ending), true));
			is = new FileInputStream(new File(file));
			if (file.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			LogEntry entry = null;
			int i = 0;
			while ((line = reader.readLine()) != null) {
				entry = processDumpLine(line);
				if(entry != null){
					Query query = entry.getSparqlQuery();
					
					String q = query.toString().toLowerCase();
					
					if (	query.isSelectType()
							&& !q.contains("optional")
							&& !q.contains("union")
							&& !q.contains("group by")
							&& !q.contains("offset")
							&& !q.contains("count")
							&& !q.contains("bif:contains")
							&& !q.contains("filter")
							&& !query.isQueryResultStar()
							&& query.getProjectVars().size() == 1
							
							) {
//							System.out.println(q);
							if(triplePatternExtractor.extractIngoingTriplePatterns(query, query.getProjectVars().get(0)).isEmpty()
								&& triplePatternExtractor.extractTriplePattern(query).size() >= 2){
								line += "\n";
								os.write(line.getBytes());
								os.flush();
								i++;
							}
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
	
	public void filterOutMultipleProjectionVars(String file) {
		OutputStream os = null;
		InputStream is = null;
		try { TriplePatternExtractor triplePatternExtractor = new TriplePatternExtractor();
			String ending = file.substring(file.lastIndexOf('.') + 1);
			os = new BufferedOutputStream(new FileOutputStream(new File(
					file.substring(0, file.lastIndexOf('.')) + "-singleProjectionVar." + ending), true));
			is = new FileInputStream(new File(file));
			if (file.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			LogEntry entry = null;
			int i = 0;
			while ((line = reader.readLine()) != null) {
				entry = processDumpLine(line);
				Query q = entry.getSparqlQuery();
				
				if (!q.isQueryResultStar()
						
						&& q.getProjectVars().size() == 1
						&& triplePatternExtractor.extractIngoingTriplePatterns(q, q.getProjectVars().get(0)).isEmpty()
						&& !q.toString().contains("bif:contains")
						&& !q.toString().toLowerCase().contains("filter")
						&& triplePatternExtractor.extractTriplePattern(q).size() >=3
						) {System.out.println(q);
					line += "\n";
					os.write(line.getBytes());
					os.flush();i++;
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
			os = new BufferedOutputStream(new FileOutputStream(new File(file.substring(0,
					file.lastIndexOf('.'))
					+ "-select." + ending), true));
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
		try {TriplePatternExtractor triplePatternExtractor = new TriplePatternExtractor();
			String ending = file.substring(file.lastIndexOf('.') + 1);
			os = new BufferedOutputStream(new FileOutputStream(new File(file.substring(0,
					file.lastIndexOf('.'))
					+ "-nonempty." + ending), true));
			is = new FileInputStream(new File(file));
			if (file.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			LogEntry entry = null;
			StringBuilder queries = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				entry = processDumpLine(line);
				if (entry != null) {
					int r = checkForResults(entry.query);
					if (r >= 3) {
						Query q = QueryFactory.create(entry.query, Syntax.syntaxARQ);
						if(triplePatternExtractor.extractTriplePattern(q).size() >= 2){
							queries.append(q.toString() + "\n++++++++++++++++++++++++++++\n");
						}
						line += "\n";
						os.write(line.getBytes());
						os.flush();
					} 
				}
			}
			Files.write(queries.toString(), new File("dbpedia-log-queries.txt"), Charsets.UTF_8);
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
	
	public void filterOutByUserAgents(String file) {
		OutputStream os = null;
		InputStream is = null;
		try {
			String ending = file.substring(file.lastIndexOf('.') + 1);
			os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(new File(file.substring(0,
					file.lastIndexOf('.'))
					+ "-nonjava." + ending), true)));
			is = new FileInputStream(new File(file));
			if (file.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line = null;
			LogEntry entry = null;
			while ((line = reader.readLine()) != null) {
				entry = processDumpLine(line);
				if (!entry.getUserAgent().equals("Java")) {
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
	
	public void filterOutIPAddressApproximatedNoise(String file) {
		OutputStream os = null;
		InputStream is = null;
		try {
			is = new FileInputStream(new File(file));
			if (file.endsWith(".gz")) {
				is = new GZIPInputStream(is);
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			List<LogEntry> entries = new ArrayList<LogEntry>();
			List<String> lines = new ArrayList<String>();
			String line = null;
			while ((line = reader.readLine()) != null) {System.out.println(line);
				entries.add(processDumpLine(line));
				lines.add(line);
			}
			
			int thresholdPerIPAddress = (int)Math.sqrt(entries.size());
			System.out.println("Max. number of queries per IP address: " + thresholdPerIPAddress);
			
			Multimap<String, LogEntry> groupedByIPAddress = LogEntryGrouping.groupByIPAddress(entries);
			
			String ending = file.substring(file.lastIndexOf('.') + 1);
			os = new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(new File(file.substring(0,
					file.lastIndexOf('.'))
					+ "-ip." + ending), true)));
			
			LogEntry entry;
			for (String l : lines) {
				entry = processDumpLine(line);
				if(groupedByIPAddress.get(entry.ip).size() <= thresholdPerIPAddress){
					line += "\n";
					os.write(l.getBytes());
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
	
	public List<LogEntry> processDump(String file, int limit) {
		return processDump(file, false, limit);
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
        return processDump(file, selectQueriesWithEmptyResults, Integer.MAX_VALUE);
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
            l.userAgent = split[10].substring(1, split[10].length() - 1);
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
        	q.setLimit(Query.NOLIMIT);
            ResultSet rs = SparqlQuery.convertJSONtoResultSet(cache.executeSelectQuery(ENDPOINT, query));
            int cnt = 0;
            while (rs.hasNext()) {
            	rs.next();
            	cnt++;
            } 
            return cnt;
        } catch (Exception e) {e.printStackTrace();
            logger.error("Query parse error for " + query);
        }
        //query parse error
        return -1;
    }

    public static void main(String args[]) {
        DBpediaDumpProcessor dp = new DBpediaDumpProcessor();
        File folder = new File("/home/me/work/DBpediaQueryLog/");
        for (File file : folder.listFiles()) {
        	if(!file.getAbsolutePath().contains("conjunctive") && !file.getAbsolutePath().contains("nonempty"))continue;
        	System.out.println("Processing " + file);
//        	dp.filterOutNonConjunctiveQueries(file.getAbsolutePath());
        	dp.filterOutEmptyQueries(file.getAbsolutePath());
		}
    }

    public List<LogEntry> processDump(String file) {
        return processDump(file, false);
    }
}
