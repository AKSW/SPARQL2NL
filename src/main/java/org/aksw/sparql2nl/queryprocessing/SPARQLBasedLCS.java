package org.aksw.sparql2nl.queryprocessing;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.kb.sparql.SparqlQuery;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.vocabulary.OWL;

public class SPARQLBasedLCS implements LCS{
	
	private SparqlEndpoint endpoint;
	
	public SPARQLBasedLCS(SparqlEndpoint endpoint) {
		this.endpoint = endpoint;
	}

	@Override
	public String getLCS(Collection<String> classes) {
		Iterator<String> iter = classes.iterator();
		String lcs = getLCS(iter.next(), iter.next());
		while(iter.hasNext()){
			lcs = getLCS(lcs, iter.next());
		}
		return lcs;
	}
	
	private String getLCS(String cls1, String cls2){
		//check some common cases first
		if(cls1.equals(cls2)){
			return cls1;
		}
		if(cls1.equals(OWL.Thing.getURI()) || cls2.equals(OWL.Thing.getURI())){
			return OWL.Thing.getURI();
		}
		
		
		
		//if not a common case
		return null;
	}
	
	private Set<String> getSuperClasses(String cls){
		Set<String> superClasses = new HashSet<String>();
		String query = String.format("SELECT ?sup WHERE {<%s> <http://www.w3.org/2000/01/rdf-schema#subClassOf> ?sup}", cls);
		ResultSet rs = new SparqlQuery(query, endpoint).send(false);
		QuerySolution qs;
		while(rs.hasNext()){
			qs = rs.next();
			if(qs.get("sup").isURIResource()){
				superClasses.add(qs.get("sup").asResource().getURI());
			}
		}
		return superClasses;
	}

}
