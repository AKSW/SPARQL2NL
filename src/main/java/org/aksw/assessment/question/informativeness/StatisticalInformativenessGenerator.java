/**
 * 
 */
package org.aksw.assessment.question.informativeness;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.ParameterizedSparqlString;

/**
 * @author Lorenz Buehmann
 *
 */
public class StatisticalInformativenessGenerator implements InformativenessGenerator{
	
	private QueryExecutionFactory qef;
	
	private static final ParameterizedSparqlString incomingLinksTemplate = new ParameterizedSparqlString("SELECT COUNT(*) WHERE {?s ?p ?o}");
	private static final ParameterizedSparqlString outgoingLinksTemplate = new ParameterizedSparqlString("SELECT COUNT(*) WHERE {?s ?p ?o}");
	
	public StatisticalInformativenessGenerator(SparqlEndpoint endpoint) {
		qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
	}

	/* (non-Javadoc)
	 * @see org.aksw.assessment.question.informativeness.InformativenessGenerator#computeInformativeness(com.hp.hpl.jena.graph.Triple)
	 */
	@Override
	public double computeInformativeness(Triple triple) {
		
		//get the popularity of the subject,
		String query = "SELECT COUNT(*) WHERE {}";
		
		//get the popularity of the object, i.e. the incoming links
		triple.getObject();
		return 0;
	}

}
