/**
 * 
 */
package org.aksw.assessment.question.rest;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheFrontend;
import org.aksw.jena_sparql_api.cache.h2.CacheUtilsH2;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.entitysummarizer.dataset.CachedDatasetBasedGraphGenerator;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator.Cooccurrence;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
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
import com.google.common.util.concurrent.MoreExecutors;

/**
 * @author Lorenz Buehmann
 *
 */
@Path("/assess")
public class RESTService {
	
	private static final Logger logger = Logger.getLogger(RESTService.class.getName());
	
	static SparqlEndpoint endpoint;
	static String namespace;
	static String cacheName = "cache";
	static Set<String> personTypes = Sets.newHashSet("http://dbpedia.org/ontology/Person");
	static BlackList blackList = new DBpediaPropertyBlackList();
	
	static Map<SparqlEndpoint, List<String>> classesCache = new HashMap<>();
	static Map<String, List<String>> propertiesCache = new HashMap<>();
	static Map<SparqlEndpoint, Map<String, List<String>>> applicableEntitesCache = new HashMap<>();

	private static double propertyFrequencyThreshold;
	private static Cooccurrence cooccurrenceType;

	private static SPARQLReasoner reasoner;

	private static QueryExecutionFactory qef;
	
	/**
	 * 
	 */
	public RESTService() {
	}
	
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
	
	public static void init(ServletContext context){
		try {
			logger.info("Loading config...");
			HierarchicalINIConfiguration config = new HierarchicalINIConfiguration();
			config.load(RESTService.class.getClassLoader().getResourceAsStream("assess_config.ini"));
			
			//endpoint settings
			SubnodeConfiguration section = config.getSection("endpoint");
			String url = section.getString("url");
			String defaultGraph = section.getString("defaultGraph");
			String namespace = section.getString("namespace");
			RESTService.namespace = namespace;
			RESTService.endpoint = new SparqlEndpoint(new URL(url), defaultGraph);
			String cacheDirectory = section.getString("cacheDirectory", "cache");
			if(cacheDirectory.startsWith("/")){
				RESTService.cacheName = cacheDirectory;
			} else {
				RESTService.cacheName = context.getRealPath(cacheDirectory);
			}
			
			//summarization setting
			section = config.getSection("summarization");
			RESTService.propertyFrequencyThreshold = section.getDouble("propertyFrequencyThreshold");
			RESTService.cooccurrenceType = Cooccurrence.valueOf(section.getString("cooccurrenceType").toUpperCase());
			
			logger.info("Endpoint:" + endpoint);
			logger.info("Namespace:" + namespace);
			logger.info("Cache directory: " + RESTService.cacheName);
			
			qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());

			CacheFrontend frontend = CacheUtilsH2.createCacheFrontend(cacheName, true, TimeUnit.DAYS.toMillis(30));
			qef = new QueryExecutionFactoryCacheEx(qef, frontend);					
			
			reasoner = new SPARQLReasoner(qef);
		} catch (ConfigurationException e) {
			logger.error("Could not load config file.", e);
		} catch (MalformedURLException e) {
			logger.error("Illegal endpoint URL.", e);
		}
	}
	
	@GET
	@Context
	@Path("/questionsold")
	@Produces(MediaType.APPLICATION_JSON)
	public RESTQuestions getQuestionsJSON(@Context ServletContext context, @QueryParam("domain") String domain, @QueryParam("type") List<String> questionTypes, @QueryParam("limit") int maxNrOfQuestions) {
		logger.info("REST Request - Get questions\nDomain:" + domain + "\nQuestionTypes:" + questionTypes + "\n#Questions:" + maxNrOfQuestions);
		
		Map<QuestionType, QuestionGenerator> generators = Maps.newLinkedHashMap();
		
		Map<NamedClass, Set<ObjectProperty>> domains = new HashMap<NamedClass, Set<ObjectProperty>>();
		domains.put(new NamedClass(domain), new HashSet<ObjectProperty>());
		
		//set up the question generators
		for (String type : questionTypes) {
			if(type.equals(QuestionType.MC.getName())){
				generators.put(QuestionType.MC, new MultipleChoiceQuestionGenerator(endpoint, qef, cacheName, namespace, domains, personTypes, blackList));
			} else if(type.equals(QuestionType.JEOPARDY.getName())){
				generators.put(QuestionType.JEOPARDY, new JeopardyQuestionGenerator(endpoint, qef, cacheName, namespace, domains, personTypes, blackList));
			} else if(type.equals(QuestionType.TRUEFALSE.getName())){
				generators.put(QuestionType.TRUEFALSE, new TrueFalseQuestionGenerator(endpoint, qef, cacheName, namespace, domains, personTypes, blackList));
			}
		}
		List<RESTQuestion> restQuestions = new ArrayList<>();
		
		//get random numbers for max. computed questions per type
		List<Integer> randomNumbers = getRandomNumbers(maxNrOfQuestions, questionTypes.size());
		
		int i = 0;
		for (Entry<QuestionType, QuestionGenerator> entry : generators.entrySet()) {
			QuestionType questionType = entry.getKey();
			QuestionGenerator generator = entry.getValue();
		
			//randomly set the max number of questions
			int max = randomNumbers.get(i);
			
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
		logger.info("Done.");
		return result;
 
	}
	
	@POST
	@Context
	@Path("/questions")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public RESTQuestions getQuestionsJSON2(@Context ServletContext context, JSONArray domain, @QueryParam("type") List<String> questionTypes, @QueryParam("limit") int maxNrOfQuestions) {
		logger.info("REST Request - Get questions\nQuestionTypes:" + questionTypes + "\n#Questions:" + maxNrOfQuestions);
		
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
		long start = System.currentTimeMillis();
		for (String type : questionTypes) {
			if (type.equals(QuestionType.MC.getName())) {
				generators.put(QuestionType.MC, new MultipleChoiceQuestionGenerator(endpoint, qef, cacheName,
						namespace, domains, personTypes, blackList));
			} else if (type.equals(QuestionType.JEOPARDY.getName())) {
				generators.put(QuestionType.JEOPARDY, new JeopardyQuestionGenerator(endpoint, qef, cacheName,
						namespace, domains, personTypes, blackList));
			} else if (type.equals(QuestionType.TRUEFALSE.getName())) {
				generators.put(QuestionType.TRUEFALSE, new TrueFalseQuestionGenerator(endpoint, qef, cacheName,
						namespace, domains, personTypes, blackList));
			}
		} 
		long end = System.currentTimeMillis();
		System.out.println("Operation took " + (end - start) + "ms");
		final List<RESTQuestion> restQuestions = Collections.synchronizedList(new ArrayList<RESTQuestion>(maxNrOfQuestions));
		
		//get random numbers for max. computed questions per type
		final List<Integer> partitionSizes = getRandomNumbers(maxNrOfQuestions, questionTypes.size());
		
		ExecutorService tp = Executors.newFixedThreadPool(generators.entrySet().size());
		//submit a task for each question type
        List<Future<List<RESTQuestion>>> list = new ArrayList<Future<List<RESTQuestion>>>();
		int i = 0;
		for (final Entry<QuestionType, QuestionGenerator> entry : generators.entrySet()) {
			QuestionType questionType = entry.getKey();
			QuestionGenerator questionGenerator = entry.getValue();
			list.add(tp.submit(new QuestionGenerationTask(questionType, questionGenerator, partitionSizes.get(i++))));
		}
		for(Future<List<RESTQuestion>> fut : list){
			try {
				List<RESTQuestion> partialRestQuestions = fut.get();
				restQuestions.addAll(partialRestQuestions);
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
        }
		tp.shutdown();
		
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
			properties = new ArrayList<String>();
			for (ObjectProperty p : reasoner.getObjectProperties(new NamedClass(classURI))) {
				if(!blackList.contains(p.getName())){
					properties.add(p.getName());
				}
			}
			Collections.sort(properties); 
			propertiesCache.put(classURI, properties);
		}
		
		logger.info("Done.");
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
			classes = new ArrayList<String>();
			for (NamedClass cls : reasoner.getNonEmptyOWLClasses()) {
				if (!blackList.contains(cls.getName())) {
					classes.add(cls.getName());
				}
			}
			Collections.sort(classes); 
			classesCache.put(endpoint, classes);
		}
		logger.info("Done.");
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
		logger.info("Done.");
		return entities;
	}
	
	public void precomputeGraphs(ServletContext context){
		logger.info("Precomputing graphs...");
		
		DatasetBasedGraphGenerator graphGenerator = new CachedDatasetBasedGraphGenerator(endpoint, cacheName);
		
		Map<String, List<String>> entities = getEntities(null);
		for (String cls : entities.keySet()) {
			try {
				logger.info(cls);
				graphGenerator.generateGraph(new NamedClass(cls), propertyFrequencyThreshold, namespace, cooccurrenceType);
			} catch (Exception e) {
				logger.error(e, e);
			}
		}
	}
	
	private List<Integer> getRandomNumbers(int total, int groups){
		Random rnd = new Random(123);
		List<Integer> partitionSizes = new ArrayList<Integer>(groups);
		for (int i = 0; i < groups - 1; i++) {
			int number = rnd.nextInt(total-(groups - i)) + 1;
			total -= number;
			partitionSizes.add(number);
		}
		partitionSizes.add(groups-1, total);
		return partitionSizes;
	}
	
	class QuestionGenerationTask implements Callable<List<RESTQuestion>>{
		
		private QuestionType questionType;
		private QuestionGenerator questionGenerator;
		private int maxNrOfQuestions;

		public QuestionGenerationTask(QuestionType questionType, QuestionGenerator questionGenerator, int maxNrOfQuestions) {
			this.questionType = questionType;
			this.questionGenerator = questionGenerator;
			this.maxNrOfQuestions = maxNrOfQuestions;
		}

		/* (non-Javadoc)
		 * @see java.util.concurrent.Callable#call()
		 */
		@Override
		public List<RESTQuestion> call() throws Exception {
			logger.info("Get " + maxNrOfQuestions + " questions of type " + questionType.getName() + "...");
			Set<Question> questions = questionGenerator.getQuestions(null, 1, maxNrOfQuestions);
			
			//convert to REST format
			List<RESTQuestion> restQuestions = new ArrayList<RESTQuestion>(questions.size());
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
			return restQuestions;
		}
		
	}
}
