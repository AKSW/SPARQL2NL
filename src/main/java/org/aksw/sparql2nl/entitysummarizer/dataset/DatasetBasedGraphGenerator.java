/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer.dataset;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreH2;
import org.aksw.jena_sparql_api.cache.extra.CacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheExImpl;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.jena_sparql_api.pagination.core.QueryExecutionFactoryPaginated;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

/**
 * @author Lorenz Buehmann
 *
 */
public class DatasetBasedGraphGenerator {

	
	private SparqlEndpoint endpoint;
	private QueryExecutionFactory qef;
	private SPARQLReasoner reasoner;

	public DatasetBasedGraphGenerator(SparqlEndpoint endpoint) {
		this(endpoint, null);
	}
	
	public DatasetBasedGraphGenerator(SparqlEndpoint endpoint, String cacheDirectory) {
		this.endpoint = endpoint;
		
		qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
		if(cacheDirectory != null){
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
		qef = new QueryExecutionFactoryPaginated(qef, 10000);
		
		reasoner = new SPARQLReasoner(new SparqlEndpointKS(endpoint), cacheDirectory);
	}
	
	public void generateGraph(double threshold){
		generateGraph(threshold, null);
	}
	
	public void generateGraph(double threshold, String namespace){
		
		//get all classes in knowledge base with given prefix
		Set<NamedClass> classes = reasoner.getTypes(namespace);
		
		SortedSet<ObjectProperty> properties;
		for (NamedClass cls : classes) {
			
			properties = getMostProminentProperties(cls, threshold);
			
			getCoOccurrences(cls, properties);
		}
		
	}
	
	public void generateGraph(NamedClass cls, double threshold){
		generateGraph(cls, threshold, null);
	}
	
	public void generateGraph(NamedClass cls, double threshold, String namespace){
		SortedSet<ObjectProperty> properties = getMostProminentProperties(cls, threshold, namespace);
		
		getCoOccurrences(cls, properties);
	}
	
	private void getCoOccurrences(NamedClass cls, Set<ObjectProperty> properties){
		Set<Set<ObjectProperty>> powerSet = Sets.powerSet(properties);
		ResultSet rs;
		for (Set<ObjectProperty> set : powerSet) {
			if(set.size() > 1){
				String query = "SELECT (COUNT(DISTINCT ?s) AS ?cnt) WHERE {\n" +
				"?s a <" + cls.getName() + ">.";
				int i = 1;
				for (ObjectProperty property : set) {
					query += "?s <" + property.getName() + "> ?o" + i++ + ".\n";
				}
				query += "}";
				
				rs = executeSelectQuery(query);
				int frequency = rs.next().getLiteral("cnt").getInt();
				System.out.println(frequency + ": " + set);
			}
		}
	}
	
	private Map<ObjectProperty, Integer> getPropertiesWithFrequency(NamedClass cls){
		return getPropertiesWithFrequency(cls, null);
	}
	
	/**
	 * Get all properties and its frequencies based on how often each property is used by instances of the given class.
	 * @param cls
	 * @param namespace
	 * @return
	 */
	private Map<ObjectProperty, Integer> getPropertiesWithFrequency(NamedClass cls, String namespace){
		Map<ObjectProperty, Integer> properties = new HashMap<ObjectProperty, Integer>();
		String query = 
				"SELECT ?p (COUNT(DISTINCT ?s) AS ?cnt) WHERE {" +
				"?s a <" + cls.getName() + ">." +
				"?s ?p ?o." +
				"{SELECT DISTINCT ?p WHERE {?s a <" + cls.getName() + ">. ?s ?p ?o." + 
				(namespace != null ? "FILTER(REGEX(?p,'" + namespace + "'))" : "") +
				"}}} GROUP BY ?p ORDER BY DESC(?cnt)";
		
		ResultSet rs = executeSelectQuery(query);
		QuerySolution qs;
		while(rs.hasNext()){
			qs = rs.next();
			properties.put(new ObjectProperty(qs.getResource("p").getURI()), qs.getLiteral("cnt").getInt());
		}
		return properties;
	}
	
	private SortedSet<ObjectProperty> getMostProminentProperties(NamedClass cls, double threshold){
		return getMostProminentProperties(cls, threshold, null);
	}
	
	private SortedSet<ObjectProperty> getMostProminentProperties(NamedClass cls, double threshold, String namespace){
		SortedSet<ObjectProperty> properties = new TreeSet<ObjectProperty>();
		
		//get total number of instances for the class
		int instanceCount = getInstanceCount(cls);
		
		//get all properties+frequency that are used by instances of the class
		Map<ObjectProperty, Integer> propertiesWithFrequency = getPropertiesWithFrequency(cls, namespace);
		
		//get all properties with a relative frequency above the threshold
		for (Entry<ObjectProperty, Integer> entry : propertiesWithFrequency.entrySet()) {
			ObjectProperty property = entry.getKey();
			Integer frequency = entry.getValue();
			double score = frequency / (double)instanceCount;
			
			if(score >= threshold){
				properties.add(property);
			}
		}
		
		return properties;
	}
	
	/**
	 * Get the total number of instances for the given class.
	 * @param cls
	 * @return
	 */
	private int getInstanceCount(NamedClass cls){
		String query = "SELECT (COUNT(?s) AS ?cnt) WHERE {?s a <" + cls.getName() + ">.}";
		ResultSet rs = executeSelectQuery(query);
		return rs.next().getLiteral("cnt").getInt();
	}
	
	private ResultSet executeSelectQuery(String query){
		QueryExecution qe = qef.createQueryExecution(query);
		ResultSet rs = qe.execSelect();
		return rs;
	}
	
	public static void main(String[] args) throws Exception {
		new DatasetBasedGraphGenerator(SparqlEndpoint.getEndpointDBpedia(), "cache").
		generateGraph(new NamedClass("http://dbpedia.org/ontology/Person"), 0.5, "http://dbpedia.org/ontology/");
	}

}
