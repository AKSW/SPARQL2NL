/**
 * 
 */
package org.aksw.assessment.question.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.aksw.assessment.question.MultipleChoiceQuestionGenerator;
import org.aksw.assessment.question.Question;
import org.aksw.assessment.question.answer.Answer;
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
	public RESTQuestions getQuestionsJSON(@QueryParam("domain") String domain) {
		MultipleChoiceQuestionGenerator generator = new MultipleChoiceQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), "cache", Sets.newHashSet(ResourceFactory.createResource(domain)));
		Set<Question> questions = generator.getQuestions(null, 1, 10);
		
		List<RESTQuestion> restQuestions = new ArrayList<>();
		for (Question question : questions) {
			RESTQuestion q = new RESTQuestion();
			q.setQuestion(question.getText());
			List<RESTAnswer> correctAnswers = new ArrayList<>();
			for (Answer answer : question.getCorrectAnswers()) {
				RESTAnswer a = new RESTAnswer();
				a.setAnswer(answer.getText());
				a.setAnswerHint("TODO: HINT");
				correctAnswers.add(a);
			}
			q.setCorrectAnswers(correctAnswers);
			List<RESTAnswer> wrongAnswers = new ArrayList<>();
			for (Answer answer : question.getWrongAnswers()) {
				RESTAnswer a = new RESTAnswer();
				a.setAnswer(answer.getText());
				a.setAnswerHint("NO HINT");
				wrongAnswers.add(a);
			}
			q.setWrongAnswers(wrongAnswers);
			restQuestions.add(q);
		}
		RESTQuestions result = new RESTQuestions();
		result.setQuestions(restQuestions);
		
		return result;
 
	}
}
