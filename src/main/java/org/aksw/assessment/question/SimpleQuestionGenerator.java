/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.assessment.question;

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
import java.util.Set;
import org.aksw.assessment.question.answer.Answer;
import org.aksw.assessment.question.answer.SimpleAnswer;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing;
import org.aksw.sparql2nl.naturallanguagegeneration.SimpleNLGwithPostprocessing2;
import org.dllearner.kb.sparql.SparqlEndpoint;

/**
 *
 * @author ngonga
 */
public class SimpleQuestionGenerator implements QuestionGenerator {

    static int DIFFICULTY = 1;
    SparqlEndpoint endpoint;
    Set<Resource> types;
    Set<Resource> blackList;
    SimpleNLGwithPostprocessing2 nlg;

    public SimpleQuestionGenerator(SparqlEndpoint ep, Set<Resource> restrictions) {
        endpoint = ep;
        types = restrictions;
        blackList = new HashSet<>();
        nlg = new SimpleNLGwithPostprocessing2(endpoint);
    }

    @Override
    public Set<Question> getQuestions(Map<Triple, Double> informativenessMap, int difficulty, int number) {
        Set<Question> questions = new HashSet<>();
        List<Resource> resources = getResources();
        Set<Resource> questionResources;
        if (resources.size() < number) {
            questionResources = new HashSet<>(resources);
        } else {
            questionResources = new HashSet<>();
            while (questionResources.size() < number) {
                questionResources.add(resources.get((int) (Math.random() * resources.size())));
            }
        }

        for (Resource r : questionResources) {
            questions.add(generateQuestion(r));
        }
        return questions;
    }

    public List<Resource> getResources() {
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
        //get properties
        String query = "select distinct ?p where {<" + r.getURI() + "> ?p ?o. FILTER(isURI(?o))}";
        Set<Resource> result = new HashSet<>();
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

        //pick random property
        int index = (int) (((double) result.size()) * Math.random());
        Iterator<Resource> iter = result.iterator();
        for (int i = 0; i < index - 1; i++);
        {
            iter.next();
        }
        property = iter.next();

        //get values for property
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

        //generate alternative answers
        if (!literalValues.isEmpty()) {
        }
        //generate alternative answers
        if (!resourceValues.isEmpty()) {
            Resource res = resourceValues.iterator().next();
            query = "select distinct ?o where {<" + res.getURI() + "> a ?x. ?o a ?x}";
            rs = executeSelectQuery(query, endpoint);
            while (rs.hasNext()) {
                qs = rs.next();
                if (!resourceValues.contains(qs.get("o").asResource())) {
                    wrongAnswers.add(qs.get("o").asResource());
                }
            }
        }
        return new SimpleQuestion(nlg.getNLR(sparqlQuery), generateAnswers(resourceValues), generateAnswers(wrongAnswers), DIFFICULTY, sparqlQuery);
    }

    public List<Answer> generateAnswers(Set<Resource> resources) {
        List<Answer> answers = new ArrayList<Answer>();
        for (Resource r : resources) {
            answers.add(new SimpleAnswer(nlg.realiser.realiseSentence(nlg.getNPPhrase(r.getURI(), false, false))));
        }
        return answers;
    }

    public static void main(String args[]) {
        Resource r = ResourceFactory.createResource("http://dbpedia.org/ontology/Species");
        Set<Resource> res = new HashSet<Resource>();
        res.add(r);
        SimpleQuestionGenerator sqg = new SimpleQuestionGenerator(SparqlEndpoint.getEndpointDBpedia(), res);
        Set<Question> questions = sqg.getQuestions(null, DIFFICULTY, 10);
        for (Question q : questions) {
            System.out.println(">>"+q.getText());
        }
    }
}
