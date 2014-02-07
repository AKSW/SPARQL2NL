/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.aksw.assessment.question.answer.Answer;
import org.aksw.assessment.question.answer.SimpleAnswer;
import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreH2;
import org.aksw.jena_sparql_api.cache.extra.CacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheExImpl;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.entitysummarizer.Verbalizer;
import org.aksw.sparql2nl.entitysummarizer.clustering.hardening.HardeningFactory;
import org.aksw.sparql2nl.entitysummarizer.clustering.hardening.HardeningFactory.HardeningType;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator.Cooccurrence;
import org.aksw.sparql2nl.naturallanguagegeneration.LiteralConverter;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.aksw.sparql2nl.naturallanguagegeneration.URIConverter;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;

import simplenlg.framework.NLGElement;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 *
 * @author ngonga
 */
public class MultipleChoiceQuestionGenerator implements QuestionGenerator {

	private static final Logger logger = Logger.getLogger(MultipleChoiceQuestionGenerator.class.getName());
    static int DIFFICULTY = 1;
    
    private SparqlEndpoint endpoint;
    
    private String namespace;
    protected SimpleNLGwithPostprocessing nlg;
    
    private LiteralConverter literalConverter;
    protected int maxNrOfAnswersPerQuestion = 5;
    
    private QueryExecutionFactory qef;
    private SPARQLReasoner reasoner;
    
    //configuration of verbalizer
    protected Verbalizer verbalizer; 
    final int maxShownValuesPerProperty = 3;
    private double propertyFrequencyThreshold = 0.2; 
    private Cooccurrence cooccurrenceType = Cooccurrence.PROPERTIES;
    private HardeningType hardeningType = HardeningFactory.HardeningType.SMALLEST;
    
	final Comparator resourceComparator = new Comparator<RDFNode>() {

		@Override
		public int compare(RDFNode o1, RDFNode o2) {
			if(o1.isLiteral() && o2.isLiteral()){
				return o1.asLiteral().getLexicalForm().compareTo(o2.asLiteral().getLexicalForm());
			} else if(o1.isResource() && o2.isResource()){
				return o1.asResource().getURI().compareTo(o2.asResource().getURI());
			}
			return -1;
		}
	};
    
	protected Map<NamedClass, Set<ObjectProperty>> restrictions;
	protected Set<RDFNode> usedWrongAnswers;
    
    public MultipleChoiceQuestionGenerator(SparqlEndpoint ep, Map<NamedClass, Set<ObjectProperty>> restrictions) {
        this(ep, null, null, restrictions);
    }
    
    public MultipleChoiceQuestionGenerator(SparqlEndpoint ep, String cacheDirectory, String namespace, Map<NamedClass, Set<ObjectProperty>> restrictions) {
    	this.endpoint = ep;
		this.namespace = namespace;
		this.restrictions = restrictions;
		
        nlg = new SimpleNLGwithPostprocessing(endpoint);
        
        literalConverter = new LiteralConverter(new URIConverter(endpoint, cacheDirectory));
        
        qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
        if(cacheDirectory != null){
			try {
				long timeToLive = TimeUnit.DAYS.toMillis(30);
				CacheCoreEx cacheBackend = CacheCoreH2.create(cacheDirectory, timeToLive, true);
				CacheEx cacheFrontend = new CacheExImpl(cacheBackend);
				qef = new QueryExecutionFactoryCacheEx(qef, cacheFrontend);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
        
        reasoner = new SPARQLReasoner(new SparqlEndpointKS(endpoint), cacheDirectory);
        
        String wordNetDir = SimpleNLGwithPostprocessing.isWindows() ? "windows" : "linux";
        wordNetDir = this.getClass().getClassLoader().getResource(wordNetDir).getPath();
        
        verbalizer = new JeopardyVerbalizer(endpoint, cacheDirectory, wordNetDir);
        verbalizer.setPersonTypes(Sets.newHashSet("http://dbpedia.org/ontology/Person"));
        verbalizer.setMaxShownValuesPerProperty(maxShownValuesPerProperty);
        verbalizer.setOmitContentInBrackets(true);
    }

    public Question generateQuestion(Resource r, NamedClass type) {
        logger.info("Generating question for resource " + r + "...");
        
        //choose the property
        String property;
        
        //if there are no restrictions on the class, we have to choose randomly a property based on the existing instance data
        if(restrictions.get(type).isEmpty()){
        	//first of all, we check if there exists any meaningful information about the given resource, 
            //i.e. whether there are interesting triples
            //this is done by getting all properties for the given resource and filter out the black listed ones
            logger.info("Getting property candidates...");
            String query = "select distinct ?p where {<" + r.getURI() + "> ?p ?o. }";//FILTER(isURI(?o))
            Set<String> propertyCandidates = new HashSet<String>();
            ResultSet rs = executeSelectQuery(query);
            QuerySolution qs;
            while (rs.hasNext()) {
                qs = rs.next();
                property = qs.getResource("p").getURI();
                if (!GeneralPropertyBlackList.contains(property) && !DBpediaPropertyBlackList.contains(property)) {
                	propertyCandidates.add(property);
                }
            }
            logger.info("...got " + propertyCandidates);
            //early termination if resource has no meaningful properties
            if (propertyCandidates.isEmpty()) {
                return null;
            }

            //pick random property
            Random rnd = new Random(123);
            property = propertyCandidates.toArray(new String[]{})[rnd.nextInt(propertyCandidates.size())];
           
        } else {
        	Set<ObjectProperty> propertyCandidates = restrictions.get(type);
        	Random rnd = new Random();
            property = propertyCandidates.toArray(new ObjectProperty[]{})[rnd.nextInt(propertyCandidates.size())].getName();
        }
        logger.info("Chosen property: " + property);

        //get values for property, i.e. the correct answers
        logger.info("Generating correct answers...");
        String query = "select distinct ?o where {<" + r.getURI() + "> <" + property + "> ?o}";
        Query sparqlQuery = QueryFactory.create(query);
        Set<RDFNode> correctAnswers = new TreeSet<RDFNode>(resourceComparator);
        ResultSet rs = executeSelectQuery(query);
        QuerySolution qs;
        while (rs.hasNext()) {
            qs = rs.next();
            if (qs.get("o").isLiteral()) {
                correctAnswers.add(qs.get("o").asLiteral());
            } else {
                correctAnswers.add(qs.get("o").asResource());
            }
        }
        logger.info("...got " + correctAnswers);
        if(correctAnswers.isEmpty()){
        	return null;
        }
        
        //we pick up at least 1 and at most n correct answers randomly
        Random rnd = new Random(123);
        List<RDFNode> correctAnswerList = new ArrayList<RDFNode>(correctAnswers);
        Collections.shuffle(correctAnswerList, rnd);
        int maxNumberOfCorrectAnswers = rnd.nextInt((maxNrOfAnswersPerQuestion - 1) + 1) + 1;
        correctAnswerList = correctAnswerList.subList(0, Math.min(correctAnswerList.size(), maxNumberOfCorrectAnswers));

        //generate alternative answers, i.e. the wrong answers
        logger.info("Generating wrong answers...");
        Set<RDFNode> wrongAnswers = new TreeSet<RDFNode>(resourceComparator);
        //get similar of nature but wrong answers by using resources in object position using the same property as for the correct answers
        //TODO: some ranking for the wrong answers could be done in the same way as for the subjects
        if (!correctAnswers.isEmpty()) {
            query = "select distinct ?o where {?x <"+property+"> ?o. } LIMIT 1000";
            rs = executeSelectQuery(query);
            while (rs.hasNext()) {
                qs = rs.next();
                if (!correctAnswers.contains(qs.get("o"))) {
                    wrongAnswers.add(qs.get("o"));
                }
            }
        }
        wrongAnswers.removeAll(usedWrongAnswers);
        
        //we pick up (n-numberOfCorrectAnswers) wrong answers randomly
        rnd = new Random(123);
        List<RDFNode> wrongAnswerList = new ArrayList<RDFNode>(wrongAnswers);
        Collections.shuffle(wrongAnswerList, rnd);
        wrongAnswerList = wrongAnswerList.subList(0, Math.min(wrongAnswerList.size(), maxNrOfAnswersPerQuestion-correctAnswerList.size()));
        usedWrongAnswers.addAll(wrongAnswerList);
        logger.info("...got " + wrongAnswers);
        return new SimpleQuestion(
        		nlg.getNLR(sparqlQuery).replaceAll("This query retrieves", "Please select"), 
        		generateAnswers(correctAnswerList, true), 
        		generateAnswers(wrongAnswerList, false), 
        		DIFFICULTY, 
        		sparqlQuery, 
        		QuestionType.MC);
    }
    
    @Override
    public Set<Question> getQuestions(Map<Triple, Double> informativenessMap, int difficulty, int numberOfQuestions) {
    	 usedWrongAnswers = new HashSet<>();
        Set<Question> questions = new HashSet<>();
        
        //1. we generate of possible resources
        Map<Resource, NamedClass> resources = getMostProminentResources(restrictions.keySet());
        
        //2. we generate question(s) as long as we have resources or we got the maximum number of questions
//        Collections.shuffle(resources, new Random(123));
        Iterator<Entry<Resource, NamedClass>> iterator = resources.entrySet().iterator();
        Entry<Resource, NamedClass> entry;
        Question q;
        while(questions.size() < numberOfQuestions && iterator.hasNext()){
        	entry = iterator.next();
        	q = generateQuestion(entry.getKey(), entry.getValue());
            if (q != null) {
                questions.add(q);
            }
        }
        
        return questions;
    }
    
	protected ResultSet executeSelectQuery(String query) {

        QueryExecution qe = qef.createQueryExecution(query);
        ResultSet rs;
        try {
            rs = qe.execSelect();
        } catch (Exception e) {
            System.err.println(query);
            rs = null;
        }
        return rs;
    }

    protected List<Answer> generateAnswers(Collection<RDFNode> resources, boolean addHint) {
		List<Answer> answers = new ArrayList<Answer>();
		for (RDFNode r : resources) {
			Answer answer = null;
			if (r.isURIResource()) {
				String summary = null;
				if (addHint && r.isURIResource()) {
//					summary = getEntitySummary(r.asResource().getURI());
				}
				answer = new SimpleAnswer(nlg.realiser.realiseSentence(nlg.getNPPhrase(r.asResource().getURI(), false,
						false)), summary);

			} else if (r.isLiteral()) {
				answer = new SimpleAnswer(literalConverter.convert(r.asLiteral()));
			}
			answers.add(answer);
		}
		return answers;
	}
    
    /**
     * Returns a textual summary for a given individual.
     * @param ind
     * @return
     */
	protected String getEntitySummary(String entityURI) {
		//get the most specific type(s) of the individual
		Set<NamedClass> mostSpecificTypes = getMostSpecificTypes(entityURI);
		
		//pick the most prominent type
		NamedClass mostSpecificType = mostSpecificTypes.iterator().next();
		
		//return the summary
		return getEntitySummary(entityURI, mostSpecificType);
	}
    
    /**
     * Returns a textual summary for a given individual and its type.
     * @param ind
     * @return
     */
	protected String getEntitySummary(String entityURI, NamedClass type) {
		logger.info("Generating summary for " + entityURI + " of type " + type + "...");
		//create the summary
		List<NLGElement> text = verbalizer.verbalize(new Individual(entityURI), type, propertyFrequencyThreshold, cooccurrenceType,
				hardeningType);
		if (text == null)
			return null;
		String summary = verbalizer.realize(text);
		if (summary == null)
			return null;
		summary = summary.replaceAll("\\s?\\((.*?)\\)", "");
		summary = summary.replace(" , among others,", ", among others,");
		logger.info("...done.");
		return summary;
	}
	
	 protected Map<Resource, NamedClass> getMostProminentResources(Set<NamedClass> types) {
        logger.info("Getting possible resources for types " + types + " ranked by prominence...");
        if (types == null || types.isEmpty()) {
            return null;
        }
        
        Map<Resource, NamedClass> result = Maps.newLinkedHashMap();
        for (NamedClass type : types) {
        	String query = "SELECT distinct ?x (COUNT(?s) AS ?cnt) WHERE {?s ?p ?x. ";
        	query += "?x a <" + type.getURI() + ">.";
            query += "} ORDER BY DESC(?cnt) LIMIT 500";
            ResultSet rs = executeSelectQuery(query);
            QuerySolution qs;
            while (rs.hasNext()) {
                qs = rs.next();
                result.put(qs.getResource("x"), type);
            }
		}
//        String query = "SELECT distinct ?x (COUNT(?s) AS ?cnt) WHERE {?s ?p ?x. ";
//		for (NamedClass nc : types) {
//			query = query + "{?x a <" + nc.getURI() + ">} UNION ";
//		}
//		query = query.substring(0, query.lastIndexOf("UNION"));
//        query += "} ORDER BY DESC(?cnt) LIMIT 500";
//        Collections.shuffle(result);
        logger.info("...got " + result.size() + " resources, e.g. " + new ArrayList<Resource>(result.keySet()).subList(0, Math.min(10, result.size())));
        return result;
    }
	
	protected Set<NamedClass> getMostSpecificTypes(String uri) {
		Set<NamedClass> types = reasoner.getMostSpecificTypes(new Individual(uri));
		for (Iterator<NamedClass> iter = types.iterator(); iter.hasNext();) {
			NamedClass cls = iter.next();
			if (namespace != null && !cls.getName().startsWith(namespace)) {
				iter.remove();
			}
		}
		return types;
	}

    protected Collection<Triple> getSummaryTriples(String entityURI){
		return verbalizer.getSummaryTriples(new Individual(entityURI));
	}
    
    public static void main(String args[]) {
        Map<NamedClass, Set<ObjectProperty>> restrictions = Maps.newHashMap();
        restrictions.put(
        		new NamedClass("http://dbpedia.org/ontology/Person"), 
        		Sets.newHashSet(new ObjectProperty("http://dbpedia.org/ontology/birthPlace"), new ObjectProperty("http://dbpedia.org/ontology/birthDate")));
        MultipleChoiceQuestionGenerator sqg = new MultipleChoiceQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), "cache", "http://dbpedia.org/ontology/", restrictions);
        Set<Question> questions = sqg.getQuestions(null, DIFFICULTY, 20);
        for (Question q : questions) {
            if (q != null) {
                System.out.println(">>" + q.getText());
                List<Answer> correctAnswers = q.getCorrectAnswers();
                System.out.println(correctAnswers);
                List<Answer> wrongAnswers = q.getWrongAnswers();
                System.out.println(wrongAnswers);
            }
        }
    }
}
