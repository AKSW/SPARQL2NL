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

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.aksw.assessment.question.DBpediaPropertyBlackList;
import org.aksw.assessment.question.JeopardyQuestionGenerator;
import org.aksw.assessment.question.MultipleChoiceQuestionGenerator;
import org.aksw.assessment.question.Question;
import org.aksw.assessment.question.QuestionGenerator;
import org.aksw.assessment.question.QuestionType;
import org.aksw.assessment.question.TrueFalseQuestionGenerator;
import org.aksw.assessment.question.answer.Answer;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

import com.google.common.collect.Maps;

/**
 * @author Lorenz Buehmann
 *
 */
@Path("/assess")
public class RESTService {
	
	private static final Logger logger = Logger.getLogger(RESTService.class.getName());
	
	SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
	String namespace = "http://dbpedia.org/ontology/";
	String cacheDirectory = "cache";
	
	@GET
	@Context
	@Path("/questions")
	@Produces(MediaType.APPLICATION_JSON)
	public RESTQuestions getQuestionsJSON(@Context ServletContext context, @QueryParam("domain") String domain, @QueryParam("type") List<String> questionTypes, @QueryParam("limit") int maxNrOfQuestions) {
		logger.info("REST Request - Get questions\nDomain:" + domain + "\nQuestionTypes:" + questionTypes + "\n#Questions:" + maxNrOfQuestions);
		
		cacheDirectory = context.getRealPath(cacheDirectory);
		
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
	
	@GET
	@Context
	@Path("/properties")
	@Produces(MediaType.APPLICATION_JSON)
	public List<String> getApplicableProperties(@Context ServletContext context, @QueryParam("class") String classURI) {
		logger.info("REST Request - Get all properties for class " + classURI);
		
		SPARQLReasoner reasoner = new SPARQLReasoner(endpoint, context.getRealPath(cacheDirectory));
		List<String> properties = new ArrayList<String>();
		for (ObjectProperty p : reasoner.getObjectProperties(new NamedClass(classURI))) {
			if(!DBpediaPropertyBlackList.contains(p.getName())){
				properties.add(p.getName());
			}
		}
		
		return properties;
	}
	
	@GET
	@Context
	@Path("/classes")
	@Produces(MediaType.APPLICATION_JSON)
	public List<String> getClasses(@Context ServletContext context) {
		logger.info("REST Request - Get all classes");
		
		SPARQLReasoner reasoner = new SPARQLReasoner(endpoint, context.getRealPath(cacheDirectory));
		List<String> classes = new ArrayList<String>();
		for (NamedClass cls : reasoner.getOWLClasses()) {
			if(!DBpediaPropertyBlackList.contains(cls.getName())){
				classes.add(cls.getName());
			}
		}
		
		return classes;
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
