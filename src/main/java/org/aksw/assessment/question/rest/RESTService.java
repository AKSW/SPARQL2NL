/**
 * 
 */
package org.aksw.assessment.question.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.aksw.assessment.question.JeopardyQuestionGenerator;
import org.aksw.assessment.question.MultipleChoiceQuestionGenerator;
import org.aksw.assessment.question.Question;
import org.aksw.assessment.question.QuestionGenerator;
import org.aksw.assessment.question.QuestionType;
import org.aksw.assessment.question.TrueFalseQuestionGenerator;
import org.aksw.assessment.question.answer.Answer;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

/**
 * @author Lorenz Buehmann
 *
 */
@Path("/questions")
public class RESTService {
	
	SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
	String cacheDirectory = "cache";
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public RESTQuestions getQuestionsJSON(@QueryParam("domain") String domain, @QueryParam("type") List<String> questionTypes, @QueryParam("limit") int maxNrOfQuestions) {
		Map<QuestionType, QuestionGenerator> generators = Maps.newLinkedHashMap();
		
		for (String type : questionTypes) {
			if(type.equals(QuestionType.MC.getName())){
				generators.put(QuestionType.MC, new MultipleChoiceQuestionGenerator(endpoint, cacheDirectory, Sets.newHashSet(ResourceFactory.createResource(domain))));
			} else if(type.equals(QuestionType.JEOPARDY.getName())){
				generators.put(QuestionType.JEOPARDY, new JeopardyQuestionGenerator(endpoint, cacheDirectory, Sets.newHashSet(ResourceFactory.createResource(domain))));
			} else if(type.equals(QuestionType.TRUEFALSE.getName())){
				generators.put(QuestionType.TRUEFALSE, new TrueFalseQuestionGenerator(endpoint, cacheDirectory, Sets.newHashSet(ResourceFactory.createResource(domain))));
			}
		}
		System.out.println(generators);
		List<RESTQuestion> restQuestions = new ArrayList<>();
		
		for (Entry<QuestionType, QuestionGenerator> entry : generators.entrySet()) {
			QuestionType questionType = entry.getKey();
			QuestionGenerator generator = entry.getValue();
		
			Set<Question> questions = generator.getQuestions(null, 1, maxNrOfQuestions);
			
			for (Question question : questions) {
				RESTQuestion q = new RESTQuestion();
				q.setQuestion(question.getText());
				q.setQuestionType(questionType.getName());
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
		}
		
		RESTQuestions result = new RESTQuestions();
		result.setQuestions(restQuestions);
		
		return result;
 
	}
}
