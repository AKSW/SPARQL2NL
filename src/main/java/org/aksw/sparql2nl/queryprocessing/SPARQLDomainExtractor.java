package org.aksw.sparql2nl.queryprocessing;

import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.kb.sparql.SparqlQuery;

import com.hp.hpl.jena.query.ResultSet;

public class SPARQLDomainExtractor implements DomainExtractor{
	
	private SparqlEndpoint endpoint;
	
	public SPARQLDomainExtractor(SparqlEndpoint endpoint) {
		this.endpoint = endpoint;
	}

	@Override
	public String getDomain(String propertyURI) {
		String query = String.format("SELECT ?domain WHERE {<%s> <http://www.w3.org/2000/01/rdf-schema#domain> ?domain}", propertyURI);
		ResultSet rs = new SparqlQuery(query, endpoint).send(false);
		while(rs.hasNext()){
			return rs.next().get("domain").asResource().getURI();
		}
		return null;
	}

}
