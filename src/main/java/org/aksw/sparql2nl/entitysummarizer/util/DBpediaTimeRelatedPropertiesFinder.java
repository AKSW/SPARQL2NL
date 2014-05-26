/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer.util;

import java.util.Set;

import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * @author Lorenz Buehmann
 *
 */
public class DBpediaTimeRelatedPropertiesFinder {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
		SPARQLReasoner reasoner = new SPARQLReasoner(endpoint, "cache");
		QueryExecutionFactoryHttp qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
		Set<ObjectProperty> properties = reasoner.getOWLObjectProperties();
		for (ObjectProperty p : properties) {
			String query = "SELECT ?o WHERE {?s <" + p + "> ?o} LIMIT 1"; 
			QueryExecution qe = qef.createQueryExecution(query);
			ResultSet rs = qe.execSelect();
			if(rs.hasNext()){
				Resource object = rs.next().getResource("o");
				if(object.getURI().contains("__")){
					System.out.println(p);
				}
			}
			qe.close();
		}

	}

}
