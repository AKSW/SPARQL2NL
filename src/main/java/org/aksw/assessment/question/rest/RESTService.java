/**
 * 
 */
package org.aksw.assessment.question.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.aksw.assessment.question.MultipleChoiceQuestionGenerator;
import org.aksw.assessment.question.Question;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

/**
 * @author Lorenz Buehmann
 *
 */
@Path("/questions")
public class RESTService {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<Question> getQuestionsJSON(@PathParam("domain") String domain) {
		 System.out.println(domain);
		MultipleChoiceQuestionGenerator generator = new MultipleChoiceQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), "cache", Sets.newHashSet(ResourceFactory.createResource(domain)));
		Set<Question> questions = generator.getQuestions(null, 1, 10);
		
		return new ArrayList<>(questions); 
 
	}
}
