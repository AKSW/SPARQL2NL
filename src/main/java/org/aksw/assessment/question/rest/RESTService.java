/**
 * 
 */
package org.aksw.assessment.question.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
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
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

/**
 * @author Lorenz Buehmann
 *
 */
@Path("/questions")
public class RESTService {
	
	SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
	String namespace = "http://dbpedia.org/ontology/";
	String cacheDirectory = "cache";
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public RESTQuestions getQuestionsJSON(@QueryParam("domain") String domain, @QueryParam("type") List<String> questionTypes, @QueryParam("limit") int maxNrOfQuestions) {
		Map<QuestionType, QuestionGenerator> generators = Maps.newLinkedHashMap();
		
		Map<NamedClass, Set<ObjectProperty>> domains = new HashMap<NamedClass, Set<ObjectProperty>>();
		domains.put(new NamedClass(domain), new HashSet<ObjectProperty>());
		
		//set up the question generators
		for (String type : questionTypes) {
			if(type.equals(QuestionType.MC.getName())){
				generators.put(QuestionType.MC, new MultipleChoiceQuestionGenerator(endpoint, cacheDirectory, namespace, domains));
			} else if(type.equals(QuestionType.JEOPARDY.getName())){
				generators.put(QuestionType.JEOPARDY, new JeopardyQuestionGenerator(endpoint, cacheDirectory, namespace, domains));
			} else if(type.equals(QuestionType.TRUEFALSE.getName())){
				generators.put(QuestionType.TRUEFALSE, new TrueFalseQuestionGenerator(endpoint, cacheDirectory, namespace, domains));
			}
		}
		List<RESTQuestion> restQuestions = new ArrayList<>();
		
		//get random numbers for max. computed questions per type
		int[] randomNumbers = getRandomNumbers(maxNrOfQuestions, questionTypes.size());
		
		int i = 0;
		for (Entry<QuestionType, QuestionGenerator> entry : generators.entrySet()) {
			QuestionType questionType = entry.getKey();
			QuestionGenerator generator = entry.getValue();
		
			//randomly set the max number of questions
			int max = randomNumbers[i++];
			
			Set<Question> questions = generator.getQuestions(null, 1, max);
			
			for (Question question : questions) {
				RESTQuestion q = new RESTQuestion();
				q.setQuestion(question.getText());
				q.setQuestionType(questionType.getName());
				List<RESTAnswer> correctAnswers = new ArrayList<>();
				for (Answer answer : question.getCorrectAnswers()) {
					RESTAnswer a = new RESTAnswer();
					a.setAnswer(answer.getText());
					if(questionType == QuestionType.MC){
						a.setAnswerHint(answer.getHint());
					}
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
	
	private int[] getRandomNumbers(int total, int groups){
		Random rnd = new Random(123);
		int[] numbers = new int[groups];
		for (int i = 0; i < groups - 1; i++) {
			int number = rnd.nextInt(total-(groups - i)) + 1;
			total -= number;
			numbers[i] = number;
		}
		numbers[groups-1] = total;
		return numbers;
	}
}
