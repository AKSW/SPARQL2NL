/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import com.google.common.collect.Multimap;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.vocabulary.RDF;
import java.util.*;
import java.util.Map.Entry;

import org.dllearner.core.owl.Property;
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

/**
 *
 * @author ngonga
 */
public class Controller {

    public static boolean selectQueriesWithEmptyResults = true;
    private static final String testFile = "resources/dbpediaLog/dbpedia.log-valid-select-nonjava.gz";

    /**
     * Generates the weighted graph for a given class
     *
     * @param ontClass Named Class
     * @param dumpFile Dump file to be processed
     * @return Weighted Graph
     */
    public static WeightedGraph generateGraph(NamedClass ontClass, String dumpFile) {
        //get dump
        DBpediaDumpProcessor dp = new DBpediaDumpProcessor();
        List<LogEntry> entries = dp.processDump(dumpFile, selectQueriesWithEmptyResults);
        return generateGraph(ontClass, entries);
    }

    /**
     * Generates the weighted graph for the whole dump without considering the
     * class
     *
     * @param dumpFile Dump file to be processed
     * @return Weighted Graph
     */
    public static WeightedGraph generateGraph(String dumpFile) {
        //get dump
        DBpediaDumpProcessor dp = new DBpediaDumpProcessor();
        List<LogEntry> entries = dp.processDump(dumpFile, selectQueriesWithEmptyResults);
        return generateGraph(entries);
    }

    /**
     * Generates the weighted graph for a given class
     *
     * @param ontClass Named Class
     * @param entries List of log entries (e.g., from a dump file)
     * @return Weighted Graph
     */
    public static WeightedGraph generateGraph(NamedClass ontClass, List<LogEntry> entries) {
        WeightedGraph wg = new WeightedGraph();
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpediaLiveAKSW();
        SPARQLQueryProcessor processor = new SPARQLQueryProcessor(endpoint);
        int count = 0;
        //feed data into the processor and then into the graph
        for (LogEntry l : entries) {
            Map<NamedClass, Set<Property>> result = processor.processQuery(l.sparqlQuery);
            if (result.containsKey(ontClass)) {
                count++;
                Set<Property> properties = result.get(ontClass);
                Set<Node> nodes = new HashSet<Node>();
                for (Property p : properties) {
                    if (!p.getName().equals(RDF.type.getURI())) {
                        if (wg.getNode(p.getName()) == null) {
                            Node newNode = new Node(p.getName());
                            nodes.add(newNode);
                        } else {
                            nodes.addAll(wg.getNode(p.getName()));
                        }
                    }
                }
                wg.addClique(nodes);
            }
        }
        System.out.println("Found " + count + " matching queries for " + ontClass.getName());
        wg.scale((double) count);
        return wg;
    }

    /**
     * Generates the weighted graph for a given class
     *
     * @param ontClass Named Class
     * @param entries List of log entries (e.g., from a dump file)
     * @return Weighted Graph
     */
    public static WeightedGraph generateGraphMultithreaded(NamedClass ontClass, Collection<Map<NamedClass, Set<Property>>> result) {
        WeightedGraph wg = new WeightedGraph();
        int count = 0;
        //feed data into the processor and then into the graph
        for (Map<NamedClass, Set<Property>> map : result) {
            if (map.containsKey(ontClass)) {
                count++;
                Set<Property> properties = map.get(ontClass);
                Set<Node> nodes = new HashSet<Node>();
                for (Property p : properties) {
                    if (!p.getName().equals(RDF.type.getURI())) {
                        if (wg.getNode(p.getName()) == null) {
                            Node newNode = new Node(p.getName());
                            nodes.add(newNode);
                        } else {
                            nodes.addAll(wg.getNode(p.getName()));
                        }
                    }
                }
                wg.addClique(nodes);
            }
        }
        System.out.println("Found " + count + " matching queries for " + ontClass.getName());
        wg.scale((double) count);
        return wg;
    }

    /**
     * Generates the weighted graph for a given class
     *
     * @param entries List of log entries (e.g., from a dump file)
     * @return Weighted Graph
     */
    public static WeightedGraph generateGraph(List<LogEntry> entries) {
        WeightedGraph wg = new WeightedGraph();
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
        SPARQLQueryProcessor processor = new SPARQLQueryProcessor(endpoint);
        int count = 0;
        //feed data into the processor and then into the graph
        for (LogEntry l : entries) {
            Map<NamedClass, Set<Property>> result = processor.processQuery(l.sparqlQuery);
            for (NamedClass ontClass : result.keySet()) {
                count++;
                Set<Property> properties = result.get(ontClass);
                Set<Node> nodes = new HashSet<Node>();
                for (Property p : properties) {
                    if (!p.getName().equals(RDF.type.getURI())) {
                        if (wg.getNode(p.getName()) == null) {
                            Node newNode = new Node(p.getName());
                            nodes.add(newNode);
                        } else {
                            nodes.addAll(wg.getNode(p.getName()));
                        }
                    }
                }
                wg.addClique(nodes);
            }
        }
        System.out.println("Found " + count + " matching queries overall");
        wg.scale((double) count);
        return wg;
    }

    /**
     * Generates the weighted graph for a given class
     *System.out.println("Reference Graph =============== ");
//            System.out.println("Edges = " + reference);
//            System.
     * @param entries List of log entries (e.g., from a dump file)
     * @return Weighted Graph
     */
    public static WeightedGraph generateGraphMultithreaded(Collection<Map<NamedClass, Set<Property>>> result) {
        WeightedGraph wg = new WeightedGraph();
        int count = 0;
        //feed data into the processor and then into the graph
       
        for (Map<NamedClass, Set<Property>> map : result) {
        	count++;
            for (NamedClass ontClass : map.keySet()) {
                Set<Property> properties = map.get(ontClass);
                Set<Node> nodes = new HashSet<Node>();
                for (Property p : properties) {
                    if (!p.getName().equals(RDF.type.getURI())) {
                        if (wg.getNode(p.getName()) == null) {
                            Node newNode = new Node(p.getName());
                            nodes.add(newNode);
                        } else {
                            nodes.addAll(wg.getNode(p.getName()));
                        }
                    }
                }
                wg.addClique(nodes);
            }
        }
        System.out.println("Found " + count + " matching queries overall");
        wg.scale((double) count);
        return wg;
    }

    public static void main(String args[]) {
//        test();
        testDumpReader();
//        testSPARQLQueryProcessor();
    }
    
	public static void testSPARQLQueryProcessor() {
		SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
		SPARQLQueryProcessor queryProcessor = new SPARQLQueryProcessor(endpoint);
		DBpediaDumpProcessor dp = new DBpediaDumpProcessor();
		List<LogEntry> entries = dp.processDump(testFile, false, 20000);
		
		// group by IP address
		Multimap<String, LogEntry> ip2Entries = LogEntryGrouping.groupByIPAddress(entries);
		System.out.println("#IP addresses: " + ip2Entries.keySet().size());
		
		// group by user agent
		Multimap<String, LogEntry> userAgent2Entries = LogEntryGrouping.groupByUserAgent(entries);
		System.out.println("#User agent: " + userAgent2Entries.keySet().size());
		
		for (Entry<String, Collection<LogEntry>> entry : userAgent2Entries.asMap().entrySet()) {
			String userAgent = entry.getKey();
			Collection<LogEntry> entriesForUserAgent = entry.getValue();
			System.out.println(userAgent + ": " + entriesForUserAgent.size());
		}

//		for (Entry<String, Collection<LogEntry>> entry : ip2Entries.asMap().entrySet()) {
//			String ip = entry.getKey();
//			Collection<LogEntry> entriesForIP = entry.getValue();
//			System.out.println(ip + ": " + entriesForIP.size());
//			// print top n
//			int n = 3;
//			for (LogEntry e : new ArrayList<LogEntry>(entriesForIP).subList(0, Math.min(entriesForIP.size(), n))) {
//				System.out.println(e.getSparqlQuery());
//				Map<NamedClass, Set<Property>> result = queryProcessor.processQuery(e.getSparqlQuery());
//				System.out.println(result);
//			}
//		}
	}

    public static void testDumpReader() {
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
        SPARQLQueryProcessor processor = new SPARQLQueryProcessor(endpoint);

//        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/Person");

        DBpediaDumpProcessor dp = new DBpediaDumpProcessor();
        List<LogEntry> entries = dp.processDump(testFile, false);
        Collection<Map<NamedClass, Set<Property>>> result = processor.processEntries(entries);
        WeightedGraph reference = Controller.generateGraphMultithreaded(result);
        for(NamedClass nc : new SPARQLReasoner(new SparqlEndpointKS(endpoint)).getOWLClasses()){
        	 WeightedGraph wg = Controller.generateGraphMultithreaded(nc, result);
            System.out.println("\n\nBasic Graph =============== ");
            System.out.println("Edges = " + wg);
            System.out.println("Nodes = " + wg.getNodes());
//            System.out.println("Reference Graph =============== ");
//            System.out.println("Edges = " + reference);
//            System.out.println("Nodes = " + reference.getNodes());
            System.out.println("Difference Graph =============== ");
            wg.nodeConservingMinus(reference);
//            System.out.println("Edges = " + wg);
            System.out.println("Nodes = " + wg.getNodes());
        }

    }

    public static void test() {
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
        SPARQLQueryProcessor processor = new SPARQLQueryProcessor(endpoint);
        List<LogEntry> entries = new ArrayList<LogEntry>();

        String q = "PREFIX dbr: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT ?s ?place ?date WHERE {?s a dbo:Person. ?s dbo:birthPlace ?place. ?s dbo:birthDate ?date.}";
        Query query = QueryFactory.create(q);
        Map<NamedClass, Set<Property>> occurrences = processor.processQuery(query);
        NamedClass nc = new NamedClass("http://dbpedia.org/ontology/Person");
//        System.out.println(Joiner.on("\n").withKeyValueSeparator("=").join(occurrences));

        LogEntry lg = new LogEntry(q);
        entries.add(lg);

        q = "PREFIX dbr: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT ?s ?place ?date WHERE {?s a dbo:Person. ?s dbo:birthPlace ?place. }";
        LogEntry lg2 = new LogEntry(q);
        entries.add(lg2);

        q = "PREFIX dbr: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT ?s ?place ?date WHERE {?s a dbo:Person. ?s dbo:birthPlace ?place. ?s dbo:birthDate ?date.}";
        LogEntry lg3 = new LogEntry(q);
        entries.add(lg3);

        q = "PREFIX dbr: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT ?s ?place ?date WHERE {?s a dbo:Person. ?s dbo:birthPlace ?place. }";

//        NamedClass nc = ne    w NamedClass("http://dbpedia.org/resource/Person");
        WeightedGraph wg = Controller.generateGraph(nc, entries);
        System.out.println("Edges = " + wg);
        System.out.println("Nodes = " + wg.getNodes());
    }
}
