/**
 * 
 */
package org.aksw.assessment.question.informativeness;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.ResultSet;

/**
 * @author Lorenz Buehmann
 *
 */
public class StatisticalInformativenessGenerator implements InformativenessGenerator{
	
	private QueryExecutionFactory qef;
	
	private static final ParameterizedSparqlString incomingLinksTemplate = new ParameterizedSparqlString("SELECT (COUNT(*) AS ?cnt) WHERE {?s ?p ?o}");
	private static final ParameterizedSparqlString outgoingLinksTemplate = new ParameterizedSparqlString("SELECT (COUNT(*) AS ?cnt) WHERE {?s ?p ?o}");
	
	public StatisticalInformativenessGenerator(SparqlEndpoint endpoint) {
		qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
	}

	/* (non-Javadoc)
	 * @see org.aksw.assessment.question.informativeness.InformativenessGenerator#computeInformativeness(com.hp.hpl.jena.graph.Triple)
	 */
	@Override
	public double computeInformativeness(Triple triple) {
		double informativeness = 0;
		
		//get the popularity of the subject, i.e. the incoming links
		incomingLinksTemplate.setIri("s", triple.getSubject().getURI());
		Query query = incomingLinksTemplate.asQuery();
		QueryExecution qe = qef.createQueryExecution(query);
		ResultSet rs = qe.execSelect();
		int subjectPropularity = rs.next().getLiteral("cnt").getInt();
		
		//get the popularity of the object, i.e. the incoming links
		outgoingLinksTemplate.setIri("o", triple.getObject().getURI());
		query = outgoingLinksTemplate.asQuery();
		qe = qef.createQueryExecution(query);
		rs = qe.execSelect();
		int objectPropularity = rs.next().getLiteral("cnt").getInt();
		
		informativeness = Math.log(subjectPropularity + objectPropularity);
		
		qe.close();
		
		return informativeness;
	}

}
