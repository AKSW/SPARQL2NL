/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer.gender;

import java.util.HashSet;
import java.util.Set;

import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.jena_sparql_api.cache.extra.CacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheExImpl;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.apache.jena.web.HttpSC;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP;

/**
 * @author Lorenz Buehmann
 *
 */
public class TypeAwareGenderDetector implements GenderDetector{
	
	private static final Logger logger = LoggerFactory.getLogger(TypeAwareGenderDetector.class);

	private QueryExecutionFactory qef;
	
	private GenderDetector genderDetector;
	private Set<String> personTypes = new HashSet<>();
	
	private boolean useInference = true;

	public TypeAwareGenderDetector(SparqlEndpoint endpoint, CacheCoreEx cacheBackend, GenderDetector genderDetector) {
		this.genderDetector = genderDetector;
		
		qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
        if(cacheBackend != null){
        	CacheEx cacheFrontend = new CacheExImpl(cacheBackend);
            qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
        }
	}
	
	public TypeAwareGenderDetector(QueryExecutionFactory qef, GenderDetector genderDetector) {
		this.qef = qef;
		this.genderDetector = genderDetector;
	}
	
	public TypeAwareGenderDetector(SparqlEndpoint endpoint, GenderDetector genderDetector) {
		this(endpoint, null, genderDetector);
	}
	
	public void setPersonTypes(Set<String> personTypes){
		this.personTypes = personTypes;
		
		//get the inferred sub types as well
		if(useInference){
			Set<String> inferredTypes = new HashSet<>();
			for (String type : personTypes) {
				String query = "select ?sub where{?sub rdfs:subClassOf* <" + type + ">.}";
				ResultSet rs = qef.createQueryExecution(query).execSelect();
				QuerySolution qs;
				while(rs.hasNext()){
					qs = rs.next();
					inferredTypes.add(qs.getResource("sub").getURI());
				}
			}
			personTypes.addAll(inferredTypes);
		}
	}
	
	public Gender getGender(String uri, String label) {
		if(isPerson(uri)){
			return genderDetector.getGender(label);
		}
		return Gender.UNKNOWN;
	}

	/* (non-Javadoc)
	 * @see org.aksw.sparql2nl.entitysummarizer.gender.GenderDetector#getGender(java.lang.String)
	 */
	@Override
	public Gender getGender(String name) {
		return genderDetector.getGender(name);
	}
	
	private boolean isPerson(String uri){
		if(personTypes.isEmpty()){
			return true;
		} else {
			//get types of URI
			Set<String> types = new HashSet<>();
			try {
				String query = "SELECT ?type WHERE {<" + uri + "> a ?type.}";
				ResultSet rs = qef.createQueryExecution(query).execSelect();
				QuerySolution qs;
				while(rs.hasNext()){
					qs = rs.next();
					types.add(qs.getResource("type").getURI());
				}
			} catch (Exception e) {
				int code = ((QueryExceptionHTTP)e.getCause()).getResponseCode();
				logger.warn("SPARQL query execution failed: " + code + " - " + HttpSC.getCode(code).getMessage());
			}
			return !Sets.intersection(personTypes, types).isEmpty();
		}
	}
}
