/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.sparql2nl.entitysummarizer;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryFactory;
import java.util.*;
import org.dllearner.core.owl.Property;
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.sparql.SparqlEndpoint;

/**
 *
 * @author ngonga
 */
public class Controller {

    public static boolean selectQueriesWithEmptyResults = true;

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
     * Generates the weighted graph for a given class
     *
     * @param ontClass Named Class
     * @param entries List of log entries (e.g., from a dump file)
     * @return Weighted Graph
     */
    public static WeightedGraph generateGraph(NamedClass ontClass, List<LogEntry> entries) {
        WeightedGraph wg = new WeightedGraph();
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
        SPARQLQueryProcessor processor = new SPARQLQueryProcessor(endpoint);

        //feed data into the processor and then into the graph
        for (LogEntry l : entries) {
            Map<NamedClass, Set<Property>> result = processor.processQuery(l.sparqlQuery);
            if (result.containsKey(ontClass)) {
            System.out.println(result);
                Set<Property> properties = result.get(ontClass);
                Set<Node> nodes = new HashSet<Node>();
                for (Property p : properties) {
                    if (wg.getNode(p.getName()) == null) {
                        Node newNode = new Node(p.getName());
                        nodes.add(newNode);
                    } else {
                        nodes.addAll(wg.getNode(p.getName()));
                    }
                }
                wg.addClique(nodes);
            }
        }
        return wg;
    }

    public static void main(String args[]) {
        testDumpReader();
    }

    public static void testDumpReader()
    {
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
        SPARQLQueryProcessor processor = new SPARQLQueryProcessor(endpoint);
        List<LogEntry> entries = new ArrayList<LogEntry>();

        String q = "PREFIX dbr: <http://dbpedia.org/resource/> "
                + "PREFIX dbo: <http://dbpedia.org/ontology/> "
                + "SELECT ?s ?place ?date WHERE {?s a dbo:Person. ?s dbo:birthPlace ?place. ?s dbo:birthDate ?date.}";
        Query query = QueryFactory.create(q);
        Map<NamedClass, Set<Property>> occurrences = processor.processQuery(query);
        NamedClass nc = new ArrayList<NamedClass>(occurrences.keySet()).get(0);
        
        
        DBpediaDumpProcessor dp = new DBpediaDumpProcessor(); 
        entries = dp.processDump("E:/Work/Data/DBpediaQueryLog/access.log-20120805/access.log-20120805", false);
        System.out.println("Graph = " + Controller.generateGraph(nc, entries));
        
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
        NamedClass nc = new ArrayList<NamedClass>(occurrences.keySet()).get(0);
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

//        NamedClass nc = new NamedClass("http://dbpedia.org/resource/Person");
        System.out.println("Graph = " + Controller.generateGraph(nc, entries));
    }
}
