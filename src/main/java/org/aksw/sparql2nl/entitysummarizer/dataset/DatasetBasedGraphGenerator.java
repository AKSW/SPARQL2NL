/**
 *
 */
package org.aksw.sparql2nl.entitysummarizer.dataset;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreH2;
import org.aksw.jena_sparql_api.cache.extra.CacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheExImpl;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

import org.aksw.sparql2nl.entitysummarizer.clustering.Node;

/**
 * @author Lorenz Buehmann
 *
 */
public class DatasetBasedGraphGenerator {

    private SparqlEndpoint endpoint;
    private QueryExecutionFactory qef;
    private SPARQLReasoner reasoner;
    private Set<String> blacklist = Sets.newHashSet("http://dbpedia.org/ontology/wikiPageExternalLink", "http://dbpedia.org/ontology/abstract",
            "http://dbpedia.org/ontology/thumbnail");

    public enum Cooccurrence {

        PROPERTIES, TRIPLESTORE
    };

    public DatasetBasedGraphGenerator(SparqlEndpoint endpoint) {
        this(endpoint, null);
    }

    public DatasetBasedGraphGenerator(SparqlEndpoint endpoint, String cacheDirectory) {
        this.endpoint = endpoint;

        qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
        if (cacheDirectory != null) {
            try {
                long timeToLive = TimeUnit.DAYS.toMillis(30);
                CacheCoreEx cacheBackend = CacheCoreH2.create(cacheDirectory, timeToLive, true);
                CacheEx cacheFrontend = new CacheExImpl(cacheBackend);
                qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
//		qef = new QueryExecutionFactoryPaginated(qef, 10000);
//		qef = new QueryExecutionFactoryDelay(qef, 500);

        reasoner = new SPARQLReasoner(new SparqlEndpointKS(endpoint), cacheDirectory);
    }

    public Map<NamedClass, WeightedGraph> generateGraphs(double threshold) {
        return generateGraphs(threshold, null);
    }

    public Map<NamedClass, WeightedGraph> generateGraphs(double threshold, String namespace) {
        Map<NamedClass, WeightedGraph> graphs = new HashMap<NamedClass, WeightedGraph>();

        //get all classes in knowledge base with given prefix
        Set<NamedClass> classes = reasoner.getTypes(namespace);

        //generate a weighted graph for each class
        WeightedGraph wg;
        for (NamedClass cls : classes) {
            wg = generateGraph(cls, threshold, namespace);
            graphs.put(cls, wg);
        }

        return graphs;
    }

    public WeightedGraph generateGraph(NamedClass cls, double threshold) {
        return generateGraph(cls, threshold, null);
    }

    public WeightedGraph generateGraph(NamedClass cls, double threshold, String namespace) {
        return generateGraph(cls, threshold, namespace, Cooccurrence.TRIPLESTORE);
    }

    public WeightedGraph generateGraph(NamedClass cls, double threshold, String namespace, Cooccurrence c) {
        //get the properties with a prominence score above threshold
        SortedSet<ObjectProperty> properties = getMostProminentProperties(cls, threshold, namespace);
        //compute the frequency for each pair of properties
        Map<Set<ObjectProperty>, Double> cooccurrences;
        if (c.equals(Cooccurrence.TRIPLESTORE)) {
            cooccurrences = getCooccurrences(cls, properties);
        } else {
            cooccurrences = getPropertySimilarities(cls, properties);
        }

        //create the weighted graph
        WeightedGraph wg = new WeightedGraph();
        LoadingCache<ObjectProperty, Node> property2Node = CacheBuilder.newBuilder().maximumSize(properties.size()).build(
                new CacheLoader<ObjectProperty, Node>() {

                    public Node load(ObjectProperty property) {
                        return new Node(property.getName());
                    }
                });
        for (Entry<Set<ObjectProperty>, Double> entry : cooccurrences.entrySet()) {
            Set<ObjectProperty> pair = entry.getKey();
            Double frequency = entry.getValue();

            Iterator<ObjectProperty> iterator = pair.iterator();
            ObjectProperty property1 = iterator.next();
            ObjectProperty property2 = iterator.next();

            try {
                Node node1 = property2Node.get(property1);
                Node node2 = property2Node.get(property2);
                wg.addNode(node1, 0d);
                wg.addNode(node2, 0d);
                wg.addEdge(node1, node2, frequency);
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
        return wg;
    }

    private Map<Set<ObjectProperty>, Double> getCooccurrences(NamedClass cls, Set<ObjectProperty> properties) {
        Map<Set<ObjectProperty>, Double> pair2Frequency = new HashMap<Set<ObjectProperty>, Double>();
        //compute the frequency for each pair
        ResultSet rs;
        Set<ObjectProperty> pair;
        for (ObjectProperty prop1 : properties) {
            for (ObjectProperty prop2 : properties) {
                if (!prop1.equals(prop2) && !pair2Frequency.containsKey(pair = Sets.newHashSet(prop1, prop2))) {
                    String query = "SELECT (COUNT(DISTINCT ?s) AS ?cnt) WHERE {"
                            + "?s a <" + cls.getName() + ">."
                            + "?s <" + prop1.getName() + "> ?o1."
                            + "?s <" + prop2.getName() + "> ?o2."
                            + "}";
                    rs = executeSelectQuery(query);
                    double frequency = (double) (rs.next().getLiteral("cnt").getInt());
                    pair2Frequency.put(pair, frequency);
                }
            }
        }
        return pair2Frequency;
    }

    private Map<Set<ObjectProperty>, Double> getPropertySimilarities(NamedClass cls, Set<ObjectProperty> properties) {
        return PropertySimilarityCorrelation.getCooccurrences(cls, properties);
    }

    private Map<ObjectProperty, Integer> getPropertiesWithFrequency(NamedClass cls) {
        return getPropertiesWithFrequency(cls, null);
    }

    /**
     * Get all properties and its frequencies based on how often each property
     * is used by instances of the given class.
     *
     * @param cls
     * @param namespace
     * @return
     */
    private Map<ObjectProperty, Integer> getPropertiesWithFrequency(NamedClass cls, String namespace) {
        Map<ObjectProperty, Integer> properties = new HashMap<ObjectProperty, Integer>();
        String query =
                "SELECT ?p (COUNT(DISTINCT ?s) AS ?cnt) WHERE {"
                + "?s a <" + cls.getName() + ">."
                + "?s ?p ?o."
                + "{SELECT DISTINCT ?p WHERE {?s a <" + cls.getName() + ">. ?s ?p ?o."
                + (namespace != null ? "FILTER(REGEX(?p,'" + namespace + "'))" : "")
                + "}}} GROUP BY ?p ORDER BY DESC(?cnt)";

        ResultSet rs = executeSelectQuery(query);
        QuerySolution qs;
        while (rs.hasNext()) {
            qs = rs.next();
            String uri = qs.getResource("p").getURI();
            if (!blacklist.contains(uri)) {
                properties.put(new ObjectProperty(uri), qs.getLiteral("cnt").getInt());
            }
        }
        return properties;
    }

    private void aPriori(NamedClass cls, Set<ObjectProperty> properties, int minSupport) {
        System.out.println("Candidates: " + properties);
        System.out.println("Min. support: " + minSupport);
        Set<Set<ObjectProperty>> nonFrequentSubsets = new HashSet<Set<ObjectProperty>>();
        for (int i = 2; i <= properties.size(); i++) {
            Set<Set<ObjectProperty>> subsets = getSubsets(properties, i);
            Set<Set<ObjectProperty>> nonFrequentSubsetsTmp = new HashSet<Set<ObjectProperty>>();
            ResultSet rs;
            for (Set<ObjectProperty> set : subsets) {
                if (Sets.intersection(getSubsets(set, i - 1), nonFrequentSubsets).isEmpty()) {
                    String query = "SELECT (COUNT(DISTINCT ?s) AS ?cnt) WHERE {\n" + "?s a <" + cls.getName() + ">.";
                    int cnt = 1;
                    for (ObjectProperty property : set) {
                        query += "?s <" + property.getName() + "> ?o" + cnt++ + ".\n";
                    }
                    query += "}";

                    rs = executeSelectQuery(query);
                    int frequency = rs.next().getLiteral("cnt").getInt();
                    if (frequency < minSupport) {
                        nonFrequentSubsetsTmp.add(set);
                        System.out.println("Too low support(" + frequency + "): " + set);
                    } else {
                        System.out.println(set);
                    }
                } else {
                    System.err.println("BLA");
                }
            }
            nonFrequentSubsets = nonFrequentSubsetsTmp;
        }
    }

    private SortedSet<ObjectProperty> getMostProminentProperties(NamedClass cls, double threshold) {
        return getMostProminentProperties(cls, threshold, null);
    }

    private SortedSet<ObjectProperty> getMostProminentProperties(NamedClass cls, double threshold, String namespace) {
        SortedSet<ObjectProperty> properties = new TreeSet<ObjectProperty>();

        //get total number of instances for the class
        int instanceCount = getInstanceCount(cls);

        //get all properties+frequency that are used by instances of the class
        Map<ObjectProperty, Integer> propertiesWithFrequency = getPropertiesWithFrequency(cls, namespace);

        //get all properties with a relative frequency above the threshold
        for (Entry<ObjectProperty, Integer> entry : propertiesWithFrequency.entrySet()) {
            ObjectProperty property = entry.getKey();
            Integer frequency = entry.getValue();
            double score = frequency / (double) instanceCount;

            if (score >= threshold) {
                properties.add(property);
            }
        }

        return properties;
    }

    /**
     * Get the total number of instances for the given class.
     *
     * @param cls
     * @return
     */
    private int getInstanceCount(NamedClass cls) {
        String query = "SELECT (COUNT(?s) AS ?cnt) WHERE {?s a <" + cls.getName() + ">.}";
        ResultSet rs = executeSelectQuery(query);
        return rs.next().getLiteral("cnt").getInt();
    }

    private ResultSet executeSelectQuery(String query) {
        System.out.println(query);
        QueryExecution qe = qef.createQueryExecution(query);
        ResultSet rs = qe.execSelect();
        return rs;
    }

    private <T> Set<Set<T>> getSubsets(Set<T> set, int size) {
        Set<Set<T>> subsets = new HashSet<Set<T>>();
        Set<Set<T>> powerSet = Sets.powerSet(set);
        for (Set<T> subset : powerSet) {
            if (subset.size() == size) {
                subsets.add(subset);
            }
        }
        return subsets;
    }

    private <T> Set<Set<T>> getSupersets(Set<Set<T>> sets, int size) {
        Set<Set<T>> supersets = new HashSet<Set<T>>();
        for (Set<T> set1 : sets) {
            for (Set<T> set2 : sets) {
                if (set1 != set2 && !Sets.intersection(set1, set2).isEmpty()) {
                    Set<T> union = Sets.newHashSet();
                    union.addAll(set1);
                    union.addAll(set2);
                    supersets.add(union);
                }
            }
        }
        return supersets;
    }

    public static void main(String[] args) throws Exception {
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
        //endpoint.getDefaultGraphURIs().add("http://dbpedia.org/sparql");
        WeightedGraph wg = new DatasetBasedGraphGenerator(endpoint, "cache").generateGraph(new NamedClass("http://dbpedia.org/ontology/AmericanFootballPlayer"), 0.5, "http://dbpedia.org/ontology/", Cooccurrence.PROPERTIES);
//		generateGraph(0.5, "http://dbpedia.org/ontology/");
    }
}