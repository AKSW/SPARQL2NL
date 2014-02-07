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
import org.aksw.sparql2nl.entitysummarizer.clustering.Node;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;
import org.dllearner.utilities.MapUtils;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.realiser.english.Realiser;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

/**
 * @author Lorenz Buehmann
 *
 */
public class DatasetBasedGraphGenerator {
	
	public enum Cooccurrence {
        PROPERTIES, TRIPLESTORE
    }
    
    public enum Direction {
    	OUTGOING, INCOMING
    }
	
	private static final Logger logger = Logger.getLogger(DatasetBasedGraphGenerator.class.getName());

    private QueryExecutionFactory qef;
    private SPARQLReasoner reasoner;
    private Set<String> blacklist = Sets.newHashSet(
    		"http://dbpedia.org/ontology/wikiPageExternalLink", 
    		"http://dbpedia.org/ontology/abstract",
            "http://dbpedia.org/ontology/thumbnail", 
            "http://dbpedia.org/ontology/wikiPageID",
            "http://dbpedia.org/ontology/wikiPageRevisionID",
            "http://dbpedia.org/ontology/wikiPageRedirects",
            "http://dbpedia.org/ontology/wikiPageDisambiguates",
            "http://dbpedia.org/ontology/individualisedPnd");

    public DatasetBasedGraphGenerator(SparqlEndpoint endpoint) {
        this(endpoint, (String)null);
    }

    public DatasetBasedGraphGenerator(SparqlEndpoint endpoint, CacheCoreEx cacheBackend) {
        qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
        if(cacheBackend != null){
        	CacheEx cacheFrontend = new CacheExImpl(cacheBackend);
            qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
        }
        
//		qef = new QueryExecutionFactoryPaginated(qef, 10000);
//		qef = new QueryExecutionFactoryDelay(qef, 500);

        reasoner = new SPARQLReasoner(new SparqlEndpointKS(endpoint), cacheBackend);
    }
    
    public DatasetBasedGraphGenerator(SparqlEndpoint endpoint, String cacheDirectory ) {
        qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
        if(cacheDirectory != null){
        	try {
				long timeToLive = TimeUnit.DAYS.toMillis(30);
				CacheCoreEx cacheBackend = CacheCoreH2.create(true, cacheDirectory, "sparql", timeToLive, true);
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
        //get the outgoing properties with a prominence score above threshold
        final SortedSet<ObjectProperty> outgoingProperties = getMostProminentProperties(cls, threshold, namespace, Direction.OUTGOING);
        
        //get the incoming properties with a prominence score above threshold
        SortedSet<ObjectProperty> incomingProperties = getMostProminentProperties(cls, threshold, namespace, Direction.INCOMING);
        
        //add properties that have as domain the class
//        outgoingProperties.addAll(reasoner.getObjectPropertiesWithDomain(cls));
        
        //compute the frequency for each pair of properties
        Map<Set<ObjectProperty>, Double> cooccurrences;
        if (c.equals(Cooccurrence.TRIPLESTORE)) {
            cooccurrences = getCooccurrences(cls, outgoingProperties, incomingProperties);
        } else {
            cooccurrences = getPropertySimilarities(cls, Sets.union(outgoingProperties, incomingProperties));
        }

        //create the weighted graph
        WeightedGraph wg = new WeightedGraph();
        LoadingCache<ObjectProperty, Node> property2Node = CacheBuilder.newBuilder().maximumSize(Sets.union(outgoingProperties, incomingProperties).size()).build(
                new CacheLoader<ObjectProperty, Node>() {

                    public Node load(ObjectProperty property) {
                        return outgoingProperties.contains(property) ? new Node(property.getName()) : new Node(property.getName(), false);
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
                if (frequency > 0) {
                    
                    wg.addEdge(node1, node2, frequency);
                    if (c.equals(Cooccurrence.PROPERTIES)) {
                        wg.addEdge(node2, node1, frequency);
                    }
                }
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
    
    private Map<Set<ObjectProperty>, Double> getCooccurrences(NamedClass cls, Set<ObjectProperty> outgoingProperties, Set<ObjectProperty> incomingProperties) {
        Map<Set<ObjectProperty>, Double> pair2Frequency = new HashMap<Set<ObjectProperty>, Double>();
        
        SetView<ObjectProperty> allProperties = Sets.union(outgoingProperties, incomingProperties);
        
        //compute the frequency for each pair
        ResultSet rs;
        Set<ObjectProperty> pair;
        for (ObjectProperty prop1 : allProperties) {
            for (ObjectProperty prop2 : allProperties) {
                if (!prop1.equals(prop2) && !pair2Frequency.containsKey(pair = Sets.newHashSet(prop1, prop2))) {
                    String query = "SELECT (COUNT(DISTINCT ?s) AS ?cnt) WHERE {"
                            + "?s a <" + cls.getName() + ">.";
                    if(outgoingProperties.contains(prop1)){
                    	query += "?s <" + prop1.getName() + "> ?o1.";
                    } else {
                    	query += "?o1 <" + prop1.getName() + "> ?s.";
                    }
                    if(outgoingProperties.contains(prop2)){
                    	query += "?s <" + prop2.getName() + "> ?o2.";
                    } else {
                    	query += "?o2 <" + prop2.getName() + "> ?s.";
                    }
                    query +=  "}";
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

    /**
     * Get all properties and its frequencies based on how often each property
     * is used by instances of the given class.
     *
     * @param cls
     * @param namespace
     * @return
     */
    private Map<ObjectProperty, Integer> getPropertiesWithFrequency(NamedClass cls, Direction propertyDirection) {
        Map<ObjectProperty, Integer> properties = new HashMap<ObjectProperty, Integer>();
        String query;
        if(propertyDirection == Direction.OUTGOING){
        	 query = "SELECT ?p (COUNT(DISTINCT ?s) AS ?cnt) WHERE {"
             		+ "?s a <" + cls.getName() + ">."
             		+ " {?p a owl:ObjectProperty.} UNION {?p a owl:DatatypeProperty.} "
             		+ "?s ?p ?o."
             		+ "} GROUP BY ?p";
        } else {
        	 query = "SELECT ?p (COUNT(DISTINCT ?s) AS ?cnt) WHERE {"
             		+ "?s a <" + cls.getName() + ">."
             		+ " {?p a owl:ObjectProperty.} UNION {?p a owl:DatatypeProperty.} "
             		+ "?o ?p ?s."
             		+ "} GROUP BY ?p";
        }

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

    private SortedSet<ObjectProperty> getMostProminentProperties(NamedClass cls, double threshold, String namespace, Direction propertyDirection) {
    	logger.info("Computing most prominent " + propertyDirection.name().toLowerCase() + " properties for class " + cls + " ...");
        SortedSet<ObjectProperty> properties = new TreeSet<ObjectProperty>();

        //get total number of instances for the class
        int instanceCount = getInstanceCount(cls);
        logger.info("Number of instances in class: " + instanceCount);

        //get all properties+frequency that are used by instances of the class
        Map<ObjectProperty, Integer> propertiesWithFrequency = getPropertiesWithFrequency(cls, propertyDirection);

        //get all properties with a relative frequency above the threshold
        for (Entry<ObjectProperty, Integer> entry : MapUtils.sortByValues(propertiesWithFrequency)) {
            ObjectProperty property = entry.getKey();
            Integer frequency = entry.getValue();
           
            double score = frequency / (double) instanceCount;
            logger.info(property + ": " + frequency + " -> " + score);
            if (score >= threshold) {
                properties.add(property);
            }
        }
        logger.info("...got " + properties);
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
        Lexicon lexicon = Lexicon.getDefaultLexicon();
        NLGFactory nlg = new NLGFactory(lexicon);
        Realiser realiser = new Realiser(lexicon);
    	SPhraseSpec clause = nlg.createClause();
    	clause.setSubject(nlg.createNounPhrase("Stephen King"));
    	clause.setVerbPhrase(nlg.createVerbPhrase("be author of"));
    	clause.setObject("Mysery");
    	System.out.println(realiser.realise(clause));
        
        
    	
        SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
        //endpoint.getDefaultGraphURIs().add("http://dbpedia.org/sparql");
        WeightedGraph wg = new DatasetBasedGraphGenerator(endpoint, "cache").generateGraph(new NamedClass("http://dbpedia.org/ontology/AmericanFootballPlayer"), 0.5, "http://dbpedia.org/ontology/", Cooccurrence.PROPERTIES);
//		generateGraph(0.5, "http://dbpedia.org/ontology/");
    }
}