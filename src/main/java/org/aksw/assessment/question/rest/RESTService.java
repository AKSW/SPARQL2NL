/**
 * 
 */
package org.aksw.assessment.question.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.aksw.assessment.question.BlackList;
import org.aksw.assessment.question.DBpediaPropertyBlackList;
import org.aksw.assessment.question.JeopardyQuestionGenerator;
import org.aksw.assessment.question.MultipleChoiceQuestionGenerator;
import org.aksw.assessment.question.Question;
import org.aksw.assessment.question.QuestionGenerator;
import org.aksw.assessment.question.QuestionType;
import org.aksw.assessment.question.TrueFalseQuestionGenerator;
import org.aksw.assessment.question.answer.Answer;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * @author Lorenz Buehmann
 *
 */
@Path("/assess")
public class RESTService {
	
	private static final Logger logger = Logger.getLogger(RESTService.class.getName());
	
	static SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
	String namespace = "http://dbpedia.org/ontology/";
	String cacheDirectory = "cache";
	Set<String> personTypes = Sets.newHashSet("http://dbpedia.org/ontology/Person");
	BlackList blackList = new DBpediaPropertyBlackList();
	
	static Map<SparqlEndpoint, List<String>> classesCache = new HashMap<>();
	static Map<String, List<String>> propertiesCache = new HashMap<>();
	static Map<SparqlEndpoint, Map<String, List<String>>> applicableEntitesCache = new HashMap<>();
	
	/**
	 * Precompute all applicable classes and for each class its applicable properties.
	 * @param context
	 */
	private void precomputeApplicableEntities(ServletContext context){
		//get the classes
		List<String> classes = getClasses(context);
		//for each class get the properties
		for (String cls : classes) {
			List<String> properties = getApplicableProperties(context, cls);
			
		}
	}
	
	@GET
	@Context
	@Path("/questionsold")
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
				generators.put(QuestionType.MC, new MultipleChoiceQuestionGenerator(endpoint, cacheDirectory, namespace, domains, personTypes, blackList));
			} else if(type.equals(QuestionType.JEOPARDY.getName())){
				generators.put(QuestionType.JEOPARDY, new JeopardyQuestionGenerator(endpoint, cacheDirectory, namespace, domains, personTypes, blackList));
			} else if(type.equals(QuestionType.TRUEFALSE.getName())){
				generators.put(QuestionType.TRUEFALSE, new TrueFalseQuestionGenerator(endpoint, cacheDirectory, namespace, domains, personTypes, blackList));
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
	
	@POST
	@Context
	@Path("/questions")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public RESTQuestions getQuestionsJSON2(@Context ServletContext context, JSONArray domain, @QueryParam("type") List<String> questionTypes, @QueryParam("limit") int maxNrOfQuestions) {
		logger.info("REST Request - Get questions\nQuestionTypes:" + questionTypes + "\n#Questions:" + maxNrOfQuestions);
		
		cacheDirectory = context.getRealPath(cacheDirectory);
		
		Map<QuestionType, QuestionGenerator> generators = Maps.newLinkedHashMap();
		
		//extract the domain from the JSON array
		Map<NamedClass, Set<ObjectProperty>> domains = new HashMap<NamedClass, Set<ObjectProperty>>();
		try {
			for(int i = 0; i < domain.length(); i++){
				JSONObject entry = domain.getJSONObject(i);
				NamedClass cls = new NamedClass(entry.getString("className"));
				JSONArray propertiesArray = entry.getJSONArray("properties");
				Set<ObjectProperty> properties = new HashSet<>();
				for (int j = 0; j < propertiesArray.length(); j++) {
					properties.add(new ObjectProperty(propertiesArray.getString(j)));
				}
				domains.put(cls, properties);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		logger.info("Domain:" + domains);
		
		//set up the question generators
		for (String type : questionTypes) {
			if(type.equals(QuestionType.MC.getName())){
				generators.put(QuestionType.MC, new MultipleChoiceQuestionGenerator(endpoint, cacheDirectory, namespace, domains, personTypes, blackList));
			} else if(type.equals(QuestionType.JEOPARDY.getName())){
				generators.put(QuestionType.JEOPARDY, new JeopardyQuestionGenerator(endpoint, cacheDirectory, namespace, domains, personTypes, blackList));
			} else if(type.equals(QuestionType.TRUEFALSE.getName())){
				generators.put(QuestionType.TRUEFALSE, new TrueFalseQuestionGenerator(endpoint, cacheDirectory, namespace, domains, personTypes, blackList));
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
			logger.info("Get " + max + " questions of type " + questionType.getName() + "...");
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
		
		List<String> properties = propertiesCache.get(classURI);
		
		if(properties == null){
			SPARQLReasoner reasoner = new SPARQLReasoner(endpoint, context.getRealPath(cacheDirectory));
			properties = new ArrayList<String>();
			for (ObjectProperty p : reasoner.getObjectProperties(new NamedClass(classURI))) {
				if(!blackList.contains(p.getName())){
					properties.add(p.getName());
				}
			}
			Collections.sort(properties); 
			propertiesCache.put(classURI, properties);
		}
		
		
		return properties;
	}
	
	@GET
	@Context
	@Path("/classes")
	@Produces(MediaType.APPLICATION_JSON)
	public List<String> getClasses(@Context ServletContext context) {
		logger.info("REST Request - Get all classes");
		
		List<String> classes = classesCache.get(endpoint);
		
		if(classes == null){
			SPARQLReasoner reasoner = new SPARQLReasoner(endpoint, cacheDirectory);
			classes = new ArrayList<String>();
			for (NamedClass cls : reasoner.getNonEmptyOWLClasses()) {
				if (!blackList.contains(cls.getName())) {
					classes.add(cls.getName());
				}
			}
			Collections.sort(classes); 
			classesCache.put(endpoint, classes);
		}
		
		return classes;
	}
	
	@GET
	@Context
	@Path("/entities")
	@Produces(MediaType.APPLICATION_JSON)
	public Map<String, List<String>> getEntities(@Context ServletContext context) {
		logger.info("REST Request - Get all applicable entities");

		Map<String, List<String>> entities = applicableEntitesCache.get(endpoint);

		if(entities == null){
			entities = new LinkedHashMap<>();
			// get the classes
			List<String> classes = getClasses(context);
			// for each class get the properties
			for (String cls : classes) {
				List<String> properties = getApplicableProperties(context, cls);
				if (!properties.isEmpty()) {
					entities.put(cls, properties);
				}
			}
			applicableEntitesCache.put(endpoint, entities);
		}
		return entities;
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
