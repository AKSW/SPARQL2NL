/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.aksw.assessment.question.Question.QuestionType;
import org.aksw.assessment.question.answer.Answer;
import org.aksw.assessment.question.answer.SimpleAnswer;
import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreEx;
import org.aksw.jena_sparql_api.cache.extra.CacheCoreH2;
import org.aksw.jena_sparql_api.cache.extra.CacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheExImpl;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

/**
 *
 * @author ngonga
 */
public class MultipleChoiceQuestionGenerator implements QuestionGenerator {

    private static final Logger logger = Logger.getLogger(MultipleChoiceQuestionGenerator.class.getName());
    static int DIFFICULTY = 1;
    SparqlEndpoint endpoint;
    Set<Resource> types;
    SimpleNLGwithPostprocessing nlg;
    
    private QueryExecutionFactory qef;

    public MultipleChoiceQuestionGenerator(SparqlEndpoint ep, Set<Resource> restrictions) {
        this(ep, null, restrictions);
    }
    
    public MultipleChoiceQuestionGenerator(SparqlEndpoint ep, String cacheDirectory, Set<Resource> restrictions) {
        endpoint = ep;
        types = restrictions;
        nlg = new SimpleNLGwithPostprocessing(endpoint);
        
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
    }

    @Override
    public Set<Question> getQuestions(Map<Triple, Double> informativenessMap, int difficulty, int number) {
        Set<Question> questions = new HashSet<>();
        
        //1. we generate of possible resources
        List<Resource> resources = getMostProminentResources();
        
        //2. we generate question(s) as long as we have resources or we got the maximum number of questions
//        Collections.shuffle(resources, new Random(123));
        Iterator<Resource> iterator = resources.iterator();
        Resource res;
        Question q;
        while(questions.size() < number && iterator.hasNext()){
        	res = iterator.next();System.out.println(getMostSpecificTypes(res));
        	q = generateQuestion(res);
            if (q != null) {
                questions.add(q);
            }
        }
        
        return questions;
    }

    public List<Resource> getResources() {
        logger.info("Getting possible resources for types " + types + "...");
        if (types == null || types.isEmpty()) {
            return null;
        }
        if (types.isEmpty()) {
            return new ArrayList<>();
        }
        String query;
        if (types.size() == 1) {
            query = "SELECT distinct ?x where {?x a <" + types.iterator().next().getURI() + "> }";
        } else {
            query = "SELECT distinct ?x where {";
            for (Resource nc : types) {
                query = query + "(?x a <" + nc.getURI() + ">) UNION ";
            }
            query = query.substring(0, query.lastIndexOf("UNION"));
        }
        query += "LIMIT 100";
        List<Resource> result = new ArrayList<>();
        ResultSet rs = executeSelectQuery(query, endpoint);
        QuerySolution qs;
        while (rs.hasNext()) {
            qs = rs.next();
            result.add(qs.getResource("x"));
        }
        logger.info("...got " + result);
        return result;
    }
    
    public List<Resource> getMostProminentResources() {
        logger.info("Getting possible resources for types " + types + " ranked by prominence...");
        if (types == null || types.isEmpty()) {
            return null;
        }
        if (types.isEmpty()) {
            return new ArrayList<>();
        }
        String query;
        if (types.size() == 1) {
            query = "SELECT distinct ?x (COUNT(?s) AS ?cnt) WHERE {?x a <" + types.iterator().next().getURI() + ">. ?s ?p ?x } ORDER BY DESC(?cnt)";
        } else {
            query = "SELECT distinct ?x where {";
            for (Resource nc : types) {
                query = query + "(?x a <" + nc.getURI() + ">) UNION ";
            }
            query = query.substring(0, query.lastIndexOf("UNION"));
        }
        List<Resource> result = new ArrayList<>();
        ResultSet rs = executeSelectQuery(query, endpoint);
        QuerySolution qs;
        while (rs.hasNext()) {
            qs = rs.next();
            result.add(qs.getResource("x"));
        }
        logger.info("...got " + result.size() + " resources, e.g. " + result.subList(0, Math.min(10, result.size())));
        return result;
    }
    
    public Set<String> getMostSpecificTypes(Resource resource){
    	Set<String> types = Sets.newHashSet();
    	String query = "SELECT ?type WHERE {<" + resource + "> a ?type. "
    			+ "FILTER NOT EXISTS{?s <http://www.w3.org/2000/01/rdf-schema#subClassOf> ?type.}}";
		QueryExecution qe = qef.createQueryExecution(query);
		ResultSet rs = qe.execSelect();
		QuerySolution qs;
		while (rs.hasNext()) {
			qs = rs.next();
			if(qs.getResource("type").isURIResource()){
				types.add(qs.getResource("type").getURI());
			}
		}
		qe.close();
		return types;
    }

    public ResultSet executeSelectQuery(String query, SparqlEndpoint endpoint) {

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

    public Question generateQuestion(Resource r) {
        logger.info("Generating question for resource " + r + "...");
        //get properties
        logger.info("Getting property candidates...");
        String query = "select distinct ?p where {<" + r.getURI() + "> ?p ?o. FILTER(isURI(?o))}";
        Set<Resource> result = new HashSet<Resource>();
        ResultSet rs = executeSelectQuery(query, endpoint);
        QuerySolution qs;
        Resource property;
        while (rs.hasNext()) {
            qs = rs.next();
            property = qs.getResource("p");
            if (!GeneralPropertyBlackList.contains(property) && !DBpediaPropertyBlackList.contains(property)) {
                result.add(property);
            }
        }
        logger.info("...got " + result);
        //early termination if resource has no meaningful properties
        if (result.isEmpty()) {
            return null;
        }

        //pick random property
        Random rnd = new Random(123);
        property = result.toArray(new Resource[]{})[rnd.nextInt(result.size())];
        logger.info("Chosen property: " + property);

        //get values for property, i.e. the correct answers
        logger.info("Generating correct answers...");
        query = "select distinct ?o where {<" + r.getURI() + "> <" + property.getURI() + "> ?o}";
        Query sparqlQuery = QueryFactory.create(query);
        Set<Resource> resourceValues = new HashSet<Resource>();
        Set<Resource> wrongAnswers = new HashSet<Resource>();
        Set<Literal> literalValues = new HashSet<Literal>();
        rs = executeSelectQuery(query, endpoint);
        while (rs.hasNext()) {
            qs = rs.next();
            if (qs.get("o").isLiteral()) {
                literalValues.add(qs.get("o").asLiteral());
            } else {
                resourceValues.add(qs.get("o").asResource());
            }
        }
        logger.info("...got " + resourceValues);

        //generate alternative answers, i.e. the wrong answers
        if (!literalValues.isEmpty()) {
        }
        //generate alternative answers
        logger.info("Generating wrong answers...");
        if (!resourceValues.isEmpty()) {
            Resource res = resourceValues.iterator().next();
            query = "select distinct ?o where {?x <"+property.getURI()+"> ?o. FILTER(isURI(?o))} LIMIT 10";
            rs = executeSelectQuery(query, endpoint);
            while (rs.hasNext()) {
                qs = rs.next();
                if (!resourceValues.contains(qs.get("o").asResource())) {
                    wrongAnswers.add(qs.get("o").asResource());
                }
            }
        }
        logger.info("...got " + wrongAnswers);
        return new SimpleQuestion(nlg.getNLR(sparqlQuery).replaceAll("This query retrieves", "Please select"), generateAnswers(resourceValues), generateAnswers(wrongAnswers), DIFFICULTY, sparqlQuery, QuestionType.MCQ);
    }

    public List<Answer> generateAnswers(Set<Resource> resources) {
        List<Answer> answers = new ArrayList<Answer>();
        for (Resource r : resources) {
            answers.add(new SimpleAnswer(nlg.realiser.realiseSentence(nlg.getNPPhrase(r.getURI(), false, false))));
        }
        return answers;
    }

    public static void main(String args[]) {
        Resource r = ResourceFactory.createResource("http://dbpedia.org/ontology/Person");
        Set<Resource> res = new HashSet<Resource>();
        res.add(r);
        MultipleChoiceQuestionGenerator sqg = new MultipleChoiceQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), "cache", res);
        Set<Question> questions = sqg.getQuestions(null, DIFFICULTY, 10);
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
