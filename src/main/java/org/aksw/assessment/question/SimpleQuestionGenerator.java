/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.aksw.assessment.question.answer.Answer;
import org.aksw.assessment.question.answer.SimpleAnswer;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.apache.log4j.Logger;
import org.dllearner.kb.sparql.SparqlEndpoint;

/**
 *
 * @author ngonga
 */
public class SimpleQuestionGenerator implements QuestionGenerator {

    private static final Logger logger = Logger.getLogger(SimpleQuestionGenerator.class.getName());
    static int DIFFICULTY = 1;
    SparqlEndpoint endpoint;
    Set<Resource> types;
    Set<Resource> blackList;
    SimpleNLGwithPostprocessing nlg;

    public SimpleQuestionGenerator(SparqlEndpoint ep, Set<Resource> restrictions) {
        endpoint = ep;
        types = restrictions;
        blackList = Sets.newHashSet(
                ResourceFactory.createResource("http://www.w3.org/ns/prov#was"), ResourceFactory.createResource("http://www.w3.org/2002/07/owl#sameAs"), ResourceFactory.createResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), ResourceFactory.createResource("http://www.w3.org/ns/prov#wasDerivedFrom"), ResourceFactory.createResource("http://xmlns.com/foaf/0.1/isPrimaryTopicOf"), ResourceFactory.createResource("http://xmlns.com/foaf/0.1/depiction"), ResourceFactory.createResource("http://xmlns.com/foaf/0.1/homepage"), ResourceFactory.createResource("http://purl.org/dc/terms/subject"), ResourceFactory.createResource("http://dbpedia.org/ontology/wikiPageRedirects"), ResourceFactory.createResource("http://dbpedia.org/ontology/wikiPageExternalLink"), ResourceFactory.createResource("http://dbpedia.org/property/hasPhotoCollection"), ResourceFactory.createResource("http://dbpedia.org/ontology/thumbnail"));
        nlg = new SimpleNLGwithPostprocessing(endpoint);
    }

    @Override
    public Set<Question> getQuestions(Map<Triple, Double> informativenessMap, int difficulty, int number) {
        Set<Question> questions = new HashSet<>();
        List<Resource> resources = getResources();
        Set<Resource> questionResources;
        int index;
        Resource res;
        if (resources.size() < number) {
            questionResources = new HashSet<>(resources);
            for (Resource r : questionResources) {
                questions.add(generateQuestion(r));
            }
        } else {
            questionResources = new HashSet<>();
            while (questionResources.size() <= number) {
                res = resources.get((int) (Math.random() * resources.size()));
                if (!questionResources.contains(res)) {
                    questionResources.add(res);
                    Question q = generateQuestion(res);
                    if (q != null) {
                        questions.add(q);
                    }
                }
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

    public static ResultSet executeSelectQuery(String query, SparqlEndpoint endpoint) {

        QueryExecutionFactory qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
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
            if (!blackList.contains(property)) {
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
        return new SimpleQuestion(nlg.getNLR(sparqlQuery).replaceAll("This query retrieves", "Please select"), generateAnswers(resourceValues), generateAnswers(wrongAnswers), DIFFICULTY, sparqlQuery);
    }

    public List<Answer> generateAnswers(Set<Resource> resources) {
        List<Answer> answers = new ArrayList<Answer>();
        for (Resource r : resources) {
            answers.add(new SimpleAnswer(nlg.realiser.realiseSentence(nlg.getNPPhrase(r.getURI(), false, false))));
        }
        return answers;
    }

    public static void main(String args[]) {
        Resource r = ResourceFactory.createResource("http://dbpedia.org/ontology/Country");
        Set<Resource> res = new HashSet<Resource>();
        res.add(r);
        SimpleQuestionGenerator sqg = new SimpleQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), res);
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
