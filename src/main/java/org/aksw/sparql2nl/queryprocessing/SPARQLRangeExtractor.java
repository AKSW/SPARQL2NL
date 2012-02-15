package org.aksw.sparql2nl.queryprocessing;

import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.kb.sparql.SparqlQuery;

import com.hp.hpl.jena.query.ResultSet;

public class SPARQLRangeExtractor implements RangeExtractor{
	
	private SparqlEndpoint endpoint;
	
	public SPARQLRangeExtractor(SparqlEndpoint endpoint) {
		this.endpoint = endpoint;
	}

	@Override
	public String getRange(String propertyURI) {
		String query = String.format("SELECT ?range WHERE {<%s> <http://www.w3.org/2000/01/rdf-schema#range> ?range}", propertyURI);
		ResultSet rs = new SparqlQuery(query, endpoint).send(false);
		while(rs.hasNext()){
			return rs.next().get("range").asResource().getURI();
		}
		return null;
	}

}
